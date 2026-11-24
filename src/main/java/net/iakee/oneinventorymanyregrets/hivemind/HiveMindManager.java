package net.iakee.oneinventorymanyregrets.hivemind;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.network.OIMRNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates real-time Hive Mind synchronization across players in each team.
 */
public final class HiveMindManager {
    private static final long POST_LOAD_SAFETY_DELAY_TICKS = 5L;
    private static final double FRACTIONAL_EPSILON = 1.0E-6D;
    private static final Identifier HEALTH_BOOST_MODIFIER_ID = Identifier.withDefaultNamespace("effect.health_boost");

    private static final Map<UUID, String> PLAYER_HIVES = new HashMap<>();
    private static final Map<UUID, String> KNOWN_PLAYER_HIVES = new HashMap<>();
    private static final Set<ServerPlayer> ATTACHED_PLAYERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<UUID, Long> PENDING_POST_LOAD_CHECKS = new HashMap<>();
    private static final Map<UUID, Component> PENDING_DEATH_MESSAGES = new HashMap<>();
    private static final Map<UUID, Component> LAST_HIVE_DEATH_MESSAGES = new HashMap<>();
    private static final Set<UUID> SUPPRESS_DEATH_CHAT = new HashSet<>();
    private static final Map<UUID, CapturedDeathContext> CAPTURED_DEATH_CONTEXTS = new HashMap<>();
    private static final Map<UUID, DamageSource> DYING_DAMAGE_SOURCES = new HashMap<>();
    private static final Map<UUID, List<PendingDamageContribution>> PENDING_DAMAGE_SOURCES = new HashMap<>();
    private static @Nullable SplashImpactState splashImpact = null;
    private static boolean pendingInvalidAffinityChatWarning = false;

    private static final Map<ResourceKey<DamageType>, String> SIMPLE_DEATH_MESSAGE_KEYS = Map.ofEntries(
        Map.entry(DamageTypes.DROWN, "death.oimr.drown"),
        Map.entry(DamageTypes.LAVA, "death.oimr.lava"),
        Map.entry(DamageTypes.IN_FIRE, "death.oimr.fire"),
        Map.entry(DamageTypes.CAMPFIRE, "death.oimr.fire"),
        Map.entry(DamageTypes.ON_FIRE, "death.oimr.fire"),
        Map.entry(DamageTypes.HOT_FLOOR, "death.oimr.fire"),
        Map.entry(DamageTypes.UNATTRIBUTED_FIREBALL, "death.oimr.fire"),
        Map.entry(DamageTypes.FREEZE, "death.oimr.freeze"),
        Map.entry(DamageTypes.WITHER, "death.oimr.wither"),
        Map.entry(DamageTypes.MAGIC, "death.oimr.magic"),
        Map.entry(DamageTypes.INDIRECT_MAGIC, "death.oimr.magic"),
        Map.entry(DamageTypes.GENERIC_KILL, "death.oimr.generic_kill"),
        Map.entry(DamageTypes.GENERIC, "death.oimr.generic_kill"),
        Map.entry(DamageTypes.FALL, "death.oimr.fall"),
        Map.entry(DamageTypes.FELL_OUT_OF_WORLD, "death.oimr.void"),
        Map.entry(DamageTypes.IN_WALL, "death.oimr.in_wall"),
        Map.entry(DamageTypes.CRAMMING, "death.oimr.cramming"),
        Map.entry(DamageTypes.EXPLOSION, "death.oimr.explosion"),
        Map.entry(DamageTypes.PLAYER_EXPLOSION, "death.oimr.explosion"),
        Map.entry(DamageTypes.BAD_RESPAWN_POINT, "death.oimr.bad_respawn_point"),
        Map.entry(DamageTypes.FLY_INTO_WALL, "death.oimr.fly_into_wall"),
        Map.entry(DamageTypes.LIGHTNING_BOLT, "death.oimr.lightning"),
        Map.entry(DamageTypes.OUTSIDE_BORDER, "death.oimr.outside_border"),
        Map.entry(DamageTypes.SONIC_BOOM, "death.oimr.sonic_boom"),
        Map.entry(DamageTypes.CACTUS, "death.oimr.cactus"),
        Map.entry(DamageTypes.SWEET_BERRY_BUSH, "death.oimr.sweet_berry_bush"),
        Map.entry(DamageTypes.STALAGMITE, "death.oimr.stalagmite"),
        Map.entry(DamageTypes.FALLING_STALACTITE, "death.oimr.falling_stalactite"),
        Map.entry(DamageTypes.FALLING_ANVIL, "death.oimr.anvil"),
        Map.entry(DamageTypes.FALLING_BLOCK, "death.oimr.falling_block"),
        Map.entry(DamageTypes.THORNS, "death.oimr.thorns"),
        Map.entry(DamageTypes.ENDER_PEARL, "death.oimr.ender_pearl"),
        Map.entry(DamageTypes.DRAGON_BREATH, "death.oimr.dragon_breath"),
        Map.entry(DamageTypes.DRY_OUT, "death.oimr.dry_out"),
        Map.entry(DamageTypes.STING, "death.oimr.sting"),
        Map.entry(DamageTypes.FIREWORKS, "death.oimr.fireworks"),
        Map.entry(DamageTypes.MACE_SMASH, "death.oimr.mace_smash"),
        Map.entry(DamageTypes.SULFUR_CUBE_HOT, "death.oimr.sulfur_cube_hot")
    );

    private static boolean syncingEffects = false;
    private static boolean syncingHealth = false;
    private static boolean syncingAffinityDamage = false;
    private static boolean suppressAffinityVitalsCascade = false;
    private static boolean applyingUnscaledHungerEffect = false;
    private static boolean syncingExperience = false;
    private static boolean syncingAdvancements = false;
    private static boolean syncingRecipes = false;
    private static boolean syncingEnvironment = false;
    private static boolean syncingRespawn = false;
    private static boolean syncingKnockback = false;
    private static boolean handlingGroupTotem = false;
    private static int teamMutationDepth = 0;
    private static boolean cascadingDeath = false;

    private HiveMindManager() {
    }

    public static void onServerStarted(MinecraftServer server) {
        OIMRConfig.load();
        pendingInvalidAffinityChatWarning = OIMRConfig.consumeInvalidAffinityResetPending();
        if (pendingInvalidAffinityChatWarning) {
            OIMRMod.LOGGER.warn("Invalid OIMR affinity setting detected in config; affinity was reset to 100%.");
        }
        PLAYER_HIVES.clear();
        KNOWN_PLAYER_HIVES.clear();
        ATTACHED_PLAYERS.clear();
        PENDING_POST_LOAD_CHECKS.clear();
        PENDING_DEATH_MESSAGES.clear();
        LAST_HIVE_DEATH_MESSAGES.clear();
        SUPPRESS_DEATH_CHAT.clear();
        CAPTURED_DEATH_CONTEXTS.clear();
        DYING_DAMAGE_SOURCES.clear();
        PENDING_DAMAGE_SOURCES.clear();
        splashImpact = null;
        syncingEffects = false;
        syncingHealth = false;
        syncingAffinityDamage = false;
        suppressAffinityVitalsCascade = false;
        applyingUnscaledHungerEffect = false;
        syncingExperience = false;
        syncingAdvancements = false;
        syncingRecipes = false;
        syncingEnvironment = false;
        syncingRespawn = false;
        syncingKnockback = false;
        handlingGroupTotem = false;
        cascadingDeath = false;
        teamMutationDepth = 0;
        HiveMindData.get(server);
    }

    public static void onServerStopped(MinecraftServer server) {
        HiveMindData.get(server).setDirty();
        PLAYER_HIVES.clear();
        KNOWN_PLAYER_HIVES.clear();
        ATTACHED_PLAYERS.clear();
        PENDING_POST_LOAD_CHECKS.clear();
        PENDING_DEATH_MESSAGES.clear();
        LAST_HIVE_DEATH_MESSAGES.clear();
        SUPPRESS_DEATH_CHAT.clear();
        CAPTURED_DEATH_CONTEXTS.clear();
        DYING_DAMAGE_SOURCES.clear();
        PENDING_DAMAGE_SOURCES.clear();
        splashImpact = null;
        pendingInvalidAffinityChatWarning = false;
        syncingEffects = false;
        syncingHealth = false;
        syncingAffinityDamage = false;
        suppressAffinityVitalsCascade = false;
        applyingUnscaledHungerEffect = false;
        syncingExperience = false;
        syncingAdvancements = false;
        syncingRecipes = false;
        syncingEnvironment = false;
        syncingRespawn = false;
        syncingKnockback = false;
        handlingGroupTotem = false;
        cascadingDeath = false;
        teamMutationDepth = 0;
    }

    public static void onBeforeSave(MinecraftServer server) {
        HiveMindData data = HiveMindData.get(server);
        if (!data.getAll().isEmpty()) {
            data.setDirty();
        }
    }

    public static boolean isSyncingAdvancements() {
        return syncingAdvancements;
    }

    public static boolean isSyncingEnvironment() {
        return syncingEnvironment;
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
        return ATTACHED_PLAYERS.contains(player) && PLAYER_HIVES.containsKey(player.getUUID());
    }

    public static HiveMind getHiveForPlayer(ServerPlayer player) {
        if (!ATTACHED_PLAYERS.contains(player)) {
            return null;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return null;
        }
        MinecraftServer server = player.level().getServer();
        return server != null ? HiveMindData.get(server).getIfPresent(hiveId) : null;
    }

