package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.iakee.oneinventorymanyregrets.client.ClientBondState;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Optional;

@Environment(EnvType.CLIENT)
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Unique
    private static final Identifier OIMR_HUNGER_SPRITE = Identifier.withDefaultNamespace("hud/food_full");
    @Unique
    private static final Identifier OIMR_EFFECTS_SPRITE = Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "tab/effects");
    @Unique
    private static final Identifier OIMR_DAMAGE_SPRITE = Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "tab/damage");
    @Unique
    private static final Identifier OIMR_HONEYCOMB_SPRITE = Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "tab/honeycomb");

    @Unique
    private static final int OIMR_GROUP_BAR_WIDTH = 2;
    @Unique
    private static final int OIMR_GROUP_BAR_SPACE = 3;
    @Unique
    private static final int OIMR_BOND_GAP = 6;
    @Unique
    private static final int OIMR_ICON_SIZE = 9;
    @Unique
    private static final int OIMR_ICON_TEXT_GAP = 1;
    @Unique
    private static final int OIMR_STAT_GAP = 4;
    @Unique
    private static final int OIMR_DEFAULT_GROUP_COLOR = 0xFF9E9E9E;
    @Unique
    private static final int OIMR_CANONICAL_GOLD_COLOR = 0xFFFFCA3A;
    @Unique
    private static final int[] OIMR_TEAM_FALLBACK_PALETTE = {
        0xFF55CDFC, // sky cyan
        0xFFF7A8B8, // coral rose
        0xFFA6E22E, // lime
        0xFFFFB347, // amber orange
        0xFFC792EA, // lavender
        0xFF4EC9B0, // teal
        0xFFFF6B6B, // crimson
        0xFFFFE66D  // warm yellow
    };

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean oimr$active;
    @Unique
    private int oimr$hungerPercent;
    @Unique
    private int oimr$effectsPercent;
    @Unique
    private int oimr$damagePercent;
    @Unique
    private boolean oimr$canonical;
    @Unique
    private int oimr$bondColumnWidth;
    @Unique
    private int oimr$extraRowWidth;
    @Unique
    private int oimr$cachedMaxNameWidth;
    @Unique
    private int oimr$cachedWidthForScore;
    @Unique
    private boolean oimr$cachedShowHead;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void oimr$prepareTabBondState(
        GuiGraphicsExtractor graphics,
        int screenWidth,
        Scoreboard scoreboard,
        @Nullable Objective displayObjective,
        CallbackInfo ci
    ) {
        boolean singleplayer = this.minecraft.getSingleplayerServer() != null;
        this.oimr$active = ClientBondState.isSynced() || singleplayer;
        if (!this.oimr$active) {
            this.oimr$bondColumnWidth = 0;
            this.oimr$extraRowWidth = 0;
            return;
        }

        if (ClientBondState.isSynced()) {
            this.oimr$hungerPercent = ClientBondState.getHungerPercent();
            this.oimr$effectsPercent = ClientBondState.getEffectsPercent();
            this.oimr$damagePercent = ClientBondState.getDamagePercent();
            this.oimr$canonical = ClientBondState.isCanonicalHiveMind();
        } else {
            this.oimr$hungerPercent = Math.max(0, Math.round(OIMRConfig.getHungerWeight() * 100.0F));
            int affinity = Math.max(OIMRConfig.MIN_AFFINITY, Math.min(OIMRConfig.MAX_AFFINITY, OIMRConfig.getAffinityPercent()));
            this.oimr$effectsPercent = OIMRConfig.isSyncEffectsEnabled() ? affinity : 0;
            this.oimr$damagePercent = affinity;
            this.oimr$canonical = this.oimr$hungerPercent == 100 && this.oimr$effectsPercent == 100 && affinity == 100;
        }

        Font font = this.minecraft.font;
        if (this.oimr$canonical) {
            this.oimr$bondColumnWidth = OIMR_ICON_SIZE + 2 + font.width("100%");
        } else {
            int hungerW = OIMR_ICON_SIZE + OIMR_ICON_TEXT_GAP + font.width(this.oimr$hungerPercent + "%");
            int effectsW = OIMR_ICON_SIZE + OIMR_ICON_TEXT_GAP + font.width(this.oimr$effectsPercent + "%");
            int damageW = OIMR_ICON_SIZE + OIMR_ICON_TEXT_GAP + font.width(this.oimr$damagePercent + "%");
            this.oimr$bondColumnWidth = hungerW + OIMR_STAT_GAP + effectsW + OIMR_STAT_GAP + damageW;
        }
        this.oimr$extraRowWidth = OIMR_GROUP_BAR_SPACE + OIMR_BOND_GAP + this.oimr$bondColumnWidth;
    }

    @ModifyArg(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"),
        index = 0
    )
    private int oimr$expandSlotWidthForBondColumn(
        int totalWidth,
        @Local(name = "cols") int cols,
        @Local(name = "maxNameWidth") int maxNameWidth,
        @Local(name = "widthForScore") int widthForScore,
        @Local(name = "showHead") boolean showHead
    ) {
        if (!this.oimr$active) {
            return totalWidth;
        }
        this.oimr$cachedMaxNameWidth = maxNameWidth;
        this.oimr$cachedWidthForScore = widthForScore;
        this.oimr$cachedShowHead = showHead;
        return totalWidth + cols * this.oimr$extraRowWidth;
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"
        )
    )
    private void oimr$shiftFaceForGroupBar(
        GuiGraphicsExtractor graphics,
        Identifier skinTexture,
        int x,
        int y,
        int size,
        boolean showHat,
        boolean flip,
        int color,
        Operation<Void> original
    ) {
        int shiftedX = this.oimr$active ? x + OIMR_GROUP_BAR_SPACE : x;
        original.call(graphics, skinTexture, shiftedX, y, size, showHat, flip, color);
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
        )
    )
    private void oimr$shiftNameForGroupBar(
        GuiGraphicsExtractor graphics,
        Font font,
        Component text,
        int x,
        int y,
        int color,
        Operation<Void> original
    ) {
        int shiftedX = this.oimr$active ? x + OIMR_GROUP_BAR_SPACE : x;
        original.call(graphics, font, text, shiftedX, y, color);
    }

    @ModifyArgs(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractTablistScore(Lnet/minecraft/world/scores/Objective;ILnet/minecraft/client/gui/components/PlayerTabOverlay$ScoreDisplayEntry;IILjava/util/UUID;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
        )
    )
    private void oimr$shiftScoreAfterBondColumn(Args args) {
        if (this.oimr$active) {
            args.set(3, (int) args.get(3) + this.oimr$extraRowWidth);
            args.set(4, (int) args.get(4) + this.oimr$extraRowWidth);
        }
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V"
        )
    )
    private void oimr$renderGroupBarAndBondColumn(
        PlayerTabOverlay instance,
        GuiGraphicsExtractor graphics,
        int slotWidth,
        int slotLeft,
        int yo,
        PlayerInfo info,
        Operation<Void> original
    ) {
        if (this.oimr$active) {
            int groupColor = oimr$resolveGroupColor(info);
            graphics.fill(slotLeft, yo, slotLeft + OIMR_GROUP_BAR_WIDTH, yo + 8, groupColor);

            if (info.getGameMode() != GameType.SPECTATOR) {
                int bondLeft = slotLeft + OIMR_GROUP_BAR_SPACE + (this.oimr$cachedShowHead ? 9 : 0) + this.oimr$cachedMaxNameWidth + OIMR_BOND_GAP;
                int bondRightLimit = slotLeft + slotWidth - 13 - (this.oimr$cachedWidthForScore > 0 ? this.oimr$cachedWidthForScore + 2 : 0);
                this.oimr$drawBondColumn(graphics, bondLeft, bondRightLimit, yo);
            }
        }
        original.call(instance, graphics, slotWidth, slotLeft, yo, info);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void oimr$clearTabBondState(
        GuiGraphicsExtractor graphics,
        int screenWidth,
        Scoreboard scoreboard,
        @Nullable Objective displayObjective,
        CallbackInfo ci
    ) {
        this.oimr$active = false;
    }

    @Unique
    private void oimr$drawBondColumn(GuiGraphicsExtractor graphics, int bondLeft, int bondRightLimit, int yo) {
        int available = bondRightLimit - bondLeft;
        if (available < OIMR_ICON_SIZE) {
            return;
        }
        Font font = this.minecraft.font;
        if (this.oimr$canonical) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OIMR_HONEYCOMB_SPRITE, bondLeft, yo, OIMR_ICON_SIZE, OIMR_ICON_SIZE);
            String label = "100%";
            int textWidth = font.width(label);
            if (OIMR_ICON_SIZE + 2 + textWidth <= available) {
                graphics.text(font, label, bondLeft + OIMR_ICON_SIZE + 2, yo, OIMR_CANONICAL_GOLD_COLOR);
            }
            return;
        }

        int cursorX = bondLeft;
        cursorX = oimr$drawStatEntry(graphics, font, OIMR_HUNGER_SPRITE, this.oimr$hungerPercent, cursorX, bondRightLimit, yo);
        if (cursorX < 0) {
            return;
        }
        cursorX += OIMR_STAT_GAP;
        cursorX = oimr$drawStatEntry(graphics, font, OIMR_EFFECTS_SPRITE, this.oimr$effectsPercent, cursorX, bondRightLimit, yo);
        if (cursorX < 0) {
            return;
        }
        cursorX += OIMR_STAT_GAP;
        oimr$drawStatEntry(graphics, font, OIMR_DAMAGE_SPRITE, this.oimr$damagePercent, cursorX, bondRightLimit, yo);
    }

    @Unique
    private static int oimr$drawStatEntry(
        GuiGraphicsExtractor graphics,
        Font font,
        Identifier sprite,
        int percent,
        int x,
        int rightLimit,
        int yo
    ) {
        String label = percent + "%";
        int entryWidth = OIMR_ICON_SIZE + OIMR_ICON_TEXT_GAP + font.width(label);
        if (x + entryWidth > rightLimit) {
            return -1;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, yo, OIMR_ICON_SIZE, OIMR_ICON_SIZE);
        graphics.text(font, label, x + OIMR_ICON_SIZE + OIMR_ICON_TEXT_GAP, yo, oimr$colorForPercent(percent));
        return x + entryWidth;
    }

    @Unique
    private static int oimr$colorForPercent(int percent) {
        if (percent > 100) {
            return 0xFF55FFFF;
        }
        if (percent == 100) {
            return 0xFF55FF55;
        }
        if (percent > 0) {
            return 0xFFFFFF55;
        }
        return 0xFFFF5555;
    }

    @Unique
    private static int oimr$resolveGroupColor(PlayerInfo info) {
        @Nullable PlayerTeam team = info.getTeam();
        int baseColor;
        if (team == null) {
            baseColor = OIMR_DEFAULT_GROUP_COLOR;
        } else {
            Optional<TeamColor> teamColor = team.getColor();
            if (teamColor.isPresent()) {
                baseColor = ARGB.opaque(teamColor.get().rgb());
            } else {
                int idx = Math.floorMod(team.getName().hashCode(), OIMR_TEAM_FALLBACK_PALETTE.length);
                baseColor = OIMR_TEAM_FALLBACK_PALETTE[idx];
            }
        }
        if (info.getGameMode() == GameType.SPECTATOR) {
            return (baseColor & 0x00FFFFFF) | 0x80000000;
        }
        return baseColor;
    }
}
