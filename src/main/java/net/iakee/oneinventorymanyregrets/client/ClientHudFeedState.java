package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.network.DamageIconType;
import net.iakee.oneinventorymanyregrets.network.OIMRAirFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRDamageFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRFoodFeedPayload;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side killfeed queues and frame-rate-independent animations for damage, hunger, and breath indicators.
 */
@Environment(EnvType.CLIENT)
public final class ClientHudFeedState {
    public static final long FADE_IN_MS = 140L;
    public static final long HOLD_MS = 2200L;
    public static final long FADE_OUT_MS = 850L;
    public static final long TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;
    public static final int MAX_VISIBLE_ENTRIES = 5;

    private static final List<DamageEntry> DAMAGE_ENTRIES = new ArrayList<>();
    private static final List<FeedEntry> RIGHT_ENTRIES = new ArrayList<>();
    private static long lastDamageTickMs = -1L;
    private static long lastRightTickMs = -1L;

    private ClientHudFeedState() {
    }

    public static synchronized void pushDamage(OIMRDamageFeedPayload payload) {
        if (payload.amount() <= 0.0F || payload.playerName().isEmpty()) {
            return;
        }
        advanceStackSlots(DAMAGE_ENTRIES);
        DAMAGE_ENTRIES.addFirst(new DamageEntry(payload.playerName(), payload.iconType(), payload.amount()));
    }

    public static synchronized void pushFood(OIMRFoodFeedPayload payload) {
        if (payload.foodItem().isEmpty() || payload.playerName().isEmpty()) {
            return;
        }
        advanceStackSlots(RIGHT_ENTRIES);
        RIGHT_ENTRIES.addFirst(new FoodEntry(payload.playerName(), payload.foodItem().copyWithCount(1), payload.recoveredFoodPoints()));
    }

    public static synchronized void pushAir(OIMRAirFeedPayload payload) {
        if (payload.drainedBubbles() <= 0 || payload.playerName().isEmpty()) {
            return;
        }
        advanceStackSlots(RIGHT_ENTRIES);
        RIGHT_ENTRIES.addFirst(new AirEntry(payload.playerName(), payload.drainedBubbles()));
    }

    public static synchronized void reset() {
        DAMAGE_ENTRIES.clear();
        RIGHT_ENTRIES.clear();
        lastDamageTickMs = -1L;
        lastRightTickMs = -1L;
    }

    public static synchronized List<DamageEntry> snapshotDamageEntries(boolean paused) {
        long nowMs = Util.getMillis();
        long deltaMs = computeDeltaMs(lastDamageTickMs, nowMs, paused);
        lastDamageTickMs = nowMs;
        advanceEntries(DAMAGE_ENTRIES, deltaMs);
        return DAMAGE_ENTRIES.isEmpty() ? List.of() : List.copyOf(DAMAGE_ENTRIES);
    }

    public static synchronized List<FeedEntry> snapshotRightEntries(boolean paused) {
        long nowMs = Util.getMillis();
        long deltaMs = computeDeltaMs(lastRightTickMs, nowMs, paused);
        lastRightTickMs = nowMs;
        advanceEntries(RIGHT_ENTRIES, deltaMs);
        return RIGHT_ENTRIES.isEmpty() ? List.of() : List.copyOf(RIGHT_ENTRIES);
    }

    private static long computeDeltaMs(long lastMs, long nowMs, boolean paused) {
        if (paused || lastMs < 0L) {
            return 0L;
        }
        return Math.min(100L, Math.max(0L, nowMs - lastMs));
    }

    private static void advanceStackSlots(List<? extends FeedEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            FeedEntry entry = entries.get(i);
            entry.targetSlot++;
            if (entry.targetSlot >= MAX_VISIBLE_ENTRIES && entry.ageMs < FADE_IN_MS + HOLD_MS) {
                entry.ageMs = FADE_IN_MS + HOLD_MS;
            }
        }
    }

    private static void advanceEntries(List<? extends FeedEntry> entries, long deltaMs) {
        if (entries.isEmpty() || deltaMs <= 0L) {
            return;
        }
        float lerpFactor = 1.0F - (float) Math.exp(-deltaMs * 0.018D);
        Iterator<? extends FeedEntry> it = entries.iterator();
        while (it.hasNext()) {
            FeedEntry entry = it.next();
            entry.ageMs += deltaMs;
            if (entry.ageMs >= TOTAL_MS) {
                it.remove();
                continue;
            }
            entry.visualSlot += (entry.targetSlot - entry.visualSlot) * lerpFactor;
        }
    }

    public static sealed class FeedEntry permits DamageEntry, FoodEntry, AirEntry {
        private final String playerName;
        long ageMs = 0L;
        int targetSlot = 0;
        float visualSlot = 0.0F;

        private FeedEntry(String playerName) {
            this.playerName = playerName;
        }

        public String playerName() {
            return this.playerName;
        }

        public float visualSlot() {
            return this.visualSlot;
        }

        public float alpha() {
            if (this.ageMs < FADE_IN_MS) {
                float t = this.ageMs / (float) FADE_IN_MS;
                return 1.0F - (1.0F - t) * (1.0F - t);
            }
            if (this.ageMs < FADE_IN_MS + HOLD_MS) {
                return 1.0F;
            }
            float t = (this.ageMs - FADE_IN_MS - HOLD_MS) / (float) FADE_OUT_MS;
            float inv = Math.max(0.0F, 1.0F - t);
            return inv * inv;
        }

        public float verticalOffsetPx(float rowStep) {
            float entranceDropPx = 0.0F;
            if (this.ageMs < FADE_IN_MS) {
                float t = this.ageMs / (float) FADE_IN_MS;
                float inv = 1.0F - t;
                entranceDropPx = 2.0F * inv * inv;
            }
            float exitRisePx = 0.0F;
            if (this.ageMs >= FADE_IN_MS + HOLD_MS) {
                float t = Math.min(1.0F, (this.ageMs - FADE_IN_MS - HOLD_MS) / (float) FADE_OUT_MS);
                exitRisePx = 7.0F * t;
            }
            return -(this.visualSlot * rowStep) - exitRisePx + entranceDropPx;
        }
    }

    public static final class DamageEntry extends FeedEntry {
        private final DamageIconType iconType;
        private final float amount;

        private DamageEntry(String playerName, DamageIconType iconType, float amount) {
            super(playerName);
            this.iconType = iconType;
            this.amount = amount;
        }

        public DamageIconType iconType() {
            return this.iconType;
        }

        public float amount() {
            return this.amount;
        }
    }

    public static final class FoodEntry extends FeedEntry {
        private final ItemStack foodItem;
        private final int recoveredFoodPoints;

        private FoodEntry(String playerName, ItemStack foodItem, int recoveredFoodPoints) {
            super(playerName);
            this.foodItem = foodItem;
            this.recoveredFoodPoints = recoveredFoodPoints;
        }

        public ItemStack foodItem() {
            return this.foodItem;
        }

        public int recoveredFoodPoints() {
            return this.recoveredFoodPoints;
        }
    }

    public static final class AirEntry extends FeedEntry {
        private final int drainedBubbles;

        private AirEntry(String playerName, int drainedBubbles) {
            super(playerName);
            this.drainedBubbles = drainedBubbles;
        }

        public int drainedBubbles() {
            return this.drainedBubbles;
        }
    }
}
