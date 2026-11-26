package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.effect.OIMRMobEffects;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.network.OIMRNetworking;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronizes status effects at full and partial affinity, splash/lingering potion impacts,
 * periodic effect tick claiming, and translucency client rendering state.
 */
public final class EffectSyncService {
    private static final Identifier HEALTH_BOOST_MODIFIER_ID = Identifier.withDefaultNamespace("effect.health_boost");

    private final HiveMindContext ctx;

    public EffectSyncService(HiveMindContext ctx) {
        this.ctx = ctx;
    }

    public void syncTranslucencyOnJoin(ServerPlayer player) {
        OIMRNetworking.syncTranslucencyToPlayer(player, collectTranslucentPlayerIds());
    }

    private Set<UUID> collectTranslucentPlayerIds() {
        Set<UUID> ids = new HashSet<>();
        for (ServerPlayer member : this.ctx.server().getPlayerList().getPlayers()) {
            if (!member.isInvisible() && member.hasEffect(OIMRMobEffects.TRANSLUCENCY)) {
                ids.add(member.getUUID());
            }
        }
        return ids;
    }

    public void syncTranslucencyIfChanged() {
        Set<UUID> current = collectTranslucentPlayerIds();
        if (!current.equals(this.ctx.lastSyncedTranslucentIds())) {
            Set<UUID> immutable = Set.copyOf(current);
            this.ctx.setLastSyncedTranslucentIds(immutable);
            OIMRNetworking.syncTranslucencyToAll(this.ctx.server(), immutable);
        }
    }

