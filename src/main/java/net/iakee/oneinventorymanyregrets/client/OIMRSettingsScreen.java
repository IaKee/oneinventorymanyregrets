package net.iakee.oneinventorymanyregrets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * In-game and title-screen settings UI for One Inventory, Many Regrets.
 */
@Environment(EnvType.CLIENT)
public class OIMRSettingsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("oimr.settings.title");
    private static final float SLIDER_MAX_WEIGHT = 5.0F;
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private MultiLineTextWidget affinityDamageIndicator;
    private CycleButton<Boolean> mainHandButton;
    private CycleButton<Boolean> wipeCreativeButton;

    public OIMRSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, TITLE);
    }

    @Override
    protected void addOptions() {
        // Section 1: Bond & Scaling
        this.list.addHeader(Component.translatable("oimr.settings.section.bond"));

        AffinitySlider affinitySlider = new AffinitySlider(310, 20, OIMRConfig.getAffinityPercent());
        affinitySlider.setTooltip(Tooltip.create(Component.translatable("oimr.settings.affinity.tooltip")));
        this.list.addBig(affinitySlider);

        this.affinityDamageIndicator = new MultiLineTextWidget(
            buildAffinityHeartIndicatorText(OIMRConfig.getAffinityPercent()),
            this.font
        ).setMaxWidth(310).setCentered(true);
        this.affinityDamageIndicator.setTooltip(Tooltip.create(Component.translatable("oimr.settings.affinity.heart_indicator.tooltip")));
        this.list.addBig(this.affinityDamageIndicator);

        WeightSlider hungerSlider = new WeightSlider(
            150,
            20,
            "oimr.settings.hunger_weight",
            OIMRConfig.getHungerWeight(),
            OIMRConfig::setHungerWeight
        );
        hungerSlider.setTooltip(Tooltip.create(Component.translatable("oimr.settings.hunger_weight.tooltip")));

        WeightSlider xpSlider = new WeightSlider(
            150,
            20,
            "oimr.settings.xp_weight",
            OIMRConfig.getXpWeight(),
            OIMRConfig::setXpWeight
        );
        xpSlider.setTooltip(Tooltip.create(Component.translatable("oimr.settings.xp_weight.tooltip")));
        this.list.addSmall(hungerSlider, xpSlider);

        // Section 2: Synced Inventory & Equipment
        this.list.addHeader(Component.translatable("oimr.settings.section.slots"));

        MultiLineTextWidget dupWarning = new MultiLineTextWidget(
            Component.translatable("oimr.settings.slots_duplication_warning").withStyle(ChatFormatting.YELLOW),
            this.font
        ).setMaxWidth(310).setCentered(true);
        dupWarning.setTooltip(Tooltip.create(Component.translatable("oimr.settings.slots_duplication_warning.tooltip")));
        this.list.addBig(dupWarning);

        boolean hotbarSynced = OIMRConfig.isSyncHotbarEnabled();
        CycleButton<Boolean> hotbarButton = CycleButton.onOffBuilder(hotbarSynced)
            .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.sync_hotbar.tooltip")))
            .create(
                0,
                0,
                310,
                20,
                Component.translatable("oimr.settings.sync_hotbar"),
                (button, value) -> {
                    applySetting(() -> OIMRConfig.setSyncHotbar(value));
                    updateMainHandButtonState(value);
                }
            );
        this.list.addBig(hotbarButton);

        this.mainHandButton = CycleButton.onOffBuilder(hotbarSynced || OIMRConfig.isSyncMainHandEnabled())
            .create(
                0,
                0,
                150,
                20,
                Component.translatable("oimr.settings.sync_main_hand"),
                (button, value) -> applySetting(() -> OIMRConfig.setSyncMainHand(value))
            );
        updateMainHandButtonState(hotbarSynced);

        CycleButton<Boolean> offHandButton = smallToggle(
            "oimr.settings.sync_off_hand",
            "oimr.settings.sync_off_hand.tooltip",
            OIMRConfig.isSyncOffHandEnabled(),
            OIMRConfig::setSyncOffHand
        );
        this.list.addSmall(this.mainHandButton, offHandButton);

        this.list.addSmall(
            smallToggle("oimr.settings.sync_helmet", "oimr.settings.sync_helmet.tooltip", OIMRConfig.isSyncHelmetEnabled(), OIMRConfig::setSyncHelmet),
            smallToggle("oimr.settings.sync_chestplate", "oimr.settings.sync_chestplate.tooltip", OIMRConfig.isSyncChestplateEnabled(), OIMRConfig::setSyncChestplate)
        );
        this.list.addSmall(
            smallToggle("oimr.settings.sync_leggings", "oimr.settings.sync_leggings.tooltip", OIMRConfig.isSyncLeggingsEnabled(), OIMRConfig::setSyncLeggings),
            smallToggle("oimr.settings.sync_boots", "oimr.settings.sync_boots.tooltip", OIMRConfig.isSyncBootsEnabled(), OIMRConfig::setSyncBoots)
        );

        // Section 3: Combat & Death Rules
        this.list.addHeader(Component.translatable("oimr.settings.section.combat"));

        this.list.addSmall(
            smallToggle("oimr.settings.shared_knockback", "oimr.settings.shared_knockback.tooltip", OIMRConfig.isSharedKnockbackEnabled(), OIMRConfig::setSharedKnockback),
            smallToggle("oimr.settings.friendly_pets", "oimr.settings.friendly_pets.tooltip", OIMRConfig.isFriendlyPetsEnabled(), OIMRConfig::setFriendlyPets)
        );

        boolean excludeCreative = OIMRConfig.isExcludeCreativeFromDeathEnabled();
        CycleButton<Boolean> excludeCreativeButton = CycleButton.onOffBuilder(excludeCreative)
            .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.exclude_creative.tooltip")))
            .create(
                0,
                0,
                150,
                20,
                Component.translatable("oimr.settings.exclude_creative"),
                (button, value) -> {
                    applySetting(() -> OIMRConfig.setExcludeCreativeFromDeath(value));
                    if (this.wipeCreativeButton != null) {
                        this.wipeCreativeButton.active = value;
                    }
                }
            );
        this.wipeCreativeButton = smallToggle(
            "oimr.settings.wipe_creative",
            "oimr.settings.wipe_creative.tooltip",
            OIMRConfig.isWipeCreativeInventoryOnDeathEnabled(),
            OIMRConfig::setWipeCreativeInventoryOnDeath
        );
        this.wipeCreativeButton.active = excludeCreative;
        this.list.addSmall(excludeCreativeButton, this.wipeCreativeButton);

        CycleButton<Boolean> comunismButton = CycleButton.onOffBuilder(OIMRConfig.isComunismEnabled())
            .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.comunism.tooltip")))
            .create(
                0,
                0,
                310,
                20,
                Component.translatable("oimr.settings.comunism"),
                (button, value) -> applySetting(() -> OIMRConfig.setComunism(value))
            );
        this.list.addBig(comunismButton);
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(
            Button.builder(Component.translatable("oimr.settings.reset"), button ->
                applySetting(OIMRConfig::resetToDefaults, () -> {
                    if (this.minecraft != null && this.minecraft.gui.screen() == this) {
                        this.rebuildWidgets();
                    }
                })
            ).width(150).build()
        );
        footer.addChild(
            Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(150)
                .build()
        );
    }

    private void updateMainHandButtonState(boolean hotbarSynced) {
        if (this.mainHandButton == null) {
            return;
        }
        if (hotbarSynced) {
            this.mainHandButton.setValue(true);
            this.mainHandButton.active = false;
            this.mainHandButton.setTooltip(Tooltip.create(Component.translatable("oimr.settings.sync_main_hand.coupled_tooltip")));
        } else {
            this.mainHandButton.setValue(OIMRConfig.isSyncMainHandEnabled());
            this.mainHandButton.active = true;
            this.mainHandButton.setTooltip(Tooltip.create(Component.translatable("oimr.settings.sync_main_hand.tooltip")));
        }
    }

    private CycleButton<Boolean> smallToggle(
        String labelKey,
        String tooltipKey,
        boolean initialValue,
        java.util.function.Consumer<Boolean> setter
    ) {
        return CycleButton.onOffBuilder(initialValue)
            .withTooltip(v -> Tooltip.create(Component.translatable(tooltipKey)))
            .create(
                0,
                0,
                150,
                20,
                Component.translatable(labelKey),
                (button, value) -> applySetting(() -> setter.accept(value))
            );
    }

    private void applySetting(Runnable action) {
        applySetting(action, null);
    }

    private void applySetting(Runnable action, @org.jspecify.annotations.Nullable Runnable afterOnClient) {
        IntegratedServer server = this.minecraft != null ? this.minecraft.getSingleplayerServer() : null;
        if (server != null) {
            server.execute(() -> {
                action.run();
                HiveMindManager.onConfigChanged(server);
                if (afterOnClient != null && this.minecraft != null) {
                    this.minecraft.execute(afterOnClient);
                }
            });
        } else {
            action.run();
            if (afterOnClient != null) {
                afterOnClient.run();
            }
        }
    }

    private final class AffinitySlider extends AbstractSliderButton {
        private int displayPercent;

        private AffinitySlider(int width, int height, int initialPercent) {
            super(
                0,
                0,
                width,
                height,
                Component.empty(),
                Mth.clamp(initialPercent / 100.0D, 0.0D, 1.0D)
            );
            this.displayPercent = Mth.clamp(initialPercent, OIMRConfig.MIN_AFFINITY, OIMRConfig.MAX_AFFINITY);
            this.updateMessage();
        }

        private int snappedPercent() {
            return Mth.clamp((int) Math.round(this.value * 100.0D), OIMRConfig.MIN_AFFINITY, OIMRConfig.MAX_AFFINITY);
        }

        private void updateAffinityDamageIndicator(int percent) {
            if (OIMRSettingsScreen.this.affinityDamageIndicator != null) {
                OIMRSettingsScreen.this.affinityDamageIndicator.setMessage(buildAffinityHeartIndicatorText(percent));
            }
        }

        @Override
        protected void updateMessage() {
            boolean full = this.displayPercent >= OIMRConfig.MAX_AFFINITY;
            Component deathBadge = Component.translatable(
                full ? "oimr.settings.affinity.linked_tag" : "oimr.settings.affinity.unlinked_tag"
            ).withStyle(full ? ChatFormatting.RED : ChatFormatting.GREEN);
            this.setMessage(Component.translatable("oimr.settings.affinity", this.displayPercent, deathBadge));
            updateAffinityDamageIndicator(this.displayPercent);
        }

        @Override
        protected void applyValue() {
            int percent = snappedPercent();
            this.displayPercent = percent;
            updateAffinityDamageIndicator(percent);
            applySetting(() -> OIMRConfig.setAffinityPercent(percent));
        }
    }

    private static Component buildAffinityHeartIndicatorText(int percent) {
        if (percent <= 0) {
            return Component.translatable("oimr.settings.affinity.heart_indicator.none")
                .withStyle(ChatFormatting.GRAY);
        }
        double requiredDamage = 200.0D / percent;
        double requiredHearts = 100.0D / percent;
        String damageText = NUMBER_FORMAT.format(requiredDamage);
        String heartsText = NUMBER_FORMAT.format(requiredHearts);
        String key = Math.abs(requiredHearts - 1.0D) < 1.0E-6D
            ? "oimr.settings.affinity.heart_indicator.singular"
            : "oimr.settings.affinity.heart_indicator";
        return Component.translatable(key, damageText, heartsText)
            .withStyle(ChatFormatting.GRAY);
    }

    private final class WeightSlider extends AbstractSliderButton {
        private final String labelKey;
        private final java.util.function.Consumer<Float> setter;
        private float displayWeight;

        private WeightSlider(
            int width,
            int height,
            String labelKey,
            float initialWeight,
            java.util.function.Consumer<Float> setter
        ) {
            super(
                0,
                0,
                width,
                height,
                Component.empty(),
                Mth.clamp(initialWeight / SLIDER_MAX_WEIGHT, 0.0D, 1.0D)
            );
            this.labelKey = labelKey;
            this.setter = setter;
            this.displayWeight = initialWeight;
            this.updateMessage();
        }

        private float snappedWeight() {
            return Math.round((float) (this.value * SLIDER_MAX_WEIGHT) * 10.0F) / 10.0F;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(this.labelKey, String.format("%.1f", this.displayWeight)));
        }

        @Override
        protected void applyValue() {
            float weight = snappedWeight();
            this.displayWeight = weight;
            applySetting(() -> this.setter.accept(weight));
        }
    }
}
