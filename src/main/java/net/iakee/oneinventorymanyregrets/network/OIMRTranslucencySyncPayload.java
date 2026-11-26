package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Syncs the set of player UUIDs currently carrying partial-affinity Translucency
 * so clients can render those players semi-transparently.
 */
public record OIMRTranslucencySyncPayload(List<UUID> translucentPlayerIds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OIMRTranslucencySyncPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "translucency_sync"));

    public static final StreamCodec<FriendlyByteBuf, OIMRTranslucencySyncPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OIMRTranslucencySyncPayload::write, OIMRTranslucencySyncPayload::read);

    public OIMRTranslucencySyncPayload {
        translucentPlayerIds = List.copyOf(translucentPlayerIds);
    }

    public static OIMRTranslucencySyncPayload of(Collection<UUID> ids) {
        return new OIMRTranslucencySyncPayload(new ArrayList<>(ids));
    }

    private static OIMRTranslucencySyncPayload read(FriendlyByteBuf input) {
        int count = Math.max(0, Math.min(input.readVarInt(), 4096));
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(input.readUUID());
        }
        return new OIMRTranslucencySyncPayload(ids);
    }

    private void write(FriendlyByteBuf output) {
        output.writeVarInt(this.translucentPlayerIds.size());
        for (UUID id : this.translucentPlayerIds) {
            output.writeUUID(id);
        }
    }

    @Override
    public CustomPacketPayload.Type<OIMRTranslucencySyncPayload> type() {
        return TYPE;
    }
}
