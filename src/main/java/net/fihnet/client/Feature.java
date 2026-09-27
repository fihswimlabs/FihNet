package net.fihnet.client;

public enum Feature {
    SOCIAL_PRESENCE("Social presence", true),
    SOCIAL_PRESENCE_JOIN_WORLD("Join friends' worlds", true),
    SOCIAL_PRESENCE_CHAT("Friend chat", true),
    SOCIAL_PRESENCE_ONLINE_STATUS("Online status", true),
    CUSTOM_SKINS("Custom skins", true),
    COSMETICS("Cosmetics and capes", true),
    SHADERS("Shader loader", false),
    PERFORMANCE("Performance profile", true),
    SETTINGS("Improved settings", true);

    private final String label;
    private final boolean enabledByDefault;

    Feature(String label, boolean enabledByDefault) {
        this.label = label;
        this.enabledByDefault = enabledByDefault;
    }

    public String label() {
        return label;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }

    public boolean isSocialSubFeature() {
        return this == SOCIAL_PRESENCE_JOIN_WORLD || this == SOCIAL_PRESENCE_CHAT || this == SOCIAL_PRESENCE_ONLINE_STATUS;
    }
}
