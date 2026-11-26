package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.client.ClientBondState;
import net.iakee.oneinventorymanyregrets.client.ClientUnderwaterState;
import net.iakee.oneinventorymanyregrets.effect.OIMRMobEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
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

    @WrapOperation(
        method = "computeFogColor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;nightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"
        )
    )
    private float oimr$scaleTwilightVisionFogColor(LivingEntity camera, float a, Operation<Float> original) {
        if (!camera.hasEffect(MobEffects.NIGHT_VISION) && camera.hasEffect(OIMRMobEffects.TWILIGHT_VISION)) {
            return ClientBondState.getPartialEffectScale(camera, OIMRMobEffects.TWILIGHT_VISION, a);
        }
        return original.call(camera, a);
    }

    @WrapOperation(
        method = "computeFogColor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"
        )
    )
    private boolean oimr$acceptTwilightVisionInFogColor(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (MobEffects.NIGHT_VISION.equals(effect) && !original.call(entity, effect) && entity.hasEffect(OIMRMobEffects.TWILIGHT_VISION)) {
            return true;
        }
        return original.call(entity, effect);
    }
}
