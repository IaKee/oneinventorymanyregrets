package net.iakee.oneinventorymanyregrets.hivemind;

import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;

import java.util.stream.Stream;

public interface HiveFoxAccess {
    Stream<EntityReference<LivingEntity>> oimr$getTrustedEntities();

    void oimr$addTrustedEntity(LivingEntity entity);
}
