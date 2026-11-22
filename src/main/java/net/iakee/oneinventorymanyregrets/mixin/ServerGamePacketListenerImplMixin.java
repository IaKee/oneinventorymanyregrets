package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At("RETURN"))
    private void oimr$afterContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        HiveMindManager.broadcastInventoryChanges(this.player);
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("RETURN"))
    private void oimr$afterCreativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        HiveMindManager.broadcastInventoryChanges(this.player);
    }

    @Inject(method = "handleAcceptPlayerLoad", at = @At("RETURN"))
    private void oimr$afterPlayerLoaded(ServerboundPlayerLoadedPacket packet, CallbackInfo ci) {
        HiveMindManager.onPlayerLoaded(this.player);
    }
}
