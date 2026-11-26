package net.iakee.oneinventorymanyregrets.hivemind.service;

import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.OIMRLang;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coordinates linked group deaths, localized death messages, random death loot placement,
 * death-screen entity hiding, and shared totem activation.
 */
public final class DeathCascadeService {
    private static final Map<ResourceKey<DamageType>, String> SIMPLE_DEATH_MESSAGE_KEYS = Map.ofEntries(
        Map.entry(DamageTypes.DROWN, "death.oimr.drown"),
        Map.entry(DamageTypes.LAVA, "death.oimr.lava"),
        Map.entry(DamageTypes.IN_FIRE, "death.oimr.fire"),
        Map.entry(DamageTypes.CAMPFIRE, "death.oimr.fire"),
        Map.entry(DamageTypes.ON_FIRE, "death.oimr.fire"),
        Map.entry(DamageTypes.HOT_FLOOR, "death.oimr.fire"),
        Map.entry(DamageTypes.UNATTRIBUTED_FIREBALL, "death.oimr.fire"),
        Map.entry(DamageTypes.FREEZE, "death.oimr.freeze"),
        Map.entry(DamageTypes.WITHER, "death.oimr.wither"),
        Map.entry(DamageTypes.MAGIC, "death.oimr.magic"),
        Map.entry(DamageTypes.INDIRECT_MAGIC, "death.oimr.magic"),
        Map.entry(DamageTypes.GENERIC_KILL, "death.oimr.generic_kill"),
        Map.entry(DamageTypes.GENERIC, "death.oimr.generic_kill"),
        Map.entry(DamageTypes.FALL, "death.oimr.fall"),
        Map.entry(DamageTypes.FELL_OUT_OF_WORLD, "death.oimr.void"),
        Map.entry(DamageTypes.IN_WALL, "death.oimr.in_wall"),
        Map.entry(DamageTypes.CRAMMING, "death.oimr.cramming"),
        Map.entry(DamageTypes.EXPLOSION, "death.oimr.explosion"),
        Map.entry(DamageTypes.PLAYER_EXPLOSION, "death.oimr.explosion"),
        Map.entry(DamageTypes.BAD_RESPAWN_POINT, "death.oimr.bad_respawn_point"),
        Map.entry(DamageTypes.FLY_INTO_WALL, "death.oimr.fly_into_wall"),
        Map.entry(DamageTypes.LIGHTNING_BOLT, "death.oimr.lightning"),
        Map.entry(DamageTypes.OUTSIDE_BORDER, "death.oimr.outside_border"),
        Map.entry(DamageTypes.SONIC_BOOM, "death.oimr.sonic_boom"),
        Map.entry(DamageTypes.CACTUS, "death.oimr.cactus"),
        Map.entry(DamageTypes.SWEET_BERRY_BUSH, "death.oimr.sweet_berry_bush"),
        Map.entry(DamageTypes.STALAGMITE, "death.oimr.stalagmite"),
        Map.entry(DamageTypes.FALLING_STALACTITE, "death.oimr.falling_stalactite"),
        Map.entry(DamageTypes.FALLING_ANVIL, "death.oimr.anvil"),
        Map.entry(DamageTypes.FALLING_BLOCK, "death.oimr.falling_block"),
        Map.entry(DamageTypes.THORNS, "death.oimr.thorns"),
        Map.entry(DamageTypes.ENDER_PEARL, "death.oimr.ender_pearl"),
        Map.entry(DamageTypes.DRAGON_BREATH, "death.oimr.dragon_breath"),
        Map.entry(DamageTypes.DRY_OUT, "death.oimr.dry_out"),
        Map.entry(DamageTypes.STING, "death.oimr.sting"),
        Map.entry(DamageTypes.FIREWORKS, "death.oimr.fireworks"),
        Map.entry(DamageTypes.MACE_SMASH, "death.oimr.mace_smash"),
        Map.entry(DamageTypes.SULFUR_CUBE_HOT, "death.oimr.sulfur_cube_hot")
    );

    private final HiveMindContexts.DeathCascade ctx;

    public DeathCascadeService(HiveMindContexts.DeathCascade ctx) {
        this.ctx = ctx;
    }

