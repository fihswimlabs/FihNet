package net.fihnet.client.service;

import net.fihnet.client.util.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FriendStorage {
    private static final Logger LOG = LogManager.getLogger("FihNet/FriendStorage");
    private final Path baseStoragePath;
    
    private final Map<UUID, FriendList> friendLists = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> dirtyFlags = new ConcurrentHashMap<>();
    
    public FriendStorage() {
        this.baseStoragePath = FabricLoader.getInstance().getConfigDir().resolve("fihnet/users");
    }
    
    private static class FriendList {
        UUID userId;
        List<Friend> friends = new ArrayList<>();
        long lastUpdated = System.currentTimeMillis();
    }
    
    public synchronized List<Friend> loadForUser(UUID userId) {
        FriendList cached = friendLists.get(userId);
        if (cached != null) {
            return new ArrayList<>(cached.friends);
        }
        
        Path userDir = baseStoragePath.resolve(userId.toString());
        Path friendsFile = userDir.resolve("friends.json");
        
        FriendList friendList = new FriendList();
        friendList.userId = userId;
        
        if (!Files.exists(friendsFile)) {
            LOG.info("No friends file for user {}, creating empty list", userId);
            friendLists.put(userId, friendList);
            dirtyFlags.put(userId, false);
            return friendList.friends;
        }
        
        try {
            String content = Files.readString(friendsFile);
            JsonObject json = JsonObject.fromString(content);
            
            if (json.containsKey("friends") && json.get("friends") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> friendDataList = (List<Map<String, Object>>) json.get("friends");
                
                for (Map<String, Object> data : friendDataList) {
                    try {
                        JsonObject friendJson = new JsonObject();
                        friendJson.getData().putAll(data);
                        Friend friend = Friend.fromJson(friendJson);
                        friendList.friends.add(friend);
                    } catch (Exception e) {
                        LOG.warn("Failed to load friend entry for user {}: {}", userId, e.getMessage());
                    }
                }
            }
            friendList.lastUpdated = json.getLong("lastUpdated", System.currentTimeMillis());
            LOG.info("Loaded {} friends for user {}", friendList.friends.size(), userId);
        } catch (IOException e) {
            LOG.error("Failed to load friends for user {}: {}", userId, e.getMessage());
        }
        
        friendLists.put(userId, friendList);
        dirtyFlags.put(userId, false);
        return friendList.friends;
    }
    
    public synchronized void saveForUser(UUID userId) {
        FriendList friendList = friendLists.get(userId);
        if (friendList == null || !dirtyFlags.getOrDefault(userId, false)) {
            return;
        }
        
        try {
            Path userDir = baseStoragePath.resolve(userId.toString());
            Files.createDirectories(userDir);
            Path friendsFile = userDir.resolve("friends.json");
            JsonObject root = new JsonObject();
            List<Map<String, Object>> friendDataList = new ArrayList<>();
            for (Friend friend : friendList.friends) {
                friendDataList.add(friend.toJson().getData());
            }
            root.set("friends", friendDataList);
            root.set("lastUpdated", friendList.lastUpdated);
            root.set("version", 1);
            Files.writeString(friendsFile, root.toString());
            LOG.debug("Saved {} friends for user {}", friendList.friends.size(), userId);
        } catch (IOException e) {
            LOG.error("Failed to save friends for user {}: {}", userId, e.getMessage());
        }
    }
    
        
    public synchronized Friend addFriend(UUID userId, String username) {
        FriendList friendList = friendLists.computeIfAbsent(userId, k -> {
            FriendList fl = new FriendList();
            fl.userId = userId;
            return fl;
        });
        Friend friend = new Friend(UUID.randomUUID(), username, null, null, null);
        friendList.friends.add(friend);
        dirtyFlags.put(userId, true);
        saveForUser(userId);
        LOG.info("User {} added friend: {}", userId, username);
        return friend;
    }
    
    public synchronized boolean removeFriend(UUID userId, UUID friendId) {
        FriendList friendList = friendLists.get(userId);
        if (friendList == null) return false;
        for (Iterator<Friend> it = friendList.friends.iterator(); it.hasNext();) {
            Friend friend = it.next();
            if (friend.getId().equals(friendId)) {
                it.remove();
                dirtyFlags.put(userId, true);
                saveForUser(userId);
                LOG.info("User {} removed friend: {}", userId, friend.getUsername());
                return true;
            }
        }
        return false;
    }
    
    public List<Friend> getFriends(UUID userId) {
        FriendList friendList = friendLists.get(userId);
        if (friendList == null) {
            loadForUser(userId);
            friendList = friendLists.get(userId);
        }
        return friendList != null ? new ArrayList<>(friendList.friends) : List.of();
    }
    
    public Friend getFriend(UUID userId, UUID friendId) {
        for (Friend friend : getFriends(userId)) {
            if (friend.getId().equals(friendId)) {
                return friend;
            }
        }
        return null;
    }
    
    public Friend getFriendByUsername(UUID userId, String username) {
        for (Friend friend : getFriends(userId)) {
            if (friend.getUsername().equalsIgnoreCase(username)) {
                return friend;
            }
        }
        return null;
    }
    
    public synchronized boolean updateFriendInfo(UUID userId, UUID friendId, String nickname, String serverAddress, String notes) {
        FriendList friendList = friendLists.get(userId);
        if (friendList == null) return false;
        for (int i = 0; i < friendList.friends.size(); i++) {
            Friend f = friendList.friends.get(i);
            if (f.getId().equals(friendId)) {
                Friend updated = new Friend(f.getId(), f.getUsername(), nickname, serverAddress, notes);
                if (f.isBlocked()) updated.setBlocked(true);
                friendList.friends.set(i, updated);
                dirtyFlags.put(userId, true);
                saveForUser(userId);
                return true;
            }
        }
        return false;
    }
    
    public synchronized boolean toggleBlock(UUID userId, UUID friendId) {
        FriendList friendList = friendLists.get(userId);
        if (friendList == null) return false;
        for (Friend friend : friendList.friends) {
            if (friend.getId().equals(friendId)) {
                friend.setBlocked(!friend.isBlocked());
                dirtyFlags.put(userId, true);
                saveForUser(userId);
                return true;
            }
        }
        return false;
    }
    
    public boolean hasFriendsFile(UUID userId) {
        Path friendsFile = baseStoragePath.resolve(userId.toString()).resolve("friends.json");
        return Files.exists(friendsFile);
    }
    
    public List<UUID> getAllStoredUsers() {
        List<UUID> users = new ArrayList<>();
        if (!Files.exists(baseStoragePath)) return users;
        try {
            Files.list(baseStoragePath).forEach(path -> {
                if (Files.isDirectory(path)) {
                    try { users.add(UUID.fromString(path.getFileName().toString())); }
                    catch (IllegalArgumentException e) {}
                }
            });
        } catch (IOException e) {
            LOG.warn("Failed to list stored users: {}", e.getMessage());
        }
                return users;
    }
}

