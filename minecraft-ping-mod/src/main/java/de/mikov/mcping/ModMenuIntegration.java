package de.mikov.mcping;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            PingClientMod mod = PingClientMod.instance();
            Screen parentScreen = (Screen) parent;
            if (mod == null || mod.config() == null) {
                return parentScreen;
            }
            return new PingSettingsScreen(parentScreen, mod.config());
        };
    }
}
