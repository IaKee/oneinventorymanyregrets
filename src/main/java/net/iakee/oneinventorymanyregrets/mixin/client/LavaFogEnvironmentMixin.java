package net.iakee.oneinventorymanyregrets.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.client.ClientBondState;
import net.iakee.oneinventorymanyregrets.effect.OIMRMobEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LavaFogEnvironment.class)
public class LavaFogEnvironmentMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void oimr$applyEmberWardLavaFog(
        FogData fog,
        Camera camera,
        ClientLevel level,
        float renderDistance,
        DeltaTracker deltaTracker,
        CallbackInfo ci
    ) {
        if (!camera.entity().isSpectator()
            && camera.entity() instanceof LivingEntity livingEntity
            && !livingEntity.hasEffect(MobEffects.FIRE_RESISTANCE)
            && livingEntity.hasEffect(OIMRMobEffects.EMBER_WARD)) {
            float factor = ClientBondState.getEffectiveEffectsFactor();
            if (factor > 0.0F) {
                fog.environmentalStart = Mth.lerp(factor, 0.25F, 0.0F);
                fog.environmentalEnd = Mth.lerp(factor, 1.0F, 5.0F);
                fog.skyEnd = fog.environmentalEnd;
                fog.cloudEnd = fog.environmentalEnd;
            }
        }
    }
}
