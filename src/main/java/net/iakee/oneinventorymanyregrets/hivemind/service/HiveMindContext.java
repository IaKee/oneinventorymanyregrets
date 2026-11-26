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
 * Per-server coordination context shared across Hive Mind domain services.
 * Replaces global static mutable state so multiple server lifecycles remain isolated.
 */
public interface HiveMindContext {
    double FRACTIONAL_EPSILON = 1.0E-6D;

    MinecraftServer server();

    HiveMindData data();

    @Nullable String getAttachedHiveId(UUID playerId);

    @Nullable String getKnownHiveId(UUID playerId);

    boolean isAttached(ServerPlayer player);

    @Nullable HiveMind getHiveForPlayer(ServerPlayer player);

    List<ServerPlayer> getOnlineMembers(String hiveId);

    List<ServerPlayer> getAliveMembers(String hiveId);

    Collection<String> getActiveHiveIds();

    boolean isVitalHazardTarget(ServerPlayer player);

    int getHungerScaleDenominator(String hiveId);

    String resolvePlayerHiveId(ServerPlayer player);

    void indexAttachedPlayer(ServerPlayer player, String hiveId);

    void unindexAttachedPlayer(ServerPlayer player);

    Map<UUID, String> playerHives();

    Map<UUID, String> knownPlayerHives();

    Set<ServerPlayer> attachedPlayers();

    Map<UUID, Long> pendingPostLoadChecks();

    Map<UUID, Component> pendingDeathMessages();

    Map<UUID, Component> lastHiveDeathMessages();

    Set<UUID> suppressDeathChat();

    Map<UUID, CapturedDeathContext> capturedDeathContexts();

    Map<UUID, DamageSource> dyingDamageSources();

    Map<UUID, ServerPlayer> activeDeathLootTargets();

    Set<UUID> deadScreenPlayers();

    Map<UUID, Set<Holder<MobEffect>>> replicatedCoarseEffects();

    Set<UUID> lastSyncedTranslucentIds();

    void setLastSyncedTranslucentIds(Set<UUID> ids);

    Map<UUID, List<PendingDamageContribution>> pendingDamageSources();

    Set<UUID> deferredHeldItemSyncs();

    Map<UUID, Map<Integer, ItemStack>> lastSentInventoryStacks();

    Map<UUID, AmmoReservation> activeAmmoReservations();

    Set<UUID> pendingStopUseSyncs();

    Set<MobSoundDedupKey> recentMobSounds();

    long getRecentMobSoundGameTime();

    void setRecentMobSoundGameTime(long gameTime);

    @Nullable SplashImpactState getSplashImpact();

    void setSplashImpact(@Nullable SplashImpactState state);

    boolean isPendingInvalidAffinityChatWarning();

    void setPendingInvalidAffinityChatWarning(boolean pending);

    OIMRConfig.SlotSyncState getLastAppliedSlotSync();

    void setLastAppliedSlotSync(OIMRConfig.SlotSyncState state);

    boolean isLastAppliedSyncEffects();

    void setLastAppliedSyncEffects(boolean enabled);

    int getLastAppliedAffinityPercent();

    void setLastAppliedAffinityPercent(int percent);

    boolean isSyncingEffects();

    void setSyncingEffects(boolean syncing);

    boolean isSyncingHealth();

    void setSyncingHealth(boolean syncing);

    boolean isSyncingAffinityDamage();

    void setSyncingAffinityDamage(boolean syncing);

    boolean isSuppressAffinityVitalsCascade();

    void setSuppressAffinityVitalsCascade(boolean suppress);

    boolean isApplyingUnscaledHungerEffect();

    void setApplyingUnscaledHungerEffect(boolean applying);

    @Nullable Holder<MobEffect> getActivePeriodicDotEffect();

    void setActivePeriodicDotEffect(@Nullable Holder<MobEffect> effect);

    boolean isSyncingExperience();

    void setSyncingExperience(boolean syncing);

    boolean isSyncingAdvancements();

    void setSyncingAdvancements(boolean syncing);

    boolean isSyncingRecipes();

    void setSyncingRecipes(boolean syncing);

    boolean isSyncingEnvironment();

    void setSyncingEnvironment(boolean syncing);

    boolean isSyncingRespawn();

    void setSyncingRespawn(boolean syncing);

    boolean isSyncingKnockback();

    void setSyncingKnockback(boolean syncing);

    boolean isSyncingGroupTeleport();

    void setSyncingGroupTeleport(boolean syncing);

    boolean isHandlingGroupTotem();

    void setHandlingGroupTotem(boolean handling);

    int getTeamMutationDepth();

    void setTeamMutationDepth(int depth);

    boolean isCascadingDeath();

    void setCascadingDeath(boolean cascading);

    boolean isClosingDeathContainer();

    void setClosingDeathContainer(boolean closing);

    List<Mob> activeMobSoundSources();

    TeamLifecycleService teamLifecycle();

    InventoryAndAmmoService inventoryAndAmmo();

    EffectSyncService effectSync();

    VitalsAndDamageService vitalsAndDamage();

    EnvironmentSyncService environmentSync();

    ProgressionAndSocialService progressionAndSocial();

    DeathCascadeService deathCascade();

    TeleportAndAudioService teleportAndAudio();

    record AmmoReservation(
        UUID playerUuid,
        @Nullable String hiveId,
        InteractionHand hand,
        ItemStack weaponSnapshot,
        ItemStack reservedStack,
        int sourceSlot
    ) {
    }

    record MobSoundDedupKey(String hiveId, SoundEvent sound, SoundSource source, long seed) {
    }

    record PendingDamageContribution(DamageSource source, double amount) {
    }

    record CapturedDeathContext(Component deathMessage, @Nullable Entity secondaryEntity, boolean collectiveStarve) {
    }

    record SplashEffectKey(String hiveId, Holder<MobEffect> effect) {
    }

    record InstantaneousEffectKey(String hiveId, MobEffect effect) {
    }

    final class InstantaneousImpactEntry {
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

    final class SplashImpactAccumulator {
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

    final class SplashImpactState {
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
