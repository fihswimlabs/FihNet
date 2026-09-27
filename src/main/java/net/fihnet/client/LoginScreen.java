package net.fihnet.client;

import net.fihnet.client.service.AuthService;
import net.fihnet.client.service.AuthService.AuthResult;
import net.fihnet.client.ui.FihNetBaseScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * Dedicated FihNet sign-in screen. This account is completely separate from the
 * player's Mojang/Microsoft account.
 *
 * <p>Important: this screen never calls {@link FihNetBaseScreen#requestRepaint()}.
 * Rebuilding the screen would recreate the {@link EditBox} widgets and wipe any
 * text the player has typed (and reset focus), which made "Show password" look
 * like it reset the form. Widgets are laid out once in {@code initContent()} and
 * every dynamic value (labels, masked text, status line) is read live at render
 * time instead.
 */
public final class LoginScreen extends FihNetBaseScreen {
    private static final int FORM_W = 340;
    private static final int FIELD_H = 30;
    private static final int PAD = 20;
    private static final int SHOW_BTN_W = 44;
    private static final int BTN_H = 32;
    private static final int GAP = 10;

    private final Screen parent;
    private final AuthService authService;

    private EditBox usernameInput;
    private EditBox passwordInput;
    private EditBox emailInput;
    private Button loginButton;
    private Button registerButton;
    private Button cancelButton;
    private Button showPasswordBtn;

    private boolean isRegisterMode = false;
    private String statusMessage = "";
    private int statusMessageType = 0;
    private boolean isLoading = false;
    private boolean showPassword = false;

    // Layout cache, recomputed from the base screen's content bounds each frame
    // so the labels can never drift away from the widgets they describe.
    private int formX;
    private int formY;
    private int formH;
    private int fieldW;
    private int passwordW;
    private int usernameLabelY;
    private int usernameBoxY;
    private int passwordLabelY;
    private int passwordBoxY;
    private int emailLabelY;
    private int emailBoxY;
    private int primaryBtnY;
    private int secondaryBtnY;

    public LoginScreen(Screen parent) {
        super(Component.literal("FihNet Login"));
        this.parent = parent;
        this.authService = FihNetClient.authService;
    }

    private void computeLayout() {
        formH = isRegisterMode ? 260 : 210;
        int contentCenter = (contentLeft + contentRight) / 2;
        formX = Math.max(contentLeft, contentCenter - FORM_W / 2);
        formY = contentTop + 8;

        fieldW = FORM_W - PAD * 2;
        passwordW = fieldW - SHOW_BTN_W - 6;

        usernameLabelY = formY + 14;
        usernameBoxY = formY + 26;
        passwordLabelY = formY + 70;
        passwordBoxY = formY + 82;
        emailLabelY = formY + 126;
        emailBoxY = formY + 138;

        primaryBtnY = formY + formH - 84;
        secondaryBtnY = primaryBtnY + BTN_H + 8;
    }

    @Override
    protected void initContent() {
        computeLayout();

        usernameInput = new EditBox(font, formX + PAD, usernameBoxY, fieldW, FIELD_H,
                Component.literal("Username"));
        usernameInput.setHint(Component.literal("FihNet username"));
        usernameInput.setMaxLength(32);
        addRenderableWidget(usernameInput);

        passwordInput = new EditBox(font, formX + PAD, passwordBoxY, passwordW, FIELD_H,
                Component.literal("Password"));
        passwordInput.setHint(Component.literal("Password"));
        passwordInput.setMaxLength(64);
        // Formatter reads showPassword live, so toggling needs no screen rebuild.
        passwordInput.addFormatter((text, firstIndex) ->
                FormattedCharSequence.forward(showPassword ? text : mask(text.length()), Style.EMPTY));
        addRenderableWidget(passwordInput);

        showPasswordBtn = Button.builder(Component.literal(showPassword ? "Hide" : "Show"), btn -> {
            showPassword = !showPassword;
            btn.setMessage(Component.literal(showPassword ? "Hide" : "Show"));
        }).bounds(formX + PAD + passwordW + 6, passwordBoxY, SHOW_BTN_W, FIELD_H).build();
        addRenderableWidget(showPasswordBtn);

        emailInput = new EditBox(font, formX + PAD, emailBoxY, fieldW, FIELD_H,
                Component.literal("Email"));
        emailInput.setHint(Component.literal("Optional email"));
        emailInput.setMaxLength(128);
        emailInput.setVisible(isRegisterMode);
        // A hidden EditBox must also be inactive, otherwise it still swallows
        // clicks aimed at the widgets underneath it.
        emailInput.active = isRegisterMode;
        addRenderableWidget(emailInput);

        int half = (fieldW - GAP) / 2;

        loginButton = Button.builder(Component.literal("Sign In"), btn -> attemptSubmit())
                .bounds(formX + PAD, primaryBtnY, fieldW, BTN_H).build();
        addRenderableWidget(loginButton);

        registerButton = Button.builder(Component.literal("Create Account"), btn -> switchToRegister())
                .bounds(formX + PAD, secondaryBtnY, half, BTN_H).build();
        addRenderableWidget(registerButton);

        cancelButton = Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(formX + PAD + half + GAP, secondaryBtnY, half, BTN_H).build();
        addRenderableWidget(cancelButton);

        updateButtons();
        usernameInput.setFocused(true);
    }

    private static String mask(int length) {
        return length <= 0 ? "" : "*".repeat(length);
    }

    private void switchToRegister() {
        if (isLoading) return;
        isRegisterMode = !isRegisterMode;

        // Toggle in place - no screen rebuild, so typed text is preserved.
        emailInput.setVisible(isRegisterMode);
        emailInput.active = isRegisterMode;
        if (!isRegisterMode) {
            emailInput.setValue("");
            emailInput.setFocused(false);
        }
        statusMessage = "";
        updateButtons();
    }

    private void updateButtons() {
        if (loginButton == null) return;
        loginButton.setMessage(Component.literal(isRegisterMode ? "Create Account" : "Sign In"));
        registerButton.setMessage(Component.literal(isRegisterMode ? "Back to Sign In" : "Create Account"));
        loginButton.active = !isLoading;
        registerButton.active = !isLoading;
    }

    private void setBusy(boolean busy) {
        isLoading = busy;
        updateButtons();
    }

    private void setStatus(String message, int type) {
        statusMessage = message;
        statusMessageType = type;
    }

    private void attemptSubmit() {
        if (isLoading) return;

        String username = usernameInput.getValue();
        String password = passwordInput.getValue();

        if (username == null || username.trim().isEmpty()) {
            setStatus("Username is required", 1);
            usernameInput.setFocused(true);
            return;
        }
        if (password == null || password.isEmpty()) {
            setStatus("Password is required", 1);
            passwordInput.setFocused(true);
            return;
        }

        if (isRegisterMode) {
            performRegister(username, password);
        } else {
            performLogin(username, password);
        }
    }

    private void performLogin(String username, String password) {
        setBusy(true);
        setStatus("Signing in...", 0);
        authService.login(username, password)
                .thenAccept(result -> minecraft.execute(() -> handleResult(result, false)))
                .exceptionally(ex -> {
                    minecraft.execute(() -> handleFailure(ex, false));
                    return null;
                });
    }

    private void performRegister(String username, String password) {
        String email = emailInput.getValue();
        setBusy(true);
        setStatus("Creating account...", 0);
        authService.register(username, password, email)
                .thenAccept(result -> minecraft.execute(() -> handleResult(result, true)))
                .exceptionally(ex -> {
                    minecraft.execute(() -> handleFailure(ex, true));
                    return null;
                });
    }

    /** Runs on the client thread. */
    private void handleResult(AuthResult result, boolean register) {
        if (minecraft.screen != this) return;
        setBusy(false);

        switch (result.type()) {
            case SUCCESS:
                onAuthSuccess();
                break;
            case INVALID_CREDENTIALS:
                setStatus(register ? "Please check your details" : "Invalid username or password", 1);
                passwordInput.setValue("");
                passwordInput.setFocused(true);
                break;
            case USERNAME_TAKEN:
                setStatus("That username is already taken", 1);
                usernameInput.setFocused(true);
                break;
            case EMAIL_TAKEN:
                setStatus("That email is already registered", 1);
                break;
            case NETWORK_ERROR:
                setStatus("Network error - check your connection", 1);
                break;
            default:
                setStatus(result.message() != null ? result.message() : "Something went wrong", 1);
                break;
        }
    }

    /** Runs on the client thread. */
    private void handleFailure(Throwable ex, boolean register) {
        if (minecraft.screen != this) return;
        setBusy(false);
        setStatus((register ? "Registration failed: " : "Login failed: ") + rootMessage(ex), 1);
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }

    /**
     * Single navigation after a successful sign-in. The previous implementation
     * called {@code onClose()} and then set the parent screen, which could leave
     * the player back in the game with no menu.
     */
    private void onAuthSuccess() {
        statusMessage = "";
        minecraft.setScreen(new FihNetScreen(parent));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (!isLoading) onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            attemptSubmit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void renderBackground(GuiGraphicsExtractor graphics) {
        super.renderBackground(graphics);
        computeLayout();

        // Form panel
        graphics.fill(formX, formY, formX + FORM_W, formY + formH, 0xF81A1F28);
        border(graphics, formX, formY, FORM_W, formH, DIVIDER);

        // Header
        Component title = Component.literal(isRegisterMode ? "Create FihNet Account" : "Sign in to FihNet");
        graphics.text(font, title, formX + PAD, formY - 18, ACCENT_COLOR);

        // Field labels, drawn from the same cached layout the widgets use.
        graphics.text(font, Component.literal("Username"), formX + PAD, usernameLabelY, TEXT_SECONDARY);
        graphics.text(font, Component.literal("Password"), formX + PAD, passwordLabelY, TEXT_SECONDARY);
        if (isRegisterMode) {
            graphics.text(font, Component.literal("Email (optional)"), formX + PAD, emailLabelY, TEXT_SECONDARY);
        }

        // Hint below the fields
        Component hint = isRegisterMode
                ? Component.literal("Password must be at least 6 characters.")
                : Component.literal("Use your FihNet account - not your Minecraft login.");
        graphics.text(font, hint, formX + PAD, formY + formH - 22, 0xFF77A0A6);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Keep the status line centred on the panel content area.
        if (!statusMessage.isEmpty()) {
            int color = switch (statusMessageType) {
                case 1 -> 0xFFFF6B6B;
                case 2 -> 0xFF4CAF50;
                default -> 0xFF77A0A6;
            };
            int centerX = (contentLeft + contentRight) / 2;
            int maxWidth = Math.max(40, contentRight - contentLeft);
            String shown = statusMessage;
            if (font.width(shown) > maxWidth) {
                while (!shown.isEmpty() && font.width(shown + "...") > maxWidth) {
                    shown = shown.substring(0, shown.length() - 1);
                }
                shown = shown + "...";
            }
            graphics.text(font, Component.literal(shown), centerX - font.width(shown) / 2,
                    formY + formH + 10, color);
        }

        Component footer = Component.literal("FihNet account required for friends features");
        graphics.text(font, footer, (contentLeft + contentRight) / 2 - font.width(footer) / 2,
                contentBottom - 20, TEXT_SECONDARY);
        Component disclaimer = Component.literal("Not affiliated with Mojang or Microsoft");
        graphics.text(font, disclaimer, (contentLeft + contentRight) / 2 - font.width(disclaimer) / 2,
                contentBottom - 8, 0xFF666666);
    }

    private void border(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        // Cancel/ESC only - successful sign-in navigates in onAuthSuccess.
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(parent);
        }
    }
}