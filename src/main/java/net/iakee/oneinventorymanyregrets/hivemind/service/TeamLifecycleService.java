package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveEnchantmentMenuAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveEquipmentAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveFoodDataAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveInventoryAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.hivemind.HivePlayerAdvancementsAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HivePlayerProgressAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveRoutedNonNullList;
import net.iakee.oneinventorymanyregrets.hivemind.HiveServerRecipeBookAccess;
import net.iakee.oneinventorymanyregrets.hivemind.OIMRLang;
import net.iakee.oneinventorymanyregrets.hivemind.PersonalInventoryState;
import net.iakee.oneinventorymanyregrets.network.OIMRNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player attachment, active-hive indexing, scoreboard team synchronization,
 * activity metadata, and orphan/retention cleanup.
 */
public final class TeamLifecycleService {
    private static final long POST_LOAD_SAFETY_DELAY_TICKS = 5L;
    private static final long ACTIVITY_TOUCH_INTERVAL_TICKS = 200L;
    public static final int DEFAULT_ORPHAN_RETENTION_DAYS = 0;
    public static final int DEFAULT_INACTIVE_RETENTION_DAYS = 30;

    private final HiveMindContext ctx;

    public TeamLifecycleService(HiveMindContext ctx) {
        this.ctx = ctx;
    }

    public record CleanupReport(
        List<String> orphanedTeamHives,
        List<String> inactiveEmptyTeamHives,
        List<UUID> stalePersonalStates,
        boolean dryRun
    ) {
        public int totalCount() {
            return this.orphanedTeamHives.size()
                + this.inactiveEmptyTeamHives.size()
                + this.stalePersonalStates.size();
        }
    }

    public static final class CleanupPolicy {
        private static final long SECONDS_PER_DAY = 86_400L;

        private CleanupPolicy() {
        }

        public static boolean isOrphanedTeamEligible(
            HiveMind hive,
            boolean teamMissingOnScoreboard,
            boolean hasOnlineMembers,
            long nowEpochSeconds,
            int orphanRetentionDays
        ) {
            return isOrphanedTeamEligible(
                hive.getId(),
                hive.isOrphaned(),
                hive.getOrphanedEpoch(),
                hive.getLastActiveEpoch(),
                teamMissingOnScoreboard,
                hasOnlineMembers,
                nowEpochSeconds,
                orphanRetentionDays
            );
        }

        public static boolean isOrphanedTeamEligible(
            String hiveId,
            boolean orphaned,
            long orphanedEpoch,
            long lastActiveEpoch,
            boolean teamMissingOnScoreboard,
            boolean hasOnlineMembers,
            long nowEpochSeconds,
            int orphanRetentionDays
        ) {
            if (!hiveId.startsWith(HiveMindData.TEAM_HIVE_PREFIX) || hasOnlineMembers) {
                return false;
            }
            if (!orphaned && !teamMissingOnScoreboard) {
                return false;
            }
            if (orphanRetentionDays <= 0) {
                return true;
            }
            long refEpoch = orphanedEpoch > 0L ? orphanedEpoch : lastActiveEpoch;
            return refEpoch > 0L && (nowEpochSeconds - refEpoch) >= (long) orphanRetentionDays * SECONDS_PER_DAY;
        }

        public static boolean isInactiveEmptyTeamEligible(
            HiveMind hive,
            @Nullable PlayerTeam scoreboardTeam,
            boolean hasOnlineMembers,
            long nowEpochSeconds,
            int inactiveRetentionDays
        ) {
            return isInactiveEmptyTeamEligible(
                hive.getId(),
                hive.isOrphaned(),
                hive.getLastActiveEpoch(),
                scoreboardTeam != null,
                scoreboardTeam != null && scoreboardTeam.getPlayers().isEmpty(),
                hasOnlineMembers,
                nowEpochSeconds,
                inactiveRetentionDays
            );
        }

