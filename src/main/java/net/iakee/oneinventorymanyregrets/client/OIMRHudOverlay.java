package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Renders Minecraft-styled killfeed indicators above the health bar (damage) and hunger/breath bars (food & air).
 */
@Environment(EnvType.CLIENT)
public final class OIMRHudOverlay {
    public static final Identifier DAMAGE_FEED_ID = Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "damage_feed");
    public static final Identifier FOOD_FEED_ID = Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "food_feed");

    private static final Identifier FOOD_FULL_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier AIR_BUBBLE_SPRITE = Identifier.withDefaultNamespace("hud/air");

    private static final int ROW_HEIGHT = 11;
    private static final int ROW_GAP = 2;
    private static final float ROW_STEP = ROW_HEIGHT + ROW_GAP;
    private static final int BAR_GAP = 2;
    private static final int CREATIVE_BOTTOM_OFFSET = 32;
    private static final int ICON_SIZE = 9;
    private static final float ITEM_SCALE = 0.5625F; // 16px * 9/16 = 9px
    private static final int PADDING_X = 2;
    private static final int GAP_ICON_AMOUNT = 3;
    private static final int GAP_AMOUNT_NAME = 3;
    private static final int GAP_AMOUNT_DRUMSTICK = 1;

    private static final int COLOR_DAMAGE = 0xFF5555;
    private static final int COLOR_FOOD_GAIN = 0x55FF55;
    private static final int COLOR_FOOD_ZERO = 0xAAAAAA;
    private static final int COLOR_AIR_DRAIN = 0x55FFFF;
    private static final int COLOR_NAME = 0xFFFFFF;
    private static final float BACKDROP_OPACITY = 0.45F;

    private static final DecimalFormat NUMBER_FORMAT =
        new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private OIMRHudOverlay() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.HELD_ITEM_TOOLTIP,
            DAMAGE_FEED_ID,
            OIMRHudOverlay::extractDamageFeed
        );
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.HELD_ITEM_TOOLTIP,
            FOOD_FEED_ID,
            OIMRHudOverlay::extractRightFeed
        );
        HudStatusBarHeightRegistry.addLeft(DAMAGE_FEED_ID, player -> 0);
        HudStatusBarHeightRegistry.addRight(FOOD_FEED_ID, player -> 0);
    }

    private static void extractDamageFeed(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!canRenderFeed(mc)) {
            return;
        }
        List<ClientHudFeedState.DamageEntry> entries = ClientHudFeedState.snapshotDamageEntries(mc.isPaused());
        if (entries.isEmpty()) {
            return;
        }

        int baseBottomY = resolveBaseBottomY(mc, graphics, DAMAGE_FEED_ID);
        int leftX = graphics.guiWidth() / 2 - 91;
        Font font = mc.font;

        for (ClientHudFeedState.DamageEntry entry : entries) {
            float alpha = entry.alpha();
            int alphaByte = toAlphaByte(alpha);
            if (alphaByte < 4) {
                continue;
            }

            String amountText = formatDamageAmount(entry.amount());
            String playerName = entry.playerName();
            int amountWidth = font.width(amountText);
            int nameWidth = font.width(playerName);
            int totalWidth = PADDING_X + ICON_SIZE + GAP_ICON_AMOUNT + amountWidth + GAP_AMOUNT_NAME + nameWidth + PADDING_X;

            int rowBottomY = Math.round(baseBottomY + entry.verticalOffsetPx(ROW_STEP));
            int rowTopY = rowBottomY - ROW_HEIGHT;

            graphics.fill(leftX, rowTopY, leftX + totalWidth, rowBottomY, backdropColor(alpha));

            int cursorX = leftX + PADDING_X;
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                entry.iconType().spriteId(),
                cursorX,
                rowTopY + 1,
                ICON_SIZE,
                ICON_SIZE,
                withAlpha(alphaByte, 0xFFFFFF)
            );
            cursorX += ICON_SIZE + GAP_ICON_AMOUNT;

            graphics.text(font, amountText, cursorX, rowTopY + 2, withAlpha(alphaByte, COLOR_DAMAGE), true);
            cursorX += amountWidth + GAP_AMOUNT_NAME;

            graphics.text(font, playerName, cursorX, rowTopY + 2, withAlpha(alphaByte, COLOR_NAME), true);
        }
    }

    private static void extractRightFeed(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!canRenderFeed(mc)) {
            return;
        }
        List<ClientHudFeedState.FeedEntry> entries = ClientHudFeedState.snapshotRightEntries(mc.isPaused());
        if (entries.isEmpty()) {
            return;
        }

        int baseBottomY = resolveBaseBottomY(mc, graphics, FOOD_FEED_ID);
        int rightX = graphics.guiWidth() / 2 + 91;
        Font font = mc.font;

        for (ClientHudFeedState.FeedEntry entry : entries) {
            float alpha = entry.alpha();
            int alphaByte = toAlphaByte(alpha);
            if (alphaByte < 4) {
                continue;
            }
            if (entry instanceof ClientHudFeedState.FoodEntry foodEntry) {
                renderFoodRow(graphics, font, foodEntry, rightX, baseBottomY, alpha, alphaByte);
            } else if (entry instanceof ClientHudFeedState.AirEntry airEntry) {
                renderAirRow(graphics, font, airEntry, rightX, baseBottomY, alpha, alphaByte);
            }
        }
    }

    private static void renderFoodRow(
        GuiGraphicsExtractor graphics,
        Font font,
        ClientHudFeedState.FoodEntry entry,
        int rightX,
        int baseBottomY,
        float alpha,
        int alphaByte
    ) {
        String amountText = formatFoodAmount(entry.recoveredFoodPoints());
        String playerName = entry.playerName();
        int amountWidth = font.width(amountText);
        int nameWidth = font.width(playerName);
        int totalWidth = PADDING_X
            + ICON_SIZE
            + GAP_ICON_AMOUNT
            + amountWidth
            + GAP_AMOUNT_DRUMSTICK
            + ICON_SIZE
            + GAP_AMOUNT_NAME
            + nameWidth
            + PADDING_X;

        int rowBottomY = Math.round(baseBottomY + entry.verticalOffsetPx(ROW_STEP));
        int rowTopY = rowBottomY - ROW_HEIGHT;
        int rowLeftX = rightX - totalWidth;

        graphics.fill(rowLeftX, rowTopY, rightX, rowBottomY, backdropColor(alpha));

        int cursorX = rowLeftX + PADDING_X;
        int itemX = cursorX;
        int itemY = rowTopY + 1;
        OIMRItemAlphaContext.withAlpha(alpha, () -> {
            graphics.pose().pushMatrix();
            graphics.pose().translate(itemX, itemY);
            graphics.pose().scale(ITEM_SCALE, ITEM_SCALE);
            graphics.item(entry.foodItem(), 0, 0);
            graphics.pose().popMatrix();
        });
        cursorX += ICON_SIZE + GAP_ICON_AMOUNT;

        int amountRgb = entry.recoveredFoodPoints() > 0 ? COLOR_FOOD_GAIN : COLOR_FOOD_ZERO;
        graphics.text(font, amountText, cursorX, rowTopY + 2, withAlpha(alphaByte, amountRgb), true);
        cursorX += amountWidth + GAP_AMOUNT_DRUMSTICK;

        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            FOOD_FULL_SPRITE,
            cursorX,
            rowTopY + 1,
            ICON_SIZE,
            ICON_SIZE,
            withAlpha(alphaByte, 0xFFFFFF)
        );
        cursorX += ICON_SIZE + GAP_AMOUNT_NAME;

        graphics.text(font, playerName, cursorX, rowTopY + 2, withAlpha(alphaByte, COLOR_NAME), true);
    }

    private static void renderAirRow(
        GuiGraphicsExtractor graphics,
        Font font,
        ClientHudFeedState.AirEntry entry,
        int rightX,
        int baseBottomY,
        float alpha,
        int alphaByte
    ) {
        String amountText = "-" + entry.drainedBubbles();
        String playerName = entry.playerName();
        int amountWidth = font.width(amountText);
        int nameWidth = font.width(playerName);
        int totalWidth = PADDING_X + ICON_SIZE + GAP_ICON_AMOUNT + amountWidth + GAP_AMOUNT_NAME + nameWidth + PADDING_X;

        int rowBottomY = Math.round(baseBottomY + entry.verticalOffsetPx(ROW_STEP));
        int rowTopY = rowBottomY - ROW_HEIGHT;
        int rowLeftX = rightX - totalWidth;

        graphics.fill(rowLeftX, rowTopY, rightX, rowBottomY, backdropColor(alpha));

        int cursorX = rowLeftX + PADDING_X;
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            AIR_BUBBLE_SPRITE,
            cursorX,
            rowTopY + 1,
            ICON_SIZE,
            ICON_SIZE,
            withAlpha(alphaByte, 0xFFFFFF)
        );
        cursorX += ICON_SIZE + GAP_ICON_AMOUNT;

        graphics.text(font, amountText, cursorX, rowTopY + 2, withAlpha(alphaByte, COLOR_AIR_DRAIN), true);
        cursorX += amountWidth + GAP_AMOUNT_NAME;

        graphics.text(font, playerName, cursorX, rowTopY + 2, withAlpha(alphaByte, COLOR_NAME), true);
    }

    private static boolean canRenderFeed(Minecraft mc) {
        return !mc.gui.hud.isHidden()
            && mc.level != null
            && mc.player != null
            && mc.getCameraEntity() instanceof Player;
    }

    private static int resolveBaseBottomY(Minecraft mc, GuiGraphicsExtractor graphics, Identifier heightId) {
        if (mc.gameMode != null && mc.gameMode.canHurtPlayer()) {
            int statusBarHeight = HudStatusBarHeightRegistry.getHeight(heightId);
            return graphics.guiHeight() - statusBarHeight - BAR_GAP;
        }
        return graphics.guiHeight() - CREATIVE_BOTTOM_OFFSET;
    }

    public static String formatDamageAmount(float amount) {
        if (!Float.isFinite(amount) || amount >= 10000.0F) {
            return "-999+";
        }
        float display = (amount > 0.0F && amount < 0.1F) ? 0.1F : amount;
        return "-" + NUMBER_FORMAT.format(display);
    }

    public static String formatFoodAmount(int recoveredFoodPoints) {
        float drumsticks = Math.max(0, recoveredFoodPoints) / 2.0F;
        return "+" + NUMBER_FORMAT.format(drumsticks);
    }

    private static int toAlphaByte(float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
    }

    private static int withAlpha(int alphaByte, int rgb) {
        return (alphaByte << 24) | (rgb & 0x00FFFFFF);
    }

    private static int backdropColor(float alpha) {
        int bgAlpha = Math.max(0, Math.min(255, Math.round(alpha * BACKDROP_OPACITY * 255.0F)));
        return bgAlpha << 24;
    }
}
