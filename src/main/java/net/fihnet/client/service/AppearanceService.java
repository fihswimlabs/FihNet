package net.fihnet.client.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AppearanceService {
    CompletableFuture<List<AppearanceAsset>> catalog();

    CompletableFuture<Void> equip(String assetId);

    CompletableFuture<Void> uploadSkin(byte[] pngBytes);

    CompletableFuture<Integer> credits();
}
