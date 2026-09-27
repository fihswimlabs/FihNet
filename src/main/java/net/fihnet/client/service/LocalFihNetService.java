package net.fihnet.client.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Offline fallback used only when no FihNet server is configured. */
public final class LocalFihNetService implements FihNetService {
    @Override
    public CompletableFuture<List<FriendPresence>> friends() {
        return CompletableFuture.completedFuture(List.of(
                new FriendPresence(UUID.randomUUID(), "Alex_plays", true, true, null, "Survival", 0, true),
                new FriendPresence(UUID.randomUUID(), "BuilderBob", true, true, null, "Creative", 0, false)
        ));
    }

    @Override
    public CompletableFuture<Void> addFriend(String username) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeFriend(String username) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> sendChatMessage(String friendName, String message) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> joinWorld(FriendPresence friend) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> credits() {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    public List<String> inbox() {
        return List.of();
    }
}