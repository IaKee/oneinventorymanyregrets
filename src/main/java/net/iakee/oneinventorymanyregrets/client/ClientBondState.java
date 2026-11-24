package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.network.OIMRBondSyncPayload;

/**
 * Client-side cache of the active server's Hive Mind bond affinity percentages.
 */
@Environment(EnvType.CLIENT)
public final class ClientBondState {
    private static volatile boolean synced = false;
    private static volatile int hungerPercent = 100;
    private static volatile int effectsPercent = 100;
    private static volatile int damagePercent = 100;

    private ClientBondState() {
    }

    public static void update(OIMRBondSyncPayload payload) {
        hungerPercent = Math.max(0, payload.hungerPercent());
        effectsPercent = Math.max(0, payload.effectsPercent());
        damagePercent = Math.max(0, payload.damagePercent());
        synced = true;
    }

    public static void reset() {
        synced = false;
        hungerPercent = 100;
        effectsPercent = 100;
        damagePercent = 100;
    }

    public static boolean isSynced() {
        return synced;
    }

    public static int getHungerPercent() {
        return hungerPercent;
    }

    public static int getEffectsPercent() {
        return effectsPercent;
    }

    public static int getDamagePercent() {
        return damagePercent;
    }

    public static boolean isCanonicalHiveMind() {
        return hungerPercent == 100 && effectsPercent == 100 && damagePercent == 100;
    }
}
