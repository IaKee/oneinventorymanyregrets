package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.hivemind.HiveEntityPortalAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.HivePortalProcessorAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin implements HiveEntityPortalAccess {
    @Shadow
    public @Nullable PortalProcessor portalProcess;

    @Override
    public @Nullable PortalProcessor oimr$getPortalProcess() {
        return this.portalProcess;
    }

    @Override
    public void oimr$setPortalProcess(@Nullable PortalProcessor portalProcess) {
        this.portalProcess = portalProcess;
    }
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

    @WrapOperation(
        method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"
        )
    )
    private void oimr$trackMobSoundSource(
        Level level,
        @Nullable Entity except,
        double x,
        double y,
        double z,
        SoundEvent sound,
        SoundSource source,
        float volume,
        float pitch,
        Operation<Void> original
    ) {
        if (!level.isClientSide() && (Object) this instanceof Mob mob) {
            HiveMindManager.pushActiveMobSoundSource(mob);
            try {
                original.call(level, except, x, y, z, sound, source, volume, pitch);
            } finally {
                HiveMindManager.popActiveMobSoundSource(mob);
            }
            return;
        }
        original.call(level, except, x, y, z, sound, source, volume, pitch);
    }

    @WrapOperation(
        method = "teleportToPortalDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;"
        )
    )
    private @Nullable Entity oimr$teleportHiveOnPortal(
        Entity instance,
        TeleportTransition transition,
        Operation<Entity> original
    ) {
        Portal portal = this.portalProcess != null ? ((HivePortalProcessorAccess) this.portalProcess).oimr$getPortal() : null;
        Entity result = original.call(instance, transition);
        if (result instanceof ServerPlayer teleported && (portal instanceof NetherPortalBlock || portal instanceof EndPortalBlock)) {
            HiveMindManager.onPortalTeleport(teleported, transition);
        }
        return result;
    }

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void oimr$hideDroppedEntitiesOnDeathScreen(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (HiveMindManager.shouldHideDroppedEntityFromPlayer((Entity) (Object) this, player)) {
            cir.setReturnValue(false);
        }
    }
}
