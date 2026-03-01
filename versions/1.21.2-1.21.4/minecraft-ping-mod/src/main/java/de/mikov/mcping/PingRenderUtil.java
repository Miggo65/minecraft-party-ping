package de.mikov.mcping;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders ping markers as world-space billboards (like entity name tags).
 * <p>
 * The icons are drawn directly in 3D world space using the GPU's matrix pipeline,
 * so camera transforms (FOV, bob, rotation) are handled natively with no jitter.
 */
public class PingRenderUtil {

    private static final int RGB_RED    = 0xFF3333;
    private static final int RGB_GREEN  = 0x5BC236;
    private static final int RGB_BLUE   = 0x4AA3FF;

    private static final int[] PRIMARY_PLAYER_COLORS = {
        RGB_RED, RGB_GREEN, RGB_BLUE
    };

    private static final int[] EXTRA_PLAYER_COLORS = {
        0xFFD400, 0xFF8A65, 0xBA68C8, 0xF06292,
        0x26C6DA, 0xAED581, 0xFFB74D, 0xE57373
    };

    private static final Map<String, Integer> ASSIGNED_PLAYER_COLORS = new LinkedHashMap<>();

    // ── Pixel-art bitmap font ───────────────────────────────────────────────
    // Each entry: {width, row0, row1, row2, row3, row4}.
    // Rows are bitmasks; bit (width-1-col) = pixel at column col.
    // Glyphs are 3px wide (digits/letters) or 5px wide (M, N, W, m).
    private static final int[][] GLYPH = new int[128][];
    static {
        GLYPH['0'] = new int[]{3, 7,5,5,5,7};
        GLYPH['1'] = new int[]{3, 2,6,2,2,7};
        GLYPH['2'] = new int[]{3, 7,1,7,4,7};
        GLYPH['3'] = new int[]{3, 7,1,7,1,7};
        GLYPH['4'] = new int[]{3, 5,5,7,1,1};
        GLYPH['5'] = new int[]{3, 7,4,7,1,7};
        GLYPH['6'] = new int[]{3, 7,4,7,5,7};
        GLYPH['7'] = new int[]{3, 7,1,1,1,1};
        GLYPH['8'] = new int[]{3, 7,5,7,5,7};
        GLYPH['9'] = new int[]{3, 7,5,7,1,7};
        GLYPH['A'] = new int[]{3, 2,5,7,5,5};
        GLYPH['B'] = new int[]{3, 6,5,6,5,6};
        GLYPH['C'] = new int[]{3, 7,4,4,4,7};
        GLYPH['D'] = new int[]{3, 6,5,5,5,6};
        GLYPH['E'] = new int[]{3, 7,4,7,4,7};
        GLYPH['F'] = new int[]{3, 7,4,7,4,4};
        GLYPH['G'] = new int[]{3, 7,4,5,5,7};
        GLYPH['H'] = new int[]{3, 5,5,7,5,5};
        GLYPH['I'] = new int[]{3, 7,2,2,2,7};
        GLYPH['J'] = new int[]{3, 1,1,1,5,2};
        GLYPH['K'] = new int[]{3, 5,6,4,6,5};
        GLYPH['L'] = new int[]{3, 4,4,4,4,7};
        GLYPH['M'] = new int[]{5, 17,27,21,17,17};
        GLYPH['N'] = new int[]{5, 17,25,21,19,17};
        GLYPH['O'] = new int[]{3, 7,5,5,5,7};
        GLYPH['P'] = new int[]{3, 7,5,7,4,4};
        GLYPH['Q'] = new int[]{3, 7,5,5,7,1};
        GLYPH['R'] = new int[]{3, 7,5,7,6,5};
        GLYPH['S'] = new int[]{3, 7,4,7,1,7};
        GLYPH['T'] = new int[]{3, 7,2,2,2,2};
        GLYPH['U'] = new int[]{3, 5,5,5,5,7};
        GLYPH['V'] = new int[]{3, 5,5,5,5,2};
        GLYPH['W'] = new int[]{5, 17,17,21,27,10};
        GLYPH['X'] = new int[]{3, 5,5,2,5,5};
        GLYPH['Y'] = new int[]{3, 5,5,7,2,2};
        GLYPH['Z'] = new int[]{3, 7,1,2,4,7};
        GLYPH['m'] = new int[]{5, 0,27,21,21,17};
        GLYPH['.'] = new int[]{1, 0,0,0,0,1};
        GLYPH['-'] = new int[]{3, 0,0,7,0,0};
        GLYPH['_'] = new int[]{3, 0,0,0,0,7};
        GLYPH[' '] = new int[]{2, 0,0,0,0,0};
    }

