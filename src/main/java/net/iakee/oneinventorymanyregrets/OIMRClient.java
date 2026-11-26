package net.iakee.oneinventorymanyregrets;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.iakee.oneinventorymanyregrets.client.ClientBondState;
import net.iakee.oneinventorymanyregrets.client.ClientHudFeedState;
import net.iakee.oneinventorymanyregrets.client.OIMRHudOverlay;
import net.iakee.oneinventorymanyregrets.network.OIMRAirFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRBondSyncPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRDamageFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRFoodFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRTranslucencySyncPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRUnderwaterSyncPayload;

public class OIMRClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            OIMRBondSyncPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientBondState.update(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            OIMRUnderwaterSyncPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientBondState.updateUnderwater(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            OIMRTranslucencySyncPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientBondState.updateTranslucency(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            OIMRDamageFeedPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientHudFeedState.pushDamage(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            OIMRFoodFeedPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientHudFeedState.pushFood(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            OIMRAirFeedPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientHudFeedState.pushAir(payload))
        );
        OIMRHudOverlay.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientBondState.reset();
            ClientHudFeedState.reset();
        });
    }
}
