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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

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
    private CycleButton<OIMRConfig.MobSoundFilter> mobSoundFilterButton;
    private MobSoundVolumeSlider mobSoundVolumeSlider;

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

        boolean isHostMenu = this.minecraft == null
            || this.minecraft.level == null
            || this.minecraft.getSingleplayerServer() != null;
        if (isHostMenu) {
            CycleButton<Boolean> syncEffectsButton = CycleButton.onOffBuilder(OIMRConfig.isSyncEffectsEnabled())
                .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.sync_effects.tooltip")))
                .create(
                    0,
                    0,
                    310,
                    20,
                    Component.translatable("oimr.settings.sync_effects"),
                    (button, value) -> applyBooleanSetting(OIMRConfig.KEY_SYNC_EFFECTS, value)
                );
            this.list.addBig(syncEffectsButton);
        }

        // Section 2: Synced Inventory & Equipment
        this.list.addHeader(Component.translatable("oimr.settings.section.slots"));

        MultiLineTextWidget dupWarning = new MultiLineTextWidget(
            Component.translatable("oimr.settings.slots_duplication_warning").withStyle(ChatFormatting.GRAY),
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
                    applyBooleanSetting(OIMRConfig.KEY_SYNC_HOTBAR, value);
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
                (button, value) -> applyBooleanSetting(OIMRConfig.KEY_SYNC_MAIN_HAND, value)
            );
        updateMainHandButtonState(hotbarSynced);

        CycleButton<Boolean> offHandButton = smallToggle(
            "oimr.settings.sync_off_hand",
            "oimr.settings.sync_off_hand.tooltip",
            OIMRConfig.isSyncOffHandEnabled(),
            OIMRConfig.KEY_SYNC_OFF_HAND
        );
        this.list.addSmall(this.mainHandButton, offHandButton);

        this.list.addSmall(
            smallToggle("oimr.settings.sync_helmet", "oimr.settings.sync_helmet.tooltip", OIMRConfig.isSyncHelmetEnabled(), OIMRConfig.KEY_SYNC_HELMET),
            smallToggle("oimr.settings.sync_chestplate", "oimr.settings.sync_chestplate.tooltip", OIMRConfig.isSyncChestplateEnabled(), OIMRConfig.KEY_SYNC_CHESTPLATE)
        );
        this.list.addSmall(
            smallToggle("oimr.settings.sync_leggings", "oimr.settings.sync_leggings.tooltip", OIMRConfig.isSyncLeggingsEnabled(), OIMRConfig.KEY_SYNC_LEGGINGS),
            smallToggle("oimr.settings.sync_boots", "oimr.settings.sync_boots.tooltip", OIMRConfig.isSyncBootsEnabled(), OIMRConfig.KEY_SYNC_BOOTS)
        );

        // Section 3: Combat & Death Rules
        this.list.addHeader(Component.translatable("oimr.settings.section.combat"));

        this.list.addSmall(
            smallToggle("oimr.settings.shared_knockback", "oimr.settings.shared_knockback.tooltip", OIMRConfig.isSharedKnockbackEnabled(), OIMRConfig.KEY_SHARED_KNOCKBACK),
            smallToggle("oimr.settings.friendly_pets", "oimr.settings.friendly_pets.tooltip", OIMRConfig.isFriendlyPetsEnabled(), OIMRConfig.KEY_FRIENDLY_PETS)
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
                    applyBooleanSetting(OIMRConfig.KEY_EXCLUDE_CREATIVE_FROM_DEATH, value);
                    if (this.wipeCreativeButton != null) {
                        this.wipeCreativeButton.active = value;
                    }
                }
            );
        this.wipeCreativeButton = smallToggle(
            "oimr.settings.wipe_creative",
            "oimr.settings.wipe_creative.tooltip",
            OIMRConfig.isWipeCreativeInventoryOnDeathEnabled(),
            OIMRConfig.KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH
        );
        this.wipeCreativeButton.active = excludeCreative;
        this.list.addSmall(excludeCreativeButton, this.wipeCreativeButton);

        CycleButton<Boolean> randomDeathLootButton = CycleButton.onOffBuilder(OIMRConfig.isRandomDeathLootEnabled())
            .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.random_death_loot.tooltip")))
            .create(
                0,
                0,
                310,
                20,
                Component.translatable("oimr.settings.random_death_loot"),
                (button, value) -> applyBooleanSetting(OIMRConfig.KEY_RANDOM_DEATH_LOOT, value)
            );
        this.list.addBig(randomDeathLootButton);

        CycleButton<OIMRConfig.MultiMemberScalingMode> lavaModeButton = smallScalingModeCycle(
            "oimr.settings.lava_damage_mode",
            "oimr.settings.lava_damage_mode.tooltip",
            OIMRConfig.getLavaDamageMode(),
            OIMRConfig::setLavaDamageMode
        );
        CycleButton<OIMRConfig.MultiMemberScalingMode> dotModeButton = smallScalingModeCycle(
            "oimr.settings.dot_damage_mode",
            "oimr.settings.dot_damage_mode.tooltip",
            OIMRConfig.getDotDamageMode(),
            OIMRConfig::setDotDamageMode
        );
        this.list.addSmall(lavaModeButton, dotModeButton);

        CycleButton<OIMRConfig.MultiMemberScalingMode> underwaterBreathModeButton = CycleButton.<OIMRConfig.MultiMemberScalingMode>builder(
                value -> Component.translatable(value.translationKey()),
                OIMRConfig.getUnderwaterBreathMode()
            )
            .withValues(OIMRConfig.MultiMemberScalingMode.values())
            .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.underwater_breath_mode.tooltip")))
            .create(
                0,
                0,
                310,
                20,
                Component.translatable("oimr.settings.underwater_breath_mode"),
                (button, value) -> applySetting(() -> OIMRConfig.setUnderwaterBreathMode(value))
            );
        this.list.addBig(underwaterBreathModeButton);

        CycleButton<Boolean> comunismButton = CycleButton.onOffBuilder(OIMRConfig.isComunismEnabled())
            .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.comunism.tooltip")))
            .create(
                0,
                0,
                310,
                20,
                Component.translatable("oimr.settings.comunism"),
                (button, value) -> applyBooleanSetting(OIMRConfig.KEY_COMUNISM, value)
            );
        this.list.addBig(comunismButton);

        // Section 4: Teleport & Sensory
        this.list.addHeader(Component.translatable("oimr.settings.section.world_audio"));

        this.list.addSmall(
            smallToggle("oimr.settings.shared_ender_pearl", "oimr.settings.shared_ender_pearl.tooltip", OIMRConfig.isSharedEnderPearlEnabled(), OIMRConfig.KEY_SHARED_ENDER_PEARL),
            smallToggle("oimr.settings.shared_portals", "oimr.settings.shared_portals.tooltip", OIMRConfig.isSharedPortalsEnabled(), OIMRConfig.KEY_SHARED_PORTALS)
        );
        boolean sharedMobSounds = OIMRConfig.isSharedMobSoundsEnabled();
        OIMRConfig.MobSoundFilter initialMobSoundFilter = OIMRConfig.getSharedMobSoundFilter();
        CycleButton<Boolean> sharedMobSoundsButton = CycleButton.onOffBuilder(sharedMobSounds)
            .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.shared_mob_sounds.tooltip")))
            .create(
                0,
                0,
                150,
                20,
                Component.translatable("oimr.settings.shared_mob_sounds"),
                (button, value) -> {
                    applyBooleanSetting(OIMRConfig.KEY_SHARED_MOB_SOUNDS, value);
                    OIMRConfig.MobSoundFilter currentFilter = this.mobSoundFilterButton != null
                        ? this.mobSoundFilterButton.getValue()
                        : OIMRConfig.getSharedMobSoundFilter();
                    updateMobSoundControlsState(value, currentFilter);
                }
            );
        this.list.addSmall(
            smallToggle("oimr.settings.underwater_muffle", "oimr.settings.underwater_muffle.tooltip", OIMRConfig.isUnderwaterMuffleEnabled(), OIMRConfig.KEY_UNDERWATER_MUFFLE),
            sharedMobSoundsButton
        );

        this.mobSoundFilterButton = CycleButton.<OIMRConfig.MobSoundFilter>builder(
                value -> Component.translatable(value.translationKey()),
                initialMobSoundFilter
            )
            .withValues(OIMRConfig.MobSoundFilter.values())
            .withTooltip(v -> Tooltip.create(Component.translatable("oimr.settings.shared_mob_sound_filter.tooltip")))
            .create(
                0,
                0,
                310,
                20,
                Component.translatable("oimr.settings.shared_mob_sound_filter"),
                (button, value) -> {
                    applySetting(() -> OIMRConfig.setSharedMobSoundFilter(value));
                    updateMobSoundControlsState(sharedMobSoundsButton.getValue(), value);
                }
            );
        this.list.addBig(this.mobSoundFilterButton);

        this.mobSoundVolumeSlider = new MobSoundVolumeSlider(310, 20, OIMRConfig.getSharedMobSoundVolumePercent());
        this.mobSoundVolumeSlider.setTooltip(Tooltip.create(Component.translatable("oimr.settings.shared_mob_sound_volume.tooltip")));
        this.list.addBig(this.mobSoundVolumeSlider);

        updateMobSoundControlsState(sharedMobSounds, initialMobSoundFilter);
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(
            Button.builder(Component.translatable("oimr.settings.reset"), button ->
                applyServerOrLocal(
                    (server, host) -> HiveMindManager.resetConfigToDefaults(server, host),
                    OIMRConfig::resetToDefaults,
                    () -> {
                        if (this.minecraft != null && this.minecraft.gui.screen() == this) {
                            this.rebuildWidgets();
                        }
                    }
                )
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
        String configKey
    ) {
        return CycleButton.onOffBuilder(initialValue)
            .withTooltip(v -> Tooltip.create(Component.translatable(tooltipKey)))
            .create(
                0,
                0,
                150,
                20,
                Component.translatable(labelKey),
                (button, value) -> applyBooleanSetting(configKey, value)
            );
    }

    private CycleButton<OIMRConfig.MultiMemberScalingMode> smallScalingModeCycle(
        String labelKey,
        String tooltipKey,
        OIMRConfig.MultiMemberScalingMode initialValue,
        Function<OIMRConfig.MultiMemberScalingMode, Boolean> setter
    ) {
        return CycleButton.<OIMRConfig.MultiMemberScalingMode>builder(
                value -> Component.translatable(value.translationKey()),
                initialValue
            )
            .withValues(OIMRConfig.MultiMemberScalingMode.values())
            .withTooltip(v -> Tooltip.create(Component.translatable(tooltipKey)))
            .create(
                0,
                0,
                150,
                20,
                Component.translatable(labelKey),
                (button, value) -> applySetting(() -> setter.apply(value))
            );
    }

    private void applyBooleanSetting(String key, boolean enabled) {
        applyServerOrLocal(
            (server, host) -> HiveMindManager.applyBooleanSetting(server, host, key, enabled),
            () -> OIMRConfig.setBooleanSettingWithTransition(key, enabled, null),
            null
        );
    }

    private void applySetting(BooleanSupplier action) {
        applyServerOrLocal((server, host) -> {
            boolean persisted = action.getAsBoolean();
            HiveMindManager.onConfigChanged(server);
            return persisted;
        }, action, null);
    }

    private void applyServerOrLocal(
        BiFunction<net.minecraft.server.MinecraftServer, @org.jspecify.annotations.Nullable ServerPlayer, Boolean> serverAction,
        BooleanSupplier localAction,
        @org.jspecify.annotations.Nullable Runnable afterOnClient
    ) {
        IntegratedServer server = this.minecraft != null ? this.minecraft.getSingleplayerServer() : null;
        if (server != null) {
            java.util.UUID hostUuid = this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
            server.execute(() -> {
                ServerPlayer host = hostUuid != null ? server.getPlayerList().getPlayer(hostUuid) : null;
                boolean persisted = serverAction.apply(server, host);
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        if (!persisted) {
                            showSaveFailure();
                        }
                        if (afterOnClient != null) {
                            afterOnClient.run();
                        }
                    });
                }
            });
        } else {
            boolean persisted = localAction.getAsBoolean();
            if (!persisted) {
                showSaveFailure();
            }
            if (afterOnClient != null) {
                afterOnClient.run();
            }
        }
    }

    private void showSaveFailure() {
        String reason = OIMRConfig.getLastSaveErrorMessage();
        if (reason == null || reason.isBlank()) {
            reason = "I/O error";
        }
        if (this.minecraft != null) {
            this.minecraft.gui.hud.getChat().addClientSystemMessage(
                Component.translatable("oimr.command.settings_save_failed", reason).withStyle(ChatFormatting.RED)
            );
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
        private final Function<Float, Boolean> setter;
        private float displayWeight;

        private WeightSlider(
            int width,
            int height,
            String labelKey,
            float initialWeight,
            Function<Float, Boolean> setter
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
            applySetting(() -> this.setter.apply(weight));
        }
    }

    private void updateMobSoundControlsState(boolean sharedMobSoundsEnabled, OIMRConfig.MobSoundFilter filter) {
        if (this.mobSoundFilterButton != null) {
            this.mobSoundFilterButton.active = sharedMobSoundsEnabled;
        }
        if (this.mobSoundVolumeSlider != null) {
            this.mobSoundVolumeSlider.active = sharedMobSoundsEnabled && filter != OIMRConfig.MobSoundFilter.NONE;
        }
    }

    private final class MobSoundVolumeSlider extends AbstractSliderButton {
        private int displayPercent;

        private MobSoundVolumeSlider(int width, int height, int initialPercent) {
            super(
                0,
                0,
                width,
                height,
                Component.empty(),
                Mth.clamp(initialPercent / 100.0D, 0.0D, 1.0D)
            );
            this.displayPercent = Mth.clamp(
                initialPercent,
                OIMRConfig.MIN_SHARED_MOB_SOUND_VOLUME,
                OIMRConfig.MAX_SHARED_MOB_SOUND_VOLUME
            );
            this.updateMessage();
        }

        private int snappedPercent() {
            return Mth.clamp(
                (int) Math.round(this.value * 100.0D),
                OIMRConfig.MIN_SHARED_MOB_SOUND_VOLUME,
                OIMRConfig.MAX_SHARED_MOB_SOUND_VOLUME
            );
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("oimr.settings.shared_mob_sound_volume", this.displayPercent));
        }

        @Override
        protected void applyValue() {
            int percent = snappedPercent();
            this.displayPercent = percent;
            applySetting(() -> OIMRConfig.setSharedMobSoundVolumePercent(percent));
        }
    }
}
