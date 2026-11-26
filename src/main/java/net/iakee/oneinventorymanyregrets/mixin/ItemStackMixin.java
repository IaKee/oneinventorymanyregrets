package net.iakee.oneinventorymanyregrets.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void oimr$beforeApplyDamage(
        int newDamage,
        @Nullable ServerPlayer player,
        Consumer<ItemStack> onBreak,
        CallbackInfo ci,
        @Share("oimrPreBreakCopy") LocalRef<@Nullable ItemStack> preBreakCopy
    ) {
        ItemStack self = (ItemStack) (Object) this;
        if (player != null && self.isDamageableItem() && newDamage >= self.getMaxDamage()) {
            preBreakCopy.set(self.copy());
            HiveMindManager.beginItemBreakHook(player);
        }
    }

    @Inject(method = "applyDamage", at = @At("RETURN"))
    private void oimr$afterApplyDamage(
        int newDamage,
        @Nullable ServerPlayer player,
        Consumer<ItemStack> onBreak,
        CallbackInfo ci,
        @Share("oimrPreBreakCopy") LocalRef<@Nullable ItemStack> preBreakCopy
    ) {
        ItemStack broken = preBreakCopy.get();
        if (player != null && broken != null) {
            HiveMindManager.endItemBreakHook(player, broken);
        }
    }
}
