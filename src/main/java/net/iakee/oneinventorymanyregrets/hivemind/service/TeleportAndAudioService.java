package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveEntityPortalAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coordinates shared Ender Pearl / Portal teleports and replicated directional mob sounds.
 */
public final class TeleportAndAudioService {
    private final HiveMindContexts.TeleportAudio ctx;

    public TeleportAndAudioService(HiveMindContexts.TeleportAudio ctx) {
        this.ctx = ctx;
    }

    public void onEnderPearlTeleport(ServerPlayer source, ServerLevel targetLevel, Vec3 targetPos) {
        if (this.ctx.isSyncingGroupTeleport()
            || this.ctx.isCascadingDeath()
            || !OIMRConfig.isSharedEnderPearlEnabled()
            || !this.ctx.isAttached(source)
            || source.isDeadOrDying()
            || source.isSpectator()) {
            return;
        }
        teleportHiveMembersTogether(source, targetLevel, targetPos, false, TeleportTransition.DO_NOTHING);
    }

    public void onPortalTeleport(ServerPlayer source, TeleportTransition sourceTransition) {
        if (this.ctx.isSyncingGroupTeleport()
            || this.ctx.isCascadingDeath()
            || !OIMRConfig.isSharedPortalsEnabled()
            || !this.ctx.isAttached(source)
            || source.isDeadOrDying()
            || source.isSpectator()) {
            return;
        }
        ServerLevel targetLevel = source.level();
        Vec3 targetPos = source.position();
        TeleportTransition.PostTeleportTransition post =
            TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
        teleportHiveMembersTogether(source, targetLevel, targetPos, true, post);
    }

