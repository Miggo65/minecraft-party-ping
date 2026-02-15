package de.mikov.mcping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcping-client.json");

    public String relayUrl = "ws://127.0.0.1:8787";

    public static PingConfig load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                PingConfig config = new PingConfig();
                config.save();
                return config;
            }

            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            PingConfig config = GSON.fromJson(json, PingConfig.class);
            if (config == null || config.relayUrl == null || config.relayUrl.isBlank()) {
                config = new PingConfig();
            }
            return config;
        } catch (IOException ex) {
            PingConfig config = new PingConfig();
            config.save();
            return config;
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
