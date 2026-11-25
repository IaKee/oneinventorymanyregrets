package net.iakee.oneinventorymanyregrets.hivemind;

import net.minecraft.world.entity.PortalProcessor;
import org.jspecify.annotations.Nullable;

public interface HiveEntityPortalAccess {
    @Nullable PortalProcessor oimr$getPortalProcess();

    void oimr$setPortalProcess(@Nullable PortalProcessor portalProcess);
}
