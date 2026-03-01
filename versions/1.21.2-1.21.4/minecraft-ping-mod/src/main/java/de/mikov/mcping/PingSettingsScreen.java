package de.mikov.mcping;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class PingSettingsScreen extends Screen {
    private final Screen parent;
    private final PingConfig config;

    private TextFieldWidget lifetimeField;
    private TextFieldWidget colorField;
    private TextFieldWidget sizeField;
    private TextFieldWidget relayUrlField;
    private ButtonWidget playerColorsToggleButton;
    private ButtonWidget showSenderNameToggleButton;
    private boolean playerColorsEnabled;
    private boolean showSenderName;
    private Text statusText = Text.empty();

    protected PingSettingsScreen(Screen parent, PingConfig config) {
        super(Text.literal("Minecraft Ping Mod Settings"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int panelTop = Math.max(8, (this.height - 246) / 2);
        playerColorsEnabled = config.playerColorsEnabled;
        showSenderName = config.showSenderName;

        lifetimeField = new TextFieldWidget(this.textRenderer, centerX - 90, panelTop + 12, 180, 20, Text.literal("Ping Duration"));
        lifetimeField.setText(String.valueOf(config.pingLifetimeSeconds));
        lifetimeField.setPlaceholder(Text.literal("5 - 300"));
        this.addDrawableChild(lifetimeField);

        colorField = new TextFieldWidget(this.textRenderer, centerX - 90, panelTop + 50, 130, 20, Text.literal("Ping Color"));
        colorField.setText(String.format("#%06X", PingConfig.normalizeRgb(config.pingColorRgb)));
        colorField.setPlaceholder(Text.literal("#RRGGBB"));
        this.addDrawableChild(colorField);

        relayUrlField = new TextFieldWidget(this.textRenderer, centerX - 90, panelTop + 130, 180, 20, Text.literal("Relay Host"));
        relayUrlField.setText(PingConfig.normalizeRelayUrl(config.relayUrl));
        relayUrlField.setPlaceholder(Text.literal("ws://ip:8787"));
        this.addDrawableChild(relayUrlField);

        sizeField = new TextFieldWidget(this.textRenderer, centerX - 90, panelTop + 166, 87, 20, Text.literal("Ping Size"));
        sizeField.setText(String.format(java.util.Locale.ROOT, "%.2f", PingConfig.clampPingScale(config.pingScale)));
        sizeField.setPlaceholder(Text.literal("0.5 - 2.0"));
        this.addDrawableChild(sizeField);

        playerColorsToggleButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            playerColorsEnabled = !playerColorsEnabled;
            refreshPlayerColorsButtonText();
        }).dimensions(centerX + 3, panelTop + 166, 87, 20).build());
        refreshPlayerColorsButtonText();

        showSenderNameToggleButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            showSenderName = !showSenderName;
            refreshShowSenderNameButtonText();
        }).dimensions(centerX - 90, panelTop + 190, 180, 20).build());
        refreshShowSenderNameButtonText();

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Orange"), b -> setColor("#E66D00"))
            .dimensions(centerX - 90, panelTop + 76, 58, 18)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Red"), b -> setColor("#FF3333"))
            .dimensions(centerX - 29, panelTop + 76, 58, 18)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Green"), b -> setColor("#5BC236"))
            .dimensions(centerX + 32, panelTop + 76, 58, 18)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Blue"), b -> setColor("#4AA3FF"))
            .dimensions(centerX - 90, panelTop + 96, 58, 18)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Yellow"), b -> setColor("#FFD400"))
            .dimensions(centerX - 29, panelTop + 96, 58, 18)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("White"), b -> setColor("#FFFFFF"))
            .dimensions(centerX + 32, panelTop + 96, 58, 18)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveAndClose())
            .dimensions(centerX - 90, panelTop + 214, 58, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
            .dimensions(centerX - 29, panelTop + 214, 58, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> resetDefaults())
                .dimensions(centerX + 32, panelTop + 214, 58, 20)
                .build());
    }

    private void setColor(String hexColor) {
        colorField.setText(hexColor);
    }

    private void refreshPlayerColorsButtonText() {
        if (playerColorsToggleButton != null) {
            playerColorsToggleButton.setMessage(Text.literal(playerColorsEnabled ? "Player Colors: ON" : "Player Colors: OFF"));
        }
    }

    private void refreshShowSenderNameButtonText() {
        if (showSenderNameToggleButton != null) {
            showSenderNameToggleButton.setMessage(Text.literal(showSenderName ? "Show Sender Name: ON" : "Show Sender Name: OFF"));
        }
    }

    private void resetDefaults() {
        lifetimeField.setText(String.valueOf(PingConfig.defaultPingLifetimeSeconds()));
        colorField.setText(String.format("#%06X", PingConfig.defaultPingColorRgb()));
        relayUrlField.setText(PingConfig.defaultRelayUrl());
        sizeField.setText(String.format(java.util.Locale.ROOT, "%.2f", PingConfig.defaultPingScale()));
        playerColorsEnabled = PingConfig.defaultPlayerColorsEnabled();
        showSenderName = PingConfig.defaultShowSenderName();
        refreshPlayerColorsButtonText();
        refreshShowSenderNameButtonText();
        statusText = Text.literal("Defaults restored. Save to apply.");
    }

    private void saveAndClose() {
        int currentLifetime = config.pingLifetimeSeconds;
        float currentScale = config.pingScale;
        int parsedLifetime;
        try {
            parsedLifetime = Integer.parseInt(lifetimeField.getText().trim());
        } catch (NumberFormatException ex) {
            statusText = Text.literal("Invalid duration. Allowed: 5 - 300 seconds.");
            return;
        }

        int clampedLifetime = PingConfig.clampLifetimeSeconds(parsedLifetime);
        if (parsedLifetime != clampedLifetime) {
            statusText = Text.literal("Duration was clamped to 5 - 300 seconds.");
        } else {
            statusText = Text.literal("Settings saved.");
        }

        int parsedColor = PingConfig.parseRgbOrDefault(colorField.getText(), config.pingColorRgb);
        if (parsedColor == config.pingColorRgb && !isValidHexColor(colorField.getText())) {
            statusText = Text.literal("Invalid color. Format: #RRGGBB");
            return;
        }

        String parsedRelayUrl = PingConfig.normalizeRelayUrl(relayUrlField.getText());

        float parsedScale = PingConfig.parsePingScaleOrDefault(sizeField.getText(), config.pingScale);
        if (!isValidScale(sizeField.getText())) {
            statusText = Text.literal("Invalid size. Allowed: 0.5 - 2.0");
            return;
        }

        config.pingLifetimeSeconds = clampedLifetime;
        config.pingColorRgb = parsedColor;
        config.relayUrl = parsedRelayUrl;
        config.pingScale = parsedScale;
        config.playerColorsEnabled = playerColorsEnabled;
        config.showSenderName = showSenderName;
        config.save();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Ping settings saved."), true);
        }

        close();

        if (client.player != null) {
            if (currentLifetime != clampedLifetime) {
                client.player.sendMessage(Text.literal("Ping duration active: " + clampedLifetime + "s"), true);
            }
            if (Math.abs(currentScale - parsedScale) > 0.001f) {
                client.player.sendMessage(Text.literal(String.format(java.util.Locale.ROOT, "Ping size active: %.2f", parsedScale)), true);
            }
        }
    }

    private boolean isValidHexColor(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized.matches("[0-9a-fA-F]{6}");
    }

    private boolean isValidScale(String value) {
        if (value == null || value.trim().isBlank()) {
            return false;
        }
        String normalized = value.trim().replace(',', '.');
        try {
            float parsed = Float.parseFloat(normalized);
            return parsed >= 0.5f && parsed <= 2.0f;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int panelTop = Math.max(8, (this.height - 246) / 2);
        int previewColor = colorField == null
            ? config.pingColorRgb
            : PingConfig.parseRgbOrDefault(colorField.getText(), config.pingColorRgb);
        int previewLeft = centerX + 46;
        int previewTop = panelTop + 50;
        int previewRight = centerX + 92;
        int previewBottom = panelTop + 70;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, panelTop - 10, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Ping Duration (seconds):"), centerX - 90, panelTop, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Ping Color (#RRGGBB):"), centerX - 90, panelTop + 38, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Relay Host (ws://ip:port):"), centerX - 90, panelTop + 118, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Ping Size (0.5 - 2.0):"), centerX - 90, panelTop + 154, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Preview:"), previewLeft, previewTop - 11, 0xFFFFFFFF);

        context.fill(previewLeft, previewTop, previewRight, previewBottom, 0xFF000000 | previewColor);

        if (!statusText.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, statusText, centerX, panelTop + 238, 0xFFFFCC66);
        }
    }
}
