# FihNet

FihNet is a Fabric client companion for social presence, appearance customization, shaders, performance profiles, and improved in-game settings.

## Current baseline

The project is pinned to Minecraft 26.1.2 with Fabric API 0.155.3. This release uses Minecraft's non-obfuscated classes and requires Java 25.

## Run

1. Install JDK 25. The included Gradle wrapper supplies Gradle 9.7.
2. Run `gradlew build`.
3. Put `build/libs/fihnet-0.1.0.jar` and the matching Fabric API jar in the client `mods` folder.
4. Use `F8` in-game to open the FihNet menu.

The network layer is intentionally a local contract for now. It can be connected to a real backend without changing the menu or feature modules.
