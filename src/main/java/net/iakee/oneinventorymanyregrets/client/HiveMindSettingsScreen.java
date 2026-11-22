package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Host-only in-game settings screen for One Inventory, Many Regrets.
 */
@Environment(EnvType.CLIENT)
public class HiveMindSettingsScreen extends Screen {
    private static final Component TITLE = Component.literal("Hive Mind Settings");
    private static final Component SHARED_KNOCKBACK_LABEL = Component.literal("Shared Knockback");
    private static final Tooltip SHARED_KNOCKBACK_TOOLTIP = Tooltip.create(
        Component.literal("Propagates knockback across the Hive Mind and applies opposite recoil when slapping a teammate.")
    );
    private static final Component COMUNISM_LABEL = Component.literal("Comunism");
    private static final Tooltip COMUNISM_TOOLTIP = Tooltip.create(
        Component.literal("Shows a collective socialism joke message when the Hive Mind dies of starvation.")
    );
    private static final Component FRIENDLY_PETS_LABEL = Component.literal("Friendly Pets");
    private static final Tooltip FRIENDLY_PETS_TOOLTIP = Tooltip.create(
        Component.literal("Prevents owned/trusted pets of Hive Mind members from becoming hostile toward teammates.")
    );
    private static final Component EXCLUDE_CREATIVE_LABEL = Component.literal("Ignore Creative on Death");
    private static final Tooltip EXCLUDE_CREATIVE_TOOLTIP = Tooltip.create(
        Component.literal("When enabled, teammates in Creative mode are not killed by cascade death.")
    );
    private static final Component WIPE_CREATIVE_INV_LABEL = Component.literal("Wipe Inventory (Creative)");
    private static final Tooltip WIPE_CREATIVE_INV_TOOLTIP = Tooltip.create(
        Component.literal("When Creative teammates survive cascade death, still wipe shared inventory, equipment, and XP.")
    );
    private static final Tooltip HUNGER_WEIGHT_TOOLTIP = Tooltip.create(
        Component.literal("Per-player hunger weight multiplier: (Weight * IndividualAction) / PartySize.")
    );
    private static final float SLIDER_MAX_HUNGER_WEIGHT = 5.0F;

    private final Screen lastScreen;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public HiveMindSettingsScreen(Screen lastScreen) {
        super(TITLE);
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.layout.addToHeader(new StringWidget(TITLE, this.font));

        LinearLayout contents = this.layout.addToContents(LinearLayout.vertical().spacing(5));
        contents.addChild(
            CycleButton.onOffBuilder(OIMRConfig.isSharedKnockbackEnabled())
                .withTooltip(value -> SHARED_KNOCKBACK_TOOLTIP)
                .create(0, 0, 240, 20, SHARED_KNOCKBACK_LABEL, (button, value) -> applySetting(() -> OIMRConfig.setSharedKnockback(value)))
        );
        contents.addChild(
            CycleButton.onOffBuilder(OIMRConfig.isComunismEnabled())
                .withTooltip(value -> COMUNISM_TOOLTIP)
                .create(0, 0, 240, 20, COMUNISM_LABEL, (button, value) -> applySetting(() -> OIMRConfig.setComunism(value)))
        );
        contents.addChild(
            CycleButton.onOffBuilder(OIMRConfig.isFriendlyPetsEnabled())
                .withTooltip(value -> FRIENDLY_PETS_TOOLTIP)
                .create(0, 0, 240, 20, FRIENDLY_PETS_LABEL, (button, value) -> applySetting(() -> OIMRConfig.setFriendlyPets(value)))
        );
        contents.addChild(
            CycleButton.onOffBuilder(OIMRConfig.isExcludeCreativeFromDeathEnabled())
                .withTooltip(value -> EXCLUDE_CREATIVE_TOOLTIP)
                .create(0, 0, 240, 20, EXCLUDE_CREATIVE_LABEL, (button, value) -> applySetting(() -> OIMRConfig.setExcludeCreativeFromDeath(value)))
        );
        contents.addChild(
            CycleButton.onOffBuilder(OIMRConfig.isWipeCreativeInventoryOnDeathEnabled())
                .withTooltip(value -> WIPE_CREATIVE_INV_TOOLTIP)
                .create(0, 0, 240, 20, WIPE_CREATIVE_INV_LABEL, (button, value) -> applySetting(() -> OIMRConfig.setWipeCreativeInventoryOnDeath(value)))
        );
        HungerWeightSlider hungerSlider = new HungerWeightSlider(240, 20, OIMRConfig.getHungerWeight());
        hungerSlider.setTooltip(HUNGER_WEIGHT_TOOLTIP);
        contents.addChild(hungerSlider);

        this.layout.addToFooter(
            Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(200)
                .build()
        );
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    private void applySetting(Runnable action) {
        IntegratedServer server = this.minecraft != null ? this.minecraft.getSingleplayerServer() : null;
        if (server != null) {
            server.execute(action);
        } else {
            action.run();
        }
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        }
    }

    private final class HungerWeightSlider extends AbstractSliderButton {
        private float displayWeight;

        private HungerWeightSlider(int width, int height, float initialWeight) {
            super(
                0,
                0,
                width,
                height,
                Component.empty(),
                Mth.clamp(initialWeight / SLIDER_MAX_HUNGER_WEIGHT, 0.0D, 1.0D)
            );
            this.displayWeight = initialWeight;
            this.updateMessage();
        }

        private float snappedWeight() {
            return Math.round((float) (this.value * SLIDER_MAX_HUNGER_WEIGHT) * 10.0F) / 10.0F;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format("Hunger Weight: %.1fx", this.displayWeight)));
        }

        @Override
        protected void applyValue() {
            float weight = snappedWeight();
            this.displayWeight = weight;
            applySetting(() -> OIMRConfig.setHungerWeight(weight));
        }
    }
}
