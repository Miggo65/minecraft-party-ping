package de.mikov.mcping;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

public class PingRenderUtil {

    private PingRenderUtil() {
    }

    public static void renderHud(DrawContext drawContext, MinecraftClient client, List<PingRecord> pings) {
        PlayerEntity player = client.player;
        if (player == null || pings.isEmpty()) {
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        Quaternionf cameraRotationInverse = new Quaternionf(camera.getRotation()).conjugate();

        TextRenderer textRenderer = client.textRenderer;
        int width = drawContext.getScaledWindowWidth();
        int height = drawContext.getScaledWindowHeight();
        float focalLength = (float) (height / (2.0 * Math.tan(Math.toRadians(70.0 / 2.0))));

        for (PingRecord ping : pings) {
            double dx = player.getX() - ping.position().x;
            double dy = player.getY() - ping.position().y;
            double dz = player.getZ() - ping.position().z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            Vector3f relative = new Vector3f(
                    (float) (ping.position().x - cameraPos.x),
                    (float) (ping.position().y - cameraPos.y),
                    (float) (ping.position().z - cameraPos.z)
            );
            relative.rotate(cameraRotationInverse);

            if (relative.z <= 0.01f) {
                continue;
            }

            int screenX = Math.round(width / 2f - (relative.x / relative.z) * focalLength);
            int screenY = Math.round(height / 2f - (relative.y / relative.z) * focalLength);

            if (screenX < -80 || screenX > width + 80 || screenY < -80 || screenY > height + 80) {
                continue;
            }

            drawContext.drawText(textRenderer, "◎", screenX - 4, screenY - 8, 0xFFE66D00, true);
            drawContext.drawText(textRenderer, String.format(Locale.ROOT, "%.0fm", distance), screenX - 10, screenY + 4, 0xFFFFFFFF, true);
            drawContext.drawText(textRenderer, ping.sender(), screenX - 18, screenY + 14, 0xFFBFBFBF, false);
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
