package net.iakee.oneinventorymanyregrets.hivemind;

import net.minecraft.world.entity.EntityEquipment;

public interface HiveLivingEntityAccess {
    EntityEquipment oimr$getEquipment();

    void oimr$detectEquipmentUpdates();

    int oimr$decreaseAirSupply(int currentSupply);
}
