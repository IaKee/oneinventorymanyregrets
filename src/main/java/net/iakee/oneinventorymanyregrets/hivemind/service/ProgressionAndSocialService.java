package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveEnchantmentMenuAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveFoxAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMerchantMenuAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.hivemind.HivePlayerProgressAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveVillagerAccess;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronizes XP, enchantment seed, respawn points, advancements, recipes,
 * villager reputation/trading prices, and friendly pet targeting.
 */
public final class ProgressionAndSocialService {
    private final HiveMindContext ctx;

    public ProgressionAndSocialService(HiveMindContext ctx) {
        this.ctx = ctx;
    }

    public int scaleExperiencePoints(ServerPlayer player, int rawPoints) {
        if (this.ctx.isSyncingExperience() || rawPoints <= 0 || !this.ctx.isAttached(player)) {
            return rawPoints;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return rawPoints;
        }
        HiveMind hive = this.ctx.data().getIfPresent(hiveId);
        if (hive == null) {
            return rawPoints;
        }
        float weight = OIMRConfig.getXpWeight();
        int denominator = this.ctx.getHungerScaleDenominator(hiveId);
        if (hive.getXpRemainder() <= 0.0D && Float.compare(weight, 1.0F) == 0 && denominator == 1) {
            return rawPoints;
        }
        double scaled = ((double) weight * (double) rawPoints) / (double) denominator;
        double total = hive.getXpRemainder() + scaled;
        int whole = Mth.floor(total + HiveMindContext.FRACTIONAL_EPSILON);
        hive.setXpRemainder(Math.max(0.0D, total - whole));
        this.ctx.data().setDirty();
        return whole;
    }

    public int scaleExperienceLevels(ServerPlayer player, int rawLevels) {
        if (this.ctx.isSyncingExperience() || rawLevels <= 0 || !this.ctx.isAttached(player)) {
            return rawLevels;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return rawLevels;
        }
        HiveMind hive = this.ctx.data().getIfPresent(hiveId);
        if (hive == null) {
            return rawLevels;
        }
        float weight = OIMRConfig.getXpWeight();
        int denominator = this.ctx.getHungerScaleDenominator(hiveId);
        if (hive.getXpLevelRemainder() <= 0.0D && Float.compare(weight, 1.0F) == 0 && denominator == 1) {
            return rawLevels;
        }
        double scaled = ((double) weight * (double) rawLevels) / (double) denominator;
        double total = hive.getXpLevelRemainder() + scaled;
        int whole = Mth.floor(total + HiveMindContext.FRACTIONAL_EPSILON);
        hive.setXpLevelRemainder(Math.max(0.0D, total - whole));
        this.ctx.data().setDirty();
        return whole;
    }

