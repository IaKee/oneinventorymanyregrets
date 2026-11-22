package net.iakee.oneinventorymanyregrets.hivemind;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;

public interface HiveEquipmentAccess {
    EnumMap<EquipmentSlot, ItemStack> oimr$getEquipmentItems();

    void oimr$setEquipmentItems(EnumMap<EquipmentSlot, ItemStack> items);
}
