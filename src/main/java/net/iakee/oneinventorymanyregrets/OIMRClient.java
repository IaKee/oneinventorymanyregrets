package net.iakee.oneinventorymanyregrets;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.iakee.oneinventorymanyregrets.client.ClientBondState;
import net.iakee.oneinventorymanyregrets.network.OIMRBondSyncPayload;

public class OIMRClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            OIMRBondSyncPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientBondState.update(payload))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientBondState.reset());
    }
}