    public void onBeforePlayerDie(ServerPlayer player, DamageSource source) {
        this.ctx.dyingDamageSources().put(player.getUUID(), source);
        if (!this.ctx.isAttached(player)) {
            return;
        }
        if (this.ctx.isCascadingDeath()) {
            this.ctx.inventoryAndAmmo().releaseAmmoReservation(player, null);
            if (OIMRConfig.isRandomDeathLootEnabled()
                && !player.isSpectator()
                && this.ctx.deadScreenPlayers().add(player.getUUID())) {
                player.level().getChunkSource().chunkMap.move(player);
            }
            return;
        }
        ServerPlayer chosenLootTarget = selectDeathLootTarget(player);
        this.ctx.inventoryAndAmmo().releaseAmmoReservation(player, chosenLootTarget);
        if (chosenLootTarget != null) {
            this.ctx.activeDeathLootTargets().put(player.getUUID(), chosenLootTarget);
        } else {
            this.ctx.activeDeathLootTargets().remove(player.getUUID());
        }
        hideDroppedEntitiesForDyingHive(player);
        if (player.hasContainerOpen()
            || !player.containerMenu.getCarried().isEmpty()
            || !player.inventoryMenu.getCraftSlots().isEmpty()) {
            this.ctx.setClosingDeathContainer(chosenLootTarget != null && chosenLootTarget != player);
            try {
                player.doCloseContainer();
            } finally {
                this.ctx.setClosingDeathContainer(false);
            }
        }
    }

