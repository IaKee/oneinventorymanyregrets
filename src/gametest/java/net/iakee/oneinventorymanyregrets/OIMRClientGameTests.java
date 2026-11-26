package net.iakee.oneinventorymanyregrets;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.iakee.oneinventorymanyregrets.client.ClientHudFeedState;
import net.iakee.oneinventorymanyregrets.client.ClientUnderwaterState;
import net.iakee.oneinventorymanyregrets.client.OIMRSettingsScreen;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.network.DamageIconType;
import net.iakee.oneinventorymanyregrets.network.OIMRDamageFeedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client smoke and widget-coupling tests for the settings screen at the smallest
 * resolution covered by the manual test matrix.
 */
public final class OIMRClientGameTests implements FabricClientGameTest {
    private static final int TEST_WIDTH = 640;
    private static final int TEST_HEIGHT = 360;

    @Override
    public void runTest(ClientGameTestContext context) {
        Path configPath = OIMRConfig.getConfigPath();
        boolean hadConfig = Files.exists(configPath);
        byte[] previousConfig = null;
        try {
            if (hadConfig) {
                previousConfig = Files.readAllBytes(configPath);
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to snapshot config before client gametest", e);
        }

        try {
            OIMRConfig.resetToDefaults();
            context.getInput().resizeWindow(TEST_WIDTH, TEST_HEIGHT);
            context.setScreen(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                return new OIMRSettingsScreen(minecraft.gui.screen(), minecraft.options);
            });
            context.waitForScreen(OIMRSettingsScreen.class);

            context.runOnClient(minecraft -> {
                Screen current = minecraft.gui.screen();
                if (!(current instanceof OIMRSettingsScreen settingsScreen)) {
                    throw new AssertionError("OIMR settings screen did not open");
                }
                if (current.width != TEST_WIDTH || current.height != TEST_HEIGHT) {
                    throw new AssertionError(
                        "Unexpected settings screen size: " + current.width + "x" + current.height
                    );
                }
                if (current.getTitle().getString().isBlank()) {
                    throw new AssertionError("Settings screen must expose a non-empty title for narration");
                }
                for (var child : current.children()) {
                    if (child instanceof AbstractWidget widget) {
                        assertWidgetInViewport(widget, current.width, current.height);
                    }
                }

                List<AbstractWidget> optionWidgets = collectOptionWidgets(settingsScreen);
                if (optionWidgets.isEmpty()) {
                    throw new AssertionError("Expected option widgets inside OIMRSettingsScreen OptionsList");
                }

                // 1. Verify Sync Hotbar <-> Main Hand coupling
                CycleButton<?> syncHotbarBtn = findCycleButtonByPrefix(optionWidgets, "oimr.settings.sync_hotbar");
                CycleButton<?> mainHandBtn = findCycleButtonByPrefix(optionWidgets, "oimr.settings.sync_main_hand");
                if (mainHandBtn.active) {
                    throw new AssertionError("Main Hand button must start inactive/coupled when Sync Hotbar is ON");
                }
                pressButton(syncHotbarBtn);
                if (!mainHandBtn.active || OIMRConfig.isSyncHotbarEnabled()) {
                    throw new AssertionError("Toggling Sync Hotbar OFF must enable Main Hand button and update config");
                }
                pressButton(syncHotbarBtn);
                if (mainHandBtn.active || !OIMRConfig.isSyncHotbarEnabled()) {
                    throw new AssertionError("Toggling Sync Hotbar back ON must re-couple Main Hand button");
                }

                // 2. Verify Ignore Creative on Death <-> Wipe Inv (Creative) coupling
                CycleButton<?> excludeCreativeBtn = findCycleButtonByPrefix(optionWidgets, "oimr.settings.exclude_creative");
                CycleButton<?> wipeCreativeBtn = findCycleButtonByPrefix(optionWidgets, "oimr.settings.wipe_creative");
                if (!wipeCreativeBtn.active) {
                    throw new AssertionError("Wipe Inv (Creative) must start active when Ignore Creative is ON");
                }
                pressButton(excludeCreativeBtn);
                if (wipeCreativeBtn.active) {
                    throw new AssertionError("Disabling Ignore Creative on Death must deactivate Wipe Inv (Creative)");
                }
                pressButton(excludeCreativeBtn);
                if (!wipeCreativeBtn.active) {
                    throw new AssertionError("Re-enabling Ignore Creative on Death must reactivate Wipe Inv (Creative)");
                }

                // 3. Verify Shared Mob Sounds <-> Filter & Volume slider coupling
                CycleButton<?> sharedMobSoundsBtn = findCycleButtonByPrefix(optionWidgets, "oimr.settings.shared_mob_sounds");
                CycleButton<?> mobSoundFilterBtn = findCycleButtonByPrefix(optionWidgets, "oimr.settings.shared_mob_sound_filter");
                AbstractWidget volumeSlider = findWidgetByPrefix(optionWidgets, "oimr.settings.shared_mob_sound_volume", 100);
                if (!mobSoundFilterBtn.active || !volumeSlider.active) {
                    throw new AssertionError("Mob sound filter and volume slider must start active");
                }
                pressButton(sharedMobSoundsBtn);
                if (mobSoundFilterBtn.active || volumeSlider.active) {
                    throw new AssertionError("Disabling Shared Mob Sounds must deactivate filter and volume slider");
                }
                pressButton(sharedMobSoundsBtn);
                if (!mobSoundFilterBtn.active || !volumeSlider.active) {
                    throw new AssertionError("Re-enabling Shared Mob Sounds must reactivate filter and volume slider");
                }

                // 3b. Verify Shared Cave Sounds toggle updates config
                CycleButton<?> sharedCaveSoundsBtn = findCycleButtonByPrefix(optionWidgets, "oimr.settings.shared_cave_sounds");
                if (!OIMRConfig.isSharedCaveSoundsEnabled()) {
                    throw new AssertionError("Shared Cave Sounds must default to ON");
                }
                pressButton(sharedCaveSoundsBtn);
                if (OIMRConfig.isSharedCaveSoundsEnabled()) {
                    throw new AssertionError("Disabling Shared Cave Sounds must update config");
                }
                pressButton(sharedCaveSoundsBtn);
                if (!OIMRConfig.isSharedCaveSoundsEnabled()) {
                    throw new AssertionError("Re-enabling Shared Cave Sounds must restore config");
                }

                // 3c. Verify Sync Advancements toggle defaults ON and updates config
                CycleButton<?> syncAdvancementsBtn = findCycleButtonByPrefix(optionWidgets, "oimr.settings.sync_advancements");
                if (!OIMRConfig.isSyncAdvancementsEnabled()) {
                    throw new AssertionError("Sync Advancements must default to ON");
                }
                pressButton(syncAdvancementsBtn);
                if (OIMRConfig.isSyncAdvancementsEnabled()) {
                    throw new AssertionError("Disabling Sync Advancements must update config");
                }
                pressButton(syncAdvancementsBtn);
                if (!OIMRConfig.isSyncAdvancementsEnabled()) {
                    throw new AssertionError("Re-enabling Sync Advancements must restore config");
                }

                // 4. Scroll the options list to the bottom and confirm footer Reset/Done stay inside 640x360
                OptionsList list = findOptionsList(settingsScreen);
                list.setScrollAmount(Double.MAX_VALUE);
                Button resetBtn = findFooterButton(settingsScreen, "oimr.settings.reset");
                Button doneBtn = findFooterButton(settingsScreen, "gui.done");
                assertWidgetInViewport(resetBtn, current.width, current.height);
                assertWidgetInViewport(doneBtn, current.width, current.height);
                if (resetBtn.getX() + resetBtn.getWidth() > doneBtn.getX()) {
                    throw new AssertionError("Footer Reset and Done buttons overlap at 640x360");
                }

                // 5. Verify ClientHudFeedState & ClientUnderwaterState safe defaults on title screen
                if (ClientUnderwaterState.shouldApplySharedUnderwater(minecraft.player)
                    || ClientUnderwaterState.isUnderwaterAudioActive(minecraft.player)) {
                    throw new AssertionError("Underwater state must stay inactive when no player is in world");
                }
                ClientHudFeedState.reset();
                ClientHudFeedState.pushDamage(new OIMRDamageFeedPayload("Alice", DamageIconType.MELEE, 4.0F));
                if (ClientHudFeedState.snapshotDamageEntries(true).size() != 1) {
                    throw new AssertionError("ClientHudFeedState damage entry validation failed");
                }
                ClientHudFeedState.reset();
            });

            context.setScreen(TitleScreen::new);
            context.waitForScreen(TitleScreen.class);

            // 6. Smoke-test joining and ticking a singleplayer world so client-side
            // world/player mixins (e.g. BiomeAmbientSoundsHandlerMixin) are exercised.
            try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
                singleplayer.getConnection().waitForChunksDownload();
                context.waitTicks(2);
                context.runOnClient(minecraft -> {
                    if (minecraft.player == null || minecraft.level == null) {
                        throw new AssertionError("Expected LocalPlayer and ClientLevel after joining singleplayer world");
                    }
                });
            }
        } finally {
            try {
                if (hadConfig && previousConfig != null) {
                    Files.createDirectories(configPath.getParent());
                    Files.write(configPath, previousConfig);
                    OIMRConfig.load();
                } else {
                    Files.deleteIfExists(configPath);
                    Files.deleteIfExists(OIMRConfig.getBackupPath(configPath));
                    OIMRConfig.resetLoadedStateForTests();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void assertWidgetInViewport(AbstractWidget widget, int width, int height) {
        if (widget.getX() < 0
            || widget.getY() < 0
            || widget.getX() + widget.getWidth() > width
            || widget.getY() + widget.getHeight() > height) {
            throw new AssertionError("Widget outside the 640x360 viewport: " + widget.getMessage().getString());
        }
    }

    private static OptionsList findOptionsList(OIMRSettingsScreen screen) {
        for (var child : screen.children()) {
            if (child instanceof OptionsList list) {
                return list;
            }
        }
        throw new AssertionError("OptionsList not found in OIMRSettingsScreen");
    }

    private static List<AbstractWidget> collectOptionWidgets(OIMRSettingsScreen screen) {
        OptionsList list = findOptionsList(screen);
        List<AbstractWidget> widgets = new ArrayList<>();
        for (Object obj : list.children()) {
            if (obj instanceof ContainerEventHandler entry) {
                for (var child : entry.children()) {
                    if (child instanceof AbstractWidget widget) {
                        widgets.add(widget);
                    }
                }
            }
        }
        return widgets;
    }

    private static CycleButton<?> findCycleButtonByPrefix(List<AbstractWidget> widgets, String translationKey) {
        AbstractWidget widget = findWidgetByPrefix(widgets, translationKey);
        if (widget instanceof CycleButton<?> cycle) {
            return cycle;
        }
        throw new AssertionError("Expected CycleButton for key " + translationKey);
    }

    private static AbstractWidget findWidgetByPrefix(List<AbstractWidget> widgets, String translationKey, Object... args) {
        String label = Component.translatable(translationKey, args).getString();
        String prefix = label.contains(":") ? label.substring(0, label.indexOf(':')) : label;
        for (AbstractWidget widget : widgets) {
            if (widget.getMessage().getString().startsWith(prefix)) {
                return widget;
            }
        }
        throw new AssertionError("Widget not found for translation key " + translationKey + " (prefix=" + prefix + ")");
    }

    private static Button findFooterButton(OIMRSettingsScreen screen, String translationKey) {
        String label = Component.translatable(translationKey).getString();
        for (var child : screen.children()) {
            if (child instanceof Button button && label.equals(button.getMessage().getString())) {
                return button;
            }
        }
        throw new AssertionError("Footer button not found: " + translationKey);
    }

    private static void pressButton(CycleButton<?> button) {
        button.onPress(new KeyEvent(0, 0, 0));
    }
}
