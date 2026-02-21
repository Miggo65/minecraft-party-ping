package de.mikov.mcping;

import net.fabricmc.api.ClientModInitializer;

public class PingClientModLegacy implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[minecraft-ping-mod-1.8.9] Initialized legacy client module");
    }
}