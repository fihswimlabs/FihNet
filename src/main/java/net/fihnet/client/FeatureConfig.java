package net.fihnet.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

public final class FeatureConfig {
    private final Path path;
    private final Map<Feature, Boolean> enabled = new EnumMap<>(Feature.class);

    public FeatureConfig(Path configDirectory) {
        this.path = configDirectory.resolve("fihnet.properties");
        for (Feature feature : Feature.values()) {
            enabled.put(feature, feature.enabledByDefault());
        }
        load();
    }

    public boolean isEnabled(Feature feature) {
        return enabled.get(feature);
    }

    public void toggle(Feature feature) {
        enabled.put(feature, !isEnabled(feature));
        save();
    }

    private void load() {
        if (!Files.exists(path)) {
            return;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            for (Feature feature : Feature.values()) {
                String value = properties.getProperty(feature.name());
                if (value != null) {
                    enabled.put(feature, Boolean.parseBoolean(value));
                }
            }
        } catch (IOException ignored) {
            // Defaults keep the client usable if the config cannot be read.
        }
    }

    private void save() {
        Properties properties = new Properties();
        enabled.forEach((feature, value) -> properties.setProperty(feature.name(), value.toString()));
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "FihNet feature settings");
            }
        } catch (IOException ignored) {
            // A transient write failure should not interrupt gameplay.
        }
    }
}
