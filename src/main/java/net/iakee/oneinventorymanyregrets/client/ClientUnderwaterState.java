package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.UnderLiquidAmbientSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates whether vanilla underwater overlay, fog, and ambient sounds should activate
 * from a linked teammate being submerged.
 *
 * <p>Only activates when synced with a server running this mod so vanilla servers are unaffected.</p>
 */
@Environment(EnvType.CLIENT)
public final class ClientUnderwaterState {
    private ClientUnderwaterState() {
    }

    public static boolean isEligiblePlayer(@Nullable LocalPlayer player) {
        return player != null
            && !player.isRemoved()
            && !player.isSpectator()
            && !player.isDeadOrDying();
    }

    static boolean shouldApplySharedUnderwaterForFlags(boolean eligiblePlayer) {
        return eligiblePlayer
            && ClientBondState.isSynced()
            && ClientBondState.isUnderwaterMuffleEnabled()
            && ClientBondState.isHiveUnderwater();
    }

    static boolean isUnderwaterAudioActiveForFlags(boolean eligiblePlayer, boolean localUnderWater) {
        return eligiblePlayer && (localUnderWater || shouldApplySharedUnderwaterForFlags(true));
    }

    public static boolean shouldApplySharedUnderwater(@Nullable LocalPlayer player) {
        return shouldApplySharedUnderwaterForFlags(isEligiblePlayer(player));
    }

    public static boolean isUnderwaterAudioActive(@Nullable LocalPlayer player) {
        return isEligiblePlayer(player)
            && isUnderwaterAudioActiveForFlags(true, player.isUnderWater());
    }

    public static UnderLiquidAmbientSoundInstance createLoopSound(LocalPlayer player) {
        return new UnderLiquidAmbientSoundInstance(
            SoundEvents.AMBIENT_UNDERWATER_LOOP,
            player,
            ClientUnderwaterState::isUnderwaterAudioActive
        );
    }
}
