package de.mikov.mcping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingClientMod implements ClientModInitializer {
    private static PingClientMod INSTANCE;
    private static final Logger LOGGER = LoggerFactory.getLogger("mcping-client");

    private static final double MAX_PING_DISTANCE = 256.0;
    private static final double PING_SELECTION_THRESHOLD_PIXELS = 5.0;
    private static final String DEFAULT_PARTY_CODE = "1";
    private static final long SELECTION_DELAY_MS = 120;

    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("simplemultiplayerping", "general"));

    private KeyBinding pingKey;
    private KeyBinding partyMenuKey;

    private PingConfig config;
    private PingManager pingManager;
    private final PartyState partyState = new PartyState();

    private RelayClient relayClient;
    private String lastKnownServerId = "";
    private String relayUrlInUse = "";

    private boolean pingKeyDown;
    private long pingKeyDownTime;
    private boolean isPingWheelOpen;
    private PingType selectedPingType = PingType.NORMAL;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        MinecraftClient client = MinecraftClient.getInstance();
        config = PingConfig.load();
        pingManager = new PingManager(config);
        relayUrlInUse = PingConfig.normalizeRelayUrl(config.relayUrl);
        relayClient = new RelayClient(client, pingManager, partyState, relayUrlInUse);
        LOGGER.info("Minecraft Ping Mod initialized. relayUrl={} selectionDelayMs={}", relayUrlInUse, SELECTION_DELAY_MS);

        pingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcping.ping",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY
        ));

        partyMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcping.party",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            PingRenderUtil.renderHud(drawContext, client,
                    pingManager.activePings(System.currentTimeMillis(), partyState.serverId(), PingRenderUtil.currentDimensionKey(client)),
                    config,
                    tickDelta);
            if (isPingWheelOpen) {
                PingRenderUtil.renderPingSelectionOverlay(drawContext, client, selectedPingType);
            }
        });
    }

    public PingConfig config() {
        return config;
    }

    public static PingClientMod instance() {
        return INSTANCE;
    }
    
    public boolean isPingWheelOpen() {
        return isPingWheelOpen;
    }

    public boolean isPingKeyDown() {
        if (pingKeyDown) {
            return true;
        }
        if (pingKey == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }

        long handle = client.getWindow().getHandle();
        InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(pingKey);
        if (bound == null) {
            return false;
        }

        if (bound.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, bound.getCode()) == GLFW.GLFW_PRESS;
        }

        return GLFW.glfwGetKey(handle, bound.getCode()) == GLFW.GLFW_PRESS;
    }

    private void onClientTick(MinecraftClient client) {
        ensureRelayClientMatchesConfig(client);
        applyDefaultPartyForCurrentServer();
        relayClient.tick();

        while (partyMenuKey.wasPressed()) {
            LOGGER.info("Opening party menu");
            client.setScreen(new PartyScreen(client, partyState, relayClient));
        }

        boolean pressed = pingKey.isPressed();
        long now = System.currentTimeMillis();

        // Key just pressed
        if (pressed && !pingKeyDown) {
            pingKeyDown = true;
            pingKeyDownTime = now;
            isPingWheelOpen = false;
        }

        // Key held logic
        if (pressed && pingKeyDown) {
            long holdDuration = now - pingKeyDownTime;

            if (!isPingWheelOpen && holdDuration >= SELECTION_DELAY_MS) {
                openPingWheel(client);
            }

            if (isPingWheelOpen) {
                updatePingSelection(client);
            }
        }

        // Key released
        if (!pressed && pingKeyDown) {
            pingKeyDown = false;

            if (isPingWheelOpen) {
                setPingWheelOpen(client, false);
                PingType typeToSend = selectedPingType;
                selectedPingType = PingType.NORMAL;
                trySendPingAtCrosshair(client, typeToSend);
            } else {
                trySendPingAtCrosshair(client, PingType.NORMAL);
            }
        }
    }

    private void openPingWheel(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        setPingWheelOpen(client, true);
        selectedPingType = PingType.NORMAL;
        LOGGER.info("Ping wheel opened");
    }

    private void updatePingSelection(MinecraftClient client) {
        if (client.player == null) {
            selectedPingType = PingType.NORMAL;
            return;
        }

        double windowScaleY = (double) client.getWindow().getScaledHeight() / Math.max(1, client.getWindow().getHeight());
        double mouseY = client.mouse.getY() * windowScaleY;
        double centerY = client.getWindow().getScaledHeight() / 2.0;
        double deltaY = mouseY - centerY;

        if (deltaY <= -PING_SELECTION_THRESHOLD_PIXELS) {
            selectedPingType = PingType.WARNING;
        } else if (deltaY >= PING_SELECTION_THRESHOLD_PIXELS) {
            selectedPingType = PingType.GO;
        } else {
            selectedPingType = PingType.NORMAL;
        }
    }

    private void setPingWheelOpen(MinecraftClient client, boolean open) {
        isPingWheelOpen = open;
        if (open) {
            client.mouse.unlockCursor();
            return;
        }

        if (client.currentScreen == null) {
            client.mouse.lockCursor();
        }
    }

    private void trySendPingAtCrosshair(MinecraftClient client, PingType pingType) {
        if (client.player == null || client.world == null) {
            return;
        }

        if (!partyState.inParty()) {
            client.player.sendMessage(Text.literal("Open the party menu with P and join a party first."), true);
            return;
        }

        HitResult target = client.player.raycast(MAX_PING_DISTANCE, 1.0F, false);
        if (!(target instanceof BlockHitResult blockHitResult)) {
            client.player.sendMessage(Text.literal("No block targeted."), true);
            return;
        }

        Vec3d pos = blockHitResult.getPos();
        String serverId = partyState.serverId();
        String dimension = PingRenderUtil.currentDimensionKey(client);
        String playerName = client.getSession().getUsername();

        pingManager.addPing(playerName, pos, serverId, dimension, pingType);
        relayClient.sendPing(pos, dimension, pingType);
        LOGGER.info("Local ping created sender={} party={} serverId={} type={} pos=({}, {}, {}) dimension={}",
            playerName,
            partyState.partyCode(),
            serverId,
            pingType,
            Math.round(pos.x),
            Math.round(pos.y),
            Math.round(pos.z),
            dimension);
    }

    private void ensureRelayClientMatchesConfig(MinecraftClient client) {
        String desiredRelayUrl = PingConfig.normalizeRelayUrl(config.relayUrl);
        if (desiredRelayUrl.equals(relayUrlInUse)) {
            return;
        }

        if (relayClient != null) {
            relayClient.close();
        }

        relayUrlInUse = desiredRelayUrl;
        relayClient = new RelayClient(client, pingManager, partyState, relayUrlInUse);
        LOGGER.info("Relay URL changed. Recreated relay client for {}", relayUrlInUse);
    }

    private void applyDefaultPartyForCurrentServer() {
        MinecraftClient client = MinecraftClient.getInstance();
        String serverId = PingRenderUtil.currentServerId(client);
        if ("unknown".equals(serverId)) {
            return;
        }

        if (serverId.equals(lastKnownServerId)) {
            return;
        }

        lastKnownServerId = serverId;
        partyState.joinParty(DEFAULT_PARTY_CODE, serverId);
        PingRenderUtil.resetAssignedPlayerColors();
        relayClient.sendJoin(DEFAULT_PARTY_CODE, serverId);
        LOGGER.info("Auto-joined default party={} for serverId={}", DEFAULT_PARTY_CODE, serverId);
    }

}
