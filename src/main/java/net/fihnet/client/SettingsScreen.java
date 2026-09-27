package net.fihnet.client;

import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends FihNetBaseScreen {
    private final Screen parent;
    private boolean showFihNetOverlay = true;
    private boolean playSounds = true;
    private boolean animations = true;
    private boolean notifications = true;
    private int overlayOpacity = 80;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Settings"));
        this.parent = parent;
    }

    @Override
    protected void initContent() {
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
                .bounds(contentLeft, contentTop, 100, 30).build());

        int buttonX = contentLeft;
        int buttonY = contentTop + 50;
        int buttonWidth = 220;
        int buttonHeight = 32;
        int buttonSpacing = 10;

        // FihNet overlay toggle
        addRenderableWidget(Button.builder(toggleLabel("Show FihNet Overlay", showFihNetOverlay), btn -> {
            showFihNetOverlay = !showFihNetOverlay;
            btn.setMessage(toggleLabel("Show FihNet Overlay", showFihNetOverlay));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Play sounds toggle
        addRenderableWidget(Button.builder(toggleLabel("Play UI Sounds", playSounds), btn -> {
            playSounds = !playSounds;
            btn.setMessage(toggleLabel("Play UI Sounds", playSounds));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Animations toggle
        addRenderableWidget(Button.builder(toggleLabel("UI Animations", animations), btn -> {
            animations = !animations;
            btn.setMessage(toggleLabel("UI Animations", animations));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Notifications toggle
        addRenderableWidget(Button.builder(toggleLabel("Notifications", notifications), btn -> {
            notifications = !notifications;
            btn.setMessage(toggleLabel("Notifications", notifications));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Overlay opacity
        addRenderableWidget(Button.builder(Component.literal("Overlay Opacity: " + overlayOpacity + "%"), btn -> {
            overlayOpacity = overlayOpacity >= 100 ? 20 : overlayOpacity + 20;
            btn.setMessage(Component.literal("Overlay Opacity: " + overlayOpacity + "%"));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Reset settings
        addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), btn -> resetDefaults()).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());
    }

    private void resetDefaults() {
        showFihNetOverlay = true;
        playSounds = true;
        animations = true;
        notifications = true;
        overlayOpacity = 80;
        minecraft.setScreen(new SettingsScreen(parent));
    }

    private Component toggleLabel(String label, boolean enabled) {
        return Component.literal((enabled ? "ON  " : "OFF ") + label);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        sectionHeader(graphics, contentLeft, contentTop - 8, Component.literal("FihNet SETTINGS"));
        graphics.text(font, Component.literal("Customize your FihNet experience."),
                contentLeft, contentBottom + 6, TEXT_SECONDARY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
