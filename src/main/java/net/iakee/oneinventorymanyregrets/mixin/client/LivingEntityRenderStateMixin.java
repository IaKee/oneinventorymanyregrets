package net.iakee.oneinventorymanyregrets.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.client.OIMRTranslucentRenderStateAccess;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements OIMRTranslucentRenderStateAccess {
    @Unique
    private boolean oimr$translucent = false;

    @Unique
    private float oimr$translucentAlpha = 1.0F;

    @Override
    public boolean oimr$isTranslucent() {
        return this.oimr$translucent;
    }

    @Override
    public float oimr$getTranslucentAlpha() {
        return this.oimr$translucentAlpha;
    }

    @Override
    public void oimr$setTranslucent(boolean translucent, float alpha) {
        this.oimr$translucent = translucent;
        this.oimr$translucentAlpha = alpha;
    }
}
