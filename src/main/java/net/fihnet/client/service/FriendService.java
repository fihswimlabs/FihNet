package net.fihnet.client.service;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FriendService {
    List<Friend> getFriends();
    Friend addFriend(String username);
    boolean removeFriend(UUID friendId);
    Friend getFriend(UUID friendId);
    Friend getFriendByUsername(String username);
    boolean updateFriendInfo(UUID friendId, String nickname, String serverAddress, String notes);
    boolean toggleBlock(UUID friendId);
    
    CompletableFuture<List<FriendPresence>> getFriendPresences();
    CompletableFuture<FriendPresence> getFriendPresence(UUID friendId);
    void refreshPresences();
    
    CompletableFuture<Void> sendMessage(UUID friendId, String message);
    List<ChatMessage> getChatHistory(UUID friendId);
    void addMessageListener(ChatMessageListener listener);
    
    CompletableFuture<Boolean> joinFriendWorld(Friend friend);
    boolean canJoinWorld(Friend friend);
    
    void load();
    void save();
}