    public void onExperienceChanged(ServerPlayer source) {
        if (this.ctx.isSyncingExperience()
            || this.ctx.isCascadingDeath()
            || !this.ctx.isAttached(source)
            || source.isDeadOrDying()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        hive.setExperience(source.experienceLevel, source.totalExperience, source.experienceProgress);
        this.ctx.data().setDirty();

        this.ctx.setSyncingExperience(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
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
            this.ctx.setSyncingExperience(false);
        }
    }

    public int getEnchantmentSeed(ServerPlayer player) {
        HiveMind hive = this.ctx.getHiveForPlayer(player);
        return hive != null ? hive.getEnchantmentSeed() : 0;
    }

    public void onEnchantmentPerformed(ServerPlayer source) {
        onExperienceChanged(source);
        if (!this.ctx.isAttached(source) || source.isDeadOrDying()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        int nextSeed = hive.advanceEnchantmentSeed();
        this.ctx.data().setDirty();

        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            ((HivePlayerProgressAccess) member).oimr$setEnchantmentSeed(nextSeed);
            if (member.containerMenu instanceof HiveEnchantmentMenuAccess enchantMenu && !enchantMenu.oimr$isInClick()) {
                enchantMenu.oimr$refreshSeed(nextSeed);
            }
        }
    }

    public void onRespawnPositionChanged(
        ServerPlayer source,
        ServerPlayer.@Nullable RespawnConfig respawnConfig,
        boolean showMessage
    ) {
        if (this.ctx.isSyncingRespawn() || !this.ctx.isAttached(source)) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        hive.setRespawnConfig(respawnConfig);
        this.ctx.data().setDirty();

        this.ctx.setSyncingRespawn(true);
        try {
            for (ServerPlayer other : this.ctx.getOnlineMembers(hiveId)) {
                if (other == source) {
                    continue;
                }
                other.setRespawnPosition(respawnConfig, showMessage);
            }
        } finally {
            this.ctx.setSyncingRespawn(false);
        }
    }

    public void onAdvancementAwarded(ServerPlayer source, AdvancementHolder holder, String criterion) {
        if (this.ctx.isSyncingAdvancements() || !this.ctx.isAttached(source)) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        hive.grantAdvancementCriterion(holder, criterion);
        this.ctx.data().setDirty();

        this.ctx.setSyncingAdvancements(true);
        try {
            for (ServerPlayer other : this.ctx.getOnlineMembers(hiveId)) {
                if (other == source) {
                    continue;
                }
                other.getAdvancements().award(holder, criterion);
            }
        } finally {
            this.ctx.setSyncingAdvancements(false);
        }
    }

    public void onAdvancementRevoked(ServerPlayer source, AdvancementHolder holder, String criterion) {
        if (this.ctx.isSyncingAdvancements() || !this.ctx.isAttached(source)) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        hive.revokeAdvancementCriterion(holder, criterion);
        this.ctx.data().setDirty();

        this.ctx.setSyncingAdvancements(true);
        try {
            for (ServerPlayer other : this.ctx.getOnlineMembers(hiveId)) {
                if (other == source) {
                    continue;
                }
                other.getAdvancements().revoke(holder, criterion);
            }
        } finally {
            this.ctx.setSyncingAdvancements(false);
        }
    }

    public void onRecipesAdded(ServerPlayer source, Collection<RecipeHolder<?>> recipes) {
        if (this.ctx.isSyncingRecipes() || !this.ctx.isAttached(source)) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        hive.addRecipes(recipes);
        this.ctx.data().setDirty();

        this.ctx.setSyncingRecipes(true);
        try {
            for (ServerPlayer other : this.ctx.getOnlineMembers(hiveId)) {
                if (other == source) {
                    continue;
                }
                other.getRecipeBook().addRecipes(recipes, other);
            }
        } finally {
            this.ctx.setSyncingRecipes(false);
        }
    }

    public void onRecipesRemoved(ServerPlayer source, Collection<RecipeHolder<?>> recipes) {
        if (this.ctx.isSyncingRecipes() || !this.ctx.isAttached(source)) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        hive.removeRecipes(recipes);
        this.ctx.data().setDirty();

        this.ctx.setSyncingRecipes(true);
        try {
            for (ServerPlayer other : this.ctx.getOnlineMembers(hiveId)) {
                if (other == source) {
                    continue;
                }
                other.getRecipeBook().removeRecipes(recipes, other);
            }
        } finally {
            this.ctx.setSyncingRecipes(false);
        }
    }

    public static UUID getHiveReputationUuid(String hiveId) {
        return UUID.nameUUIDFromBytes(("oimr:hive_reputation:" + hiveId).getBytes(StandardCharsets.UTF_8));
    }

    public UUID resolveReputationTargetUuid(@Nullable Entity entity, UUID fallback) {
        if (entity instanceof ServerPlayer player && this.ctx.isAttached(player)) {
            String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
            if (hiveId != null) {
                return getHiveReputationUuid(hiveId);
            }
        }
        return fallback;
    }

    public boolean areSameHive(@Nullable Entity first, @Nullable Entity second) {
        if (!(first instanceof ServerPlayer playerA) || !(second instanceof ServerPlayer playerB)) {
            return false;
        }
        if (!this.ctx.isAttached(playerA) || !this.ctx.isAttached(playerB)) {
            return false;
        }
        String hiveA = this.ctx.getAttachedHiveId(playerA.getUUID());
        return hiveA != null && hiveA.equals(this.ctx.getAttachedHiveId(playerB.getUUID()));
    }

    public void applyOfflineZombieVillagerCure(UUID starterUuid, Villager villager) {
        String hiveId = resolvePlayerHiveIdByUuid(starterUuid);
        if (hiveId == null) {
            return;
        }
        UUID hiveRepUuid = getHiveReputationUuid(hiveId);
        villager.getGossips().add(hiveRepUuid, GossipType.MAJOR_POSITIVE, 20);
        villager.getGossips().add(hiveRepUuid, GossipType.MINOR_POSITIVE, 25);
        if (villager.getTradingPlayer() instanceof ServerPlayer tradingPlayer
            && this.ctx.isAttached(tradingPlayer)
            && hiveId.equals(this.ctx.getAttachedHiveId(tradingPlayer.getUUID()))) {
            ((HiveVillagerAccess) villager).oimr$updateSpecialPrices(tradingPlayer);
        }
    }

    public void refreshOpenMerchantPricesForHive(String hiveId) {
        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            if (member.containerMenu instanceof MerchantMenu
                && member.containerMenu instanceof HiveMerchantMenuAccess merchantAccess
                && merchantAccess.oimr$getTrader() instanceof Villager villager
                && villager.getTradingPlayer() == member) {
                ((HiveVillagerAccess) villager).oimr$updateSpecialPrices(member);
            }
        }
    }

