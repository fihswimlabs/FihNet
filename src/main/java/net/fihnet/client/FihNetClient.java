package net.fihnet.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fihnet.client.service.AuthService;
import net.fihnet.client.service.FriendService;
import net.fihnet.client.service.FihNetService;
import net.fihnet.client.service.LocalAppearanceService;
import net.fihnet.client.service.LocalFriendService;
import net.fihnet.client.service.RemoteAuthService;
import net.fihnet.client.service.RemoteFihNetService;
import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public final class FihNetClient implements ClientModInitializer {
    public static final String MOD_ID = "fihnet";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);
    public static FeatureConfig config;
    public static AuthService authService;
    public static FriendService friendService;
    public static LocalAppearanceService appearanceService;
    public static FihNetService fihnetService;
    private static RemoteFihNetService remoteService;
    private static KeyMapping openMenuKey;

    /** Exposed so FihNetMenu can match key events against the bound key. */
    public static KeyMapping openMenuKey() {
        return openMenuKey;
    }

    @Override
    public void onInitializeClient() {
        config = new FeatureConfig(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());

        // Real FihNet account backend, talking to the server in config/fihnet/server.txt.
        authService = new RemoteAuthService();
        remoteService = new RemoteFihNetService(() -> authService == null ? "" : authService.getAuthToken());
        fihnetService = remoteService;
        remoteService.start();

        friendService = new LocalFriendService();
        appearanceService = new LocalAppearanceService();

        if (authService.isLoggedIn()) {
            LOG.info("FihNet session restored for {}", authService.getUsername());
            friendService.load();
        } else {
            LOG.info("Not signed in to FihNet - press the menu key to sign in");
        }

        authService.addAuthListener(new AuthService.AuthListener() {
            @Override
            public void onLoggedIn(String username, java.util.UUID userId) {
                LOG.info("FihNet signed in as {}", username);
                friendService.load();
            }

            @Override
            public void onLoggedOut() {
                LOG.info("FihNet signed out");
            }

            @Override
            public void onAuthError(String message) {
                LOG.warn("FihNet auth error: {}", message);
            }
        });

        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fihnet.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyMapping.Category.MISC
        ));

        // Path 1 - no screen open: the normal keybind click queue. Also the place
        // where presence heartbeats are sent to the FihNet server.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                FihNetMenu.toggle(client);
            }
            remoteService.tickPresence(client);
        });

        // Path 2 - a screen is open: vanilla does not tick key bindings then, so
        // intercept the bound key through the screen events. This is what makes the
        // menu reachable (and closable) from the title screen, pause menu, chat, etc.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                    if (!FihNetMenu.isToggleKey(event)) {
                        return true;
                    }
                    // Do not steal the key while typing in a vanilla text field.
                    if (!(s instanceof FihNetBaseScreen) && hasFocusedTextInput(s)) {
                        return true;
                    }
                    FihNetMenu.toggle(client);
                    return false;
                }));

        LOG.info("FihNet initialized. Auth: " + (authService.isLoggedIn() ? "signed in" : "signed out"));
    }

    private static boolean hasFocusedTextInput(Screen screen) {
        return screen.getFocused() instanceof EditBox;
    }
}