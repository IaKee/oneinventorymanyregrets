package net.iakee.oneinventorymanyregrets.mixin;

import io.netty.channel.ChannelFutureListener;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin {
    @Unique
    private boolean oimr$sendingFilteredPacket = false;

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void oimr$deferConcurrentHeldItemUpdate(
        Packet<?> packet,
        @Nullable ChannelFutureListener listener,
        CallbackInfo ci
    ) {
        if (this.oimr$sendingFilteredPacket) {
            return;
        }
        Object self = this;
        if (self instanceof ServerGamePacketListenerImpl gameListener) {
            Packet<?> filtered = HiveMindManager.filterOrDeferSharedHeldItemUpdate(gameListener.player, packet);
            if (filtered == null) {
                ci.cancel();
            } else if (filtered != packet) {
                this.oimr$sendingFilteredPacket = true;
                try {
                    ((ServerCommonPacketListenerImpl) self).send(filtered, listener);
                } finally {
                    this.oimr$sendingFilteredPacket = false;
                }
                ci.cancel();
            }
        }
    }
}
