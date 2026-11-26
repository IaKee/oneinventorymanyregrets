package net.iakee.oneinventorymanyregrets.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
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
        PayloadTypeRegistry.clientboundPlay().register(OIMRTranslucencySyncPayload.TYPE, OIMRTranslucencySyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OIMRDamageFeedPayload.TYPE, OIMRDamageFeedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OIMRFoodFeedPayload.TYPE, OIMRFoodFeedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OIMRAirFeedPayload.TYPE, OIMRAirFeedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OIMRGroundSleepPayload.TYPE, OIMRGroundSleepPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OIMRCaveSoundPayload.TYPE, OIMRCaveSoundPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
            OIMRCaveSoundPayload.TYPE,
            (payload, context) -> context.server().execute(() -> HiveMindManager.onCaveSoundEmitted(
                context.player(),
                payload.soundId(),
                payload.offsetX(),
                payload.offsetY(),
                payload.offsetZ(),
                payload.seed()
            ))
        );
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncToPlayer(handler.player);
            HiveMindManager.syncUnderwaterOnJoin(handler.player);
            HiveMindManager.syncTranslucencyOnJoin(handler.player);
        });
    }

    public static void syncTranslucencyToPlayer(ServerPlayer player, Collection<UUID> translucentIds) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, OIMRTranslucencySyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, OIMRTranslucencySyncPayload.of(translucentIds));
        }
    }

    public static void syncTranslucencyToAll(@Nullable MinecraftServer server, Collection<UUID> translucentIds) {
        if (server == null) {
            return;
        }
        OIMRTranslucencySyncPayload payload = OIMRTranslucencySyncPayload.of(translucentIds);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection != null && ServerPlayNetworking.canSend(player, OIMRTranslucencySyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
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

    public static void sendGroundSleepUpdate(ServerPlayer sleeper, boolean groundSleeping, Direction orientation) {
        OIMRGroundSleepPayload payload = new OIMRGroundSleepPayload(sleeper.getId(), groundSleeping, orientation);
        for (ServerPlayer viewer : sleeper.level().players()) {
            if (viewer.connection != null && ServerPlayNetworking.canSend(viewer, OIMRGroundSleepPayload.TYPE)) {
                ServerPlayNetworking.send(viewer, payload);
            }
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
