package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Consumable.class)
public class ConsumableMixin {
    @Inject(method = "onConsume", at = @At("HEAD"))
    private void oimr$capturePreConsumeFood(
        Level level,
        LivingEntity entity,
        ItemStack stack,
        CallbackInfoReturnable<ItemStack> cir,
        @Share("oimrConsumedFood") LocalRef<ItemStack> consumedFood,
        @Share("oimrPreFoodLevel") LocalIntRef preFoodLevel
    ) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player && stack.has(DataComponents.FOOD)) {
            consumedFood.set(stack.copyWithCount(1));
            preFoodLevel.set(player.getFoodData().getFoodLevel());
        } else {
            consumedFood.set(null);
        }
    }

    @Inject(method = "onConsume", at = @At("RETURN"))
    private void oimr$afterConsumeFood(
        Level level,
        LivingEntity entity,
        ItemStack stack,
        CallbackInfoReturnable<ItemStack> cir,
        @Share("oimrConsumedFood") LocalRef<ItemStack> consumedFood,
        @Share("oimrPreFoodLevel") LocalIntRef preFoodLevel
    ) {
        ItemStack foodCopy = consumedFood.get();
        if (foodCopy != null && entity instanceof ServerPlayer player) {
            int recovered = Math.max(0, player.getFoodData().getFoodLevel() - preFoodLevel.get());
            HiveMindManager.onPlayerConsumedFood(player, foodCopy, recovered);
        }
    }
}
