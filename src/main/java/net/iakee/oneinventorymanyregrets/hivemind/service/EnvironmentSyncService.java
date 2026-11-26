package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.effect.OIMRMobEffects;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.network.OIMRNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Synchronizes air supply, fire, freezing, group respiration, and underwater audio status.
 */
public final class EnvironmentSyncService {
    private final HiveMindContext ctx;

    public EnvironmentSyncService(HiveMindContext ctx) {
        this.ctx = ctx;
    }

    public void syncUnderwaterOnJoin(ServerPlayer player) {
        HiveMind hive = this.ctx.getHiveForPlayer(player);
        if (hive != null) {
            boolean underwater = computeHiveUnderwaterState(this.ctx.getAliveMembers(hive.getId()));
            hive.setAnyMemberUnderwater(underwater);
            OIMRNetworking.syncUnderwaterToPlayer(player, underwater);
        } else {
            OIMRNetworking.syncUnderwaterToPlayer(player, false);
        }
    }

    public boolean computeHiveUnderwaterState(List<ServerPlayer> aliveMembers) {
        if (!OIMRConfig.isUnderwaterMuffleEnabled() || OIMRConfig.getAffinityPercent() <= 0) {
            return false;
        }
        for (ServerPlayer member : aliveMembers) {
            if (!member.isSpectator() && member.isEyeInFluid(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    public void tickHiveEnvironment(
        HiveMind hive,
        List<ServerPlayer> aliveMembers,
        long gameTime,
        HiveMindData data
    ) {
        if (!hive.tryClaimEnvironmentTick(gameTime)) {
            return;
        }

        // 1. Air Supply (1x cap vs Nx multiplicative across submerged non-breathing members)
        List<ServerPlayer> drowners = new ArrayList<>();
        for (ServerPlayer member : aliveMembers) {
            if (!member.isCreative()
                && !member.isSpectator()
                && !member.getAbilities().invulnerable
                && member.isEyeInFluid(FluidTags.WATER)
                && !member.level().getBlockState(BlockPos.containing(member.getX(), member.getEyeY(), member.getZ())).is(Blocks.BUBBLE_COLUMN)
                && !member.canBreatheUnderwater()
                && !MobEffectUtil.hasWaterBreathing(member)) {
                drowners.add(member);
            }
        }
        int prevAir = hive.getAirSupply();
        if (!drowners.isEmpty()) {
            int drainSteps = OIMRConfig.isUnderwaterBreathCapped1x() ? 1 : drowners.size();
            int currentAir = prevAir;
            int maxAir = Math.max(1, drowners.getFirst().getMaxAirSupply());
            for (int i = 0; i < drainSteps; i++) {
                ServerPlayer drowner = drowners.get(i);
                int beforeStep = currentAir;
                int nextAir = ((HiveLivingEntityAccess) drowner).oimr$decreaseAirSupply(beforeStep);
                int drownerMaxAir = Math.max(1, drowner.getMaxAirSupply());
                int prevBubbles = airSupplyToFullBubbles(beforeStep, drownerMaxAir);
                int nextBubbles = airSupplyToFullBubbles(nextAir, drownerMaxAir);
                int drainedBubbles = prevBubbles - nextBubbles;
                if (drainedBubbles > 0) {
                    String drownerName = drowner.getGameProfile().name();
                    for (ServerPlayer member : this.ctx.getOnlineMembers(hive.getId())) {
                        OIMRNetworking.sendAirFeed(member, drownerName, drainedBubbles);
                    }
                }
                if (nextAir <= -20) {
                    nextAir = 0;
                    drowner.level().broadcastEntityEvent(drowner, (byte) 67);
                    drowner.hurtServer(drowner.level(), drowner.damageSources().drown(), 2.0F);
                }
                currentAir = Math.min(nextAir, maxAir);
            }
            hive.setAirSupply(currentAir);
        } else {
            int maxAir = aliveMembers.getFirst().getMaxAirSupply();
            if (prevAir < maxAir) {
                hive.setAirSupply(Math.min(prevAir + 4, maxAir));
            }
        }
        if (hive.getAirSupply() != prevAir) {
            data.setDirty();
        }

        // 2. Fire ticks (1x cap vs Nx multiplicative across party)
        int prevFire = hive.getRemainingFireTicks();
        if (prevFire > 0) {
            boolean allFireImmune = true;
            List<ServerPlayer> vulnerableBurners = new ArrayList<>();
            for (ServerPlayer member : aliveMembers) {
                if (!member.fireImmune()) {
                    allFireImmune = false;
                    if (this.ctx.isVitalHazardTarget(member)) {
                        vulnerableBurners.add(member);
                    }
                }
            }
            if (allFireImmune) {
                hive.setRemainingFireTicks(0);
            } else {
                if (!vulnerableBurners.isEmpty() && prevFire % 20 == 0) {
                    int burnCount = OIMRConfig.isDotDamageCapped1x() ? 1 : vulnerableBurners.size();
                    for (int i = 0; i < burnCount; i++) {
                        ServerPlayer burner = vulnerableBurners.get(i);
                        burner.hurtServer(burner.level(), burner.damageSources().onFire(), 1.0F);
                    }
                }
                hive.setRemainingFireTicks(prevFire - 1);
            }
            data.setDirty();
        }

        // 3. Freezing (1x cap vs Nx multiplicative build-up and damage in powder snow, 1x thaw outside)
        int prevFreeze = hive.getTicksFrozen();
        List<ServerPlayer> freezers = new ArrayList<>();
        for (ServerPlayer member : aliveMembers) {
            if (!member.isCreative()
                && !member.isSpectator()
                && !member.getAbilities().invulnerable
                && member.isInPowderSnow
                && member.canFreeze()) {
                freezers.add(member);
            }
        }
        if (!freezers.isEmpty()) {
            ServerPlayer leadFreezer = freezers.getFirst();
            int increment = OIMRConfig.isDotDamageCapped1x() ? 1 : freezers.size();
            if (hive.tryClaimFreezeIncrement(gameTime)) {
                hive.setTicksFrozen(Math.min(leadFreezer.getTicksRequiredToFreeze(), prevFreeze + increment));
            }
            if (gameTime % 40 == 0 && hive.getTicksFrozen() >= leadFreezer.getTicksRequiredToFreeze()) {
                int freezeHurtCount = OIMRConfig.isDotDamageCapped1x() ? 1 : freezers.size();
                for (int i = 0; i < freezeHurtCount; i++) {
                    ServerPlayer freezer = freezers.get(i);
                    freezer.hurtServer(freezer.level(), freezer.damageSources().freeze(), 1.0F);
                }
            }
        } else if (prevFreeze > 0) {
            hive.setTicksFrozen(Math.max(0, prevFreeze - 2));
        }
        if (hive.getTicksFrozen() != prevFreeze) {
            data.setDirty();
        }

        syncEnvironmentToMembers(hive, aliveMembers);
    }

    private static int airSupplyToFullBubbles(int airSupply, int maxAir) {
        int clamped = Mth.clamp(airSupply, 0, maxAir);
        return Math.max(0, Mth.ceil((float) (clamped - 2) * 10.0F / (float) maxAir));
    }

    private void syncEnvironmentToMembers(HiveMind hive, List<ServerPlayer> aliveMembers) {
        this.ctx.setSyncingEnvironment(true);
        try {
            for (ServerPlayer member : aliveMembers) {
                member.setAirSupply(Mth.clamp(hive.getAirSupply(), -20, member.getMaxAirSupply()));
                member.setRemainingFireTicks(hive.getRemainingFireTicks());
                member.setSharedFlagOnFire(hive.getRemainingFireTicks() > 0);
                member.setTicksFrozen(hive.getTicksFrozen());
            }
        } finally {
            this.ctx.setSyncingEnvironment(false);
        }
    }

    public void onPlayerIgnited(Entity entity) {
        if (this.ctx.isSyncingEnvironment()
            || !OIMRConfig.isFullAffinity()
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)
            || player.isDeadOrDying()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        int newFireTicks = player.getRemainingFireTicks();
        if (newFireTicks > hive.getRemainingFireTicks()) {
            hive.setRemainingFireTicks(newFireTicks);
            hive.setTicksFrozen(0);
            this.ctx.data().setDirty();
            syncEnvironmentToMembers(hive, this.ctx.getAliveMembers(hiveId));
        }
    }

    public void onPlayerClearFire(Entity entity) {
        if (this.ctx.isSyncingEnvironment()
            || !OIMRConfig.isFullAffinity()
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)
            || player.isDeadOrDying()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null || hive.getRemainingFireTicks() <= 0) {
            return;
        }

        List<ServerPlayer> alive = this.ctx.getAliveMembers(hiveId);
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
        this.ctx.data().setDirty();
        syncEnvironmentToMembers(hive, alive);
    }

    public void onPlayerTicksFrozenChanged(Entity entity, int ticks) {
        if (this.ctx.isSyncingEnvironment()
            || !OIMRConfig.isFullAffinity()
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)
            || player.isDeadOrDying()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        long gameTime = this.ctx.server().overworld().getGameTime();
        if (ticks == 0) {
            if (hive.getTicksFrozen() != 0) {
                hive.setTicksFrozen(0);
                this.ctx.data().setDirty();
                syncEnvironmentToMembers(hive, this.ctx.getAliveMembers(hiveId));
            }
        } else if (ticks > hive.getTicksFrozen()) {
            if (hive.tryClaimFreezeIncrement(gameTime)) {
                int capped = Math.min(player.getTicksRequiredToFreeze(), hive.getTicksFrozen() + 1);
                hive.setTicksFrozen(capped);
                this.ctx.data().setDirty();
                syncEnvironmentToMembers(hive, this.ctx.getAliveMembers(hiveId));
            } else {
                syncEnvironmentToMembers(hive, List.of(player));
            }
        }
    }

    public void onPlayerAirSupplyChanged(Entity entity, int supply) {
        if (this.ctx.isSyncingEnvironment()
            || !OIMRConfig.isFullAffinity()
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)
            || player.isDeadOrDying()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null || hive.getAirSupply() == supply) {
            return;
        }

        hive.setAirSupply(Mth.clamp(supply, -20, player.getMaxAirSupply()));
        this.ctx.data().setDirty();
        syncEnvironmentToMembers(hive, this.ctx.getAliveMembers(hiveId));
    }

    public OptionalInt tryDecreaseAirSupplyWithGroupRespiration(LivingEntity entity, int currentSupply) {
        if (!(entity instanceof ServerPlayer player) || !this.ctx.isAttached(player)) {
            return OptionalInt.empty();
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return OptionalInt.empty();
        }

        if (player.hasEffect(OIMRMobEffects.AQUATIC_RESPIRATION)
            || player.hasEffect(OIMRMobEffects.CONDUIT_ECHO)
            || player.hasEffect(OIMRMobEffects.NAUTILUS_ECHO)) {
            double effectFactor = this.ctx.effectSync().getEffectiveReplicatedEffectFactor(player);
            if (effectFactor > 0.0D && player.getRandom().nextDouble() < effectFactor) {
                return OptionalInt.of(currentSupply);
            }
        }
        double ownBonus = Math.max(0.0D, player.getAttributeValue(Attributes.OXYGEN_BONUS));
        double maxOtherBonus = 0.0D;
        for (ServerPlayer member : this.ctx.getAliveMembers(hiveId)) {
            if (member != player && this.ctx.isVitalHazardTarget(member)) {
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
}
