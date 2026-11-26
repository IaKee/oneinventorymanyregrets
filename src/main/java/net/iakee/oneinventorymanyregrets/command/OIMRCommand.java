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
import net.iakee.oneinventorymanyregrets.hivemind.service.TeamLifecycleService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
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
                            .then(mobSoundFilterSetting())
                            .then(
                                Commands.literal(OIMRConfig.KEY_SHARED_MOB_SOUND_VOLUME)
                                    .executes(OIMRCommand::executeSettingsStatus)
                                    .then(
                                        Commands.argument(
                                            "percent",
                                            IntegerArgumentType.integer(
                                                OIMRConfig.MIN_SHARED_MOB_SOUND_VOLUME,
                                                OIMRConfig.MAX_SHARED_MOB_SOUND_VOLUME
                                            )
                                        ).executes(OIMRCommand::executeSetSharedMobSoundVolume)
                                    )
                            )
                            .then(boolSetting(OIMRConfig.KEY_COMUNISM))
                            .then(boolSetting(OIMRConfig.KEY_FRIENDLY_PETS))
                            .then(boolSetting(OIMRConfig.KEY_EXCLUDE_CREATIVE_FROM_DEATH))
                            .then(boolSetting(OIMRConfig.KEY_WIPE_CREATIVE_INVENTORY_ON_DEATH))
                            .then(boolSetting(OIMRConfig.KEY_RANDOM_DEATH_LOOT))
                            .then(scalingModeSetting(
                                OIMRConfig.KEY_LAVA_DAMAGE_MODE,
                                OIMRConfig::getLavaDamageMode,
                                OIMRConfig::setLavaDamageMode
                            ))
                            .then(scalingModeSetting(
                                OIMRConfig.KEY_UNDERWATER_BREATH_MODE,
                                OIMRConfig::getUnderwaterBreathMode,
                                OIMRConfig::setUnderwaterBreathMode
                            ))
                            .then(scalingModeSetting(
                                OIMRConfig.KEY_DOT_DAMAGE_MODE,
                                OIMRConfig::getDotDamageMode,
                                OIMRConfig::setDotDamageMode
                            ))
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
                    .then(
                        Commands.literal("cleanup")
                            .requires(OIMRCommand::canEditSettings)
                            .executes(context -> executeCleanup(
                                context,
                                true,
                                TeamLifecycleService.DEFAULT_ORPHAN_RETENTION_DAYS,
                                TeamLifecycleService.DEFAULT_INACTIVE_RETENTION_DAYS
                            ))
                            .then(
                                Commands.literal("dry-run")
                                    .executes(context -> executeCleanup(
                                        context,
                                        true,
                                        TeamLifecycleService.DEFAULT_ORPHAN_RETENTION_DAYS,
                                        TeamLifecycleService.DEFAULT_INACTIVE_RETENTION_DAYS
                                    ))
                                    .then(
                                        Commands.argument("retentionDays", IntegerArgumentType.integer(0, 36500))
                                            .executes(context -> {
                                                int days = IntegerArgumentType.getInteger(context, "retentionDays");
                                                return executeCleanup(context, true, days, days);
                                            })
                                    )
                            )
                            .then(
                                Commands.literal("confirm")
                                    .executes(context -> executeCleanup(
                                        context,
                                        false,
                                        TeamLifecycleService.DEFAULT_ORPHAN_RETENTION_DAYS,
                                        TeamLifecycleService.DEFAULT_INACTIVE_RETENTION_DAYS
                                    ))
                                    .then(
                                        Commands.argument("retentionDays", IntegerArgumentType.integer(0, 36500))
                                            .executes(context -> {
                                                int days = IntegerArgumentType.getInteger(context, "retentionDays");
                                                return executeCleanup(context, false, days, days);
                                            })
                                    )
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
                        boolean persisted = HiveMindManager.applyBooleanSetting(
                            context.getSource().getServer(), initiator, key, enabled
                        );
                        if (!persisted) {
                            return reportSaveFailure(context);
                        }
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

    private static LiteralArgumentBuilder<CommandSourceStack> scalingModeSetting(
        String key,
        java.util.function.Supplier<OIMRConfig.MultiMemberScalingMode> getter,
        Function<OIMRConfig.MultiMemberScalingMode, Boolean> setter
    ) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(key)
            .executes(OIMRCommand::executeSettingsStatus);
        for (OIMRConfig.MultiMemberScalingMode mode : OIMRConfig.MultiMemberScalingMode.values()) {
            builder.then(
                Commands.literal(mode.configValue())
                    .executes(context -> {
                        boolean persisted = setter.apply(mode);
                        OIMRConfig.MultiMemberScalingMode updated = getter.get();
                        HiveMindManager.onConfigChanged(context.getSource().getServer());
                        if (!persisted) {
                            return reportSaveFailure(context);
                        }
                        context.getSource().sendSuccess(
                            () -> OIMRLang.localizeForSource(
                                OIMRLang.translatable(
                                    "oimr.command.setting_updated",
                                    key,
                                    OIMRLang.translatable(updated.translationKey())
                                ),
                                context.getSource()
                            ),
                            true
                        );
                        return 1;
                    })
            );
        }
        return builder;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mobSoundFilterSetting() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(OIMRConfig.KEY_SHARED_MOB_SOUND_FILTER)
            .executes(OIMRCommand::executeSettingsStatus);
        for (OIMRConfig.MobSoundFilter filter : OIMRConfig.MobSoundFilter.values()) {
            builder.then(
                Commands.literal(filter.configValue())
                    .executes(context -> {
                        boolean persisted = OIMRConfig.setSharedMobSoundFilter(filter);
                        OIMRConfig.MobSoundFilter updated = OIMRConfig.getSharedMobSoundFilter();
                        HiveMindManager.onConfigChanged(context.getSource().getServer());
                        if (!persisted) {
                            return reportSaveFailure(context);
                        }
                        context.getSource().sendSuccess(
                            () -> OIMRLang.localizeForSource(
                                OIMRLang.translatable(
                                    "oimr.command.setting_updated",
                                    OIMRConfig.KEY_SHARED_MOB_SOUND_FILTER,
                                    OIMRLang.translatable(updated.translationKey())
                                ),
                                context.getSource()
                            ),
                            true
                        );
                        return 1;
                    })
            );
        }
        return builder;
    }

    private static int executeSetSharedMobSoundVolume(CommandContext<CommandSourceStack> context) {
        int percent = IntegerArgumentType.getInteger(context, "percent");
        boolean persisted = OIMRConfig.setSharedMobSoundVolumePercent(percent);
        int updated = OIMRConfig.getSharedMobSoundVolumePercent();
        HiveMindManager.onConfigChanged(context.getSource().getServer());
        if (!persisted) {
            return reportSaveFailure(context);
        }
        context.getSource().sendSuccess(
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.setting_updated.percent", OIMRConfig.KEY_SHARED_MOB_SOUND_VOLUME, updated),
                context.getSource()
            ),
            true
        );
        return 1;
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
        summary.append(" | ")
            .append(OIMRLang.translatable("oimr.settings.shared_mob_sound_filter"))
            .append(": ")
            .append(OIMRLang.translatable(OIMRConfig.getSharedMobSoundFilter().translationKey()));
        summary.append(" | ")
            .append(OIMRLang.translatable(
                "oimr.settings.shared_mob_sound_volume",
                OIMRConfig.getSharedMobSoundVolumePercent()
            ));
        appendSettingStatus(summary, "oimr.settings.friendly_pets", OIMRConfig.isFriendlyPetsEnabled());
        appendSettingStatus(summary, "oimr.settings.exclude_creative", OIMRConfig.isExcludeCreativeFromDeathEnabled());
        appendSettingStatus(summary, "oimr.settings.wipe_creative", OIMRConfig.isWipeCreativeInventoryOnDeathEnabled());
        appendSettingStatus(summary, "oimr.settings.random_death_loot", OIMRConfig.isRandomDeathLootEnabled());
        summary.append(" | ")
            .append(OIMRLang.translatable("oimr.settings.lava_damage_mode"))
            .append(": ")
            .append(OIMRLang.translatable(OIMRConfig.getLavaDamageMode().translationKey()));
        summary.append(" | ")
            .append(OIMRLang.translatable("oimr.settings.underwater_breath_mode"))
            .append(": ")
            .append(OIMRLang.translatable(OIMRConfig.getUnderwaterBreathMode().translationKey()));
        summary.append(" | ")
            .append(OIMRLang.translatable("oimr.settings.dot_damage_mode"))
            .append(": ")
            .append(OIMRLang.translatable(OIMRConfig.getDotDamageMode().translationKey()));
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
        boolean persisted = OIMRConfig.setHungerWeight(weight);
        float updated = OIMRConfig.getHungerWeight();
        HiveMindManager.onConfigChanged(context.getSource().getServer());
        if (!persisted) {
            return reportSaveFailure(context);
        }
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
        boolean persisted = OIMRConfig.setXpWeight(weight);
        float updated = OIMRConfig.getXpWeight();
        HiveMindManager.onConfigChanged(context.getSource().getServer());
        if (!persisted) {
            return reportSaveFailure(context);
        }
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
        boolean persisted = OIMRConfig.setAffinityPercent(percent);
        int updated = OIMRConfig.getAffinityPercent();
        HiveMindManager.onConfigChanged(context.getSource().getServer());
        if (!persisted) {
            return reportSaveFailure(context);
        }
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
        OIMRConfig.ReloadResult result = HiveMindManager.reloadConfig(server, initiator);
        Component summary = formatSettingsSummary();
        if (result.status() == OIMRConfig.ReloadStatus.FAILED_APPLIED_NOT_PERSISTED) {
            String reason = result.errorMessage() != null ? result.errorMessage() : "I/O error";
            context.getSource().sendFailure(
                OIMRLang.localizeForSource(
                    OIMRLang.translatable("oimr.command.settings_reload_applied_not_persisted", reason, summary),
                    context.getSource()
                )
            );
            return 0;
        }
        if (!result.isSuccess()) {
            String reason = result.errorMessage() != null ? result.errorMessage() : "I/O error";
            context.getSource().sendFailure(
                OIMRLang.localizeForSource(
                    OIMRLang.translatable("oimr.command.settings_reload_failed", reason, summary),
                    context.getSource()
                )
            );
            return 0;
        }
        context.getSource().sendSuccess(
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.settings_reloaded", summary),
                context.getSource()
            ),
            true
        );
        return 1;
    }

    private static int reportSaveFailure(CommandContext<CommandSourceStack> context) {
        String reason = OIMRConfig.getLastSaveErrorMessage();
        if (reason == null || reason.isBlank()) {
            reason = "I/O error";
        }
        context.getSource().sendFailure(
            OIMRLang.localizeForSource(
                OIMRLang.translatable("oimr.command.settings_save_failed", reason),
                context.getSource()
            )
        );
        return 0;
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

    private static int executeCleanup(
        CommandContext<CommandSourceStack> context,
        boolean dryRun,
        int orphanRetentionDays,
        int inactiveRetentionDays
    ) {
        MinecraftServer server = context.getSource().getServer();
        TeamLifecycleService.CleanupReport report = dryRun
            ? HiveMindManager.inspectCleanup(server, orphanRetentionDays, inactiveRetentionDays)
            : HiveMindManager.executeCleanup(server, orphanRetentionDays, inactiveRetentionDays);

        String orphanedSummary = report.orphanedTeamHives().isEmpty()
            ? "-"
            : String.join(", ", report.orphanedTeamHives());
        String inactiveSummary = report.inactiveEmptyTeamHives().isEmpty()
            ? "-"
            : String.join(", ", report.inactiveEmptyTeamHives());
        String stalePersonalSummary = report.stalePersonalStates().isEmpty()
            ? "-"
            : report.stalePersonalStates().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));

        String key = dryRun ? "oimr.command.cleanup.dry_run" : "oimr.command.cleanup.executed";
        context.getSource().sendSuccess(
            () -> OIMRLang.localizeForSource(
                OIMRLang.translatable(
                    key,
                    report.orphanedTeamHives().size(),
                    orphanedSummary,
                    report.inactiveEmptyTeamHives().size(),
                    inactiveSummary,
                    report.stalePersonalStates().size() + " (" + stalePersonalSummary + ")",
                    report.totalCount()
                ),
                context.getSource()
            ),
            !dryRun
        );
        return report.totalCount();
    }
}
