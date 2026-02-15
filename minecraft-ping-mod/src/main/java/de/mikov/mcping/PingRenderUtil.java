package de.mikov.mcping;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Locale;

public class PingRenderUtil {

    private PingRenderUtil() {
    }

    public static void renderWorldMarkers(WorldRenderContext context, MinecraftClient client, List<PingRecord> pings) {
        if (client.world == null || pings.isEmpty()) {
            return;
        }

        for (PingRecord ping : pings) {
            Vec3d pos = ping.position();
            DebugRenderer.drawBox(
                    context.matrixStack(),
                    context.consumers(),
                    pos.x - 0.20,
                    pos.y,
                    pos.z - 0.20,
                    pos.x + 0.20,
                    pos.y + 0.40,
                    pos.z + 0.20,
                    1.0F,
                    0.85F,
                    0.1F,
                    0.9F
            );
        }
    }

    public static void renderHud(DrawContext drawContext, MinecraftClient client, List<PingRecord> pings) {
        PlayerEntity player = client.player;
        if (player == null || pings.isEmpty()) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        int startY = 20;

        for (PingRecord ping : pings) {
            double distance = player.getPos().distanceTo(ping.position());
            int x = drawContext.getScaledWindowWidth() - 35;
            int y = startY;

            drawContext.drawText(textRenderer, "◎", x, y, 0xFFE66D00, true);
            drawContext.drawText(textRenderer, String.format(Locale.ROOT, "%.0fm", distance), x - 4, y + 10, 0xFFFFFFFF, true);
            drawContext.drawText(textRenderer, ping.sender(), x - 24, y + 20, 0xFFBFBFBF, false);

            startY += 34;
        }
    }

    public static String currentDimensionKey(MinecraftClient client) {
        if (client.world == null) {
            return "unknown";
        }
        return client.world.getRegistryKey().getValue().toString();
    }

    public static String currentServerId(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null) {
            return client.getCurrentServerEntry().address.trim().toLowerCase(Locale.ROOT);
        }
        if (client.isInSingleplayer()) {
            return "singleplayer";
        }
        return "unknown";
    }
}
