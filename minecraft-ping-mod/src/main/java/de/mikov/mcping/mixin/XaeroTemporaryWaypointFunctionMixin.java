package de.mikov.mcping.mixin;

import de.mikov.mcping.PingClientMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.hud.minimap.controls.key.function.TemporaryWaypointFunction", remap = false)
public class XaeroTemporaryWaypointFunctionMixin {
    @Inject(method = "onRelease", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcping$cancelXaeroTemporaryWaypointWhenUsingPing(CallbackInfo ci) {
        PingClientMod mod = PingClientMod.instance();
        if (mod != null && mod.isPingKeyDown()) {
            ci.cancel();
        }
    }
}
