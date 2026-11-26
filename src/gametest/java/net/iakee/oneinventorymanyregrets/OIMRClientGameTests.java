package net.iakee.oneinventorymanyregrets;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.iakee.oneinventorymanyregrets.client.OIMRSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Client smoke tests for the settings screen at the smallest resolution covered by the manual matrix.
 */
public final class OIMRClientGameTests implements FabricClientGameTest {
    private static final int TEST_WIDTH = 640;
    private static final int TEST_HEIGHT = 360;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(TEST_WIDTH, TEST_HEIGHT);
        context.setScreen(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            return new OIMRSettingsScreen(minecraft.gui.screen(), minecraft.options);
        });
        context.waitForScreen(OIMRSettingsScreen.class);

        context.runOnClient(minecraft -> {
            Screen current = minecraft.gui.screen();
            if (!(current instanceof OIMRSettingsScreen)) {
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
                if (child instanceof AbstractWidget widget
                    && (widget.getX() < 0
                        || widget.getY() < 0
                        || widget.getX() + widget.getWidth() > current.width
                        || widget.getY() + widget.getHeight() > current.height)) {
                    throw new AssertionError("Widget outside the 640x360 viewport: " + widget.getMessage().getString());
                }
            }
        });

        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }
}
