package net.iakee.oneinventorymanyregrets.hivemind;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.service.DeathCascadeService;
import net.iakee.oneinventorymanyregrets.hivemind.service.EffectSyncService;
import net.iakee.oneinventorymanyregrets.hivemind.service.EnvironmentSyncService;
import net.iakee.oneinventorymanyregrets.hivemind.service.HiveMindContext;
import net.iakee.oneinventorymanyregrets.hivemind.service.InventoryAndAmmoService;
import net.iakee.oneinventorymanyregrets.hivemind.service.ProgressionAndSocialService;
import net.iakee.oneinventorymanyregrets.hivemind.service.TeamLifecycleService;
import net.iakee.oneinventorymanyregrets.hivemind.service.TeleportAndAudioService;
import net.iakee.oneinventorymanyregrets.hivemind.service.VitalsAndDamageService;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-server Hive Mind runtime coordinator holding all transient state and domain services.
 */
public final class HiveMindCoordinator implements HiveMindContext {
    private static final Map<MinecraftServer, HiveMindCoordinator> COORDINATORS = Collections.synchronizedMap(new IdentityHashMap<>());

    private final MinecraftServer server;
    private final Map<UUID, String> playerHives = new HashMap<>();
    private final Map<UUID, String> knownPlayerHives = new HashMap<>();
    private final Set<ServerPlayer> attachedPlayers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, List<ServerPlayer>> onlineMembersByHive = new LinkedHashMap<>();
    private final Map<UUID, Long> pendingPostLoadChecks = new HashMap<>();
    private final Map<UUID, Component> pendingDeathMessages = new HashMap<>();
    private final Map<UUID, Component> lastHiveDeathMessages = new HashMap<>();
    private final Set<UUID> suppressDeathChat = new HashSet<>();
    private final Map<UUID, CapturedDeathContext> capturedDeathContexts = new HashMap<>();
    private final Map<UUID, DamageSource> dyingDamageSources = new HashMap<>();
    private final Map<UUID, ServerPlayer> activeDeathLootTargets = new HashMap<>();
    private final Set<UUID> deadScreenPlayers = new HashSet<>();
    private final Map<UUID, Set<Holder<MobEffect>>> replicatedCoarseEffects = new HashMap<>();
    private volatile Set<UUID> lastSyncedTranslucentIds = Set.of();
    private final Map<UUID, List<PendingDamageContribution>> pendingDamageSources = new HashMap<>();
    private final Set<UUID> deferredHeldItemSyncs = new HashSet<>();
    private final Map<UUID, Map<Integer, ItemStack>> lastSentInventoryStacks = new HashMap<>();
    private final Map<UUID, AmmoReservation> activeAmmoReservations = new HashMap<>();
    private final Set<UUID> pendingStopUseSyncs = new HashSet<>();
    private final Set<MobSoundDedupKey> recentMobSounds = new HashSet<>();
    private long recentMobSoundGameTime = Long.MIN_VALUE;
    private @Nullable SplashImpactState splashImpact = null;
    private boolean pendingInvalidAffinityChatWarning = false;
    private OIMRConfig.SlotSyncState lastAppliedSlotSync = OIMRConfig.SlotSyncState.ALL_SYNCED;
    private boolean lastAppliedSyncEffects = true;
    private int lastAppliedAffinityPercent = OIMRConfig.MAX_AFFINITY;

    private boolean syncingEffects = false;
    private boolean syncingHealth = false;
    private boolean syncingAffinityDamage = false;
    private boolean suppressAffinityVitalsCascade = false;
    private boolean applyingUnscaledHungerEffect = false;
    private @Nullable Holder<MobEffect> activePeriodicDotEffect = null;
    private boolean syncingExperience = false;
    private boolean syncingAdvancements = false;
    private boolean syncingRecipes = false;
    private boolean syncingEnvironment = false;
    private boolean syncingRespawn = false;
    private boolean syncingKnockback = false;
    private boolean syncingGroupTeleport = false;
    private boolean handlingGroupTotem = false;
    private int teamMutationDepth = 0;
    private boolean cascadingDeath = false;
    private boolean closingDeathContainer = false;
    private final ThreadLocal<List<Mob>> activeMobSoundSources = ThreadLocal.withInitial(ArrayList::new);

