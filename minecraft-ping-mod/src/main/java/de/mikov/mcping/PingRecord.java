package de.mikov.mcping;

import net.minecraft.util.math.Vec3d;

public record PingRecord(
        String sender,
        Vec3d position,
        String dimension,
        long expiresAtMs
) {
    public boolean expired(long nowMs) {
        return nowMs >= expiresAtMs;
    }
}
