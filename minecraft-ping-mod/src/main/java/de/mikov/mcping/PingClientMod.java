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

import java.util.List;

public class PingClientMod implements ClientModInitializer {
    private KeyBinding pingKey;
    private KeyBinding partyMenuKey;

    private final PingManager pingManager = new PingManager();
    private final PartyState partyState = new PartyState();

    private RelayClient relayClient;

    @Override
    public void onInitializeClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        PingConfig config = PingConfig.load();
        relayClient = new RelayClient(client, pingManager, partyState, config.relayUrl);

        pingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcping.ping",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            KeyBinding.Category.create(Identifier.of("mcping", "general"))
        ));

        partyMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcping.party",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
            KeyBinding.Category.create(Identifier.of("mcping", "general"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            List<PingRecord> pings = pingManager.activePings(System.currentTimeMillis(), PingRenderUtil.currentDimensionKey(client));
            PingRenderUtil.renderHud(drawContext, client, pings);
        });
    }

    private void onClientTick(MinecraftClient client) {
        relayClient.tick();

        while (partyMenuKey.wasPressed()) {
            client.setScreen(new PartyScreen(client, partyState, relayClient));
        }

        while (pingKey.wasPressed()) {
            trySendPingAtCrosshair(client);
        }
    }

    private void trySendPingAtCrosshair(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        if (!partyState.inParty()) {
            client.player.sendMessage(Text.literal("Öffne mit P das Party-Menü und tritt einer Party bei."), true);
            return;
        }

        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHitResult)) {
            client.player.sendMessage(Text.literal("Kein Block anvisiert."), true);
            return;
        }

        Vec3d pos = Vec3d.ofCenter(blockHitResult.getBlockPos());
        String dimension = PingRenderUtil.currentDimensionKey(client);

        pingManager.addPing(client.getSession().getUsername(), pos, dimension);
        relayClient.sendPing(pos, dimension);
    }
}
