package net.iakee.oneinventorymanyregrets.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Informational commands for inspecting active Hive Mind groups and managing host/console-only settings.
 * Team membership itself is controlled via vanilla {@code /team}.
 */
public final class OIMRCommand {
    private OIMRCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("oimr")
                    .then(Commands.literal("status").executes(OIMRCommand::executeStatus))
                    .then(Commands.literal("list").executes(OIMRCommand::executeList))
                    .then(
                        Commands.literal("settings")
                            .requires(OIMRCommand::canEditSettings)
                            .executes(OIMRCommand::executeSettingsStatus)
                            .then(boolSetting("sharedKnockback", OIMRConfig::setSharedKnockback))
                            .then(boolSetting("comunism", OIMRConfig::setComunism))
                            .then(boolSetting("friendlyPets", OIMRConfig::setFriendlyPets))
                            .then(boolSetting("excludeCreativeFromDeath", OIMRConfig::setExcludeCreativeFromDeath))
                            .then(boolSetting("wipeCreativeInventoryOnDeath", OIMRConfig::setWipeCreativeInventoryOnDeath))
                            .then(boolSetting("syncHotbar", OIMRConfig::setSyncHotbar))
                            .then(boolSetting("syncHelmet", OIMRConfig::setSyncHelmet))
                            .then(boolSetting("syncChestplate", OIMRConfig::setSyncChestplate))
                            .then(boolSetting("syncLeggings", OIMRConfig::setSyncLeggings))
                            .then(boolSetting("syncBoots", OIMRConfig::setSyncBoots))
                            .then(boolSetting("syncMainHand", OIMRConfig::setSyncMainHand))
                            .then(boolSetting("syncOffHand", OIMRConfig::setSyncOffHand))
                            .then(
                                Commands.literal("hungerWeight")
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument(
                                            "weight",
                                            FloatArgumentType.floatArg(OIMRConfig.MIN_HUNGER_WEIGHT, OIMRConfig.MAX_HUNGER_WEIGHT)
                                        ).executes(OIMRCommand::executeSetHungerWeight)
                                    )
                            )
                            .then(
                                Commands.literal("xpWeight")
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument(
                                            "weight",
                                            FloatArgumentType.floatArg(OIMRConfig.MIN_XP_WEIGHT, OIMRConfig.MAX_XP_WEIGHT)
                                        ).executes(OIMRCommand::executeSetXpWeight)
                                    )
                            )
                            .then(
                                Commands.literal("affinity")
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument(
                                            "percent",
                                            IntegerArgumentType.integer(OIMRConfig.MIN_AFFINITY, OIMRConfig.MAX_AFFINITY)
                                        ).executes(OIMRCommand::executeSetAffinity)
                                    )
                            )
                            .then(
                                Commands.literal("reload")
                                    .executes(OIMRCommand::executeReloadSettings)
                            )
                    )
            )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> boolSetting(String key, Consumer<Boolean> setter) {
        return Commands.literal(key)
            .executes(OIMRCommand::executeSettingsStatus)
            .then(
                Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(context -> {
                        boolean enabled = BoolArgumentType.getBool(context, "enabled");
                        setter.accept(enabled);
                        HiveMindManager.onConfigChanged(context.getSource().getServer());
                        context.getSource().sendSuccess(
                            () -> Component.literal("Updated OIMR setting: " + key + " = " + enabled),
                            true
                        );
                        return 1;
                    })
            );
    }

    /**
     * Restricts mod settings strictly to the Singleplayer/LAN host or the dedicated server console/RCON.
     */
    public static boolean canEditSettings(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return false;
        }
        if (source.getEntity() == null) {
            return Commands.LEVEL_OWNERS.check(source.permissions());
        }
        return source.getEntity() instanceof ServerPlayer player
            && server.isSingleplayer()
            && server.isSingleplayerOwner(player.nameAndId());
    }

    private static String formatSettingsSummary() {
        return String.format(
            "affinity = %d%% | hungerWeight = %.2f | xpWeight = %.2f | syncHotbar = %s | slots[H=%s,C=%s,L=%s,B=%s,MH=%s,OH=%s] | sharedKnockback = %s | friendlyPets = %s | excludeCreativeFromDeath = %s | wipeCreativeInventoryOnDeath = %s | comunism = %s",
            OIMRConfig.getAffinityPercent(),
            OIMRConfig.getHungerWeight(),
            OIMRConfig.getXpWeight(),
            OIMRConfig.isSyncHotbarEnabled(),
            OIMRConfig.isSyncHelmetEnabled(),
            OIMRConfig.isSyncChestplateEnabled(),
            OIMRConfig.isSyncLeggingsEnabled(),
            OIMRConfig.isSyncBootsEnabled(),
            OIMRConfig.isSyncMainHandEnabled(),
            OIMRConfig.isSyncOffHandEnabled(),
            OIMRConfig.isSharedKnockbackEnabled(),
            OIMRConfig.isFriendlyPetsEnabled(),
            OIMRConfig.isExcludeCreativeFromDeathEnabled(),
            OIMRConfig.isWipeCreativeInventoryOnDeathEnabled(),
            OIMRConfig.isComunismEnabled()
        );
    }

    private static int executeSettingsStatus(CommandContext<CommandSourceStack> context) {
        String summary = formatSettingsSummary();
        context.getSource().sendSuccess(
            () -> Component.literal("OIMR Settings | " + summary),
            false
        );
        return 1;
    }

    private static int executeSetHungerWeight(CommandContext<CommandSourceStack> context) {
        float weight = FloatArgumentType.getFloat(context, "weight");
        OIMRConfig.setHungerWeight(weight);
        float updated = OIMRConfig.getHungerWeight();
        HiveMindManager.onConfigChanged(context.getSource().getServer());
        context.getSource().sendSuccess(
            () -> Component.literal(String.format("Updated OIMR setting: hungerWeight = %.2f", updated)),
            true
        );
        return 1;
    }

    private static int executeSetXpWeight(CommandContext<CommandSourceStack> context) {
        float weight = FloatArgumentType.getFloat(context, "weight");
        OIMRConfig.setXpWeight(weight);
        float updated = OIMRConfig.getXpWeight();
        HiveMindManager.onConfigChanged(context.getSource().getServer());
        context.getSource().sendSuccess(
            () -> Component.literal(String.format("Updated OIMR setting: xpWeight = %.2f", updated)),
            true
        );
        return 1;
    }

    private static int executeSetAffinity(CommandContext<CommandSourceStack> context) {
        int percent = IntegerArgumentType.getInteger(context, "percent");
        OIMRConfig.setAffinityPercent(percent);
        int updated = OIMRConfig.getAffinityPercent();
        HiveMindManager.onConfigChanged(context.getSource().getServer());
        context.getSource().sendSuccess(
            () -> Component.literal("Updated OIMR setting: affinity = " + updated + "%"),
            true
        );
        return 1;
    }

    private static int executeReloadSettings(CommandContext<CommandSourceStack> context) {
        OIMRConfig.load();
        MinecraftServer server = context.getSource().getServer();
        HiveMindManager.onConfigChanged(server);
        HiveMindManager.notifyIfInvalidAffinityReset(server);
        String summary = formatSettingsSummary();
        context.getSource().sendSuccess(
            () -> Component.literal("Reloaded OIMR settings | " + summary),
            true
        );
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        String hiveId = HiveMindManager.getHiveId(player);
        List<ServerPlayer> onlineMembers = HiveMindManager.getOnlineMembers(server, hiveId);
        int hungerScale = HiveMindManager.getHungerScaleDenominator(server, hiveId);
        String memberNames = onlineMembers.stream()
            .map(p -> p.getGameProfile().name())
            .collect(Collectors.joining(", "));

        context.getSource().sendSuccess(
            () -> Component.literal(String.format(
                "Group: %s | Online: %d (%s) | Affinity: %d%% | Hunger: %.2fx / %d | XP: %.2fx / %d | Shared KB: %s | HP: %.1f | Food: %d | XP Lv: %d",
                hiveId,
                onlineMembers.size(),
                memberNames.isEmpty() ? "none" : memberNames,
                OIMRConfig.getAffinityPercent(),
                OIMRConfig.getHungerWeight(),
                hungerScale,
                OIMRConfig.getXpWeight(),
                hungerScale,
                OIMRConfig.isSharedKnockbackEnabled() ? "ON" : "OFF",
                player.getHealth(),
                player.getFoodData().getFoodLevel(),
                player.experienceLevel
            )),
            false
        );
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        HiveMindData data = HiveMindData.get(server);
        if (data.getAll().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No groups registered yet."), false);
            return 1;
        }

        String summary = data.getAll().stream()
            .map(hive -> {
                List<ServerPlayer> members = HiveMindManager.getOnlineMembers(server, hive.getId());
                String names = members.stream()
                    .map(p -> p.getGameProfile().name())
                    .collect(Collectors.joining(", "));
                return String.format("%s (%d online%s)",
                    hive.getId(),
                    members.size(),
                    names.isEmpty() ? "" : ": " + names);
            })
            .collect(Collectors.joining(" | "));

        context.getSource().sendSuccess(() -> Component.literal("Groups: " + summary), false);
        return 1;
    }
}
