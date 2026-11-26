package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Domain-segregated (ISP) context contracts and shared runtime value types
 * used by Hive Mind services.
 */
public final class HiveMindContexts {
    private HiveMindContexts() {
    }

    public static final double FRACTIONAL_EPSILON = 1.0E-6D;

    /**
     * Common read-only hive membership queries shared across domain contexts.
     */
    public interface HiveLookup {
        boolean isAttached(ServerPlayer player);

        @Nullable String getAttachedHiveId(UUID playerId);

        List<ServerPlayer> getOnlineMembers(String hiveId);

        List<ServerPlayer> getAliveMembers(String hiveId);
    }

    public interface InventoryAmmo extends HiveLookup {
        MinecraftServer server();

        HiveMindData data();

        Set<UUID> deferredHeldItemSyncs();

        Map<UUID, Map<Integer, ItemStack>> lastSentInventoryStacks();

        Map<UUID, AmmoReservation> activeAmmoReservations();

        Set<UUID> pendingStopUseSyncs();

        void setLastAppliedSlotSync(OIMRConfig.SlotSyncState state);
    }

    public interface EffectSync extends HiveLookup {
        MinecraftServer server();

        HiveMindData data();

        @Nullable HiveMind getHiveForPlayer(ServerPlayer player);

        boolean isVitalHazardTarget(ServerPlayer player);

        Map<UUID, Set<Holder<MobEffect>>> replicatedCoarseEffects();

        Set<UUID> lastSyncedTranslucentIds();

        void setLastSyncedTranslucentIds(Set<UUID> ids);

        @Nullable SplashImpactState getSplashImpact();

        void setSplashImpact(@Nullable SplashImpactState state);

        boolean isSyncingEffects();

        void setSyncingEffects(boolean syncing);

        boolean isSuppressAffinityVitalsCascade();

        void setSuppressAffinityVitalsCascade(boolean suppress);

        boolean isApplyingUnscaledHungerEffect();

        void setApplyingUnscaledHungerEffect(boolean applying);

        @Nullable Holder<MobEffect> getActivePeriodicDotEffect();

        void setActivePeriodicDotEffect(@Nullable Holder<MobEffect> effect);

        ProgressionAndSocialService progressionAndSocial();
    }

    public interface VitalsDamage extends HiveLookup {
        MinecraftServer server();

        HiveMindData data();

        @Nullable HiveMind getHiveForPlayer(ServerPlayer player);

        boolean isVitalHazardTarget(ServerPlayer player);

        int getHungerScaleDenominator(String hiveId);

        Map<UUID, List<PendingDamageContribution>> pendingDamageSources();

        Map<UUID, Component> pendingDeathMessages();

        @Nullable Holder<MobEffect> getActivePeriodicDotEffect();

        boolean isSyncingHealth();

        void setSyncingHealth(boolean syncing);

        boolean isSyncingAffinityDamage();

        void setSyncingAffinityDamage(boolean syncing);

        boolean isSuppressAffinityVitalsCascade();

        void setSuppressAffinityVitalsCascade(boolean suppress);

        boolean isApplyingUnscaledHungerEffect();

        boolean isSyncingKnockback();

        void setSyncingKnockback(boolean syncing);

        boolean isCascadingDeath();

        DeathCascadeService deathCascade();
    }

    public interface EnvironmentSync extends HiveLookup {
        MinecraftServer server();

        HiveMindData data();

        @Nullable HiveMind getHiveForPlayer(ServerPlayer player);

        boolean isVitalHazardTarget(ServerPlayer player);

        boolean isSyncingEnvironment();

        void setSyncingEnvironment(boolean syncing);

        EffectSyncService effectSync();
    }

    public interface ProgressionSocial extends HiveLookup {
        MinecraftServer server();

        HiveMindData data();

        @Nullable String getKnownHiveId(UUID playerId);

        @Nullable HiveMind getHiveForPlayer(ServerPlayer player);

        int getHungerScaleDenominator(String hiveId);

        boolean isCascadingDeath();

