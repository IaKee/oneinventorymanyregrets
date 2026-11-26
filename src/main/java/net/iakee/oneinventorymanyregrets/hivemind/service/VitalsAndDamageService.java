package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.network.DamageIconType;
import net.iakee.oneinventorymanyregrets.network.OIMRNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles shared health, absorption, healing, fractional damage remainders,
 * multi-member hazard caps (lava/drown/DoT), hunger scaling, peaceful regen, and knockback.
 */
public final class VitalsAndDamageService {
    private final HiveMindContexts.VitalsDamage ctx;

    public VitalsAndDamageService(HiveMindContexts.VitalsDamage ctx) {
        this.ctx = ctx;
    }

    public float scaleFoodExhaustion(ServerPlayer player, float rawAmount) {
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return rawAmount;
        }
        float weight = OIMRConfig.getHungerWeight();
        if (this.ctx.isApplyingUnscaledHungerEffect()) {
            return weight * rawAmount;
        }
        int denominator = this.ctx.getHungerScaleDenominator(hiveId);
        return (weight * rawAmount) / (float) denominator;
    }

    public boolean shouldTickFood(ServerPlayer player) {
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return true;
        }
        if (player.isDeadOrDying() || player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable) {
            return false;
        }
        HiveMind hive = this.ctx.data().getIfPresent(hiveId);
        if (hive == null) {
            return true;
        }
        return hive.tryClaimFoodTick(this.ctx.server().overworld().getGameTime());
    }

    public boolean isPrimaryPeacefulRegenMember(ServerPlayer player) {
        if (!this.ctx.isAttached(player) || player.isDeadOrDying()) {
            return false;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return true;
        }
        List<ServerPlayer> alive = this.ctx.getAliveMembers(hiveId);
        if (alive.isEmpty()) {
            return false;
        }
        List<ServerPlayer> vitalTargets = new ArrayList<>();
        for (ServerPlayer member : alive) {
            if (this.ctx.isVitalHazardTarget(member)) {
                vitalTargets.add(member);
            }
        }
        List<ServerPlayer> pool = vitalTargets.isEmpty() ? alive : vitalTargets;
        ServerPlayer chosen = null;
        for (ServerPlayer candidate : pool) {
            if (chosen == null || candidate.getUUID().compareTo(chosen.getUUID()) < 0) {
                chosen = candidate;
            }
        }
        return chosen == player;
    }

    public boolean shouldRunPeacefulRegen(ServerPlayer player) {
        if (!this.ctx.isAttached(player)) {
            return true;
        }
        if (player.isDeadOrDying()) {
            return false;
        }
        if (OIMRConfig.isFullAffinity()) {
            return isPrimaryPeacefulRegenMember(player);
        }
        return true;
    }

    public boolean beginPeacefulRegenTick(ServerPlayer player) {
        if (OIMRConfig.isFullAffinity() || !this.ctx.isAttached(player) || this.ctx.isSuppressAffinityVitalsCascade()) {
            return false;
        }
        this.ctx.setSuppressAffinityVitalsCascade(true);
        return true;
    }

    public void endPeacefulRegenTick(boolean restoreSuppress) {
        if (restoreSuppress) {
            this.ctx.setSuppressAffinityVitalsCascade(false);
        }
    }

    public boolean shouldApplyPeacefulFoodRegen(ServerPlayer player) {
        if (!this.ctx.isAttached(player)) {
            return true;
        }
        return isPrimaryPeacefulRegenMember(player);
    }

    private static boolean isSharedEnvFireSource(DamageSource source) {
        return source.is(DamageTypes.IN_FIRE)
            || source.is(DamageTypes.ON_FIRE)
            || source.is(DamageTypes.CAMPFIRE)
            || source.is(DamageTypes.HOT_FLOOR)
            || source.is(DamageTypes.SULFUR_CUBE_HOT);
    }

    private HiveMind.@Nullable HazardCapCategory resolveCappedHazardCategory(DamageSource source) {
        if (source.is(DamageTypes.LAVA)) {
            return OIMRConfig.isLavaDamageCapped1x() ? HiveMind.HazardCapCategory.LAVA : null;
        }
        if (source.is(DamageTypes.DROWN)) {
            return OIMRConfig.isUnderwaterBreathCapped1x() ? HiveMind.HazardCapCategory.DROWN : null;
        }
        if (!OIMRConfig.isDotDamageCapped1x()) {
            return null;
        }
        if (isSharedEnvFireSource(source)) {
            return HiveMind.HazardCapCategory.FIRE;
        }
        if (source.is(DamageTypes.FREEZE)) {
            return HiveMind.HazardCapCategory.FREEZE;
        }
        if (source.is(DamageTypes.WITHER)) {
            return HiveMind.HazardCapCategory.WITHER;
        }
        if (source.is(DamageTypes.CACTUS)) {
            return HiveMind.HazardCapCategory.CACTUS;
        }
        if (source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            return HiveMind.HazardCapCategory.SWEET_BERRY_BUSH;
        }
        if (source.is(DamageTypes.DRAGON_BREATH)) {
            return HiveMind.HazardCapCategory.DRAGON_BREATH;
        }
        Holder<MobEffect> activeDot = this.ctx.getActivePeriodicDotEffect();
        if (source.is(DamageTypes.MAGIC)
            && activeDot != null
            && activeDot.is(MobEffects.POISON)) {
            return HiveMind.HazardCapCategory.POISON;
        }
        return null;
    }

    private static boolean isHazardWindowActive(HiveMind.HazardWindowState window, long gameTime) {
        long elapsed = gameTime - window.getWindowStartTick();
        return elapsed >= 0L && elapsed < 10L;
    }

    public boolean shouldSuppressDuplicateHazardHurt(LivingEntity entity, DamageSource source, float damage) {
        if (this.ctx.isCascadingDeath()
            || this.ctx.isSyncingAffinityDamage()
            || OIMRConfig.getAffinityPercent() <= 0
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)) {
            return false;
        }
        HiveMind.HazardCapCategory category = resolveCappedHazardCategory(source);
        if (category == null) {
            return false;
        }
        HiveMind hive = this.ctx.getHiveForPlayer(player);
        if (hive == null) {
            return false;
        }
        HiveMind.HazardWindowState window = hive.getHazardWindowIfPresent(category);
        if (window == null || !isHazardWindowActive(window, this.ctx.server().overworld().getGameTime())) {
            return false;
        }
        if (OIMRConfig.isFullAffinity()) {
            return damage <= window.getMaxPreCooldownDamage();
        }
        if (!player.getUUID().equals(window.getPrimarySourcePlayer())) {
            float alreadyShared = window.getSharedDamageReceived(player.getUUID());
            return alreadyShared > 0.0F && damage <= alreadyShared + HiveMindContexts.FRACTIONAL_EPSILON;
        }
        return false;
    }

    public float adjustHazardDamage(LivingEntity entity, DamageSource source, float damage) {
        if (this.ctx.isCascadingDeath()
            || this.ctx.isSyncingAffinityDamage()
            || OIMRConfig.getAffinityPercent() <= 0
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)) {
            return damage;
        }
        HiveMind.HazardCapCategory category = resolveCappedHazardCategory(source);
        if (category == null) {
            return damage;
        }
        HiveMind hive = this.ctx.getHiveForPlayer(player);
        if (hive == null) {
            return damage;
        }
        HiveMind.HazardWindowState window = hive.getHazardWindowIfPresent(category);
        if (window == null || !isHazardWindowActive(window, this.ctx.server().overworld().getGameTime())) {
            return damage;
        }
        if (OIMRConfig.isFullAffinity()) {
            if (damage > window.getMaxPreCooldownDamage()) {
                return damage - window.getMaxPreCooldownDamage();
            }
            return damage;
        }
        if (!player.getUUID().equals(window.getPrimarySourcePlayer())) {
            float alreadyShared = window.getSharedDamageReceived(player.getUUID());
            if (alreadyShared > 0.0F) {
                return Math.max(0.0F, damage - alreadyShared);
            }
        }
        return damage;
    }

    public void recordHazardHurtIfApplicable(LivingEntity entity, DamageSource source, float preCooldownDamage) {
        if (this.ctx.isCascadingDeath()
            || this.ctx.isSyncingAffinityDamage()
            || OIMRConfig.getAffinityPercent() <= 0
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)) {
            return;
        }
        HiveMind.HazardCapCategory category = resolveCappedHazardCategory(source);
        if (category == null) {
            return;
        }
        HiveMind hive = this.ctx.getHiveForPlayer(player);
        if (hive == null) {
            return;
        }
        long gameTime = this.ctx.server().overworld().getGameTime();
        HiveMind.HazardWindowState window = hive.getOrCreateHazardWindow(category);
        if (isHazardWindowActive(window, gameTime)) {
            if (OIMRConfig.isFullAffinity() || player.getUUID().equals(window.getPrimarySourcePlayer())) {
                window.updateMaxPreCooldownDamage(preCooldownDamage);
            }
        } else {
            window.resetWindow(gameTime, preCooldownDamage, player.getUUID());
        }
    }

    public void onHealthChanged(LivingEntity entity) {
        if (this.ctx.isSyncingHealth()
            || this.ctx.isSyncingAffinityDamage()
            || this.ctx.isCascadingDeath()
            || !OIMRConfig.isFullAffinity()
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)) {
            return;
        }
        float newHealth = player.getHealth();
        if (newHealth <= 0.0F || player.isDeadOrDying()) {
            return;
        }

        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null || Float.compare(hive.getHealth(), newHealth) == 0) {
            return;
        }

        hive.setHealth(newHealth);
        this.ctx.data().setDirty();

        this.ctx.setSyncingHealth(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == player) {
                    continue;
                }
                other.setHealth(Mth.clamp(newHealth, 0.01F, other.getMaxHealth()));
            }
        } finally {
            this.ctx.setSyncingHealth(false);
        }
    }

    public void onAbsorptionChanged(LivingEntity entity) {
        if (this.ctx.isSyncingHealth()
            || this.ctx.isSyncingAffinityDamage()
            || this.ctx.isCascadingDeath()
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

        float newAbsorption = player.getAbsorptionAmount();
        if (Float.compare(hive.getAbsorption(), newAbsorption) == 0) {
            return;
        }

        hive.setAbsorption(newAbsorption);
        this.ctx.data().setDirty();

        this.ctx.setSyncingHealth(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == player) {
                    continue;
                }
                other.setAbsorptionAmount(Mth.clamp(newAbsorption, 0.0F, other.getMaxAbsorption()));
            }
        } finally {
            this.ctx.setSyncingHealth(false);
        }
    }

    public void onPlayerHealed(LivingEntity entity, float healedAmount) {
        if (this.ctx.isSyncingHealth()
            || this.ctx.isSyncingAffinityDamage()
            || this.ctx.isSuppressAffinityVitalsCascade()
            || this.ctx.isCascadingDeath()
            || OIMRConfig.isFullAffinity()
            || healedAmount <= 0.0F
            || !(entity instanceof ServerPlayer source)
            || !this.ctx.isAttached(source)
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
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        if (hiveId == null) {
            return;
        }
        HiveMind hive = this.ctx.data().getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        boolean dirty = false;
        this.ctx.setSyncingHealth(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
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
                if (totalHeal + HiveMindContexts.FRACTIONAL_EPSILON < 1.0D
                    && totalHeal + HiveMindContexts.FRACTIONAL_EPSILON < missingHealth) {
                    hive.setHealRemainder(other.getUUID(), totalHeal);
                    dirty = true;
                    continue;
                }
                float appliedHeal = (float) (totalHeal < 1.0D && totalHeal + HiveMindContexts.FRACTIONAL_EPSILON >= 1.0D ? 1.0D : totalHeal);
                hive.setHealRemainder(other.getUUID(), 0.0D);
                dirty = true;
                float nextHealth = Math.min(other.getMaxHealth(), other.getHealth() + appliedHeal);
                if (nextHealth > other.getHealth()) {
                    other.setHealth(nextHealth);
                }
            }
        } finally {
            this.ctx.setSyncingHealth(false);
        }
        if (dirty) {
            this.ctx.data().setDirty();
        }
    }

    public void onAfterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
        if (this.ctx.isCascadingDeath()
            || this.ctx.isSyncingAffinityDamage()
            || this.ctx.isSuppressAffinityVitalsCascade()
            || !OIMRConfig.isFullAffinity()
            || !(entity instanceof ServerPlayer victim)
            || !this.ctx.isAttached(victim)) {
            return;
        }
        if (damageTaken <= 0.0F || victim.isDeadOrDying()) {
            return;
        }

        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        if (hiveId == null) {
            return;
        }

        for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
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

    public void onPlayerActuallyHurt(ServerPlayer victim, DamageSource source, float damageTaken) {
        if (this.ctx.isCascadingDeath()
            || this.ctx.isSyncingAffinityDamage()
            || this.ctx.isSuppressAffinityVitalsCascade()
            || this.ctx.isSyncingHealth()
            || damageTaken <= 0.0F
            || !this.ctx.isAttached(victim)) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        if (hiveId != null) {
            DamageIconType iconType = DamageIconType.fromDamageSource(source);
            String victimName = victim.getGameProfile().name();
            for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
                OIMRNetworking.sendDamageFeed(member, victimName, iconType, damageTaken);
            }
        }
        if (OIMRConfig.isFullAffinity()) {
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
        if (sharedDamage <= 0.0D || hiveId == null) {
            return;
        }

        HiveMind hive = this.ctx.data().getIfPresent(hiveId);
        if (hive == null) {
            return;
        }

        HiveMind.HazardCapCategory cappedCategory = resolveCappedHazardCategory(source);
        HiveMind.HazardWindowState hazardWindow = null;
        if (cappedCategory != null) {
            long gameTime = this.ctx.server().overworld().getGameTime();
            hazardWindow = hive.getOrCreateHazardWindow(cappedCategory);
            if (isHazardWindowActive(hazardWindow, gameTime)) {
                if (!victim.getUUID().equals(hazardWindow.getPrimarySourcePlayer())) {
                    return;
                }
            } else {
                hazardWindow.resetWindow(gameTime, damageTaken, victim.getUUID());
            }
        }

        boolean dirty = false;
        this.ctx.setSyncingAffinityDamage(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == victim || other.isSpectator() || other.isCreative() || other.getAbilities().invulnerable) {
                    continue;
                }
                if (hazardWindow != null) {
                    hazardWindow.recordSharedDamageReceived(other.getUUID(), (float) sharedDamage);
                }
                double totalDamage = hive.getDamageRemainder(other.getUUID()) + sharedDamage;
                recordPendingDamageSource(other.getUUID(), source, sharedDamage);
                double effectiveHp = other.getHealth() + other.getAbsorptionAmount();
                if (totalDamage + HiveMindContexts.FRACTIONAL_EPSILON < 1.0D
                    && totalDamage + HiveMindContexts.FRACTIONAL_EPSILON < effectiveHp) {
                    hive.setDamageRemainder(other.getUUID(), totalDamage);
                    dirty = true;
                    continue;
                }

                float appliedDamage = (float) (totalDamage < 1.0D && totalDamage + HiveMindContexts.FRACTIONAL_EPSILON >= 1.0D ? 1.0D : totalDamage);
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
                        Component deathMsg = this.ctx.deathCascade().buildAffinityDeathMessage(other, victim, resolvedSource);
                        this.ctx.pendingDeathMessages().put(other.getUUID(), deathMsg);
                        try {
                            other.setHealth(0.0F);
                            other.die(resolvedSource);
                        } finally {
                            this.ctx.pendingDeathMessages().remove(other.getUUID());
                        }
                    } else {
                        other.setHealth(nextHealth);
                    }
                }
            }
        } finally {
            this.ctx.setSyncingAffinityDamage(false);
        }
        if (dirty) {
            this.ctx.data().setDirty();
        }
    }

    public void onPlayerConsumedFood(ServerPlayer eater, ItemStack foodItem, int recoveredFoodPoints) {
        if (this.ctx.isCascadingDeath() || foodItem.isEmpty() || !this.ctx.isAttached(eater)) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(eater.getUUID());
        if (hiveId == null) {
            return;
        }
        String eaterName = eater.getGameProfile().name();
        ItemStack iconStack = foodItem.copyWithCount(1);
        int clampedRecovered = Math.max(0, recoveredFoodPoints);
        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            OIMRNetworking.sendFoodFeed(member, eaterName, iconStack, clampedRecovered);
        }
    }

    public void onPlayerKnockback(
        LivingEntity entity,
        double rawPower,
        double appliedXd,
        double appliedZd,
        @Nullable DamageSource source,
        float damage,
        boolean comesFromEffect
    ) {
        if (this.ctx.isSyncingKnockback()
            || this.ctx.isCascadingDeath()
            || !OIMRConfig.isSharedKnockbackEnabled()
            || !(entity instanceof ServerPlayer victim)
            || !this.ctx.isAttached(victim)
            || victim.isDeadOrDying()) {
            return;
        }
        if (rawPower <= 0.0 || appliedXd * appliedXd + appliedZd * appliedZd <= 1.0E-12) {
            return;
        }

        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        if (hiveId == null) {
            return;
        }

        ServerPlayer attacker = null;
        if (source != null && source.getEntity() instanceof ServerPlayer sourcePlayer
            && sourcePlayer != victim
            && this.ctx.isAttached(sourcePlayer)
            && hiveId.equals(this.ctx.getAttachedHiveId(sourcePlayer.getUUID()))) {
            attacker = sourcePlayer;
        }

        float factor = OIMRConfig.getAffinityFactor();
        if (factor <= 0.0F) {
            return;
        }
        double scaledPower = rawPower * factor;

        this.ctx.setSyncingKnockback(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
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
            this.ctx.setSyncingKnockback(false);
        }
    }

    public void onExplosionKnockback(ServerLevel level, Map<Player, Vec3> hitPlayers) {
        if (this.ctx.isSyncingKnockback()
            || this.ctx.isCascadingDeath()
            || !OIMRConfig.isSharedKnockbackEnabled()
            || hitPlayers.isEmpty()) {
            return;
        }

        Map<String, Vec3> hiveKnockbacks = new HashMap<>();
        for (Map.Entry<Player, Vec3> entry : hitPlayers.entrySet()) {
            if (!(entry.getKey() instanceof ServerPlayer victim) || !this.ctx.isAttached(victim) || victim.isDeadOrDying()) {
                continue;
            }
            Vec3 knockback = entry.getValue();
            if (knockback.lengthSqr() <= 1.0E-12) {
                continue;
            }
            String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
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

        this.ctx.setSyncingKnockback(true);
        try {
            for (Map.Entry<String, Vec3> entry : hiveKnockbacks.entrySet()) {
                Vec3 knockback = entry.getValue().scale(factor);
                for (ServerPlayer other : this.ctx.getAliveMembers(entry.getKey())) {
                    if (hitPlayers.containsKey(other) || other.isSpectator() || other.isCreative()) {
                        continue;
                    }
                    other.pushFromExplosion(knockback);
                    other.syncVelocity = true;
                }
            }
        } finally {
            this.ctx.setSyncingKnockback(false);
        }
    }

    private void recordPendingDamageSource(UUID recipientUuid, DamageSource source, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        List<HiveMindContexts.PendingDamageContribution> list = this.ctx.pendingDamageSources()
            .computeIfAbsent(recipientUuid, ignored -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            HiveMindContexts.PendingDamageContribution entry = list.get(i);
            if (sameDamageOrigin(entry.source(), source)) {
                list.set(i, new HiveMindContexts.PendingDamageContribution(source, entry.amount() + amount));
                return;
            }
        }
        list.add(new HiveMindContexts.PendingDamageContribution(source, amount));
    }

    private static boolean sameDamageOrigin(DamageSource a, DamageSource b) {
        return Objects.equals(a.typeHolder(), b.typeHolder())
            && a.getEntity() == b.getEntity()
            && a.getDirectEntity() == b.getDirectEntity();
    }

    private DamageSource resolvePendingDamageSource(UUID recipientUuid, DamageSource fallback) {
        List<HiveMindContexts.PendingDamageContribution> list = this.ctx.pendingDamageSources().remove(recipientUuid);
        if (list == null || list.isEmpty()) {
            return fallback;
        }
        HiveMindContexts.PendingDamageContribution best = list.getLast();
        for (HiveMindContexts.PendingDamageContribution entry : list) {
            if (entry.amount() > best.amount() + HiveMindContexts.FRACTIONAL_EPSILON) {
                best = entry;
            } else if (Math.abs(entry.amount() - best.amount()) <= HiveMindContexts.FRACTIONAL_EPSILON
                && entry.source().getEntity() != null
                && best.source().getEntity() == null) {
                best = entry;
            }
        }
        return best.source();
    }
}
