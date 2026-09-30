import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * FihNet reference server. Implements FIHNET_SERVER_API.md with nothing but the
 * JDK, so it runs straight from source:
 *
 *   java server/FihNetServer.java --port 8765 --data server/fihnet-data.json
 */
public final class FihNetServer {
    static final long ONLINE_WINDOW_MS = 15000L;
    static final long TOKEN_TTL_MS = 86400000L;
    static final int MAX_EVENTS = 500;

    static final Map<String, User> USERS = new ConcurrentHashMap<>();
    static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
    static final Map<String, Long> TOKEN_EXPIRY = new ConcurrentHashMap<>();
    static final Map<String, List<String>> FRIENDS = new ConcurrentHashMap<>();
    static final Map<String, Presence> PRESENCE = new ConcurrentHashMap<>();
    static final Map<String, List<Msg>> HISTORY = new ConcurrentHashMap<>();
    static final Map<String, Events> EVENTS = new ConcurrentHashMap<>();
    static final SecureRandom RANDOM = new SecureRandom();

    static Path dataFile;

    static final class User {
        String username;
        String salt;
        String hash;
        int credits;
    }

    static final class Presence {
        boolean inGame;
        String serverAddress = "";
        String serverName = "";
        int playerCount;
        long lastSeen;
    }

    static final class Msg {
        String from;
        String to;
        String message;
        long ts;
    }

    static final class Event {
        long id;
        String json;
    }

    static final class Events {
        final List<Event> items = new ArrayList<>();
        long lastId;
    }