        public static boolean isInactiveEmptyTeamEligible(
            String hiveId,
            boolean orphaned,
            long lastActiveEpoch,
            boolean scoreboardTeamExists,
            boolean scoreboardTeamEmpty,
            boolean hasOnlineMembers,
            long nowEpochSeconds,
            int inactiveRetentionDays
        ) {
            if (!hiveId.startsWith(HiveMindData.TEAM_HIVE_PREFIX)
                || hasOnlineMembers
                || orphaned
                || !scoreboardTeamExists
                || !scoreboardTeamEmpty
                || inactiveRetentionDays <= 0) {
                return false;
            }
            return lastActiveEpoch > 0L && (nowEpochSeconds - lastActiveEpoch) >= (long) inactiveRetentionDays * SECONDS_PER_DAY;
        }

        public static boolean isPersonalStateEligible(
            PersonalInventoryState state,
            boolean isOnline,
            long nowEpochSeconds,
            int inactiveRetentionDays
        ) {
            return isPersonalStateEligible(state.isEmpty(), state.getLastActiveEpoch(), isOnline, nowEpochSeconds, inactiveRetentionDays);
        }

        public static boolean isPersonalStateEligible(
            boolean empty,
            long lastActiveEpoch,
            boolean isOnline,
            long nowEpochSeconds,
            int inactiveRetentionDays
        ) {
            if (isOnline) {
                return false;
            }
            if (empty) {
                return true;
            }
            if (inactiveRetentionDays <= 0) {
                return false;
            }
            return lastActiveEpoch > 0L && (nowEpochSeconds - lastActiveEpoch) >= (long) inactiveRetentionDays * SECONDS_PER_DAY;
        }
    }

    public void onServerStarted() {
        OIMRConfig.load();
        boolean invalidPending = OIMRConfig.consumeInvalidAffinityResetPending();
        this.ctx.setPendingInvalidAffinityChatWarning(invalidPending);
        if (invalidPending) {
            OIMRMod.LOGGER.warn("Invalid OIMR affinity setting detected in config; affinity was reset to 100%.");
        }
        HiveMindData data = this.ctx.data();
        long nowEpoch = Instant.now().getEpochSecond();
        ServerScoreboard scoreboard = this.ctx.server().getScoreboard();
        boolean migrated = false;

        for (HiveMind hive : data.getAll()) {
            if (hive.getLastActiveEpoch() <= 0L) {
                hive.setLastActiveEpoch(nowEpoch);
                migrated = true;
            }
            if (hive.getId().startsWith(HiveMindData.TEAM_HIVE_PREFIX)) {
                String teamName = hive.getId().substring(HiveMindData.TEAM_HIVE_PREFIX.length());
                if (scoreboard.getPlayerTeam(teamName) == null && !hive.isOrphaned()) {
                    hive.markOrphaned(nowEpoch);
                    migrated = true;
                }
            }
        }
        for (PersonalInventoryState personal : data.getPersonalInventoriesView().values()) {
            if (personal.getLastActiveEpoch() <= 0L) {
                personal.setLastActiveEpoch(nowEpoch);
                migrated = true;
            }
        }
        if (migrated) {
            data.setDirty();
        }
    }

    public void onServerStopped() {
        this.ctx.data().setDirty();
    }

    public void onBeforeSave() {
        HiveMindData data = this.ctx.data();
        if (!data.getAll().isEmpty()) {
            data.setDirty();
        }
    }

    public void onPlayerJoin(ServerPlayer player) {
        attachPlayer(player, true, true);
        this.ctx.pendingPostLoadChecks().put(player.getUUID(), this.ctx.server().overworld().getGameTime() + 20L);
    }

    public void onPlayerLoaded(ServerPlayer player) {
        if (!this.ctx.isAttached(player)) {
            return;
        }
        resyncPlayerFromHive(player);
        this.ctx.pendingPostLoadChecks().put(
            player.getUUID(),
            this.ctx.server().overworld().getGameTime() + POST_LOAD_SAFETY_DELAY_TICKS
        );
        broadcastInvalidAffinityWarningIfPending();
    }

