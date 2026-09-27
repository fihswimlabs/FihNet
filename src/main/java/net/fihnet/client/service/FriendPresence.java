package net.fihnet.client.service;

import java.util.UUID;

public class FriendPresence {
    private final UUID friendId;
    private final String username;
    private final boolean online;
    private final boolean inGame;
    private final String serverAddress;
    private final String serverName;
    private final int playerCount;
    private final boolean modded;
    private final long lastSeen;
    
    public FriendPresence(UUID friendId, String username, boolean online, boolean inGame,
                         String serverAddress, String serverName, int playerCount, boolean modded) {
        this.friendId = friendId;
        this.username = username;
        this.online = online;
        this.inGame = inGame;
        this.serverAddress = serverAddress;
        this.serverName = serverName;
        this.playerCount = playerCount;
        this.modded = modded;
        this.lastSeen = System.currentTimeMillis();
    }
    
    public UUID getFriendId() { return friendId; }
    public String getUsername() { return username; }
    public boolean isOnline() { return online; }
    public boolean isInGame() { return inGame; }
    public String getServerAddress() { return serverAddress; }
    public String getServerName() { return serverName; }
    public int getPlayerCount() { return playerCount; }
    public boolean isModded() { return modded; }
    public long getLastSeen() { return lastSeen; }
    
    public boolean canJoin() {
        return online && (serverAddress != null && !serverAddress.isEmpty() || inGame);
    }
    
    public static FriendPresence offline(Friend friend) {
        return new FriendPresence(
            friend.getId(),
            friend.getUsername(),
            false,
            false,
            null,
            null,
            0,
            false
        );
    }
    
    @Override
    public String toString() {
        if (online) {
            if (inGame && serverName != null) {
                return username + " is playing on " + serverName + 
                       " (" + playerCount + " players)" + (modded ? " [Modded]" : "");
            } else if (inGame) {
                return username + " is in-game";
            }
            return username + " is online";
        }
        return username + " is offline";
    }
}
