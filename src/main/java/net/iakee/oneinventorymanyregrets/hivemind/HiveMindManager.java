package net.iakee.oneinventorymanyregrets.hivemind;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.service.ProgressionAndSocialService;
import net.iakee.oneinventorymanyregrets.hivemind.service.TeamLifecycleService;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Public facade for Hive Mind synchronization hooks.
 * Delegates all runtime state and domain logic to per-server {@link HiveMindCoordinator} services.
 */
public final class HiveMindManager {
    private HiveMindManager() {
    }

    private static @Nullable HiveMindCoordinator coordinator(@Nullable MinecraftServer server) {
        return HiveMindCoordinator.getIfPresent(server);
    }

    private static @Nullable HiveMindCoordinator coordinatorForEntity(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        MinecraftServer server = entity.level().getServer();
        return server != null ? HiveMindCoordinator.getOrCreate(server) : null;
    }

    public static void onServerStarted(MinecraftServer server) {
        HiveMindCoordinator coordinator = HiveMindCoordinator.getOrCreate(server);
        coordinator.resetTransientState();
        coordinator.teamLifecycle().onServerStarted();
    }

    public static void onServerStopped(MinecraftServer server) {
        HiveMindCoordinator coordinator = coordinator(server);
        if (coordinator != null) {
            coordinator.teamLifecycle().onServerStopped();
        }
        HiveMindCoordinator.destroy(server);
    }

    public static void onBeforeSave(MinecraftServer server) {
        HiveMindCoordinator coordinator = HiveMindCoordinator.getOrCreate(server);
        coordinator.teamLifecycle().onBeforeSave();
    }

    public static boolean isSyncingAdvancements(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.isSyncingAdvancements();
    }

    public static boolean isAttachedServerPlayer(Object entity) {
        return entity instanceof ServerPlayer player && isAttached(player);
    }

    public static boolean isFullAffinityEnvironmentShared(Object entity) {
        return isAttachedServerPlayer(entity) && OIMRConfig.isFullAffinity();
    }

    public static String getHiveId(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        return team != null ? HiveMindData.TEAM_HIVE_PREFIX + team.getName() : HiveMindData.DEFAULT_HIVE_ID;
    }

    public static boolean isAttached(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.isAttached(player);
    }

    public static @Nullable HiveMind getHiveForPlayer(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null ? coordinator.getHiveForPlayer(player) : null;
    }

    public static List<ServerPlayer> getOnlineMembers(MinecraftServer server, String hiveId) {
        return HiveMindCoordinator.getOrCreate(server).getOnlineMembers(hiveId);
    }

    public static List<ServerPlayer> getAliveMembers(MinecraftServer server, String hiveId) {
        return HiveMindCoordinator.getOrCreate(server).getAliveMembers(hiveId);
    }

