package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.iakee.oneinventorymanyregrets.hivemind.HiveFoodDataAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.HivePlayerProgressAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin implements HiveFoodDataAccess, HivePlayerProgressAccess {
    @Shadow
    protected FoodData foodData;

    @Shadow
    protected int enchantmentSeed;

    @Override
    public void oimr$setFoodData(FoodData foodData) {
        this.foodData = foodData;
    }

    @Override
    public FoodData oimr$getFoodData() {
        return this.foodData;
    }

    @Override
    public int oimr$getEnchantmentSeed() {
        return this.enchantmentSeed;
    }

    @Override
    public void oimr$setEnchantmentSeed(int seed) {
        this.enchantmentSeed = seed;
    }

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float oimr$scaleExhaustionByPartySize(float amount) {
        if ((Object) this instanceof ServerPlayer serverPlayer) {
            return HiveMindManager.scaleFoodExhaustion(serverPlayer, amount);
        }
        return amount;
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void oimr$capturePreHurtAbsorption(
        ServerLevel level,
        DamageSource source,
        float amount,
        CallbackInfo ci,
        @Share("oimrPreHurtAbsorb") LocalFloatRef preAbsorb,
        @Share("oimrHurtApplied") LocalBooleanRef hurtApplied
    ) {
        preAbsorb.set(((Player) (Object) this).getAbsorptionAmount());
        hurtApplied.set(false);
    }

    @Inject(
        method = "actuallyHurt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setAbsorptionAmount(F)V",
            shift = At.Shift.AFTER
        )
    )
    private void oimr$markHurtApplied(
        ServerLevel level,
        DamageSource source,
        float amount,
        CallbackInfo ci,
        @Share("oimrHurtApplied") LocalBooleanRef hurtApplied
    ) {
        hurtApplied.set(true);
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void oimr$onActuallyHurt(
        ServerLevel level,
        DamageSource source,
        float amount,
        CallbackInfo ci,
        @Local(argsOnly = true) float healthDamage,
        @Share("oimrPreHurtAbsorb") LocalFloatRef preAbsorb,
        @Share("oimrHurtApplied") LocalBooleanRef hurtApplied
    ) {
        if (hurtApplied.get() && (Object) this instanceof ServerPlayer serverPlayer) {
            float absorbed = Math.max(0.0F, preAbsorb.get() - serverPlayer.getAbsorptionAmount());
            HiveMindManager.onPlayerActuallyHurt(serverPlayer, source, healthDamage + absorbed);
        }
    }

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void oimr$preserveInventoryForCreativeSurvivor(ServerLevel level, CallbackInfo ci) {
        if (HiveMindManager.shouldSuppressDeathInventoryDrop((Player) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
    private void oimr$useReservedProjectile(ItemStack heldWeapon, CallbackInfoReturnable<ItemStack> cir) {
        if ((Object) this instanceof ServerPlayer serverPlayer) {
            ItemStack reserved = HiveMindManager.getReservedProjectile(serverPlayer, heldWeapon);
            if (!reserved.isEmpty()) {
                cir.setReturnValue(reserved);
            }
        }
    }
}
