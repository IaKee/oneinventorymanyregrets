package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Notifies group clients when a member drains air bubbles underwater so teammates can see who is losing breath.
 */
public record OIMRAirFeedPayload(
    String playerName,
    int drainedBubbles
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OIMRAirFeedPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "air_feed"));

    public static final StreamCodec<FriendlyByteBuf, OIMRAirFeedPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OIMRAirFeedPayload::write, OIMRAirFeedPayload::new);

    private OIMRAirFeedPayload(FriendlyByteBuf input) {
        this(
            input.readUtf(64),
            Math.max(1, input.readVarInt())
        );
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(this.playerName != null ? this.playerName : "", 64);
        output.writeVarInt(Math.max(1, this.drainedBubbles));
    }

    @Override
    public CustomPacketPayload.Type<OIMRAirFeedPayload> type() {
        return TYPE;
    }
}
