package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Syncs the server's active Hive Mind bond percentages (hunger, effects, damage) to modded clients.
 */
public record OIMRBondSyncPayload(int hungerPercent, int effectsPercent, int damagePercent) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OIMRBondSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "bond_sync"));

    public static final StreamCodec<FriendlyByteBuf, OIMRBondSyncPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OIMRBondSyncPayload::write, OIMRBondSyncPayload::new);

    private OIMRBondSyncPayload(FriendlyByteBuf input) {
        this(
            Math.max(0, input.readVarInt()),
            Math.max(0, input.readVarInt()),
            Math.max(0, input.readVarInt())
        );
    }

    private void write(FriendlyByteBuf output) {
        output.writeVarInt(Math.max(0, this.hungerPercent));
        output.writeVarInt(Math.max(0, this.effectsPercent));
        output.writeVarInt(Math.max(0, this.damagePercent));
    }

    public static OIMRBondSyncPayload fromConfig() {
        int hunger = Math.max(0, Math.round(OIMRConfig.getHungerWeight() * 100.0F));
        int affinity = Math.max(OIMRConfig.MIN_AFFINITY, Math.min(OIMRConfig.MAX_AFFINITY, OIMRConfig.getAffinityPercent()));
        int effects = OIMRConfig.isSyncEffectsEnabled() ? affinity : 0;
        return new OIMRBondSyncPayload(hunger, effects, affinity);
    }

    @Override
    public CustomPacketPayload.Type<OIMRBondSyncPayload> type() {
        return TYPE;
    }
}
