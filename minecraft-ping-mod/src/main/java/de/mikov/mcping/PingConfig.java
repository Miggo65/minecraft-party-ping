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
    private static final String DEFAULT_RELAY_URL = "ws://158.180.50.88:8787";
    private static final int DEFAULT_PING_LIFETIME_SECONDS = 10;
    private static final int DEFAULT_PING_COLOR_RGB = 0xE66D00;
    private static final float DEFAULT_PING_SCALE = 1.0f;
    private static final boolean DEFAULT_PLAYER_COLORS_ENABLED = false;
    private static final boolean DEFAULT_SHOW_SENDER_NAME = true;

    public String relayUrl = DEFAULT_RELAY_URL;
    public int pingLifetimeSeconds = DEFAULT_PING_LIFETIME_SECONDS;
    public int pingColorRgb = DEFAULT_PING_COLOR_RGB;
    public float pingScale = DEFAULT_PING_SCALE;
    public boolean playerColorsEnabled = DEFAULT_PLAYER_COLORS_ENABLED;
    public boolean showSenderName = DEFAULT_SHOW_SENDER_NAME;

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
                config.save();
                return config;
            }

            config.relayUrl = normalizeRelayUrl(config.relayUrl);
            config.pingLifetimeSeconds = clampLifetimeSeconds(config.pingLifetimeSeconds);
            config.pingColorRgb = normalizeRgb(config.pingColorRgb);
            if (config.pingScale <= 0.0f) {
                config.pingScale = DEFAULT_PING_SCALE;
            }
            config.pingScale = clampPingScale(config.pingScale);
            return config;
        } catch (IOException ex) {
            PingConfig config = new PingConfig();
            config.save();
            return config;
        }
    }

    public void save() {
        try {
            pingLifetimeSeconds = clampLifetimeSeconds(pingLifetimeSeconds);
            pingColorRgb = normalizeRgb(pingColorRgb);
            pingScale = clampPingScale(pingScale);
            relayUrl = normalizeRelayUrl(relayUrl);
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public long pingLifetimeMs() {
        return (long) clampLifetimeSeconds(pingLifetimeSeconds) * 1_000L;
    }

    public int pingColorArgb() {
        return 0xFF000000 | normalizeRgb(pingColorRgb);
    }

    public float pingScale() {
        return clampPingScale(pingScale);
    }

    public static int clampLifetimeSeconds(int seconds) {
        return Math.max(5, Math.min(300, seconds));
    }

    public static int normalizeRgb(int rgb) {
        return rgb & 0x00FFFFFF;
    }

    public static String defaultRelayUrl() {
        return DEFAULT_RELAY_URL;
    }

    public static int defaultPingLifetimeSeconds() {
        return DEFAULT_PING_LIFETIME_SECONDS;
    }

    public static int defaultPingColorRgb() {
        return DEFAULT_PING_COLOR_RGB;
    }

    public static float defaultPingScale() {
        return DEFAULT_PING_SCALE;
    }

    public static boolean defaultPlayerColorsEnabled() {
        return DEFAULT_PLAYER_COLORS_ENABLED;
    }

    public static boolean defaultShowSenderName() {
        return DEFAULT_SHOW_SENDER_NAME;
    }

    public static float clampPingScale(float scale) {
        return Math.max(0.5f, Math.min(2.0f, scale));
    }

    public static float parsePingScaleOrDefault(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().replace(',', '.');
        if (normalized.isEmpty()) {
            return fallback;
        }
        try {
            return clampPingScale(Float.parseFloat(normalized));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static int parseRgbOrDefault(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[0-9a-fA-F]{6}")) {
            return fallback;
        }
        try {
            return normalizeRgb(Integer.parseInt(normalized, 16));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static String normalizeRelayUrl(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_RELAY_URL;
        }

        String normalized = value.trim();
        if (!normalized.contains("://")) {
            normalized = "ws://" + normalized;
        }

        if (normalized.startsWith("http://")) {
            normalized = "ws://" + normalized.substring("http://".length());
        } else if (normalized.startsWith("https://")) {
            normalized = "wss://" + normalized.substring("https://".length());
        }

        if (normalized.startsWith("ws://") || normalized.startsWith("wss://")) {
            return normalized;
        }

        return DEFAULT_RELAY_URL;
    }
}
