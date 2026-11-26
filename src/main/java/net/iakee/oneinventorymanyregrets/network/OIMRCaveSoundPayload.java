package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Reports a client-triggered ambient mood (cave/eerie) sound to the server so it can be propagated to the group.
 */
public record OIMRCaveSoundPayload(
    Identifier soundId,
    double offsetX,
    double offsetY,
    double offsetZ,
    long seed
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OIMRCaveSoundPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "cave_sound"));

    public static final StreamCodec<FriendlyByteBuf, OIMRCaveSoundPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OIMRCaveSoundPayload::write, OIMRCaveSoundPayload::read);

    private static OIMRCaveSoundPayload read(FriendlyByteBuf input) {
        return new OIMRCaveSoundPayload(
            input.readIdentifier(),
            input.readDouble(),
            input.readDouble(),
            input.readDouble(),
            input.readLong()
        );
    }

    private void write(FriendlyByteBuf output) {
        output.writeIdentifier(this.soundId);
        output.writeDouble(this.offsetX);
        output.writeDouble(this.offsetY);
        output.writeDouble(this.offsetZ);
        output.writeLong(this.seed);
    }

    @Override
    public CustomPacketPayload.Type<OIMRCaveSoundPayload> type() {
        return TYPE;
    }
}
