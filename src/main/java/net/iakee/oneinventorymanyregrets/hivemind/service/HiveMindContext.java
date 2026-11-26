package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Full per-server coordination context implemented by {@code HiveMindCoordinator}.
 * Aggregates all narrow {@link HiveMindContexts} sub-interfaces.
 */
public interface HiveMindContext extends
    HiveMindContexts.TeamLifecycle,
    HiveMindContexts.InventoryAmmo,
    HiveMindContexts.EffectSync,
    HiveMindContexts.VitalsDamage,
    HiveMindContexts.EnvironmentSync,
    HiveMindContexts.ProgressionSocial,
    HiveMindContexts.DeathCascade,
    HiveMindContexts.TeleportAudio {

    double FRACTIONAL_EPSILON = HiveMindContexts.FRACTIONAL_EPSILON;

    Map<UUID, String> playerHives();

    Map<UUID, String> knownPlayerHives();

    Set<ServerPlayer> attachedPlayers();

    VitalsAndDamageService vitalsAndDamage();

    ProgressionAndSocialService progressionAndSocial();

    DeathCascadeService deathCascade();

    TeleportAndAudioService teleportAndAudio();
}
