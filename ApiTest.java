import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * End-to-end checks for the FihNet server.
 *
 * Usage:
 *   java -cp server/out ApiTest                     (defaults to http://127.0.0.1:8765)
 *   java -cp server/out ApiTest https://your-url    (against a deployed instance)
 *
 * Usernames carry a per-run tag, so the suite can be re-run against the same server
 * (including Render, where data is ephemeral) without tripping its own duplicate
 * registration checks. Exits non-zero if anything fails.
 */
public final class ApiTest {
    static String base = "http://127.0.0.1:8765";
    static int fails = 0;
    static final HttpClient CLIENT = HttpClient.newHttpClient();
    static final String RUN = Long.toString(System.currentTimeMillis() % 100000000L);

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            base = args[0];
        }
        String alexName = "Alex" + RUN;
        String bobName = "Bob" + RUN;
        String ghostName = "Ghost" + RUN;
        System.out.println("Testing " + base + " as " + alexName + " / " + bobName);

        String alex = register(alexName, "secret123");
        String bob = register(bobName, "secret456");
        check("register two users", alex != null && bob != null);
        check("duplicate register is 409", registerStatus(alexName, "secret123") == 409);
        check("wrong password is 401", loginStatus(alexName, "nope") == 401);
        check("short password is 400", registerStatus("Tiny" + RUN, "abc") == 400);

        check("add friend is 201", post("/api/friends", friendJson(bobName), alex).statusCode() == 201);
        check("duplicate friend is 409", post("/api/friends", friendJson(bobName), alex).statusCode() == 409);
        check("unknown friend is 404", post("/api/friends", friendJson(ghostName), alex).statusCode() == 404);

        post("/api/presence", "{\"inGame\":true,\"serverAddress\":\"play.example.net:25565\",\"serverName\":\"Survival\",\"playerCount\":4}", bob);

        String friends = get("/api/friends", alex).body();
        check("friend list shows Bob", friends.contains("\"username\":\"" + bobName + "\""));
        check("friend list marks Bob online", friends.contains("\"online\":true"));
        check("friend list carries server info", friends.contains("Survival") && friends.contains("play.example.net:25565"));
        check("credits endpoint", get("/api/credits", alex).body().contains("\"credits\":120"));
        check("me endpoint", get("/api/auth/me", alex).body().contains("\"username\":\"" + alexName + "\""));
        check("health endpoint", get("/api/health", null).body().contains("\"ok\":true"));
        check("version endpoint", get("/api/version", null).body().contains("\"name\":\"FihNet\""));

        check("chat send is 202", post("/api/chat", "{\"to\":\"" + alexName + "\",\"message\":\"hello from Bob\"}", bob).statusCode() == 202);
        check("chat to non-friend is 404", post("/api/chat", "{\"to\":\"" + ghostName + "\",\"message\":\"hi\"}", bob).statusCode() == 404);

        String events = get("/api/events?since=0", alex).body();
        check("event stream delivers the chat", events.contains("\"type\":\"chat\"") && events.contains("hello from Bob"));
        check("event stream delivers presence", events.contains("\"type\":\"presence\""));
        long next = extractNext(events);
        String again = get("/api/events?since=" + next, alex).body();
        check("cursor prevents duplicates", again.contains("\"events\":[]"));
        check("chat history stored", get("/api/chat/" + bobName, alex).body().contains("hello from Bob"));
        check("unauthorised is 401", get("/api/friends", null).statusCode() == 401);

        System.out.println(fails == 0 ? "ALL TESTS PASSED" : fails + " TEST(S) FAILED");
        if (fails > 0) {
            System.exit(1);
        }
    }

    static String friendJson(String username) {
        return "{\"username\":\"" + username + "\"}";
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) {
            fails++;
        }
    }

    static String register(String username, String password) throws Exception {
        HttpResponse<String> response = post("/api/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null);
        return response.statusCode() == 201 ? token(response.body()) : null;
    }

    static int registerStatus(String username, String password) throws Exception {
        return post("/api/auth/register",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null).statusCode();
    }

    static int loginStatus(String username, String password) throws Exception {
        return post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null).statusCode();
    }

    static String token(String body) {
        int at = body.indexOf("\"token\":\"");
        if (at < 0) {
            return null;
        }
        int start = at + 9;
        int end = body.indexOf(34, start);
        return body.substring(start, end);
    }

    static long extractNext(String body) {
        int at = body.indexOf("\"next\":");
        if (at < 0) {
            return 0L;
        }
        int start = at + 7;
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.parseLong(body.substring(start, end));
    }

    static HttpResponse<String> get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), token);
    }

    static HttpResponse<String> post(String path, String body, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), token);
    }

    static HttpResponse<String> send(HttpRequest.Builder builder, String token) throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.timeout(java.time.Duration.ofSeconds(45));
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}