    public void notifyIfInvalidAffinityReset() {
        if (OIMRConfig.consumeInvalidAffinityResetPending()) {
            OIMRMod.LOGGER.warn("Invalid OIMR affinity setting detected in config; affinity was reset to 100%.");
            this.ctx.setPendingInvalidAffinityChatWarning(true);
        }
        broadcastInvalidAffinityWarningIfPending();
    }

    private void broadcastInvalidAffinityWarningIfPending() {
        if (!this.ctx.isPendingInvalidAffinityChatWarning() || this.ctx.server().getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        this.ctx.setPendingInvalidAffinityChatWarning(false);
        Component warning = OIMRLang.translatable("oimr.warning.affinity_reset");
        this.ctx.server().getPlayerList().broadcastSystemMessage(
            warning,
            recipient -> OIMRLang.localizeForPlayer(warning, recipient),
            false
        );
    }

    public void onPlayerLeave(ServerPlayer player) {
        this.ctx.inventoryAndAmmo().releaseAmmoReservation(player, null);
        this.ctx.pendingPostLoadChecks().remove(player.getUUID());
        this.ctx.dyingDamageSources().remove(player.getUUID());
        this.ctx.deadScreenPlayers().remove(player.getUUID());

        long nowEpoch = Instant.now().getEpochSecond();
        long gameTime = this.ctx.server().overworld().getGameTime();
        HiveMindData data = this.ctx.data();
        HiveMind hive = this.ctx.getHiveForPlayer(player);
        if (hive != null) {
            if (OIMRConfig.isFullAffinity() && !player.isDeadOrDying()) {
                if (OIMRConfig.isSyncEffectsEnabled()) {
                    hive.snapshotEffectsFrom(player.getActiveEffects());
                }
                hive.setHealth(player.getHealth());
                hive.setAbsorption(player.getAbsorptionAmount());
            }
            hive.touchActivity(nowEpoch, gameTime);
            data.setDirty();
        }

        PersonalInventoryState personal = data.getPersonalStateIfPresent(player.getUUID());
        if (personal != null) {
            if (personal.isEmpty()) {
                data.removePersonalState(player.getUUID());
            } else {
                personal.touchActivity(nowEpoch, gameTime);
                data.setDirty();
            }
        }
        detachPlayer(player);
    }

    public void onPlayerAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        detachPlayer(oldPlayer);
        this.ctx.deadScreenPlayers().remove(newPlayer.getUUID());

        String hiveId = this.ctx.resolvePlayerHiveId(newPlayer);
        HiveMindData data = this.ctx.data();
        HiveMind hive = getOrCreateActiveHive(data, hiveId);
        if (!hive.isInitialized()) {
            hive.markInitializedEmpty();
        }

        if (!alive && OIMRConfig.isFullAffinity()) {
            boolean anyAliveMember = !this.ctx.getAliveMembers(hiveId).isEmpty();
            if (!anyAliveMember) {
                hive.resetVitalsForRespawn(newPlayer.getMaxHealth());
            }
        }

        hive.touchActivity(Instant.now().getEpochSecond(), this.ctx.server().overworld().getGameTime());
        data.setDirty();
        attachPlayer(newPlayer, false, true);
        newPlayer.level().getChunkSource().chunkMap.move(newPlayer);
    }

    private HiveMind getOrCreateActiveHive(HiveMindData data, String hiveId) {
        HiveMind existing = data.getIfPresent(hiveId);
        if (existing != null && existing.isOrphaned()) {
            // A previous team with this name was removed; recreate cleanly instead of inheriting stale state.
            data.removeHive(hiveId);
        }
        return data.getOrCreate(hiveId);
    }

