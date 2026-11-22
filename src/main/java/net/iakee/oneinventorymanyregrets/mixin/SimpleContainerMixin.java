package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveInventoryAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SimpleContainer.class)
public class SimpleContainerMixin implements HiveInventoryAccess {
    @Mutable
    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    @Override
    public NonNullList<ItemStack> oimr$getItems() {
        return this.items;
    }

    @Override
    public void oimr$setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }
}