    private PingRenderUtil() {
    }

    public static void resetAssignedPlayerColors() {
        ASSIGNED_PLAYER_COLORS.clear();
    }

    // ── World-space billboard rendering ─────────────────────────────────────

    /**
     * Render all active pings as world-space billboards.
     * Called from {@code WorldRenderEvents.END}.
     *
     * <p>We build our own MatrixStack (with the view rotation) and set
     * {@code RenderSystem.getModelViewMatrix()} to identity so that vertex
     * positions baked via the MatrixStack are the only transform the shader
     * sees.  This avoids the double-transform issue that occurs when the
     * shader's ModelViewMat is non-identity during world rendering.</p>
     */
    public static void renderWorldPings(Camera camera,
                                         MinecraftClient client,
                                         List<PingRecord> pings, PingConfig config) {
        PlayerEntity player = client.player;
        if (player == null || pings.isEmpty()) return;

        Vec3d cameraPos = camera.getPos();
        String ownPlayerName = client.getSession() != null
                ? client.getSession().getUsername() : "";
        float pingScale = config.pingScale();

        // Camera forward direction in world space for behind-camera culling.
        // camera.getRotation() maps camera-local → world, and camera looks
        // along -Z in its local space.
        Vector3f camForward = new Vector3f(0, 0, -1);
        camForward.rotate(camera.getRotation());

        // Build a fresh MatrixStack with the view rotation as base.
        // conjugate(camRot) = world → camera-space (view matrix).
        MatrixStack matrices = new MatrixStack();
        matrices.multiply(new Quaternionf(camera.getRotation()).conjugate());

        // ── Prevent double model-view transform ──
        // BufferRenderer.drawWithGlobalProgram() and the text shaders read
        // ModelViewMat from RenderSystem.  During world rendering this is
        // NOT identity, so our already-baked vertex positions would get
        // transformed a second time.  Setting it to identity means the
        // shader passes through our positions unchanged.
        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        RenderSystem.getModelViewMatrix().identity();

        // GL state: depth test off so pings show through walls,
        // depth mask off so we don't write to the Z-buffer (HUD/crosshair stays on top).
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        for (PingRecord ping : pings) {
            double dx = ping.position().x - cameraPos.x;
            double dy = ping.position().y - cameraPos.y;
            double dz = ping.position().z - cameraPos.z;

            // Cull pings behind the camera (dot with forward vector)
            double dot = dx * camForward.x + dy * camForward.y + dz * camForward.z;
            if (dot <= 0) continue;

            // Distance from player for the label
            double distance = Math.sqrt(
                sq(player.getX() - ping.position().x)
              + sq(player.getY() - ping.position().y)
              + sq(player.getZ() - ping.position().z));

            matrices.push();

            // 1) Translate to ping world position
            matrices.translate(dx, dy, dz);

            // 2) Billboard: rotate local axes to face the camera
            matrices.multiply(camera.getRotation());

            // 3) Scale: compensate perspective so the icon stays readable.
            //    Y is negated so that +Y in local coords = down on screen,
            //    matching our pixel-coord convention.
            float distFactor = (float) Math.max(1.0, 0.6 + distance * 0.06);
            float worldScale = Math.min(0.025f * distFactor * pingScale, 0.35f * pingScale);
            matrices.scale(worldScale, -worldScale, worldScale);

            Matrix4f posMatrix = matrices.peek().getPositionMatrix();
            int senderColor = resolvePingColorArgb(ping.sender(), ownPlayerName, config);

            // Icon (all Tessellator-based, no TextRenderer)
            if (ping.type() == PingType.WARNING) {
                drawWarningBillboard(posMatrix, senderColor);
            } else if (ping.type() == PingType.GO) {
                drawGoBillboard(posMatrix, senderColor);
            } else {
                drawNormalBillboard(posMatrix, senderColor);
            }

            // Distance label (pixel-art bitmap font)
            String distText = String.format(Locale.ROOT, "%.0fm", distance);
            drawBitmapString(posMatrix, distText, 6, 0xFFFFFFFF, 0xFF3F3F3F);

            // Sender name
            if (config.showSenderName) {
                drawBitmapString(posMatrix, ping.sender().toUpperCase(Locale.ROOT),
                    14, 0xFFBFBFBF, 0xFF2F2F2F);
            }

            matrices.pop();
        }

        // Restore GL state + model-view matrix
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.getModelViewMatrix().set(savedModelView);
    }

