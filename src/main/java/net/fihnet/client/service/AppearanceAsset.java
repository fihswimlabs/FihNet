package net.fihnet.client.service;

public record AppearanceAsset(String id, String displayName, Type type, int creditCost) {
    public enum Type {
        SKIN,
        COSMETIC,
        CAPE,
        SHADER
    }
}
