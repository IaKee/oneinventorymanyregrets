package net.iakee.oneinventorymanyregrets.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
                            .then(
                                Commands.literal("sharedKnockback")
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument("enabled", BoolArgumentType.bool())
                                            .executes(OIMRCommand::executeSetSharedKnockback)
                                    )
                            )
                            .then(
                                Commands.literal("comunism")
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument("enabled", BoolArgumentType.bool())
                                            .executes(OIMRCommand::executeSetComunism)
                                    )
                            )
                            .then(
                                Commands.literal("friendlyPets")
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument("enabled", BoolArgumentType.bool())
                                            .executes(OIMRCommand::executeSetFriendlyPets)
                                    )
                            )
                            .then(
                                Commands.literal("excludeCreativeFromDeath")
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument("enabled", BoolArgumentType.bool())
                                            .executes(OIMRCommand::executeSetExcludeCreativeFromDeath)
                                    )
                            )
                            .then(
                                Commands.literal("wipeCreativeInventoryOnDeath")
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument("enabled", BoolArgumentType.bool())
                                            .executes(OIMRCommand::executeSetWipeCreativeInventoryOnDeath)
                                    )
                            )
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
                                Commands.literal("reload")
                                    .executes(OIMRCommand::executeReloadSettings)
                            )
                    )
            )
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
            "sharedKnockback = %s | comunism = %s | friendlyPets = %s | excludeCreativeFromDeath = %s | wipeCreativeInventoryOnDeath = %s | hungerWeight = %.2f",
            OIMRConfig.isSharedKnockbackEnabled(),
            OIMRConfig.isComunismEnabled(),
            OIMRConfig.isFriendlyPetsEnabled(),
            OIMRConfig.isExcludeCreativeFromDeathEnabled(),
            OIMRConfig.isWipeCreativeInventoryOnDeathEnabled(),
            OIMRConfig.getHungerWeight()
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

    private static int executeSetSharedKnockback(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        OIMRConfig.setSharedKnockback(enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("Updated OIMR setting: sharedKnockback = " + enabled),
            true
        );
        return 1;
    }

    private static int executeSetComunism(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        OIMRConfig.setComunism(enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("Updated OIMR setting: comunism = " + enabled),
            true
        );
        return 1;
    }

    private static int executeSetFriendlyPets(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        OIMRConfig.setFriendlyPets(enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("Updated OIMR setting: friendlyPets = " + enabled),
            true
        );
        return 1;
    }

    private static int executeSetExcludeCreativeFromDeath(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        OIMRConfig.setExcludeCreativeFromDeath(enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("Updated OIMR setting: excludeCreativeFromDeath = " + enabled),
            true
        );
        return 1;
    }

    private static int executeSetWipeCreativeInventoryOnDeath(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        OIMRConfig.setWipeCreativeInventoryOnDeath(enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("Updated OIMR setting: wipeCreativeInventoryOnDeath = " + enabled),
            true
        );
        return 1;
    }

    private static int executeSetHungerWeight(CommandContext<CommandSourceStack> context) {
        float weight = FloatArgumentType.getFloat(context, "weight");
        OIMRConfig.setHungerWeight(weight);
        float updated = OIMRConfig.getHungerWeight();
        context.getSource().sendSuccess(
            () -> Component.literal(String.format("Updated OIMR setting: hungerWeight = %.2f", updated)),
            true
        );
        return 1;
    }

    private static int executeReloadSettings(CommandContext<CommandSourceStack> context) {
        OIMRConfig.load();
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
                "Hive Mind: %s | Online: %d (%s) | Hunger rate: %.2fx / %d per player | Shared KB: %s | HP: %.1f | Food: %d | XP Lv: %d",
                hiveId,
                onlineMembers.size(),
                memberNames.isEmpty() ? "none" : memberNames,
                OIMRConfig.getHungerWeight(),
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
            context.getSource().sendSuccess(() -> Component.literal("No Hive Minds registered yet."), false);
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

        context.getSource().sendSuccess(() -> Component.literal("Hive Minds: " + summary), false);
        return 1;
    }
}
