package net.fihnet.client.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class LocalAppearanceService implements AppearanceService {
    @Override
    public CompletableFuture<List<AppearanceAsset>> catalog() {
        return CompletableFuture.completedFuture(List.of(
                new AppearanceAsset("cape_rainbow", "Rainbow Cape", AppearanceAsset.Type.CAPE, 50),
                new AppearanceAsset("cape_flame", "Flame Cape", AppearanceAsset.Type.CAPE, 75),
                new AppearanceAsset("hat_tophat", "Top Hat", AppearanceAsset.Type.COSMETIC, 30),
                new AppearanceAsset("glasses", "Round Glasses", AppearanceAsset.Type.COSMETIC, 25),
                new AppearanceAsset("backpack", "Adventurer's Backpack", AppearanceAsset.Type.COSMETIC, 40)
        ));
    }

    @Override
    public CompletableFuture<Void> equip(String assetId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> uploadSkin(byte[] pngBytes) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> credits() {
        return CompletableFuture.completedFuture(120);
    }
}
