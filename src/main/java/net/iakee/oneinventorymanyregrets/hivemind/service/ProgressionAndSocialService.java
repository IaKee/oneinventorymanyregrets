package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveEnchantmentMenuAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveFoxAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMerchantMenuAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.hivemind.HivePlayerProgressAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveVillagerAccess;
import net.iakee.oneinventorymanyregrets.hivemind.OIMRLang;
import net.iakee.oneinventorymanyregrets.network.OIMRNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    private final HiveMindContexts.ProgressionSocial ctx;

    public ProgressionAndSocialService(HiveMindContexts.ProgressionSocial ctx) {
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
        int whole = Mth.floor(total + HiveMindContexts.FRACTIONAL_EPSILON);
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
        int whole = Mth.floor(total + HiveMindContexts.FRACTIONAL_EPSILON);
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

    public record GroundSleepPlacement(Vec3 targetPos, BlockPos sleepBlockPos, Direction orientation) {
    }

    public void onPlayerStartedSleeping(ServerPlayer source) {
        if (this.ctx.isSyncingGroupSleep()
            || !this.ctx.isAttached(source)
            || source.isRemoved()
            || source.isDeadOrDying()
            || source.isSpectator()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        if (hiveId == null) {
            return;
        }

        this.ctx.setSyncingGroupSleep(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == source
                    || other.isRemoved()
                    || other.isDeadOrDying()
                    || other.isSpectator()
                    || other.isSleeping()
                    || other.connection == null
                    || !other.connection.isAcceptingMessages()) {
                    continue;
                }
                startGroundSleep(other);
            }
        } finally {
            this.ctx.setSyncingGroupSleep(false);
        }
    }

    public void onPlayerStoppedSleeping(ServerPlayer source, boolean forcefulWakeUp, boolean updateLevelList) {
        if (this.ctx.isSyncingGroupSleep() || !this.ctx.isAttached(source)) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        if (hiveId == null) {
            return;
        }
        if (!updateLevelList) {
            if (forcefulWakeUp) {
                for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
                    if (member != source
                        && member.isSleeping()
                        && !((HiveLivingEntityAccess) member).oimr$isGroundSleeping()) {
                        return;
                    }
                }
            }
            this.ctx.setSyncingGroupSleep(true);
            try {
                for (ServerPlayer other : this.ctx.getOnlineMembers(hiveId)) {
                    if (other == source || other.isRemoved() || !other.isSleeping()) {
                        continue;
                    }
                    if (((HiveLivingEntityAccess) other).oimr$isGroundSleeping()) {
                        other.stopSleepInBed(forcefulWakeUp, forcefulWakeUp);
                    }
                }
            } finally {
                this.ctx.setSyncingGroupSleep(false);
            }
            return;
        }

        java.util.List<ServerPlayer> wokenPlayers = new java.util.ArrayList<>();
        this.ctx.setSyncingGroupSleep(true);
        try {
            for (ServerPlayer other : this.ctx.getOnlineMembers(hiveId)) {
                if (other == source || other.isRemoved() || !other.isSleeping()) {
                    continue;
                }
                other.stopSleepInBed(forcefulWakeUp, true);
                if (!other.isSleeping()) {
                    wokenPlayers.add(other);
                }
            }
        } finally {
            this.ctx.setSyncingGroupSleep(false);
        }

        if (!wokenPlayers.isEmpty() && (forcefulWakeUp || source.level().isDarkOutside())) {
            Component wokenNames = joinDisplayNames(wokenPlayers);
            String key = wokenPlayers.size() == 1 ? "oimr.sleep.woke_up" : "oimr.sleep.woke_up.plural";
            Component message = OIMRLang.translatable(key, wokenNames, source.getDisplayName());
            for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
                if (!member.isRemoved() && member.connection != null) {
                    member.sendSystemMessage(OIMRLang.localizeForPlayer(message, member));
                }
            }
        }
    }

    private static Component joinDisplayNames(java.util.List<ServerPlayer> players) {
        if (players.size() == 1) {
            return players.getFirst().getDisplayName();
        }
        if (players.size() == 2) {
            return OIMRLang.translatable("death.oimr.list.two", players.get(0).getDisplayName(), players.get(1).getDisplayName());
        }
        Component combined = players.getFirst().getDisplayName();
        for (int i = 1; i < players.size() - 1; i++) {
            combined = OIMRLang.translatable("death.oimr.list.sep", combined, players.get(i).getDisplayName());
        }
        return OIMRLang.translatable("death.oimr.list.end", combined, players.getLast().getDisplayName());
    }

    private static void startGroundSleep(ServerPlayer player) {
        if (player.isPassenger()) {
            player.stopRiding();
        }
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }

        GroundSleepPlacement placement = findGroundSleepPlacement(player);
        ((HiveLivingEntityAccess) player).oimr$setGroundSleeping(true, placement.orientation());
        ((HivePlayerProgressAccess) player).oimr$setSleepCounter(0);
        player.setPos(placement.targetPos().x, placement.targetPos().y, placement.targetPos().z);
        player.setPose(Pose.SLEEPING);
        player.setSleepingPos(placement.sleepBlockPos());
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        if (player.connection != null) {
            player.connection.teleport(
                placement.targetPos().x,
                placement.targetPos().y,
                placement.targetPos().z,
                player.getYRot(),
                player.getXRot()
            );
        }
        OIMRNetworking.sendGroundSleepUpdate(player, true, placement.orientation());
        player.level().updateSleepingPlayerList();
    }

    public static GroundSleepPlacement findGroundSleepPlacement(ServerPlayer player) {
        ServerLevel level = player.level();
        double floorY = resolveFloorY(level, player);
        BlockPos basePos = BlockPos.containing(player.getX(), floorY + 0.05D, player.getZ());
        Direction preferred = Direction.fromYRot(player.getYRot()).getOpposite();
        if (preferred.getAxis() == Direction.Axis.Y) {
            preferred = Direction.NORTH;
        }
        Direction[] directions = {
            preferred,
            preferred.getClockWise(),
            preferred.getCounterClockWise(),
            preferred.getOpposite()
        };

        double sleepY = floorY + 0.125D;
        double baseHeadX = basePos.getX() + 0.5D;
        double baseHeadZ = basePos.getZ() + 0.5D;

        for (Direction dir : directions) {
            if (isSleepPoseWallFree(level, player, baseHeadX, sleepY, baseHeadZ, dir)
                && hasSafeFloorSupport(level, player, baseHeadX, floorY, baseHeadZ, dir)) {
                return new GroundSleepPlacement(new Vec3(baseHeadX, sleepY, baseHeadZ), basePos, dir);
            }
        }
        for (Direction dir : directions) {
            if (isSleepPoseWallFree(level, player, baseHeadX, sleepY, baseHeadZ, dir)) {
                return new GroundSleepPlacement(new Vec3(baseHeadX, sleepY, baseHeadZ), basePos, dir);
            }
        }

        boolean baseHeadFree = isHeadSpotWallFree(level, player, baseHeadX, sleepY, baseHeadZ);
        int[][] offsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (boolean requireFloor : new boolean[] { true, false }) {
            for (int[] offset : offsets) {
                BlockPos candidatePos = basePos.offset(offset[0], 0, offset[1]);
                double candX = candidatePos.getX() + 0.5D;
                double candZ = candidatePos.getZ() + 0.5D;
                if (isHazardousBlock(level, candidatePos)) {
                    continue;
                }
                if (baseHeadFree && !canStepWithoutPassingWall(level, player, baseHeadX, sleepY, baseHeadZ, candX, candZ)) {
                    continue;
                }
                for (Direction dir : directions) {
                    if (isSleepPoseWallFree(level, player, candX, sleepY, candZ, dir)
                        && (!requireFloor || hasSafeFloorSupport(level, player, candX, floorY, candZ, dir))) {
                        return new GroundSleepPlacement(new Vec3(candX, sleepY, candZ), candidatePos, dir);
                    }
                }
            }
        }

        if (baseHeadFree) {
            return new GroundSleepPlacement(new Vec3(baseHeadX, sleepY, baseHeadZ), basePos, preferred);
        }
        return new GroundSleepPlacement(new Vec3(player.getX(), sleepY, player.getZ()), basePos, preferred);
    }

    private static double resolveFloorY(ServerLevel level, ServerPlayer player) {
        double currentY = player.getY();
        if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying) {
            return currentY;
        }
        AABB scanBox = player.getBoundingBox().setMinY(currentY - 3.0D).setMaxY(currentY + 1.0E-4D);
        double bestTopY = Double.NEGATIVE_INFINITY;
        for (VoxelShape shape : level.getBlockCollisions(player, scanBox)) {
            double maxY = shape.max(Direction.Axis.Y);
            if (maxY <= currentY + 1.0E-4D && maxY > bestTopY) {
                bestTopY = maxY;
            }
        }
        if (Double.isFinite(bestTopY)) {
            BlockPos surfacePos = BlockPos.containing(player.getX(), bestTopY + 0.05D, player.getZ());
            if (!isHazardousBlock(level, surfacePos)) {
                return bestTopY;
            }
        }
        return currentY;
    }

    private static boolean isSleepPoseWallFree(
        ServerLevel level,
        ServerPlayer player,
        double headX,
        double sleepY,
        double headZ,
        Direction orientation
    ) {
        Direction towardFeet = orientation.getOpposite();
        double footX = headX + towardFeet.getStepX() * 1.35D;
        double footZ = headZ + towardFeet.getStepZ() * 1.35D;
        double minX = Math.min(headX, footX) - 0.25D;
        double maxX = Math.max(headX, footX) + 0.25D;
        double minZ = Math.min(headZ, footZ) - 0.25D;
        double maxZ = Math.max(headZ, footZ) + 0.25D;
        AABB bodyBox = new AABB(minX, sleepY, minZ, maxX, sleepY + 0.45D, maxZ);
        return level.noBlockCollision(player, bodyBox);
    }

    private static boolean isHeadSpotWallFree(
        ServerLevel level,
        ServerPlayer player,
        double headX,
        double sleepY,
        double headZ
    ) {
        AABB headBox = new AABB(headX - 0.25D, sleepY, headZ - 0.25D, headX + 0.25D, sleepY + 0.45D, headZ + 0.25D);
        return level.noBlockCollision(player, headBox);
    }

    private static boolean canStepWithoutPassingWall(
        ServerLevel level,
        ServerPlayer player,
        double fromX,
        double sleepY,
        double fromZ,
        double toX,
        double toZ
    ) {
        double midX = (fromX + toX) * 0.5D;
        double midZ = (fromZ + toZ) * 0.5D;
        AABB midBox = new AABB(midX - 0.25D, sleepY, midZ - 0.25D, midX + 0.25D, sleepY + 0.45D, midZ + 0.25D);
        return level.noBlockCollision(player, midBox);
    }

    private static boolean hasSafeFloorSupport(
        ServerLevel level,
        ServerPlayer player,
        double headX,
        double floorY,
        double headZ,
        Direction orientation
    ) {
        Direction towardFeet = orientation.getOpposite();
        double footX = headX + towardFeet.getStepX() * 1.25D;
        double footZ = headZ + towardFeet.getStepZ() * 1.25D;
        BlockPos headBlock = BlockPos.containing(headX, floorY + 0.05D, headZ);
        BlockPos footBlock = BlockPos.containing(footX, floorY + 0.05D, footZ);
        if (isHazardousBlock(level, headBlock) || isHazardousBlock(level, footBlock)) {
            return false;
        }
        AABB headFloorBox = new AABB(headX - 0.15D, floorY - 0.25D, headZ - 0.15D, headX + 0.15D, floorY + 0.02D, headZ + 0.15D);
        AABB footFloorBox = new AABB(footX - 0.15D, floorY - 0.25D, footZ - 0.15D, footX + 0.15D, floorY + 0.02D, footZ + 0.15D);
        return !level.noBlockCollision(player, headFloorBox) && !level.noBlockCollision(player, footFloorBox);
    }

    private static boolean isHazardousBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.LAVA)
            || state.is(BlockTags.FIRE)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.getFluidState().is(FluidTags.LAVA)
            || below.is(Blocks.MAGMA_BLOCK)
            || below.is(BlockTags.FIRE);
    }

    public void onAdvancementAwarded(ServerPlayer source, AdvancementHolder holder, String criterion) {
        if (!OIMRConfig.isSyncAdvancementsEnabled() || this.ctx.isSyncingAdvancements() || !this.ctx.isAttached(source)) {
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
        if (!OIMRConfig.isSyncAdvancementsEnabled() || this.ctx.isSyncingAdvancements() || !this.ctx.isAttached(source)) {
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