    private final TeamLifecycleService teamLifecycleService;
    private final InventoryAndAmmoService inventoryAndAmmoService;
    private final EffectSyncService effectSyncService;
    private final VitalsAndDamageService vitalsAndDamageService;
    private final EnvironmentSyncService environmentSyncService;
    private final ProgressionAndSocialService progressionAndSocialService;
    private final DeathCascadeService deathCascadeService;
    private final TeleportAndAudioService teleportAndAudioService;

    private HiveMindCoordinator(MinecraftServer server) {
        this.server = server;
        this.teamLifecycleService = new TeamLifecycleService(this);
        this.inventoryAndAmmoService = new InventoryAndAmmoService(this);
        this.effectSyncService = new EffectSyncService(this);
        this.vitalsAndDamageService = new VitalsAndDamageService(this);
        this.environmentSyncService = new EnvironmentSyncService(this);
        this.progressionAndSocialService = new ProgressionAndSocialService(this);
        this.deathCascadeService = new DeathCascadeService(this);
        this.teleportAndAudioService = new TeleportAndAudioService(this);
    }

    public static HiveMindCoordinator getOrCreate(MinecraftServer server) {
        return COORDINATORS.computeIfAbsent(server, HiveMindCoordinator::new);
    }

    public static @Nullable HiveMindCoordinator getIfPresent(@Nullable MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return COORDINATORS.get(server);
    }

    public static void destroy(MinecraftServer server) {
        HiveMindCoordinator removed = COORDINATORS.remove(server);
        if (removed != null) {
            removed.resetTransientState();
        }
    }

    public void resetTransientState() {
        this.playerHives.clear();
        this.knownPlayerHives.clear();
        this.attachedPlayers.clear();
        this.onlineMembersByHive.clear();
        this.pendingPostLoadChecks.clear();
        this.pendingDeathMessages.clear();
        this.lastHiveDeathMessages.clear();
        this.suppressDeathChat.clear();
        this.capturedDeathContexts.clear();
        this.dyingDamageSources.clear();
        this.activeDeathLootTargets.clear();
        this.deadScreenPlayers.clear();
        this.replicatedCoarseEffects.clear();
        this.lastSyncedTranslucentIds = Set.of();
        this.pendingDamageSources.clear();
        this.deferredHeldItemSyncs.clear();
        this.lastSentInventoryStacks.clear();
        this.activeAmmoReservations.clear();
        this.pendingStopUseSyncs.clear();
        this.recentMobSounds.clear();
        this.recentMobSoundGameTime = Long.MIN_VALUE;
        this.splashImpact = null;
        this.pendingInvalidAffinityChatWarning = false;
        this.lastAppliedSlotSync = OIMRConfig.getSlotSyncState();
        this.lastAppliedSyncEffects = OIMRConfig.isSyncEffectsEnabled();
        this.lastAppliedAffinityPercent = OIMRConfig.getAffinityPercent();
        this.syncingEffects = false;
        this.syncingHealth = false;
        this.syncingAffinityDamage = false;
        this.suppressAffinityVitalsCascade = false;
        this.applyingUnscaledHungerEffect = false;
        this.activePeriodicDotEffect = null;
        this.syncingExperience = false;
        this.syncingAdvancements = false;
        this.syncingRecipes = false;
        this.syncingEnvironment = false;
        this.syncingRespawn = false;
        this.syncingKnockback = false;
        this.syncingGroupTeleport = false;
        this.handlingGroupTotem = false;
        this.teamMutationDepth = 0;
        this.cascadingDeath = false;
        this.closingDeathContainer = false;
        this.activeMobSoundSources.remove();
    }

