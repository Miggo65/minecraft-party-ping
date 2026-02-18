package de.mikov.mcping;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PingRenderUtil {
    private static final int RGB_RED = 0xFF3333;
    private static final int RGB_GREEN = 0x5BC236;
    private static final int RGB_BLUE = 0x4AA3FF;

    private static final int[] PRIMARY_PLAYER_COLORS = new int[] {
        RGB_RED,
        RGB_GREEN,
        RGB_BLUE
    };

    private static final int[] EXTRA_PLAYER_COLORS = new int[] {
        0xFFD400,
        0xFF8A65,
        0xBA68C8,
        0xF06292,
        0x26C6DA,
        0xAED581,
        0xFFB74D,
        0xE57373
    };

    private static final Map<String, Integer> ASSIGNED_PLAYER_COLORS = new LinkedHashMap<>();

    private static Method gameRendererFovMethod;
    private static Method tickCounterDeltaMethodBoolean;
    private static Method tickCounterDeltaMethodNoArgs;

    private PingRenderUtil() {
    }

    public static void resetAssignedPlayerColors() {
        ASSIGNED_PLAYER_COLORS.clear();
    }

    public static void renderHud(DrawContext drawContext, MinecraftClient client, List<PingRecord> pings, PingConfig config, Object renderTickCounter) {
        PlayerEntity player = client.player;
        if (player == null || pings.isEmpty()) {
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        Quaternionf worldToCameraRotation = new Quaternionf(camera.getRotation()).conjugate();

        TextRenderer textRenderer = client.textRenderer;
        int width = drawContext.getScaledWindowWidth();
        int height = drawContext.getScaledWindowHeight();
        double fovDegrees = resolveDynamicFov(client.gameRenderer, camera, resolveTickDelta(renderTickCounter), client.options.getFov().getValue());
        float focalLength = (float) (height / (2.0 * Math.tan(Math.toRadians(fovDegrees / 2.0))));
        float pingSizeScale = config.pingScale();
        String ownPlayerName = client.getSession() != null ? client.getSession().getUsername() : "";

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
            relative.rotate(worldToCameraRotation);

            if (relative.z < -0.01f) {
                float z = -relative.z;
                int screenX = Math.round(width / 2f + (relative.x / z) * focalLength);
                int screenY = Math.round(height / 2f - (relative.y / z) * focalLength);

                if (screenX >= -80 && screenX <= width + 80 && screenY >= -80 && screenY <= height + 80) {
                    float scale = distanceScale(distance);

                    float renderScale = quantizeScale(scale * pingSizeScale);

                    drawContext.getMatrices().pushMatrix();
                    drawContext.getMatrices().translate(screenX, screenY);
                    drawContext.getMatrices().scale(renderScale, renderScale);

                    int senderColor = resolvePingColorArgb(ping.sender(), ownPlayerName, config);

                    if (ping.type() == PingType.WARNING) {
                        drawWarningPingIcon(drawContext, textRenderer, senderColor);
                    } else if (ping.type() == PingType.GO) {
                        drawContext.drawText(textRenderer, "⚑", -4, -8, senderColor, false);
                    } else {
                        drawNormalPingIcon(drawContext, senderColor);
                    }

                    drawContext.drawText(textRenderer, String.format(Locale.ROOT, "%.0fm", distance), -10, 4, 0xFFFFFFFF, true);
                    if (config.showSenderName) {
                        drawContext.drawText(textRenderer, ping.sender(), -18, 14, 0xFFBFBFBF, false);
                    }
                    drawContext.getMatrices().popMatrix();
                }
            }
        }
    }

    private static int resolvePingColorArgb(String sender, String ownPlayerName, PingConfig config) {
        int ownColor = config.pingColorArgb();
        if (!config.playerColorsEnabled) {
            return ownColor;
        }

        if (sender == null || sender.isBlank()) {
            return ownColor;
        }

        if (sender.equalsIgnoreCase(ownPlayerName)) {
            return ownColor;
        }

        String senderKey = sender.trim().toLowerCase(Locale.ROOT);
        Integer assignedColor = ASSIGNED_PLAYER_COLORS.get(senderKey);
        if (assignedColor != null) {
            return 0xFF000000 | (assignedColor & 0x00FFFFFF);
        }

        List<Integer> candidateColors = buildCandidateColors(config.pingColorRgb);
        if (candidateColors.isEmpty()) {
            return ownColor;
        }

        int selectedColor = candidateColors.get(Math.floorMod(ASSIGNED_PLAYER_COLORS.size(), candidateColors.size()));
        ASSIGNED_PLAYER_COLORS.put(senderKey, selectedColor);
        return 0xFF000000 | (selectedColor & 0x00FFFFFF);
    }

    private static List<Integer> buildCandidateColors(int ownColorRgb) {
        int ownNormalized = ownColorRgb & 0x00FFFFFF;
        List<Integer> colors = new ArrayList<>();

        for (int color : PRIMARY_PLAYER_COLORS) {
            if (color == ownNormalized) {
                continue;
            }
            colors.add(color);
        }

        for (int color : EXTRA_PLAYER_COLORS) {
            if (color == ownNormalized) {
                continue;
            }
            if (!colors.contains(color)) {
                colors.add(color);
            }
        }

        return colors;
    }

    public static void renderPingSelectionOverlay(DrawContext drawContext, MinecraftClient client, PingType selectedType) {
        int width = drawContext.getScaledWindowWidth();
        int height = drawContext.getScaledWindowHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        TextRenderer textRenderer = client.textRenderer;

        int topY = centerY - 26;
        int bottomY = centerY + 8;
        int boxHeight = 14;

        int topFill = selectedType == PingType.WARNING ? 0xAA992020 : 0x88333333;
        int bottomFill = selectedType == PingType.GO ? 0xAA2B6D1B : 0x88333333;

        drawVerticalTrapezoid(drawContext, centerX, topY, boxHeight, 62, 74, topFill);
        drawVerticalTrapezoid(drawContext, centerX, bottomY, boxHeight, 74, 62, bottomFill);

        drawContext.drawCenteredTextWithShadow(textRenderer, "▲ Warning", centerX, topY + 3,
                selectedType == PingType.WARNING ? 0xFFFF8080 : 0xFFCCCCCC);
        drawContext.drawCenteredTextWithShadow(textRenderer, "⚑ Move", centerX, bottomY + 3,
                selectedType == PingType.GO ? 0xFF9CFF7A : 0xFFCCCCCC);
    }

    private static void drawVerticalTrapezoid(DrawContext drawContext, int centerX, int y, int height,
                              int widthTop, int widthBottom, int color) {
        for (int row = 0; row < height; row++) {
            float t = height <= 1 ? 0.0f : row / (float) (height - 1);
            int width = Math.round(widthTop + (widthBottom - widthTop) * t);
            int halfWidth = width / 2;
            drawContext.fill(centerX - halfWidth, y + row, centerX + halfWidth + 1, y + row + 1, color);
        }
    }

    private static void drawCenteredScaledGlyph(DrawContext drawContext, TextRenderer textRenderer,
                                                String glyph, float centerX, float topY,
                                                float scale, int color, boolean shadow) {
        int width = textRenderer.getWidth(glyph);
        float drawX = centerX - (width * scale) / 2.0f;

        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(drawX, topY);
        drawContext.getMatrices().scale(scale, scale);
        drawContext.drawText(textRenderer, glyph, 0, 0, color, shadow);
        drawContext.getMatrices().popMatrix();
    }

    private static void drawWarningPingIcon(DrawContext drawContext, TextRenderer textRenderer, int senderColor) {
        // Pixel-perfect Warning Triangle (11px wide base, ~10px tall)
        // Center x=0. To centre vertically around 0, we shift coordinates.
        // Let's draw relative to (0, -4) as "center" of the icon visually.

        // 1. Draw Triangle Background (Sender Color)
        // Row -9 (Top tip)
        drawContext.fill(0, -9, 1, -8, senderColor);
        // Row -8
        drawContext.fill(-1, -8, 2, -7, senderColor);
        // Row -7
        drawContext.fill(-1, -7, 2, -6, senderColor);
        // Row -6
        drawContext.fill(-2, -6, 3, -5, senderColor);
        // Row -5
        drawContext.fill(-2, -5, 3, -4, senderColor);
        // Row -4
        drawContext.fill(-3, -4, 4, -3, senderColor);
        // Row -3
        drawContext.fill(-3, -3, 4, -2, senderColor);
        // Row -2 (Base)
        drawContext.fill(-4, -2, 5, -1, senderColor);

        // 2. Draw Exclamation Mark (Black 0xFF000000 or near black)
        int black = 0xFF101010;
        
        // Stem: x=0, y=-7 to -4 (Original was -3, moved up to shorten)
        // Fills y = -7, -6, -5
        drawContext.fill(0, -7, 1, -4, black);
        
        // Dot: x=0, y=-3 (Moved up from -2)
        // Fills y = -3
        drawContext.fill(0, -3, 1, -2, black); 
    }

    private static void drawNormalPingIcon(DrawContext drawContext, int senderColor) {
        // "Cleaner" Normal Ping - Rounder Design
        // Center x=0, y=-4
        
        int centerY = -4; 
        
        int black = 0xFF000000;
        // Inner field: Darker but with more saturation (less greyed out)
        // We mix senderColor with black but try to keep saturation.
        int r = (senderColor >> 16) & 0xFF;
        int g = (senderColor >> 8) & 0xFF;
        int b = senderColor & 0xFF;
        // Simple darken
        int dimColor = ((int)(r * 0.6) << 16) | ((int)(g * 0.6) << 8) | (int)(b * 0.6) | 0xFF000000;

        // Bright outline (colored but brighter version of senderColor)
        // Or "rand auch im Farbton aber etwas heller"
        int outlineR = Math.min(255, (int)(r * 1.3));
        int outlineG = Math.min(255, (int)(g * 1.3));
        int outlineB = Math.min(255, (int)(b * 1.3));
        int outline = (outlineR << 16) | (outlineG << 8) | outlineB | 0xFF000000;

        // 1. Outer Outline (Rounder shape)
        // 7x7 canvas (-3 to +3 in relative X)
        //     .XXX.
        //    X.....X
        //   X.......X
        //   X.......X
        //   X.......X
        //    X.....X
        //     .XXX.
        
        // Top/Bottom caps
        drawContext.fill(-1, centerY - 3, 2, centerY - 2, outline);
        drawContext.fill(-1, centerY + 3, 2, centerY + 4, outline);
        // Side caps
        drawContext.fill(-3, centerY - 1, -2, centerY + 2, outline);
        drawContext.fill( 3, centerY - 1,  4, centerY + 2, outline);
        // Corners (diagonal pixels)
        drawContext.fill(-2, centerY - 3, -1, centerY - 2, outline);
        drawContext.fill( 2, centerY - 3,  3, centerY - 2, outline);
        drawContext.fill(-3, centerY - 2, -2, centerY - 1, outline);
        drawContext.fill( 3, centerY - 2,  4, centerY - 1, outline);
        drawContext.fill(-3, centerY + 2, -2, centerY + 3, outline);
        drawContext.fill( 3, centerY + 2,  4, centerY + 3, outline);
        drawContext.fill(-2, centerY + 3, -1, centerY + 4, outline);
        drawContext.fill( 2, centerY + 3,  3, centerY + 4, outline);

        // 2. Main Ring (Sender Color)
        //     .XXX.
        //    X.....X
        //    X.....X
        //    X.....X
        //     .XXX.
        // Similar shape but 1 px smaller radius
        
        // Horizontal main bars
        drawContext.fill(-1, centerY - 2, 2, centerY - 1, senderColor); // Top
        drawContext.fill(-1, centerY + 2, 2, centerY + 3, senderColor); // Bottom
        // Vertical main bars
        drawContext.fill(-2, centerY - 1, -1, centerY + 2, senderColor); // Left
        drawContext.fill( 2, centerY - 1,  3, centerY + 2, senderColor); // Right
        // Corner fillers for roundness (inner ring)
        drawContext.fill(-2, centerY - 2, -1, centerY - 1, senderColor);
        drawContext.fill( 2, centerY - 2,  3, centerY - 1, senderColor);
        drawContext.fill(-2, centerY + 2, -1, centerY + 3, senderColor);
        drawContext.fill( 2, centerY + 2,  3, centerY + 3, senderColor);
        
        // 3. Inner Field (Dim Color) - 3x3 circle-ish
        // We can just fill the 3x3 box, the corners will be covered by the ring if we did it right, 
        // but let's be safe and just fill the center cross
        drawContext.fill(-1, centerY - 1, 2, centerY + 2, dimColor); 

        // 4. Center Dot (Black)
        drawContext.fill(0, centerY, 1, centerY + 1, black);
    }

    private static void drawFilledCircle(DrawContext drawContext, int centerX, int centerY, int radius, int color) {
        int radiusSquared = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radiusSquared) {
                    drawContext.fill(centerX + x, centerY + y, centerX + x + 1, centerY + y + 1, color);
                }
            }
        }
    }

    private static void drawRing(DrawContext drawContext, int centerX, int centerY, int outerRadius, int innerRadius, int color) {
        int outerSquared = outerRadius * outerRadius;
        int innerSquared = innerRadius * innerRadius;

        for (int y = -outerRadius; y <= outerRadius; y++) {
            for (int x = -outerRadius; x <= outerRadius; x++) {
                int distanceSquared = x * x + y * y;
                if (distanceSquared <= outerSquared && distanceSquared >= innerSquared) {
                    drawContext.fill(centerX + x, centerY + y, centerX + x + 1, centerY + y + 1, color);
                }
            }
        }
    }

    private static int lightenColor(int argb, float amount) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        red = clampColorChannel((int) (red + (255 - red) * amount));
        green = clampColorChannel((int) (green + (255 - green) * amount));
        blue = clampColorChannel((int) (blue + (255 - blue) * amount));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int darkenColor(int argb, float amount) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        red = clampColorChannel((int) (red * (1.0f - amount)));
        green = clampColorChannel((int) (green * (1.0f - amount)));
        blue = clampColorChannel((int) (blue * (1.0f - amount)));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int clampColorChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float resolveTickDelta(Object renderTickCounter) {
        if (renderTickCounter == null) {
            return 1.0f;
        }

        try {
            if (tickCounterDeltaMethodBoolean == null) {
                tickCounterDeltaMethodBoolean = renderTickCounter.getClass().getMethod("getTickDelta", boolean.class);
            }
            Object value = tickCounterDeltaMethodBoolean.invoke(renderTickCounter, true);
            if (value instanceof Float f) {
                return f;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            if (tickCounterDeltaMethodNoArgs == null) {
                tickCounterDeltaMethodNoArgs = renderTickCounter.getClass().getMethod("getTickDelta");
            }
            Object value = tickCounterDeltaMethodNoArgs.invoke(renderTickCounter);
            if (value instanceof Float f) {
                return f;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return 1.0f;
    }

    private static float quantizeScale(float scale) {
        float clamped = Math.max(0.5f, Math.min(3.0f, scale));
        return Math.round(clamped * 20.0f) / 20.0f;
    }

    private static float distanceScale(double distance) {
        double raw = 1.0 / (1.0 + (distance * 0.03));
        return (float) Math.max(0.45, Math.min(1.0, raw));
    }

    private static double resolveDynamicFov(GameRenderer gameRenderer, Camera camera, float tickDelta, double fallbackFov) {
        try {
            if (gameRendererFovMethod == null) {
                for (Method method : GameRenderer.class.getDeclaredMethods()) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length != 3) {
                        continue;
                    }
                    if (parameterTypes[0] != Camera.class || parameterTypes[1] != float.class || parameterTypes[2] != boolean.class) {
                        continue;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType != double.class && returnType != float.class) {
                        continue;
                    }
                    method.setAccessible(true);
                    gameRendererFovMethod = method;
                    break;
                }
            }

            if (gameRendererFovMethod != null) {
                Object result = gameRendererFovMethod.invoke(gameRenderer, camera, tickDelta, true);
                if (result instanceof Double d) {
                    return d;
                }
                if (result instanceof Float f) {
                    return f;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return fallbackFov;
    }

    public static String currentDimensionKey(MinecraftClient client) {
        if (client.world == null) {
            return "unknown";
        }
        return client.world.getRegistryKey().getValue().toString();
    }

    public static String currentServerId(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null) {
            String raw = client.getCurrentServerEntry().address.trim().toLowerCase(Locale.ROOT);
            try {
                URI uri = URI.create("dummy://" + raw);
                String host = uri.getHost();
                int port = uri.getPort();
                if (host == null || host.isBlank()) {
                    return raw;
                }
                if (port == -1 || port == 25565) {
                    return host;
                }
                return host + ":" + port;
            } catch (IllegalArgumentException ex) {
                return raw;
            }
        }
        if (client.isInSingleplayer()) {
            return "singleplayer";
        }
        return "unknown";
    }
}
