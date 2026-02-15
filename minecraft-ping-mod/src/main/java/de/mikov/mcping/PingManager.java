package de.mikov.mcping;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PingManager {
    public static final long PING_LIFETIME_MS = 30_000L;

    private final List<PingRecord> pings = new ArrayList<>();

    public synchronized void addPing(String sender, Vec3d pos, String dimension) {
        long expiresAt = System.currentTimeMillis() + PING_LIFETIME_MS;
        pings.add(new PingRecord(sender, pos, dimension, expiresAt));
    }

    public synchronized List<PingRecord> activePings(long nowMs, String currentDimension) {
        Iterator<PingRecord> iterator = pings.iterator();
        while (iterator.hasNext()) {
            PingRecord ping = iterator.next();
            if (ping.expired(nowMs)) {
                iterator.remove();
            }
        }

        List<PingRecord> filtered = new ArrayList<>();
        for (PingRecord ping : pings) {
            if (ping.dimension().equals(currentDimension)) {
                filtered.add(ping);
            }
        }
        return filtered;
    }
}
