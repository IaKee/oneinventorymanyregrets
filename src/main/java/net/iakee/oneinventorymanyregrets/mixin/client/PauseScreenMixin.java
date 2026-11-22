package net.iakee.oneinventorymanyregrets.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.iakee.oneinventorymanyregrets.client.HiveMindSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(
        method = "createPauseMenu",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/PauseScreen;getCustomAdditions()Ljava/util/Optional;"
        )
    )
    private void oimr$addHostSettingsButton(CallbackInfo ci, @Local GridLayout.RowHelper helper) {
        if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null && this.minecraft.level != null) {
            helper.addChild(
                Button.builder(
                    Component.literal("Hive Mind Settings..."),
                    button -> this.minecraft.gui.setScreen(new HiveMindSettingsScreen(this))
                ).width(204).build(),
                2
            );
        }
    }
}