    public static List<ServerPlayer> getOnlineMembers(MinecraftServer server, String hiveId) {
        List<ServerPlayer> members = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ATTACHED_PLAYERS.contains(player) && hiveId.equals(PLAYER_HIVES.get(player.getUUID()))) {
                members.add(player);
            }
        }
        return members;
    }

    public static List<ServerPlayer> getAliveMembers(MinecraftServer server, String hiveId) {
        List<ServerPlayer> members = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ATTACHED_PLAYERS.contains(player) && hiveId.equals(PLAYER_HIVES.get(player.getUUID())) && !player.isDeadOrDying()) {
                members.add(player);
            }
        }
        return members;
    }

    private static boolean isVitalHazardTarget(ServerPlayer player) {
        return !player.isDeadOrDying()
            && !player.isCreative()
            && !player.isSpectator()
            && !player.getAbilities().invulnerable;
    }

    public static int getHungerScaleDenominator(MinecraftServer server, String hiveId) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ATTACHED_PLAYERS.contains(player)
                && hiveId.equals(PLAYER_HIVES.get(player.getUUID()))
                && isVitalHazardTarget(player)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        attachPlayer(player, true, true);
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            PENDING_POST_LOAD_CHECKS.put(player.getUUID(), server.overworld().getGameTime() + 20L);
        }
    }

    public static void onPlayerLoaded(ServerPlayer player) {
        if (!isAttached(player)) {
            return;
        }
        resyncPlayerFromHive(player);
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            PENDING_POST_LOAD_CHECKS.put(player.getUUID(), server.overworld().getGameTime() + POST_LOAD_SAFETY_DELAY_TICKS);
            broadcastInvalidAffinityWarningIfPending(server);
        }
    }

    public static void notifyIfInvalidAffinityReset(@Nullable MinecraftServer server) {
        if (OIMRConfig.consumeInvalidAffinityResetPending()) {
            OIMRMod.LOGGER.warn("Invalid OIMR affinity setting detected in config; affinity was reset to 100%.");
            pendingInvalidAffinityChatWarning = true;
        }
        if (server != null) {
            broadcastInvalidAffinityWarningIfPending(server);
        }
    }

    private static void broadcastInvalidAffinityWarningIfPending(MinecraftServer server) {
        if (!pendingInvalidAffinityChatWarning || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        pendingInvalidAffinityChatWarning = false;
        Component warning = OIMRLang.translatable("oimr.warning.affinity_reset");
        server.getPlayerList().broadcastSystemMessage(warning, recipient -> OIMRLang.localizeForPlayer(warning, recipient), false);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        PENDING_POST_LOAD_CHECKS.remove(player.getUUID());
        DYING_DAMAGE_SOURCES.remove(player.getUUID());
        HiveMind hive = getHiveForPlayer(player);
        if (hive != null) {
            MinecraftServer server = player.level().getServer();
            if (server != null) {
                if (OIMRConfig.isFullAffinity() && !player.isDeadOrDying()) {
                    hive.snapshotEffectsFrom(player.getActiveEffects());
                    hive.setHealth(player.getHealth());
                    hive.setAbsorption(player.getAbsorptionAmount());
                }
                HiveMindData.get(server).setDirty();
            }
        }
        detachPlayer(player);
    }

    public static void onPlayerAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        detachPlayer(oldPlayer);

        MinecraftServer server = newPlayer.level().getServer();
        if (server == null) {
            return;
        }

        String hiveId = getHiveId(newPlayer);
        HiveMindData data = HiveMindData.get(server);
        HiveMind hive = data.getOrCreate(hiveId);
        if (!hive.isInitialized()) {
            hive.markInitializedEmpty();
        }

        if (!alive && OIMRConfig.isFullAffinity()) {
            boolean anyAliveMember = !getAliveMembers(server, hiveId).isEmpty();
            if (!anyAliveMember) {
                hive.resetVitalsForRespawn(newPlayer.getMaxHealth());
            }
        }

        data.setDirty();
        attachPlayer(newPlayer, false, true);
    }

    private static void attachPlayer(ServerPlayer player, boolean allowInitialCapture, boolean syncVitals) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        String hiveId = getHiveId(player);
        HiveMindData data = HiveMindData.get(server);
        HiveMind hive = data.getOrCreate(hiveId);
        PersonalInventoryState personal = data.getOrCreatePersonalState(player.getUUID());

        if (!hive.isInitialized()) {
            if (allowInitialCapture && HiveMindData.DEFAULT_HIVE_ID.equals(hiveId)) {
                hive.initializeFrom(player, personal);
            } else {
                hive.markInitializedEmpty();
            }
            data.setDirty();
        }

        PLAYER_HIVES.put(player.getUUID(), hiveId);
        KNOWN_PLAYER_HIVES.put(player.getUUID(), hiveId);
        ATTACHED_PLAYERS.add(player);

        ((HiveInventoryAccess) player.getInventory())
            .oimr$setItems(HiveRoutedNonNullList.create(hive.getItems(), personal.getHotbarItems()));
        ((HiveInventoryAccess) player.getEnderChestInventory()).oimr$setItems(hive.getEnderChestItems());
        HiveEquipmentAccess equipAccess = (HiveEquipmentAccess) ((HiveLivingEntityAccess) player).oimr$getEquipment();
        equipAccess.oimr$setEquipmentItems(hive.getEquipmentMap());
        equipAccess.oimr$setRoutedMaps(hive.getEquipmentMap(), personal.getEquipmentMap());
        ((HiveFoodDataAccess) player).oimr$setFoodData(hive.getFoodData());

        syncingRespawn = true;
        try {
            player.setRespawnPosition(hive.getRespawnConfig(), false);
        } finally {
            syncingRespawn = false;
        }

        ((HivePlayerProgressAccess) player).oimr$setEnchantmentSeed(hive.getEnchantmentSeed());

        syncingAdvancements = true;
        try {
            ((HivePlayerAdvancementsAccess) player.getAdvancements())
                .oimr$syncFromHive(server.getAdvancements(), hive.getAdvancements());
        } finally {
            syncingAdvancements = false;
        }

        syncingRecipes = true;
        try {
            ((HiveServerRecipeBookAccess) player.getRecipeBook())
                .oimr$syncFromHive(hive.getKnownRecipes(), hive.getHighlightRecipes(), player);
        } finally {
            syncingRecipes = false;
        }

        ((HiveLivingEntityAccess) player).oimr$detectEquipmentUpdates();

        if (syncVitals && !player.isDeadOrDying()) {
            syncVitalsToPlayer(player, server, hiveId, hive, true);
        }

        player.inventoryMenu.sendAllDataToRemote();
    }

    public static void resyncPlayerFromHive(ServerPlayer player) {
        if (!isAttached(player) || player.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return;
        }
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        ((HiveLivingEntityAccess) player).oimr$detectEquipmentUpdates();
        syncVitalsToPlayer(player, server, hiveId, hive, false);
        ((HivePlayerProgressAccess) player).oimr$setEnchantmentSeed(hive.getEnchantmentSeed());
        if (player.containerMenu instanceof HiveEnchantmentMenuAccess enchantMenu
            && enchantMenu.oimr$getSeed() != hive.getEnchantmentSeed()) {
            enchantMenu.oimr$refreshSeed(hive.getEnchantmentSeed());
        }
        player.inventoryMenu.sendAllDataToRemote();
    }

    private static void syncVitalsToPlayer(
        ServerPlayer player,
        MinecraftServer server,
        String hiveId,
        HiveMind hive,
        boolean forceApplyEffects
    ) {
        if (OIMRConfig.isFullAffinity()) {
            ServerPlayer otherAliveRep = null;
            for (ServerPlayer member : getAliveMembers(server, hiveId)) {
                if (member != player) {
                    otherAliveRep = member;
                    break;
                }
            }

            if (otherAliveRep != null) {
                hive.snapshotEffectsFrom(otherAliveRep.getActiveEffects());
                hive.setHealth(otherAliveRep.getHealth());
                hive.setAbsorption(otherAliveRep.getAbsorptionAmount());
            } else if (hive.getHealth() <= 0.0F) {
                hive.resetVitalsForRespawn(player.getMaxHealth());
                HiveMindData.get(server).setDirty();
            }

            if (forceApplyEffects || effectsDiffer(player, hive)) {
                applyHiveEffectsToPlayer(player, hive);
            }

            syncingHealth = true;
            try {
                float targetHealth = hive.getHealth() > 0.0F ? hive.getHealth() : player.getMaxHealth();
                player.setHealth(Mth.clamp(targetHealth, 0.01F, player.getMaxHealth()));
                player.setAbsorptionAmount(Mth.clamp(hive.getAbsorption(), 0.0F, player.getMaxAbsorption()));
            } finally {
                syncingHealth = false;
            }

            syncingEnvironment = true;
            try {
                player.setAirSupply(Mth.clamp(hive.getAirSupply(), -20, player.getMaxAirSupply()));
                player.setRemainingFireTicks(hive.getRemainingFireTicks());
                player.setSharedFlagOnFire(hive.getRemainingFireTicks() > 0);
                player.setTicksFrozen(hive.getTicksFrozen());
            } finally {
                syncingEnvironment = false;
            }
        }

        syncingExperience = true;
        try {
            player.experienceLevel = hive.getExperienceLevel();
            player.totalExperience = hive.getTotalExperience();
            player.experienceProgress = hive.getExperienceProgress();
        } finally {
            syncingExperience = false;
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
    }

    private static boolean effectsDiffer(ServerPlayer player, HiveMind hive) {
        Map<Holder<MobEffect>, MobEffectInstance> hiveEffects = hive.getEffects();
        Collection<MobEffectInstance> playerEffects = player.getActiveEffects();
        if (playerEffects.size() != hiveEffects.size()) {
            return true;
        }
        for (MobEffectInstance playerEffect : playerEffects) {
            MobEffectInstance hiveEffect = hiveEffects.get(playerEffect.getEffect());
            if (hiveEffect == null
                || hiveEffect.getAmplifier() != playerEffect.getAmplifier()
                || Math.abs(hiveEffect.getDuration() - playerEffect.getDuration()) > 2) {
                return true;
            }
        }
        return false;
    }

    private static void detachPlayer(ServerPlayer player) {
        ATTACHED_PLAYERS.remove(player);
        PLAYER_HIVES.remove(player.getUUID());
        ((HiveInventoryAccess) player.getInventory())
            .oimr$setItems(NonNullList.withSize(HiveMind.MAIN_INVENTORY_SIZE, ItemStack.EMPTY));
        ((HiveInventoryAccess) player.getEnderChestInventory())
            .oimr$setItems(NonNullList.withSize(HiveMind.ENDER_CHEST_SIZE, ItemStack.EMPTY));
        HiveEquipmentAccess equipAccess = (HiveEquipmentAccess) ((HiveLivingEntityAccess) player).oimr$getEquipment();
        equipAccess.oimr$setRoutedMaps(null, null);
        equipAccess.oimr$setEquipmentItems(new EnumMap<>(EquipmentSlot.class));
        ((HiveFoodDataAccess) player).oimr$setFoodData(new FoodData());
    }

    public static void onConfigChanged(@Nullable MinecraftServer server) {
        if (server == null) {
            return;
        }
        HiveMindData data = HiveMindData.get(server);
        if (OIMRConfig.isFullAffinity()) {
            PENDING_DAMAGE_SOURCES.clear();
            for (HiveMind hive : data.getAll()) {
                hive.clearAllPlayerRemainders();
                List<ServerPlayer> alive = getAliveMembers(server, hive.getId());
                if (!alive.isEmpty()) {
                    ServerPlayer rep = alive.getFirst();
                    hive.snapshotEffectsFrom(rep.getActiveEffects());
                    hive.setHealth(rep.getHealth());
                    hive.setAbsorption(rep.getAbsorptionAmount());
                }
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAttached(player)) {
                resyncPlayerFromHive(player);
            }
        }
        data.setDirty();
        OIMRNetworking.syncToAll(server);
    }

    private static void applyHiveEffectsToPlayer(ServerPlayer player, HiveMind hive) {
        syncingEffects = true;
        try {
            player.removeAllEffects();
            for (MobEffectInstance effect : hive.getEffects().values()) {
                MobEffectInstance copy = new MobEffectInstance(effect);
                player.forceAddEffect(copy, null);
                copy.onEffectStarted(player);
            }
        } finally {
            syncingEffects = false;
        }
    }

    public static void beginTeamMutation() {
        teamMutationDepth++;
    }

    public static void endTeamMutation(MinecraftServer server) {
        if (teamMutationDepth > 0) {
            teamMutationDepth--;
        }
        if (teamMutationDepth == 0 && server != null) {
            syncAllPlayerTeams(server);
        }
    }

    public static void syncAllPlayerTeams(MinecraftServer server) {
        if (server == null || teamMutationDepth > 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String currentHiveId = PLAYER_HIVES.get(player.getUUID());
            if (currentHiveId == null) {
                continue;
            }
            String expectedHiveId = getHiveId(player);
            if (!expectedHiveId.equals(currentHiveId)) {
                HiveMind oldHive = HiveMindData.get(server).getIfPresent(currentHiveId);
                if (oldHive != null) {
                    oldHive.clearPlayerRemainders(player.getUUID());
                    HiveMindData.get(server).setDirty();
                }
                PENDING_DAMAGE_SOURCES.remove(player.getUUID());
                if (!player.isDeadOrDying()) {
                    player.doCloseContainer();
                }
                detachPlayer(player);
                attachPlayer(player, false, true);
            }
        }
    }

    public static void broadcastInventoryChanges(ServerPlayer source) {
        String hiveId = PLAYER_HIVES.get(source.getUUID());
        if (hiveId == null) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }

        HiveMindData.get(server).setDirty();
        for (ServerPlayer member : getOnlineMembers(server, hiveId)) {
            member.containerMenu.broadcastChanges();
            ((HiveLivingEntityAccess) member).oimr$detectEquipmentUpdates();
        }
    }

    public static void onEndServerTick(MinecraftServer server) {
        syncAllPlayerTeams(server);

        long gameTime = server.overworld().getGameTime();
        if (!PENDING_POST_LOAD_CHECKS.isEmpty()) {
            List<UUID> duePlayers = null;
            Iterator<Map.Entry<UUID, Long>> it = PENDING_POST_LOAD_CHECKS.entrySet().iterator();
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

        HiveMindData data = HiveMindData.get(server);
        for (HiveMind hive : data.getAll()) {
            List<ServerPlayer> members = getOnlineMembers(server, hive.getId());
            if (members.isEmpty()) {
                continue;
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
                tickHiveEnvironment(hive, aliveMembers, gameTime, data);
                ServerPlayer rep = aliveMembers.getFirst();
                hive.snapshotEffectsFrom(rep.getActiveEffects());
                hive.setHealth(rep.getHealth());
                hive.setAbsorption(rep.getAbsorptionAmount());
            }
        }
    }

    private static void tickHiveEnvironment(
        HiveMind hive,
        List<ServerPlayer> aliveMembers,
        long gameTime,
        HiveMindData data
    ) {
        if (!hive.tryClaimEnvironmentTick(gameTime)) {
            return;
        }

        // 1. Air Supply (1x rate across party; no land-vs-water tug-of-war)
        ServerPlayer drowner = null;
        for (ServerPlayer member : aliveMembers) {
            if (!member.isCreative()
                && !member.isSpectator()
                && !member.getAbilities().invulnerable
                && member.isEyeInFluid(FluidTags.WATER)
                && !member.level().getBlockState(BlockPos.containing(member.getX(), member.getEyeY(), member.getZ())).is(Blocks.BUBBLE_COLUMN)
                && !member.canBreatheUnderwater()
                && !MobEffectUtil.hasWaterBreathing(member)) {
                drowner = member;
                break;
            }
        }
        int prevAir = hive.getAirSupply();
        if (drowner != null) {
            int nextAir = ((HiveLivingEntityAccess) drowner).oimr$decreaseAirSupply(prevAir);
            if (nextAir <= -20) {
                nextAir = 0;
                drowner.level().broadcastEntityEvent(drowner, (byte) 67);
                drowner.hurtServer(drowner.level(), drowner.damageSources().drown(), 2.0F);
            }
            hive.setAirSupply(nextAir);
        } else {
            int maxAir = aliveMembers.getFirst().getMaxAirSupply();
            if (prevAir < maxAir) {
                hive.setAirSupply(Math.min(prevAir + 4, maxAir));
            }
        }
        if (hive.getAirSupply() != prevAir) {
            data.setDirty();
        }

        // 2. Fire ticks (1x countdown + 1x damage across party)
        int prevFire = hive.getRemainingFireTicks();
        if (prevFire > 0) {
            boolean allFireImmune = true;
            ServerPlayer burner = null;
            for (ServerPlayer member : aliveMembers) {
                if (!member.fireImmune()) {
                    allFireImmune = false;
                    if (isVitalHazardTarget(member)) {
                        burner = member;
                        break;
                    }
                }
            }
            if (allFireImmune) {
                hive.setRemainingFireTicks(0);
            } else {
                if (burner != null && prevFire % 20 == 0 && !burner.isInLava()) {
                    burner.hurtServer(burner.level(), burner.damageSources().onFire(), 1.0F);
                }
                hive.setRemainingFireTicks(prevFire - 1);
            }
            data.setDirty();
        }

        // 3. Freezing (1x build-up in powder snow, 1x thaw outside)
        int prevFreeze = hive.getTicksFrozen();
        ServerPlayer freezer = null;
        for (ServerPlayer member : aliveMembers) {
            if (!member.isCreative()
                && !member.isSpectator()
                && !member.getAbilities().invulnerable
                && member.isInPowderSnow
                && member.canFreeze()) {
                freezer = member;
                break;
            }
        }
        if (freezer != null) {
            if (hive.tryClaimFreezeIncrement(gameTime)) {
                hive.setTicksFrozen(Math.min(freezer.getTicksRequiredToFreeze(), prevFreeze + 1));
            }
            if (gameTime % 40 == 0 && hive.getTicksFrozen() >= freezer.getTicksRequiredToFreeze()) {
                freezer.hurtServer(freezer.level(), freezer.damageSources().freeze(), 1.0F);
            }
        } else if (prevFreeze > 0) {
            hive.setTicksFrozen(Math.max(0, prevFreeze - 2));
        }
        if (hive.getTicksFrozen() != prevFreeze) {
            data.setDirty();
        }

        syncEnvironmentToMembers(hive, aliveMembers);
    }

    private static void syncEnvironmentToMembers(HiveMind hive, List<ServerPlayer> aliveMembers) {
        syncingEnvironment = true;
        try {
            for (ServerPlayer member : aliveMembers) {
                member.setAirSupply(Mth.clamp(hive.getAirSupply(), -20, member.getMaxAirSupply()));
                member.setRemainingFireTicks(hive.getRemainingFireTicks());
                member.setSharedFlagOnFire(hive.getRemainingFireTicks() > 0);
                member.setTicksFrozen(hive.getTicksFrozen());
            }
        } finally {
            syncingEnvironment = false;
        }
    }

    public static void onPlayerIgnited(Entity entity) {
        if (syncingEnvironment || !OIMRConfig.isFullAffinity() || !(entity instanceof ServerPlayer player) || !isAttached(player) || player.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        int newFireTicks = player.getRemainingFireTicks();
        if (newFireTicks > hive.getRemainingFireTicks()) {
            hive.setRemainingFireTicks(newFireTicks);
            hive.setTicksFrozen(0);
            HiveMindData.get(server).setDirty();
            syncEnvironmentToMembers(hive, getAliveMembers(server, hiveId));
        }
    }

    public static void onPlayerClearFire(Entity entity) {
        if (syncingEnvironment || !OIMRConfig.isFullAffinity() || !(entity instanceof ServerPlayer player) || !isAttached(player) || player.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null || hive.getRemainingFireTicks() <= 0) {
            return;
        }

        List<ServerPlayer> alive = getAliveMembers(server, hiveId);
        boolean inWater = player.isInWater();
        for (ServerPlayer other : alive) {
            if (other == player) {
                continue;
            }
            if (other.isInLava() || other.getInBlockState().is(BlockTags.FIRE)) {
                return;
            }
            if (!inWater && !other.isInWaterOrRain()) {
                return;
            }
        }

        hive.setRemainingFireTicks(0);
        HiveMindData.get(server).setDirty();
        syncEnvironmentToMembers(hive, alive);
    }

    public static void onPlayerTicksFrozenChanged(Entity entity, int ticks) {
        if (syncingEnvironment || !OIMRConfig.isFullAffinity() || !(entity instanceof ServerPlayer player) || !isAttached(player) || player.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        long gameTime = server.overworld().getGameTime();
        if (ticks == 0) {
            if (hive.getTicksFrozen() != 0) {
                hive.setTicksFrozen(0);
                HiveMindData.get(server).setDirty();
                syncEnvironmentToMembers(hive, getAliveMembers(server, hiveId));
            }
        } else if (ticks > hive.getTicksFrozen()) {
            if (hive.tryClaimFreezeIncrement(gameTime)) {
                int capped = Math.min(player.getTicksRequiredToFreeze(), hive.getTicksFrozen() + 1);
                hive.setTicksFrozen(capped);
                HiveMindData.get(server).setDirty();
                syncEnvironmentToMembers(hive, getAliveMembers(server, hiveId));
            } else {
                syncEnvironmentToMembers(hive, List.of(player));
            }
        }
    }

    public static void onPlayerAirSupplyChanged(Entity entity, int supply) {
        if (syncingEnvironment || !OIMRConfig.isFullAffinity() || !(entity instanceof ServerPlayer player) || !isAttached(player) || player.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null || hive.getAirSupply() == supply) {
            return;
        }

        hive.setAirSupply(Mth.clamp(supply, -20, player.getMaxAirSupply()));
        HiveMindData.get(server).setDirty();
        syncEnvironmentToMembers(hive, getAliveMembers(server, hiveId));
    }

    public static OptionalInt tryDecreaseAirSupplyWithGroupRespiration(LivingEntity entity, int currentSupply) {
        if (!(entity instanceof ServerPlayer player) || !isAttached(player)) {
            return OptionalInt.empty();
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return OptionalInt.empty();
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return OptionalInt.empty();
        }

        double ownBonus = Math.max(0.0D, player.getAttributeValue(Attributes.OXYGEN_BONUS));
        double maxOtherBonus = 0.0D;
        for (ServerPlayer member : getAliveMembers(server, hiveId)) {
            if (member != player && isVitalHazardTarget(member)) {
                double memberBonus = Math.max(0.0D, member.getAttributeValue(Attributes.OXYGEN_BONUS));
                if (memberBonus > maxOtherBonus) {
                    maxOtherBonus = memberBonus;
                }
            }
        }
        double effectiveBonus = Math.max(ownBonus, maxOtherBonus * OIMRConfig.getAffinityFactor());
        if (effectiveBonus > 0.0D && player.getRandom().nextDouble() >= 1.0D / (effectiveBonus + 1.0D)) {
            return OptionalInt.of(currentSupply);
        }
        return OptionalInt.of(currentSupply - 1);
    }

    public static void beginSplashPotionImpact() {
        beginPotionImpact(true);
    }

    public static void beginPotionImpact(boolean stackDurations) {
        splashImpact = new SplashImpactState(stackDurations);
    }

    public static boolean captureSplashPotionEffect(LivingEntity target, MobEffectInstance effect) {
        if (splashImpact == null
            || syncingEffects
            || !OIMRConfig.isFullAffinity()
            || !(target instanceof ServerPlayer player)
            || !isAttached(player)
            || player.isDeadOrDying()) {
            return false;
        }
        if (!player.canBeAffected(effect)) {
            return false;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return false;
        }
        splashImpact.record(hiveId, effect);
        return true;
    }

    public static boolean captureInstantaneousPotionEffect(
        MobEffect mobEffect,
        LivingEntity target,
        int amplifier,
        double healthFactor
    ) {
        if (splashImpact == null
            || syncingEffects
            || !OIMRConfig.isFullAffinity()
            || !(target instanceof ServerPlayer player)
            || !isAttached(player)
            || player.isDeadOrDying()) {
            return false;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return false;
        }
        splashImpact.recordInstantaneous(hiveId, mobEffect, player, amplifier, healthFactor);
        return true;
    }

    public static void endSplashPotionImpact(Entity potion, @Nullable Entity effectSource) {
        SplashImpactState state = splashImpact;
        splashImpact = null;
        if (state == null || state.isEmpty() || !(potion.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        HiveMindData data = HiveMindData.get(server);
        if (!state.entries.isEmpty()) {
            syncingEffects = true;
            try {
                for (SplashImpactAccumulator entry : state.entries.values()) {
                    HiveMind hive = data.getIfPresent(entry.hiveId);
                    if (hive == null) {
                        continue;
                    }
                    MobEffectInstance splashEffect = new MobEffectInstance(
                        entry.effect,
                        entry.totalDuration,
                        entry.amplifier,
                        entry.ambient,
                        entry.visible
                    );
                    MobEffectInstance existing = hive.getEffects().get(entry.effect);
                    MobEffectInstance resolved;
                    if (existing == null) {
                        resolved = splashEffect;
                    } else {
                        resolved = new MobEffectInstance(existing);
                        resolved.update(splashEffect);
                    }

                    hive.putEffect(resolved);
                    data.setDirty();

                    for (ServerPlayer member : getAliveMembers(server, entry.hiveId)) {
                        MobEffectInstance copy = new MobEffectInstance(resolved);
                        member.forceAddEffect(copy, effectSource);
                        copy.onEffectStarted(member);
                    }
                    if (MobEffects.HERO_OF_THE_VILLAGE.equals(entry.effect)) {
                        refreshOpenMerchantPricesForHive(server, entry.hiveId);
                    }
                }
            } finally {
                syncingEffects = false;
            }
        }
        for (InstantaneousImpactEntry entry : state.instantEntries.values()) {
            if (entry.target.isAlive() && isAttached(entry.target)) {
                entry.effect.applyInstantaneousEffect(
                    level,
                    potion,
                    effectSource,
                    entry.target,
                    entry.amplifier,
                    entry.healthFactor
                );
            }
        }
    }

    private static int getEffectDurationUnitTicks(Holder<MobEffect> effectHolder, int amplifier) {
        if (effectHolder.is(MobEffects.POISON)) {
            return Math.max(1, 25 >> amplifier);
        }
        if (effectHolder.is(MobEffects.WITHER)) {
            return Math.max(1, 40 >> amplifier);
        }
        if (effectHolder.is(MobEffects.REGENERATION)) {
            return Math.max(1, 50 >> amplifier);
        }
        if (effectHolder.is(MobEffects.DARKNESS)) {
            return 22;
        }
        if (effectHolder.is(MobEffects.NAUSEA)) {
            return 60;
        }
        return 1;
    }

    private static boolean isFoodSharedDirectEffect(Holder<MobEffect> effectHolder) {
        return effectHolder.is(MobEffects.SATURATION) || effectHolder.is(MobEffects.HUNGER);
    }

    private static boolean isOmenTriggerEffect(Holder<MobEffect> effectHolder) {
        return effectHolder.is(MobEffects.BAD_OMEN) || effectHolder.is(MobEffects.TRIAL_OMEN);
    }

    private static boolean isDeathTriggerEffect(Holder<MobEffect> effectHolder) {
        return effectHolder.is(MobEffects.WIND_CHARGED)
            || effectHolder.is(MobEffects.WEAVING)
            || effectHolder.is(MobEffects.OOZING);
    }

    private static boolean isEffectStrongerOrLonger(MobEffectInstance candidate, @Nullable MobEffectInstance current) {
        if (current == null) {
            return true;
        }
        if (candidate.getAmplifier() != current.getAmplifier()) {
            return candidate.getAmplifier() > current.getAmplifier();
        }
        if (candidate.isInfiniteDuration()) {
            return !current.isInfiniteDuration();
        }
        if (current.isInfiniteDuration()) {
            return false;
        }
        return candidate.getDuration() > current.getDuration();
    }

    public static void onEffectAddedOrUpdated(LivingEntity entity, MobEffectInstance effect, Entity source) {
        if (syncingEffects
            || (splashImpact != null && OIMRConfig.isFullAffinity())
            || !(entity instanceof ServerPlayer player)
            || !isAttached(player)
            || player.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        if (!OIMRConfig.isFullAffinity()) {
            Holder<MobEffect> effectHolder = effect.getEffect();
            if (effectHolder.is(MobEffects.INFESTED)) {
                hive.setInfestedChanceScale(player.getUUID(), 1.0D);
                HiveMindData.get(server).setDirty();
            }
            if (isFoodSharedDirectEffect(effectHolder) || effectHolder.is(MobEffects.RAID_OMEN)) {
                return;
            }

            double factor = OIMRConfig.getAffinityFactorDouble();
            if (factor <= 0.0D) {
                return;
            }
            int fullAmplifier = effect.getAmplifier();
            boolean infinite = effect.isInfiniteDuration();
            boolean omenTrigger = isOmenTriggerEffect(effectHolder);
            boolean deathTrigger = isDeathTriggerEffect(effectHolder);
            double scaledDuration = (infinite || omenTrigger || deathTrigger) ? 0.0D : (double) effect.getDuration() * factor;
            int unitTicks = (infinite || omenTrigger || deathTrigger) ? 1 : getEffectDurationUnitTicks(effectHolder, fullAmplifier);
            boolean dirty = false;

            syncingEffects = true;
            try {
                for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                    if (other == player) {
                        continue;
                    }
                    MobEffectInstance probe = new MobEffectInstance(
                        effectHolder,
                        (infinite || omenTrigger || deathTrigger) ? effect.getDuration() : unitTicks,
                        fullAmplifier,
                        effect.isAmbient(),
                        effect.isVisible(),
                        effect.showIcon()
                    );
                    if (!other.canBeAffected(probe)) {
                        continue;
                    }

                    MobEffectInstance current = other.getEffect(effectHolder);
                    if (omenTrigger || deathTrigger) {
                        double gain = omenTrigger ? (fullAmplifier + 1) * factor : factor;
                        double totalTrigger = hive.getEffectTriggerRemainder(other.getUUID(), effectHolder) + gain;
                        int grantedUnits = Mth.floor(totalTrigger + FRACTIONAL_EPSILON);
                        double nextTriggerRem = Math.max(0.0D, totalTrigger - grantedUnits);
                        hive.setEffectTriggerRemainder(other.getUUID(), effectHolder, nextTriggerRem);
                        dirty = true;
                        if (grantedUnits < 1) {
                            continue;
                        }
                        int targetAmp = omenTrigger ? (grantedUnits - 1) : fullAmplifier;
                        MobEffectInstance triggered = new MobEffectInstance(
                            effectHolder,
                            effect.getDuration(),
                            targetAmp,
                            effect.isAmbient(),
                            effect.isVisible(),
                            effect.showIcon()
                        );
                        if (isEffectStrongerOrLonger(triggered, current)) {
                            other.forceAddEffect(triggered, source);
                            triggered.onEffectStarted(other);
                        }
                        continue;
                    }

                    int targetDuration;
                    double nextDurationRem = hive.getEffectDurationRemainder(other.getUUID(), effectHolder);
                    if (infinite) {
                        targetDuration = effect.getDuration();
                    } else {
                        if (current != null && (current.isInfiniteDuration() || current.getAmplifier() > fullAmplifier)) {
                            continue;
                        }
                        double totalDuration = nextDurationRem + scaledDuration;
                        int units = Mth.floor((totalDuration + FRACTIONAL_EPSILON) / unitTicks);
                        targetDuration = units * unitTicks;
                        nextDurationRem = Math.max(0.0D, totalDuration - targetDuration);
                        hive.setEffectDurationRemainder(other.getUUID(), effectHolder, nextDurationRem);
                        dirty = true;
                    }

                    if (MobEffects.ABSORPTION.equals(effectHolder)) {
                        double totalAbsorb = hive.getAbsorptionRemainder(other.getUUID()) + ((fullAmplifier + 1) * 4.0D * factor);
                        if (totalAbsorb + FRACTIONAL_EPSILON < 1.0D || (!infinite && targetDuration < unitTicks)) {
                            hive.setAbsorptionRemainder(other.getUUID(), totalAbsorb);
                            if (!infinite && targetDuration >= unitTicks) {
                                // Keep duration accumulated while waiting for >= 1.0 absorption shield.
                                hive.setEffectDurationRemainder(other.getUUID(), effectHolder, nextDurationRem + targetDuration);
                            }
                            dirty = true;
                            continue;
                        }
                        float grantedAbsorption = (float) (totalAbsorb < 1.0D && totalAbsorb + FRACTIONAL_EPSILON >= 1.0D ? 1.0D : totalAbsorb);
                        hive.setAbsorptionRemainder(other.getUUID(), 0.0D);
                        dirty = true;
                        MobEffectInstance scaled = new MobEffectInstance(
                            effectHolder,
                            targetDuration,
                            fullAmplifier,
                            effect.isAmbient(),
                            effect.isVisible(),
                            effect.showIcon()
                        );
                        if (isEffectStrongerOrLonger(scaled, current)) {
                            float prevAbsorb = other.getAbsorptionAmount();
                            other.forceAddEffect(scaled, source);
                            scaled.onEffectStarted(other);
                            float targetAbsorb = Math.max(prevAbsorb, grantedAbsorption);
                            other.setAbsorptionAmount(Mth.clamp(targetAbsorb, 0.0F, other.getMaxAbsorption()));
                        }
                        continue;
                    }

                    if (MobEffects.HEALTH_BOOST.equals(effectHolder)) {
                        double totalBoost = hive.getHealthBoostRemainder(other.getUUID()) + ((fullAmplifier + 1) * 4.0D * factor);
                        int wholeBoost = Mth.floor(totalBoost + FRACTIONAL_EPSILON);
                        if (wholeBoost < 1 || (!infinite && targetDuration < unitTicks)) {
                            hive.setHealthBoostRemainder(other.getUUID(), totalBoost);
                            if (!infinite && targetDuration >= unitTicks) {
                                hive.setEffectDurationRemainder(other.getUUID(), effectHolder, nextDurationRem + targetDuration);
                            }
                            dirty = true;
                            continue;
                        }
                        double nextBoostRem = Math.max(0.0D, totalBoost - wholeBoost);
                        hive.setHealthBoostRemainder(other.getUUID(), nextBoostRem);
                        dirty = true;
                        MobEffectInstance scaled = new MobEffectInstance(
                            effectHolder,
                            targetDuration,
                            fullAmplifier,
                            effect.isAmbient(),
                            effect.isVisible(),
                            effect.showIcon()
                        );
                        if (isEffectStrongerOrLonger(scaled, current)) {
                            AttributeInstance maxHealthAttr = other.getAttribute(Attributes.MAX_HEALTH);
                            double prevBoostAmount = 0.0D;
                            if (current != null && maxHealthAttr != null) {
                                AttributeModifier existingMod = maxHealthAttr.getModifier(HEALTH_BOOST_MODIFIER_ID);
                                if (existingMod != null) {
                                    prevBoostAmount = existingMod.amount();
                                }
                            }
                            other.forceAddEffect(scaled, source);
                            scaled.onEffectStarted(other);
                            if (maxHealthAttr != null) {
                                double targetBoostAmount = Math.max(prevBoostAmount, wholeBoost);
                                maxHealthAttr.addOrUpdateTransientModifier(
                                    new AttributeModifier(HEALTH_BOOST_MODIFIER_ID, targetBoostAmount, AttributeModifier.Operation.ADD_VALUE)
                                );
                                if (other.getHealth() > other.getMaxHealth()) {
                                    other.setHealth(other.getMaxHealth());
                                }
                            }
                        }
                        continue;
                    }

                    if (!infinite && targetDuration < unitTicks) {
                        continue;
                    }

                    MobEffectInstance scaled = new MobEffectInstance(
                        effectHolder,
                        targetDuration,
                        fullAmplifier,
                        effect.isAmbient(),
                        effect.isVisible(),
                        effect.showIcon()
                    );
                    if (isEffectStrongerOrLonger(scaled, current)) {
                        other.forceAddEffect(scaled, source);
                        scaled.onEffectStarted(other);
                        if (MobEffects.INFESTED.equals(effectHolder)) {
                            double existingScale = current != null ? hive.getInfestedChanceScale(other.getUUID()) : 0.0D;
                            hive.setInfestedChanceScale(other.getUUID(), Math.max(existingScale, factor));
                            dirty = true;
                        }
                    }
                }
            } finally {
                syncingEffects = false;
            }
            if (dirty) {
                HiveMindData.get(server).setDirty();
            }
            if (MobEffects.HERO_OF_THE_VILLAGE.equals(effectHolder)) {
                refreshOpenMerchantPricesForHive(server, hiveId);
            }
            return;
        }

        MobEffectInstance existing = hive.getEffects().get(effect.getEffect());
        MobEffectInstance resolved = effect;
        boolean playerNeedsResync = false;
        if (existing != null && existing != effect) {
            MobEffectInstance merged = new MobEffectInstance(existing);
            boolean updated = merged.update(effect);
            resolved = merged;
            playerNeedsResync = !updated
                && (merged.getAmplifier() > effect.getAmplifier() || merged.getDuration() > effect.getDuration());
        }

        hive.putEffect(resolved);
        HiveMindData.get(server).setDirty();

        syncingEffects = true;
        try {
            for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                if (other == player && !playerNeedsResync) {
                    continue;
                }
                MobEffectInstance copy = new MobEffectInstance(resolved);
                other.forceAddEffect(copy, source);
                copy.onEffectStarted(other);
            }
        } finally {
            syncingEffects = false;
        }
        if (MobEffects.HERO_OF_THE_VILLAGE.equals(effect.getEffect())) {
            refreshOpenMerchantPricesForHive(server, hiveId);
        }
    }

    public static void onEffectsRemoved(LivingEntity entity, Collection<MobEffectInstance> removedEffects) {
        if (syncingEffects || !(entity instanceof ServerPlayer player) || !isAttached(player) || player.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        if (!OIMRConfig.isFullAffinity()) {
            for (MobEffectInstance removed : removedEffects) {
                if (MobEffects.INFESTED.equals(removed.getEffect())) {
                    hive.setInfestedChanceScale(player.getUUID(), 1.0D);
                    HiveMindData.get(server).setDirty();
                }
            }
            return;
        }

        boolean heroRemoved = false;
        for (MobEffectInstance removed : removedEffects) {
            if (MobEffects.HERO_OF_THE_VILLAGE.equals(removed.getEffect())) {
                heroRemoved = true;
            }
            hive.removeEffect(removed.getEffect());
        }
        HiveMindData.get(server).setDirty();

        syncingEffects = true;
        try {
            for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                if (other == player) {
                    continue;
                }
                for (MobEffectInstance removed : removedEffects) {
                    other.removeEffect(removed.getEffect());
                }
            }
        } finally {
            syncingEffects = false;
        }
        if (heroRemoved) {
            refreshOpenMerchantPricesForHive(server, hiveId);
        }
    }

    public static boolean shouldApplyPeriodicEffectTick(LivingEntity entity, Holder<MobEffect> effectHolder) {
        if (!(entity instanceof ServerPlayer player) || !isAttached(player) || player.isDeadOrDying()) {
            return true;
        }
        boolean alwaysDeduplicated = effectHolder.is(MobEffects.SATURATION);
        if (!alwaysDeduplicated && !OIMRConfig.isFullAffinity()) {
            return true;
        }
        if (!alwaysDeduplicated
            && !effectHolder.is(MobEffects.POISON)
            && !effectHolder.is(MobEffects.WITHER)
            && !effectHolder.is(MobEffects.REGENERATION)) {
            return true;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return true;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return true;
        }

        List<ServerPlayer> eligible = new ArrayList<>();
        for (ServerPlayer member : getAliveMembers(server, hiveId)) {
            if (isVitalHazardTarget(member) && member.hasEffect(effectHolder)) {
                eligible.add(member);
            }
        }
        if (eligible.isEmpty() || !isVitalHazardTarget(player)) {
            return false;
        }
        if (eligible.size() == 1) {
            return true;
        }
        return hive.isPrimaryEffectTicker(player, effectHolder, server.overworld().getGameTime(), eligible);
    }

    public static int beginPeriodicEffectTick(LivingEntity target, Holder<MobEffect> effectHolder) {
        if (OIMRConfig.isFullAffinity() || !isAttachedServerPlayer(target)) {
            return 0;
        }
        int flags = 0;
        if (!suppressAffinityVitalsCascade) {
            suppressAffinityVitalsCascade = true;
            flags |= 1;
        }
        if (effectHolder.is(MobEffects.HUNGER) && !applyingUnscaledHungerEffect) {
            applyingUnscaledHungerEffect = true;
            flags |= 2;
        }
        return flags;
    }

    public static void endPeriodicEffectTick(int flags) {
        if ((flags & 1) != 0) {
            suppressAffinityVitalsCascade = false;
        }
        if ((flags & 2) != 0) {
            applyingUnscaledHungerEffect = false;
        }
    }

    public static double getInfestedChanceScale(LivingEntity entity) {
        if (OIMRConfig.isFullAffinity() || !(entity instanceof ServerPlayer player) || !isAttached(player)) {
            return 1.0D;
        }
        HiveMind hive = getHiveForPlayer(player);
        return hive != null ? hive.getInfestedChanceScale(player.getUUID()) : 1.0D;
    }

    public static float scaleFoodExhaustion(ServerPlayer player, float rawAmount) {
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return rawAmount;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return rawAmount;
        }
        float weight = OIMRConfig.getHungerWeight();
        if (applyingUnscaledHungerEffect) {
            return weight * rawAmount;
        }
        int denominator = getHungerScaleDenominator(server, hiveId);
        return (weight * rawAmount) / (float) denominator;
    }

    public static boolean shouldTickFood(ServerPlayer player) {
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return true;
        }
        if (player.isDeadOrDying() || player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return true;
        }
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return true;
        }
        return hive.tryClaimFoodTick(server.overworld().getGameTime());
    }

    public static void onHealthChanged(LivingEntity entity) {
        if (syncingHealth || syncingAffinityDamage || cascadingDeath || !OIMRConfig.isFullAffinity() || !(entity instanceof ServerPlayer player) || !isAttached(player)) {
            return;
        }
        float newHealth = player.getHealth();
        if (newHealth <= 0.0F || player.isDeadOrDying()) {
            return;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null || Float.compare(hive.getHealth(), newHealth) == 0) {
            return;
        }

        hive.setHealth(newHealth);
        HiveMindData.get(server).setDirty();

        syncingHealth = true;
        try {
            for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                if (other == player) {
                    continue;
                }
                other.setHealth(Mth.clamp(newHealth, 0.01F, other.getMaxHealth()));
            }
        } finally {
            syncingHealth = false;
        }
    }

    public static void onAbsorptionChanged(LivingEntity entity) {
        if (syncingHealth || syncingAffinityDamage || cascadingDeath || !OIMRConfig.isFullAffinity() || !(entity instanceof ServerPlayer player) || !isAttached(player) || player.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        float newAbsorption = player.getAbsorptionAmount();
        if (Float.compare(hive.getAbsorption(), newAbsorption) == 0) {
            return;
        }

        hive.setAbsorption(newAbsorption);
        HiveMindData.get(server).setDirty();

        syncingHealth = true;
        try {
            for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                if (other == player) {
                    continue;
                }
                other.setAbsorptionAmount(Mth.clamp(newAbsorption, 0.0F, other.getMaxAbsorption()));
            }
        } finally {
            syncingHealth = false;
        }
    }

    public static void onPlayerHealed(LivingEntity entity, float healedAmount) {
        if (syncingHealth
            || syncingAffinityDamage
            || suppressAffinityVitalsCascade
            || cascadingDeath
            || OIMRConfig.isFullAffinity()
            || healedAmount <= 0.0F
            || !(entity instanceof ServerPlayer source)
            || !isAttached(source)
            || source.isDeadOrDying()) {
            return;
        }
        double factor = OIMRConfig.getAffinityFactorDouble();
        if (factor <= 0.0D) {
            return;
        }
        double sharedHeal = (double) healedAmount * factor;
        if (sharedHeal <= 0.0D) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(source.getUUID());
        if (hiveId == null) {
            return;
        }
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        boolean dirty = false;
        syncingHealth = true;
        try {
            for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                if (other == source || other.isCreative() || other.isSpectator() || other.getAbilities().invulnerable) {
                    continue;
                }
                double missingHealth = Math.max(0.0D, other.getMaxHealth() - other.getHealth());
                if (missingHealth <= 0.0D) {
                    if (hive.getHealRemainder(other.getUUID()) > 0.0D) {
                        hive.setHealRemainder(other.getUUID(), 0.0D);
                        dirty = true;
                    }
                    continue;
                }
                double totalHeal = hive.getHealRemainder(other.getUUID()) + sharedHeal;
                if (totalHeal + FRACTIONAL_EPSILON < 1.0D && totalHeal + FRACTIONAL_EPSILON < missingHealth) {
                    hive.setHealRemainder(other.getUUID(), totalHeal);
                    dirty = true;
                    continue;
                }
                float appliedHeal = (float) (totalHeal < 1.0D && totalHeal + FRACTIONAL_EPSILON >= 1.0D ? 1.0D : totalHeal);
                hive.setHealRemainder(other.getUUID(), 0.0D);
                dirty = true;
                float nextHealth = Math.min(other.getMaxHealth(), other.getHealth() + appliedHeal);
                if (nextHealth > other.getHealth()) {
                    other.setHealth(nextHealth);
                }
            }
        } finally {
            syncingHealth = false;
        }
        if (dirty) {
            HiveMindData.get(server).setDirty();
        }
    }

    public static void onAfterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
        if (cascadingDeath
            || syncingAffinityDamage
            || suppressAffinityVitalsCascade
            || !OIMRConfig.isFullAffinity()
            || !(entity instanceof ServerPlayer victim)
            || !isAttached(victim)) {
            return;
        }
        if (damageTaken <= 0.0F || victim.isDeadOrDying()) {
            return;
        }

        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(victim.getUUID());
        if (hiveId == null) {
            return;
        }

        for (ServerPlayer other : getAliveMembers(server, hiveId)) {
            if (other == victim || other.isSpectator() || other.isCreative()) {
                continue;
            }
            if (other.isSleeping()) {
                other.stopSleeping();
            }
            other.hurtDuration = 10;
            other.hurtTime = 10;
            other.level().broadcastDamageEvent(other, source);
            other.level().playSound(
                null,
                other.getX(),
                other.getY(),
                other.getZ(),
                source.type().effects().sound(),
                other.getSoundSource(),
                1.0F,
                1.0F
            );
        }
    }

    public static void onPlayerActuallyHurt(ServerPlayer victim, DamageSource source, float damageTaken) {
        if (cascadingDeath
            || syncingAffinityDamage
            || suppressAffinityVitalsCascade
            || OIMRConfig.isFullAffinity()
            || damageTaken <= 0.0F
            || !isAttached(victim)) {
            return;
        }
        if (source.is(DamageTypes.GENERIC_KILL) && damageTaken >= 10000.0F) {
            return;
        }
        double factor = OIMRConfig.getAffinityFactorDouble();
        if (factor <= 0.0D) {
            return;
        }
        double sharedDamage = (double) damageTaken * factor;
        if (sharedDamage <= 0.0D) {
            return;
        }

        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(victim.getUUID());
        if (hiveId == null) {
            return;
        }
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        boolean dirty = false;
        syncingAffinityDamage = true;
        try {
            for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                if (other == victim || other.isSpectator() || other.isCreative() || other.getAbilities().invulnerable) {
                    continue;
                }
                double totalDamage = hive.getDamageRemainder(other.getUUID()) + sharedDamage;
                recordPendingDamageSource(other.getUUID(), source, sharedDamage);
                double effectiveHp = other.getHealth() + other.getAbsorptionAmount();
                if (totalDamage + FRACTIONAL_EPSILON < 1.0D && totalDamage + FRACTIONAL_EPSILON < effectiveHp) {
                    hive.setDamageRemainder(other.getUUID(), totalDamage);
                    dirty = true;
                    continue;
                }

                float appliedDamage = (float) (totalDamage < 1.0D && totalDamage + FRACTIONAL_EPSILON >= 1.0D ? 1.0D : totalDamage);
                hive.setDamageRemainder(other.getUUID(), 0.0D);
                dirty = true;
                DamageSource resolvedSource = resolvePendingDamageSource(other.getUUID(), source);

                if (other.isSleeping()) {
                    other.stopSleeping();
                }
                other.hurtDuration = 10;
                other.hurtTime = 10;
                other.level().broadcastDamageEvent(other, resolvedSource);
                other.level().playSound(
                    null,
                    other.getX(),
                    other.getY(),
                    other.getZ(),
                    resolvedSource.type().effects().sound(),
                    other.getSoundSource(),
                    1.0F,
                    1.0F
                );

                float absorption = other.getAbsorptionAmount();
                float absorbed = Math.min(absorption, appliedDamage);
                if (absorbed > 0.0F) {
                    other.setAbsorptionAmount(absorption - absorbed);
                }
                float remainingDamage = appliedDamage - absorbed;
                if (remainingDamage > 0.0F) {
                    float nextHealth = Math.max(0.0F, other.getHealth() - remainingDamage);
                    if (nextHealth <= 0.0F) {
                        if (((HiveLivingEntityAccess) other).oimr$checkTotemDeathProtection(resolvedSource)) {
                            hive.clearPlayerVitalRemainders(other.getUUID());
                            continue;
                        }
                        hive.clearPlayerRemainders(other.getUUID());
                        Component deathMsg = buildAffinityDeathMessage(other, victim, resolvedSource);
                        PENDING_DEATH_MESSAGES.put(other.getUUID(), deathMsg);
                        try {
                            other.setHealth(0.0F);
                            other.die(resolvedSource);
                        } finally {
                            PENDING_DEATH_MESSAGES.remove(other.getUUID());
                        }
                    } else {
                        other.setHealth(nextHealth);
                    }
                }
            }
        } finally {
            syncingAffinityDamage = false;
        }
        if (dirty) {
            HiveMindData.get(server).setDirty();
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
        if (syncingKnockback
            || cascadingDeath
            || !OIMRConfig.isSharedKnockbackEnabled()
            || !(entity instanceof ServerPlayer victim)
            || !isAttached(victim)
            || victim.isDeadOrDying()) {
            return;
        }
        if (rawPower <= 0.0 || appliedXd * appliedXd + appliedZd * appliedZd <= 1.0E-12) {
            return;
        }

        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return;
        }

        String hiveId = PLAYER_HIVES.get(victim.getUUID());
        if (hiveId == null) {
            return;
        }

        ServerPlayer attacker = null;
        if (source != null && source.getEntity() instanceof ServerPlayer sourcePlayer
            && sourcePlayer != victim
            && isAttached(sourcePlayer)
            && hiveId.equals(PLAYER_HIVES.get(sourcePlayer.getUUID()))) {
            attacker = sourcePlayer;
        }

        float factor = OIMRConfig.getAffinityFactor();
        if (factor <= 0.0F) {
            return;
        }
        double scaledPower = rawPower * factor;

        syncingKnockback = true;
        try {
            for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                if (other == victim || other.isSpectator() || other.isCreative()) {
                    continue;
                }
                if (other == attacker) {
                    other.knockback(scaledPower, -appliedXd, -appliedZd, source, damage, comesFromEffect);
                } else {
                    other.knockback(scaledPower, appliedXd, appliedZd, source, damage, comesFromEffect);
                }
                other.syncVelocity = true;
            }
        } finally {
            syncingKnockback = false;
        }
    }

    public static void onExplosionKnockback(ServerLevel level, Map<Player, Vec3> hitPlayers) {
        if (syncingKnockback
            || cascadingDeath
            || !OIMRConfig.isSharedKnockbackEnabled()
            || hitPlayers.isEmpty()) {
            return;
        }

        MinecraftServer server = level.getServer();
        Map<String, Vec3> hiveKnockbacks = new HashMap<>();
        for (Map.Entry<Player, Vec3> entry : hitPlayers.entrySet()) {
            if (!(entry.getKey() instanceof ServerPlayer victim) || !isAttached(victim) || victim.isDeadOrDying()) {
                continue;
            }
            Vec3 knockback = entry.getValue();
            if (knockback.lengthSqr() <= 1.0E-12) {
                continue;
            }
            String hiveId = PLAYER_HIVES.get(victim.getUUID());
            if (hiveId != null) {
                hiveKnockbacks.putIfAbsent(hiveId, knockback);
            }
        }
        if (hiveKnockbacks.isEmpty()) {
            return;
        }

        float factor = OIMRConfig.getAffinityFactor();
        if (factor <= 0.0F) {
            return;
        }

        syncingKnockback = true;
        try {
            for (Map.Entry<String, Vec3> entry : hiveKnockbacks.entrySet()) {
                Vec3 knockback = entry.getValue().scale(factor);
                for (ServerPlayer other : getAliveMembers(server, entry.getKey())) {
                    if (hitPlayers.containsKey(other) || other.isSpectator() || other.isCreative()) {
                        continue;
                    }
                    other.pushFromExplosion(knockback);
                    other.syncVelocity = true;
                }
            }
        } finally {
            syncingKnockback = false;
        }
    }

    public static int scaleExperiencePoints(ServerPlayer player, int rawPoints) {
        if (syncingExperience || rawPoints <= 0 || !isAttached(player)) {
            return rawPoints;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return rawPoints;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return rawPoints;
        }
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return rawPoints;
        }
        float weight = OIMRConfig.getXpWeight();
        int denominator = getHungerScaleDenominator(server, hiveId);
        if (hive.getXpRemainder() <= 0.0D && Float.compare(weight, 1.0F) == 0 && denominator == 1) {
            return rawPoints;
        }
        double scaled = ((double) weight * (double) rawPoints) / (double) denominator;
        double total = hive.getXpRemainder() + scaled;
        int whole = Mth.floor(total + FRACTIONAL_EPSILON);
        hive.setXpRemainder(Math.max(0.0D, total - whole));
        HiveMindData.get(server).setDirty();
        return whole;
    }

    public static int scaleExperienceLevels(ServerPlayer player, int rawLevels) {
        if (syncingExperience || rawLevels <= 0 || !isAttached(player)) {
            return rawLevels;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return rawLevels;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return rawLevels;
        }
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return rawLevels;
        }
        float weight = OIMRConfig.getXpWeight();
        int denominator = getHungerScaleDenominator(server, hiveId);
        if (hive.getXpLevelRemainder() <= 0.0D && Float.compare(weight, 1.0F) == 0 && denominator == 1) {
            return rawLevels;
        }
        double scaled = ((double) weight * (double) rawLevels) / (double) denominator;
        double total = hive.getXpLevelRemainder() + scaled;
        int whole = Mth.floor(total + FRACTIONAL_EPSILON);
        hive.setXpLevelRemainder(Math.max(0.0D, total - whole));
        HiveMindData.get(server).setDirty();
        return whole;
    }

    public static void onExperienceChanged(ServerPlayer source) {
        if (syncingExperience || cascadingDeath || !isAttached(source) || source.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(source.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        hive.setExperience(source.experienceLevel, source.totalExperience, source.experienceProgress);
        HiveMindData.get(server).setDirty();

        syncingExperience = true;
        try {
            for (ServerPlayer other : getAliveMembers(server, hiveId)) {
                if (other == source) {
                    continue;
                }
                other.experienceLevel = source.experienceLevel;
                other.totalExperience = source.totalExperience;
                other.experienceProgress = source.experienceProgress;
                other.connection.send(new ClientboundSetExperiencePacket(
                    other.experienceProgress,
                    other.totalExperience,
                    other.experienceLevel
                ));
            }
        } finally {
            syncingExperience = false;
        }
    }

    public static int getEnchantmentSeed(ServerPlayer player) {
        HiveMind hive = getHiveForPlayer(player);
        return hive != null ? hive.getEnchantmentSeed() : 0;
    }

    public static void onEnchantmentPerformed(ServerPlayer source) {
        onExperienceChanged(source);
        if (!isAttached(source) || source.isDeadOrDying()) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(source.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        int nextSeed = hive.advanceEnchantmentSeed();
        HiveMindData.get(server).setDirty();

        for (ServerPlayer member : getOnlineMembers(server, hiveId)) {
            ((HivePlayerProgressAccess) member).oimr$setEnchantmentSeed(nextSeed);
            if (member.containerMenu instanceof HiveEnchantmentMenuAccess enchantMenu && !enchantMenu.oimr$isInClick()) {
                enchantMenu.oimr$refreshSeed(nextSeed);
            }
        }
    }

    public static void onRespawnPositionChanged(
        ServerPlayer source,
        ServerPlayer.@Nullable RespawnConfig respawnConfig,
        boolean showMessage
    ) {
        if (syncingRespawn || !isAttached(source)) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(source.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        hive.setRespawnConfig(respawnConfig);
        HiveMindData.get(server).setDirty();

        syncingRespawn = true;
        try {
            for (ServerPlayer other : getOnlineMembers(server, hiveId)) {
                if (other == source) {
                    continue;
                }
                other.setRespawnPosition(respawnConfig, showMessage);
            }
        } finally {
            syncingRespawn = false;
        }
    }

    public static void onAdvancementAwarded(ServerPlayer source, AdvancementHolder holder, String criterion) {
        if (syncingAdvancements || !isAttached(source)) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(source.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        hive.grantAdvancementCriterion(holder, criterion);
        HiveMindData.get(server).setDirty();

        syncingAdvancements = true;
        try {
            for (ServerPlayer other : getOnlineMembers(server, hiveId)) {
                if (other == source) {
                    continue;
                }
                other.getAdvancements().award(holder, criterion);
            }
        } finally {
            syncingAdvancements = false;
        }
    }

    public static void onAdvancementRevoked(ServerPlayer source, AdvancementHolder holder, String criterion) {
        if (syncingAdvancements || !isAttached(source)) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(playerHiveKey(source));
        if (hiveId == null) {
            return;
        }
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        hive.revokeAdvancementCriterion(holder, criterion);
        HiveMindData.get(server).setDirty();

        syncingAdvancements = true;
        try {
            for (ServerPlayer other : getOnlineMembers(server, hiveId)) {
                if (other == source) {
                    continue;
                }
                other.getAdvancements().revoke(holder, criterion);
            }
        } finally {
            syncingAdvancements = false;
        }
    }

    private static UUID playerHiveKey(ServerPlayer player) {
        return player.getUUID();
    }

    public static void onRecipesAdded(ServerPlayer source, Collection<RecipeHolder<?>> recipes) {
        if (syncingRecipes || !isAttached(source)) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(source.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        hive.addRecipes(recipes);
        HiveMindData.get(server).setDirty();

        syncingRecipes = true;
        try {
            for (ServerPlayer other : getOnlineMembers(server, hiveId)) {
                if (other == source) {
                    continue;
                }
                other.getRecipeBook().addRecipes(recipes, other);
            }
        } finally {
            syncingRecipes = false;
        }
    }

    public static void onRecipesRemoved(ServerPlayer source, Collection<RecipeHolder<?>> recipes) {
        if (syncingRecipes || !isAttached(source)) {
            return;
        }
        MinecraftServer server = source.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(source.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        hive.removeRecipes(recipes);
        HiveMindData.get(server).setDirty();

        syncingRecipes = true;
        try {
            for (ServerPlayer other : getOnlineMembers(server, hiveId)) {
                if (other == source) {
                    continue;
                }
                other.getRecipeBook().removeRecipes(recipes, other);
            }
        } finally {
            syncingRecipes = false;
        }
    }

    public static UUID getHiveReputationUuid(String hiveId) {
        return UUID.nameUUIDFromBytes(("oimr:hive_reputation:" + hiveId).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID resolveReputationTargetUuid(@Nullable Entity entity, UUID fallback) {
        if (entity instanceof ServerPlayer player && isAttached(player)) {
            String hiveId = PLAYER_HIVES.get(player.getUUID());
            if (hiveId != null) {
                return getHiveReputationUuid(hiveId);
            }
        }
        return fallback;
    }

    public static boolean areSameHive(@Nullable Entity first, @Nullable Entity second) {
        if (!(first instanceof ServerPlayer playerA) || !(second instanceof ServerPlayer playerB)) {
            return false;
        }
        if (!isAttached(playerA) || !isAttached(playerB)) {
            return false;
        }
        String hiveA = PLAYER_HIVES.get(playerA.getUUID());
        return hiveA != null && hiveA.equals(PLAYER_HIVES.get(playerB.getUUID()));
    }

    public static void applyOfflineZombieVillagerCure(MinecraftServer server, UUID starterUuid, Villager villager) {
        String hiveId = resolvePlayerHiveIdByUuid(server, starterUuid);
        if (hiveId == null) {
            return;
        }
        UUID hiveRepUuid = getHiveReputationUuid(hiveId);
        villager.getGossips().add(hiveRepUuid, GossipType.MAJOR_POSITIVE, 20);
        villager.getGossips().add(hiveRepUuid, GossipType.MINOR_POSITIVE, 25);
        if (villager.getTradingPlayer() instanceof ServerPlayer tradingPlayer
            && isAttached(tradingPlayer)
            && hiveId.equals(PLAYER_HIVES.get(tradingPlayer.getUUID()))) {
            ((HiveVillagerAccess) villager).oimr$updateSpecialPrices(tradingPlayer);
        }
    }

    private static void refreshOpenMerchantPricesForHive(MinecraftServer server, String hiveId) {
        for (ServerPlayer member : getOnlineMembers(server, hiveId)) {
            if (member.containerMenu instanceof MerchantMenu
                && member.containerMenu instanceof HiveMerchantMenuAccess merchantAccess
                && merchantAccess.oimr$getTrader() instanceof Villager villager
                && villager.getTradingPlayer() == member) {
                ((HiveVillagerAccess) villager).oimr$updateSpecialPrices(member);
            }
        }
    }

    public static void onBeforePlayerDie(ServerPlayer player, DamageSource source) {
        DYING_DAMAGE_SOURCES.put(player.getUUID(), source);
        if (!cascadingDeath
            && isAttached(player)
            && (player.hasContainerOpen()
                || !player.containerMenu.getCarried().isEmpty()
                || !player.inventoryMenu.getCraftSlots().isEmpty())) {
            player.doCloseContainer();
        }
    }

    public static void popDyingDamageSource(ServerPlayer player) {
        DYING_DAMAGE_SOURCES.remove(player.getUUID());
    }

    public static @Nullable DamageSource peekDyingDamageSource(LivingEntity entity) {
        return DYING_DAMAGE_SOURCES.get(entity.getUUID());
    }

    public static boolean shouldPreventPetAttack(LivingEntity attacker, @Nullable LivingEntity target) {
        if (!OIMRConfig.isFriendlyPetsEnabled() || target == null) {
            return false;
        }
        if (!(attacker.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        Set<String> petHiveIds = resolvePetHiveIds(server, attacker);
        if (petHiveIds.isEmpty()) {
            return false;
        }

        if (target instanceof ServerPlayer targetPlayer && isAttached(targetPlayer)) {
            String targetHiveId = PLAYER_HIVES.get(targetPlayer.getUUID());
            return targetHiveId != null && petHiveIds.contains(targetHiveId);
        }

        if (target instanceof OwnableEntity || target instanceof Fox) {
            Set<String> targetHiveIds = resolvePetHiveIds(server, target);
            for (String targetHiveId : targetHiveIds) {
                if (petHiveIds.contains(targetHiveId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> resolvePetHiveIds(MinecraftServer server, Entity entity) {
        if (entity instanceof OwnableEntity ownable) {
            EntityReference<LivingEntity> ownerRef = ownable.getOwnerReference();
            if (ownerRef == null) {
                return Set.of();
            }
            String hiveId = resolvePlayerHiveIdByUuid(server, ownerRef.getUUID());
            return hiveId != null ? Set.of(hiveId) : Set.of();
        }
        if (entity instanceof Fox && entity instanceof HiveFoxAccess foxAccess) {
            Set<String> result = new HashSet<>();
            foxAccess.oimr$getTrustedEntities().forEach(ref -> {
                String hiveId = resolvePlayerHiveIdByUuid(server, ref.getUUID());
                if (hiveId != null) {
                    result.add(hiveId);
                }
            });
            return result;
        }
        return Set.of();
    }

    private static @Nullable String resolvePlayerHiveIdByUuid(MinecraftServer server, UUID playerUuid) {
        String activeHive = PLAYER_HIVES.get(playerUuid);
        if (activeHive != null) {
            return activeHive;
        }
        String knownHive = KNOWN_PLAYER_HIVES.get(playerUuid);
        if (knownHive != null) {
            return knownHive;
        }
        Optional<NameAndId> cached = server.services().nameToIdCache().get(playerUuid);
        if (cached.isPresent()) {
            PlayerTeam team = server.getScoreboard().getPlayersTeam(cached.get().name());
            return team != null ? HiveMindData.TEAM_HIVE_PREFIX + team.getName() : HiveMindData.DEFAULT_HIVE_ID;
        }
        return null;
    }

    public static boolean shouldSuppressDeathInventoryDrop(Player player) {
        if (cascadingDeath || !(player instanceof ServerPlayer victim) || !isAttached(victim)) {
            return false;
        }
        if (!OIMRConfig.isExcludeCreativeFromDeathEnabled() || OIMRConfig.isWipeCreativeInventoryOnDeathEnabled()) {
            return false;
        }
        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return false;
        }
        String hiveId = PLAYER_HIVES.get(victim.getUUID());
        if (hiveId == null) {
            return false;
        }
        for (ServerPlayer member : getOnlineMembers(server, hiveId)) {
            if (member != victim && !member.isDeadOrDying() && !member.isSpectator() && member.isCreative()) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable Component consumePendingDeathMessage(LivingEntity entity) {
        Component message = PENDING_DEATH_MESSAGES.remove(entity.getUUID());
        if (message != null) {
            LAST_HIVE_DEATH_MESSAGES.put(entity.getUUID(), message);
        }
        return message;
    }

    public static @Nullable Component getLastHiveDeathMessage(UUID playerId) {
        return LAST_HIVE_DEATH_MESSAGES.get(playerId);
    }

    public static boolean consumeSuppressDeathChat(ServerPlayer player) {
        return SUPPRESS_DEATH_CHAT.remove(player.getUUID());
    }

    public static @Nullable Component createAndCaptureCollectiveStarveMessage(LivingEntity entity, @Nullable Entity secondary) {
        if (cascadingDeath || !(entity instanceof ServerPlayer victim) || !isAttached(victim)) {
            return null;
        }
        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return null;
        }
        String hiveId = PLAYER_HIVES.get(victim.getUUID());
        if (hiveId == null) {
            return null;
        }

        List<Component> names = new ArrayList<>();
        if (OIMRConfig.isFullAffinity()) {
            for (ServerPlayer member : getOnlineMembers(server, hiveId)) {
                if (member == victim
                    || (!member.isDeadOrDying()
                        && !member.isSpectator()
                        && !(member.isCreative() && OIMRConfig.isExcludeCreativeFromDeathEnabled()))) {
                    names.add(member.getDisplayName());
                }
            }
        }
        if (names.isEmpty()) {
            names.add(victim.getDisplayName());
        }

        String prefix = OIMRConfig.isComunismEnabled() ? "death.oimr.comunism." : "death.oimr.starve.";
        Component secondaryName = (secondary != null && secondary != victim) ? secondary.getDisplayName() : null;
        Component result;
        if (names.size() == 1) {
            result = secondaryName != null
                ? OIMRLang.translatable(prefix + "solo.player", names.get(0), secondaryName)
                : OIMRLang.translatable(prefix + "solo", names.get(0));
        } else {
            Component joined = formatPlayerNameList(names);
            result = secondaryName != null
                ? OIMRLang.translatable(prefix + "group.player", joined, secondaryName)
                : OIMRLang.translatable(prefix + "group", joined);
        }

        LAST_HIVE_DEATH_MESSAGES.put(victim.getUUID(), result);
        CAPTURED_DEATH_CONTEXTS.put(victim.getUUID(), new CapturedDeathContext(result, secondary, OIMRConfig.isFullAffinity()));
        return result;
    }

    private static Component formatPlayerNameList(List<Component> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return OIMRLang.translatable("death.oimr.list.two", names.get(0), names.get(1));
        }
        Component acc = names.get(0);
        for (int i = 1; i < names.size() - 1; i++) {
            acc = OIMRLang.translatable("death.oimr.list.sep", acc, names.get(i));
        }
        return OIMRLang.translatable("death.oimr.list.end", acc, names.get(names.size() - 1));
    }

    private static Component buildAffinityDeathMessage(ServerPlayer other, ServerPlayer victim, DamageSource source) {
        Component otherName = other.getDisplayName();
        Component victimName = victim.getDisplayName();
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != victim && attacker != other) {
            return OIMRLang.translatable("death.oimr.affinity.player", otherName, victimName, attacker.getDisplayName());
        }
        return OIMRLang.translatable("death.oimr.affinity", otherName, victimName);
    }

    public static void captureOriginalDeathContext(LivingEntity entity, Component deathMessage, @Nullable Entity secondaryEntity) {
        if (cascadingDeath || !(entity instanceof ServerPlayer victim) || !isAttached(victim)) {
            return;
        }
        CAPTURED_DEATH_CONTEXTS.put(victim.getUUID(), new CapturedDeathContext(deathMessage, secondaryEntity, false));
    }

    private static Component buildTeammateDeathMessage(
        ServerPlayer other,
        ServerPlayer victim,
        String hiveId,
        DamageSource source,
        @Nullable CapturedDeathContext captured
    ) {
        Component otherName = other.getDisplayName();
        Component victimName = victim.getDisplayName();

        Entity directSource = source.getEntity();
        Entity secondary = directSource != null && directSource != victim
            ? directSource
            : (captured != null && captured.secondaryEntity() != victim ? captured.secondaryEntity() : victim.getKillCredit());

        ServerPlayer teammateAttacker = null;
        boolean directTeammateAttack = false;
        if (directSource instanceof ServerPlayer sp
            && sp != victim
            && isAttached(sp)
            && hiveId.equals(PLAYER_HIVES.get(sp.getUUID()))) {
            teammateAttacker = sp;
            directTeammateAttack = true;
        } else if (secondary instanceof ServerPlayer sp
            && sp != victim
            && isAttached(sp)
            && hiveId.equals(PLAYER_HIVES.get(sp.getUUID()))) {
            teammateAttacker = sp;
        }

        if (teammateAttacker != null) {
            ItemStack weapon = ItemStack.EMPTY;
            if (directTeammateAttack) {
                ItemStack sourceWeapon = source.getWeaponItem();
                if (sourceWeapon != null && !sourceWeapon.isEmpty()) {
                    weapon = sourceWeapon;
                } else if (source.getDirectEntity() == teammateAttacker) {
                    weapon = teammateAttacker.getMainHandItem();
                }
            }
            boolean hasCustomWeapon = !weapon.isEmpty() && weapon.has(DataComponents.CUSTOM_NAME);
            if (other == teammateAttacker) {
                if (directTeammateAttack) {
                    return hasCustomWeapon
                        ? OIMRLang.translatable("death.oimr.friendly_fire.self.item", otherName, victimName, weapon.getDisplayName())
                        : OIMRLang.translatable("death.oimr.friendly_fire.self", otherName, victimName);
                }
                return OIMRLang.translatable("death.oimr.friendly_fire.self_indirect", otherName, victimName);
            }
            return hasCustomWeapon
                ? OIMRLang.translatable("death.oimr.friendly_fire.other.item", otherName, teammateAttacker.getDisplayName(), victimName, weapon.getDisplayName())
                : OIMRLang.translatable("death.oimr.friendly_fire.other", otherName, teammateAttacker.getDisplayName(), victimName);
        }

        Component secondaryName = (secondary != null && secondary != victim && secondary != other)
            ? secondary.getDisplayName()
            : null;

        if (source.is(DamageTypes.STARVE)) {
            if (captured != null && captured.collectiveStarve()) {
                return captured.deathMessage();
            }
            String prefix = OIMRConfig.isComunismEnabled() ? "death.oimr.comunism." : "death.oimr.starve.";
            Component joined = formatPlayerNameList(List.of(otherName, victimName));
            return secondaryName != null
                ? OIMRLang.translatable(prefix + "group.player", joined, secondaryName)
                : OIMRLang.translatable(prefix + "group", joined);
        }

        ResourceKey<DamageType> damageTypeKey = source.typeHolder().unwrapKey().orElse(null);
        String mappedKey = damageTypeKey != null ? SIMPLE_DEATH_MESSAGE_KEYS.get(damageTypeKey) : null;
        if (mappedKey != null) {
            return secondaryName != null
                ? OIMRLang.translatable(mappedKey + ".player", otherName, victimName, secondaryName)
                : OIMRLang.translatable(mappedKey, otherName, victimName);
        }

        Component victimMessage = captured != null ? captured.deathMessage() : source.getLocalizedDeathMessage(victim);
        return OIMRLang.translatable("death.oimr.cascade", otherName, victimMessage);
    }

    public static void onPlayerDeath(LivingEntity entity, DamageSource source) {
        if (cascadingDeath || !(entity instanceof ServerPlayer victim) || !isAttached(victim)) {
            return;
        }
        CapturedDeathContext captured = CAPTURED_DEATH_CONTEXTS.remove(victim.getUUID());
        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return;
        }
        boolean keepInventory = victim.level().getGameRules().get(GameRules.KEEP_INVENTORY);
        boolean preserveForCreative = !keepInventory && shouldSuppressDeathInventoryDrop(victim);
        String hiveId = PLAYER_HIVES.get(victim.getUUID());
        HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);

        if (!OIMRConfig.isFullAffinity()) {
            PENDING_DAMAGE_SOURCES.remove(victim.getUUID());
            if (hive != null) {
                hive.clearPlayerRemainders(victim.getUUID());
                if (!keepInventory && !preserveForCreative) {
                    hive.resetExperience();
                }
            }
            if (hive != null && !keepInventory && !preserveForCreative) {
                syncingExperience = true;
                try {
                    for (ServerPlayer member : getOnlineMembers(server, hiveId)) {
                        if (member == victim || member.isDeadOrDying()) {
                            continue;
                        }
                        member.experienceLevel = 0;
                        member.totalExperience = 0;
                        member.experienceProgress = 0.0F;
                        member.connection.send(new ClientboundSetExperiencePacket(0.0F, 0, 0));
                        member.inventoryMenu.sendAllDataToRemote();
                        ((HiveLivingEntityAccess) member).oimr$detectEquipmentUpdates();
                    }
                } finally {
                    syncingExperience = false;
                }
            } else {
                broadcastInventoryChanges(victim);
            }
            HiveMindData.get(server).setDirty();
            return;
        }

        if (hive != null) {
            hive.setHealth(0.0F);
            hive.setAbsorption(0.0F);
            hive.setAirSupply(HiveMind.DEFAULT_AIR_SUPPLY);
            hive.setRemainingFireTicks(0);
            hive.setTicksFrozen(0);
            hive.getEffects().clear();
            if (!keepInventory && !preserveForCreative) {
                hive.resetExperience();
            }
            HiveMindData.get(server).setDirty();
        }

        cascadingDeath = true;
        try {
            for (ServerPlayer other : getOnlineMembers(server, hiveId)) {
                if (other == victim || other.isDeadOrDying() || other.isSpectator()) {
                    continue;
                }
                if (other.isCreative() && OIMRConfig.isExcludeCreativeFromDeathEnabled()) {
                    if (!keepInventory && OIMRConfig.isWipeCreativeInventoryOnDeathEnabled()) {
                        other.experienceLevel = 0;
                        other.totalExperience = 0;
                        other.experienceProgress = 0.0F;
                        other.connection.send(new ClientboundSetExperiencePacket(0.0F, 0, 0));
                        other.inventoryMenu.sendAllDataToRemote();
                        ((HiveLivingEntityAccess) other).oimr$detectEquipmentUpdates();
                    }
                    continue;
                }
                if (!keepInventory) {
                    other.experienceLevel = 0;
                    other.totalExperience = 0;
                    other.experienceProgress = 0.0F;
                }
                other.doCloseContainer();
                boolean suppressChat = captured != null && captured.collectiveStarve();
                Component customDeathMessage = buildTeammateDeathMessage(other, victim, hiveId, source, captured);
                PENDING_DEATH_MESSAGES.put(other.getUUID(), customDeathMessage);
                if (suppressChat) {
                    SUPPRESS_DEATH_CHAT.add(other.getUUID());
                }
                try {
                    other.kill(other.level());
                    if (!other.isDeadOrDying()) {
                        other.setHealth(0.0F);
                        other.die(other.level().damageSources().genericKill());
                    }
                } finally {
                    PENDING_DEATH_MESSAGES.remove(other.getUUID());
                    SUPPRESS_DEATH_CHAT.remove(other.getUUID());
                }
            }
        } finally {
            cascadingDeath = false;
        }
    }

    public static void onTotemActivated(LivingEntity entity) {
        if (handlingGroupTotem || !(entity instanceof ServerPlayer player) || !isAttached(player)) {
            return;
        }
        broadcastInventoryChanges(player);
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String hiveId = PLAYER_HIVES.get(player.getUUID());
        if (hiveId == null) {
            return;
        }
        if (!OIMRConfig.isFullAffinity()) {
            PENDING_DAMAGE_SOURCES.remove(player.getUUID());
            HiveMind hive = HiveMindData.get(server).getIfPresent(hiveId);
            if (hive != null) {
                hive.clearPlayerVitalRemainders(player.getUUID());
                HiveMindData.get(server).setDirty();
            }
            return;
        }
        for (ServerPlayer member : getAliveMembers(server, hiveId)) {
            if (member != player && !member.isSpectator()) {
                member.level().broadcastEntityEvent(member, (byte) 35);
            }
        }
    }

    public static boolean tryActivateGroupTotem(LivingEntity entity, DamageSource source) {
        if (handlingGroupTotem
            || !OIMRConfig.isFullAffinity()
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || !(entity instanceof ServerPlayer victim)
            || !isAttached(victim)) {
            return false;
        }
        MinecraftServer server = victim.level().getServer();
        if (server == null) {
            return false;
        }
        String hiveId = PLAYER_HIVES.get(victim.getUUID());
        if (hiveId == null) {
            return false;
        }

        List<ServerPlayer> aliveMembers = getAliveMembers(server, hiveId);
        ServerPlayer totemHolder = null;
        ItemStack totemStack = ItemStack.EMPTY;
        DeathProtection protection = null;

        for (ServerPlayer candidate : aliveMembers) {
            if (candidate == victim || candidate.isSpectator()) {
                continue;
            }
            if (candidate.isCreative() && OIMRConfig.isExcludeCreativeFromDeathEnabled()) {
                continue;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = candidate.getItemInHand(hand);
                DeathProtection candidateProtection = stack.get(DataComponents.DEATH_PROTECTION);
                if (candidateProtection != null) {
                    totemHolder = candidate;
                    totemStack = stack;
                    protection = candidateProtection;
                    break;
                }
            }
            if (protection != null) {
                break;
            }
        }
        if (protection == null || totemHolder == null) {
            return false;
        }

        ItemStack totemCopy = totemStack.copy();
        totemStack.shrink(1);
        totemHolder.awardStat(Stats.ITEM_USED.get(totemCopy.getItem()));
        CriteriaTriggers.USED_TOTEM.trigger(totemHolder, totemCopy);
        totemCopy.causeUseVibration(totemHolder, GameEvent.ITEM_INTERACT_FINISH);

        handlingGroupTotem = true;
        try {
            victim.setHealth(1.0F);
            protection.applyEffects(totemCopy, victim);
            for (ServerPlayer member : aliveMembers) {
                if (!member.isSpectator()) {
                    member.level().broadcastEntityEvent(member, (byte) 35);
                }
            }
            broadcastInventoryChanges(victim);
        } finally {
            handlingGroupTotem = false;
        }
        return true;
    }

    private static void recordPendingDamageSource(UUID recipientUuid, DamageSource source, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        List<PendingDamageContribution> list = PENDING_DAMAGE_SOURCES.computeIfAbsent(recipientUuid, ignored -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            PendingDamageContribution entry = list.get(i);
            if (sameDamageOrigin(entry.source(), source)) {
                list.set(i, new PendingDamageContribution(source, entry.amount() + amount));
                return;
            }
        }
        list.add(new PendingDamageContribution(source, amount));
    }

    private static boolean sameDamageOrigin(DamageSource a, DamageSource b) {
        return Objects.equals(a.typeHolder(), b.typeHolder())
            && a.getEntity() == b.getEntity()
            && a.getDirectEntity() == b.getDirectEntity();
    }

    private static DamageSource resolvePendingDamageSource(UUID recipientUuid, DamageSource fallback) {
        List<PendingDamageContribution> list = PENDING_DAMAGE_SOURCES.remove(recipientUuid);
        if (list == null || list.isEmpty()) {
            return fallback;
        }
        PendingDamageContribution best = list.getLast();
        for (PendingDamageContribution entry : list) {
            if (entry.amount() > best.amount() + FRACTIONAL_EPSILON) {
                best = entry;
            } else if (Math.abs(entry.amount() - best.amount()) <= FRACTIONAL_EPSILON
                && entry.source().getEntity() != null
                && best.source().getEntity() == null) {
                best = entry;
            }
        }
        return best.source();
    }

    private record PendingDamageContribution(DamageSource source, double amount) {
    }

    private record CapturedDeathContext(Component deathMessage, @Nullable Entity secondaryEntity, boolean collectiveStarve) {
    }

    private record SplashEffectKey(String hiveId, Holder<MobEffect> effect) {
    }

    private record InstantaneousEffectKey(String hiveId, MobEffect effect) {
    }

    private static final class InstantaneousImpactEntry {
        private final MobEffect effect;
        private ServerPlayer target;
        private int amplifier;
        private double healthFactor;

        private InstantaneousImpactEntry(MobEffect effect, ServerPlayer target, int amplifier, double healthFactor) {
            this.effect = effect;
            this.target = target;
            this.amplifier = amplifier;
            this.healthFactor = healthFactor;
        }

        private void update(ServerPlayer candidateTarget, int candidateAmplifier, double candidateHealthFactor) {
            if (candidateAmplifier > this.amplifier
                || (candidateAmplifier == this.amplifier && candidateHealthFactor > this.healthFactor)) {
                this.target = candidateTarget;
                this.amplifier = candidateAmplifier;
                this.healthFactor = candidateHealthFactor;
            }
        }
    }

    private static final class SplashImpactAccumulator {
        private final String hiveId;
        private final Holder<MobEffect> effect;
        private final boolean stackDurations;
        private int totalDuration;
        private int amplifier;
        private boolean ambient;
        private boolean visible;

        private SplashImpactAccumulator(String hiveId, MobEffectInstance first, boolean stackDurations) {
            this.hiveId = hiveId;
            this.effect = first.getEffect();
            this.stackDurations = stackDurations;
            this.totalDuration = first.getDuration();
            this.amplifier = first.getAmplifier();
            this.ambient = first.isAmbient();
            this.visible = first.isVisible();
        }

        private void add(MobEffectInstance hitEffect) {
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

    private static final class SplashImpactState {
        private final boolean stackDurations;
        private final Map<SplashEffectKey, SplashImpactAccumulator> entries = new LinkedHashMap<>();
        private final Map<InstantaneousEffectKey, InstantaneousImpactEntry> instantEntries = new LinkedHashMap<>();

        private SplashImpactState(boolean stackDurations) {
            this.stackDurations = stackDurations;
        }

        private void record(String hiveId, MobEffectInstance hitEffect) {
            SplashEffectKey key = new SplashEffectKey(hiveId, hitEffect.getEffect());
            SplashImpactAccumulator existing = this.entries.get(key);
            if (existing == null) {
                this.entries.put(key, new SplashImpactAccumulator(hiveId, hitEffect, this.stackDurations));
            } else {
                existing.add(hitEffect);
            }
        }

        private void recordInstantaneous(
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

        private boolean isEmpty() {
            return this.entries.isEmpty() && this.instantEntries.isEmpty();
        }
    }
}
