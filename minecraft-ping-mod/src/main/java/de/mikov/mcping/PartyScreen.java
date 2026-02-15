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
        codeField.setPlaceholder(Text.literal("Code eingeben"));
        this.addDrawableChild(codeField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Party erstellen"), button -> {
            String code = generateCode(6);
            String serverId = PingRenderUtil.currentServerId(client);
            partyState.joinParty(code, serverId);
            relayClient.sendJoin(code, serverId);
            close();
        }).dimensions(centerX - 90, centerY - 10, 180, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Beitreten"), button -> {
            String code = codeField.getText().trim().toUpperCase();
            if (!code.isBlank()) {
                String serverId = PingRenderUtil.currentServerId(client);
                partyState.joinParty(code, serverId);
                relayClient.sendJoin(code, serverId);
                close();
            }
        }).dimensions(centerX - 90, centerY + 15, 87, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Verlassen"), button -> {
            partyState.leaveParty();
            relayClient.sendLeave();
            close();
        }).dimensions(centerX + 3, centerY + 15, 87, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> close())
                .dimensions(centerX - 90, centerY + 45, 180, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        String status = partyState.inParty()
                ? "Aktiv: " + partyState.partyCode() + " @ " + partyState.serverId()
                : "Keine Party aktiv";

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 70, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), centerX, centerY - 55, 0xBFBFBF);
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
