package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.iakee.oneinventorymanyregrets.client.ClientUnderwaterState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.UnderLiquidSubSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(UnderLiquidSubSound.class)
public class UnderLiquidSubSoundMixin {
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUnderWater()Z"
        )
    )
    private boolean oimr$keepSharedUnderwaterSubSoundsAlive(
        LocalPlayer instance,
        Operation<Boolean> original
    ) {
        return original.call(instance) || ClientUnderwaterState.isUnderwaterAudioActive(instance);
    }
}
