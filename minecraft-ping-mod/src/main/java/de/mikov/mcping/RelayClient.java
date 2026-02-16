package de.mikov.mcping;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelayClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcping-relay-client");

    private final MinecraftClient client;
    private final PingManager pingManager;
    private final PartyState partyState;
    private final String relayUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private WebSocket webSocket;
    private volatile boolean connecting;
    private long lastConnectAttemptMs;

    public RelayClient(MinecraftClient client, PingManager pingManager, PartyState partyState, String relayUrl) {
        this.client = client;
        this.pingManager = pingManager;
        this.partyState = partyState;
        this.relayUrl = relayUrl;
    }

    public void tick() {
        if (webSocket == null && !connecting) {
            long now = System.currentTimeMillis();
            if (now - lastConnectAttemptMs > 5_000L) {
                connect();
            }
        }
    }

    public void connect() {
        lastConnectAttemptMs = System.currentTimeMillis();
        connecting = true;
        LOGGER.info("Connecting to relay {}", relayUrl);

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(relayUrl), new Listener())
                .whenComplete((ws, err) -> {
                    connecting = false;
                    if (err == null) {
                        webSocket = ws;
                        LOGGER.info("Connected to relay {}", relayUrl);
                        sendHelloIfPartyActive();
                    } else {
                        webSocket = null;
                        LOGGER.warn("Relay connection failed: {}", err.getMessage());
                    }
                });
    }

    public void sendJoin(String partyCode, String serverId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "join");
        payload.addProperty("party", partyCode);
        payload.addProperty("serverId", serverId);
        payload.addProperty("player", getPlayerName());
        sendJson(payload);
        LOGGER.info("Join sent party={} serverId={} player={}", partyCode, serverId, getPlayerName());
    }

    public void sendLeave() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "leave");
        payload.addProperty("player", getPlayerName());
        sendJson(payload);
        LOGGER.info("Leave sent player={}", getPlayerName());
    }

    public void sendPing(Vec3d pos, String dimension, PingType pingType) {
        if (!partyState.inParty()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "ping");
        payload.addProperty("party", partyState.partyCode());
        payload.addProperty("serverId", partyState.serverId());
        payload.addProperty("player", getPlayerName());
        payload.addProperty("x", pos.x);
        payload.addProperty("y", pos.y);
        payload.addProperty("z", pos.z);
        payload.addProperty("dimension", dimension);
        payload.addProperty("pingType", pingType.wireValue());
        sendJson(payload);
        LOGGER.info("Ping sent party={} serverId={} player={} type={} pos=({}, {}, {}) dimension={}",
            partyState.partyCode(),
            partyState.serverId(),
            getPlayerName(),
            pingType,
            Math.round(pos.x),
            Math.round(pos.y),
            Math.round(pos.z),
            dimension);
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    public String relayUrl() {
        return relayUrl;
    }

    public void close() {
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "client-reload");
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void sendHelloIfPartyActive() {
        if (partyState.inParty()) {
            sendJoin(partyState.partyCode(), partyState.serverId());
        }
    }

    private void sendJson(JsonObject payload) {
        WebSocket ws = webSocket;
        if (ws == null) {
            return;
        }
        ws.sendText(payload.toString(), true);
    }

    private String getPlayerName() {
        if (client.getSession() == null) {
            return "unknown";
        }
        return client.getSession().getUsername();
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            RelayClient.this.webSocket = null;
            LOGGER.warn("Relay connection closed status={} reason={}", statusCode, reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            RelayClient.this.webSocket = null;
            LOGGER.warn("Relay socket error: {}", error.getMessage());
        }

        private void handleMessage(String message) {
            try {
                JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
                if (!obj.has("type") || !"ping".equals(obj.get("type").getAsString())) {
                    return;
                }
                if (!obj.has("player") || !obj.has("serverId") || !obj.has("dimension") || !obj.has("x") || !obj.has("y") || !obj.has("z")) {
                    return;
                }

                String sender = obj.get("player").getAsString();
                String serverId = obj.get("serverId").getAsString();
                String dimension = obj.get("dimension").getAsString();
                double x = obj.get("x").getAsDouble();
                double y = obj.get("y").getAsDouble();
                double z = obj.get("z").getAsDouble();
                PingType pingType = obj.has("pingType") ? PingType.fromWire(obj.get("pingType").getAsString()) : PingType.NORMAL;

                if (!partyState.inParty() || !partyState.serverId().equals(serverId)) {
                    return;
                }

                LOGGER.info("Ping received sender={} type={} pos=({}, {}, {}) dimension={}",
                        sender,
                        pingType,
                        Math.round(x),
                        Math.round(y),
                        Math.round(z),
                        dimension);

                client.execute(() -> pingManager.addPing(sender, new Vec3d(x, y, z), serverId, dimension, pingType));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
