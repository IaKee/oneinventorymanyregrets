package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.OptionalInt;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements HiveLivingEntityAccess {
    @Shadow
    @Final
    protected EntityEquipment equipment;

    @Unique
    private boolean oimr$tickingEffects = false;

    @Invoker("detectEquipmentUpdates")
    protected abstract void oimr$invokeDetectEquipmentUpdates();

    @Invoker("decreaseAirSupply")
    protected abstract int oimr$invokeDecreaseAirSupply(int currentSupply);

    @Invoker("checkTotemDeathProtection")
    protected abstract boolean oimr$invokeCheckTotemDeathProtection(DamageSource source);

    @Override
    public EntityEquipment oimr$getEquipment() {
        return this.equipment;
    }

    @Override
    public void oimr$detectEquipmentUpdates() {
        this.oimr$invokeDetectEquipmentUpdates();
    }

    @Override
    public int oimr$decreaseAirSupply(int currentSupply) {
        return this.oimr$invokeDecreaseAirSupply(currentSupply);
    }

    @Override
    public boolean oimr$checkTotemDeathProtection(DamageSource source) {
        return this.oimr$invokeCheckTotemDeathProtection(source);
    }

    @Inject(method = "decreaseAirSupply", at = @At("HEAD"), cancellable = true)
    private void oimr$sharedRespirationAirSupply(int currentSupply, CallbackInfoReturnable<Integer> cir) {
        OptionalInt adjusted = HiveMindManager.tryDecreaseAirSupplyWithGroupRespiration(
            (LivingEntity) (Object) this,
            currentSupply
        );
        if (adjusted.isPresent()) {
            cir.setReturnValue(adjusted.getAsInt());
        }
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void oimr$handleHiveTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (cir.getReturnValueZ()) {
            HiveMindManager.onTotemActivated(self);
        } else if (HiveMindManager.tryActivateGroupTotem(self, source)) {
            cir.setReturnValue(true);
        }
    }

    @WrapWithCondition(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setAirSupply(I)V"
        )
    )
    private boolean oimr$suppressPerPlayerAirTick(LivingEntity instance, int supply) {
        return !HiveMindManager.isFullAffinityEnvironmentShared(instance);
    }

    @WrapOperation(
        method = "baseTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        )
    )
    private boolean oimr$suppressPerPlayerDrownDamage(
        LivingEntity instance,
        ServerLevel level,
        DamageSource source,
        float amount,
        Operation<Boolean> original
    ) {
        if (source.is(DamageTypeTags.IS_DROWNING) && HiveMindManager.isFullAffinityEnvironmentShared(instance)) {
            return false;
        }
        return original.call(instance, level, source, amount);
    }

    @WrapWithCondition(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setTicksFrozen(I)V"
        )
    )
    private boolean oimr$suppressPerPlayerFreezeThaw(LivingEntity instance, int ticks) {
        return !HiveMindManager.isFullAffinityEnvironmentShared(instance);
    }

    @WrapOperation(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        )
    )
    private boolean oimr$suppressPerPlayerFreezeDamage(
        LivingEntity instance,
        ServerLevel level,
        DamageSource source,
        float amount,
        Operation<Boolean> original
    ) {
        if (source.is(DamageTypeTags.IS_FREEZING) && HiveMindManager.isFullAffinityEnvironmentShared(instance)) {
            return false;
        }
        return original.call(instance, level, source, amount);
    }

    @Inject(method = "heal", at = @At("HEAD"))
    private void oimr$capturePreHeal(float amount, CallbackInfo ci, @Share("oimrPreHealHealth") LocalFloatRef preHealth) {
        preHealth.set(((LivingEntity) (Object) this).getHealth());
    }

    @Inject(method = "heal", at = @At("RETURN"))
    private void oimr$afterHeal(float amount, CallbackInfo ci, @Share("oimrPreHealHealth") LocalFloatRef preHealth) {
        LivingEntity self = (LivingEntity) (Object) this;
        float healed = self.getHealth() - preHealth.get();
        if (healed > 0.0F) {
            HiveMindManager.onPlayerHealed(self, healed);
        }
    }

    @Inject(method = "setHealth", at = @At("TAIL"))
    private void oimr$onSetHealth(float health, CallbackInfo ci) {
        HiveMindManager.onHealthChanged((LivingEntity) (Object) this);
    }

    @Inject(method = "setAbsorptionAmount", at = @At("TAIL"))
    private void oimr$onSetAbsorptionAmount(float absorptionAmount, CallbackInfo ci) {
        HiveMindManager.onAbsorptionChanged((LivingEntity) (Object) this);
    }

    @Inject(method = "tickEffects", at = @At("HEAD"))
    private void oimr$beginTickEffects(CallbackInfo ci) {
        this.oimr$tickingEffects = true;
    }

    @Inject(method = "tickEffects", at = @At("RETURN"))
    private void oimr$endTickEffects(CallbackInfo ci) {
        this.oimr$tickingEffects = false;
    }

    @Inject(method = "onEffectAdded", at = @At("TAIL"))
    private void oimr$onEffectAdded(MobEffectInstance effect, @Nullable Entity source, CallbackInfo ci) {
        HiveMindManager.onEffectAddedOrUpdated((LivingEntity) (Object) this, effect, source);
    }

    @Inject(method = "onEffectUpdated", at = @At("TAIL"))
    private void oimr$onEffectUpdated(MobEffectInstance effect, boolean doRefreshAttributes, @Nullable Entity source, CallbackInfo ci) {
        if (!doRefreshAttributes) {
            return;
        }
        if (this.oimr$tickingEffects) {
            HiveMindManager.onEffectDowngraded((LivingEntity) (Object) this, effect);
            return;
        }
        HiveMindManager.onEffectAddedOrUpdated((LivingEntity) (Object) this, effect, source);
    }

    @Inject(method = "onEffectsRemoved", at = @At("TAIL"))
    private void oimr$onEffectsRemoved(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        HiveMindManager.onEffectsRemoved((LivingEntity) (Object) this, effects);
    }

    @Inject(
        method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
        at = @At("HEAD")
    )
    private void oimr$capturePreKnockback(
        double power,
        double xd,
        double zd,
        DamageSource source,
        float damage,
        boolean comesFromEffect,
        CallbackInfo ci,
        @Share("oimrPreVel") LocalRef<Vec3> preVel,
        @Share("oimrRawPower") LocalDoubleRef rawPower
    ) {
        preVel.set(((LivingEntity) (Object) this).getDeltaMovement());
        rawPower.set(power);
    }

    @Inject(
        method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V",
            shift = At.Shift.AFTER
        )
    )
    private void oimr$afterKnockback(
        double power,
        double xd,
        double zd,
        DamageSource source,
        float damage,
        boolean comesFromEffect,
        CallbackInfo ci,
        @Share("oimrPreVel") LocalRef<Vec3> preVel,
        @Share("oimrRawPower") LocalDoubleRef rawPower
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 pre = preVel.get();
        if (pre == null) {
            return;
        }
        Vec3 post = self.getDeltaMovement();
        double appliedXd = pre.x / 2.0 - post.x;
        double appliedZd = pre.z / 2.0 - post.z;
        HiveMindManager.onPlayerKnockback(self, rawPower.get(), appliedXd, appliedZd, source, damage, comesFromEffect);
    }

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void oimr$preventFriendlyPetAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (HiveMindManager.shouldPreventPetAttack((LivingEntity) (Object) this, target)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "hurtServer",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Float;isNaN(F)Z"
        ),
        cancellable = true
    )
    private void oimr$suppressDuplicateEnvFireHurt(
        ServerLevel level,
        DamageSource source,
        float damage,
        CallbackInfoReturnable<Boolean> cir,
        @Share("oimrPreCooldownFireDamage") LocalFloatRef preCooldownDamage,
        @Share("oimrEnvFireCandidate") LocalBooleanRef envCandidate
    ) {
        preCooldownDamage.set(damage);
        envCandidate.set(true);
        if (HiveMindManager.shouldSuppressDuplicateEnvFireHurt((LivingEntity) (Object) this, source, damage)) {
            envCandidate.set(false);
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(
        method = "hurtServer",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;damageCooldownTime:I",
            opcode = Opcodes.GETFIELD
        ),
        argsOnly = true
    )
    private float oimr$adjustEnvFireHurtDamage(float damage, ServerLevel level, DamageSource source) {
        return HiveMindManager.adjustEnvFireDamage((LivingEntity) (Object) this, source, damage);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void oimr$recordEnvFireHurt(
        ServerLevel level,
        DamageSource source,
        float damage,
        CallbackInfoReturnable<Boolean> cir,
        @Share("oimrPreCooldownFireDamage") LocalFloatRef preCooldownDamage,
        @Share("oimrEnvFireCandidate") LocalBooleanRef envCandidate
    ) {
        if (cir.getReturnValueZ() && envCandidate.get()) {
            HiveMindManager.recordEnvFireHurtIfApplicable((LivingEntity) (Object) this, source, preCooldownDamage.get());
        }
    }

    @WrapOperation(
        method = "dropExperience",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"
        )
    )
    private void oimr$redirectDeathExperience(
        ServerLevel level,
        Vec3 pos,
        int amount,
        Operation<Void> original
    ) {
        if ((Object) this instanceof ServerPlayer player) {
            ServerPlayer dropTarget = HiveMindManager.resolveDeathLootTarget(player);
            if (dropTarget != null && dropTarget != player) {
                original.call(dropTarget.level(), dropTarget.position(), amount);
                return;
            }
        }
        original.call(level, pos, amount);
    }

    @Inject(
        method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void oimr$redirectDeathContainerCloseDrop(
        ItemStack itemStack,
        boolean thrownFromHand,
        Prediction prediction,
        CallbackInfoReturnable<@Nullable ItemEntity> cir
    ) {
        if (itemStack.isEmpty()) {
            return;
        }
        ServerPlayer dropTarget = HiveMindManager.resolveContainerCloseDropTarget((LivingEntity) (Object) this);
        if (dropTarget == null || dropTarget == (Object) this) {
            return;
        }
        ItemEntity drop = dropTarget.createItemStackToDrop(itemStack, true, thrownFromHand);
        if (drop != null) {
            dropTarget.level().addFreshEntity(drop);
        }
        cir.setReturnValue(drop);
    }

    @Inject(method = "startUsingItem", at = @At("TAIL"))
    private void oimr$afterStartUsingItem(InteractionHand hand, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide() && self instanceof ServerPlayer player && self.isUsingItem()) {
            HiveMindManager.onPlayerStartUsingItem(player, hand, self.getUseItem());
        }
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void oimr$beforeStopUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide() && self instanceof ServerPlayer player) {
            HiveMindManager.onPlayerStopUsingItem(player);
        }
    }

    @Inject(method = "stopUsingItem", at = @At("RETURN"))
    private void oimr$afterStopUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide() && self instanceof ServerPlayer player) {
            HiveMindManager.onAfterPlayerStopUsingItem(player);
        }
    }

    @Inject(method = "completeUsingItem", at = @At("RETURN"))
    private void oimr$afterCompleteUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide() && self instanceof ServerPlayer player) {
            HiveMindManager.broadcastInventoryChanges(player);
        }
    }
}
