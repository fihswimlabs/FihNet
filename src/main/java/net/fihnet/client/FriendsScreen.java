package net.fihnet.client;

import net.fihnet.client.service.FriendPresence;
import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class FriendsScreen extends FihNetBaseScreen {
    private final Screen parent;
    private List<FriendPresence> friends = List.of();
    private EditBox chatInput;
    private int selectedFriend = -1;
    private String statusMessage = "Loading friends...";
    private FihNetBaseScreen activeScreen;

    public FriendsScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
    }

    @Override
    protected void initContent() {
        // Back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
                .bounds(contentLeft, contentTop, 100, 30).build());

        // Refresh button
        addRenderableWidget(Button.builder(Component.literal("Refresh"), btn -> refreshFriends())
                .bounds(contentLeft + 120, contentTop, 100, 30).build());

        // Chat input
        chatInput = new EditBox(font, contentLeft, contentBottom - 40, 300, 30, Component.literal("Chat message"));
        chatInput.setHint(Component.literal("Type a message to send to selected friend..."));
        addRenderableWidget(chatInput);

        // Send chat button
        addRenderableWidget(Button.builder(Component.literal("Send"), btn -> sendChat())
                .bounds(contentLeft + 310, contentBottom - 40, 100, 30).build());

        // Join world button
        addRenderableWidget(Button.builder(Component.literal("Join World"), btn -> joinWorld())
                .bounds(contentLeft + 420, contentBottom - 40, 100, 30).build());

        // Delay data load to the first tick so the screen is stable before
        // any async callbacks run, avoiding repeated recreate loops.
    }

    @Override
    protected void loadDeferredData() {
        refreshFriends();
    }

    private void refreshFriends() {
        statusMessage = "Loading friends...";
        selectedFriend = -1;
        activeScreen = this;
        FihNetClient.fihnetService.friends().thenAccept(result -> {
            if (activeScreen != this) return; // screen was replaced; ignore stale callback
            // Marshal to the client thread and assign state only - the render
            // pass picks it up. Rebuilding the screen here would clear widgets.
            minecraft.execute(() -> {
                if (activeScreen != this) return;
                friends = result;
                statusMessage = friends.isEmpty() ? "No friends online." : "";
            });
        });
    }

    /**
     * Fast index-based selection: clicking anywhere in the friend list row
     * selects that friend. (Improves perceived responsiveness.)
     */
    private int friendIndexAt(int mouseX, int mouseY) {
        int listX = contentLeft;
        int listY = contentTop + 60;
        int rowHeight = 34;

        // Reject clicks outside the list region or while loading.
        if (mouseX < listX || mouseX > listX + 400 || mouseY < listY || mouseY > listY + Math.max(1, friends.size()) * rowHeight) {
            return -1;
        }
        int idx = (int) ((mouseY - listY) / rowHeight);
        return (idx >= 0 && idx < friends.size()) ? idx : -1;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        if (handled) return true;

        int idx = friendIndexAt((int) event.x(), (int) event.y());
        if (idx >= 0) {
            selectedFriend = idx;
            return true;
        }
        return false;
    }

    private void sendChat() {
        if (selectedFriend < 0 || selectedFriend >= friends.size()) {
            statusMessage = "Select a friend first.";
            return;
        }
        if (!FihNetClient.config.isEnabled(Feature.SOCIAL_PRESENCE_CHAT)) {
            statusMessage = "Chat is disabled.";
            return;
        }
        FriendPresence friend = friends.get(selectedFriend);
        String message = chatInput.getValue();
        if (message.isEmpty()) return;

        // Clear the box after sending. No screen rebuild is performed, so the
        // player keeps their focus and can type the next message immediately.
        chatInput.setValue("");
        activeScreen = this;
        FihNetClient.fihnetService.sendChatMessage(friend.getUsername(), message).thenRun(() -> {
            if (activeScreen != this) return;
            minecraft.execute(() -> statusMessage = "Message sent to " + friend.getUsername() + ".");
        }).exceptionally(ex -> {
            if (activeScreen != this) return null;
            minecraft.execute(() -> statusMessage = "Failed to send message: " + ex.getMessage());
            return null;
        });
    }

    private void joinWorld() {
        if (selectedFriend < 0 || selectedFriend >= friends.size()) {
            statusMessage = "Select a friend first.";
            return;
        }
        if (!FihNetClient.config.isEnabled(Feature.SOCIAL_PRESENCE_JOIN_WORLD)) {
            statusMessage = "Join world is disabled.";
            return;
        }
        FriendPresence friend = friends.get(selectedFriend);
        if (!friend.isOnline()) {
            statusMessage = friend.getUsername() + " is not online.";
            return;
        }

        activeScreen = this;
        FihNetClient.fihnetService.joinWorld(friend).thenRun(() -> {
            if (activeScreen != this) return;
            minecraft.execute(() -> statusMessage = "Joining " + friend.getUsername() + "'s world...");
        }).exceptionally(ex -> {
            if (activeScreen != this) return null;
            minecraft.execute(() -> statusMessage = "Failed to join world: " + ex.getMessage());
            return null;
        });
    }

    @Override
    @SuppressWarnings("null")
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Friend list header
        sectionHeader(graphics, contentLeft, contentTop + 24, Component.literal("ONLINE FRIENDS"));

        int listX = contentLeft;
        int listY = contentTop + 50;
        int listWidth = 400;
        int rowHeight = 34;

        // Friend list background
        graphics.fill(listX, listY, listX + listWidth, listY + Math.max(1, friends.size()) * rowHeight + 4, 0xFF1A1F28);

        if (friends.isEmpty()) {
            int panelCenterX = (panelLeft + panelLeft + PANEL_WIDTH) / 2;
            int panelCenterY = (panelTop + panelTop + PANEL_HEIGHT) / 2;
            loadingCaption(graphics, panelCenterX, panelCenterY,
                    Component.literal(statusMessage != null ? statusMessage : "Loading friends..."));
        } else {
            for (int i = 0; i < friends.size(); i++) {
                FriendPresence friend = friends.get(i);
                int y = listY + i * rowHeight;
                boolean selected = (i == selectedFriend);
                boolean hovered = isHovered(listX, y, listWidth, rowHeight, mouseX, mouseY);

                drawRow(graphics, listX, y, listWidth, rowHeight, mouseX, mouseY, selected);

                // Online indicator
                int dotColor = friend.isOnline() ? 0xFF4CAF50 : 0xFF9E9E9E;
                graphics.fill(listX + 8, y + 8, listX + 16, y + 16, dotColor);

                // Friend name
                graphics.text(font, Component.literal(String.valueOf(friend.getUsername())), listX + 24, y + 8, TEXT_PRIMARY);

                // Server info
                String serverName = friend.getServerName() != null ? friend.getServerName() : "";
                String serverInfo = friend.isOnline() ? (friend.isModded() ? "Modded" : "Vanilla") + " - " + serverName : "Offline";
                graphics.text(font, Component.literal(serverInfo), listX + 24, y + 18, TEXT_SECONDARY);
            }
        }

        // Status message (only show when not loading)
        if (!friends.isEmpty() && statusMessage != null && !statusMessage.isEmpty()) {
            graphics.text(font, Component.literal(statusMessage), listX, listY + Math.max(1, friends.size()) * rowHeight + 12, 0xFFFF6B6B);
        }

        // Chat label
        graphics.text(font, Component.literal("FRIEND CHAT"), contentLeft, contentBottom - 60, ACCENT_COLOR);
        graphics.fill(contentLeft, contentBottom - 48, contentLeft + font.width(Component.literal("FRIEND CHAT")), contentBottom - 47, ACCENT_COLOR);
    }

    @Override
    public void onClose() {
        activeScreen = null;
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
