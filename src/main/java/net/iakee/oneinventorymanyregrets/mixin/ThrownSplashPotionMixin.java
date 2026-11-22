package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownSplashPotion.class)
public class ThrownSplashPotionMixin {
    @Inject(method = "onHitAsPotion", at = @At("HEAD"))
    private void oimr$beginSplashImpact(ServerLevel level, ItemStack potionItem, HitResult hitResult, CallbackInfo ci) {
        HiveMindManager.beginSplashPotionImpact();
    }

    @WrapOperation(
        method = "onHitAsPotion",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean oimr$captureGroupSplashEffect(
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

    @Inject(method = "onHitAsPotion", at = @At("RETURN"))
    private void oimr$endSplashImpact(ServerLevel level, ItemStack potionItem, HitResult hitResult, CallbackInfo ci) {
        HiveMindManager.endSplashPotionImpact((ThrownSplashPotion) (Object) this);
    }
}
