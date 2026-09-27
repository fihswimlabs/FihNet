package net.fihnet.client.service;

import net.fihnet.client.FihNetClient;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalFriendService implements FriendService {
    private static final Logger LOG = LogManager.getLogger("FihNet/FriendService");
    private static final long PRESENCE_CHECK_INTERVAL = 30_000;
    
    private final FriendStorage storage;
    private final AuthService authService;
    private final ExecutorService executor;
    private final Map<UUID, Map<UUID, FriendPresence>> presenceCache = new HashMap<>();
    private final Map<UUID, Map<UUID, List<ChatMessage>>> chatHistories = new HashMap<>();
    private final List<ChatMessageListener> messageListeners = new ArrayList<>();
    private long lastPresenceCheck = 0;
    
    public LocalFriendService() {
        this.storage = new FriendStorage();
        this.authService = FihNetClient.authService;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "FihNet-FriendService");
            t.setDaemon(true);
            return t;
        });
        
        authService.addAuthListener(new AuthService.AuthListener() {
            @Override
            public void onLoggedIn(String username, UUID userId) {
                LOG.info("Friend service notified: user logged in: {}", username);
                onUserLoggedIn();
            }
            
            @Override
            public void onLoggedOut() {
                LOG.info("Friend service notified: user logged out");
                onUserLoggedOut();
            }
            
            @Override
            public void onAuthError(String message) {
                LOG.warn("Auth error in friend service: {}", message);
            }
        });
    }
    
    @Override
    public List<Friend> getFriends() {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            LOG.warn("Cannot get friends - not logged in");
            return List.of();
        }
        return storage.getFriends(userId);
    }
    
    @Override
    public Friend addFriend(String username) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("Not logged in to FihNet");
        }
        return storage.addFriend(userId, username);
    }
    
    @Override
    public boolean removeFriend(UUID friendId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("Not logged in to FihNet");
        }
        return storage.removeFriend(userId, friendId);
    }
    
    @Override
    public Friend getFriend(UUID friendId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return storage.getFriend(userId, friendId);
    }
    
    @Override
    public Friend getFriendByUsername(String username) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return storage.getFriendByUsername(userId, username);
    }
    
    @Override
    public boolean updateFriendInfo(UUID friendId, String nickname, String serverAddress, String notes) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return storage.updateFriendInfo(userId, friendId, nickname, serverAddress, notes);
    }
    
    @Override
    public boolean toggleBlock(UUID friendId) {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return storage.toggleBlock(userId, friendId);
    }
    
    
    @Override
    public CompletableFuture<List<FriendPresence>> getFriendPresences() {
        return CompletableFuture.supplyAsync(() -> {
            UUID userId = getCurrentUserId();
            if (userId == null) {
                LOG.warn("Cannot get presences - not logged in");
                return List.of();
            }
            
            List<FriendPresence> presences = new ArrayList<>();
            List<Friend> friends = storage.getFriends(userId);
            
            for (Friend friend : friends) {
                if (friend.isBlocked()) continue;
                
                Map<UUID, FriendPresence> userCache = presenceCache.get(userId);
                if (userCache != null) {
                    FriendPresence cached = userCache.get(friend.getId());
                    if (cached != null) {
                        presences.add(cached);
                        continue;
                    }
                }
                
                FriendPresence presence = checkFriendPresence(userId, friend);
                presences.add(presence);
            }
            
            return presences;
        }, executor);
    }
    
    @Override
    public CompletableFuture<FriendPresence> getFriendPresence(UUID friendId) {
        return CompletableFuture.supplyAsync(() -> {
            UUID userId = getCurrentUserId();
            if (userId == null) return null;
            
            Map<UUID, FriendPresence> userCache = presenceCache.get(userId);
            if (userCache != null) {
                FriendPresence cached = userCache.get(friendId);
                if (cached != null) return cached;
            }
            
            Friend friend = storage.getFriend(userId, friendId);
            if (friend == null) return null;
            
            return checkFriendPresence(userId, friend);
        }, executor);
    }
    
    @Override
    public void refreshPresences() {
        UUID userId = getCurrentUserId();
        if (userId == null) return;
        
        long now = System.currentTimeMillis();
        if (now - lastPresenceCheck < PRESENCE_CHECK_INTERVAL) return;
        
        lastPresenceCheck = now;
        List<Friend> friends = storage.getFriends(userId);
        Map<UUID, FriendPresence> userCache = new HashMap<>();
        
        for (Friend friend : friends) {
            if (friend.isBlocked()) continue;
            FriendPresence presence = checkFriendPresence(userId, friend);
            userCache.put(friend.getId(), presence);
        }
        
        presenceCache.put(userId, userCache);
    }
    
    private FriendPresence checkFriendPresence(UUID userId, Friend friend) {
        Minecraft client = Minecraft.getInstance();
        
        if (client.player != null && client.player.connection != null) {
            // Friend might be on same server - check later
        }
        
        return FriendPresence.offline(friend);
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(UUID friendId, String message) {
        return CompletableFuture.runAsync(() -> {
            UUID userId = getCurrentUserId();
            if (userId == null) throw new IllegalStateException("Not logged in");
            
            ChatMessage chatMessage = new ChatMessage(friendId, null, message, ChatMessage.MessageType.SENT);
            Map<UUID, List<ChatMessage>> userChats = chatHistories.computeIfAbsent(userId, k -> new HashMap<>());
            userChats.computeIfAbsent(friendId, k -> new ArrayList<>()).add(chatMessage);
            
            for (ChatMessageListener listener : messageListeners) {
                try { listener.onMessageSent(friendId, chatMessage); }
                catch (Exception e) { LOG.warn("Error notifying listener: {}", e.getMessage()); }
            }
        }, executor);
    }
    
    @Override
    public List<ChatMessage> getChatHistory(UUID friendId) {
        UUID userId = getCurrentUserId();
        if (userId == null) return List.of();
        Map<UUID, List<ChatMessage>> userChats = chatHistories.get(userId);
        if (userChats == null) return List.of();
        return new ArrayList<>(userChats.getOrDefault(friendId, List.of()));
    }
    
    @Override
    public void addMessageListener(ChatMessageListener listener) {
        messageListeners.add(listener);
    }
    
    @Override
    public CompletableFuture<Boolean> joinFriendWorld(Friend friend) {
        return CompletableFuture.supplyAsync(() -> {
            if (friend == null || !canJoinWorld(friend)) return false;
            LOG.info("Attempting to join world of friend: {}", friend.getUsername());
            return true;
        }, executor);
    }
    
    @Override
    public boolean canJoinWorld(Friend friend) {
        if (friend == null) return false;
        return friend.getServerAddress() != null && !friend.getServerAddress().isEmpty();
    }
    
    @Override
    public void load() {
        UUID userId = getCurrentUserId();
        if (userId != null) {
            storage.loadForUser(userId);
            LOG.info("Loaded friends for user {}", userId);
        }
    }
    
    @Override
    public void save() {
        UUID userId = getCurrentUserId();
        if (userId != null) {
            storage.saveForUser(userId);
        }
    }
    
    public void onUserLoggedIn() {
        LOG.info("Friend service: user logged in, loading data");
        load();
        refreshPresences();
    }
    
    public void onUserLoggedOut() {
        LOG.info("Friend service: user logged out, clearing cache");
        presenceCache.clear();
        chatHistories.clear();
    }
    
    private UUID getCurrentUserId() {
        return authService.getUserId();
    }
}

