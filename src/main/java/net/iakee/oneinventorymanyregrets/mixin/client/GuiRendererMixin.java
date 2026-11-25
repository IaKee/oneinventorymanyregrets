package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.client.OIMRAlphaItemRenderState;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @WrapOperation(
        method = "submitBlitFromItemAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addBlitToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/BlitRenderState;)V"
        )
    )
    private void oimr$applyItemAlpha(
        GuiRenderState instance,
        BlitRenderState blit,
        Operation<Void> original,
        @Local(argsOnly = true) GuiItemRenderState itemState
    ) {
        float alpha = ((OIMRAlphaItemRenderState) (Object) itemState).oimr$getAlpha();
        if (alpha < 0.999F) {
            int c = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
            // Item atlas uses GUI_TEXTURED_PREMULTIPLIED_ALPHA, so scale RGB along with A.
            int premulColor = (c << 24) | (c << 16) | (c << 8) | c;
            blit = new BlitRenderState(
                blit.pipeline(),
                blit.textureSetup(),
                blit.pose(),
                blit.x0(),
                blit.y0(),
                blit.x1(),
                blit.y1(),
                blit.u0(),
                blit.u1(),
                blit.v0(),
                blit.v1(),
                premulColor,
                blit.scissorArea(),
                blit.bounds()
            );
        }
        original.call(instance, blit);
    }
}
