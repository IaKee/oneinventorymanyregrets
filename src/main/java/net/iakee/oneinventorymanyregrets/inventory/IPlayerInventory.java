package net.iakee.oneinventorymanyregrets.inventory;

import net.minecraft.entity.EntityEquipment;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public interface IPlayerInventory
{
    default void sharedInventoryTick(SharedInventory source)
    {
        throw new UnsupportedOperationException("This should be overwritten in the mixin");
    }

    default void sharedInventoryClear(SharedInventory source)
    {
        throw new UnsupportedOperationException("This should be overwritten in the mixin");
    }

    default void sharedInventorySave(SharedInventory source)
    {
        throw new UnsupportedOperationException("This should be overwritten in the mixin");
    }

    default void sharedInventoryLoad(SharedInventory source)
    {
        throw new UnsupportedOperationException("This should be overwritten in the mixin");
    }

    record SavedInventory(DefaultedList<ItemStack> main, EntityEquipment equipment)
    {
        public SavedInventory(int mainSize)
        {
            this(DefaultedList.ofSize(mainSize, ItemStack.EMPTY), new EntityEquipment());
        }
    }
}
