package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.client.ClientUnderwaterState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @WrapOperation(
        method = "extractPlayerState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"
        )
    )
    private boolean oimr$includeSharedUnderwaterOverlay(
        LocalPlayer instance,
        TagKey<Fluid> fluidTag,
        Operation<Boolean> original
    ) {
        return original.call(instance, fluidTag)
            || (FluidTags.WATER.equals(fluidTag) && ClientUnderwaterState.shouldApplySharedUnderwater(instance));
    }
}
