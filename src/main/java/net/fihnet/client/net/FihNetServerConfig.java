package net.fihnet.client.net;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the FihNet backend and stores the session token.
 *
 * <p>Point the client at your own FihNet server by writing one line into
 * config/fihnet/server.txt, for example http://play.example.net:8765
 */
public final class FihNetServerConfig {
    private static final Logger LOG = LogManager.getLogger("FihNet/ServerConfig");
    public static final String DEFAULT_SERVER = "http://127.0.0.1:8765";

    private FihNetServerConfig() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("fihnet");
    }

    private static Path serverFile() {
        return dir().resolve("server.txt");
    }

    private static Path tokenFile() {
        return dir().resolve("token.txt");
    }

    public static String serverUrl() {
        try {
            if (Files.exists(serverFile())) {
                String raw = Files.readString(serverFile(), StandardCharsets.UTF_8).trim();
                if (!raw.isEmpty()) {
                    return stripTrailingSlash(raw);
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not read server.txt: {}", e.getMessage());
        }
        return DEFAULT_SERVER;
    }

    public static void setServerUrl(String url) {
        try {
            Files.createDirectories(dir());
            Files.writeString(serverFile(), stripTrailingSlash(url.trim()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Could not write server.txt: {}", e.getMessage());
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String readToken() {
        try {
            if (Files.exists(tokenFile())) {
                return Files.readString(tokenFile(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            LOG.warn("Could not read token.txt: {}", e.getMessage());
        }
        return "";
    }

    public static void writeToken(String token) {
        try {
            Files.createDirectories(dir());
            Files.writeString(tokenFile(), token == null ? "" : token, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Could not write token.txt: {}", e.getMessage());
        }
    }

    public static void clearToken() {
        try {
            Files.deleteIfExists(tokenFile());
        } catch (IOException e) {
            LOG.warn("Could not delete token.txt: {}", e.getMessage());
        }
    }
}