    public boolean effectsDiffer(ServerPlayer player, HiveMind hive) {
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

    public void clearReplicatedEffectsFromPlayer(ServerPlayer player) {
        this.ctx.setSyncingEffects(true);
        try {
            OIMRMobEffects.clearReplicatedEffects(player);
            Set<Holder<MobEffect>> coarse = this.ctx.replicatedCoarseEffects().remove(player.getUUID());
            if (coarse != null) {
                for (Holder<MobEffect> holder : coarse) {
                    if (player.hasEffect(holder)) {
                        player.removeEffect(holder);
                    }
                }
            }
        } finally {
            this.ctx.setSyncingEffects(false);
        }
    }

    public void clearReplicatedEffectsFromAllAttached() {
        for (ServerPlayer player : this.ctx.server().getPlayerList().getPlayers()) {
            if (this.ctx.isAttached(player)) {
                clearReplicatedEffectsFromPlayer(player);
            }
        }
    }

    public void snapshotMergedNonReplicatedEffects(HiveMind hive, List<ServerPlayer> alive) {
        hive.getEffects().clear();
        for (ServerPlayer member : alive) {
            for (MobEffectInstance effect : member.getActiveEffects()) {
                if (OIMRMobEffects.isReplicatedEffect(effect.getEffect())) {
                    continue;
                }
                MobEffectInstance existing = hive.getEffects().get(effect.getEffect());
                if (existing == null || isEffectStrongerOrLonger(effect, existing)) {
                    hive.putEffect(effect);
                }
            }
        }
    }

    public void applyHiveEffectsToPlayer(ServerPlayer player, HiveMind hive) {
        this.ctx.setSyncingEffects(true);
        try {
            player.removeAllEffects();
            for (MobEffectInstance effect : hive.getEffects().values()) {
                MobEffectInstance copy = new MobEffectInstance(effect);
                player.forceAddEffect(copy, null);
                copy.onEffectStarted(player);
            }
        } finally {
            this.ctx.setSyncingEffects(false);
        }
    }

    public void beginPotionImpact(boolean stackDurations) {
        this.ctx.setSplashImpact(new HiveMindContext.SplashImpactState(stackDurations));
    }

    public boolean captureSplashPotionEffect(LivingEntity target, MobEffectInstance effect) {
        HiveMindContext.SplashImpactState splashImpact = this.ctx.getSplashImpact();
        if (splashImpact == null
            || this.ctx.isSyncingEffects()
            || !OIMRConfig.isSyncEffectsEnabled()
            || !OIMRConfig.isFullAffinity()
            || !(target instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)
            || player.isDeadOrDying()) {
            return false;
        }
        if (!player.canBeAffected(effect)) {
            return false;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return false;
        }
        splashImpact.record(hiveId, effect);
        return true;
    }

    public boolean captureInstantaneousPotionEffect(
        MobEffect mobEffect,
        LivingEntity target,
        int amplifier,
        double healthFactor
    ) {
        HiveMindContext.SplashImpactState splashImpact = this.ctx.getSplashImpact();
        if (splashImpact == null
            || this.ctx.isSyncingEffects()
            || !OIMRConfig.isFullAffinity()
            || !(target instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)
            || player.isDeadOrDying()) {
            return false;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return false;
        }
        splashImpact.recordInstantaneous(hiveId, mobEffect, player, amplifier, healthFactor);
        return true;
    }

    public void endSplashPotionImpact(Entity potion, @Nullable Entity effectSource) {
        HiveMindContext.SplashImpactState state = this.ctx.getSplashImpact();
        this.ctx.setSplashImpact(null);
        if (state == null || state.isEmpty() || !(potion.level() instanceof ServerLevel level)) {
            return;
        }
        HiveMindData data = this.ctx.data();
        if (!state.entries.isEmpty()) {
            this.ctx.setSyncingEffects(true);
            try {
                for (HiveMindContext.SplashImpactAccumulator entry : state.entries.values()) {
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

                    for (ServerPlayer member : this.ctx.getAliveMembers(entry.hiveId)) {
                        MobEffectInstance copy = new MobEffectInstance(resolved);
                        member.forceAddEffect(copy, effectSource);
                        copy.onEffectStarted(member);
                    }
                    if (MobEffects.HERO_OF_THE_VILLAGE.equals(entry.effect)) {
                        this.ctx.progressionAndSocial().refreshOpenMerchantPricesForHive(entry.hiveId);
                    }
                }
            } finally {
                this.ctx.setSyncingEffects(false);
            }
        }
        for (HiveMindContext.InstantaneousImpactEntry entry : state.instantEntries.values()) {
            if (entry.target.isAlive() && this.ctx.isAttached(entry.target)) {
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

    public void onEffectDowngraded(LivingEntity entity, MobEffectInstance effect) {
        if (this.ctx.isSyncingEffects()
            || !OIMRConfig.isSyncEffectsEnabled()
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

        hive.putEffect(effect);
        this.ctx.data().setDirty();

        this.ctx.setSyncingEffects(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == player) {
                    continue;
                }
                MobEffectInstance copy = new MobEffectInstance(effect);
                other.forceAddEffect(copy, null);
                copy.onEffectStarted(other);
            }
        } finally {
            this.ctx.setSyncingEffects(false);
        }
        if (MobEffects.HERO_OF_THE_VILLAGE.equals(effect.getEffect())) {
            this.ctx.progressionAndSocial().refreshOpenMerchantPricesForHive(hiveId);
        }
    }

    public double getEffectiveReplicatedEffectFactor(LivingEntity entity) {
        if (entity instanceof ServerPlayer player && this.ctx.isAttached(player)) {
            if (OIMRConfig.isFullAffinity()) {
                return 1.0D;
            }
            if (!OIMRConfig.isSyncEffectsEnabled()) {
                return 0.0D;
            }
            return OIMRConfig.getAffinityFactorDouble();
        }
        return OIMRConfig.isSyncEffectsEnabled() ? OIMRConfig.getAffinityFactorDouble() : 0.0D;
    }

    public void onEffectAddedOrUpdated(LivingEntity entity, MobEffectInstance effect, @Nullable Entity source) {
        if (this.ctx.isSyncingEffects()
            || !OIMRConfig.isSyncEffectsEnabled()
            || (this.ctx.getSplashImpact() != null && OIMRConfig.isFullAffinity())
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)
            || player.isDeadOrDying()) {
            return;
        }
        if (OIMRMobEffects.isReplicatedEffect(effect.getEffect())) {
            if (OIMRConfig.isFullAffinity() || OIMRConfig.getAffinityPercent() <= 0) {
                clearReplicatedEffectsFromPlayer(player);
            }
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return;
        }

        if (!OIMRConfig.isFullAffinity()) {
            handlePartialAffinityEffectAdded(player, hiveId, hive, effect, source);
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
        this.ctx.data().setDirty();

        this.ctx.setSyncingEffects(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == player && !playerNeedsResync) {
                    continue;
                }
                MobEffectInstance copy = new MobEffectInstance(resolved);
                other.forceAddEffect(copy, source);
                copy.onEffectStarted(other);
            }
        } finally {
            this.ctx.setSyncingEffects(false);
        }
        if (MobEffects.HERO_OF_THE_VILLAGE.equals(effect.getEffect())) {
            this.ctx.progressionAndSocial().refreshOpenMerchantPricesForHive(hiveId);
        }
    }

    private void handlePartialAffinityEffectAdded(
        ServerPlayer player,
        String hiveId,
        HiveMind hive,
        MobEffectInstance effect,
        @Nullable Entity source
    ) {
        Holder<MobEffect> effectHolder = effect.getEffect();
        Set<Holder<MobEffect>> ownReplicated = this.ctx.replicatedCoarseEffects().get(player.getUUID());
        if (ownReplicated != null) {
            ownReplicated.remove(effectHolder);
        }
        if (effectHolder.is(MobEffects.INFESTED)) {
            hive.setInfestedChanceScale(player.getUUID(), 1.0D);
            this.ctx.data().setDirty();
        }
        if (isFoodSharedDirectEffect(effectHolder) || effectHolder.is(MobEffects.RAID_OMEN)) {
            return;
        }

        double factor = OIMRConfig.getAffinityFactorDouble();
        if (factor <= 0.0D) {
            return;
        }
        Holder<MobEffect> replicatedCounterpart = OIMRMobEffects.getReplicatedCounterpart(effectHolder);
        if (replicatedCounterpart != null) {
            this.ctx.setSyncingEffects(true);
            try {
                for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                    if (other == player) {
                        continue;
                    }
                    MobEffectInstance replicated = new MobEffectInstance(
                        replicatedCounterpart,
                        effect.getDuration(),
                        effect.getAmplifier(),
                        effect.isAmbient(),
                        effect.isVisible(),
                        effect.showIcon()
                    );
                    if (!other.canBeAffected(replicated)) {
                        continue;
                    }
                    MobEffectInstance current = other.getEffect(replicatedCounterpart);
                    if (isEffectStrongerOrLonger(replicated, current)) {
                        other.forceAddEffect(replicated, source);
                        replicated.onEffectStarted(other);
                    }
                }
            } finally {
                this.ctx.setSyncingEffects(false);
            }
            syncTranslucencyIfChanged();
            return;
        }
        Holder<MobEffect> fineCounterpart = OIMRMobEffects.getFineCounterpart(effectHolder);
        if (fineCounterpart != null) {
            int sourceLevels = effect.getAmplifier() + 1;
            double exactFineUnits = sourceLevels * factor * OIMRMobEffects.FINE_SUBDIVISIONS;
            boolean infinite = effect.isInfiniteDuration();
            boolean dirty = false;
            this.ctx.setSyncingEffects(true);
            try {
                for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                    if (other == player) {
                        continue;
                    }
                    if (exactFineUnits >= 1.0D - HiveMindContext.FRACTIONAL_EPSILON) {
                        int totalFineUnits = Math.max(1, (int) Math.floor(exactFineUnits + HiveMindContext.FRACTIONAL_EPSILON));
                        int coarseLevels = totalFineUnits / OIMRMobEffects.FINE_SUBDIVISIONS;
                        int fineLevels = totalFineUnits % OIMRMobEffects.FINE_SUBDIVISIONS;
                        if (coarseLevels > 0) {
                            MobEffectInstance coarseInst = new MobEffectInstance(
                                effectHolder,
                                effect.getDuration(),
                                coarseLevels - 1,
                                effect.isAmbient(),
                                effect.isVisible(),
                                effect.showIcon()
                            );
                            if (other.canBeAffected(coarseInst) && isEffectStrongerOrLonger(coarseInst, other.getEffect(effectHolder))) {
                                other.forceAddEffect(coarseInst, source);
                                coarseInst.onEffectStarted(other);
                                this.ctx.replicatedCoarseEffects()
                                    .computeIfAbsent(other.getUUID(), ignored -> new HashSet<>())
                                    .add(effectHolder);
                            }
                        }
                        if (fineLevels > 0) {
                            MobEffectInstance fineInst = new MobEffectInstance(
                                fineCounterpart,
                                effect.getDuration(),
                                fineLevels - 1,
                                effect.isAmbient(),
                                effect.isVisible(),
                                effect.showIcon()
                            );
                            if (other.canBeAffected(fineInst)) {
                                MobEffectInstance currentFine = other.getEffect(fineCounterpart);
                                if (currentFine == null
                                    || currentFine.getAmplifier() != fineInst.getAmplifier()
                                    || Math.abs(currentFine.getDuration() - fineInst.getDuration()) > 2) {
                                    other.forceAddEffect(fineInst, source);
                                    fineInst.onEffectStarted(other);
                                }
                            }
                        } else if (other.hasEffect(fineCounterpart)) {
                            other.removeEffect(fineCounterpart);
                        }
                    } else {
                        int targetDuration;
                        if (infinite) {
                            targetDuration = effect.getDuration();
                        } else {
                            double scaledDuration = (double) effect.getDuration() * exactFineUnits;
                            double totalDuration = hive.getEffectDurationRemainder(other.getUUID(), fineCounterpart) + scaledDuration;
                            targetDuration = Mth.floor(totalDuration + HiveMindContext.FRACTIONAL_EPSILON);
                            double nextRem = Math.max(0.0D, totalDuration - targetDuration);
                            hive.setEffectDurationRemainder(other.getUUID(), fineCounterpart, nextRem);
                            dirty = true;
                        }
                        if (infinite || targetDuration >= 1) {
                            MobEffectInstance fineInst = new MobEffectInstance(
                                fineCounterpart,
                                targetDuration,
                                0,
                                effect.isAmbient(),
                                effect.isVisible(),
                                effect.showIcon()
                            );
                            if (other.canBeAffected(fineInst) && isEffectStrongerOrLonger(fineInst, other.getEffect(fineCounterpart))) {
                                other.forceAddEffect(fineInst, source);
                                fineInst.onEffectStarted(other);
                            }
                        }
                    }
                }
            } finally {
                this.ctx.setSyncingEffects(false);
            }
            if (dirty) {
                this.ctx.data().setDirty();
            }
            return;
        }