    private void hideDroppedEntitiesForDyingHive(ServerPlayer victim) {
        if (!OIMRConfig.isRandomDeathLootEnabled() || !OIMRConfig.isFullAffinity() || victim.isSpectator()) {
            return;
        }
        if (this.ctx.deadScreenPlayers().add(victim.getUUID())) {
            victim.level().getChunkSource().chunkMap.move(victim);
        }
        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        if (hiveId == null) {
            return;
        }
        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            if (member == victim || member.isDeadOrDying() || member.isSpectator()) {
                continue;
            }
            if (member.isCreative() && OIMRConfig.isExcludeCreativeFromDeathEnabled()) {
                continue;
            }
            if (this.ctx.deadScreenPlayers().add(member.getUUID())) {
                member.level().getChunkSource().chunkMap.move(member);
            }
        }
    }

    public boolean shouldHideDroppedEntityFromPlayer(Entity entity, ServerPlayer player) {
        if (!OIMRConfig.isRandomDeathLootEnabled()) {
            return false;
        }
        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
            return false;
        }
        if (!this.ctx.isAttached(player)) {
            return false;
        }
        return player.isDeadOrDying() || this.ctx.deadScreenPlayers().contains(player.getUUID());
    }

    private @Nullable ServerPlayer selectDeathLootTarget(ServerPlayer victim) {
        if (!OIMRConfig.isRandomDeathLootEnabled()
            || !OIMRConfig.isFullAffinity()
            || victim.isSpectator()) {
            return null;
        }
        if (victim.level().getGameRules().get(GameRules.KEEP_INVENTORY) || shouldSuppressDeathInventoryDrop(victim)) {
            return null;
        }
        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        if (hiveId == null) {
            return null;
        }
        List<ServerPlayer> candidates = new ArrayList<>();
        candidates.add(victim);
        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            if (member == victim || member.isDeadOrDying() || member.isSpectator()) {
                continue;
            }
            if (member.isCreative() && OIMRConfig.isExcludeCreativeFromDeathEnabled()) {
                continue;
            }
            candidates.add(member);
        }
        if (candidates.size() <= 1) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    public @Nullable ServerPlayer resolveDeathLootTarget(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        ServerPlayer target = this.ctx.activeDeathLootTargets().get(serverPlayer.getUUID());
        if (target == null || target.isRemoved()) {
            return null;
        }
        return target;
    }

    public @Nullable ServerPlayer resolveContainerCloseDropTarget(LivingEntity entity) {
        if (!this.ctx.isClosingDeathContainer() || !(entity instanceof ServerPlayer player)) {
            return null;
        }
        return resolveDeathLootTarget(player);
    }

    public void popDyingDamageSource(ServerPlayer player) {
        this.ctx.dyingDamageSources().remove(player.getUUID());
    }

    public void onAfterPlayerDieReturn(ServerPlayer player) {
        popDyingDamageSource(player);
        if (this.ctx.isCascadingDeath() || player.isDeadOrDying()) {
            return;
        }
        this.ctx.activeDeathLootTargets().remove(player.getUUID());
        this.ctx.capturedDeathContexts().remove(player.getUUID());
        if (this.ctx.deadScreenPlayers().remove(player.getUUID())) {
            player.level().getChunkSource().chunkMap.move(player);
        }
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId != null) {
            for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
                if (!member.isDeadOrDying() && this.ctx.deadScreenPlayers().remove(member.getUUID())) {
                    member.level().getChunkSource().chunkMap.move(member);
                }
            }
        }
    }

    public @Nullable DamageSource peekDyingDamageSource(LivingEntity entity) {
        return this.ctx.dyingDamageSources().get(entity.getUUID());
    }

    public boolean shouldSuppressDeathInventoryDrop(Player player) {
        if (this.ctx.isCascadingDeath() || !(player instanceof ServerPlayer victim) || !this.ctx.isAttached(victim)) {
            return false;
        }
        if (!OIMRConfig.isExcludeCreativeFromDeathEnabled() || OIMRConfig.isWipeCreativeInventoryOnDeathEnabled()) {
            return false;
        }
        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        if (hiveId == null) {
            return false;
        }
        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            if (member != victim && !member.isDeadOrDying() && !member.isSpectator() && member.isCreative()) {
                return true;
            }
        }
        return false;
    }

    public @Nullable Component consumePendingDeathMessage(LivingEntity entity) {
        Component message = this.ctx.pendingDeathMessages().remove(entity.getUUID());
        if (message != null) {
            this.ctx.lastHiveDeathMessages().put(entity.getUUID(), message);
        }
        return message;
    }

    public @Nullable Component getLastHiveDeathMessage(UUID playerId) {
        return this.ctx.lastHiveDeathMessages().get(playerId);
    }

    public boolean consumeSuppressDeathChat(ServerPlayer player) {
        return this.ctx.suppressDeathChat().remove(player.getUUID());
    }

    public @Nullable Component createAndCaptureCollectiveStarveMessage(LivingEntity entity, @Nullable Entity secondary) {
        if (this.ctx.isCascadingDeath() || !(entity instanceof ServerPlayer victim) || !this.ctx.isAttached(victim)) {
            return null;
        }
        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        if (hiveId == null) {
            return null;
        }

        List<Component> names = new ArrayList<>();
        if (OIMRConfig.isFullAffinity()) {
            for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
                if (member == victim
                    || (!member.isDeadOrDying()
                        && !member.isSpectator()
                        && !(member.isCreative() && OIMRConfig.isExcludeCreativeFromDeathEnabled()))) {
                    names.add(member.getDisplayName());
                }
            }
        }
        if (names.isEmpty()) {
            names.add(victim.getDisplayName());
        }

        String prefix = OIMRConfig.isComunismEnabled() ? "death.oimr.comunism." : "death.oimr.starve.";
        Component secondaryName = (secondary != null && secondary != victim) ? secondary.getDisplayName() : null;
        Component result;
        if (names.size() == 1) {
            result = secondaryName != null
                ? OIMRLang.translatable(prefix + "solo.player", names.get(0), secondaryName)
                : OIMRLang.translatable(prefix + "solo", names.get(0));
        } else {
            Component joined = formatPlayerNameList(names);
            result = secondaryName != null
                ? OIMRLang.translatable(prefix + "group.player", joined, secondaryName)
                : OIMRLang.translatable(prefix + "group", joined);
        }

        this.ctx.lastHiveDeathMessages().put(victim.getUUID(), result);
        this.ctx.capturedDeathContexts().put(victim.getUUID(), new HiveMindContexts.CapturedDeathContext(result, secondary, OIMRConfig.isFullAffinity()));
        return result;
    }

    private static Component formatPlayerNameList(List<Component> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return OIMRLang.translatable("death.oimr.list.two", names.get(0), names.get(1));
        }
        Component acc = names.get(0);
        for (int i = 1; i < names.size() - 1; i++) {
            acc = OIMRLang.translatable("death.oimr.list.sep", acc, names.get(i));
        }
        return OIMRLang.translatable("death.oimr.list.end", acc, names.get(names.size() - 1));
    }

    public Component buildAffinityDeathMessage(ServerPlayer other, ServerPlayer victim, DamageSource source) {
        Component otherName = other.getDisplayName();
        Component victimName = victim.getDisplayName();
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != victim && attacker != other) {
            return OIMRLang.translatable("death.oimr.affinity.player", otherName, victimName, attacker.getDisplayName());
        }
        return OIMRLang.translatable("death.oimr.affinity", otherName, victimName);
    }

    public void captureOriginalDeathContext(LivingEntity entity, Component deathMessage, @Nullable Entity secondaryEntity) {
        if (this.ctx.isCascadingDeath() || !(entity instanceof ServerPlayer victim) || !this.ctx.isAttached(victim)) {
            return;
        }
        this.ctx.capturedDeathContexts().put(victim.getUUID(), new HiveMindContexts.CapturedDeathContext(deathMessage, secondaryEntity, false));
    }

    private Component buildTeammateDeathMessage(
        ServerPlayer other,
        ServerPlayer victim,
        String hiveId,
        DamageSource source,
        HiveMindContexts.@Nullable CapturedDeathContext captured
    ) {
        Component otherName = other.getDisplayName();
        Component victimName = victim.getDisplayName();

        Entity directSource = source.getEntity();
        Entity secondary = directSource != null && directSource != victim
            ? directSource
            : (captured != null && captured.secondaryEntity() != victim ? captured.secondaryEntity() : victim.getKillCredit());

        ServerPlayer teammateAttacker = null;
        boolean directTeammateAttack = false;
        if (directSource instanceof ServerPlayer sp
            && sp != victim
            && this.ctx.isAttached(sp)
            && hiveId.equals(this.ctx.getAttachedHiveId(sp.getUUID()))) {
            teammateAttacker = sp;
            directTeammateAttack = true;
        } else if (secondary instanceof ServerPlayer sp
            && sp != victim
            && this.ctx.isAttached(sp)
            && hiveId.equals(this.ctx.getAttachedHiveId(sp.getUUID()))) {
            teammateAttacker = sp;
        }

        if (teammateAttacker != null) {
            ItemStack weapon = ItemStack.EMPTY;
            if (directTeammateAttack) {
                ItemStack sourceWeapon = source.getWeaponItem();
                if (sourceWeapon != null && !sourceWeapon.isEmpty()) {
                    weapon = sourceWeapon;
                } else if (source.getDirectEntity() == teammateAttacker) {
                    weapon = teammateAttacker.getMainHandItem();
                }
            }
            boolean hasCustomWeapon = !weapon.isEmpty() && weapon.has(DataComponents.CUSTOM_NAME);
            if (other == teammateAttacker) {
                if (directTeammateAttack) {
                    return hasCustomWeapon
                        ? OIMRLang.translatable("death.oimr.friendly_fire.self.item", otherName, victimName, weapon.getDisplayName())
                        : OIMRLang.translatable("death.oimr.friendly_fire.self", otherName, victimName);
                }
                return OIMRLang.translatable("death.oimr.friendly_fire.self_indirect", otherName, victimName);
            }
            return hasCustomWeapon
                ? OIMRLang.translatable("death.oimr.friendly_fire.other.item", otherName, teammateAttacker.getDisplayName(), victimName, weapon.getDisplayName())
                : OIMRLang.translatable("death.oimr.friendly_fire.other", otherName, teammateAttacker.getDisplayName(), victimName);
        }

        Component secondaryName = (secondary != null && secondary != victim && secondary != other)
            ? secondary.getDisplayName()
            : null;

        if (source.is(DamageTypes.STARVE)) {
            if (captured != null && captured.collectiveStarve()) {
                return captured.deathMessage();
            }
            String prefix = OIMRConfig.isComunismEnabled() ? "death.oimr.comunism." : "death.oimr.starve.";
            Component joined = formatPlayerNameList(List.of(otherName, victimName));
            return secondaryName != null
                ? OIMRLang.translatable(prefix + "group.player", joined, secondaryName)
                : OIMRLang.translatable(prefix + "group", joined);
        }

        ResourceKey<DamageType> damageTypeKey = source.typeHolder().unwrapKey().orElse(null);
        String mappedKey = damageTypeKey != null ? SIMPLE_DEATH_MESSAGE_KEYS.get(damageTypeKey) : null;
        if (mappedKey != null) {
            return secondaryName != null
                ? OIMRLang.translatable(mappedKey + ".player", otherName, victimName, secondaryName)
                : OIMRLang.translatable(mappedKey, otherName, victimName);
        }

        Component victimMessage = captured != null ? captured.deathMessage() : source.getLocalizedDeathMessage(victim);
        return OIMRLang.translatable("death.oimr.cascade", otherName, victimMessage);
    }

    public void onPlayerDeath(LivingEntity entity, DamageSource source) {
        if (this.ctx.isCascadingDeath() || !(entity instanceof ServerPlayer victim) || !this.ctx.isAttached(victim)) {
            return;
        }
        ServerPlayer chosenLootTarget = this.ctx.activeDeathLootTargets().remove(victim.getUUID());
        HiveMindContexts.CapturedDeathContext captured = this.ctx.capturedDeathContexts().remove(victim.getUUID());
        boolean keepInventory = victim.level().getGameRules().get(GameRules.KEEP_INVENTORY);
        boolean preserveForCreative = !keepInventory && shouldSuppressDeathInventoryDrop(victim);
        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        HiveMind hive = hiveId != null ? this.ctx.data().getIfPresent(hiveId) : null;

        if (!OIMRConfig.isFullAffinity()) {
            this.ctx.pendingDamageSources().remove(victim.getUUID());
            if (hive != null) {
                hive.clearPlayerRemainders(victim.getUUID());
                if (!keepInventory && !preserveForCreative) {
                    hive.resetExperience();
                }
            }
            if (hive != null && !keepInventory && !preserveForCreative) {
                this.ctx.setSyncingExperience(true);
                try {
                    for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
                        if (member == victim || member.isDeadOrDying()) {
                            continue;
                        }
                        member.experienceLevel = 0;
                        member.totalExperience = 0;
                        member.experienceProgress = 0.0F;
                        member.connection.send(new ClientboundSetExperiencePacket(0.0F, 0, 0));
                        member.inventoryMenu.sendAllDataToRemote();
                        ((HiveLivingEntityAccess) member).oimr$detectEquipmentUpdates();
                    }
                } finally {
                    this.ctx.setSyncingExperience(false);
                }
            } else {
                this.ctx.inventoryAndAmmo().broadcastInventoryChanges(victim);
            }
            this.ctx.data().setDirty();
            return;
        }

        if (hive != null) {
            hive.setHealth(0.0F);
            hive.setAbsorption(0.0F);
            hive.setAirSupply(HiveMind.DEFAULT_AIR_SUPPLY);
            hive.setRemainingFireTicks(0);
            hive.setTicksFrozen(0);
            hive.getEffects().clear();
            if (!keepInventory && !preserveForCreative) {
                hive.resetExperience();
            }
            this.ctx.data().setDirty();
        }

        if (hiveId == null) {
            return;
        }

        this.ctx.setCascadingDeath(true);
        try {
            for (ServerPlayer other : this.ctx.getOnlineMembers(hiveId)) {
                if (other == victim || other.isDeadOrDying() || other.isSpectator()) {
                    continue;
                }
                if (other.isCreative() && OIMRConfig.isExcludeCreativeFromDeathEnabled()) {
                    if (!keepInventory && OIMRConfig.isWipeCreativeInventoryOnDeathEnabled()) {
                        other.experienceLevel = 0;
                        other.totalExperience = 0;
                        other.experienceProgress = 0.0F;
                        other.connection.send(new ClientboundSetExperiencePacket(0.0F, 0, 0));
                        other.inventoryMenu.sendAllDataToRemote();
                        ((HiveLivingEntityAccess) other).oimr$detectEquipmentUpdates();
                    }
                    continue;
                }
                if (!keepInventory) {
                    other.experienceLevel = 0;
                    other.totalExperience = 0;
                    other.experienceProgress = 0.0F;
                }
                if (chosenLootTarget != null && !keepInventory && !preserveForCreative) {
                    this.ctx.activeDeathLootTargets().put(other.getUUID(), chosenLootTarget);
                }
                this.ctx.setClosingDeathContainer(chosenLootTarget != null && chosenLootTarget != other && !keepInventory && !preserveForCreative);
                try {
                    other.doCloseContainer();
                } finally {
                    this.ctx.setClosingDeathContainer(false);
                }
                boolean suppressChat = captured != null && captured.collectiveStarve();
                Component customDeathMessage = buildTeammateDeathMessage(other, victim, hiveId, source, captured);
                this.ctx.pendingDeathMessages().put(other.getUUID(), customDeathMessage);
                if (suppressChat) {
                    this.ctx.suppressDeathChat().add(other.getUUID());
                }
                try {
                    other.kill(other.level());
                    if (!other.isDeadOrDying()) {
                        other.setHealth(0.0F);
                        other.die(other.level().damageSources().genericKill());
                    }
                } finally {
                    this.ctx.activeDeathLootTargets().remove(other.getUUID());
                    this.ctx.pendingDeathMessages().remove(other.getUUID());
                    this.ctx.suppressDeathChat().remove(other.getUUID());
                }
            }
        } finally {
            this.ctx.setCascadingDeath(false);
        }

        for (ServerPlayer member : this.ctx.getOnlineMembers(hiveId)) {
            if (!member.isDeadOrDying() && this.ctx.deadScreenPlayers().remove(member.getUUID())) {
                member.level().getChunkSource().chunkMap.move(member);
            }
        }
    }

    public void onTotemActivated(LivingEntity entity) {
        if (this.ctx.isHandlingGroupTotem() || !(entity instanceof ServerPlayer player) || !this.ctx.isAttached(player)) {
            return;
        }
        this.ctx.inventoryAndAmmo().broadcastInventoryChanges(player);
        String hiveId = this.ctx.getAttachedHiveId(player.getUUID());
        if (hiveId == null) {
            return;
        }
        if (!OIMRConfig.isFullAffinity()) {
            this.ctx.pendingDamageSources().remove(player.getUUID());
            HiveMind hive = this.ctx.data().getIfPresent(hiveId);
            if (hive != null) {
                hive.clearPlayerVitalRemainders(player.getUUID());
                this.ctx.data().setDirty();
            }
            return;
        }
        for (ServerPlayer member : this.ctx.getAliveMembers(hiveId)) {
            if (member != player && !member.isSpectator()) {
                member.level().broadcastEntityEvent(member, (byte) 35);
            }
        }
    }

    public boolean tryActivateGroupTotem(LivingEntity entity, DamageSource source) {
        if (this.ctx.isHandlingGroupTotem()
            || !OIMRConfig.isFullAffinity()
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || !(entity instanceof ServerPlayer victim)
            || !this.ctx.isAttached(victim)) {
            return false;
        }
        String hiveId = this.ctx.getAttachedHiveId(victim.getUUID());
        if (hiveId == null) {
            return false;
        }

        List<ServerPlayer> aliveMembers = this.ctx.getAliveMembers(hiveId);
        ServerPlayer totemHolder = null;
        ItemStack totemStack = ItemStack.EMPTY;
        DeathProtection protection = null;

        for (ServerPlayer candidate : aliveMembers) {
            if (candidate == victim || candidate.isSpectator()) {
                continue;
            }
            if (candidate.isCreative() && OIMRConfig.isExcludeCreativeFromDeathEnabled()) {
                continue;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = candidate.getItemInHand(hand);
                DeathProtection candidateProtection = stack.get(DataComponents.DEATH_PROTECTION);
                if (candidateProtection != null) {
                    totemHolder = candidate;
                    totemStack = stack;
                    protection = candidateProtection;
                    break;
                }
            }
            if (protection != null) {
                break;
            }
        }
        if (protection == null || totemHolder == null) {
            return false;
        }

        ItemStack totemCopy = totemStack.copy();
        totemStack.shrink(1);
        totemHolder.awardStat(Stats.ITEM_USED.get(totemCopy.getItem()));
        CriteriaTriggers.USED_TOTEM.trigger(totemHolder, totemCopy);
        totemCopy.causeUseVibration(totemHolder, GameEvent.ITEM_INTERACT_FINISH);

        this.ctx.setHandlingGroupTotem(true);
        try {
            victim.setHealth(1.0F);
            protection.applyEffects(totemCopy, victim);
            for (ServerPlayer member : aliveMembers) {
                if (!member.isSpectator()) {
                    member.level().broadcastEntityEvent(member, (byte) 35);
                }
            }
            this.ctx.inventoryAndAmmo().broadcastInventoryChanges(victim);
        } finally {
            this.ctx.setHandlingGroupTotem(false);
        }
        return true;
    }
}
