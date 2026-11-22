package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveEquipmentAccess;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.EnumMap;

@Mixin(EntityEquipment.class)
public class EntityEquipmentMixin implements HiveEquipmentAccess {
    @Mutable
    @Shadow
    @Final
    private EnumMap<EquipmentSlot, ItemStack> items;

    @Override
    public EnumMap<EquipmentSlot, ItemStack> oimr$getEquipmentItems() {
        return this.items;
    }

    @Override
    public void oimr$setEquipmentItems(EnumMap<EquipmentSlot, ItemStack> items) {
        this.items = items;
    }
}
