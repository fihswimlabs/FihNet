package net.fihnet.client.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FihNetService {
    CompletableFuture<List<FriendPresence>> friends();

    /** Adds a friend by username. Friendship is mutual on the FihNet server. */
    CompletableFuture<Void> addFriend(String username);

    CompletableFuture<Void> removeFriend(String username);

    CompletableFuture<Void> sendChatMessage(String friendName, String message);

    CompletableFuture<Void> joinWorld(FriendPresence friend);

    CompletableFuture<Integer> credits();

    /** Recent chat lines, oldest first, so the UI can show them. */
    List<String> inbox();
}