package net.fihnet.client.service;

import net.fihnet.client.net.FihNetServerConfig;
import net.fihnet.client.net.HttpJson;
import net.fihnet.client.util.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FihNet account authentication backed by a real FihNet server.
 *
 * <p>Entirely separate from the Mojang/Microsoft account: FihNet has its own
 * usernames, passwords and session tokens, and every call goes over HTTP to the
 * server configured in config/fihnet/server.txt.
 */
public final class RemoteAuthService implements AuthService {
    private static final Logger LOG = LogManager.getLogger("FihNet/Auth");
    private final ExecutorService executor;
    private final List<AuthListener> listeners = new ArrayList<>();

    private SessionData session;

    public RemoteAuthService() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "FihNet-Auth");
            t.setDaemon(true);
            return t;
        });
        this.session = loadSession();
        if (session != null) {
            LOG.info("Restored FihNet session for {}", session.username());
        }
    }

    @Override
    public boolean isLoggedIn() {
        return session != null && !isExpired(session);
    }

    @Override
    public String getUsername() {
        return session == null ? null : session.username();
    }

    @Override
    public UUID getUserId() {
        return session == null ? null : session.userId();
    }

    @Override
    public long getLoginTime() {
        return session == null ? 0L : session.issuedAt();
    }

    @Override
    public String getAuthToken() {
        return session == null ? "" : session.token();
    }

    @Override
    public boolean hasValidSession() {
        return isLoggedIn();
    }

    @Override
    public void addAuthListener(AuthListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeAuthListener(AuthListener listener) {
        listeners.remove(listener);
    }

    private boolean isExpired(SessionData data) {
        return data == null || System.currentTimeMillis() >= data.expiresAt();
    }

    private static Path sessionFile() {
        return FihNetServerConfig.dir().resolve("session.json");
    }

    @Override
    public void saveSession(SessionData data) {
        this.session = data;
        if (data == null) {
            try {
                Files.deleteIfExists(sessionFile());
            } catch (Exception e) {
                LOG.warn("Could not delete session file: {}", e.getMessage());
            }
            FihNetServerConfig.clearToken();
            return;
        }
        try {
            Files.createDirectories(FihNetServerConfig.dir());
            JsonObject root = new JsonObject();
            root.set("userId", data.userId().toString());
            root.set("username", data.username());
            root.set("token", data.token());
            root.set("expiresAt", data.expiresAt());
            root.set("issuedAt", data.issuedAt());
            Files.writeString(sessionFile(), root.toString(), StandardCharsets.UTF_8);
            FihNetServerConfig.writeToken(data.token());
        } catch (Exception e) {
            LOG.error("Could not save session: {}", e.getMessage());
        }
    }

    @Override
    public SessionData loadSession() {
        try {
            if (!Files.exists(sessionFile())) {
                return null;
            }
            JsonObject root = JsonObject.fromString(Files.readString(sessionFile(), StandardCharsets.UTF_8));
            String idRaw = root.getString("userId");
            String username = root.getString("username");
            String token = root.getString("token");
            if (idRaw == null || username == null || token == null) {
                return null;
            }
            SessionData data = new SessionData(UUID.fromString(idRaw), username, token,
                    root.getLong("expiresAt"), root.getLong("issuedAt"));
            return isExpired(data) ? null : data;
        } catch (Exception e) {
            LOG.warn("Could not load session: {}", e.getMessage());
            return null;
        }
    }
    private String base() {
        return FihNetServerConfig.serverUrl();
    }

    private void notifyLoggedIn(SessionData data) {
        for (AuthListener listener : listeners) {
            try {
                listener.onLoggedIn(data.username(), data.userId());
            } catch (Exception e) {
                LOG.warn("Auth listener failed: {}", e.getMessage());
            }
        }
    }

    private void notifyError(String message) {
        for (AuthListener listener : listeners) {
            try {
                listener.onAuthError(message);
            } catch (Exception e) {
                LOG.warn("Auth listener failed: {}", e.getMessage());
            }
        }
    }

    private SessionData parseSession(JsonObject root) {
        String idRaw = root.getString("userId");
        String username = root.getString("username");
        String token = root.getString("token");
        if (idRaw == null || username == null || token == null) {
            return null;
        }
        long expiresAt = root.getLong("expiresAt");
        if (expiresAt <= 0L) {
            expiresAt = System.currentTimeMillis() + 86400000L;
        }
        return new SessionData(UUID.fromString(idRaw), username, token, expiresAt, root.getLong("issuedAt"));
    }

    @Override
    public CompletableFuture<AuthResult> login(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject body = new JsonObject();
            body.set("username", username);
            body.set("password", password);
            try {
                HttpJson.Response response = HttpJson.post(base() + "/api/auth/login", body.toString(), null);
                if (response.ok()) {
                    SessionData data = parseSession(response.json());
                    if (data == null) {
                        return new AuthResult(AuthResultType.UNKNOWN_ERROR, "Malformed server response", null);
                    }
                    saveSession(data);
                    notifyLoggedIn(data);
                    return new AuthResult(AuthResultType.SUCCESS, "Signed in as " + data.username(), data);
                }
                if (response.status() == 401) {
                    return new AuthResult(AuthResultType.INVALID_CREDENTIALS, "Invalid username or password", null);
                }
                if (response.status() == 404) {
                    return new AuthResult(AuthResultType.NETWORK_ERROR, "No FihNet server at " + base(), null);
                }
                return new AuthResult(AuthResultType.UNKNOWN_ERROR, "Server returned " + response.status(), null);
            } catch (Exception e) {
                LOG.warn("Login failed: {}", e.getMessage());
                return new AuthResult(AuthResultType.NETWORK_ERROR, "Cannot reach " + base(), null);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<AuthResult> register(String username, String password, String email) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject body = new JsonObject();
            body.set("username", username);
            body.set("password", password);
            body.set("email", email == null ? "" : email);
            try {
                HttpJson.Response response = HttpJson.post(base() + "/api/auth/register", body.toString(), null);
                if (response.ok()) {
                    SessionData data = parseSession(response.json());
                    if (data == null) {
                        return new AuthResult(AuthResultType.UNKNOWN_ERROR, "Malformed server response", null);
                    }
                    saveSession(data);
                    notifyLoggedIn(data);
                    return new AuthResult(AuthResultType.SUCCESS, "Created FihNet account " + data.username(), data);
                }
                if (response.status() == 409) {
                    String text = response.body() == null ? "" : response.body().toLowerCase(java.util.Locale.ROOT);
                    AuthResultType type = text.contains("email") ? AuthResultType.EMAIL_TAKEN : AuthResultType.USERNAME_TAKEN;
                    return new AuthResult(type, "That name is already taken", null);
                }
                return new AuthResult(AuthResultType.UNKNOWN_ERROR, "Server returned " + response.status(), null);
            } catch (Exception e) {
                LOG.warn("Registration failed: {}", e.getMessage());
                return new AuthResult(AuthResultType.NETWORK_ERROR, "Cannot reach " + base(), null);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> logout() {
        return CompletableFuture.runAsync(() -> {
            String token = getAuthToken();
            if (token != null && !token.isEmpty()) {
                try {
                    HttpJson.post(base() + "/api/auth/logout", "{}", token);
                } catch (Exception e) {
                    LOG.warn("Logout call failed: {}", e.getMessage());
                }
            }
            saveSession(null);
            for (AuthListener listener : listeners) {
                try {
                    listener.onLoggedOut();
                } catch (Exception e) {
                    LOG.warn("Auth listener failed: {}", e.getMessage());
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> refreshSession() {
        return CompletableFuture.runAsync(() -> {
            String token = getAuthToken();
            if (token == null || token.isEmpty()) {
                return;
            }
            try {
                HttpJson.Response response = HttpJson.get(base() + "/api/auth/me", token);
                if (response.ok()) {
                    SessionData data = parseSession(response.json());
                    if (data != null) {
                        saveSession(data);
                        return;
                    }
                }
                if (response.status() == 401) {
                    LOG.info("FihNet session expired, signing out");
                    saveSession(null);
                    notifyError("Session expired");
                }
            } catch (Exception e) {
                LOG.warn("Session refresh failed: {}", e.getMessage());
            }
        }, executor);
    }
}