    public void onEndCreditsPortalTriggered(ServerPlayer source) {
        if (this.ctx.isSyncingGroupTeleport()
            || this.ctx.isCascadingDeath()
            || !OIMRConfig.isSharedPortalsEnabled()
            || !this.ctx.isAttached(source)
            || source.isDeadOrDying()
            || source.isSpectator()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        if (hiveId == null) {
            return;
        }

        this.ctx.setSyncingGroupTeleport(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == source
                    || other.isRemoved()
                    || other.isDeadOrDying()
                    || other.isSpectator()
                    || other.wonGame
                    || other.connection == null
                    || !other.connection.isAcceptingMessages()) {
                    continue;
                }
                if (other.level().dimension() == Level.END) {
                    if (other.isSleeping()) {
                        other.stopSleeping();
                    }
                    ((HiveEntityPortalAccess) other).oimr$setPortalProcess(null);
                    other.setPortalCooldown();
                    if (!other.seenCredits) {
                        other.showEndCredits();
                    } else {
                        TeleportTransition respawn = other.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
                        ServerPlayer teleported = other.teleport(respawn);
                        if (teleported != null) {
                            teleported.resetFallDistance();
                            teleported.resetCurrentImpulseContext();
                        }
                    }
                }
            }
        } finally {
            this.ctx.setSyncingGroupTeleport(false);
        }
    }

    private void teleportHiveMembersTogether(
        ServerPlayer source,
        ServerLevel targetLevel,
        Vec3 targetPos,
        boolean portalCooldown,
        TeleportTransition.PostTeleportTransition postTransition
    ) {
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        if (hiveId == null) {
            return;
        }

        this.ctx.setSyncingGroupTeleport(true);
        try {
            for (ServerPlayer other : this.ctx.getAliveMembers(hiveId)) {
                if (other == source
                    || other.isRemoved()
                    || other.isDeadOrDying()
                    || other.isSpectator()
                    || other.connection == null
                    || !other.connection.isAcceptingMessages()) {
                    continue;
                }
                if (other.isSleeping()) {
                    other.stopSleeping();
                }
                if (portalCooldown) {
                    ((HiveEntityPortalAccess) other).oimr$setPortalProcess(null);
                    other.setPortalCooldown();
                }
                TeleportTransition transition = new TeleportTransition(
                    targetLevel,
                    targetPos,
                    Vec3.ZERO,
                    other.getYRot(),
                    other.getXRot(),
                    Set.of(),
                    postTransition
                );
                ServerPlayer teleported = other.teleport(transition);
                if (teleported != null) {
                    teleported.resetFallDistance();
                    teleported.resetCurrentImpulseContext();
                }
            }
        } finally {
            this.ctx.setSyncingGroupTeleport(false);
        }
    }

    public void pushActiveMobSoundSource(Mob mob) {
        this.ctx.activeMobSoundSources().add(mob);
    }

    public void popActiveMobSoundSource() {
        List<Mob> stack = this.ctx.activeMobSoundSources();
        if (!stack.isEmpty()) {
            stack.removeLast();
        }
    }

    public @Nullable Mob peekActiveMobSoundSource() {
        List<Mob> stack = this.ctx.activeMobSoundSources();
        return stack.isEmpty() ? null : stack.getLast();
    }

    public void onMobSoundEmitted(
        ServerLevel level,
        @Nullable Entity except,
        Vec3 mobPos,
        Holder<SoundEvent> sound,
        SoundSource source,
        float volume,
        float pitch,
        long seed
    ) {
        if (!OIMRConfig.isSharedMobSoundsEnabled()
            || !OIMRConfig.getSharedMobSoundFilter().matches(source)
            || volume <= 0.0F) {
            return;
        }
        float volumeFactor = OIMRConfig.getSharedMobSoundVolumeFactor();
        if (volumeFactor <= 0.0F) {
            return;
        }
        float affinityFactor = OIMRConfig.getAffinityFactor();
        if (affinityFactor <= 0.0F) {
            return;
        }
        if (this.ctx.getActiveHiveIds().isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime != this.ctx.getRecentMobSoundGameTime()) {
            this.ctx.setRecentMobSoundGameTime(gameTime);
            this.ctx.recentMobSounds().clear();
        }

        float range = sound.value().getRange(volume);
        double rangeSq = (double) range * (double) range;
        float configuredVolume = volume * volumeFactor;
        float replicatedVolume = configuredVolume * affinityFactor;
        if (replicatedVolume <= 0.0F) {
            return;
        }
        float replicatedRange = sound.value().getRange(replicatedVolume);

        for (String hiveId : this.ctx.getActiveHiveIds()) {
            List<ServerPlayer> members = this.ctx.getOnlineMembers(hiveId);
            int groupSize = members.size();
            if (groupSize <= 1) {
                continue;
            }

            ServerPlayer closestOriginalListener = null;
            double closestDistSq = Double.MAX_VALUE;
            List<ServerPlayer> recipients = null;

            for (ServerPlayer member : members) {
                if (member.isDeadOrDying() || member.isSpectator()) {
                    continue;
                }
                boolean inNativeRange = member != except
                    && member.level() == level
                    && member.position().distanceToSqr(mobPos) < rangeSq;
                if (inNativeRange) {
                    double distSq = member.position().distanceToSqr(mobPos);
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closestOriginalListener = member;
                    }
                } else if (member != except && member.connection != null) {
                    if (recipients == null) {
                        recipients = new ArrayList<>();
                    }
                    recipients.add(member);
                }
            }

            if (closestOriginalListener == null || recipients == null || recipients.isEmpty()) {
                continue;
            }
            if (!this.ctx.recentMobSounds().add(new HiveMindContexts.MobSoundDedupKey(hiveId, sound.value(), source, seed))) {
                continue;
            }
            if (ThreadLocalRandom.current().nextInt(groupSize) != 0) {
                continue;
            }

            double emissionDistance = Math.sqrt(closestDistSq);
            double replicatedDistance = (range > 0.0F && replicatedRange < range)
                ? emissionDistance * ((double) replicatedRange / (double) range)
                : emissionDistance;
            Vec3 fallbackOffset = mobPos.subtract(closestOriginalListener.position());
            double fallbackLen = fallbackOffset.length();
            if (fallbackLen > 1.0E-5D && Math.abs(replicatedDistance - emissionDistance) > 1.0E-6D) {
                fallbackOffset = fallbackOffset.scale(replicatedDistance / fallbackLen);
            }

            for (ServerPlayer recipient : recipients) {
                Vec3 repPos;
                if (recipient.level() == level) {
                    Vec3 toMob = mobPos.subtract(recipient.position());
                    double toMobLen = toMob.length();
                    if (toMobLen > 1.0E-5D) {
                        repPos = recipient.position().add(toMob.scale(replicatedDistance / toMobLen));
                    } else {
                        repPos = recipient.position().add(fallbackOffset);
                    }
                } else {
                    repPos = recipient.position().add(fallbackOffset);
                }

                recipient.connection.send(new ClientboundSoundPacket(
                    sound,
                    source,
                    repPos.x,
                    repPos.y,
                    repPos.z,
                    replicatedVolume,
                    pitch,
                    seed
                ));
            }
        }
    }

    public void beginItemBreakHook() {
        this.ctx.itemBreakHandledStack().add(Boolean.FALSE);
    }

    public void onPlayerEquippedItemBroken(ServerPlayer source, ItemStack brokenItem, EquipmentSlot inSlot) {
        List<Boolean> stack = this.ctx.itemBreakHandledStack();
        if (!stack.isEmpty()) {
            stack.set(stack.size() - 1, Boolean.TRUE);
        }
        propagateItemBreakSound(source, brokenItem, true);
    }

    public void endItemBreakHook(ServerPlayer source, ItemStack brokenItem) {
        List<Boolean> stack = this.ctx.itemBreakHandledStack();
        boolean handledByEntityEvent = false;
        if (!stack.isEmpty()) {
            handledByEntityEvent = Boolean.TRUE.equals(stack.removeLast());
        }
        if (!handledByEntityEvent) {
            propagateItemBreakSound(source, brokenItem, false);
        }
    }

    public void propagateItemBreakSound(ServerPlayer source, ItemStack brokenItem, boolean broadcastViaEntityEvent) {
        if (!this.ctx.isAttached(source) || source.isSilent() || brokenItem.isEmpty()) {
            return;
        }
        Holder<SoundEvent> breakSound = brokenItem.get(DataComponents.BREAK_SOUND);
        if (breakSound == null) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        if (hiveId == null) {
            return;
        }

        float volume = 0.8F;
        float pitch = 0.8F + source.getRandom().nextFloat() * 0.4F;
        long seed = source.getRandom().nextLong();
        float range = breakSound.value().getRange(volume);
        double rangeSq = (double) range * (double) range;

        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            if (member.isRemoved()
                || member.isDeadOrDying()
                || member.isSpectator()
                || member.connection == null) {
                continue;
            }
            if (broadcastViaEntityEvent) {
                if (member == source) {
                    continue;
                }
                if (member.level() == source.level()
                    && member.position().distanceToSqr(source.position()) < rangeSq) {
                    continue;
                }
            }
            member.connection.send(new ClientboundSoundPacket(
                breakSound,
                source.getSoundSource(),
                member.getX(),
                member.getY(),
                member.getZ(),
                volume,
                pitch,
                seed
            ));
        }
    }

    public void onCaveSoundEmitted(
        ServerPlayer source,
        Identifier soundId,
        double offsetX,
        double offsetY,
        double offsetZ,
        long seed
    ) {
        if (!OIMRConfig.isSharedCaveSoundsEnabled()
            || !this.ctx.isAttached(source)
            || source.isRemoved()
            || source.isDeadOrDying()
            || source.isSpectator()) {
            return;
        }
        if (!isAllowedCaveMoodSound(source, soundId)) {
            return;
        }
        Optional<Holder.Reference<SoundEvent>> soundHolderOpt = BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (soundHolderOpt.isEmpty()) {
            return;
        }
        String hiveId = this.ctx.getAttachedHiveId(source.getUUID());
        if (hiveId == null) {
            return;
        }

        long gameTime = source.level().getGameTime();
        Long lastGameTime = this.ctx.lastCaveSoundGameTimes().get(source.getUUID());
        if (lastGameTime != null && gameTime >= lastGameTime && (gameTime - lastGameTime) < 20L) {
            return;
        }
        this.ctx.lastCaveSoundGameTimes().put(source.getUUID(), gameTime);

        double dx = clampSoundOffset(offsetX);
        double dy = clampSoundOffset(offsetY);
        double dz = clampSoundOffset(offsetZ);
        Holder<SoundEvent> soundHolder = soundHolderOpt.get();

        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            if (member == source
                || member.isRemoved()
                || member.isDeadOrDying()
                || member.isSpectator()
                || member.connection == null) {
                continue;
            }
            member.connection.send(new ClientboundSoundPacket(
                soundHolder,
                SoundSource.AMBIENT,
                member.getX() + dx,
                member.getEyeY() + dy,
                member.getZ() + dz,
                1.0F,
                1.0F,
                seed
            ));
        }
    }

    private static double clampSoundOffset(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(-32.0D, Math.min(32.0D, value));
    }

    private static boolean isAllowedCaveMoodSound(ServerPlayer source, Identifier soundId) {
        if (soundId.equals(SoundEvents.AMBIENT_CAVE.value().location())
            || soundId.equals(SoundEvents.AMBIENT_NETHER_WASTES_MOOD.value().location())
            || soundId.equals(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value().location())
            || soundId.equals(SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value().location())
            || soundId.equals(SoundEvents.AMBIENT_CRIMSON_FOREST_MOOD.value().location())
            || soundId.equals(SoundEvents.AMBIENT_WARPED_FOREST_MOOD.value().location())) {
            return true;
        }
        return source.level()
            .environmentAttributes()
            .getValue(EnvironmentAttributes.AMBIENT_SOUNDS, source.position())
            .mood()
            .map(mood -> mood.soundEvent().value().location().equals(soundId))
            .orElse(false);
    }
}
