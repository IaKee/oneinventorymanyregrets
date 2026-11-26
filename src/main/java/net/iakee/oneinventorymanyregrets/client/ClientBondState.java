package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.network.OIMRBondSyncPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRUnderwaterSyncPayload;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of the active server's Hive Mind bond affinity percentages.
 */
@Environment(EnvType.CLIENT)
public final class ClientBondState {
    private static volatile boolean synced = false;
    private static volatile int hungerPercent = 100;
    private static volatile int effectsPercent = 100;
    private static volatile int damagePercent = 100;
    private static volatile boolean underwaterMuffleEnabled = true;
    private static volatile boolean hiveUnderwater = false;
    private static volatile Set<UUID> translucentPlayers = ConcurrentHashMap.newKeySet();

    private ClientBondState() {
    }

    public static void update(OIMRBondSyncPayload payload) {
        hungerPercent = Math.max(0, payload.hungerPercent());
        effectsPercent = Math.max(0, payload.effectsPercent());
        damagePercent = Math.max(0, payload.damagePercent());
        synced = true;
    }

    public static void updateUnderwater(OIMRUnderwaterSyncPayload payload) {
        underwaterMuffleEnabled = payload.enabled();
        hiveUnderwater = payload.hiveUnderwater();
    }

    public static void updateTranslucency(net.iakee.oneinventorymanyregrets.network.OIMRTranslucencySyncPayload payload) {
        Set<UUID> next = ConcurrentHashMap.newKeySet();
        next.addAll(payload.translucentPlayerIds());
        translucentPlayers = next;
    }

    public static boolean isPlayerTranslucent(UUID uuid) {
        return translucentPlayers.contains(uuid);
    }

    public static void reset() {
        synced = false;
        hungerPercent = 100;
        effectsPercent = 100;
        damagePercent = 100;
        underwaterMuffleEnabled = true;
        hiveUnderwater = false;
        translucentPlayers = ConcurrentHashMap.newKeySet();
    }

    public static boolean isSynced() {
        return synced;
    }

    public static boolean isUnderwaterMuffleEnabled() {
        return underwaterMuffleEnabled;
    }

    public static boolean isHiveUnderwater() {
        return hiveUnderwater;
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

    public static float getEffectiveEffectsFactor() {
        if (synced) {
            return Mth.clamp(effectsPercent / 100.0F, 0.0F, 1.0F);
        }
        return OIMRConfig.isSyncEffectsEnabled() ? OIMRConfig.getAffinityFactor() : 0.0F;
    }

    public static float getPartialEffectScale(LivingEntity entity, Holder<MobEffect> effect, float partialTicks) {
        MobEffectInstance inst = entity.getEffect(effect);
        if (inst == null) {
            return 0.0F;
        }
        float baseScale = !inst.endsWithin(200)
            ? 1.0F
            : 0.7F + Mth.sin((inst.getDuration() - partialTicks) * (float) Math.PI * 0.2F) * 0.3F;
        return baseScale * getEffectiveEffectsFactor();
    }
}
