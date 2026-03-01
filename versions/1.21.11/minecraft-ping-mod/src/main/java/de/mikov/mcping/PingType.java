package de.mikov.mcping;

import java.util.Locale;

public enum PingType {
    NORMAL,
    WARNING,
    GO;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PingType fromWire(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "warning" -> WARNING;
            case "go", "move" -> GO;
            default -> NORMAL;
        };
    }
}
