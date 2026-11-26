package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.client.ClientBondState;
import net.iakee.oneinventorymanyregrets.effect.OIMRMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @WrapOperation(
        method = "renderHand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
        )
    )
    private void oimr$applyFirstPersonHandTranslucency(
        SubmitNodeCollector instance,
        ModelPart modelPart,
        PoseStack poseStack,
        RenderType renderType,
        int lightCoords,
        int overlayCoords,
        @Nullable TextureAtlasSprite sprite,
        Operation<Void> original
    ) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null
            && !player.isInvisible()
            && (player.hasEffect(OIMRMobEffects.TRANSLUCENCY) || ClientBondState.isPlayerTranslucent(player.getUUID()))) {
            float factor = ClientBondState.getEffectiveEffectsFactor();
            float alpha = Mth.clamp(Mth.lerp(factor, 1.0F, 0.15F), 0.15F, 1.0F);
            if (alpha < 0.99F) {
                int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 20, 255);
                int tintedColor = ARGB.color(alphaByte, 255, 255, 255);
                instance.submitModelPart(modelPart, poseStack, renderType, lightCoords, overlayCoords, sprite, tintedColor, 0);
                return;
            }
        }
        original.call(instance, modelPart, poseStack, renderType, lightCoords, overlayCoords, sprite);
    }
}
