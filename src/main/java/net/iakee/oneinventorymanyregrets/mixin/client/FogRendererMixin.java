package net.iakee.oneinventorymanyregrets.mixin.client;

import net.iakee.oneinventorymanyregrets.client.ClientUnderwaterState;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "getFogType", at = @At("RETURN"), cancellable = true)
    private void oimr$applySharedWaterFog(Camera camera, CallbackInfoReturnable<FogType> cir) {
        if (cir.getReturnValue() == FogType.NONE
            && camera.entity() instanceof LocalPlayer player
            && ClientUnderwaterState.shouldApplySharedUnderwater(player)) {
            cir.setReturnValue(FogType.WATER);
        }
    }
}
