package net.fihnet.client;

import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SkinsScreen extends FihNetBaseScreen {
    private final Screen parent;

    public SkinsScreen(Screen parent) {
        super(Component.literal("Custom Skins"));
        this.parent = parent;
    }

    @Override
    protected void initContent() {
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
                .bounds(contentLeft, contentTop, 100, 30).build());

        addRenderableWidget(Button.builder(Component.literal("Upload Skin"), btn -> uploadSkin())
                .bounds(contentLeft, contentTop + 50, 200, 36).build());

        addRenderableWidget(Button.builder(Component.literal("Apply Default Skin"), btn -> applyDefaultSkin())
                .bounds(contentLeft, contentTop + 100, 200, 36).build());

        addRenderableWidget(Button.builder(Component.literal("Remove Custom Skin"), btn -> removeCustomSkin())
                .bounds(contentLeft, contentTop + 150, 200, 36).build());
    }

    private void uploadSkin() {
        // In a full implementation, this would open a file picker dialog
        // and upload the selected PNG to the FihNet service.
        // For now, it's a placeholder.
    }

    private void applyDefaultSkin() {
        // Reverts to the default Minecraft skin
    }

    private void removeCustomSkin() {
        // Removes the custom skin and reverts to default
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        sectionHeader(graphics, contentLeft, contentTop - 8, Component.literal("CUSTOM SKIN LOADER"));
        graphics.text(font, Component.literal("Upload a PNG skin to apply it to your character."),
                contentLeft, contentTop + 160, TEXT_SECONDARY);
        graphics.text(font, Component.literal("Other players using FihNet will see your custom skin."),
                contentLeft, contentTop + 180, TEXT_SECONDARY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
