package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveEntityPortalAccess;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
}
