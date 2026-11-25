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

    public static boolean shouldApplySharedUnderwater(@Nullable LocalPlayer player) {
        return player != null
            && !player.isRemoved()
            && !player.isSpectator()
            && !player.isDeadOrDying()
            && ClientBondState.isSynced()
            && ClientBondState.isUnderwaterMuffleEnabled()
            && ClientBondState.isHiveUnderwater();
    }

    public static boolean isUnderwaterAudioActive(@Nullable LocalPlayer player) {
        return player != null
            && !player.isRemoved()
            && !player.isSpectator()
            && !player.isDeadOrDying()
            && (player.isUnderWater() || shouldApplySharedUnderwater(player));
    }

    public static UnderLiquidAmbientSoundInstance createLoopSound(LocalPlayer player) {
        return new UnderLiquidAmbientSoundInstance(
            SoundEvents.AMBIENT_UNDERWATER_LOOP,
            player,
            ClientUnderwaterState::isUnderwaterAudioActive
        );
    }
}