    public void attachPlayer(ServerPlayer player, boolean allowInitialCapture, boolean syncVitals) {
        String hiveId = this.ctx.resolvePlayerHiveId(player);
        HiveMindData data = this.ctx.data();
        HiveMind hive = getOrCreateActiveHive(data, hiveId);
        PersonalInventoryState personal = data.getOrCreatePersonalState(player.getUUID());

        long nowEpoch = Instant.now().getEpochSecond();
        long gameTime = this.ctx.server().overworld().getGameTime();
        if (!hive.isInitialized()) {
            if (allowInitialCapture && HiveMindData.DEFAULT_HIVE_ID.equals(hiveId)) {
                hive.initializeFrom(player, personal);
            } else {
                hive.markInitializedEmpty();
            }
            data.setDirty();
        }
        hive.touchActivity(nowEpoch, gameTime);
        personal.touchActivity(nowEpoch, gameTime);

        this.ctx.indexAttachedPlayer(player, hiveId);

        ((HiveInventoryAccess) player.getInventory())
            .oimr$setItems(HiveRoutedNonNullList.create(hive.getItems(), personal.getHotbarItems()));
        ((HiveInventoryAccess) player.getEnderChestInventory()).oimr$setItems(hive.getEnderChestItems());
        HiveEquipmentAccess equipAccess = (HiveEquipmentAccess) ((HiveLivingEntityAccess) player).oimr$getEquipment();
        equipAccess.oimr$setEquipmentItems(hive.getEquipmentMap());
        equipAccess.oimr$setRoutedMaps(hive.getEquipmentMap(), personal.getEquipmentMap());
        ((HiveFoodDataAccess) player).oimr$setFoodData(hive.getFoodData());

        this.ctx.setSyncingRespawn(true);
        try {
            player.setRespawnPosition(hive.getRespawnConfig(), false);
        } finally {
            this.ctx.setSyncingRespawn(false);
        }

        ((HivePlayerProgressAccess) player).oimr$setEnchantmentSeed(hive.getEnchantmentSeed());

        this.ctx.setSyncingAdvancements(true);
        try {
            ((HivePlayerAdvancementsAccess) player.getAdvancements())
                .oimr$syncFromHive(this.ctx.server().getAdvancements(), hive.getAdvancements());
        } finally {
            this.ctx.setSyncingAdvancements(false);
        }

        this.ctx.setSyncingRecipes(true);
        try {
            ((HiveServerRecipeBookAccess) player.getRecipeBook())
                .oimr$syncFromHive(hive.getKnownRecipes(), hive.getHighlightRecipes(), player);
        } finally {
            this.ctx.setSyncingRecipes(false);
        }

        ((HiveLivingEntityAccess) player).oimr$detectEquipmentUpdates();
        this.ctx.inventoryAndAmmo().flushPendingSyncDrops(hive, data, player);

        if (syncVitals && !player.isDeadOrDying()) {
            syncVitalsToPlayer(player, hiveId, hive, true, true);
        } else {
            this.ctx.environmentSync().syncUnderwaterOnJoin(player);
        }

        player.inventoryMenu.sendAllDataToRemote();
    }

    public void detachPlayer(ServerPlayer player) {
        this.ctx.unindexAttachedPlayer(player);
        this.ctx.replicatedCoarseEffects().remove(player.getUUID());
        this.ctx.effectSync().clearReplicatedEffectsFromPlayer(player);
        OIMRNetworking.syncUnderwaterToPlayer(player, false);
        this.ctx.activeDeathLootTargets().remove(player.getUUID());
        this.ctx.deadScreenPlayers().remove(player.getUUID());
        this.ctx.deferredHeldItemSyncs().remove(player.getUUID());
        this.ctx.lastSentInventoryStacks().remove(player.getUUID());
        this.ctx.activeAmmoReservations().remove(player.getUUID());
        this.ctx.pendingStopUseSyncs().remove(player.getUUID());
        ((HiveInventoryAccess) player.getInventory())
            .oimr$setItems(NonNullList.withSize(HiveMind.MAIN_INVENTORY_SIZE, ItemStack.EMPTY));
        ((HiveInventoryAccess) player.getEnderChestInventory())
            .oimr$setItems(NonNullList.withSize(HiveMind.ENDER_CHEST_SIZE, ItemStack.EMPTY));
        HiveEquipmentAccess equipAccess = (HiveEquipmentAccess) ((HiveLivingEntityAccess) player).oimr$getEquipment();
        equipAccess.oimr$setRoutedMaps(null, null);
        equipAccess.oimr$setEquipmentItems(new EnumMap<>(EquipmentSlot.class));
        ((HiveFoodDataAccess) player).oimr$setFoodData(new FoodData());
    }