    // ── Billboard icon drawing ──────────────────────────────────────────────

    private static void drawNormalBillboard(Matrix4f m, int senderColor) {
        int cY = -4;
        int r = (senderColor >> 16) & 0xFF;
        int g = (senderColor >>  8) & 0xFF;
        int b =  senderColor        & 0xFF;

        int dimColor = ((int)(r * 0.6) << 16) | ((int)(g * 0.6) << 8)
                     | (int)(b * 0.6) | 0xFF000000;
        int olR = Math.min(255, (int)(r * 1.3));
        int olG = Math.min(255, (int)(g * 1.3));
        int olB = Math.min(255, (int)(b * 1.3));
        int outline = (olR << 16) | (olG << 8) | olB | 0xFF000000;
        int sc    = senderColor | 0xFF000000;
        int black = 0xFF000000;

        RenderSystem.setShader(MinecraftClient.getInstance().getShaderLoader().getOrCreateProgram(ShaderProgramKeys.POSITION_COLOR));
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS,
                                        VertexFormats.POSITION_COLOR);

        // Outer outline
        bFill(m, buf, -1, cY - 3,  2, cY - 2, outline);
        bFill(m, buf, -1, cY + 3,  2, cY + 4, outline);
        bFill(m, buf, -3, cY - 1, -2, cY + 2, outline);
        bFill(m, buf,  3, cY - 1,  4, cY + 2, outline);
        bFill(m, buf, -2, cY - 3, -1, cY - 2, outline);
        bFill(m, buf,  2, cY - 3,  3, cY - 2, outline);
        bFill(m, buf, -3, cY - 2, -2, cY - 1, outline);
        bFill(m, buf,  3, cY - 2,  4, cY - 1, outline);
        bFill(m, buf, -3, cY + 2, -2, cY + 3, outline);
        bFill(m, buf,  3, cY + 2,  4, cY + 3, outline);
        bFill(m, buf, -2, cY + 3, -1, cY + 4, outline);
        bFill(m, buf,  2, cY + 3,  3, cY + 4, outline);

        // Main ring (sender colour)
        bFill(m, buf, -1, cY - 2,  2, cY - 1, sc);
        bFill(m, buf, -1, cY + 2,  2, cY + 3, sc);
        bFill(m, buf, -2, cY - 1, -1, cY + 2, sc);
        bFill(m, buf,  2, cY - 1,  3, cY + 2, sc);
        bFill(m, buf, -2, cY - 2, -1, cY - 1, sc);
        bFill(m, buf,  2, cY - 2,  3, cY - 1, sc);
        bFill(m, buf, -2, cY + 2, -1, cY + 3, sc);
        bFill(m, buf,  2, cY + 2,  3, cY + 3, sc);

        // Inner field
        bFill(m, buf, -1, cY - 1,  2, cY + 2, dimColor);

        // Centre dot
        bFill(m, buf,  0, cY,  1, cY + 1, black);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawWarningBillboard(Matrix4f m, int senderColor) {
        int sc    = senderColor | 0xFF000000;
        int black = 0xFF101010;

        RenderSystem.setShader(MinecraftClient.getInstance().getShaderLoader().getOrCreateProgram(ShaderProgramKeys.POSITION_COLOR));
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS,
                                        VertexFormats.POSITION_COLOR);

        bFill(m, buf,  0, -9,  1, -8, sc);
        bFill(m, buf, -1, -8,  2, -7, sc);
        bFill(m, buf, -1, -7,  2, -6, sc);
        bFill(m, buf, -2, -6,  3, -5, sc);
        bFill(m, buf, -2, -5,  3, -4, sc);
        bFill(m, buf, -3, -4,  4, -3, sc);
        bFill(m, buf, -3, -3,  4, -2, sc);
        bFill(m, buf, -4, -2,  5, -1, sc);

        bFill(m, buf,  0, -7,  1, -4, black);
        bFill(m, buf,  0, -3,  1, -2, black);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawGoBillboard(Matrix4f m, int senderColor) {
        int sc = senderColor | 0xFF000000;

        RenderSystem.setShader(MinecraftClient.getInstance().getShaderLoader().getOrCreateProgram(ShaderProgramKeys.POSITION_COLOR));
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS,
                                        VertexFormats.POSITION_COLOR);

        // Mast (Gleiche Farbe wie Flagge, Mast x=0)
        bFill(m, buf, 0, -9, 1, -1, sc);

