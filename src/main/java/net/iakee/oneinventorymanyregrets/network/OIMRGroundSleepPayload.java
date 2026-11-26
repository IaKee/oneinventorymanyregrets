package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Syncs ground-sleeping state and horizontal facing orientation for a player sleeping without a bed.
 */
public record OIMRGroundSleepPayload(
    int entityId,
    boolean groundSleeping,
    Direction orientation
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OIMRGroundSleepPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "ground_sleep"));

    public static final StreamCodec<FriendlyByteBuf, OIMRGroundSleepPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OIMRGroundSleepPayload::write, OIMRGroundSleepPayload::read);

    private static OIMRGroundSleepPayload read(FriendlyByteBuf input) {
        int entityId = input.readVarInt();
        boolean groundSleeping = input.readBoolean();
        Direction orientation = input.readEnum(Direction.class);
        if (orientation.getAxis() == Direction.Axis.Y) {
            orientation = Direction.NORTH;
        }
        return new OIMRGroundSleepPayload(entityId, groundSleeping, orientation);
    }

    private void write(FriendlyByteBuf output) {
        output.writeVarInt(this.entityId);
        output.writeBoolean(this.groundSleeping);
        Direction safeDir = this.orientation != null && this.orientation.getAxis().isHorizontal()
            ? this.orientation
            : Direction.NORTH;
        output.writeEnum(safeDir);
    }

    @Override
    public CustomPacketPayload.Type<OIMRGroundSleepPayload> type() {
        return TYPE;
    }
}
