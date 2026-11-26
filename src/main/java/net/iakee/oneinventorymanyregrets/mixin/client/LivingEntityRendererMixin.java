package net.iakee.oneinventorymanyregrets.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.client.ClientBondState;
import net.iakee.oneinventorymanyregrets.client.ClientGroundSleepState;
import net.iakee.oneinventorymanyregrets.client.OIMRTranslucentRenderStateAccess;
import net.iakee.oneinventorymanyregrets.effect.OIMRMobEffects;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Shadow
    public abstract Identifier getTextureLocation(S state);

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void oimr$extractTranslucencyState(T entity, S state, float partialTicks, CallbackInfo ci) {
        OIMRTranslucentRenderStateAccess access = (OIMRTranslucentRenderStateAccess) state;
        boolean translucent = !state.isInvisible
            && entity instanceof Player player
            && (player.hasEffect(OIMRMobEffects.TRANSLUCENCY) || ClientBondState.isPlayerTranslucent(player.getUUID()));
        if (translucent) {
            float factor = ClientBondState.getEffectiveEffectsFactor();
            float alpha = Mth.clamp(Mth.lerp(factor, 1.0F, 0.15F), 0.15F, 1.0F);
            access.oimr$setTranslucent(alpha < 0.99F, alpha);
        } else {
            access.oimr$setTranslucent(false, 1.0F);
        }
        if (state.bedOrientation == null && entity.isSleeping()) {
            Direction groundDir = ClientGroundSleepState.getOrientation(entity.getId());
            if (groundDir != null) {
                state.bedOrientation = groundDir;
                state.eyeHeight = entity.getEyeHeight(Pose.STANDING);
            }
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void oimr$useTranslucentRenderType(
        S state,
        boolean isBodyVisible,
        boolean forceTransparent,
        boolean appearGlowing,
        CallbackInfoReturnable<@Nullable RenderType> cir
    ) {
        OIMRTranslucentRenderStateAccess access = (OIMRTranslucentRenderStateAccess) state;
        if (isBodyVisible && !forceTransparent && access.oimr$isTranslucent()) {
            cir.setReturnValue(RenderTypes.entityTranslucentCull(this.getTextureLocation(state)));
        }
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void oimr$applyTranslucentTint(S state, CallbackInfoReturnable<Integer> cir) {
        OIMRTranslucentRenderStateAccess access = (OIMRTranslucentRenderStateAccess) state;
        if (access.oimr$isTranslucent()) {
            int alphaByte = Mth.clamp(Math.round(access.oimr$getTranslucentAlpha() * 255.0F), 20, 255);
            int translucentTint = ARGB.color(alphaByte, 255, 255, 255);
            cir.setReturnValue(ARGB.multiply(cir.getReturnValueI(), translucentTint));
        }
    }
}
