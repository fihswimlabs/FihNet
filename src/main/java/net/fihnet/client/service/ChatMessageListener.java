package net.fihnet.client.service;

import java.util.UUID;

public interface ChatMessageListener {
    void onMessageReceived(UUID friendId, ChatMessage message);
    void onMessageSent(UUID friendId, ChatMessage message);
}
