package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Propagates transient HUD alpha multipliers to {@code GuiItemRenderState} instances during extraction.
 */
@Environment(EnvType.CLIENT)
public final class OIMRItemAlphaContext {
    private static final ThreadLocal<Float> CURRENT_ALPHA = ThreadLocal.withInitial(() -> 1.0F);

    private OIMRItemAlphaContext() {
    }

    public static float getCurrentAlpha() {
        return CURRENT_ALPHA.get();
    }

    public static void withAlpha(float alpha, Runnable action) {
        Float previous = CURRENT_ALPHA.get();
        CURRENT_ALPHA.set(Math.max(0.0F, Math.min(1.0F, alpha)));
        try {
            action.run();
        } finally {
            CURRENT_ALPHA.set(previous);
        }
    }
}
