package net.iakee.oneinventorymanyregrets.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Registers custom payloads and pushes active bond affinity percentages to connected clients.
 */
public final class OIMRNetworking {
    private static boolean registered = false;
    private static @Nullable BiConsumer<ServerPlayer, CustomPacketPayload> testFeedObserver = null;

    private OIMRNetworking() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.clientboundPlay().register(OIMRBondSyncPayload.TYPE, OIMRBondSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OIMRUnderwaterSyncPayload.TYPE, OIMRUnderwaterSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OIMRDamageFeedPayload.TYPE, OIMRDamageFeedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OIMRFoodFeedPayload.TYPE, OIMRFoodFeedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OIMRAirFeedPayload.TYPE, OIMRAirFeedPayload.STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncToPlayer(handler.player);
            HiveMindManager.syncUnderwaterOnJoin(handler.player);
        });
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, OIMRBondSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, OIMRBondSyncPayload.fromConfig());
        }
    }

    public static void syncUnderwaterToPlayer(ServerPlayer player, boolean hiveUnderwater) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, OIMRUnderwaterSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, OIMRUnderwaterSyncPayload.fromState(hiveUnderwater));
        }
    }

    public static void setTestFeedObserver(@Nullable BiConsumer<ServerPlayer, CustomPacketPayload> observer) {
        testFeedObserver = observer;
    }

    public static void sendDamageFeed(
        ServerPlayer recipient,
        String victimName,
        DamageIconType iconType,
        float amount
    ) {
        OIMRDamageFeedPayload payload = new OIMRDamageFeedPayload(victimName, iconType, amount);
        if (testFeedObserver != null) {
            testFeedObserver.accept(recipient, payload);
        }
        if (recipient.connection != null && ServerPlayNetworking.canSend(recipient, OIMRDamageFeedPayload.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void sendFoodFeed(
        ServerPlayer recipient,
        String eaterName,
        ItemStack foodItem,
        int recoveredFoodPoints
    ) {
        OIMRFoodFeedPayload payload = new OIMRFoodFeedPayload(eaterName, foodItem, recoveredFoodPoints);
        if (testFeedObserver != null) {
            testFeedObserver.accept(recipient, payload);
        }
        if (recipient.connection != null && ServerPlayNetworking.canSend(recipient, OIMRFoodFeedPayload.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void sendAirFeed(
        ServerPlayer recipient,
        String playerName,
        int drainedBubbles
    ) {
        OIMRAirFeedPayload payload = new OIMRAirFeedPayload(playerName, drainedBubbles);
        if (testFeedObserver != null) {
            testFeedObserver.accept(recipient, payload);
        }
        if (recipient.connection != null && ServerPlayNetworking.canSend(recipient, OIMRAirFeedPayload.TYPE)) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void syncToAll(@Nullable MinecraftServer server) {
        if (server == null) {
            return;
        }
        OIMRBondSyncPayload payload = OIMRBondSyncPayload.fromConfig();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection != null && ServerPlayNetworking.canSend(player, OIMRBondSyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
