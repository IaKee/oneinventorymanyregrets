package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.client.ClientBondState;
import net.iakee.oneinventorymanyregrets.effect.OIMRMobEffects;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"
        )
    )
    private boolean oimr$acceptReplicatedVisionEffects(LocalPlayer player, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (original.call(player, effect)) {
            return true;
        }
        if (MobEffects.NIGHT_VISION.equals(effect) && player.hasEffect(OIMRMobEffects.TWILIGHT_VISION)) {
            return true;
        }
        return MobEffects.CONDUIT_POWER.equals(effect) && player.hasEffect(OIMRMobEffects.CONDUIT_ECHO);
    }

    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;nightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"
        )
    )
    private float oimr$scaleTwilightVisionLightmap(LivingEntity camera, float a, Operation<Float> original) {
        if (!camera.hasEffect(MobEffects.NIGHT_VISION) && camera.hasEffect(OIMRMobEffects.TWILIGHT_VISION)) {
            return ClientBondState.getPartialEffectScale(camera, OIMRMobEffects.TWILIGHT_VISION, a);
        }
        return original.call(camera, a);
    }

    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getWaterVision()F"
        )
    )
    private float oimr$scaleConduitEchoWaterVision(LocalPlayer player, Operation<Float> original) {
        float waterVision = original.call(player);
        if (!player.hasEffect(MobEffects.CONDUIT_POWER) && player.hasEffect(OIMRMobEffects.CONDUIT_ECHO)) {
            return waterVision * ClientBondState.getEffectiveEffectsFactor();
        }
        return waterVision;
    }
}
