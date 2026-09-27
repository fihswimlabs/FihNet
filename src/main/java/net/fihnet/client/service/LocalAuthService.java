package net.fihnet.client.service;

import net.fihnet.client.util.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalAuthService implements AuthService {
    private static final Logger LOG = LogManager.getLogger("FihNet/Auth");
    private final Path authStoragePath;
    private final ExecutorService executor;
    private final List<AuthListener> listeners = new ArrayList<>();
    
    private SessionData currentSession = null;
    
    public LocalAuthService() {
        this.authStoragePath = FabricLoader.getInstance().getConfigDir().resolve("fihnet");
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "FihNet-AuthService");
            t.setDaemon(true);
            return t;
        });
        currentSession = loadSession();
    }
    
    @Override
    public boolean isLoggedIn() {
        return currentSession != null && !isSessionExpired(currentSession);
    }
    
    @Override
    public String getUsername() {
        return currentSession != null ? currentSession.username() : null;
    }
    
    @Override
    public UUID getUserId() {
        return currentSession != null ? currentSession.userId() : null;
    }
    
    @Override
    public long getLoginTime() {
        return currentSession != null ? currentSession.issuedAt() : 0;
    }
    
    @Override
    public String getAuthToken() {
        return currentSession != null ? currentSession.token() : null;
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
    
    private boolean isSessionExpired(SessionData session) {
        return session == null || System.currentTimeMillis() > session.expiresAt();
    }
    
    private String generateAuthToken(UUID userId, String username) {
        long timestamp = System.currentTimeMillis();
        return String.format("fihnet_%s_%s_%d",
            userId.toString().replace("-", ""),
            username.toLowerCase().replace(" ", "_"),
            timestamp);
    }
    
        
    @Override
    public CompletableFuture<AuthResult> login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                new AuthResult(AuthResultType.INVALID_CREDENTIALS, "Username cannot be empty", null));
        }
        if (password == null || password.isEmpty()) {
            return CompletableFuture.completedFuture(
                new AuthResult(AuthResultType.INVALID_CREDENTIALS, "Password cannot be empty", null));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.info("Attempting login for user: {}", username);
                Thread.sleep(500);
                
                UUID userId = UUID.randomUUID();
                String token = generateAuthToken(userId, username);
                
                SessionData session = new SessionData(
                    userId, username.trim(), token,
                    System.currentTimeMillis() + (24 * 60 * 60 * 1000),
                    System.currentTimeMillis()
                );
                
                saveSession(session);
                currentSession = session;
                LOG.info("Login successful for user: {}", username);
                
                for (AuthListener listener : listeners) {
                    try { listener.onLoggedIn(username, userId); }
                    catch (Exception e) { LOG.warn("Error notifying listener: {}", e.getMessage()); }
                }
                
                return new AuthResult(AuthResultType.SUCCESS, "Login successful", session);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new AuthResult(AuthResultType.NETWORK_ERROR, "Login interrupted", null);
            } catch (Exception e) {
                LOG.error("Login failed: {}", e.getMessage());
                return new AuthResult(AuthResultType.UNKNOWN_ERROR, "Login failed: " + e.getMessage(), null);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<AuthResult> register(String username, String password, String email) {
        if (username == null || username.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                new AuthResult(AuthResultType.INVALID_CREDENTIALS, "Username cannot be empty", null));
        }
        if (password == null || password.length() < 6) {
            return CompletableFuture.completedFuture(
                new AuthResult(AuthResultType.INVALID_CREDENTIALS, "Password must be at least 6 characters", null));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.info("Attempting registration for user: {}", username);
                Thread.sleep(500);
                
                UUID userId = UUID.randomUUID();
                String token = generateAuthToken(userId, username);
                
                SessionData session = new SessionData(
                    userId, username.trim(), token,
                    System.currentTimeMillis() + (24 * 60 * 60 * 1000),
                    System.currentTimeMillis()
                );
                
                saveSession(session);
                currentSession = session;
                LOG.info("Registration successful for user: {}", username);
                
                for (AuthListener listener : listeners) {
                    try { listener.onLoggedIn(username, userId); }
                    catch (Exception e) { LOG.warn("Error notifying listener: {}", e.getMessage()); }
                }
                
                return new AuthResult(AuthResultType.SUCCESS, "Account created successfully", session);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new AuthResult(AuthResultType.NETWORK_ERROR, "Registration interrupted", null);
            } catch (Exception e) {
                LOG.error("Registration failed: {}", e.getMessage());
                return new AuthResult(AuthResultType.UNKNOWN_ERROR, "Registration failed: " + e.getMessage(), null);
            }
        }, executor);
    }
    
        
    @Override
    public CompletableFuture<Void> logout() {
        return CompletableFuture.runAsync(() -> {
            LOG.info("Logging out user: {}", currentSession != null ? currentSession.username() : "unknown");
            currentSession = null;
            Path sessionFile = FabricLoader.getInstance().getConfigDir().resolve("fihnet/session.json");
            try { Files.deleteIfExists(sessionFile); }
            catch (IOException e) { LOG.warn("Failed to delete session file: {}", e.getMessage()); }
            for (AuthListener listener : listeners) {
                try { listener.onLoggedOut(); }
                catch (Exception e) { LOG.warn("Error notifying listener: {}", e.getMessage()); }
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> refreshSession() {
        return CompletableFuture.runAsync(() -> {
            if (currentSession == null || isSessionExpired(currentSession)) {
                currentSession = loadSession();
                if (currentSession != null && isSessionExpired(currentSession)) {
                    currentSession = null;
                }
            }
        }, executor);
    }
    
    @Override
    public void saveSession(SessionData session) {
        if (session == null) return;
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("fihnet");
            Files.createDirectories(dir);
            Path sessionFile = dir.resolve("session.json");
            JsonObject json = new JsonObject();
            json.set("userId", session.userId().toString());
            json.set("username", session.username());
            json.set("token", session.token());
            json.set("expiresAt", session.expiresAt());
            json.set("issuedAt", session.issuedAt());
            Files.writeString(sessionFile, json.toString());
        } catch (IOException e) {
            LOG.error("Failed to save session: {}", e.getMessage());
        }
    }
    
    @Override
    public SessionData loadSession() {
        Path sessionFile = FabricLoader.getInstance().getConfigDir().resolve("fihnet/session.json");
        if (!Files.exists(sessionFile)) return null;
        
        try {
            String content = Files.readString(sessionFile);
            JsonObject json = JsonObject.fromString(content);
            UUID userId = UUID.fromString(json.getString("userId"));
            String username = json.getString("username");
            String token = json.getString("token");
            long expiresAt = json.getLong("expiresAt");
            long issuedAt = json.getLong("issuedAt");
            return new SessionData(userId, username, token, expiresAt, issuedAt);
        } catch (Exception e) {
            LOG.warn("Failed to load session: {}", e.getMessage());
            return null;
        }
    }
    
    public void onAuthError(String message) {
        for (AuthListener listener : listeners) {
            try { listener.onAuthError(message); }
            catch (Exception e) { LOG.warn("Error notifying listener: {}", e.getMessage()); }
        }
    }
}
