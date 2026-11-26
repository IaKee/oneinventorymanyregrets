package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AreaEffectCloud.class)
public class AreaEffectCloudMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private void oimr$beginCloudTick(ServerLevel level, CallbackInfo ci) {
        HiveMindManager.beginPotionImpact(level.getServer(), false);
    }

    @WrapOperation(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean oimr$captureGroupCloudEffect(
        LivingEntity target,
        MobEffectInstance effect,
        @Nullable Entity source,
        Operation<Boolean> original
    ) {
        if (HiveMindManager.captureSplashPotionEffect(target, effect)) {
            return true;
        }
        return original.call(target, effect, source);
    }

    @WrapOperation(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;applyInstantaneousEffect(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"
        )
    )
    private void oimr$captureGroupCloudInstantaneousEffect(
        MobEffect instance,
        ServerLevel level,
        @Nullable Entity source,
        @Nullable Entity owner,
        LivingEntity target,
        int amplifier,
        double health,
        Operation<Void> original
    ) {
        if (HiveMindManager.captureInstantaneousPotionEffect(instance, target, amplifier, health)) {
            return;
        }
        original.call(instance, level, source, owner, target, amplifier, health);
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void oimr$endCloudTick(ServerLevel level, CallbackInfo ci) {
        AreaEffectCloud self = (AreaEffectCloud) (Object) this;
        HiveMindManager.endSplashPotionImpact(self, self.getOwner());
    }
}