    public static void main(String[] args) throws Exception {
        int port = 8765;
        String data = "server/fihnet-data.json";
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            port = Integer.parseInt(envPort.trim());
        }
        String envData = System.getenv("FIHNET_DATA");
        if (envData != null && !envData.isBlank()) {
            data = envData.trim();
        }
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[i + 1]);
            }
            if ("--data".equals(args[i])) {
                data = args[i + 1];
            }
        }
        dataFile = Path.of(data);
        load();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", FihNetServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("FihNet server listening on port " + port);
        System.out.println("Users loaded: " + USERS.size() + ", data file: " + dataFile.toAbsolutePath());
    }

    static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            // Render uses this as the service health check path.
            if (path.equals("/api/health")) {
                send(ex, 200, "{\"ok\":true,\"name\":\"FihNet\",\"proto\":1}");
                return;
            }
            // Cheap endpoint for uptime checks and dashboards.
            if (path.equals("/api/version")) {
                send(ex, 200, "{\"name\":\"FihNet\",\"proto\":1,\"health\":\"/api/health\"}");
                return;
            }
            if (path.equals("/api/auth/register") && method.equals("POST")) {
                register(ex);
                return;
            }
            if (path.equals("/api/auth/login") && method.equals("POST")) {
                login(ex);
                return;
            }
            if (path.equals("/api/auth/logout") && method.equals("POST")) {
                logout(ex);
                return;
            }
            if (path.equals("/api/auth/me") && method.equals("GET")) {
                me(ex);
                return;
            }
            if (path.equals("/api/presence")) {
                presence(ex, method);
                return;
            }
            if (path.startsWith("/api/friends")) {
                friends(ex, method, path);
                return;
            }
            if (path.equals("/api/chat") && method.equals("POST")) {
                chatSend(ex);
                return;
            }
            if (path.startsWith("/api/chat/") && method.equals("GET")) {
                chatHistory(ex, path);
                return;
            }
            if (path.equals("/api/events") && method.equals("GET")) {
                events(ex);
                return;
            }
            if (path.equals("/api/credits") && method.equals("GET")) {
                credits(ex);
                return;
            }
            send(ex, 404, "{\"error\":\"unknown route\"}");
        } catch (Exception e) {
            System.out.println("ERROR " + e);
            send(ex, 500, "{\"error\":\"server error\"}");
        } finally {
            ex.close();
        }
    }
    static void register(HttpExchange ex) throws IOException {
        Map<String, Object> body = parseObj(readBody(ex));
        String username = str(body, "username");
        String password = str(body, "password");
        if (username.isBlank() || password.length() < 6) {
            send(ex, 400, "{\"error\":\"username required and password must be at least 6 characters\"}");
            return;
        }
        String key = username.toLowerCase(Locale.ROOT);
        if (USERS.containsKey(key)) {
            send(ex, 409, "{\"error\":\"username already taken\"}");
            return;
        }
        User user = new User();
        user.username = username;
        user.salt = newSalt();
        user.hash = hash(password, user.salt);
        user.credits = 120;
        USERS.put(key, user);
        FRIENDS.computeIfAbsent(key, k -> new ArrayList<>());
        save();
        System.out.println("Registered " + username);
        String token = newToken(key);
        send(ex, 201, session(user, token));
    }

    static void login(HttpExchange ex) throws IOException {
        Map<String, Object> body = parseObj(readBody(ex));
        String key = str(body, "username").toLowerCase(Locale.ROOT);
        String password = str(body, "password");
        User user = USERS.get(key);
        if (user == null || !user.hash.equals(hash(password, user.salt))) {
            send(ex, 401, "{\"error\":\"invalid username or password\"}");
            return;
        }
        String token = newToken(key);
        System.out.println("Login " + user.username);
        send(ex, 200, session(user, token));
    }

    static void logout(HttpExchange ex) throws IOException {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            TOKENS.remove(token);
            TOKEN_EXPIRY.remove(token);
        }
        sendNoContent(ex, 204);
    }

    static void me(HttpExchange ex) throws IOException {
        String key = authUser(ex);
        if (key == null) {
            send(ex, 401, "{\"error\":\"not signed in\"}");
            return;
        }
        User user = USERS.get(key);
        String token = ex.getRequestHeaders().getFirst("Authorization").substring(7).trim();
        send(ex, 200, session(user, token));
    }

    static String authUser(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        Long expiry = TOKEN_EXPIRY.get(token);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            TOKENS.remove(token);
            TOKEN_EXPIRY.remove(token);
            return null;
        }
        return TOKENS.get(token);
    }

    static String session(User user, String token) {
        long now = System.currentTimeMillis();
        return "{\"userId\":\"" + uuidFor(user.username) + "\",\"username\":\"" + esc(user.username)
                + "\",\"token\":\"" + token + "\",\"expiresAt\":" + (now + TOKEN_TTL_MS)
                + ",\"issuedAt\":" + now + "}";
    }

    static void presence(HttpExchange ex, String method) throws IOException {
        String key = authUser(ex);
        if (key == null) {
            send(ex, 401, "{\"error\":\"not signed in\"}");
            return;
        }
        if (method.equals("DELETE")) {
            PRESENCE.remove(key);
            broadcastPresence(key);
            sendNoContent(ex, 204);
            return;
        }
        Map<String, Object> body = parseObj(readBody(ex));
        Presence presence = new Presence();
        presence.inGame = bool(body, "inGame");
        presence.serverAddress = str(body, "serverAddress");
        presence.serverName = str(body, "serverName");
        presence.playerCount = num(body, "playerCount");
        presence.lastSeen = System.currentTimeMillis();
        PRESENCE.put(key, presence);
        broadcastPresence(key);
        sendNoContent(ex, 204);
    }

    static void friends(HttpExchange ex, String method, String path) throws IOException {
        String key = authUser(ex);
        if (key == null) {
            send(ex, 401, "{\"error\":\"not signed in\"}");
            return;
        }
        if (method.equals("GET")) {
            List<String> list = FRIENDS.computeIfAbsent(key, k -> new ArrayList<>());
            StringBuilder sb = new StringBuilder();
            sb.append("{\"friends\":[");
            boolean first = true;
            for (String friend : new ArrayList<>(list)) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(friendJson(friend));
            }
            sb.append("]}");
            send(ex, 200, sb.toString());
            return;
        }
        if (method.equals("DELETE")) {
            String target = path.substring("/api/friends/".length());
            String other = target.toLowerCase(Locale.ROOT);
            FRIENDS.computeIfAbsent(key, k -> new ArrayList<>()).removeIf(n -> n.equalsIgnoreCase(target));
            FRIENDS.computeIfAbsent(other, k -> new ArrayList<>()).removeIf(n -> n.equalsIgnoreCase(USERS.getOrDefault(key, new User()).username));
            save();
            sendNoContent(ex, 204);
            return;
        }
        Map<String, Object> body = parseObj(readBody(ex));
        String target = str(body, "username");
        String other = target.toLowerCase(Locale.ROOT);
        if (!USERS.containsKey(other)) {
            send(ex, 404, "{\"error\":\"no such user\"}");
            return;
        }
        if (other.equals(key)) {
            send(ex, 400, "{\"error\":\"cannot add yourself\"}");
            return;
        }
        List<String> mine = FRIENDS.computeIfAbsent(key, k -> new ArrayList<>());
        if (mine.stream().anyMatch(n -> n.equalsIgnoreCase(target))) {
            send(ex, 409, "{\"error\":\"already friends\"}");
            return;
        }
        mine.add(USERS.get(other).username);
        FRIENDS.computeIfAbsent(other, k -> new ArrayList<>()).add(USERS.get(key).username);
        save();
        System.out.println(USERS.get(key).username + " and " + USERS.get(other).username + " are now friends");
        send(ex, 201, friendJson(other));
    }

    static void chatSend(HttpExchange ex) throws IOException {
        String key = authUser(ex);
        if (key == null) {
            send(ex, 401, "{\"error\":\"not signed in\"}");
            return;
        }
        Map<String, Object> body = parseObj(readBody(ex));
        String to = str(body, "to");
        String message = str(body, "message");
        String other = to.toLowerCase(Locale.ROOT);
        if (!USERS.containsKey(other) || !isFriend(key, other)) {
            send(ex, 404, "{\"error\":\"not a friend\"}");
            return;
        }
        if (message.isBlank()) {
            send(ex, 400, "{\"error\":\"empty message\"}");
            return;
        }
        Msg msg = new Msg();
        msg.from = USERS.get(key).username;
        msg.to = USERS.get(other).username;
        msg.message = message;
        msg.ts = System.currentTimeMillis();
        history(key, other).add(msg);
        save();
        pushEvent(other, "{\"type\":\"chat\",\"from\":\"" + esc(msg.from) + "\",\"message\":\"" + esc(msg.message) + "\",\"ts\":" + msg.ts + "}");
        System.out.println("Chat " + msg.from + " to " + msg.to + ": " + msg.message);
        send(ex, 202, "{\"ok\":true}");
    }

    static void chatHistory(HttpExchange ex, String path) throws IOException {
        String key = authUser(ex);
        if (key == null) {
            send(ex, 401, "{\"error\":\"not signed in\"}");
            return;
        }
        String with = path.substring("/api/chat/".length()).toLowerCase(Locale.ROOT);
        int limit = queryInt(ex, "limit", 50);
        List<Msg> all = history(key, with);
        int start = Math.max(0, all.size() - limit);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"messages\":[");
        for (int i = start; i < all.size(); i++) {
            if (i > start) {
                sb.append(",");
            }
            Msg m = all.get(i);
            sb.append("{\"from\":\"").append(esc(m.from)).append("\",\"to\":\"").append(esc(m.to))
                    .append("\",\"message\":\"").append(esc(m.message)).append("\",\"ts\":").append(m.ts).append("}");
        }
        sb.append("]}");
        send(ex, 200, sb.toString());
    }

    static void events(HttpExchange ex) throws IOException {
        String key = authUser(ex);
        if (key == null) {
            send(ex, 401, "{\"error\":\"not signed in\"}");
            return;
        }
        long since = queryInt(ex, "since", 0);
        Events queue = EVENTS.computeIfAbsent(key, k -> new Events());
        String payload;
        synchronized (queue) {
            List<Event> out = collect(queue, since);
            if (out.isEmpty()) {
                try {
                    queue.wait(8000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                out = collect(queue, since);
            }
            payload = eventsJson(out, queue.lastId, since);
        }
        send(ex, 200, payload);
    }

    static void credits(HttpExchange ex) throws IOException {
        String key = authUser(ex);
        if (key == null) {
            send(ex, 401, "{\"error\":\"not signed in\"}");
            return;
        }
        send(ex, 200, "{\"credits\":" + USERS.get(key).credits + "}");
    }

    static boolean isFriend(String a, String b) {
        for (String name : FRIENDS.getOrDefault(a, List.of())) {
            if (name.equalsIgnoreCase(b)) {
                return true;
            }
        }
        return false;
    }

    static String historyKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    static List<Msg> history(String a, String b) {
        return HISTORY.computeIfAbsent(historyKey(a, b), k -> new ArrayList<>());
    }

    static void pushEvent(String key, String json) {
        Events queue = EVENTS.computeIfAbsent(key, k -> new Events());
        synchronized (queue) {
            queue.lastId++;
            Event event = new Event();
            event.id = queue.lastId;
            event.json = json;
            queue.items.add(event);
            while (queue.items.size() > MAX_EVENTS) {
                queue.items.remove(0);
            }
            queue.notifyAll();
        }
    }

    static List<Event> collect(Events queue, long since) {
        List<Event> out = new ArrayList<>();
        for (Event e : queue.items) {
            if (e.id > since) {
                out.add(e);
            }
        }
        return out;
    }

    static String eventsJson(List<Event> out, long lastId, long since) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"events\":[");
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(out.get(i).json);
        }
        sb.append("],\"next\":").append(Math.max(lastId, since)).append("}");
        return sb.toString();
    }

    static boolean isOnline(String key) {
        Presence p = PRESENCE.get(key);
        return p != null && System.currentTimeMillis() - p.lastSeen < ONLINE_WINDOW_MS;
    }

    static void broadcastPresence(String key) {
        User user = USERS.get(key);
        if (user == null) {
            return;
        }
        Presence p = PRESENCE.get(key);
        boolean online = isOnline(key);
        String json = "{\"type\":\"presence\",\"username\":\"" + esc(user.username) + "\",\"online\":" + online
                + ",\"inGame\":" + (online && p.inGame) + ",\"serverName\":\"" + esc(p == null ? "" : p.serverName) + "\"}";
        for (String friend : FRIENDS.getOrDefault(key, List.of())) {
            pushEvent(friend.toLowerCase(Locale.ROOT), json);
        }
    }

    static String friendJson(String rawKey) {
        String key = rawKey.toLowerCase(Locale.ROOT);
        User user = USERS.get(key);
        String username = user == null ? rawKey : user.username;
        Presence p = PRESENCE.get(key);
        boolean online = isOnline(key);
        return "{\"id\":\"" + uuidFor(username) + "\",\"username\":\"" + esc(username)
                + "\",\"online\":" + online
                + ",\"inGame\":" + (online && p.inGame)
                + ",\"serverAddress\":\"" + esc(online && p != null ? p.serverAddress : "") + "\""
                + ",\"serverName\":\"" + esc(online && p != null ? p.serverName : "") + "\""
                + ",\"playerCount\":" + (online && p != null ? p.playerCount : 0)
                + ",\"modded\":true"
                + ",\"lastSeen\":" + (p == null ? 0L : p.lastSeen) + "}";
    }

    static int queryInt(HttpExchange ex, String name, int def) {
        String query = ex.getRequestURI().getQuery();
        if (query == null) {
            return def;
        }
        for (String part : query.split("&")) {
            if (part.startsWith(name + "=")) {
                try {
                    return Integer.parseInt(part.substring(name.length() + 1));
                } catch (NumberFormatException ignored) {
                    return def;
                }
            }
        }
        return def;
    }

    static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void sendNoContent(HttpExchange ex, int status) throws IOException {
        ex.sendResponseHeaders(status, -1);
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 34) {
                sb.append("\\\"");
            } else if (c == 92) {
                sb.append("\\\\");
            } else if (c == 10) {
                sb.append("\\n");
            } else if (c == 13) {
                sb.append("\\r");
            } else if (c == 9) {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static Map<String, Object> parseObj(String json) {
        Map<String, Object> out = new HashMap<>();
        if (json == null) {
            return out;
        }
        json = json.trim();
        if (json.length() < 2 || json.charAt(0) != 123 || json.charAt(json.length() - 1) != 125) {
            return out;
        }
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return out;
        }
        for (String pair : splitPairs(json)) {
            int colon = -1;
            boolean inString = false;
            for (int i = 0; i < pair.length(); i++) {
                char c = pair.charAt(i);
                if (c == 34) {
                    inString = !inString;
                }
                if (!inString && c == 58) {
                    colon = i;
                    break;
                }
            }
            if (colon < 0) {
                continue;
            }
            String k = unquote(pair.substring(0, colon).trim());
            String v = pair.substring(colon + 1).trim();
            if (v.length() >= 2 && v.charAt(0) == 34 && v.charAt(v.length() - 1) == 34) {
                out.put(k, unquote(v));
            } else if (v.equals("true")) {
                out.put(k, Boolean.TRUE);
            } else if (v.equals("false")) {
                out.put(k, Boolean.FALSE);
            } else if (!v.isEmpty()) {
                try {
                    out.put(k, v.contains(".") ? Double.valueOf(v) : Long.valueOf(v));
                } catch (NumberFormatException e) {
                    out.put(k, v);
                }
            }
        }
        return out;
    }

    static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == 34 && s.charAt(s.length() - 1) == 34) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    static String[] splitPairs(String json) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        int braces = 0;
        int brackets = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == 34) {
                inString = !inString;
            }
            if (!inString) {
                if (c == 123) braces++;
                if (c == 125) braces--;
                if (c == 91) brackets++;
                if (c == 93) brackets--;
            }
            if (c == 44 && !inString && braces == 0 && brackets == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString().trim());
        }
        return out.toArray(new String[0]);
    }

    static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    static boolean bool(Map<String, Object> map, String key) {
        return Boolean.TRUE.equals(map.get(key));
    }

    static int num(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.intValue() : 0;
    }

    static long longNum(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : 0L;
    }

    static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    static String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), 100000, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String newToken(String key) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        TOKENS.put(token, key);
        TOKEN_EXPIRY.put(token, System.currentTimeMillis() + TOKEN_TTL_MS);
        return token;
    }

    static String uuidFor(String username) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(username.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            long msb = 0L;
            long lsb = 0L;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (d[i] & 255);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (d[i] & 255);
            }
            return new java.util.UUID(msb, lsb).toString();
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString();
        }
    }

    static void save() {
        if (dataFile == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, User> entry : USERS.entrySet()) {
            User u = entry.getValue();
            sb.append("{\"kind\":\"user\",\"key\":\"").append(esc(entry.getKey()))
              .append("\",\"username\":\"").append(esc(u.username))
              .append("\",\"salt\":\"").append(u.salt)
              .append("\",\"hash\":\"").append(u.hash)
              .append("\",\"credits\":").append(u.credits).append("}").append("\n");
        }
        for (Map.Entry<String, List<String>> entry : FRIENDS.entrySet()) {
            for (String friend : entry.getValue()) {
                sb.append("{\"kind\":\"friend\",\"a\":\"").append(esc(entry.getKey()))
                  .append("\",\"b\":\"").append(esc(friend)).append("}").append("\n");
            }
        }
        for (List<Msg> list : HISTORY.values()) {
            for (Msg m : list) {
                sb.append("{\"kind\":\"msg\",\"from\":\"").append(esc(m.from))
                  .append("\",\"to\":\"").append(esc(m.to))
                  .append("\",\"message\":\"").append(esc(m.message))
                  .append("\",\"ts\":").append(m.ts).append("}").append("\n");
            }
        }
        try {
            Path parent = dataFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(dataFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Could not save data: " + e.getMessage());
        }
    }

    static void load() {
        if (dataFile == null || !Files.exists(dataFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(dataFile, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = parseObj(line);
                String kind = str(row, "kind");
                if (kind.equals("user")) {
                    User u = new User();
                    u.username = str(row, "username");
                    u.salt = str(row, "salt");
                    u.hash = str(row, "hash");
                    u.credits = num(row, "credits");
                    String key = str(row, "key");
                    USERS.put(key, u);
                    FRIENDS.computeIfAbsent(key, k -> new ArrayList<>());
                } else if (kind.equals("friend")) {
                    String a = str(row, "a").toLowerCase(Locale.ROOT);
                    String b = str(row, "b");
                    List<String> list = FRIENDS.computeIfAbsent(a, k -> new ArrayList<>());
                    boolean present = false;
                    for (String name : list) {
                        if (name.equalsIgnoreCase(b)) {
                            present = true;
                        }
                    }
                    if (!present) {
                        list.add(b);
                    }
                } else if (kind.equals("msg")) {
                    Msg m = new Msg();
                    m.from = str(row, "from");
                    m.to = str(row, "to");
                    m.message = str(row, "message");
                    m.ts = longNum(row, "ts");
                    history(m.from.toLowerCase(Locale.ROOT), m.to.toLowerCase(Locale.ROOT)).add(m);
                }
            }
            System.out.println("Loaded " + USERS.size() + " users from " + dataFile);
        } catch (Exception e) {
            System.out.println("Could not load data: " + e.getMessage());
        }
    }
}
