package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Duck interface on {@link net.minecraft.client.renderer.entity.state.LivingEntityRenderState}
 * carrying partial-affinity translucency visual state.
 */
@Environment(EnvType.CLIENT)
public interface OIMRTranslucentRenderStateAccess {
    boolean oimr$isTranslucent();

    float oimr$getTranslucentAlpha();

    void oimr$setTranslucent(boolean translucent, float alpha);
}
