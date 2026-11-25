package net.iakee.oneinventorymanyregrets.mixin.client;

import net.iakee.oneinventorymanyregrets.client.ClientUnderwaterState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.UnderLiquidAmbientSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UnderLiquidAmbientSoundInstance.class)
public class UnderLiquidAmbientSoundInstanceMixin {
    @Inject(method = "underwater", at = @At("HEAD"), cancellable = true)
    private static void oimr$useSharedUnderwaterPredicate(
        LocalPlayer player,
        CallbackInfoReturnable<UnderLiquidAmbientSoundInstance> cir
    ) {
        cir.setReturnValue(ClientUnderwaterState.createLoopSound(player));
    }
}
