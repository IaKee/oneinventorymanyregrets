package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobEffectInstance.class)
public class MobEffectInstanceMixin {
    @Shadow
    @Final
    private Holder<MobEffect> effect;

    @WrapOperation(
        method = "tickServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;applyEffectTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;I)Z"
        )
    )
    private boolean oimr$guardSharedPeriodicEffectTick(
        MobEffect instance,
        ServerLevel level,
        LivingEntity target,
        int amplification,
        Operation<Boolean> original
    ) {
        if (!HiveMindManager.shouldApplyPeriodicEffectTick(target, this.effect)) {
            return true;
        }
        int tickFlags = HiveMindManager.beginPeriodicEffectTick(target, this.effect);
        try {
            return original.call(instance, level, target, amplification);
        } finally {
            HiveMindManager.endPeriodicEffectTick(target, tickFlags);
        }
    }
}
