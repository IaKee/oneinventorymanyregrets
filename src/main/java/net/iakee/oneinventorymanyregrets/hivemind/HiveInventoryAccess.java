package net.iakee.oneinventorymanyregrets.hivemind;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public interface HiveInventoryAccess {
    NonNullList<ItemStack> oimr$getItems();

    void oimr$setItems(NonNullList<ItemStack> items);
}