    public static int getHungerScaleDenominator(MinecraftServer server, String hiveId) {
        return HiveMindCoordinator.getOrCreate(server).getHungerScaleDenominator(hiveId);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.teamLifecycle().onPlayerJoin(player);
        }
    }

    public static void onPlayerLoaded(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.teamLifecycle().onPlayerLoaded(player);
        }
    }

    public static void onPlayerLeave(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.teamLifecycle().onPlayerLeave(player);
        }
    }

    public static void onPlayerAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        HiveMindCoordinator coordinator = coordinatorForEntity(newPlayer);
        if (coordinator != null) {
            coordinator.teamLifecycle().onPlayerAfterRespawn(oldPlayer, newPlayer, alive);
        }
    }

    public static void syncUnderwaterOnJoin(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.environmentSync().syncUnderwaterOnJoin(player);
        }
    }

    public static void syncTranslucencyOnJoin(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.effectSync().syncTranslucencyOnJoin(player);
        }
    }

    public static void resyncPlayerFromHive(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.teamLifecycle().resyncPlayerFromHive(player);
        }
    }

    public static boolean applyBooleanSetting(
        @Nullable MinecraftServer server,
        @Nullable ServerPlayer initiator,
        String key,
        boolean enabled
    ) {
        if (server == null) {
            return OIMRConfig.setBooleanSettingWithTransition(key, enabled, null);
        }
        HiveMindCoordinator coordinator = HiveMindCoordinator.getOrCreate(server);
        boolean persisted = OIMRConfig.setBooleanSettingWithTransition(key, enabled, nextSlotSync ->
            coordinator.inventoryAndAmmo().prepareSlotSyncTransition(initiator, OIMRConfig.getSlotSyncState(), nextSlotSync)
        );
        coordinator.teamLifecycle().onConfigChanged();
        return persisted;
    }

    public static OIMRConfig.ReloadResult reloadConfig(@Nullable MinecraftServer server, @Nullable ServerPlayer initiator) {
        if (server == null) {
            return OIMRConfig.reloadWithResult(null);
        }
        HiveMindCoordinator coordinator = HiveMindCoordinator.getOrCreate(server);
        OIMRConfig.ReloadResult result = OIMRConfig.reloadWithResult(nextSlotSync ->
            coordinator.inventoryAndAmmo().prepareSlotSyncTransition(initiator, OIMRConfig.getSlotSyncState(), nextSlotSync)
        );
        if (result.isSuccess() || result.status() == OIMRConfig.ReloadStatus.FAILED_APPLIED_NOT_PERSISTED) {
            coordinator.teamLifecycle().onConfigChanged();
            coordinator.teamLifecycle().notifyIfInvalidAffinityReset();
        }
        return result;
    }

    public static boolean resetConfigToDefaults(@Nullable MinecraftServer server, @Nullable ServerPlayer initiator) {
        if (server != null) {
            HiveMindCoordinator coordinator = HiveMindCoordinator.getOrCreate(server);
            coordinator.inventoryAndAmmo().prepareSlotSyncTransition(
                initiator,
                OIMRConfig.getSlotSyncState(),
                OIMRConfig.SlotSyncState.ALL_SYNCED
            );
            boolean persisted = OIMRConfig.resetToDefaults();
            coordinator.teamLifecycle().onConfigChanged();
            return persisted;
        }
        return OIMRConfig.resetToDefaults();
    }

    public static void onConfigChanged(@Nullable MinecraftServer server) {
        if (server != null) {
            HiveMindCoordinator.getOrCreate(server).teamLifecycle().onConfigChanged();
        }
    }

    public static void beginTeamMutation(MinecraftServer server) {
        HiveMindCoordinator.getOrCreate(server).teamLifecycle().beginTeamMutation();
    }

    public static void endTeamMutation(MinecraftServer server) {
        HiveMindCoordinator.getOrCreate(server).teamLifecycle().endTeamMutation();
    }

    public static void syncAllPlayerTeams(@Nullable MinecraftServer server) {
        if (server != null) {
            HiveMindCoordinator.getOrCreate(server).teamLifecycle().syncAllPlayerTeams();
        }
    }

    public static void onTeamRemoved(@Nullable MinecraftServer server, PlayerTeam team) {
        if (server != null) {
            HiveMindCoordinator.getOrCreate(server).teamLifecycle().onTeamRemoved(team);
        }
    }

    public static void onTeamAdded(@Nullable MinecraftServer server, PlayerTeam team) {
        if (server != null) {
            HiveMindCoordinator.getOrCreate(server).teamLifecycle().onTeamAdded(team);
        }
    }

    public static TeamLifecycleService.CleanupReport inspectCleanup(
        MinecraftServer server,
        int orphanRetentionDays,
        int inactiveRetentionDays
    ) {
        return HiveMindCoordinator.getOrCreate(server)
            .teamLifecycle()
            .inspectCleanup(orphanRetentionDays, inactiveRetentionDays);
    }

    public static TeamLifecycleService.CleanupReport executeCleanup(
        MinecraftServer server,
        int orphanRetentionDays,
        int inactiveRetentionDays
    ) {
        return HiveMindCoordinator.getOrCreate(server)
            .teamLifecycle()
            .executeCleanup(orphanRetentionDays, inactiveRetentionDays);
    }

    public static void broadcastInventoryChanges(ServerPlayer source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.inventoryAndAmmo().broadcastInventoryChanges(source);
        }
    }

    public static void onPlayerStartUsingItem(ServerPlayer player, InteractionHand hand, ItemStack usedItem) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.inventoryAndAmmo().onPlayerStartUsingItem(player, hand, usedItem);
        }
    }

    public static void onPlayerStopUsingItem(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.inventoryAndAmmo().onPlayerStopUsingItem(player);
        }
    }

    public static void onAfterPlayerStopUsingItem(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.inventoryAndAmmo().onAfterPlayerStopUsingItem(player);
        }
    }

    public static void onCrossbowAmmoLoaded(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.inventoryAndAmmo().onCrossbowAmmoLoaded(player);
        }
    }

    public static ItemStack getReservedProjectile(ServerPlayer player, ItemStack heldWeapon) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null ? coordinator.inventoryAndAmmo().getReservedProjectile(player, heldWeapon) : ItemStack.EMPTY;
    }

    public static boolean shouldRejectStaleOrEmptyUseItem(ServerPlayer player, ItemStack itemStack, InteractionHand hand) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.inventoryAndAmmo().shouldRejectStaleOrEmptyUseItem(player, itemStack, hand);
    }

    public static boolean shouldRejectStaleUseItemOn(ServerPlayer player, ItemStack itemStack, InteractionHand hand) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.inventoryAndAmmo().shouldRejectStaleUseItemOn(player, itemStack, hand);
    }

    public static boolean shouldRejectEmptyOrStaleConsume(ServerPlayer player, ItemStack stack) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.inventoryAndAmmo().shouldRejectEmptyOrStaleConsume(player, stack);
    }

    public static void onAfterServerPlayerUseItem(ServerPlayer player, InteractionHand hand, InteractionResult result) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.inventoryAndAmmo().onAfterServerPlayerUseItem(player, hand, result);
        }
    }

    public static boolean shouldDeferSharedHeldItemUpdate(ServerPlayer player, Packet<?> packet) {
        return filterOrDeferSharedHeldItemUpdate(player, packet) == null;
    }

    public static @Nullable Packet<?> filterOrDeferSharedHeldItemUpdate(ServerPlayer player, Packet<?> packet) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null ? coordinator.inventoryAndAmmo().filterOrDeferSharedHeldItemUpdate(player, packet) : packet;
    }

    public static void onEndServerTick(MinecraftServer server) {
        HiveMindCoordinator.getOrCreate(server).teamLifecycle().onEndServerTick();
    }

    public static void onPlayerIgnited(Entity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.environmentSync().onPlayerIgnited(entity);
        }
    }

    public static void onPlayerClearFire(Entity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.environmentSync().onPlayerClearFire(entity);
        }
    }

    public static void onPlayerTicksFrozenChanged(Entity entity, int ticks) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.environmentSync().onPlayerTicksFrozenChanged(entity, ticks);
        }
    }

    public static void onPlayerAirSupplyChanged(Entity entity, int supply) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.environmentSync().onPlayerAirSupplyChanged(entity, supply);
        }
    }

    public static OptionalInt tryDecreaseAirSupplyWithGroupRespiration(LivingEntity entity, int currentSupply) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null
            ? coordinator.environmentSync().tryDecreaseAirSupplyWithGroupRespiration(entity, currentSupply)
            : OptionalInt.empty();
    }

    public static void beginSplashPotionImpact(MinecraftServer server) {
        beginPotionImpact(server, true);
    }

    public static void beginPotionImpact(MinecraftServer server, boolean stackDurations) {
        HiveMindCoordinator.getOrCreate(server).effectSync().beginPotionImpact(stackDurations);
    }

    public static boolean captureSplashPotionEffect(LivingEntity target, MobEffectInstance effect) {
        HiveMindCoordinator coordinator = coordinatorForEntity(target);
        return coordinator != null && coordinator.effectSync().captureSplashPotionEffect(target, effect);
    }

    public static boolean captureInstantaneousPotionEffect(
        MobEffect mobEffect,
        LivingEntity target,
        int amplifier,
        double healthFactor
    ) {
        HiveMindCoordinator coordinator = coordinatorForEntity(target);
        return coordinator != null && coordinator.effectSync().captureInstantaneousPotionEffect(mobEffect, target, amplifier, healthFactor);
    }

    public static void endSplashPotionImpact(Entity potion, @Nullable Entity effectSource) {
        HiveMindCoordinator coordinator = coordinatorForEntity(potion);
        if (coordinator != null) {
            coordinator.effectSync().endSplashPotionImpact(potion, effectSource);
        }
    }

    public static void onEffectDowngraded(LivingEntity entity, MobEffectInstance effect) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.effectSync().onEffectDowngraded(entity, effect);
        }
    }

    public static double getEffectiveReplicatedEffectFactor(LivingEntity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            return coordinator.effectSync().getEffectiveReplicatedEffectFactor(entity);
        }
        return OIMRConfig.isSyncEffectsEnabled() ? OIMRConfig.getAffinityFactorDouble() : 0.0D;
    }

    public static void onEffectAddedOrUpdated(LivingEntity entity, MobEffectInstance effect, @Nullable Entity source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.effectSync().onEffectAddedOrUpdated(entity, effect, source);
        }
    }

    public static void onEffectsRemoved(LivingEntity entity, Collection<MobEffectInstance> removedEffects) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.effectSync().onEffectsRemoved(entity, removedEffects);
        }
    }

    public static boolean shouldApplyPeriodicEffectTick(LivingEntity entity, Holder<MobEffect> effectHolder) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator == null || coordinator.effectSync().shouldApplyPeriodicEffectTick(entity, effectHolder);
    }

    public static int beginPeriodicEffectTick(LivingEntity target, Holder<MobEffect> effectHolder) {
        HiveMindCoordinator coordinator = coordinatorForEntity(target);
        return coordinator != null ? coordinator.effectSync().beginPeriodicEffectTick(target, effectHolder) : 0;
    }

    public static void endPeriodicEffectTick(LivingEntity target, int flags) {
        HiveMindCoordinator coordinator = coordinatorForEntity(target);
        if (coordinator != null) {
            coordinator.effectSync().endPeriodicEffectTick(flags);
        }
    }

    public static double getInfestedChanceScale(LivingEntity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null ? coordinator.effectSync().getInfestedChanceScale(entity) : 1.0D;
    }

    public static float scaleFoodExhaustion(ServerPlayer player, float rawAmount) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null ? coordinator.vitalsAndDamage().scaleFoodExhaustion(player, rawAmount) : rawAmount;
    }

    public static boolean shouldTickFood(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator == null || coordinator.vitalsAndDamage().shouldTickFood(player);
    }

    public static boolean isPrimaryPeacefulRegenMember(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator == null || coordinator.vitalsAndDamage().isPrimaryPeacefulRegenMember(player);
    }

    public static boolean shouldRunPeacefulRegen(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator == null || coordinator.vitalsAndDamage().shouldRunPeacefulRegen(player);
    }

    public static boolean beginPeacefulRegenTick(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.vitalsAndDamage().beginPeacefulRegenTick(player);
    }

    public static void endPeacefulRegenTick(ServerPlayer player, boolean restoreSuppress) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().endPeacefulRegenTick(restoreSuppress);
        }
    }

    public static boolean shouldApplyPeacefulFoodRegen(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator == null || coordinator.vitalsAndDamage().shouldApplyPeacefulFoodRegen(player);
    }

    public static boolean shouldSuppressDuplicateHazardHurt(LivingEntity entity, DamageSource source, float damage) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null && coordinator.vitalsAndDamage().shouldSuppressDuplicateHazardHurt(entity, source, damage);
    }

    public static float adjustHazardDamage(LivingEntity entity, DamageSource source, float damage) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null ? coordinator.vitalsAndDamage().adjustHazardDamage(entity, source, damage) : damage;
    }

    public static void recordHazardHurtIfApplicable(LivingEntity entity, DamageSource source, float preCooldownDamage) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().recordHazardHurtIfApplicable(entity, source, preCooldownDamage);
        }
    }

    public static void onHealthChanged(LivingEntity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().onHealthChanged(entity);
        }
    }

    public static void onAbsorptionChanged(LivingEntity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().onAbsorptionChanged(entity);
        }
    }

    public static void onPlayerHealed(LivingEntity entity, float healedAmount) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().onPlayerHealed(entity, healedAmount);
        }
    }

    public static void onAfterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().onAfterDamage(entity, source, baseDamageTaken, damageTaken, blocked);
        }
    }

    public static void onPlayerActuallyHurt(ServerPlayer victim, DamageSource source, float damageTaken) {
        HiveMindCoordinator coordinator = coordinatorForEntity(victim);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().onPlayerActuallyHurt(victim, source, damageTaken);
        }
    }

    public static void onPlayerConsumedFood(ServerPlayer eater, ItemStack foodItem, int recoveredFoodPoints) {
        HiveMindCoordinator coordinator = coordinatorForEntity(eater);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().onPlayerConsumedFood(eater, foodItem, recoveredFoodPoints);
        }
    }

    public static void onEnderPearlTeleport(ServerPlayer source, ServerLevel targetLevel, Vec3 targetPos) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.teleportAndAudio().onEnderPearlTeleport(source, targetLevel, targetPos);
        }
    }

    public static void onPortalTeleport(ServerPlayer source, TeleportTransition sourceTransition) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.teleportAndAudio().onPortalTeleport(source, sourceTransition);
        }
    }

    public static void onEndCreditsPortalTriggered(ServerPlayer source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.teleportAndAudio().onEndCreditsPortalTriggered(source);
        }
    }

    public static void pushActiveMobSoundSource(Mob mob) {
        HiveMindCoordinator coordinator = coordinatorForEntity(mob);
        if (coordinator != null) {
            coordinator.teleportAndAudio().pushActiveMobSoundSource(mob);
        }
    }

    public static void popActiveMobSoundSource(Mob mob) {
        HiveMindCoordinator coordinator = coordinatorForEntity(mob);
        if (coordinator != null) {
            coordinator.teleportAndAudio().popActiveMobSoundSource();
        }
    }

    public static @Nullable Mob peekActiveMobSoundSource(ServerLevel level) {
        return HiveMindCoordinator.getOrCreate(level.getServer()).teleportAndAudio().peekActiveMobSoundSource();
    }

    public static void onMobSoundEmitted(
        ServerLevel level,
        @Nullable Entity except,
        Vec3 mobPos,
        Holder<SoundEvent> sound,
        SoundSource source,
        float volume,
        float pitch,
        long seed
    ) {
        HiveMindCoordinator.getOrCreate(level.getServer())
            .teleportAndAudio()
            .onMobSoundEmitted(level, except, mobPos, sound, source, volume, pitch, seed);
    }

    public static void beginItemBreakHook(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.teleportAndAudio().beginItemBreakHook();
        }
    }

    public static void onPlayerEquippedItemBroken(ServerPlayer source, ItemStack brokenItem, net.minecraft.world.entity.EquipmentSlot inSlot) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.teleportAndAudio().onPlayerEquippedItemBroken(source, brokenItem, inSlot);
        }
    }

    public static void endItemBreakHook(ServerPlayer player, ItemStack brokenItem) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.teleportAndAudio().endItemBreakHook(player, brokenItem);
        }
    }

    public static void onCaveSoundEmitted(
        ServerPlayer source,
        Identifier soundId,
        double offsetX,
        double offsetY,
        double offsetZ,
        long seed
    ) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.teleportAndAudio().onCaveSoundEmitted(source, soundId, offsetX, offsetY, offsetZ, seed);
        }
    }

    public static void onPlayerStartedSleeping(ServerPlayer source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onPlayerStartedSleeping(source);
        }
    }

    public static void onPlayerStoppedSleeping(ServerPlayer source, boolean forcefulWakeUp, boolean updateLevelList) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onPlayerStoppedSleeping(source, forcefulWakeUp, updateLevelList);
        }
    }

    public static void onPlayerKnockback(
        LivingEntity entity,
        double rawPower,
        double appliedXd,
        double appliedZd,
        @Nullable DamageSource source,
        float damage,
        boolean comesFromEffect
    ) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.vitalsAndDamage().onPlayerKnockback(entity, rawPower, appliedXd, appliedZd, source, damage, comesFromEffect);
        }
    }

    public static void onExplosionKnockback(ServerLevel level, Map<Player, Vec3> hitPlayers) {
        HiveMindCoordinator.getOrCreate(level.getServer()).vitalsAndDamage().onExplosionKnockback(level, hitPlayers);
    }

    public static int scaleExperiencePoints(ServerPlayer player, int rawPoints) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null ? coordinator.progressionAndSocial().scaleExperiencePoints(player, rawPoints) : rawPoints;
    }

    public static int scaleExperienceLevels(ServerPlayer player, int rawLevels) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null ? coordinator.progressionAndSocial().scaleExperienceLevels(player, rawLevels) : rawLevels;
    }

    public static void onExperienceChanged(ServerPlayer source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onExperienceChanged(source);
        }
    }

    public static int getEnchantmentSeed(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null ? coordinator.progressionAndSocial().getEnchantmentSeed(player) : 0;
    }

    public static void onEnchantmentPerformed(ServerPlayer source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onEnchantmentPerformed(source);
        }
    }

    public static void onRespawnPositionChanged(
        ServerPlayer source,
        ServerPlayer.@Nullable RespawnConfig respawnConfig,
        boolean showMessage
    ) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onRespawnPositionChanged(source, respawnConfig, showMessage);
        }
    }

    public static void onAdvancementAwarded(ServerPlayer source, AdvancementHolder holder, String criterion) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onAdvancementAwarded(source, holder, criterion);
        }
    }

    public static void onAdvancementRevoked(ServerPlayer source, AdvancementHolder holder, String criterion) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onAdvancementRevoked(source, holder, criterion);
        }
    }

    public static void onRecipesAdded(ServerPlayer source, Collection<RecipeHolder<?>> recipes) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onRecipesAdded(source, recipes);
        }
    }

    public static void onRecipesRemoved(ServerPlayer source, Collection<RecipeHolder<?>> recipes) {
        HiveMindCoordinator coordinator = coordinatorForEntity(source);
        if (coordinator != null) {
            coordinator.progressionAndSocial().onRecipesRemoved(source, recipes);
        }
    }

    public static UUID getHiveReputationUuid(String hiveId) {
        return ProgressionAndSocialService.getHiveReputationUuid(hiveId);
    }

    public static UUID resolveReputationTargetUuid(@Nullable Entity entity, UUID fallback) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null
            ? coordinator.progressionAndSocial().resolveReputationTargetUuid(entity, fallback)
            : fallback;
    }

    public static boolean areSameHive(@Nullable Entity first, @Nullable Entity second) {
        HiveMindCoordinator coordinator = coordinatorForEntity(first);
        return coordinator != null && coordinator.progressionAndSocial().areSameHive(first, second);
    }

    public static void applyOfflineZombieVillagerCure(MinecraftServer server, UUID starterUuid, Villager villager) {
        HiveMindCoordinator.getOrCreate(server)
            .progressionAndSocial()
            .applyOfflineZombieVillagerCure(starterUuid, villager);
    }

    public static void onBeforePlayerDie(ServerPlayer player, DamageSource source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.deathCascade().onBeforePlayerDie(player, source);
        }
    }

    public static boolean shouldHideDroppedEntityFromPlayer(Entity entity, ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.deathCascade().shouldHideDroppedEntityFromPlayer(entity, player);
    }

    public static @Nullable ServerPlayer resolveDeathLootTarget(Player player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null ? coordinator.deathCascade().resolveDeathLootTarget(player) : null;
    }

    public static @Nullable ServerPlayer resolveContainerCloseDropTarget(LivingEntity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null ? coordinator.deathCascade().resolveContainerCloseDropTarget(entity) : null;
    }

    public static void popDyingDamageSource(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.deathCascade().popDyingDamageSource(player);
        }
    }

    public static void onAfterPlayerDieReturn(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        if (coordinator != null) {
            coordinator.deathCascade().onAfterPlayerDieReturn(player);
        }
    }

    public static @Nullable DamageSource peekDyingDamageSource(LivingEntity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null ? coordinator.deathCascade().peekDyingDamageSource(entity) : null;
    }

    public static boolean shouldPreventPetAttack(LivingEntity attacker, @Nullable LivingEntity target) {
        HiveMindCoordinator coordinator = coordinatorForEntity(attacker);
        return coordinator != null && coordinator.progressionAndSocial().shouldPreventPetAttack(attacker, target);
    }

    public static boolean shouldSuppressDeathInventoryDrop(Player player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.deathCascade().shouldSuppressDeathInventoryDrop(player);
    }

    public static @Nullable Component consumePendingDeathMessage(LivingEntity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null ? coordinator.deathCascade().consumePendingDeathMessage(entity) : null;
    }

    public static @Nullable Component getLastHiveDeathMessage(MinecraftServer server, UUID playerId) {
        return HiveMindCoordinator.getOrCreate(server).deathCascade().getLastHiveDeathMessage(playerId);
    }

    public static boolean consumeSuppressDeathChat(ServerPlayer player) {
        HiveMindCoordinator coordinator = coordinatorForEntity(player);
        return coordinator != null && coordinator.deathCascade().consumeSuppressDeathChat(player);
    }

    public static @Nullable Component createAndCaptureCollectiveStarveMessage(LivingEntity entity, @Nullable Entity secondary) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null ? coordinator.deathCascade().createAndCaptureCollectiveStarveMessage(entity, secondary) : null;
    }

    public static void captureOriginalDeathContext(LivingEntity entity, Component deathMessage, @Nullable Entity secondaryEntity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.deathCascade().captureOriginalDeathContext(entity, deathMessage, secondaryEntity);
        }
    }

    public static void onPlayerDeath(LivingEntity entity, DamageSource source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.deathCascade().onPlayerDeath(entity, source);
        }
    }

    public static void onTotemActivated(LivingEntity entity) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        if (coordinator != null) {
            coordinator.deathCascade().onTotemActivated(entity);
        }
    }

    public static boolean tryActivateGroupTotem(LivingEntity entity, DamageSource source) {
        HiveMindCoordinator coordinator = coordinatorForEntity(entity);
        return coordinator != null && coordinator.deathCascade().tryActivateGroupTotem(entity, source);
    }
}
