package de.mikov.mcping.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the standard crosshair near-grayscale while keeping its dynamic
 * light/dark adaptation (vanilla blend mode).
 * <p>
 * Vanilla crosshair uses {@code ONE_MINUS_DST_COLOR / ONE_MINUS_SRC_COLOR}
 * inversion blending. With a pure-white source this inverts per-channel,
 * producing coloured crosshairs on coloured backgrounds (orange → blue).
 * <p>
 * By tinting the source from white to gray (0.65), the per-channel colour
 * range is compressed from 0..1 to 0.35..0.65, making the crosshair
 * near-grayscale while preserving the adaptive brightness effect.
 * Custom crosshairs with their own colours will see the same tint but are
 * unaffected in practice because their textures define the visible colour.
 */
@Mixin(InGameHud.class)
public class CrosshairGrayscaleMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"))
    private void mcping$tintCrosshairGray(CallbackInfo ci) {
        // Tint crosshair texture from white to gray. Formula with vanilla blend:
        //   output = 0.65*(1-dst) + dst*(1-0.65) = 0.65 - 0.30*dst
        //   Black bg → 0.65 (light gray), White bg → 0.35 (dark gray)
        //   Orange bg → (0.35, 0.46, 0.65) ≈ near-gray, slight warm tint
        RenderSystem.setShaderColor(0.65f, 0.65f, 0.65f, 1.0f);
    }

    @Inject(method = "renderCrosshair", at = @At("RETURN"))
    private void mcping$resetCrosshairColor(CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