    public void resyncPlayerFromHive(ServerPlayer player) {
        resyncPlayerFromHive(player, true);
    }

    public void resyncPlayerFromHive(ServerPlayer player, boolean snapshotFromOtherMember) {
        if (!this.ctx.isAttached(player) || player.isDeadOrDying()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return;
        }
        HiveMind hive = this.ctx.data().getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        ((HiveLivingEntityAccess) player).oimr$detectEquipmentUpdates();
        player.containerMenu.broadcastChanges();
        syncVitalsToPlayer(player, hiveId, hive, false, snapshotFromOtherMember);
        ((HivePlayerProgressAccess) player).oimr$setEnchantmentSeed(hive.getEnchantmentSeed());
        if (player.containerMenu instanceof HiveEnchantmentMenuAccess enchantMenu
            && enchantMenu.oimr$getSeed() != hive.getEnchantmentSeed()) {
            enchantMenu.oimr$refreshSeed(hive.getEnchantmentSeed());
        }
        player.inventoryMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.sendAllDataToRemote();
        }
    }

    public void syncVitalsToPlayer(
        ServerPlayer player,
        String hiveId,
        HiveMind hive,
        boolean forceApplyEffects,
        boolean snapshotFromOtherMember
    ) {
        if (OIMRConfig.isFullAffinity()) {
            ServerPlayer otherAliveRep = null;
            if (snapshotFromOtherMember) {
                for (ServerPlayer member : this.ctx.getAliveMembers(hiveId)) {
                    if (member != player) {
                        otherAliveRep = member;
                        break;
                    }
                }
            }

            if (otherAliveRep != null) {
                if (OIMRConfig.isSyncEffectsEnabled()) {
                    hive.snapshotEffectsFrom(otherAliveRep.getActiveEffects());
                }
                hive.setHealth(otherAliveRep.getHealth());
                hive.setAbsorption(otherAliveRep.getAbsorptionAmount());
            } else if (hive.getHealth() <= 0.0F) {
                hive.resetVitalsForRespawn(player.getMaxHealth());
                this.ctx.data().setDirty();
            }

            if (OIMRConfig.isSyncEffectsEnabled() && (forceApplyEffects || this.ctx.effectSync().effectsDiffer(player, hive))) {
                this.ctx.effectSync().applyHiveEffectsToPlayer(player, hive);
            }

            this.ctx.setSyncingHealth(true);
            try {
                float targetHealth = hive.getHealth() > 0.0F ? hive.getHealth() : player.getMaxHealth();
                player.setHealth(Mth.clamp(targetHealth, 0.01F, player.getMaxHealth()));
                player.setAbsorptionAmount(Mth.clamp(hive.getAbsorption(), 0.0F, player.getMaxAbsorption()));
            } finally {
                this.ctx.setSyncingHealth(false);
            }

            this.ctx.setSyncingEnvironment(true);
            try {
                player.setAirSupply(Mth.clamp(hive.getAirSupply(), -20, player.getMaxAirSupply()));
                player.setRemainingFireTicks(hive.getRemainingFireTicks());
                player.setSharedFlagOnFire(hive.getRemainingFireTicks() > 0);
                player.setTicksFrozen(hive.getTicksFrozen());
            } finally {
                this.ctx.setSyncingEnvironment(false);
            }
        }

        this.ctx.setSyncingExperience(true);
        try {
            player.experienceLevel = hive.getExperienceLevel();
            player.totalExperience = hive.getTotalExperience();
            player.experienceProgress = hive.getExperienceProgress();
        } finally {
            this.ctx.setSyncingExperience(false);
        }

        player.connection.send(new ClientboundSetHealthPacket(
            player.getHealth(),
            hive.getFoodData().getFoodLevel(),
            hive.getFoodData().getSaturationLevel()
        ));
        player.connection.send(new ClientboundSetExperiencePacket(
            player.experienceProgress,
            player.totalExperience,
            player.experienceLevel
        ));
        HiveMind currentHive = this.ctx.getHiveForPlayer(player);
        OIMRNetworking.syncUnderwaterToPlayer(player, currentHive != null && currentHive.isAnyMemberUnderwater());
    }

    public void beginTeamMutation() {
        this.ctx.setTeamMutationDepth(this.ctx.getTeamMutationDepth() + 1);
    }

    public void endTeamMutation() {
        int depth = this.ctx.getTeamMutationDepth();
        if (depth > 0) {
            this.ctx.setTeamMutationDepth(depth - 1);
        }
        if (this.ctx.getTeamMutationDepth() == 0) {
            syncAllPlayerTeams();
        }
    }

    public void syncAllPlayerTeams() {
        if (this.ctx.getTeamMutationDepth() > 0) {
            return;
        }

        for (ServerPlayer player : this.ctx.server().getPlayerList().getPlayers()) {
            String currentHiveId = this.ctx.getAttachedHiveId(player.getUUID());
            if (currentHiveId == null) {
                continue;
            }
            String expectedHiveId = this.ctx.resolvePlayerHiveId(player);
            if (!expectedHiveId.equals(currentHiveId)) {
                this.ctx.inventoryAndAmmo().releaseAmmoReservation(player, null);
                if (player.isUsingItem()) {
                    player.stopUsingItem();
                }
                HiveMind oldHive = this.ctx.data().getIfPresent(currentHiveId);
                if (oldHive != null) {
                    oldHive.clearPlayerRemainders(player.getUUID());
                    this.ctx.data().setDirty();
                }
                this.ctx.pendingDamageSources().remove(player.getUUID());
                if (!player.isDeadOrDying()) {
                    player.doCloseContainer();
                }
                detachPlayer(player);
                attachPlayer(player, false, true);
            }
        }
    }

    public void onTeamRemoved(PlayerTeam team) {
        syncAllPlayerTeams();
        String hiveId = HiveMindData.TEAM_HIVE_PREFIX + team.getName();
        HiveMind hive = this.ctx.data().getIfPresent(hiveId);
        if (hive != null) {
            hive.markOrphaned(Instant.now().getEpochSecond());
            this.ctx.data().setDirty();
        }
    }

    public void onTeamAdded(PlayerTeam team) {
        String hiveId = HiveMindData.TEAM_HIVE_PREFIX + team.getName();
        HiveMind existing = this.ctx.data().getIfPresent(hiveId);
        if (existing != null && existing.isOrphaned()) {
            this.ctx.data().removeHive(hiveId);
        }
    }

    public void onConfigChanged() {
        OIMRConfig.SlotSyncState currentSlotSync = OIMRConfig.getSlotSyncState();
        if (!this.ctx.getLastAppliedSlotSync().equals(currentSlotSync)) {
            this.ctx.inventoryAndAmmo().prepareSlotSyncTransition(
                null,
                this.ctx.getLastAppliedSlotSync(),
                currentSlotSync
            );
        }
        HiveMindData data = this.ctx.data();
        for (HiveMind hive : data.getAll()) {
            hive.clearHazardWindows();
        }
        boolean currentSyncEffects = OIMRConfig.isSyncEffectsEnabled();
        int currentAffinityPercent = OIMRConfig.getAffinityPercent();
        boolean affinityBecameFull = this.ctx.getLastAppliedAffinityPercent() < OIMRConfig.MAX_AFFINITY
            && OIMRConfig.isFullAffinity();
        if (this.ctx.isLastAppliedSyncEffects() && !currentSyncEffects) {
            for (HiveMind hive : data.getAll()) {
                hive.getEffects().clear();
                hive.clearAllEffectRemainders();
                hive.clearPrimaryEffectTickers();
            }
        }
        if (OIMRConfig.isFullAffinity() || !currentSyncEffects || currentAffinityPercent <= 0) {
            this.ctx.effectSync().clearReplicatedEffectsFromAllAttached();
        }
        this.ctx.setLastAppliedSyncEffects(currentSyncEffects);
        this.ctx.setLastAppliedAffinityPercent(currentAffinityPercent);
        if (OIMRConfig.isFullAffinity()) {
            this.ctx.pendingDamageSources().clear();
            for (String hiveId : this.ctx.getActiveHiveIds()) {
                HiveMind hive = data.getIfPresent(hiveId);
                if (hive == null) {
                    continue;
                }
                hive.clearAllPlayerRemainders();
                List<ServerPlayer> alive = this.ctx.getAliveMembers(hiveId);
                if (!alive.isEmpty()) {
                    ServerPlayer rep = alive.getFirst();
                    if (OIMRConfig.isSyncEffectsEnabled()) {
                        if (affinityBecameFull) {
                            this.ctx.effectSync().snapshotMergedNonReplicatedEffects(hive, alive);
                        } else {
                            hive.snapshotEffectsFrom(rep.getActiveEffects());
                        }
                    }
                    hive.setHealth(rep.getHealth());
                    hive.setAbsorption(rep.getAbsorptionAmount());
                }
            }
        }
        for (String hiveId : this.ctx.getActiveHiveIds()) {
            HiveMind hive = data.getIfPresent(hiveId);
            if (hive != null) {
                List<ServerPlayer> alive = this.ctx.getAliveMembers(hiveId);
                hive.setAnyMemberUnderwater(this.ctx.environmentSync().computeHiveUnderwaterState(alive));
            }
        }
        for (ServerPlayer player : this.ctx.server().getPlayerList().getPlayers()) {
            if (this.ctx.isAttached(player)) {
                resyncPlayerFromHive(player, false);
            }
        }
        data.setDirty();
        OIMRNetworking.syncToAll(this.ctx.server());
        for (String hiveId : this.ctx.getActiveHiveIds()) {
            HiveMind hive = data.getIfPresent(hiveId);
            if (hive != null) {
                boolean underwater = hive.isAnyMemberUnderwater();
                for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
                    OIMRNetworking.syncUnderwaterToPlayer(member, underwater);
                }
            }
        }
        this.ctx.effectSync().syncTranslucencyIfChanged();
    }

    public void onEndServerTick() {
        syncAllPlayerTeams();

        MinecraftServer server = this.ctx.server();
        long gameTime = server.overworld().getGameTime();
        if (!this.ctx.pendingPostLoadChecks().isEmpty()) {
            List<UUID> duePlayers = null;
            Iterator<Map.Entry<UUID, Long>> it = this.ctx.pendingPostLoadChecks().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                if (gameTime >= entry.getValue()) {
                    if (duePlayers == null) {
                        duePlayers = new ArrayList<>();
                    }
                    duePlayers.add(entry.getKey());
                    it.remove();
                }
            }
            if (duePlayers != null) {
                for (UUID playerId : duePlayers) {
                    ServerPlayer loaded = server.getPlayerList().getPlayer(playerId);
                    if (loaded != null) {
                        resyncPlayerFromHive(loaded);
                    }
                }
            }
        }

        HiveMindData data = this.ctx.data();
        boolean touchPeriodicActivity = (gameTime % ACTIVITY_TOUCH_INTERVAL_TICKS) == 0L;
        long nowEpoch = touchPeriodicActivity ? Instant.now().getEpochSecond() : 0L;

        // Iterate ONLY hives with online members (O(online players) instead of O(all saved hives)).
        for (String hiveId : this.ctx.getActiveHiveIds()) {
            HiveMind hive = data.getIfPresent(hiveId);
            if (hive == null) {
                continue;
            }
            List<ServerPlayer> members = this.ctx.getOnlineMembers(hiveId);
            if (members.isEmpty()) {
                continue;
            }

            if (touchPeriodicActivity) {
                hive.touchActivity(nowEpoch, gameTime);
            }

            List<ServerPlayer> aliveMembers = new ArrayList<>(members.size());
            for (ServerPlayer member : members) {
                member.containerMenu.broadcastChanges();
                ((HiveLivingEntityAccess) member).oimr$detectEquipmentUpdates();
                member.getAdvancements().flushDirty(member, true);
                if (!member.isDeadOrDying()) {
                    aliveMembers.add(member);
                }
            }

            if (!aliveMembers.isEmpty() && OIMRConfig.isFullAffinity()) {
                this.ctx.environmentSync().tickHiveEnvironment(hive, aliveMembers, gameTime, data);
                ServerPlayer rep = aliveMembers.getFirst();
                if (OIMRConfig.isSyncEffectsEnabled()) {
                    hive.snapshotEffectsFrom(rep.getActiveEffects());
                }
                hive.setHealth(rep.getHealth());
                hive.setAbsorption(rep.getAbsorptionAmount());
            }

            boolean underwaterNow = this.ctx.environmentSync().computeHiveUnderwaterState(aliveMembers);
            if (underwaterNow != hive.isAnyMemberUnderwater()) {
                hive.setAnyMemberUnderwater(underwaterNow);
                for (ServerPlayer member : members) {
                    OIMRNetworking.syncUnderwaterToPlayer(member, underwaterNow);
                }
            }
        }
        this.ctx.inventoryAndAmmo().flushDeferredHeldItemSyncs();
        this.ctx.effectSync().syncTranslucencyIfChanged();
    }

    public CleanupReport inspectCleanup(int orphanRetentionDays, int inactiveRetentionDays) {
        return runCleanup(orphanRetentionDays, inactiveRetentionDays, true);
    }

    public CleanupReport executeCleanup(int orphanRetentionDays, int inactiveRetentionDays) {
        return runCleanup(orphanRetentionDays, inactiveRetentionDays, false);
    }

    private CleanupReport runCleanup(int orphanRetentionDays, int inactiveRetentionDays, boolean dryRun) {
        HiveMindData data = this.ctx.data();
        MinecraftServer server = this.ctx.server();
        ServerScoreboard scoreboard = server.getScoreboard();
        long nowEpoch = Instant.now().getEpochSecond();

        List<String> orphanedTargets = new ArrayList<>();
        List<String> inactiveEmptyTargets = new ArrayList<>();
        List<UUID> stalePersonalTargets = new ArrayList<>();

        for (HiveMind hive : data.getAll()) {
            String hiveId = hive.getId();
            if (!hiveId.startsWith(HiveMindData.TEAM_HIVE_PREFIX)) {
                continue;
            }
            boolean hasOnlineMembers = !this.ctx.getOnlineMembers(hiveId).isEmpty();
            String teamName = hiveId.substring(HiveMindData.TEAM_HIVE_PREFIX.length());
            PlayerTeam scoreboardTeam = scoreboard.getPlayerTeam(teamName);
            boolean missingTeam = scoreboardTeam == null;

            if (CleanupPolicy.isOrphanedTeamEligible(hive, missingTeam, hasOnlineMembers, nowEpoch, orphanRetentionDays)) {
                orphanedTargets.add(hiveId);
            } else if (CleanupPolicy.isInactiveEmptyTeamEligible(hive, scoreboardTeam, hasOnlineMembers, nowEpoch, inactiveRetentionDays)) {
                inactiveEmptyTargets.add(hiveId);
            }
        }

        for (Map.Entry<UUID, PersonalInventoryState> entry : data.getPersonalInventoriesView().entrySet()) {
            UUID playerId = entry.getKey();
            boolean isOnline = server.getPlayerList().getPlayer(playerId) != null;
            if (CleanupPolicy.isPersonalStateEligible(entry.getValue(), isOnline, nowEpoch, inactiveRetentionDays)) {
                stalePersonalTargets.add(playerId);
            }
        }

        if (!dryRun) {
            for (String hiveId : orphanedTargets) {
                data.removeHive(hiveId);
            }
            for (String hiveId : inactiveEmptyTargets) {
                data.removeHive(hiveId);
            }
            for (UUID playerId : stalePersonalTargets) {
                data.removePersonalState(playerId);
            }
        }

        return new CleanupReport(
            List.copyOf(orphanedTargets),
            List.copyOf(inactiveEmptyTargets),
            List.copyOf(stalePersonalTargets),
            dryRun
        );
    }
}
