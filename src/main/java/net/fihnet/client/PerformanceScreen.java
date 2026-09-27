package net.fihnet.client;

import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PerformanceScreen extends FihNetBaseScreen {
    private final Screen parent;
    private boolean fpsLimiter = false;
    private boolean entityCulling = true;
    private boolean chunkLoading = true;
    private boolean smoothLighting = true;
    private boolean particles = true;
    private int fpsLimit = 120;

    public PerformanceScreen(Screen parent) {
        super(Component.literal("Performance"));
        this.parent = parent;
    }

    @Override
    protected void initContent() {
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
                .bounds(contentLeft, contentTop, 100, 30).build());

        int buttonX = contentLeft;
        int buttonY = contentTop + 50;
        int buttonWidth = 200;
        int buttonHeight = 32;
        int buttonSpacing = 10;

        // FPS Limiter toggle
        addRenderableWidget(Button.builder(toggleLabel("FPS Limiter", fpsLimiter), btn -> {
            fpsLimiter = !fpsLimiter;
            btn.setMessage(toggleLabel("FPS Limiter", fpsLimiter));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Entity Culling toggle
        addRenderableWidget(Button.builder(toggleLabel("Entity Culling", entityCulling), btn -> {
            entityCulling = !entityCulling;
            btn.setMessage(toggleLabel("Entity Culling", entityCulling));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Chunk Loading optimization
        addRenderableWidget(Button.builder(toggleLabel("Smart Chunk Loading", chunkLoading), btn -> {
            chunkLoading = !chunkLoading;
            btn.setMessage(toggleLabel("Smart Chunk Loading", chunkLoading));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Smooth Lighting
        addRenderableWidget(Button.builder(toggleLabel("Smooth Lighting", smoothLighting), btn -> {
            smoothLighting = !smoothLighting;
            btn.setMessage(toggleLabel("Smooth Lighting", smoothLighting));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // Particles
        addRenderableWidget(Button.builder(toggleLabel("Particles", particles), btn -> {
            particles = !particles;
            btn.setMessage(toggleLabel("Particles", particles));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());

        buttonY += buttonHeight + buttonSpacing;
        // FPS Limit adjustment
        addRenderableWidget(Button.builder(Component.literal("Set FPS Limit: " + fpsLimit), btn -> {
            fpsLimit = fpsLimit >= 144 ? 30 : fpsLimit + 30;
            btn.setMessage(Component.literal("Set FPS Limit: " + fpsLimit));
        }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());
    }

    private Component toggleLabel(String label, boolean enabled) {
        return Component.literal((enabled ? "ON  " : "OFF ") + label);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        sectionHeader(graphics, contentLeft, contentTop - 8, Component.literal("PERFORMANCE PROFILE"));
        graphics.text(font, Component.literal("Toggle optimizations to improve game performance."),
                contentLeft, contentBottom + 6, TEXT_SECONDARY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
