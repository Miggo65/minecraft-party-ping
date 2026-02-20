package de.mikov.mcping.mixin;

import de.mikov.mcping.XaeroCompatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderReader", remap = false)
public class XaeroWaypointWorldRenderReaderMixin {
    @Inject(method = "isHidden(Ljava/lang/Object;Ljava/lang/Object;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void mcping$hideOwnPingWaypoints(Object waypoint, Object context, CallbackInfoReturnable<Boolean> cir) {
        if (XaeroCompatBridge.isOwnPingWaypoint(waypoint)) {
            cir.setReturnValue(true);
        }
    }
}