    @Override
    public MinecraftServer server() {
        return this.server;
    }

    @Override
    public HiveMindData data() {
        return HiveMindData.get(this.server);
    }

    @Override
    public @Nullable String getAttachedHiveId(UUID playerId) {
        return this.playerHives.get(playerId);
    }

    @Override
    public @Nullable String getKnownHiveId(UUID playerId) {
        return this.knownPlayerHives.get(playerId);
    }

    @Override
    public boolean isAttached(ServerPlayer player) {
        return this.attachedPlayers.contains(player) && this.playerHives.containsKey(player.getUUID());
    }

    @Override
    public @Nullable HiveMind getHiveForPlayer(ServerPlayer player) {
        if (!this.attachedPlayers.contains(player)) {
            return null;
        }
        String hiveId = this.playerHives.get(player.getUUID());
        if (hiveId == null) {
            return null;
        }
        return this.data().getIfPresent(hiveId);
    }

    @Override
    public List<ServerPlayer> getOnlineMembers(String hiveId) {
        List<ServerPlayer> indexed = this.onlineMembersByHive.get(hiveId);
        if (indexed == null || indexed.isEmpty()) {
            return List.of();
        }
        return List.copyOf(indexed);
    }

    @Override
    public List<ServerPlayer> getAliveMembers(String hiveId) {
        List<ServerPlayer> indexed = this.onlineMembersByHive.get(hiveId);
        if (indexed == null || indexed.isEmpty()) {
            return List.of();
        }
        List<ServerPlayer> alive = new ArrayList<>(indexed.size());
        for (ServerPlayer player : indexed) {
            if (!player.isDeadOrDying()) {
                alive.add(player);
            }
        }
        return alive;
    }

    @Override
    public Collection<String> getActiveHiveIds() {
        if (this.onlineMembersByHive.isEmpty()) {
            return List.of();
        }
        return List.copyOf(this.onlineMembersByHive.keySet());
    }

    @Override
    public boolean isVitalHazardTarget(ServerPlayer player) {
        return !player.isDeadOrDying()
            && !player.isCreative()
            && !player.isSpectator()
            && !player.getAbilities().invulnerable;
    }