        boolean isSyncingExperience();

        void setSyncingExperience(boolean syncing);

        boolean isSyncingAdvancements();

        void setSyncingAdvancements(boolean syncing);

        boolean isSyncingRecipes();

        void setSyncingRecipes(boolean syncing);

        boolean isSyncingRespawn();

        void setSyncingRespawn(boolean syncing);
    }

    public interface DeathCascade extends HiveLookup {
        HiveMindData data();

        Map<UUID, DamageSource> dyingDamageSources();

        Map<UUID, ServerPlayer> activeDeathLootTargets();

        Set<UUID> deadScreenPlayers();

        Map<UUID, CapturedDeathContext> capturedDeathContexts();

        Map<UUID, Component> pendingDeathMessages();

        Map<UUID, Component> lastHiveDeathMessages();

        Set<UUID> suppressDeathChat();

        Map<UUID, List<PendingDamageContribution>> pendingDamageSources();

        boolean isCascadingDeath();

        void setCascadingDeath(boolean cascading);

        boolean isClosingDeathContainer();

        void setClosingDeathContainer(boolean closing);

        boolean isHandlingGroupTotem();

        void setHandlingGroupTotem(boolean handling);

        void setSyncingExperience(boolean syncing);

        InventoryAndAmmoService inventoryAndAmmo();
    }

    public interface TeleportAudio extends HiveLookup {
        Collection<String> getActiveHiveIds();

        boolean isCascadingDeath();

        boolean isSyncingGroupTeleport();

        void setSyncingGroupTeleport(boolean syncing);

        Set<MobSoundDedupKey> recentMobSounds();

        long getRecentMobSoundGameTime();

        void setRecentMobSoundGameTime(long gameTime);

        List<Mob> activeMobSoundSources();
    }

    public interface TeamLifecycle extends HiveLookup {
        MinecraftServer server();

        HiveMindData data();

        @Nullable HiveMind getHiveForPlayer(ServerPlayer player);

        Collection<String> getActiveHiveIds();

        String resolvePlayerHiveId(ServerPlayer player);

        void indexAttachedPlayer(ServerPlayer player, String hiveId);

        void unindexAttachedPlayer(ServerPlayer player);

        Map<UUID, Long> pendingPostLoadChecks();

        Map<UUID, DamageSource> dyingDamageSources();

        Set<UUID> deadScreenPlayers();

        Map<UUID, ServerPlayer> activeDeathLootTargets();

        Map<UUID, Set<Holder<MobEffect>>> replicatedCoarseEffects();

        Map<UUID, List<PendingDamageContribution>> pendingDamageSources();

        Set<UUID> deferredHeldItemSyncs();

        Map<UUID, Map<Integer, ItemStack>> lastSentInventoryStacks();

        Map<UUID, AmmoReservation> activeAmmoReservations();

        Set<UUID> pendingStopUseSyncs();

        boolean isPendingInvalidAffinityChatWarning();

        void setPendingInvalidAffinityChatWarning(boolean pending);

        OIMRConfig.SlotSyncState getLastAppliedSlotSync();

        boolean isLastAppliedSyncEffects();

        void setLastAppliedSyncEffects(boolean enabled);

        int getLastAppliedAffinityPercent();

        void setLastAppliedAffinityPercent(int percent);

        void setSyncingHealth(boolean syncing);

        void setSyncingExperience(boolean syncing);

        void setSyncingAdvancements(boolean syncing);

        void setSyncingRecipes(boolean syncing);

        void setSyncingEnvironment(boolean syncing);

        void setSyncingRespawn(boolean syncing);

        int getTeamMutationDepth();

        void setTeamMutationDepth(int depth);

        TeamLifecycleService teamLifecycle();

        InventoryAndAmmoService inventoryAndAmmo();

        EffectSyncService effectSync();

        EnvironmentSyncService environmentSync();
    }

    public record AmmoReservation(
        UUID playerUuid,
        @Nullable String hiveId,
        InteractionHand hand,
        ItemStack weaponSnapshot,
        ItemStack reservedStack,
        int sourceSlot
    ) {
    }

