package de.mikov.mcping;

import net.minecraft.util.math.Vec3d;

public record PingRecord(
        String sender,
        Vec3d position,
    String serverId,
        String dimension,
    PingType type,
        long expiresAtMs
) {
    public boolean expired(long nowMs) {
        return nowMs >= expiresAtMs;
    }
}
