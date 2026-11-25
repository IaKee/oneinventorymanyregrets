package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Syncs the shared underwater immersion toggle and whether any living member of the player's hive is underwater.
 */
public record OIMRUnderwaterSyncPayload(boolean enabled, boolean hiveUnderwater) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OIMRUnderwaterSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "underwater_sync"));

    public static final StreamCodec<FriendlyByteBuf, OIMRUnderwaterSyncPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OIMRUnderwaterSyncPayload::write, OIMRUnderwaterSyncPayload::new);

    private OIMRUnderwaterSyncPayload(FriendlyByteBuf input) {
        this(input.readBoolean(), input.readBoolean());
    }

    private void write(FriendlyByteBuf output) {
        output.writeBoolean(this.enabled);
        output.writeBoolean(this.hiveUnderwater);
    }

    public static OIMRUnderwaterSyncPayload fromState(boolean hiveUnderwater) {
        return new OIMRUnderwaterSyncPayload(OIMRConfig.isUnderwaterMuffleEnabled(), hiveUnderwater);
    }

    @Override
    public CustomPacketPayload.Type<OIMRUnderwaterSyncPayload> type() {
        return TYPE;
    }
}