    public record MobSoundDedupKey(String hiveId, SoundEvent sound, SoundSource source, long seed) {
    }

    public record PendingDamageContribution(DamageSource source, double amount) {
    }

    public record CapturedDeathContext(Component deathMessage, @Nullable Entity secondaryEntity, boolean collectiveStarve) {
    }

    public record SplashEffectKey(String hiveId, Holder<MobEffect> effect) {
    }

    public record InstantaneousEffectKey(String hiveId, MobEffect effect) {
    }

    public static final class InstantaneousImpactEntry {
        public final MobEffect effect;
        public ServerPlayer target;
        public int amplifier;
        public double healthFactor;

        public InstantaneousImpactEntry(MobEffect effect, ServerPlayer target, int amplifier, double healthFactor) {
            this.effect = effect;
            this.target = target;
            this.amplifier = amplifier;
            this.healthFactor = healthFactor;
        }

        public void update(ServerPlayer candidateTarget, int candidateAmplifier, double candidateHealthFactor) {
            if (candidateAmplifier > this.amplifier
                || (candidateAmplifier == this.amplifier && candidateHealthFactor > this.healthFactor)) {
                this.target = candidateTarget;
                this.amplifier = candidateAmplifier;
                this.healthFactor = candidateHealthFactor;
            }
        }
    }

    public static final class SplashImpactAccumulator {
        public final String hiveId;
        public final Holder<MobEffect> effect;
        private final boolean stackDurations;
        public int totalDuration;
        public int amplifier;
        public boolean ambient;
        public boolean visible;

        public SplashImpactAccumulator(String hiveId, MobEffectInstance first, boolean stackDurations) {
            this.hiveId = hiveId;
            this.effect = first.getEffect();
            this.stackDurations = stackDurations;
            this.totalDuration = first.getDuration();
            this.amplifier = first.getAmplifier();
            this.ambient = first.isAmbient();
            this.visible = first.isVisible();
        }

        public void add(MobEffectInstance hitEffect) {
            if (this.stackDurations) {
                this.totalDuration += hitEffect.getDuration();
            } else if (hitEffect.getDuration() > this.totalDuration) {
                this.totalDuration = hitEffect.getDuration();
            }
            if (hitEffect.getAmplifier() > this.amplifier) {
                this.amplifier = hitEffect.getAmplifier();
            }
            this.ambient = this.ambient && hitEffect.isAmbient();
            this.visible = this.visible || hitEffect.isVisible();
        }
    }

    public static final class SplashImpactState {
        private final boolean stackDurations;
        public final Map<SplashEffectKey, SplashImpactAccumulator> entries = new LinkedHashMap<>();
        public final Map<InstantaneousEffectKey, InstantaneousImpactEntry> instantEntries = new LinkedHashMap<>();

        public SplashImpactState(boolean stackDurations) {
            this.stackDurations = stackDurations;
        }

        public void record(String hiveId, MobEffectInstance hitEffect) {
            SplashEffectKey key = new SplashEffectKey(hiveId, hitEffect.getEffect());
            SplashImpactAccumulator existing = this.entries.get(key);
            if (existing == null) {
                this.entries.put(key, new SplashImpactAccumulator(hiveId, hitEffect, this.stackDurations));
            } else {
                existing.add(hitEffect);
            }
        }

        public void recordInstantaneous(
            String hiveId,
            MobEffect effect,
            ServerPlayer target,
            int amplifier,
            double healthFactor
        ) {
            InstantaneousEffectKey key = new InstantaneousEffectKey(hiveId, effect);
            InstantaneousImpactEntry existing = this.instantEntries.get(key);
            if (existing == null) {
                this.instantEntries.put(key, new InstantaneousImpactEntry(effect, target, amplifier, healthFactor));
            } else {
                existing.update(target, amplifier, healthFactor);
            }
        }

        public boolean isEmpty() {
            return this.entries.isEmpty() && this.instantEntries.isEmpty();
        }
    }
}
