package de.mikov.mcping.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crosshair grayscale tint — disabled for 1.21.6+.
 * <p>
 * {@code RenderSystem.setShaderColor()} was removed in 1.21.6, so the
 * uniform-based tint no longer works. The mixin hooks remain as no-ops
 * so the mixin JSON stays valid without changes.
 */
@Mixin(InGameHud.class)
public class CrosshairGrayscaleMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"))
    private void mcping$tintCrosshairGray(CallbackInfo ci) {
        // No-op: RenderSystem.setShaderColor() removed in 1.21.6
    }

    @Inject(method = "renderCrosshair", at = @At("RETURN"))
    private void mcping$resetCrosshairColor(CallbackInfo ci) {
        // No-op
    }
}