        // Flag body:
        // Mast (M) ist bei x=0
        // y=-9: MXX..XX (x=1..2, x=5..6)
        // y=-8: MXXXXXX (x=1..6)
        // y=-7: MXXXXXX (x=1..6)
        // y=-6: MXXXXXX (x=1..6)
        // y=-5: MXXXXXX (x=1..6)
        // y=-4: M..XX.. (x=3..4)
        // y=-3: M...... (Kein Flaggenstoff)
        // y=-2: M...... (Kein Flaggenstoff)
        
        bFill(m, buf, 1, -9, 3, -8, sc); // row 1, part 1
        bFill(m, buf, 5, -9, 7, -8, sc); // row 1, part 2
        
        bFill(m, buf, 1, -8, 7, -7, sc); // row 2
        bFill(m, buf, 1, -7, 7, -6, sc); // row 3
        bFill(m, buf, 1, -6, 7, -5, sc); // row 4
        bFill(m, buf, 1, -5, 7, -4, sc); // row 5
        
        bFill(m, buf, 3, -4, 5, -3, sc); // row 6

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    // ── Pixel-art text rendering ────────────────────────────────────────────

    /**
     * Draw a string as pixel-art quads, horizontally centred around x=0.
     * Uses the static {@code GLYPH} table.  Lowercase a-z is mapped to
     * uppercase; unknown characters are skipped with a small gap.
     *
     * @param m       position matrix (already has billboard + scale baked in)
     * @param text    the text to render
     * @param y       Y offset in local billboard coords (positive = down)
     * @param fg      foreground ARGB colour
     * @param shadow  shadow ARGB colour (drawn at +1,+1 offset)
     */
    private static void drawBitmapString(Matrix4f m, String text, float y,
                                          int fg, int shadow) {
        if (text == null || text.isEmpty()) return;

        float totalW = bitmapStringWidth(text);
        float x = -totalW / 2f;

        RenderSystem.setShader(MinecraftClient.getInstance().getShaderLoader().getOrCreateProgram(ShaderProgramKeys.POSITION_COLOR));
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS,
                                        VertexFormats.POSITION_COLOR);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') c = (char)(c - 32);
            int[] glyph = (c < 128) ? GLYPH[c] : null;
            if (glyph == null) { x += 4; continue; }
            int w = glyph[0];

