package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.client.ClientUnderwaterState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Unique
    private boolean oimr$wasUnderwaterAudio = false;

    @WrapOperation(
        method = { "aiStep", "getWaterVision" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"
        )
    )
    private boolean oimr$includeSharedWaterVision(
        LocalPlayer instance,
        TagKey<Fluid> fluidTag,
        Operation<Boolean> original
    ) {
        return original.call(instance, fluidTag)
            || (FluidTags.WATER.equals(fluidTag) && ClientUnderwaterState.shouldApplySharedUnderwater(instance));
    }

    @WrapWithCondition(
        method = "updateIsUnderwater",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"
        )
    )
    private boolean oimr$suppressVanillaUnderwaterEnterExitSound(
        Level instance,
        double x,
        double y,
        double z,
        SoundEvent sound,
        SoundSource source,
        float volume,
        float pitch,
        boolean distanceDelay
    ) {
        return false;
    }

    @WrapOperation(
        method = "updateIsUnderwater",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"
        )
    )
    private SoundEngine.PlayResult oimr$suppressVanillaUnderwaterLoopStart(
        SoundManager instance,
        SoundInstance sound,
        Operation<SoundEngine.PlayResult> original
    ) {
        return null;
    }

    @Inject(method = "updateIsUnderwater", at = @At("RETURN"))
    private void oimr$syncSharedUnderwaterAmbientAudio(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.isSpectator()) {
            this.oimr$wasUnderwaterAudio = false;
            return;
        }
        boolean prevAudio = this.oimr$wasUnderwaterAudio;
        boolean nowAudio = cir.getReturnValueZ() || ClientUnderwaterState.shouldApplySharedUnderwater(self);
        this.oimr$wasUnderwaterAudio = nowAudio;
        if (!prevAudio && nowAudio) {
            self.level().playLocalSound(
                self.getX(),
                self.getY(),
                self.getZ(),
                SoundEvents.AMBIENT_UNDERWATER_ENTER,
                SoundSource.AMBIENT,
                1.0F,
                1.0F,
                false
            );
            this.minecraft.getSoundManager().play(ClientUnderwaterState.createLoopSound(self));
        } else if (prevAudio && !nowAudio) {
            self.level().playLocalSound(
                self.getX(),
                self.getY(),
                self.getZ(),
                SoundEvents.AMBIENT_UNDERWATER_EXIT,
                SoundSource.AMBIENT,
                1.0F,
                1.0F,
                false
            );
        }
    }
}
