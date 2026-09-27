package net.fihnet.client.service;

import java.util.UUID;

public class Friend {
    private final UUID id;
    private final String username;
    private final String nickname;
    private final String serverAddress;
    private final String notes;
    private final long addedAt;
    private boolean blocked;
    
    public Friend(UUID id, String username, String nickname, String serverAddress, String notes) {
        this.id = id;
        this.username = username;
        this.nickname = nickname != null ? nickname : username;
        this.serverAddress = serverAddress;
        this.notes = notes;
        this.addedAt = System.currentTimeMillis();
        this.blocked = false;
    }
    
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getNickname() { return nickname; }
    public String getServerAddress() { return serverAddress; }
    public String getNotes() { return notes; }
    public long getAddedAt() { return addedAt; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    
    public net.fihnet.client.util.JsonObject toJson() {
        net.fihnet.client.util.JsonObject json = new net.fihnet.client.util.JsonObject();
        json.set("id", id.toString());
        json.set("username", username);
        json.set("nickname", nickname);
        json.set("serverAddress", serverAddress);
        json.set("notes", notes);
        json.set("blocked", blocked);
        json.set("addedAt", addedAt);
        return json;
    }
    
    public static Friend fromJson(net.fihnet.client.util.JsonObject json) {
        UUID id = UUID.fromString(json.getString("id"));
        String username = json.getString("username");
        String nickname = json.getString("nickname", null);
        String serverAddress = json.getString("serverAddress", null);
        String notes = json.getString("notes", null);
        Friend friend = new Friend(id, username, nickname, serverAddress, notes);
        if (json.containsKey("blocked")) {
            friend.setBlocked(json.getBoolean("blocked"));
        }
        return friend;
    }
    
    @Override
    public String toString() {
        return nickname + " (" + username + ")";
    }
}
