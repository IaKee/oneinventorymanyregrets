package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @WrapOperation(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        )
    )
    private boolean oimr$suppressPerPlayerFireDamage(
        Entity instance,
        ServerLevel level,
        DamageSource source,
        float amount,
        Operation<Boolean> original
    ) {
        if (HiveMindManager.isFullAffinityEnvironmentShared(instance)) {
            return false;
        }
        return original.call(instance, level, source, amount);
    }

    @WrapWithCondition(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setRemainingFireTicks(I)V"
        )
    )
    private boolean oimr$suppressPerPlayerFireCountdown(Entity instance, int ticks) {
        return !HiveMindManager.isFullAffinityEnvironmentShared(instance);
    }

    @WrapWithCondition(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;clearFire()V"
        )
    )
    private boolean oimr$suppressPerPlayerBaseTickClearFire(Entity instance) {
        return !HiveMindManager.isFullAffinityEnvironmentShared(instance);
    }

    @Inject(method = "igniteForTicks", at = @At("TAIL"))
    private void oimr$onIgniteForTicks(int ticks, CallbackInfo ci) {
        HiveMindManager.onPlayerIgnited((Entity) (Object) this);
    }

    @Inject(method = "clearFire", at = @At("TAIL"))
    private void oimr$onClearFire(CallbackInfo ci) {
        HiveMindManager.onPlayerClearFire((Entity) (Object) this);
    }

    @Inject(method = "setTicksFrozen", at = @At("TAIL"))
    private void oimr$onSetTicksFrozen(int ticks, CallbackInfo ci) {
        HiveMindManager.onPlayerTicksFrozenChanged((Entity) (Object) this, ticks);
    }

    @Inject(method = "setAirSupply", at = @At("TAIL"))
    private void oimr$onSetAirSupply(int supply, CallbackInfo ci) {
        HiveMindManager.onPlayerAirSupplyChanged((Entity) (Object) this, supply);
    }
}
