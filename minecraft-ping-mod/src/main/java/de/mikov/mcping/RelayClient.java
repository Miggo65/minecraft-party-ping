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

public class RelayClient {
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

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(relayUrl), new Listener())
                .whenComplete((ws, err) -> {
                    connecting = false;
                    if (err == null) {
                        webSocket = ws;
                        sendHelloIfPartyActive();
                    } else {
                        webSocket = null;
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
    }

    public void sendLeave() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "leave");
        payload.addProperty("player", getPlayerName());
        sendJson(payload);
    }

    public void sendPing(Vec3d pos, String dimension) {
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
        sendJson(payload);
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    public String relayUrl() {
        return relayUrl;
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
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            RelayClient.this.webSocket = null;
        }

        private void handleMessage(String message) {
            try {
                JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
                if (!obj.has("type") || !"ping".equals(obj.get("type").getAsString())) {
                    return;
                }
                if (!obj.has("player") || !obj.has("dimension") || !obj.has("x") || !obj.has("y") || !obj.has("z")) {
                    return;
                }

                String sender = obj.get("player").getAsString();
                String dimension = obj.get("dimension").getAsString();
                double x = obj.get("x").getAsDouble();
                double y = obj.get("y").getAsDouble();
                double z = obj.get("z").getAsDouble();

                client.execute(() -> pingManager.addPing(sender, new Vec3d(x, y, z), dimension));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
