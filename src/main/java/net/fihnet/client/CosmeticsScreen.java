package net.fihnet.client;

import net.fihnet.client.service.AppearanceAsset;
import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CosmeticsScreen extends FihNetBaseScreen {
    private final Screen parent;
    private List<AppearanceAsset> catalog = List.of();
    private int credits = 0;
    private String statusMessage = "Loading catalog...";
    private FihNetBaseScreen activeScreen;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosmetics & Capes"));
        this.parent = parent;
    }

    @Override
    protected void initContent() {
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
                .bounds(contentLeft, contentTop, 100, 30).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh"), btn -> refreshCatalog())
                .bounds(contentLeft + 120, contentTop, 100, 30).build());

        // Delay data load to the first tick so the screen is stable before
        // any async callbacks run, avoiding repeated recreate loops.
    }

    @Override
    protected void loadDeferredData() {
        refreshCatalog();
    }

    private void refreshCatalog() {
        statusMessage = "Loading catalog...";
        activeScreen = this;
        FihNetClient.appearanceService.catalog().thenAccept(result -> {
            if (activeScreen != this) return; // screen was replaced; ignore stale callback
            catalog = result;
            statusMessage = catalog.isEmpty() ? "No items available." : "";
            minecraft.execute(this::requestRepaint);
        });

        FihNetClient.appearanceService.credits().thenAccept(result -> {
            if (activeScreen != this) return; // screen was replaced; ignore stale callback
            credits = result;
            minecraft.execute(this::requestRepaint);
        });
    }

    private void purchaseAndEquip(AppearanceAsset asset) {
        if (credits < asset.creditCost()) {
            statusMessage = "Not enough credits! (" + credits + "/" + asset.creditCost() + ")";
            requestRepaint();
            return;
        }
        activeScreen = this;
        FihNetClient.appearanceService.equip(asset.id()).thenRun(() -> {
            if (activeScreen != this) return;
            statusMessage = "Equipped: " + asset.displayName();
            credits -= asset.creditCost();
            minecraft.execute(this::requestRepaint);
        }).exceptionally(ex -> {
            if (activeScreen != this) return null;
            statusMessage = "Failed to equip: " + ex.getMessage();
            minecraft.execute(this::requestRepaint);
            return null;
        });
    }

    @Override
    @SuppressWarnings("null")
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        sectionHeader(graphics, contentLeft, contentTop - 8, Component.literal("COSMETICS & CAPES"));
        graphics.text(font, Component.literal("Credits: " + credits), contentLeft, contentTop + 4, 0xFF61D4C3);

        int listX = contentLeft;
        int listY = contentTop + 36;
        int listWidth = 450;
        int rowHeight = 40;

        // Background fill for the whole list region
        graphics.fill(listX, listY, listX + listWidth, listY + Math.max(1, catalog.size()) * rowHeight + 4, 0xFF1A1F28);

        if (catalog.isEmpty()) {
            int panelCenterX = (panelLeft + panelLeft + PANEL_WIDTH) / 2;
            int panelCenterY = (panelTop + panelTop + PANEL_HEIGHT) / 2;
            loadingCaption(graphics, panelCenterX, panelCenterY,
                    Component.literal(statusMessage != null ? statusMessage : "Loading catalog..."));
        } else {
            for (int i = 0; i < catalog.size(); i++) {
                AppearanceAsset asset = catalog.get(i);
                int y = listY + i * rowHeight;
                boolean hovered = isHovered(listX, y, listWidth, rowHeight, mouseX, mouseY);

                drawRow(graphics, listX, y, listWidth, rowHeight, mouseX, mouseY, false);

                // Asset type indicator
                String typeStr = asset.type().name();
                graphics.text(font, Component.literal(typeStr != null ? typeStr : ""), listX + 10, y + 8, 0xFF77A0A6);

                // Asset name
                graphics.text(font, Component.literal(String.valueOf(asset.displayName())), listX + 10, y + 20, TEXT_PRIMARY);

                // Credit cost
                graphics.text(font, Component.literal("Cost: " + asset.creditCost()), listX + 200, y + 20, 0xFFFFD700);

                // Equip button (hover-aware)
                int btnX = listX + 330;
                int btnY = y + 8;
                int btnW = 80;
                int btnH = 24;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, BUTTON_BG);
                tintIfHovered(graphics, btnX, btnY, btnW, btnH, mouseX, mouseY);
                graphics.fill(btnX, btnY, btnX + 1, btnY + btnH, BUTTON_BORDER);
                graphics.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, BUTTON_BORDER);
                graphics.text(font, Component.literal("Equip"), btnX + 4, btnY + 6, TEXT_PRIMARY);
            }
        }

        // Status message only when not loading
        if (!catalog.isEmpty() && statusMessage != null && !statusMessage.isEmpty()) {
            graphics.text(font, Component.literal(statusMessage), listX, listY + Math.max(1, catalog.size()) * rowHeight + 12, 0xFFFF6B6B);
        }
    }

    @Override
    public void onClose() {
        activeScreen = null;
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