    public boolean shouldPreventPetAttack(LivingEntity attacker, @Nullable LivingEntity target) {
        if (!OIMRConfig.isFriendlyPetsEnabled() || target == null) {
            return false;
        }
        Set<String> petHiveIds = resolvePetHiveIds(attacker);
        if (petHiveIds.isEmpty()) {
            return false;
        }

        if (target instanceof ServerPlayer targetPlayer && this.ctx.isAttached(targetPlayer)) {
            String targetHiveId = this.ctx.getAttachedHiveId(targetPlayer.getUUID());
            return targetHiveId != null && petHiveIds.contains(targetHiveId);
        }

        if (target instanceof OwnableEntity || target instanceof Fox) {
            Set<String> targetHiveIds = resolvePetHiveIds(target);
            for (String targetHiveId : targetHiveIds) {
                if (petHiveIds.contains(targetHiveId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> resolvePetHiveIds(Entity entity) {
        if (entity instanceof OwnableEntity ownable) {
            EntityReference<LivingEntity> ownerRef = ownable.getOwnerReference();
            if (ownerRef == null) {
                return Set.of();
            }
            String hiveId = resolvePlayerHiveIdByUuid(ownerRef.getUUID());
            return hiveId != null ? Set.of(hiveId) : Set.of();
        }
        if (entity instanceof Fox && entity instanceof HiveFoxAccess foxAccess) {
            Set<String> result = new HashSet<>();
            foxAccess.oimr$getTrustedEntities().forEach(ref -> {
                String hiveId = resolvePlayerHiveIdByUuid(ref.getUUID());
                if (hiveId != null) {
                    result.add(hiveId);
                }
            });
            return result;
        }
        return Set.of();
    }

    private @Nullable String resolvePlayerHiveIdByUuid(UUID playerUuid) {
        String activeHive = this.ctx.getAttachedHiveId(playerUuid);
        if (activeHive != null) {
            return activeHive;
        }
        String knownHive = this.ctx.getKnownHiveId(playerUuid);
        if (knownHive != null) {
            return knownHive;
        }
        MinecraftServer server = this.ctx.server();
        Optional<NameAndId> cached = server.services().nameToIdCache().get(playerUuid);
        if (cached.isPresent()) {
            PlayerTeam team = server.getScoreboard().getPlayersTeam(cached.get().name());
            return team != null ? HiveMindData.TEAM_HIVE_PREFIX + team.getName() : HiveMindData.DEFAULT_HIVE_ID;
        }
        return null;
    }
}