    @Override
    public int getHungerScaleDenominator(String hiveId) {
        List<ServerPlayer> indexed = this.onlineMembersByHive.get(hiveId);
        if (indexed == null || indexed.isEmpty()) {
            return 1;
        }
        int count = 0;
        for (ServerPlayer player : indexed) {
            if (isVitalHazardTarget(player)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    @Override
    public String resolvePlayerHiveId(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        return team != null ? HiveMindData.TEAM_HIVE_PREFIX + team.getName() : HiveMindData.DEFAULT_HIVE_ID;
    }

    @Override
    public void indexAttachedPlayer(ServerPlayer player, String hiveId) {
        this.playerHives.put(player.getUUID(), hiveId);
        this.knownPlayerHives.put(player.getUUID(), hiveId);
        this.attachedPlayers.add(player);
        List<ServerPlayer> members = this.onlineMembersByHive.computeIfAbsent(hiveId, ignored -> new ArrayList<>());
        if (!members.contains(player)) {
            members.add(player);
        }
    }

    @Override
    public void unindexAttachedPlayer(ServerPlayer player) {
        this.attachedPlayers.remove(player);
        String oldHiveId = this.playerHives.remove(player.getUUID());
        if (oldHiveId != null) {
            List<ServerPlayer> members = this.onlineMembersByHive.get(oldHiveId);
            if (members != null) {
                members.remove(player);
                if (members.isEmpty()) {
                    this.onlineMembersByHive.remove(oldHiveId);
                }
            }
        }
    }

    @Override public Map<UUID, String> playerHives() { return this.playerHives; }
    @Override public Map<UUID, String> knownPlayerHives() { return this.knownPlayerHives; }
    @Override public Set<ServerPlayer> attachedPlayers() { return this.attachedPlayers; }
    @Override public Map<UUID, Long> pendingPostLoadChecks() { return this.pendingPostLoadChecks; }
    @Override public Map<UUID, Component> pendingDeathMessages() { return this.pendingDeathMessages; }
    @Override public Map<UUID, Component> lastHiveDeathMessages() { return this.lastHiveDeathMessages; }
    @Override public Set<UUID> suppressDeathChat() { return this.suppressDeathChat; }
    @Override public Map<UUID, CapturedDeathContext> capturedDeathContexts() { return this.capturedDeathContexts; }
    @Override public Map<UUID, DamageSource> dyingDamageSources() { return this.dyingDamageSources; }
    @Override public Map<UUID, ServerPlayer> activeDeathLootTargets() { return this.activeDeathLootTargets; }
    @Override public Set<UUID> deadScreenPlayers() { return this.deadScreenPlayers; }
    @Override public Map<UUID, Set<Holder<MobEffect>>> replicatedCoarseEffects() { return this.replicatedCoarseEffects; }
    @Override public Set<UUID> lastSyncedTranslucentIds() { return this.lastSyncedTranslucentIds; }
    @Override public void setLastSyncedTranslucentIds(Set<UUID> ids) { this.lastSyncedTranslucentIds = ids; }
    @Override public Map<UUID, List<PendingDamageContribution>> pendingDamageSources() { return this.pendingDamageSources; }
    @Override public Set<UUID> deferredHeldItemSyncs() { return this.deferredHeldItemSyncs; }
    @Override public Map<UUID, Map<Integer, ItemStack>> lastSentInventoryStacks() { return this.lastSentInventoryStacks; }
    @Override public Map<UUID, AmmoReservation> activeAmmoReservations() { return this.activeAmmoReservations; }
    @Override public Set<UUID> pendingStopUseSyncs() { return this.pendingStopUseSyncs; }
    @Override public Set<MobSoundDedupKey> recentMobSounds() { return this.recentMobSounds; }
    @Override public long getRecentMobSoundGameTime() { return this.recentMobSoundGameTime; }
    @Override public void setRecentMobSoundGameTime(long gameTime) { this.recentMobSoundGameTime = gameTime; }
    @Override public @Nullable SplashImpactState getSplashImpact() { return this.splashImpact; }
    @Override public void setSplashImpact(@Nullable SplashImpactState state) { this.splashImpact = state; }
    @Override public boolean isPendingInvalidAffinityChatWarning() { return this.pendingInvalidAffinityChatWarning; }
    @Override public void setPendingInvalidAffinityChatWarning(boolean pending) { this.pendingInvalidAffinityChatWarning = pending; }
    @Override public OIMRConfig.SlotSyncState getLastAppliedSlotSync() { return this.lastAppliedSlotSync; }
    @Override public void setLastAppliedSlotSync(OIMRConfig.SlotSyncState state) { this.lastAppliedSlotSync = state; }
    @Override public boolean isLastAppliedSyncEffects() { return this.lastAppliedSyncEffects; }
    @Override public void setLastAppliedSyncEffects(boolean enabled) { this.lastAppliedSyncEffects = enabled; }
    @Override public int getLastAppliedAffinityPercent() { return this.lastAppliedAffinityPercent; }
    @Override public void setLastAppliedAffinityPercent(int percent) { this.lastAppliedAffinityPercent = percent; }
    @Override public boolean isSyncingEffects() { return this.syncingEffects; }
    @Override public void setSyncingEffects(boolean syncing) { this.syncingEffects = syncing; }
    @Override public boolean isSyncingHealth() { return this.syncingHealth; }
    @Override public void setSyncingHealth(boolean syncing) { this.syncingHealth = syncing; }
    @Override public boolean isSyncingAffinityDamage() { return this.syncingAffinityDamage; }
    @Override public void setSyncingAffinityDamage(boolean syncing) { this.syncingAffinityDamage = syncing; }
    @Override public boolean isSuppressAffinityVitalsCascade() { return this.suppressAffinityVitalsCascade; }
    @Override public void setSuppressAffinityVitalsCascade(boolean suppress) { this.suppressAffinityVitalsCascade = suppress; }
    @Override public boolean isApplyingUnscaledHungerEffect() { return this.applyingUnscaledHungerEffect; }
    @Override public void setApplyingUnscaledHungerEffect(boolean applying) { this.applyingUnscaledHungerEffect = applying; }
    @Override public @Nullable Holder<MobEffect> getActivePeriodicDotEffect() { return this.activePeriodicDotEffect; }
    @Override public void setActivePeriodicDotEffect(@Nullable Holder<MobEffect> effect) { this.activePeriodicDotEffect = effect; }
    @Override public boolean isSyncingExperience() { return this.syncingExperience; }
    @Override public void setSyncingExperience(boolean syncing) { this.syncingExperience = syncing; }
    @Override public boolean isSyncingAdvancements() { return this.syncingAdvancements; }
    @Override public void setSyncingAdvancements(boolean syncing) { this.syncingAdvancements = syncing; }
    @Override public boolean isSyncingRecipes() { return this.syncingRecipes; }
    @Override public void setSyncingRecipes(boolean syncing) { this.syncingRecipes = syncing; }
    @Override public boolean isSyncingEnvironment() { return this.syncingEnvironment; }
    @Override public void setSyncingEnvironment(boolean syncing) { this.syncingEnvironment = syncing; }
    @Override public boolean isSyncingRespawn() { return this.syncingRespawn; }
    @Override public void setSyncingRespawn(boolean syncing) { this.syncingRespawn = syncing; }
    @Override public boolean isSyncingKnockback() { return this.syncingKnockback; }
    @Override public void setSyncingKnockback(boolean syncing) { this.syncingKnockback = syncing; }
    @Override public boolean isSyncingGroupTeleport() { return this.syncingGroupTeleport; }
    @Override public void setSyncingGroupTeleport(boolean syncing) { this.syncingGroupTeleport = syncing; }
    @Override public boolean isHandlingGroupTotem() { return this.handlingGroupTotem; }
    @Override public void setHandlingGroupTotem(boolean handling) { this.handlingGroupTotem = handling; }
    @Override public int getTeamMutationDepth() { return this.teamMutationDepth; }
    @Override public void setTeamMutationDepth(int depth) { this.teamMutationDepth = depth; }
    @Override public boolean isCascadingDeath() { return this.cascadingDeath; }
    @Override public void setCascadingDeath(boolean cascading) { this.cascadingDeath = cascading; }
    @Override public boolean isClosingDeathContainer() { return this.closingDeathContainer; }
    @Override public void setClosingDeathContainer(boolean closing) { this.closingDeathContainer = closing; }
    @Override public List<Mob> activeMobSoundSources() { return this.activeMobSoundSources.get(); }

    @Override public TeamLifecycleService teamLifecycle() { return this.teamLifecycleService; }
    @Override public InventoryAndAmmoService inventoryAndAmmo() { return this.inventoryAndAmmoService; }
    @Override public EffectSyncService effectSync() { return this.effectSyncService; }
    @Override public VitalsAndDamageService vitalsAndDamage() { return this.vitalsAndDamageService; }
    @Override public EnvironmentSyncService environmentSync() { return this.environmentSyncService; }
    @Override public ProgressionAndSocialService progressionAndSocial() { return this.progressionAndSocialService; }
    @Override public DeathCascadeService deathCascade() { return this.deathCascadeService; }
    @Override public TeleportAndAudioService teleportAndAudio() { return this.teleportAndAudioService; }
}
