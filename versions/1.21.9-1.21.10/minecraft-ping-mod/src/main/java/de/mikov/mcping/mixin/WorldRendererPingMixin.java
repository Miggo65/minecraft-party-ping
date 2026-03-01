package de.mikov.mcping.mixin;

import de.mikov.mcping.PingClientMod;
import de.mikov.mcping.PingRenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the removed {@code WorldRenderEvents.END} callback from Fabric API.
 * Injects at the end of {@link WorldRenderer#render} to draw world-space ping markers.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererPingMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void mcping$renderWorldPings(
            net.minecraft.client.util.ObjectAllocator objectAllocator,
            net.minecraft.client.render.RenderTickCounter renderTickCounter,
            boolean renderBlockOutline,
            Camera camera,
            org.joml.Matrix4f positionMatrix,
            org.joml.Matrix4f projectionMatrix,
            org.joml.Matrix4f matrix4f3,
            com.mojang.blaze3d.buffers.GpuBufferSlice gpuBufferSlice,
            org.joml.Vector4f vector4f,
            boolean bl,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        PingClientMod mod = PingClientMod.instance();
        if (mod == null || mod.config() == null) return;

        PingRenderUtil.renderWorldPings(
                camera,
                client,
                mod.getPingManager().activePings(
                        System.currentTimeMillis(),
                        mod.getPartyState().serverId(),
                        PingRenderUtil.currentDimensionKey(client)
                ),
                mod.config()
        );
    }
}
