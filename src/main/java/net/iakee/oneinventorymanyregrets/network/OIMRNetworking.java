package net.iakee.oneinventorymanyregrets.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Registers custom payloads and pushes active bond affinity percentages to connected clients.
 */
public final class OIMRNetworking {
    private static boolean registered = false;

    private OIMRNetworking() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.clientboundPlay().register(OIMRBondSyncPayload.TYPE, OIMRBondSyncPayload.STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncToPlayer(handler.player));
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, OIMRBondSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, OIMRBondSyncPayload.fromConfig());
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
