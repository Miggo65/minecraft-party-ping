package de.mikov.mcping;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PingManager {
    private static final int MAX_PINGS_PER_SENDER = 2;

    private final PingConfig config;
    private final List<PingRecord> pings = new ArrayList<>();

    public PingManager(PingConfig config) {
        this.config = config;
    }

    public synchronized void addPing(String sender, Vec3d pos, String serverId, String dimension) {
        addPing(sender, pos, serverId, dimension, PingType.NORMAL);
    }

    public synchronized void addPing(String sender, Vec3d pos, String serverId, String dimension, PingType type) {
        pruneExpired(System.currentTimeMillis());

        int sameSenderCount = 0;
        int oldestSameSenderIndex = -1;
        long oldestSameSenderExpiresAt = Long.MAX_VALUE;
        String senderKey = normalizeSenderKey(sender);

        for (int index = 0; index < pings.size(); index++) {
            PingRecord existing = pings.get(index);
            if (!normalizeSenderKey(existing.sender()).equals(senderKey)) {
                continue;
            }

            sameSenderCount++;
            if (existing.expiresAtMs() < oldestSameSenderExpiresAt) {
                oldestSameSenderExpiresAt = existing.expiresAtMs();
                oldestSameSenderIndex = index;
            }
        }

        if (sameSenderCount >= MAX_PINGS_PER_SENDER && oldestSameSenderIndex >= 0) {
            pings.remove(oldestSameSenderIndex);
        }

        long expiresAt = System.currentTimeMillis() + config.pingLifetimeMs();
        pings.add(new PingRecord(sender, pos, serverId, dimension, type, expiresAt));
    }

    public synchronized List<PingRecord> activePings(long nowMs, String currentServerId, String currentDimension) {
        pruneExpired(nowMs);

        List<PingRecord> filtered = new ArrayList<>();
        for (PingRecord ping : pings) {
            if (ping.dimension().equals(currentDimension) && ping.serverId().equals(currentServerId)) {
                filtered.add(ping);
            }
        }
        return filtered;
    }

    private void pruneExpired(long nowMs) {
        Iterator<PingRecord> iterator = pings.iterator();
        while (iterator.hasNext()) {
            PingRecord ping = iterator.next();
            if (ping.expired(nowMs)) {
                iterator.remove();
            }
        }
    }

    private static String normalizeSenderKey(String sender) {
        return sender == null ? "" : sender.trim().toLowerCase();
    }
}
