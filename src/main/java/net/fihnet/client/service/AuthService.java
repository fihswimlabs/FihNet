package net.fihnet.client.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AuthService {
    boolean isLoggedIn();
    String getUsername();
    UUID getUserId();
    long getLoginTime();
    
    CompletableFuture<AuthResult> login(String username, String password);
    CompletableFuture<AuthResult> register(String username, String password, String email);
    CompletableFuture<Void> logout();
    CompletableFuture<Void> refreshSession();
    
    String getAuthToken();
    void saveSession(SessionData session);
    SessionData loadSession();
    boolean hasValidSession();
    
    void addAuthListener(AuthListener listener);
    void removeAuthListener(AuthListener listener);
    
    interface AuthListener {
        void onLoggedIn(String username, UUID userId);
        void onLoggedOut();
        void onAuthError(String message);
    }
    
    enum AuthResultType {
        SUCCESS,
        INVALID_CREDENTIALS,
        USERNAME_TAKEN,
        EMAIL_TAKEN,
        NETWORK_ERROR,
        UNKNOWN_ERROR
    }
    
    record AuthResult(AuthResultType type, String message, SessionData session) {}
    
    record SessionData(
        UUID userId,
        String username,
        String token,
        long expiresAt,
        long issuedAt
    ) {}
}
