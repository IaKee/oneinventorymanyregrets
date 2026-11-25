package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Notifies group clients when a member suffers original damage so it can appear in the HUD killfeed.
 */
public record OIMRDamageFeedPayload(
    String playerName,
    DamageIconType iconType,
    float amount
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OIMRDamageFeedPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "damage_feed"));

    public static final StreamCodec<FriendlyByteBuf, OIMRDamageFeedPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OIMRDamageFeedPayload::write, OIMRDamageFeedPayload::new);

    private OIMRDamageFeedPayload(FriendlyByteBuf input) {
        this(
            input.readUtf(64),
            input.readEnum(DamageIconType.class),
            Math.max(0.0F, input.readFloat())
        );
    }

    private void write(FriendlyByteBuf output) {
        output.writeUtf(this.playerName != null ? this.playerName : "", 64);
        output.writeEnum(this.iconType != null ? this.iconType : DamageIconType.GENERIC);
        output.writeFloat(Math.max(0.0F, this.amount));
    }

    @Override
    public CustomPacketPayload.Type<OIMRDamageFeedPayload> type() {
        return TYPE;
    }
}
