package net.iakee.oneinventorymanyregrets.hivemind;

import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEquipment;
import org.jspecify.annotations.Nullable;

public interface HiveLivingEntityAccess {
    EntityEquipment oimr$getEquipment();

    void oimr$detectEquipmentUpdates();

    int oimr$decreaseAirSupply(int currentSupply);

    boolean oimr$checkTotemDeathProtection(DamageSource source);

    void oimr$setGroundSleeping(boolean groundSleeping, @Nullable Direction orientation);

    boolean oimr$isGroundSleeping();
}
