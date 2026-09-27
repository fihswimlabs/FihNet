package net.fihnet.client.service;

import java.util.UUID;

public class ChatMessage {
    private final UUID id;
    private final UUID friendId;
    private final String sender;
    private final String message;
    private final long timestamp;
    private final MessageType type;
    
    public enum MessageType {
        SENT,
        RECEIVED,
        SYSTEM
    }
    
    public ChatMessage(UUID friendId, String sender, String message, MessageType type) {
        this.id = UUID.randomUUID();
        this.friendId = friendId;
        this.sender = sender;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.type = type;
    }
    
    public UUID getId() { return id; }
    public UUID getFriendId() { return friendId; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public MessageType getType() { return type; }
    
    @Override
    public String toString() {
        String time = java.text.SimpleDateFormat.getTimeInstance().format(new java.util.Date(timestamp));
        return "[" + time + "] " + sender + ": " + message;
    }
}