            // Shadow pass (+1, +1)
            for (int row = 0; row < 5; row++) {
                int bits = glyph[1 + row];
                for (int col = 0; col < w; col++) {
                    if ((bits & (1 << (w - 1 - col))) != 0) {
                        bFill(m, buf, x + col + 1, y + row + 1,
                                      x + col + 2, y + row + 2, shadow);
                    }
                }
            }
            // Foreground pass
            for (int row = 0; row < 5; row++) {
                int bits = glyph[1 + row];
                for (int col = 0; col < w; col++) {
                    if ((bits & (1 << (w - 1 - col))) != 0) {
                        bFill(m, buf, x + col, y + row,
                                      x + col + 1, y + row + 1, fg);
                    }
                }
            }
            x += w + 1;
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static float bitmapStringWidth(String text) {
        float w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') c = (char)(c - 32);
            int[] glyph = (c < 128) ? GLYPH[c] : null;
            if (glyph == null) { w += 4; continue; }
            w += glyph[0] + 1;
        }
        return w > 0 ? w - 1 : 0;
    }

    // ── Tessellator helper ──────────────────────────────────────────────────

    /** Add a coloured rectangle (one quad = 4 vertices) to the buffer. */
    private static void bFill(Matrix4f m, BufferBuilder buf,
                               float x1, float y1, float x2, float y2, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>  16) & 0xFF;
        int g = (argb >>   8) & 0xFF;
        int b =  argb         & 0xFF;
        buf.vertex(m, x1, y1, 0).color(r, g, b, a);
        buf.vertex(m, x1, y2, 0).color(r, g, b, a);
        buf.vertex(m, x2, y2, 0).color(r, g, b, a);
        buf.vertex(m, x2, y1, 0).color(r, g, b, a);
    }

    private static double sq(double v) { return v * v; }

    // ── HUD rendering (ping selection overlay only) ─────────────────────────

    public static void renderPingSelectionOverlay(DrawContext drawContext,
                                                   MinecraftClient client,
                                                   PingType selectedType) {
        int width   = drawContext.getScaledWindowWidth();
        int height  = drawContext.getScaledWindowHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        TextRenderer textRenderer = client.textRenderer;

        int topY    = centerY - 26;
        int bottomY = centerY + 8;
        int boxH    = 14;

        int topFill    = selectedType == PingType.WARNING ? 0xAA992020 : 0x88333333;
        int bottomFill = selectedType == PingType.GO      ? 0xAA2B6D1B : 0x88333333;

        drawVerticalTrapezoid(drawContext, centerX, topY,    boxH, 62, 74, topFill);
        drawVerticalTrapezoid(drawContext, centerX, bottomY, boxH, 74, 62, bottomFill);

        drawContext.drawCenteredTextWithShadow(textRenderer, "\u25B2 Warning",
            centerX, topY + 3,
            selectedType == PingType.WARNING ? 0xFFFF8080 : 0xFFCCCCCC);
        drawContext.drawCenteredTextWithShadow(textRenderer, "\u2691 Move",
            centerX, bottomY + 3,
            selectedType == PingType.GO ? 0xFF9CFF7A : 0xFFCCCCCC);
    }

    private static void drawVerticalTrapezoid(DrawContext dc, int cx, int y,
                                               int h, int wTop, int wBot, int color) {
        for (int row = 0; row < h; row++) {
            float t = h <= 1 ? 0f : row / (float)(h - 1);
            int w = Math.round(wTop + (wBot - wTop) * t);
            int half = w / 2;
            dc.fill(cx - half, y + row, cx + half + 1, y + row + 1, color);
        }
    }

    // ── Colour resolution ───────────────────────────────────────────────────

    private static int resolvePingColorArgb(String sender, String ownPlayerName,
                                             PingConfig config) {
        int ownColor = config.pingColorArgb();
        if (!config.playerColorsEnabled) return ownColor;
        if (sender == null || sender.isBlank()) return ownColor;
        if (sender.equalsIgnoreCase(ownPlayerName)) return ownColor;

        String key = sender.trim().toLowerCase(Locale.ROOT);
        Integer assigned = ASSIGNED_PLAYER_COLORS.get(key);
        if (assigned != null) return 0xFF000000 | (assigned & 0x00FFFFFF);

        List<Integer> candidates = buildCandidateColors(config.pingColorRgb);
        if (candidates.isEmpty()) return ownColor;

        int picked = candidates.get(
            Math.floorMod(ASSIGNED_PLAYER_COLORS.size(), candidates.size()));
        ASSIGNED_PLAYER_COLORS.put(key, picked);
        return 0xFF000000 | (picked & 0x00FFFFFF);
    }

    private static List<Integer> buildCandidateColors(int ownRgb) {
        int own = ownRgb & 0x00FFFFFF;
        List<Integer> list = new ArrayList<>();
        for (int c : PRIMARY_PLAYER_COLORS)
            if (c != own) list.add(c);
        for (int c : EXTRA_PLAYER_COLORS)
            if (c != own && !list.contains(c)) list.add(c);
        return list;
    }

    // ── Server / dimension helpers ──────────────────────────────────────────

    public static String currentDimensionKey(MinecraftClient client) {
        return client.world == null ? "unknown"
            : client.world.getRegistryKey().getValue().toString();
    }

    public static String currentServerId(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null
                && client.getCurrentServerEntry().address != null) {
            String fromEntry = normalizeServerAddress(
                client.getCurrentServerEntry().address);
            if (!"unknown".equals(fromEntry)) return fromEntry;
        }
        try {
            if (client.getNetworkHandler() != null
                    && client.getNetworkHandler().getConnection() != null) {
                SocketAddress addr =
                    client.getNetworkHandler().getConnection().getAddress();
                if (addr instanceof InetSocketAddress inet) {
                    String host = inet.getHostString();
                    int port = inet.getPort();
                    if (host != null && !host.isBlank()) {
                        host = host.trim().toLowerCase(Locale.ROOT);
                        return (port <= 0 || port == 25565)
                            ? host : host + ":" + port;
                    }
                }
                if (addr != null) {
                    String fb = addr.toString();
                    if (fb != null && !fb.isBlank())
                        return fb.trim().toLowerCase(Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {}
        return client.isInSingleplayer() ? "singleplayer" : "unknown";
    }

    private static String normalizeServerAddress(String input) {
        if (input == null) return "unknown";
        String raw = input.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) return "unknown";
        try {
            URI uri = URI.create("dummy://" + raw);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || host.isBlank()) return raw;
            return (port == -1 || port == 25565) ? host : host + ":" + port;
        } catch (IllegalArgumentException ex) {
            return raw;
        }
    }
}
