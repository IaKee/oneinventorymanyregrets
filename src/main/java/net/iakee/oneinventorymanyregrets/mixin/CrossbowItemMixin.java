package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {
    @Inject(method = "tryLoadProjectiles", at = @At("RETURN"))
    private static void oimr$afterCrossbowLoadProjectiles(
        LivingEntity shooter,
        ItemStack heldItem,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ() && shooter instanceof ServerPlayer player) {
            HiveMindManager.onCrossbowAmmoLoaded(player);
        }
    }
}
