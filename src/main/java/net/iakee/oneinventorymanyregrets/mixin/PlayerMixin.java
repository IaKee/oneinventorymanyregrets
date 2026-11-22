package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveFoodDataAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.HivePlayerProgressAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void oimr$preserveInventoryForCreativeSurvivor(ServerLevel level, CallbackInfo ci) {
        if (HiveMindManager.shouldSuppressDeathInventoryDrop((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
