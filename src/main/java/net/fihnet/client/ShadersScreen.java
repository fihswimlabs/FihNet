package net.fihnet.client;

import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ShadersScreen extends FihNetBaseScreen {
    private final Screen parent;
    private List<String> availableShaders = List.of();
    private String selectedShader = "";
    private String statusMessage = "No shaders loaded.";

    public ShadersScreen(Screen parent) {
        super(Component.literal("Shader Loader"));
        this.parent = parent;
    }

    @Override
    protected void initContent() {
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
                .bounds(contentLeft, contentTop, 100, 30).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh Shaders"), btn -> refreshShaders())
                .bounds(contentLeft + 120, contentTop, 120, 30).build());

        addRenderableWidget(Button.builder(Component.literal("Apply Shader"), btn -> applyShader())
                .bounds(contentLeft, contentTop + 50, 140, 36).build());

        addRenderableWidget(Button.builder(Component.literal("Disable Shaders"), btn -> disableShaders())
                .bounds(contentLeft + 160, contentTop + 50, 140, 36).build());

        refreshShaders();
    }

    private void refreshShaders() {
        // In a full implementation, this would scan the shaders folder
        // and populate the list with available shader packs.
        availableShaders = List.of("Sildur's Enhanced Default", "SEUS Renewed", "Continuum", "BSL", "Complementary");
        statusMessage = availableShaders.isEmpty() ? "No shaders found." : "";
    }

    private void applyShader() {
        if (selectedShader.isEmpty()) {
            statusMessage = "Select a shader first.";
            return;
        }
        statusMessage = "Applied: " + selectedShader;
    }

    private void disableShaders() {
        selectedShader = "";
        statusMessage = "Shaders disabled.";
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        sectionHeader(graphics, contentLeft, contentTop - 8, Component.literal("SHADER LOADER"));

        int listX = contentLeft;
        int listY = contentTop + 40;
        int listWidth = 300;
        int rowHeight = 28;

        graphics.fill(listX, listY, listX + listWidth, listY + Math.max(1, availableShaders.size()) * rowHeight + 4, 0xFF1A1F28);

        for (int i = 0; i < availableShaders.size(); i++) {
            String shader = availableShaders.get(i);
            int y = listY + i * rowHeight;
            boolean selected = shader.equals(selectedShader);

            drawRow(graphics, listX, y, listWidth, rowHeight, mouseX, mouseY, selected);
            graphics.text(font, Component.literal(shader), listX + 10, y + 8, TEXT_PRIMARY);
        }

        if (availableShaders.isEmpty()) {
            int panelCenterX = (panelLeft + panelLeft + PANEL_WIDTH) / 2;
            int panelCenterY = (panelTop + panelTop + PANEL_HEIGHT) / 2;
            loadingCaption(graphics, panelCenterX, panelCenterY,
                    Component.literal("No shaders loaded."));
        }

        if (!availableShaders.isEmpty() && statusMessage != null && !statusMessage.isEmpty()) {
            graphics.text(font, Component.literal(statusMessage), listX, listY + Math.max(1, availableShaders.size()) * rowHeight + 12, 0xFF77A0A6);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
