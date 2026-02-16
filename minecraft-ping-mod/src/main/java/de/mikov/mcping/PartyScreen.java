package de.mikov.mcping;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.security.SecureRandom;

public class PartyScreen extends Screen {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final MinecraftClient client;
    private final PartyState partyState;
    private final RelayClient relayClient;

    private TextFieldWidget codeField;

    protected PartyScreen(MinecraftClient client, PartyState partyState, RelayClient relayClient) {
        super(Text.literal("Party"));
        this.client = client;
        this.partyState = partyState;
        this.relayClient = relayClient;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        codeField = new TextFieldWidget(this.textRenderer, centerX - 90, centerY - 40, 180, 20, Text.literal("Party Code"));
        codeField.setPlaceholder(Text.literal("Enter code"));
        this.addDrawableChild(codeField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Create Party"), button -> {
            String code = generateCode(6);
            String serverId = PingRenderUtil.currentServerId(client);
            partyState.joinParty(code, serverId);
            relayClient.sendJoin(code, serverId);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Party created: " + code), false);
                client.player.sendMessage(Text.literal("Relay: " + (relayClient.isConnected() ? "connected" : "disconnected") + " | " + relayClient.relayUrl()), false);
                client.player.sendMessage(Text.literal("Server ID: " + serverId), false);
            }
            client.setScreen(null);
        }).dimensions(centerX - 90, centerY - 10, 180, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Join Party"), button -> {
            String code = codeField.getText().trim().toUpperCase();
            if (!code.isBlank()) {
                String serverId = PingRenderUtil.currentServerId(client);
                partyState.joinParty(code, serverId);
                relayClient.sendJoin(code, serverId);
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Joined party: " + code), false);
                    client.player.sendMessage(Text.literal("Relay: " + (relayClient.isConnected() ? "connected" : "disconnected") + " | " + relayClient.relayUrl()), false);
                    client.player.sendMessage(Text.literal("Server ID: " + serverId), false);
                }
                client.setScreen(null);
            }
        }).dimensions(centerX - 90, centerY + 15, 87, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Leave"), button -> {
            partyState.leaveParty();
            relayClient.sendLeave();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Left party."), false);
            }
            client.setScreen(null);
        }).dimensions(centerX + 3, centerY + 15, 87, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> client.setScreen(null))
                .dimensions(centerX - 90, centerY + 45, 180, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        String status = partyState.inParty()
            ? "Active: " + partyState.partyCode() + " @ " + partyState.serverId()
            : "No active party";
        String relayStatus = relayClient.isConnected() ? "Relay: connected" : "Relay: disconnected";

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 70, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), centerX, centerY - 55, 0xBFBFBF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(relayStatus), centerX, centerY - 42, relayClient.isConnected() ? 0x77FF77 : 0xFF7777);
    }

    private static String generateCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }
}