        int fullAmplifier = effect.getAmplifier();
        boolean infinite = effect.isInfiniteDuration();
        boolean omenTrigger = isOmenTriggerEffect(effectHolder);
        boolean deathTrigger = isDeathTriggerEffect(effectHolder);
        double scaledDuration = (infinite || omenTrigger || deathTrigger) ? 0.0D : (double) effect.getDuration() * factor;
        int unitTicks = (infinite || omenTrigger || deathTrigger) ? 1 : getEffectDurationUnitTicks(effectHolder, fullAmplifier);
        boolean dirty = false;

        this.ctx.setSyncingEffects(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
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
                    int grantedUnits = Mth.floor(totalTrigger + HiveMindContext.FRACTIONAL_EPSILON);
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
                    int units = Mth.floor((totalDuration + HiveMindContext.FRACTIONAL_EPSILON) / unitTicks);
                    targetDuration = units * unitTicks;
                    nextDurationRem = Math.max(0.0D, totalDuration - targetDuration);
                    hive.setEffectDurationRemainder(other.getUUID(), effectHolder, nextDurationRem);
                    dirty = true;
                }

                if (MobEffects.ABSORPTION.equals(effectHolder)) {
                    double totalAbsorb = hive.getAbsorptionRemainder(other.getUUID()) + ((fullAmplifier + 1) * 4.0D * factor);
                    if (totalAbsorb + HiveMindContext.FRACTIONAL_EPSILON < 1.0D || (!infinite && targetDuration < unitTicks)) {
                        hive.setAbsorptionRemainder(other.getUUID(), totalAbsorb);
                        if (!infinite && targetDuration >= unitTicks) {
                            hive.setEffectDurationRemainder(other.getUUID(), effectHolder, nextDurationRem + targetDuration);
                        }
                        dirty = true;
                        continue;
                    }
                    float grantedAbsorption = (float) (totalAbsorb < 1.0D && totalAbsorb + HiveMindContext.FRACTIONAL_EPSILON >= 1.0D ? 1.0D : totalAbsorb);
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
                    int wholeBoost = Mth.floor(totalBoost + HiveMindContext.FRACTIONAL_EPSILON);
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
            this.ctx.setSyncingEffects(false);
        }
        if (dirty) {
            this.ctx.data().setDirty();
        }
        if (MobEffects.HERO_OF_THE_VILLAGE.equals(effectHolder)) {
            this.ctx.progressionAndSocial().refreshOpenMerchantPricesForHive(hiveId);
        }
    }

    public void onEffectsRemoved(LivingEntity entity, Collection<MobEffectInstance> removedEffects) {
        if (this.ctx.isSyncingEffects()
            || !OIMRConfig.isSyncEffectsEnabled()
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

        if (!OIMRConfig.isFullAffinity()) {
            List<ServerPlayer> alive = this.ctx.getAliveMembers(hiveId);
            for (MobEffectInstance removed : removedEffects) {
                if (MobEffects.INFESTED.equals(removed.getEffect())) {
                    hive.setInfestedChanceScale(player.getUUID(), 1.0D);
                    this.ctx.data().setDirty();
                }
                Holder<MobEffect> counterpart = OIMRMobEffects.getReplicatedCounterpart(removed.getEffect());
                Holder<MobEffect> fineCounterpart = OIMRMobEffects.getFineCounterpart(removed.getEffect());
                Set<Holder<MobEffect>> selfCoarse = this.ctx.replicatedCoarseEffects().get(player.getUUID());
                boolean removedWasReplicatedCoarse = selfCoarse != null && selfCoarse.remove(removed.getEffect());
                Holder<MobEffect> cleanupTarget = counterpart != null ? counterpart : fineCounterpart;
                if (cleanupTarget != null && !removedWasReplicatedCoarse) {
                    boolean anyStillHasOriginal = false;
                    for (ServerPlayer member : alive) {
                        if (member != player && member.hasEffect(removed.getEffect())) {
                            Set<Holder<MobEffect>> memberReplicated = this.ctx.replicatedCoarseEffects().get(member.getUUID());
                            if (memberReplicated == null || !memberReplicated.contains(removed.getEffect())) {
                                anyStillHasOriginal = true;
                                break;
                            }
                        }
                    }
                    if (!anyStillHasOriginal) {
                        this.ctx.setSyncingEffects(true);
                        try {
                            for (ServerPlayer other : alive) {
                                if (other != player) {
                                    if (other.hasEffect(cleanupTarget)) {
                                        other.removeEffect(cleanupTarget);
                                    }
                                    Set<Holder<MobEffect>> otherCoarse = this.ctx.replicatedCoarseEffects().get(other.getUUID());
                                    if (otherCoarse != null && otherCoarse.remove(removed.getEffect()) && other.hasEffect(removed.getEffect())) {
                                        other.removeEffect(removed.getEffect());
                                    }
                                }
                            }
                        } finally {
                            this.ctx.setSyncingEffects(false);
                        }
                    }
                }
            }
            syncTranslucencyIfChanged();
            return;
        }

        boolean heroRemoved = false;
        for (MobEffectInstance removed : removedEffects) {
            if (MobEffects.HERO_OF_THE_VILLAGE.equals(removed.getEffect())) {
                heroRemoved = true;
            }
            hive.removeEffect(removed.getEffect());
        }
        this.ctx.data().setDirty();

        this.ctx.setSyncingEffects(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == player) {
                    continue;
                }
                for (MobEffectInstance removed : removedEffects) {
                    other.removeEffect(removed.getEffect());
                }
            }
        } finally {
            this.ctx.setSyncingEffects(false);
        }
        if (heroRemoved) {
            this.ctx.progressionAndSocial().refreshOpenMerchantPricesForHive(hiveId);
        }
    }

    public boolean shouldApplyPeriodicEffectTick(LivingEntity entity, Holder<MobEffect> effectHolder) {
        if (!(entity instanceof ServerPlayer player) || !this.ctx.isAttached(player) || player.isDeadOrDying()) {
            return true;
        }
        boolean alwaysDeduplicated = effectHolder.is(MobEffects.SATURATION);
        if (!alwaysDeduplicated && (!OIMRConfig.isFullAffinity() || !OIMRConfig.isSyncEffectsEnabled())) {
            return true;
        }
        boolean isDotEffect = effectHolder.is(MobEffects.POISON) || effectHolder.is(MobEffects.WITHER);
        if (isDotEffect && !OIMRConfig.isDotDamageCapped1x()) {
            return true;
        }
        if (!alwaysDeduplicated
            && !isDotEffect
            && !effectHolder.is(MobEffects.REGENERATION)) {
            return true;
        }

        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;
        if (hive == null) {
            return true;
        }

        List<ServerPlayer> eligible = new ArrayList<>();
        for (ServerPlayer member : this.ctx.getAliveMembers(hiveId)) {
            if (this.ctx.isVitalHazardTarget(member) && member.hasEffect(effectHolder)) {
                eligible.add(member);
            }
        }
        if (eligible.isEmpty() || !this.ctx.isVitalHazardTarget(player)) {
            return false;
        }
        if (eligible.size() == 1) {
            return true;
        }
        return hive.isPrimaryEffectTicker(player, effectHolder, this.ctx.server().overworld().getGameTime(), eligible);
    }

    public int beginPeriodicEffectTick(LivingEntity target, Holder<MobEffect> effectHolder) {
        if (!(target instanceof ServerPlayer player) || !this.ctx.isAttached(player)) {
            return 0;
        }
        int flags = 0;
        if (effectHolder.is(MobEffects.POISON) || effectHolder.is(MobEffects.WITHER)) {
            if (this.ctx.getActivePeriodicDotEffect() == null) {
                this.ctx.setActivePeriodicDotEffect(effectHolder);
                flags |= 4;
            }
        }
        if (OIMRConfig.isFullAffinity() || !OIMRConfig.isSyncEffectsEnabled()) {
            return flags;
        }
        boolean isDotEffect = effectHolder.is(MobEffects.POISON) || effectHolder.is(MobEffects.WITHER);
        if ((!isDotEffect || OIMRConfig.isDotDamageCapped1x()) && !this.ctx.isSuppressAffinityVitalsCascade()) {
            this.ctx.setSuppressAffinityVitalsCascade(true);
            flags |= 1;
        }
        if (effectHolder.is(MobEffects.HUNGER) && !this.ctx.isApplyingUnscaledHungerEffect()) {
            this.ctx.setApplyingUnscaledHungerEffect(true);
            flags |= 2;
        }
        return flags;
    }

    public void endPeriodicEffectTick(int flags) {
        if ((flags & 1) != 0) {
            this.ctx.setSuppressAffinityVitalsCascade(false);
        }
        if ((flags & 2) != 0) {
            this.ctx.setApplyingUnscaledHungerEffect(false);
        }
        if ((flags & 4) != 0) {
            this.ctx.setActivePeriodicDotEffect(null);
        }
    }

    public double getInfestedChanceScale(LivingEntity entity) {
        if (OIMRConfig.isFullAffinity()
            || !OIMRConfig.isSyncEffectsEnabled()
            || !(entity instanceof ServerPlayer player)
            || !this.ctx.isAttached(player)) {
            return 1.0D;
        }
        HiveMind hive = this.ctx.getHiveForPlayer(player);
        return hive != null ? hive.getInfestedChanceScale(player.getUUID()) : 1.0D;
    }
}
