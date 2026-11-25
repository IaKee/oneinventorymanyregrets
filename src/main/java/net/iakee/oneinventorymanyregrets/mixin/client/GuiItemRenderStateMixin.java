package net.iakee.oneinventorymanyregrets.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.client.OIMRAlphaItemRenderState;
import net.iakee.oneinventorymanyregrets.client.OIMRItemAlphaContext;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GuiItemRenderState.class)
public class GuiItemRenderStateMixin implements OIMRAlphaItemRenderState {
    @Unique
    private float oimr$alpha = 1.0F;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void oimr$captureAlpha(CallbackInfo ci) {
        this.oimr$alpha = OIMRItemAlphaContext.getCurrentAlpha();
    }

    @Override
    public float oimr$getAlpha() {
        return this.oimr$alpha;
    }
}
