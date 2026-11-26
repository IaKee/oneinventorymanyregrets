package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.HiveServerPlayerGameModeAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin implements HiveServerPlayerGameModeAccess {
    @Shadow
    private boolean isDestroyingBlock;

    @Shadow
    private boolean hasDelayedDestroy;

    @Override
    public boolean oimr$isDestroyingOrDelayedBlock() {
        return this.isDestroyingBlock || this.hasDelayedDestroy;
    }

    @Override
    public void oimr$setDestroyingBlockForTest(boolean destroying) {
        this.isDestroyingBlock = destroying;
        if (!destroying) {
            this.hasDelayedDestroy = false;
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void oimr$guardUseItem(
        ServerPlayer player,
        Level level,
        ItemStack itemStack,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (HiveMindManager.shouldRejectStaleOrEmptyUseItem(player, itemStack, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void oimr$afterUseItem(
        ServerPlayer player,
        Level level,
        ItemStack itemStack,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        HiveMindManager.onAfterServerPlayerUseItem(player, hand, cir.getReturnValue());
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void oimr$guardUseItemOn(
        ServerPlayer player,
        Level level,
        ItemStack itemStack,
        InteractionHand hand,
        BlockHitResult hitResult,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (HiveMindManager.shouldRejectStaleUseItemOn(player, itemStack, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void oimr$afterUseItemOn(
        ServerPlayer player,
        Level level,
        ItemStack itemStack,
        InteractionHand hand,
        BlockHitResult hitResult,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (cir.getReturnValue().consumesAction()) {
            HiveMindManager.broadcastInventoryChanges(player);
        }
    }
}
