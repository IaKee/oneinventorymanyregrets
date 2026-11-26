package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.network.OIMRGroundSleepPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side tracking of horizontal orientations for players sleeping on the ground without a bed.
 */
@Environment(EnvType.CLIENT)
public final class ClientGroundSleepState {
    private static final Map<Integer, Direction> GROUND_SLEEP_ORIENTATIONS = new ConcurrentHashMap<>();

    private ClientGroundSleepState() {
    }

    public static void update(Minecraft minecraft, OIMRGroundSleepPayload payload) {
        if (payload.groundSleeping()) {
            GROUND_SLEEP_ORIENTATIONS.put(payload.entityId(), payload.orientation());
        } else {
            GROUND_SLEEP_ORIENTATIONS.remove(payload.entityId());
        }
        if (minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(payload.entityId());
            if (entity instanceof HiveLivingEntityAccess access) {
                access.oimr$setGroundSleeping(payload.groundSleeping(), payload.groundSleeping() ? payload.orientation() : null);
            }
        }
    }

    public static @Nullable Direction getOrientation(int entityId) {
        return GROUND_SLEEP_ORIENTATIONS.get(entityId);
    }

    public static void reset() {
        GROUND_SLEEP_ORIENTATIONS.clear();
    }
}
