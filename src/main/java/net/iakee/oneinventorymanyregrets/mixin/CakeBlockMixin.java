package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CakeBlock.class)
public class CakeBlockMixin {
    @Inject(method = "eat", at = @At("HEAD"))
    private static void oimr$capturePreCakeEat(
        LevelAccessor level,
        BlockPos pos,
        BlockState state,
        Player player,
        CallbackInfoReturnable<InteractionResult> cir,
        @Share("oimrTrackCake") LocalBooleanRef trackCake,
        @Share("oimrPreFoodLevel") LocalIntRef preFoodLevel
    ) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            trackCake.set(true);
            preFoodLevel.set(serverPlayer.getFoodData().getFoodLevel());
        } else {
            trackCake.set(false);
        }
    }

    @Inject(method = "eat", at = @At("RETURN"))
    private static void oimr$afterCakeEat(
        LevelAccessor level,
        BlockPos pos,
        BlockState state,
        Player player,
        CallbackInfoReturnable<InteractionResult> cir,
        @Share("oimrTrackCake") LocalBooleanRef trackCake,
        @Share("oimrPreFoodLevel") LocalIntRef preFoodLevel
    ) {
        if (trackCake.get()
            && cir.getReturnValue().consumesAction()
            && player instanceof ServerPlayer serverPlayer) {
            int recovered = Math.max(0, serverPlayer.getFoodData().getFoodLevel() - preFoodLevel.get());
            HiveMindManager.onPlayerConsumedFood(serverPlayer, new ItemStack(Items.CAKE), recovered);
        }
    }
}
