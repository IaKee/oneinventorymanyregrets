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
import net.iakee.oneinventorymanyregrets.hivemind.OIMRLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
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
                            .then(boolSetting(OIMRConfig.KEY_SHARED_KNOCKBACK))
                            .then(boolSetting(OIMRConfig.KEY_SHARED_ENDER_PEARL))
                            .then(boolSetting(OIMRConfig.KEY_SHARED_PORTALS))
                            .then(boolSetting(OIMRConfig.KEY_UNDERWATER_MUFFLE))
                            .then(boolSetting(OIMRConfig.KEY_SHARED_MOB_SOUNDS))
                            .then(boolSetting(OIMRConfig.KEY_COMUNISM))
                            .then(boolSetting(OIMRConfig.KEY_FRIENDLY_PETS))
                            .then(boolSetting(OIMRConfig.KEY_EXCLUDE_CREATIVE_FROM_DEATH))
                            .then(boolSetting(OIMRConfig.KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH))
                            .then(boolSetting(OIMRConfig.KEY_RANDOM_DEATH_LOOT))
                            .then(boolSetting(OIMRConfig.KEY_SYNC_EFFECTS))
                            .then(boolSetting(OIMRConfig.KEY_SYNC_HOTBAR))
                            .then(boolSetting(OIMRConfig.KEY_SYNC_HELMET))
                            .then(boolSetting(OIMRConfig.KEY_SYNC_CHESTPLATE))
                            .then(boolSetting(OIMRConfig.KEY_SYNC_LEGGINGS))
                            .then(boolSetting(OIMRConfig.KEY_SYNC_BOOTS))
                            .then(boolSetting(OIMRConfig.KEY_SYNC_MAIN_HAND))
                            .then(boolSetting(OIMRConfig.KEY_SYNC_OFF_HAND))
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

    private static LiteralArgumentBuilder<CommandSourceStack> boolSetting(String key) {
        return Commands.literal(key)
            .executes(OIMRCommand::executeSettingsStatus)
            .then(
                Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(context -> {
                        boolean enabled = BoolArgumentType.getBool(context, "enabled");
                        ServerPlayer initiator = context.getSource().getEntity() instanceof ServerPlayer player ? player : null;
                        HiveMindManager.applyBooleanSetting(context.getSource().getServer(), initiator, key, enabled);
                        context.getSource().sendSuccess(
                            () -> OIMRLang.localizeForSource(
                                OIMRLang.translatable(
                                    "oimr.command.setting_updated",
                                    key,
                                    OIMRLang.translatable(enabled ? "oimr.command.on" : "oimr.command.off")
                                ),
                                context.getSource()
                            ),
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

    private static MutableComponent formatSettingsSummary() {
        MutableComponent summary = Component.empty()
            .append(OIMRLang.translatable(
                "oimr.settings.affinity",
                OIMRConfig.getAffinityPercent(),
                OIMRLang.translatable(
                    OIMRConfig.getAffinityPercent() == OIMRConfig.MAX_AFFINITY
                        ? "oimr.settings.affinity.linked_tag"
                        : "oimr.settings.affinity.unlinked_tag"
                )
            ))
            .append(" | ")
            .append(OIMRLang.translatable(
                "oimr.settings.hunger_weight",
                String.format(Locale.ROOT, "%.2f", OIMRConfig.getHungerWeight())
            ))
            .append(" | ")
            .append(OIMRLang.translatable(
                "oimr.settings.xp_weight",
                String.format(Locale.ROOT, "%.2f", OIMRConfig.getXpWeight())
            ));

        appendSettingStatus(summary, "oimr.settings.sync_effects", OIMRConfig.isSyncEffectsEnabled());
        appendSettingStatus(summary, "oimr.settings.sync_hotbar", OIMRConfig.isSyncHotbarEnabled());
        appendSettingStatus(summary, "oimr.settings.sync_helmet", OIMRConfig.isSyncHelmetEnabled());
        appendSettingStatus(summary, "oimr.settings.sync_chestplate", OIMRConfig.isSyncChestplateEnabled());
        appendSettingStatus(summary, "oimr.settings.sync_leggings", OIMRConfig.isSyncLeggingsEnabled());
        appendSettingStatus(summary, "oimr.settings.sync_boots", OIMRConfig.isSyncBootsEnabled());
        appendSettingStatus(summary, "oimr.settings.sync_main_hand", OIMRConfig.isSyncMainHandEnabled());
        appendSettingStatus(summary, "oimr.settings.sync_off_hand", OIMRConfig.isSyncOffHandEnabled());
        appendSettingStatus(summary, "oimr.settings.shared_knockback", OIMRConfig.isSharedKnockbackEnabled());
        appendSettingStatus(summary, "oimr.settings.shared_ender_pearl", OIMRConfig.isSharedEnderPearlEnabled());
        appendSettingStatus(summary, "oimr.settings.shared_portals", OIMRConfig.isSharedPortalsEnabled());
        appendSettingStatus(summary, "oimr.settings.underwater_muffle", OIMRConfig.isUnderwaterMuffleEnabled());
        appendSettingStatus(summary, "oimr.settings.shared_mob_sounds", OIMRConfig.isSharedMobSoundsEnabled());
        appendSettingStatus(summary, "oimr.settings.friendly_pets", OIMRConfig.isFriendlyPetsEnabled());
        appendSettingStatus(summary, "oimr.settings.exclude_creative", OIMRConfig.isExcludeCreativeFromDeathEnabled());
        appendSettingStatus(summary, "oimr.settings.wipe_creative", OIMRConfig.isWipeCreativeInventoryOnDeathEnabled());
        appendSettingStatus(summary, "oimr.settings.random_death_loot", OIMRConfig.isRandomDeathLootEnabled());
        appendSettingStatus(summary, "oimr.settings.comunism", OIMRConfig.isComunismEnabled());
        return summary;
    }

    private static void appendSettingStatus(MutableComponent summary, String labelKey, boolean enabled) {
        summary.append(" | ")
            .append(OIMRLang.translatable(labelKey))
            .append(": ")
            .append(OIMRLang.translatable(enabled ? "oimr.command.on" : "oimr.command.off"));
    }

    private static int executeSettingsStatus(CommandContext<CommandSourceStack> context) {
        Component summary = formatSettingsSummary();
        context.getSource().sendSuccess(
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.settings_status", summary),
                context.getSource()
            ),
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
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.setting_updated", "hungerWeight", String.format(Locale.ROOT, "%.2f", updated)),
                context.getSource()
            ),
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
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.setting_updated", "xpWeight", String.format(Locale.ROOT, "%.2f", updated)),
                context.getSource()
            ),
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
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.setting_updated.percent", "affinity", updated),
                context.getSource()
            ),
            true
        );
        return 1;
    }

    private static int executeReloadSettings(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ServerPlayer initiator = context.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        HiveMindManager.reloadConfig(server, initiator);
        Component summary = formatSettingsSummary();
        context.getSource().sendSuccess(
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.settings_reloaded", summary),
                context.getSource()
            ),
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
        Component membersText = memberNames.isEmpty()
            ? OIMRLang.translatable("oimr.command.status.none")
            : Component.literal(memberNames);
        Component sharedKbText = OIMRLang.translatable(
            OIMRConfig.isSharedKnockbackEnabled() ? "oimr.command.on" : "oimr.command.off"
        );

        context.getSource().sendSuccess(
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable(
                    "oimr.command.status",
                    hiveId,
                    onlineMembers.size(),
                    membersText,
                    OIMRConfig.getAffinityPercent(),
                    String.format(Locale.ROOT, "%.2f", OIMRConfig.getHungerWeight()),
                    hungerScale,
                    String.format(Locale.ROOT, "%.2f", OIMRConfig.getXpWeight()),
                    hungerScale,
                    sharedKbText,
                    String.format(Locale.ROOT, "%.1f", player.getHealth()),
                    player.getFoodData().getFoodLevel(),
                    player.experienceLevel
                ),
                context.getSource()
            ),
            false
        );
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        HiveMindData data = HiveMindData.get(server);
        if (data.getAll().isEmpty()) {
            context.getSource().sendSuccess(
                () -> OIMRLang.localizeForSource(
                    OIMRLang.translatable("oimr.command.list.empty"),
                    context.getSource()
                ),
                false
            );
            return 1;
        }

        MutableComponent entries = Component.empty();
        boolean first = true;
        for (var hive : data.getAll()) {
            if (!first) {
                entries.append(" | ");
            }
            first = false;
            List<ServerPlayer> members = HiveMindManager.getOnlineMembers(server, hive.getId());
            String names = members.stream()
                .map(p -> p.getGameProfile().name())
                .collect(Collectors.joining(", "));
            entries.append(
                names.isEmpty()
                    ? OIMRLang.translatable("oimr.command.list.entry", hive.getId(), members.size())
                    : OIMRLang.translatable("oimr.command.list.entry.named", hive.getId(), members.size(), names)
            );
        }

        context.getSource().sendSuccess(
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.list", entries),
                context.getSource()
            ),
            false
        );
        return 1;
    }
}
