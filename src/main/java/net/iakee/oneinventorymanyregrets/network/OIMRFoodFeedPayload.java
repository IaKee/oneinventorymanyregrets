package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Notifies group clients when a member consumes food so it can appear in the HUD hunger feed.
 */
public record OIMRFoodFeedPayload(
    String playerName,
    ItemStack foodItem,
    int recoveredFoodPoints
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OIMRFoodFeedPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "food_feed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OIMRFoodFeedPayload> STREAM_CODEC =
        StreamCodec.ofMember(OIMRFoodFeedPayload::write, OIMRFoodFeedPayload::read);

    private static OIMRFoodFeedPayload read(RegistryFriendlyByteBuf input) {
        String playerName = input.readUtf(64);
        ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(input);
        int recovered = Math.max(0, input.readVarInt());
        return new OIMRFoodFeedPayload(playerName, stack, recovered);
    }

    private void write(RegistryFriendlyByteBuf output) {
        output.writeUtf(this.playerName != null ? this.playerName : "", 64);
        ItemStack safeStack = this.foodItem != null ? this.foodItem : ItemStack.EMPTY;
        ItemStack.OPTIONAL_STREAM_CODEC.encode(output, safeStack);
        output.writeVarInt(Math.max(0, this.recoveredFoodPoints));
    }

    @Override
    public CustomPacketPayload.Type<OIMRFoodFeedPayload> type() {
        return TYPE;
    }
}
