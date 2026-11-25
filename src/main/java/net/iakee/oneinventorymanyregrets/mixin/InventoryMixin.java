package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveInventoryAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public class InventoryMixin implements HiveInventoryAccess {
    @Mutable
    @Shadow
    @Final
    private NonNullList<ItemStack> items;

    @Shadow
    @Final
    public Player player;

    @Shadow
    @Final
    private EntityEquipment equipment;

    @Override
    public NonNullList<ItemStack> oimr$getItems() {
        return this.items;
    }

    @Override
    public void oimr$setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
    private void oimr$redirectDeathDropAll(CallbackInfo ci) {
        ServerPlayer dropTarget = HiveMindManager.resolveDeathLootTarget(this.player);
        if (dropTarget == null || dropTarget == this.player) {
            return;
        }
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (!itemStack.isEmpty()) {
                ItemEntity drop = dropTarget.createItemStackToDrop(itemStack, true, false);
                if (drop != null) {
                    dropTarget.level().addFreshEntity(drop);
                }
                this.items.set(i, ItemStack.EMPTY);
            }
        }
        this.equipment.dropAll(dropTarget);
        ci.cancel();
    }
}
