package net.fihnet.client;

import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * Opens and closes the FihNet menu from anywhere in the game.
 *
 * <p>Vanilla only ticks key bindings while no screen is open, so the menu used to be
 * unreachable (and unclosable) whenever a screen was showing. FihNetClient intercepts
 * the binding through ScreenKeyboardEvents, and both paths funnel through
 * {@link #toggle(Minecraft)} so the same key always opens and closes.
 */
public final class FihNetMenu {
    private FihNetMenu() {}

    /** Opens the menu, or closes it when a FihNet screen is already showing. */
    public static void toggle(Minecraft client) {
        if (client == null) {
            return;
        }
        Screen current = client.screen;
        if (current instanceof FihNetBaseScreen base) {
            base.onClose();
            return;
        }
        // The menu itself never requires a FihNet account. Only the friends
        // features do, and they ask for sign-in from inside the menu.
        client.setScreen(new FihNetScreen(current));
    }

    /** True when the pressed key matches the bound FihNet menu key, so rebinds work. */
    public static boolean isToggleKey(KeyEvent event) {
        KeyMapping mapping = FihNetClient.openMenuKey();
        return mapping != null && mapping.matches(event);
    }
}