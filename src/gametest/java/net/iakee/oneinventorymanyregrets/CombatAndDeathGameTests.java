package net.iakee.oneinventorymanyregrets;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.iakee.oneinventorymanyregrets.command.OIMRCommand;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.effect.OIMRMobEffects;
import net.iakee.oneinventorymanyregrets.hivemind.HiveFoodDataFieldsAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveFoxAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveLivingEntityAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.HiveServerPlayerGameModeAccess;
import net.iakee.oneinventorymanyregrets.network.DamageIconType;
import net.iakee.oneinventorymanyregrets.network.OIMRAirFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRBondSyncPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRDamageFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRFoodFeedPayload;
import net.iakee.oneinventorymanyregrets.network.OIMRNetworking;
import net.iakee.oneinventorymanyregrets.network.OIMRUnderwaterSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.iakee.oneinventorymanyregrets.HiveMindTestSupport.*;

public class CombatAndDeathGameTests {

    @GameTest
    public void testSharedHealthDamageAndDeathCascade(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice4");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob4");
        try {
            alice.setHealth(20.0F);
            helper.assertTrue(Math.abs(bob.getHealth() - 20.0F) < 0.01F, "Bob starts at 20 HP");

            alice.hurtServer(level, level.damageSources().generic(), 6.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 14.0F) < 0.01F, "Alice HP should be 14");
            helper.assertTrue(Math.abs(bob.getHealth() - 14.0F) < 0.01F, "Bob HP should sync to 14");

            bob.heal(2.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 16.0F) < 0.01F, "Alice HP should heal to 16");

            alice.kill(level);
            helper.assertTrue(alice.isDeadOrDying(), "Alice should be dead");
            helper.assertTrue(bob.isDeadOrDying(), "Bob should die when Alice dies");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testRespawnReattachesAndResetsWhenAllDead(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice6");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob6");
        ServerPlayer respawnedAlice = null;
        ServerPlayer respawnedBob = null;
        try {
            alice.kill(level);
            helper.assertTrue(bob.isDeadOrDying(), "Bob must die with Alice");

            alice.connection.handleClientCommand(
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            respawnedAlice = alice.connection.player;
            respawnedAlice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            helper.assertTrue(Math.abs(respawnedAlice.getHealth() - 20.0F) < 0.01F, "First respawned player resets HP to 20");

            // Alice takes 5 damage before Bob respawns -> Bob should inherit 15 HP on respawn
            boolean hurt = respawnedAlice.hurtServer(level, level.damageSources().generic(), 5.0F);
            helper.assertTrue(hurt && Math.abs(respawnedAlice.getHealth() - 15.0F) < 0.01F,
                "Respawned Alice should take 5 damage (hurt=" + hurt + ", hp=" + respawnedAlice.getHealth()
                    + ", clientLoaded=" + respawnedAlice.connection.hasClientLoaded()
                    + ", changingDim=" + respawnedAlice.isChangingDimension() + ")");
            bob.connection.handleClientCommand(
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            respawnedBob = bob.connection.player;
            respawnedBob.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

            HiveMind hiveAfter = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(Math.abs(respawnedBob.getHealth() - 15.0F) < 0.01F,
                "Second respawning player should sync to live party HP (15.0), got " + respawnedBob.getHealth()
                    + " (hiveHP=" + (hiveAfter != null ? hiveAfter.getHealth() : -1)
                    + ", sameObj=" + (respawnedBob == bob)
                    + ", aliceAttached=" + HiveMindManager.isAttached(respawnedAlice)
                    + ", bobAttached=" + HiveMindManager.isAttached(respawnedBob) + ")");

            respawnedAlice.getInventory().setItem(5, new ItemStack(Items.TORCH, 10));
            helper.assertTrue(respawnedBob.getInventory().getItem(5).getCount() == 10,
                "Respawned players must continue sharing inventory");

            helper.succeed();
        } finally {
            cleanupPlayers(server,
                respawnedAlice != null ? respawnedAlice : alice,
                respawnedBob != null ? respawnedBob : bob);
        }
    }

    @GameTest
    public void testSharedKnockbackSameHiveSlapAndToggle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean previousSetting = OIMRConfig.isSharedKnockbackEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice11");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob11");
        ServerPlayer charlie = spawnTestPlayer(server, level, "HiveCharlie11");
        try {
            OIMRConfig.setSharedKnockback(true);
            alice.setPos(10.0, 64.0, 0.0);
            bob.setPos(12.0, 64.0, 0.0);
            charlie.setPos(12.0, 64.0, 6.0);
            alice.setYRot(-90.0F);
            alice.setDeltaMovement(Vec3.ZERO);
            bob.setDeltaMovement(Vec3.ZERO);
            charlie.setDeltaMovement(Vec3.ZERO);
            alice.syncVelocity = false;
            bob.syncVelocity = false;
            charlie.syncVelocity = false;
            alice.resetAttackStrengthTicker();

            alice.attack(bob);

            Vec3 aliceVel = alice.getDeltaMovement();
            Vec3 charlieVel = charlie.getDeltaMovement();
            helper.assertTrue(aliceVel.x < -0.1 && Math.abs(aliceVel.z) < 0.05 && alice.syncVelocity,
                "Attacker Alice must receive opposite knockback (-X) when slapping teammate Bob (got " + aliceVel + ")");
            helper.assertTrue(charlieVel.x > 0.1 && Math.abs(charlieVel.z) < 0.05 && charlie.syncVelocity,
                "Teammate Charlie must receive victim Bob's knockback (+X) (got " + charlieVel + ")");

            OIMRConfig.setSharedKnockback(false);
            bob.damageCooldownTime = 0;
            alice.setDeltaMovement(Vec3.ZERO);
            bob.setDeltaMovement(Vec3.ZERO);
            charlie.setDeltaMovement(Vec3.ZERO);
            alice.syncVelocity = false;
            charlie.syncVelocity = false;
            alice.resetAttackStrengthTicker();

            alice.attack(bob);

            helper.assertTrue(alice.getDeltaMovement().horizontalDistanceSqr() < 1.0E-9 && !alice.syncVelocity,
                "Attacker must not get recoil knockback when sharedKnockback setting is disabled");
            helper.assertTrue(charlie.getDeltaMovement().horizontalDistanceSqr() < 1.0E-9 && !charlie.syncVelocity,
                "Teammates must not get shared knockback when sharedKnockback setting is disabled");

            helper.succeed();
        } finally {
            OIMRConfig.setSharedKnockback(previousSetting);
            cleanupPlayers(server, alice, bob, charlie);
        }
    }

    @GameTest
    public void testExternalKnockbackAndSettingsPermissions(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam teamA = scoreboard.addPlayerTeam("oimr_kb_a");
        PlayerTeam teamB = scoreboard.addPlayerTeam("oimr_kb_b");
        boolean previousSetting = OIMRConfig.isSharedKnockbackEnabled();
        ServerPlayer alpha = spawnTestPlayer(server, level, "HiveAlpha12");
        ServerPlayer betaVictim = spawnTestPlayer(server, level, "HiveBetaVic12");
        ServerPlayer betaMate = spawnTestPlayer(server, level, "HiveBetaMate12");
        try {
            OIMRConfig.setSharedKnockback(true);
            scoreboard.addPlayerToTeam(alpha.getScoreboardName(), teamA);
            scoreboard.addPlayerToTeam(betaVictim.getScoreboardName(), teamB);
            scoreboard.addPlayerToTeam(betaMate.getScoreboardName(), teamB);

            alpha.setPos(10.0, 64.0, 0.0);
            betaVictim.setPos(12.0, 64.0, 0.0);
            betaMate.setPos(20.0, 64.0, 0.0);
            alpha.setYRot(-90.0F);
            alpha.setDeltaMovement(Vec3.ZERO);
            betaVictim.setDeltaMovement(Vec3.ZERO);
            betaMate.setDeltaMovement(Vec3.ZERO);
            alpha.syncVelocity = false;
            betaMate.syncVelocity = false;
            alpha.resetAttackStrengthTicker();

            alpha.attack(betaVictim);

            helper.assertTrue(betaMate.getDeltaMovement().x > 0.1 && betaMate.syncVelocity,
                "Team B mate must receive knockback when Team B victim is hit (got " + betaMate.getDeltaMovement() + ")");
            helper.assertTrue(alpha.getDeltaMovement().horizontalDistanceSqr() < 1.0E-9 && !alpha.syncVelocity,
                "External attacker from Team A must not receive opposite recoil knockback");

            helper.assertTrue(OIMRCommand.canEditSettings(server.createCommandSourceStack()),
                "Server console must be allowed to edit OIMR settings");
            helper.assertFalse(OIMRCommand.canEditSettings(betaVictim.createCommandSourceStack()),
                "Non-host multiplayer player must not be allowed to edit OIMR settings");

            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings sharedKnockback false");
            helper.assertFalse(OIMRConfig.isSharedKnockbackEnabled(),
                "Console command /oimr settings sharedKnockback false must update OIMRConfig");

            helper.succeed();
        } finally {
            OIMRConfig.setSharedKnockback(previousSetting);
            cleanupPlayers(server, alpha, betaVictim, betaMate);
            scoreboard.removePlayerTeam(teamA);
            scoreboard.removePlayerTeam(teamB);
        }
    }

    @GameTest
    public void testDeathMessagesSecondaryCauses(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice13");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob13");
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        try {
            helper.assertTrue(zombie != null, "Zombie should spawn for combat death test");
            bob.hurtServer(level, level.damageSources().mobAttack(zombie), 2.0F);
            bob.damageCooldownTime = 0;
            bob.hurtServer(level, bob.damageSources().lava(), 50.0F);

            Component aliceDeath = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            helper.assertTrue(aliceDeath != null
                && aliceDeath.getContents() instanceof TranslatableContents tc
                && "death.oimr.lava.player".equals(tc.getKey()),
                "Alice death message should be death.oimr.lava.player with secondary attacker (got " + aliceDeath + ")");
            String text = aliceDeath.getString();
            helper.assertTrue(text.contains("HiveAlice13") && text.contains("HiveBob13") && text.contains("Zombie"),
                "Lava cascade message must include teammate, victim, and Zombie (got '" + text + "')");

            alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            bob.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            ServerPlayer aliceRespawned = server.getPlayerList().getPlayer(alice.getUUID());
            ServerPlayer bobRespawned = server.getPlayerList().getPlayer(bob.getUUID());
            alice = aliceRespawned;
            bob = bobRespawned;
            alice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            bob.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

            bob.hurtServer(level, level.damageSources().mobAttack(zombie), 50.0F);
            Component cascadeDeath = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            helper.assertTrue(cascadeDeath != null
                && cascadeDeath.getContents() instanceof TranslatableContents tc2
                && "death.oimr.cascade".equals(tc2.getKey()),
                "Direct mob kill should wrap victim's full death message via death.oimr.cascade (got " + cascadeDeath + ")");
            String cascadeText = cascadeDeath.getString();
            helper.assertTrue(cascadeText.contains("HiveAlice13") && cascadeText.contains("HiveBob13") && cascadeText.contains("Zombie"),
                "Cascade message must preserve victim cause and attacker (got '" + cascadeText + "')");

            helper.succeed();
        } finally {
            if (zombie != null) {
                zombie.discard();
            }
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testDeathMessagesFriendlyFireAndEnvironment(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean previousComunism = OIMRConfig.isComunismEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice14");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob14");
        ServerPlayer charlie = spawnTestPlayer(server, level, "HiveCharlie14");
        try {
            OIMRConfig.setComunism(false);
            bob.hurtServer(level, level.damageSources().playerAttack(alice), 50.0F);

            Component aliceDeath = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            Component charlieDeath = HiveMindManager.getLastHiveDeathMessage(server, charlie.getUUID());
            helper.assertTrue(aliceDeath != null
                && aliceDeath.getContents() instanceof TranslatableContents atc
                && "death.oimr.friendly_fire.self".equals(atc.getKey()),
                "Attacker Alice should receive friendly_fire.self death message (got " + aliceDeath + ")");
            helper.assertTrue(charlieDeath != null
                && charlieDeath.getContents() instanceof TranslatableContents ctc
                && "death.oimr.friendly_fire.other".equals(ctc.getKey()),
                "Third teammate Charlie should receive friendly_fire.other death message (got " + charlieDeath + ")");
            String charlieText = charlieDeath.getString();
            helper.assertTrue(charlieText.contains("HiveCharlie14")
                && charlieText.contains("HiveAlice14")
                && charlieText.contains("HiveBob14"),
                "Charlie's friendly-fire death message must name Alice and Bob (got '" + charlieText + "')");

            alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            bob.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            charlie.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            alice = server.getPlayerList().getPlayer(alice.getUUID());
            bob = server.getPlayerList().getPlayer(bob.getUUID());
            charlie = server.getPlayerList().getPlayer(charlie.getUUID());
            alice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            bob.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            charlie.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

            bob.hurtServer(level, bob.damageSources().drown(), 50.0F);
            Component drownMsg = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            helper.assertTrue(drownMsg != null
                && drownMsg.getContents() instanceof TranslatableContents dtc
                && "death.oimr.drown".equals(dtc.getKey()),
                "Drown cascade should emit death.oimr.drown (got " + drownMsg + ")");

            alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            bob.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            charlie.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            alice = server.getPlayerList().getPlayer(alice.getUUID());
            bob = server.getPlayerList().getPlayer(bob.getUUID());
            charlie = server.getPlayerList().getPlayer(charlie.getUUID());
            alice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            bob.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            charlie.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

            bob.hurtServer(level, bob.damageSources().starve(), 50.0F);
            Component starveMsg = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            Component bobStarveMsg = HiveMindManager.getLastHiveDeathMessage(server, bob.getUUID());
            helper.assertTrue(starveMsg != null
                && starveMsg.getContents() instanceof TranslatableContents stc
                && "death.oimr.starve.group".equals(stc.getKey())
                && starveMsg.equals(bobStarveMsg),
                "Starve cascade should emit collective death.oimr.starve.group (got " + starveMsg + ")");
            String starveText = starveMsg.getString();
            helper.assertTrue(starveText.contains("HiveAlice14") && starveText.contains("HiveBob14") && starveText.contains("HiveCharlie14"),
                "Collective starve message must list all dying players without Hive Mind jargon (got '" + starveText + "')");

            helper.succeed();
        } finally {
            OIMRConfig.setComunism(previousComunism);
            cleanupPlayers(server, alice, bob, charlie);
        }
    }

    @GameTest
    public void testComunismSettingAndStarveDeathMessage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean previousComunism = OIMRConfig.isComunismEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice15");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob15");
        ServerPlayer charlie = spawnTestPlayer(server, level, "HiveCharlie15");
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings comunism true");
            helper.assertTrue(OIMRConfig.isComunismEnabled(),
                "Console command /oimr settings comunism true must enable comunism setting");

            bob.hurtServer(level, bob.damageSources().starve(), 50.0F);

            Component bobMsg = HiveMindManager.getLastHiveDeathMessage(server, bob.getUUID());
            Component aliceMsg = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            Component charlieMsg = HiveMindManager.getLastHiveDeathMessage(server, charlie.getUUID());
            helper.assertTrue(bobMsg != null
                && bobMsg.getContents() instanceof TranslatableContents btc
                && "death.oimr.comunism.group".equals(btc.getKey()),
                "Victim Bob should get death.oimr.comunism.group (got " + bobMsg + ")");
            helper.assertTrue(aliceMsg != null && aliceMsg.equals(bobMsg) && charlieMsg != null && charlieMsg.equals(bobMsg),
                "All teammates should share the collective communism starvation message on death screens");
            String text = bobMsg.getString();
            helper.assertTrue(text.contains("HiveAlice15") && text.contains("HiveBob15") && text.contains("HiveCharlie15"),
                "Collective communism message must include all living party members (got '" + text + "')");

            helper.succeed();
        } finally {
            OIMRConfig.setComunism(previousComunism);
            cleanupPlayers(server, alice, bob, charlie);
        }
    }

    @GameTest
    public void testCreativeDeathExclusionAndInventoryWipeSettings(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevExclude = OIMRConfig.isExcludeCreativeFromDeathEnabled();
        boolean prevWipe = OIMRConfig.isWipeCreativeInventoryOnDeathEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice17");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob17");
        ServerPlayer aliceRespawned = null;
        ServerPlayer aliceRespawned2 = null;
        try {
            OIMRConfig.setExcludeCreativeFromDeath(true);
            OIMRConfig.setWipeCreativeInventoryOnDeath(true);
            bob.setGameMode(GameType.CREATIVE);
            alice.getInventory().setItem(0, new ItemStack(Items.EMERALD, 5));

            // 1. Default: Creative teammate survives, shared inventory is wiped
            alice.hurtServer(level, level.damageSources().generic(), 50.0F);
            helper.assertTrue(alice.isDeadOrDying(), "Survival Alice must die");
            helper.assertFalse(bob.isDeadOrDying(), "Creative Bob must be excluded from cascade death by default");
            helper.assertTrue(bob.getInventory().getItem(0).isEmpty(),
                "Shared inventory must still be wiped when wipeCreativeInventoryOnDeath is true");

            // 2. Preserve inventory when wipeCreativeInventoryOnDeath = false
            alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            aliceRespawned = alice.connection.player;
            aliceRespawned.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

            OIMRConfig.setWipeCreativeInventoryOnDeath(false);
            aliceRespawned.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
            aliceRespawned.hurtServer(level, level.damageSources().generic(), 50.0F);
            helper.assertTrue(aliceRespawned.isDeadOrDying(), "Survival Alice must die");
            helper.assertFalse(bob.isDeadOrDying(), "Creative Bob must stay alive");
            helper.assertTrue(bob.getInventory().getItem(0).is(Items.GOLD_INGOT) && bob.getInventory().getItem(0).getCount() == 3,
                "Shared inventory must be preserved when wipeCreativeInventoryOnDeath is false");

            // 3. Creative teammate dies in cascade when excludeCreativeFromDeath = false
            aliceRespawned.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            aliceRespawned2 = aliceRespawned.connection.player;
            aliceRespawned2.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

            OIMRConfig.setExcludeCreativeFromDeath(false);
            aliceRespawned2.hurtServer(level, level.damageSources().generic(), 50.0F);
            helper.assertTrue(bob.isDeadOrDying(),
                "Creative Bob must die in cascade when excludeCreativeFromDeath is false");

            helper.succeed();
        } finally {
            OIMRConfig.setExcludeCreativeFromDeath(prevExclude);
            OIMRConfig.setWipeCreativeInventoryOnDeath(prevWipe);
            cleanupPlayers(server,
                aliceRespawned2 != null ? aliceRespawned2 : (aliceRespawned != null ? aliceRespawned : alice),
                bob);
        }
    }

    @GameTest
    public void testNewDeathMessagesKillVoidDirectStarveAndWeapon(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevComunism = OIMRConfig.isComunismEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice21");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob21");
        try {
            OIMRConfig.setComunism(false);

            // 1. /kill -> death.oimr.generic_kill
            bob.kill(level);
            Component killMsg = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            helper.assertTrue(killMsg != null
                && killMsg.getContents() instanceof TranslatableContents ktc
                && "death.oimr.generic_kill".equals(ktc.getKey()),
                "Generic kill must emit death.oimr.generic_kill (got " + killMsg + ")");

            alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            bob.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            alice = alice.connection.player;
            bob = bob.connection.player;
            alice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            bob.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

            // 2. Direct die(starve) with empty CombatTracker entries -> still emits death.oimr.starve.group
            bob.setHealth(0.0F);
            bob.die(bob.damageSources().starve());
            Component starveMsg = HiveMindManager.getLastHiveDeathMessage(server, bob.getUUID());
            Component aliceStarveMsg = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            helper.assertTrue(starveMsg != null
                && starveMsg.getContents() instanceof TranslatableContents stc
                && "death.oimr.starve.group".equals(stc.getKey())
                && starveMsg.equals(aliceStarveMsg),
                "Direct die(starve) without CombatEntry must still emit collective death.oimr.starve.group (got " + starveMsg + ")");

            alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            bob.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            alice = alice.connection.player;
            bob = bob.connection.player;
            alice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            bob.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

            // 3. Friendly-fire projectile must use the weapon fired from, NOT the swapped main-hand tool
            ItemStack customBow = new ItemStack(Items.BOW);
            customBow.set(DataComponents.CUSTOM_NAME, Component.literal("TeamSniperBow"));
            ItemStack customPick = new ItemStack(Items.DIAMOND_PICKAXE);
            customPick.set(DataComponents.CUSTOM_NAME, Component.literal("WrongPickaxe"));
            alice.getInventory().setItem(0, customPick);
            alice.getInventory().setSelectedSlot(0);

            Arrow arrow = new Arrow(level, alice, new ItemStack(Items.ARROW), customBow);
            bob.hurtServer(level, level.damageSources().arrow(arrow, alice), 50.0F);
            arrow.discard();

            Component ffMsg = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            helper.assertTrue(ffMsg != null
                && ffMsg.getContents() instanceof TranslatableContents ftc
                && "death.oimr.friendly_fire.self.item".equals(ftc.getKey()),
                "Arrow friendly fire with custom bow must emit friendly_fire.self.item (got " + ffMsg + ")");
            String ffText = ffMsg.getString();
            helper.assertTrue(ffText.contains("TeamSniperBow") && !ffText.contains("WrongPickaxe"),
                "Death message must reference firing bow TeamSniperBow, not swapped mainhand item (got '" + ffText + "')");

            helper.succeed();
        } finally {
            OIMRConfig.setComunism(prevComunism);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testDeathMessageFallbacksAndLocalization(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice24");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob24");
        try {
            bob.hurtServer(level, level.damageSources().fall(), 50.0F);
            Component aliceDeath = HiveMindManager.getLastHiveDeathMessage(server, alice.getUUID());
            helper.assertTrue(aliceDeath != null
                && aliceDeath.getContents() instanceof TranslatableContents tc
                && "death.oimr.fall".equals(tc.getKey())
                && tc.getFallback() != null
                && !tc.getFallback().isEmpty(),
                "Death message must carry non-empty fallback for vanilla clients (got " + aliceDeath + ")");

            alice.updateOptions(new ClientInformation("pt_br", 8, net.minecraft.world.entity.player.ChatVisiblity.FULL, true, 0,
                net.minecraft.world.entity.HumanoidArm.RIGHT, false, true, net.minecraft.server.level.ParticleStatus.ALL));
            Component ptLocalized = net.iakee.oneinventorymanyregrets.hivemind.OIMRLang.localizeForPlayer(aliceDeath, alice);
            helper.assertTrue(ptLocalized.getContents() instanceof TranslatableContents ptTc
                && ptTc.getFallback() != null
                && ptTc.getFallback().contains("caiu de um lugar alto"),
                "pt_br client must receive Portuguese fallback (got " + ptLocalized + ")");

            bob.updateOptions(new ClientInformation("en_us", 8, net.minecraft.world.entity.player.ChatVisiblity.FULL, true, 0,
                net.minecraft.world.entity.HumanoidArm.RIGHT, false, true, net.minecraft.server.level.ParticleStatus.ALL));
            Component enLocalized = net.iakee.oneinventorymanyregrets.hivemind.OIMRLang.localizeForPlayer(aliceDeath, bob);
            helper.assertTrue(enLocalized.getContents() instanceof TranslatableContents enTc
                && enTc.getFallback() != null
                && enTc.getFallback().contains("hit the ground too hard"),
                "en_us client must receive English fallback (got " + enLocalized + ")");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testAffinityScalingAndDeSyncedDeaths(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice27");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob27");
        try {
            // 1. 50% affinity: damage, healing, effects scale; inventory stays synced; deaths de-sync
            OIMRConfig.setAffinityPercent(50);
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);

            alice.hurtServer(level, level.damageSources().generic(), 10.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 10.0F) < 0.05F,
                "Alice should have 10 HP after 10 damage (got " + alice.getHealth() + ")");
            helper.assertTrue(Math.abs(bob.getHealth() - 15.0F) < 0.05F,
                "Bob should take 50% (5) shared damage to 15 HP (got " + bob.getHealth() + ")");

            alice.heal(4.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 14.0F) < 0.05F
                && Math.abs(bob.getHealth() - 17.0F) < 0.05F,
                "Bob should heal 50% (2 HP) to 17 HP (got " + bob.getHealth() + ")");

            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
            helper.assertTrue(bob.hasEffect(MobEffects.SPEED), "Bob must receive scaled Speed effect");
            helper.assertTrue(bob.getEffect(MobEffects.SPEED).getAmplifier() == 0
                && bob.getEffect(MobEffects.SPEED).getDuration() == 200,
                "Speed II (200t) at 50% affinity must convert to Speed I (amp 0) for full 200 ticks (got amp="
                    + bob.getEffect(MobEffects.SPEED).getAmplifier() + ", dur=" + bob.getEffect(MobEffects.SPEED).getDuration() + ")");
            alice.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 1));
            helper.assertTrue(bob.hasEffect(MobEffects.BLINDNESS)
                && bob.getEffect(MobEffects.BLINDNESS).getAmplifier() == 1
                && Math.abs(bob.getEffect(MobEffects.BLINDNESS).getDuration() - 100) <= 2,
                "Non-subdivided Blindness II (200t) at 50% must preserve amp 1 for 100 ticks");

            // Inventory still syncs regardless of affinity
            alice.getInventory().setItem(12, new ItemStack(Items.GOLD_INGOT, 4));
            helper.assertTrue(bob.getInventory().getItem(12).is(Items.GOLD_INGOT)
                && bob.getInventory().getItem(12).getCount() == 4,
                "Synced inventory slots must be unaffected by affinity");

            // Lethal hit on low-HP Bob does NOT force-kill healthy Alice (Bob has no prior hurt cooldown)
            bob.setHealth(6.0F);
            alice.setHealth(20.0F);
            bob.hurtServer(level, level.damageSources().generic(), 10.0F);
            helper.assertTrue(bob.isDeadOrDying(), "Bob must die from lethal damage");
            helper.assertFalse(alice.isDeadOrDying(), "Alice must NOT obligatorily die when affinity < 100%");
            helper.assertTrue(Math.abs(alice.getHealth() - 15.0F) < 0.05F,
                "Alice should only take 50% of the lethal hit's damage (got " + alice.getHealth() + ")");

            // 2. 0% affinity: zero damage/effect sharing
            bob.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            OIMRConfig.setAffinityPercent(0);
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);
            alice.removeAllEffects();
            bob.hurtServer(level, level.damageSources().generic(), 8.0F);
            bob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
            helper.assertTrue(Math.abs(alice.getHealth() - 20.0F) < 0.01F && !alice.hasEffect(MobEffects.STRENGTH),
                "At 0% affinity Alice must take no shared damage or effects");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testAbsorption1xAndSub100Propagation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        boolean prevChest = OIMRConfig.isSyncChestplateEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice28");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob28");
        try {
            // 1. At 100% affinity, Absorption is a single 1x shared pool
            OIMRConfig.setAffinityPercent(100);
            alice.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 0));
            helper.assertTrue(Math.abs(alice.getAbsorptionAmount() - 4.0F) < 0.01F
                && Math.abs(bob.getAbsorptionAmount() - 4.0F) < 0.01F,
                "Both teammates should share 4.0 absorption at 100% affinity");

            alice.hurtServer(level, level.damageSources().generic(), 2.0F);
            helper.assertTrue(Math.abs(alice.getAbsorptionAmount() - 2.0F) < 0.01F
                && Math.abs(bob.getAbsorptionAmount() - 2.0F) < 0.01F,
                "Damage absorbed by Alice must reduce Bob's shared absorption to 2.0 (got bob=" + bob.getAbsorptionAmount() + ")");

            // 2. At 50% affinity, Absorption effect scales 1x -> Bob gets 50% (2.0), and fully absorbed hit still propagates
            OIMRConfig.setAffinityPercent(50);
            alice.removeAllEffects();
            bob.removeAllEffects();
            alice.setAbsorptionAmount(0.0F);
            bob.setAbsorptionAmount(0.0F);
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);

            alice.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 0));
            helper.assertTrue(Math.abs(alice.getAbsorptionAmount() - 4.0F) < 0.01F
                && Math.abs(bob.getAbsorptionAmount() - 2.0F) < 0.01F,
                "At 50% affinity Bob must receive 2.0 absorption from Alice's Golden Apple (got bob=" + bob.getAbsorptionAmount() + ")");

            alice.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().generic(), 2.0F);
            helper.assertTrue(Math.abs(alice.getAbsorptionAmount() - 2.0F) < 0.01F
                && Math.abs(alice.getHealth() - 20.0F) < 0.01F,
                "Alice should absorb 2.0 damage cleanly");
            helper.assertTrue(Math.abs(bob.getAbsorptionAmount() - 1.0F) < 0.01F
                && Math.abs(bob.getHealth() - 20.0F) < 0.01F,
                "Bob must lose 50% (1.0) absorption even when Alice's damage is 100% absorbed (got bobAbs=" + bob.getAbsorptionAmount() + ")");

            // 3. Protection IV applies 1x on victim, not double-reduced on unarmored teammate
            OIMRConfig.setSyncChestplate(false);
            ItemStack protChest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            protChest.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
            alice.setItemSlot(EquipmentSlot.CHEST, protChest);
            bob.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            alice.removeAllEffects();
            bob.removeAllEffects();
            alice.setAbsorptionAmount(0.0F);
            bob.setAbsorptionAmount(0.0F);
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().mobAttack(alice), 10.0F);
            float aliceDmg = 20.0F - alice.getHealth();
            float bobDmg = 20.0F - bob.getHealth();
            helper.assertTrue(aliceDmg > 0.0F && Math.abs(bobDmg - aliceDmg * 0.5F) < 0.05F,
                "Bob must take exact 50% of Alice's post-Protection damage (aliceDmg=" + aliceDmg + ", bobDmg=" + bobDmg + ")");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setSyncChestplate(prevChest);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testGroupTotemAndRespirationScaling(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        boolean prevOffHand = OIMRConfig.isSyncOffHandEnabled();
        boolean prevHelmet = OIMRConfig.isSyncHelmetEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice31");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob31");
        try {
            // 1. At 100% affinity, Bob's personal offhand Totem saves Alice when Alice takes lethal damage
            OIMRConfig.setAffinityPercent(100);
            OIMRConfig.setSyncOffHand(false);
            alice.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            bob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);

            alice.hurtServer(level, level.damageSources().generic(), 50.0F);

            helper.assertFalse(alice.isDeadOrDying(), "Alice must be saved by teammate Bob's Totem at 100% affinity");
            helper.assertFalse(bob.isDeadOrDying(), "Bob must remain alive when Totem procs for the group");
            helper.assertTrue(bob.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(), "Bob's Totem must be consumed");
            helper.assertTrue(alice.hasEffect(MobEffects.REGENERATION) && bob.hasEffect(MobEffects.REGENERATION),
                "Both teammates must receive Totem Regeneration effects");

            // 2. Respiration affects teammates even with syncHelmet = false, proportional to affinity
            OIMRConfig.setSyncHelmet(false);
            ItemStack respHelmet = new ItemStack(Items.DIAMOND_HELMET);
            respHelmet.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.RESPIRATION), 3);
            alice.setItemSlot(EquipmentSlot.HEAD, respHelmet);
            bob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            ((HiveLivingEntityAccess) alice).oimr$detectEquipmentUpdates();
            ((HiveLivingEntityAccess) bob).oimr$detectEquipmentUpdates();

            // At 50% affinity: Bob has no helmet, Alice has Respiration III (bonus 3.0 -> 50% = 1.5 -> ~40% loss chance vs 100% without)
            OIMRConfig.setAffinityPercent(50);
            int air50 = 300;
            for (int i = 0; i < 200; i++) {
                air50 = ((HiveLivingEntityAccess) bob).oimr$decreaseAirSupply(air50);
            }
            int lost50 = 300 - air50;
            helper.assertTrue(lost50 > 20 && lost50 < 150,
                "At 50% affinity Bob must inherit partial Respiration from Alice (expected ~80 air lost in 200 ticks, got " + lost50 + ")");

            // At 0% affinity: Bob inherits 0% Respiration -> loses all 200 air
            OIMRConfig.setAffinityPercent(0);
            int air0 = 300;
            for (int i = 0; i < 200; i++) {
                air0 = ((HiveLivingEntityAccess) bob).oimr$decreaseAirSupply(air0);
            }
            helper.assertTrue(air0 == 100,
                "At 0% affinity Bob must receive no Respiration bonus from Alice (expected 100 air left, got " + air0 + ")");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setSyncOffHand(prevOffHand);
            OIMRConfig.setSyncHelmet(prevHelmet);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testFractionalDamageAndHealAccumulationAtLowAffinity(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice33");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob33");
        try {
            OIMRConfig.setAffinityPercent(1);
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);

            // 1. Alice takes 10 damage at 1% affinity -> 0.1 shared (< 1.0), held in debt on Bob
            alice.hurtServer(level, level.damageSources().generic(), 10.0F);
            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null, "Default hive must exist");
            helper.assertTrue(Math.abs(bob.getHealth() - 20.0F) < 0.001F,
                "Sub-unit 0.1 shared damage must wait until reaching 1.0 (got bob=" + bob.getHealth() + ")");
            helper.assertTrue(Math.abs(hive.getDamageRemainder(bob.getUUID()) - 0.1D) < 1.0E-5D,
                "Bob must have 0.1 damage remainder stored (got " + hive.getDamageRemainder(bob.getUUID()) + ")");

            // Alice heals back to 20 so she can absorb another 90 damage (0.9 shared)
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            // Take 90 damage in 2 hits (50 + 40) since max HP is 20 -> wait: hurtServer damageTaken is capped?
            // Actually LivingEntity.actuallyHurt passes raw post-armor damage (not capped by remaining HP unless clamped? Let's test 10 hits of 10!)
            for (int i = 0; i < 8; i++) {
                alice.setHealth(20.0F);
                alice.damageCooldownTime = 0;
                alice.hurtServer(level, level.damageSources().generic(), 10.0F);
            }
            // Total so far: 9 * 0.1 = 0.9 -> Bob still at 20.0 HP
            helper.assertTrue(Math.abs(bob.getHealth() - 20.0F) < 0.001F
                && Math.abs(hive.getDamageRemainder(bob.getUUID()) - 0.9D) < 1.0E-5D,
                "After 0.9 total shared damage Bob must still be at 20.0 HP with 0.9 remainder (got hp="
                    + bob.getHealth() + ", rem=" + hive.getDamageRemainder(bob.getUUID()) + ")");

            // 10th hit of 10 -> +0.1 = 1.0 -> applies 1.0 damage to Bob!
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().generic(), 10.0F);
            helper.assertTrue(Math.abs(bob.getHealth() - 19.0F) < 0.01F
                && hive.getDamageRemainder(bob.getUUID()) < 1.0E-5D,
                "Reaching 1.0 total shared damage must apply full 1.0 HP to Bob and clear remainder (got hp="
                    + bob.getHealth() + ", rem=" + hive.getDamageRemainder(bob.getUUID()) + ")");

            // Test overshoot beyond 0.9: 0.1 owed + 1.5 new -> applies 1.6 total damage
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().generic(), 10.0F); // +0.1 stored
            bob.setHealth(20.0F);
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().generic(), 150.0F); // +1.5 -> 1.6 applied
            helper.assertTrue(Math.abs(bob.getHealth() - 18.4F) < 0.02F
                && hive.getDamageRemainder(bob.getUUID()) < 1.0E-5D,
                "Accumulated 0.1 + 1.5 must apply 1.6 damage to Bob (got hp=" + bob.getHealth() + ")");

            // 2. Fractional healing: Bob at 15.0 HP; 10x Alice heal(10.0) at 1% -> 0.1 * 10 = 1.0 -> Bob heals to 16.0
            bob.setHealth(15.0F);
            for (int i = 0; i < 9; i++) {
                alice.setHealth(10.0F);
                alice.heal(10.0F);
            }
            helper.assertTrue(Math.abs(bob.getHealth() - 15.0F) < 0.001F
                && Math.abs(hive.getHealRemainder(bob.getUUID()) - 0.9D) < 1.0E-5D,
                "After 0.9 total shared heal Bob must stay at 15.0 HP with 0.9 remainder (got hp="
                    + bob.getHealth() + ", rem=" + hive.getHealRemainder(bob.getUUID()) + ")");

            alice.setHealth(10.0F);
            alice.heal(10.0F);
            helper.assertTrue(Math.abs(bob.getHealth() - 16.0F) < 0.01F
                && hive.getHealRemainder(bob.getUUID()) < 1.0E-5D,
                "10th heal reaching 1.0 must heal Bob to 16.0 HP (got hp=" + bob.getHealth() + ")");

            // Full-HP Bob must NOT stockpile healing debt
            bob.setHealth(20.0F);
            alice.setHealth(10.0F);
            alice.heal(10.0F);
            helper.assertTrue(hive.getHealRemainder(bob.getUUID()) < 1.0E-5D,
                "Full-HP teammate must not stockpile healing remainder");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testLavaAccumulatesPerPlayerWhileFireIsCappedAtOnePlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        OIMRConfig.MultiMemberScalingMode prevLava = OIMRConfig.getLavaDamageMode();
        OIMRConfig.MultiMemberScalingMode prevDot = OIMRConfig.getDotDamageMode();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice38");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob38");
        ServerPlayer charlie = spawnTestPlayer(server, level, "HiveCharlie38");
        try {
            OIMRConfig.setAffinityPercent(100);
            OIMRConfig.setLavaDamageMode(OIMRConfig.MultiMemberScalingMode.MULTIPLICATIVE);
            OIMRConfig.setDotDamageMode(OIMRConfig.MultiMemberScalingMode.CAP_1X);
            alice.setHealth(20.0F);
            HiveMindManager.onEndServerTick(server);

            // In multiplicative lava mode, every exposed player contributes lava damage: 3 * 4.0F = 12.0F shared damage.
            alice.hurtServer(level, level.damageSources().lava(), 4.0F);
            bob.hurtServer(level, level.damageSources().lava(), 4.0F);
            charlie.hurtServer(level, level.damageSources().lava(), 4.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 8.0F) < 0.01F
                    && Math.abs(bob.getHealth() - 8.0F) < 0.01F
                    && Math.abs(charlie.getHealth() - 8.0F) < 0.01F,
                "3 players in lava simultaneously must deal 3x lava damage (8.0 HP), got alice="
                    + alice.getHealth() + ", bob=" + bob.getHealth() + ", charlie=" + charlie.getHealth());

            // Fire is group-scoped: the same contact/fire tick reported by all 3 members deals only 1.0F total.
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null, "Default hive must exist");
            hive.resetVitalsForRespawn(20.0F);
            HiveMindManager.resyncPlayerFromHive(alice);
            HiveMindManager.resyncPlayerFromHive(bob);
            HiveMindManager.resyncPlayerFromHive(charlie);

            alice.hurtServer(level, level.damageSources().inFire(), 1.0F);
            bob.hurtServer(level, level.damageSources().inFire(), 1.0F);
            charlie.hurtServer(level, level.damageSources().inFire(), 1.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 19.0F) < 0.01F,
                "3 simultaneous fire reports must deal at most 1x fire damage (19.0 HP), got " + alice.getHealth());

            // Fire and lava use independent accounting: one fire hit + three lava hits = 13.0F total.
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            hive.resetVitalsForRespawn(20.0F);
            HiveMindManager.resyncPlayerFromHive(alice);
            HiveMindManager.resyncPlayerFromHive(bob);
            HiveMindManager.resyncPlayerFromHive(charlie);

            alice.hurtServer(level, level.damageSources().inFire(), 1.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().lava(), 4.0F);
            bob.hurtServer(level, level.damageSources().lava(), 4.0F);
            charlie.hurtServer(level, level.damageSources().lava(), 4.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 7.0F) < 0.01F,
                "One fire hit plus 3 lava hits must deal 13.0 total damage (7.0 HP), got "
                    + alice.getHealth());

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setLavaDamageMode(prevLava);
            OIMRConfig.setDotDamageMode(prevDot);
            cleanupPlayers(server, alice, bob, charlie);
        }
    }

    @GameTest
    public void testRandomDeathLootDefaultConfigCommandAndScatter(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        ServerLevel nether = server.getLevel(Level.NETHER);
        boolean prevRandomLoot = OIMRConfig.isRandomDeathLootEnabled();
        boolean prevExcludeCreative = OIMRConfig.isExcludeCreativeFromDeathEnabled();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        TestPlayerHandle aliceHandle = spawnTestPlayerWithChannel(server, overworld, "HiveAlice43");
        TestPlayerHandle bobHandle = spawnTestPlayerWithChannel(server, overworld, "HiveBob43");
        TestPlayerHandle charlieHandle = spawnTestPlayerWithChannel(server, overworld, "HiveCharlie43");
        ServerPlayer alice = aliceHandle.player();
        ServerPlayer bob = bobHandle.player();
        ServerPlayer creativeCharlie = charlieHandle.player();
        try {
            helper.assertTrue(nether != null, "Nether dimension must exist");

            // 1. Default enabled + persistence + command toggle
            helper.assertTrue(OIMRConfig.isRandomDeathLootEnabled(),
                "randomDeathLoot must be enabled by default");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings randomDeathLoot false");
            helper.assertFalse(OIMRConfig.isRandomDeathLootEnabled(),
                "Command /oimr settings randomDeathLoot false must disable randomDeathLoot");
            OIMRConfig.load();
            helper.assertFalse(OIMRConfig.isRandomDeathLootEnabled(),
                "randomDeathLoot=false must persist across OIMRConfig.load()");

            OIMRConfig.setAffinityPercent(100);
            OIMRConfig.setExcludeCreativeFromDeath(true);
            creativeCharlie.setGameMode(GameType.CREATIVE);

            BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
            Vec3 alicePos = new Vec3(origin.getX() + 0.5, origin.getY() + 2.0, origin.getZ() + 0.5);
            Vec3 bobPos = new Vec3(origin.getX() + 40.5, origin.getY() + 2.0, origin.getZ() + 0.5);
            Vec3 charliePos = new Vec3(origin.getX() + 80.5, origin.getY() + 2.0, origin.getZ() + 0.5);
            Vec3 netherBobPos = new Vec3(120.5, 70.0, 0.5);
            overworld.getChunk(BlockPos.containing(alicePos));
            overworld.getChunk(BlockPos.containing(bobPos));
            overworld.getChunk(BlockPos.containing(charliePos));
            int netherChunkX = BlockPos.containing(netherBobPos).getX() >> 4;
            int netherChunkZ = BlockPos.containing(netherBobPos).getZ() >> 4;
            nether.setChunkForced(netherChunkX, netherChunkZ, true);
            server.managedBlock(() -> {
                nether.tick(() -> true);
                return nether.isPositionEntityTicking(BlockPos.containing(netherBobPos));
            });
            java.util.function.Consumer<ServerPlayer> flushPlayerChunks = p -> {
                ServerLevel lvl = p.level();
                lvl.getChunk(p.blockPosition());
                lvl.getChunkSource().chunkMap.move(p);
                p.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
                for (int i = 0; i < 4; i++) {
                    p.connection.chunkSender.sendNextChunks(p);
                    p.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
                }
                lvl.getChunkSource().chunkMap.move(p);
            };

            alice.teleportTo(overworld, alicePos.x, alicePos.y, alicePos.z, java.util.Set.of(), 0.0F, 0.0F, false);
            bob.teleportTo(overworld, bobPos.x, bobPos.y, bobPos.z, java.util.Set.of(), 0.0F, 0.0F, false);
            creativeCharlie.teleportTo(overworld, charliePos.x, charliePos.y, charliePos.z, java.util.Set.of(), 0.0F, 0.0F, false);
            flushPlayerChunks.accept(alice);
            flushPlayerChunks.accept(bob);
            flushPlayerChunks.accept(creativeCharlie);

            java.util.function.Function<EmbeddedChannel, List<Packet<?>>> drainPackets = channel -> {
                List<Packet<?>> out = new java.util.ArrayList<>();
                Object msg;
                while ((msg = channel.outboundMessages().poll()) != null) {
                    if (msg instanceof ClientboundBundlePacket bundle) {
                        for (Packet<?> sub : bundle.subPackets()) {
                            out.add(sub);
                        }
                    } else if (msg instanceof Packet<?> packet) {
                        out.add(packet);
                    }
                }
                return out;
            };

            Runnable clearDroppedEntities = () -> {
                for (Vec3 p : List.of(alicePos, bobPos, charliePos)) {
                    AABB box = new AABB(p.x - 10, p.y - 10, p.z - 10, p.x + 10, p.y + 10, p.z + 10);
                    overworld.getEntitiesOfClass(ItemEntity.class, box).forEach(Entity::discard);
                    overworld.getEntitiesOfClass(ExperienceOrb.class, box).forEach(Entity::discard);
                }
                AABB netherBox = new AABB(netherBobPos.x - 10, netherBobPos.y - 10, netherBobPos.z - 10, netherBobPos.x + 10, netherBobPos.y + 10, netherBobPos.z + 10);
                nether.getEntitiesOfClass(ItemEntity.class, netherBox).forEach(Entity::discard);
                nether.getEntitiesOfClass(ExperienceOrb.class, netherBox).forEach(Entity::discard);
            };

            // 2. Toggle OFF -> loot always drops at original victim (Alice) and remains visible on death screen
            clearDroppedEntities.run();
            aliceHandle.channel().outboundMessages().clear();
            bobHandle.channel().outboundMessages().clear();
            charlieHandle.channel().outboundMessages().clear();
            alice.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
            alice.getInventory().setItem(9, new ItemStack(Items.EMERALD, 4));
            alice.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            alice.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            alice.setExperienceLevels(10);

            alice.hurtServer(overworld, overworld.damageSources().generic(), 50.0F);
            helper.assertTrue(alice.isDeadOrDying() && bob.isDeadOrDying(), "Both Alice and Bob must die");
            helper.assertFalse(creativeCharlie.isDeadOrDying(), "Creative Charlie must survive");
            long offAliceAdds = drainPackets.apply(aliceHandle.channel()).stream()
                .filter(p -> p instanceof ClientboundAddEntityPacket add && add.getType() == EntityTypes.ITEM)
                .count();
            helper.assertTrue(offAliceAdds == 4,
                "With randomDeathLoot OFF, Alice must still see all 4 dropped item entities (got " + offAliceAdds + ")");

            AABB aliceBox = new AABB(alicePos.x - 6, alicePos.y - 6, alicePos.z - 6, alicePos.x + 6, alicePos.y + 6, alicePos.z + 6);
            AABB bobBox = new AABB(bobPos.x - 6, bobPos.y - 6, bobPos.z - 6, bobPos.x + 6, bobPos.y + 6, bobPos.z + 6);
            AABB charlieBox = new AABB(charliePos.x - 6, charliePos.y - 6, charliePos.z - 6, charliePos.x + 6, charliePos.y + 6, charliePos.z + 6);
            AABB netherBobBox = new AABB(netherBobPos.x - 6, netherBobPos.y - 6, netherBobPos.z - 6, netherBobPos.x + 6, netherBobPos.y + 6, netherBobPos.z + 6);

            List<ItemEntity> offItemsAlice = overworld.getEntitiesOfClass(ItemEntity.class, aliceBox);
            List<ItemEntity> offItemsBob = overworld.getEntitiesOfClass(ItemEntity.class, bobBox);
            List<ExperienceOrb> offXpAlice = overworld.getEntitiesOfClass(ExperienceOrb.class, aliceBox);
            List<ExperienceOrb> offXpBob = overworld.getEntitiesOfClass(ExperienceOrb.class, bobBox);
            helper.assertTrue(offItemsAlice.size() == 4
                    && offItemsBob.isEmpty()
                    && !offXpAlice.isEmpty()
                    && offXpBob.isEmpty(),
                "With randomDeathLoot OFF, all 4 item stacks and XP must drop at Alice (itemsAlice="
                    + offItemsAlice.size() + ", itemsBob=" + offItemsBob.size()
                    + ", xpAlice=" + offXpAlice.size() + ", xpBob=" + offXpBob.size() + ")");

            // 3. Toggle ON -> loot is drawn between Alice and Bob (never Creative Charlie), preserves scatter,
            //    and hides all ItemEntity/ExperienceOrb entities from dying players until they respawn
            OIMRConfig.setRandomDeathLoot(true);
            int aliceWins = 0;
            int bobWins = 0;
            boolean verifiedBobScatter = false;
            boolean verifiedPreExistingHiddenAndRespawnRestored = false;
            boolean verifiedSurvivingObserverSeesRealLoot = false;

            for (int round = 0; round < 24; round++) {
                if (alice.isDeadOrDying()) {
                    alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    alice = alice.connection.player;
                    alice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                }
                if (bob.isDeadOrDying()) {
                    bob.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    bob = bob.connection.player;
                    bob.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                }

                alice.teleportTo(overworld, alicePos.x, alicePos.y, alicePos.z, java.util.Set.of(), 0.0F, 0.0F, false);
                bob.teleportTo(overworld, bobPos.x, bobPos.y, bobPos.z, java.util.Set.of(), 0.0F, 0.0F, false);
                // Place surviving Charlie near Bob until verified so we confirm living observers still see real drops
                if (!verifiedSurvivingObserverSeesRealLoot) {
                    creativeCharlie.teleportTo(overworld, bobPos.x + 2.0, bobPos.y, bobPos.z, java.util.Set.of(), 0.0F, 0.0F, false);
                } else {
                    creativeCharlie.teleportTo(overworld, charliePos.x, charliePos.y, charliePos.z, java.util.Set.of(), 0.0F, 0.0F, false);
                }
                flushPlayerChunks.accept(alice);
                flushPlayerChunks.accept(bob);
                flushPlayerChunks.accept(creativeCharlie);
                clearDroppedEntities.run();

                // Spawn a pre-existing ground item near Alice on round 0 to verify it is hidden when death occurs
                ItemEntity preExistingItem = null;
                if (!verifiedPreExistingHiddenAndRespawnRestored) {
                    preExistingItem = new ItemEntity(overworld, alicePos.x + 1.0, alicePos.y, alicePos.z, new ItemStack(Items.COBBLESTONE, 1));
                    overworld.addFreshEntity(preExistingItem);
                    overworld.getChunkSource().chunkMap.move(alice);
                }

                aliceHandle.channel().outboundMessages().clear();
                bobHandle.channel().outboundMessages().clear();
                charlieHandle.channel().outboundMessages().clear();

                alice.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
                alice.getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 5));
                alice.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                alice.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH, 8));
                // Also put an item in Bob's inventory crafting grid to verify teammate container-close items go to drawn target
                bob.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.APPLE, 3));
                alice.setExperienceLevels(10);

                alice.hurtServer(overworld, overworld.damageSources().generic(), 50.0F);

                List<Packet<?>> alicePackets = drainPackets.apply(aliceHandle.channel());
                List<Packet<?>> bobPackets = drainPackets.apply(bobHandle.channel());
                List<Packet<?>> charliePackets = drainPackets.apply(charlieHandle.channel());

                List<ItemEntity> atAlice = overworld.getEntitiesOfClass(ItemEntity.class, aliceBox);
                List<ItemEntity> atBob = overworld.getEntitiesOfClass(ItemEntity.class, bobBox);
                List<ItemEntity> atCharlie = overworld.getEntitiesOfClass(ItemEntity.class, charlieBox);
                List<ExperienceOrb> xpAtAlice = overworld.getEntitiesOfClass(ExperienceOrb.class, aliceBox);
                List<ExperienceOrb> xpAtBob = overworld.getEntitiesOfClass(ExperienceOrb.class, bobBox);

                helper.assertTrue(atCharlie.isEmpty(), "Surviving Creative Charlie must never receive death loot");

                // Neither Alice nor Bob may receive any ITEM or EXPERIENCE_ORB add packets while on the death screen
                long aliceHiddenAdds = alicePackets.stream()
                    .filter(p -> p instanceof ClientboundAddEntityPacket add
                        && (add.getType() == EntityTypes.ITEM || add.getType() == EntityTypes.EXPERIENCE_ORB))
                    .count();
                long bobHiddenAdds = bobPackets.stream()
                    .filter(p -> p instanceof ClientboundAddEntityPacket add
                        && (add.getType() == EntityTypes.ITEM || add.getType() == EntityTypes.EXPERIENCE_ORB))
                    .count();
                helper.assertTrue(aliceHiddenAdds == 0 && bobHiddenAdds == 0,
                    "Dying players must not receive any ItemEntity or ExperienceOrb spawn packets while on death screen (alice="
                        + aliceHiddenAdds + ", bob=" + bobHiddenAdds + ")");

                if (preExistingItem != null) {
                    final int preId = preExistingItem.getId();
                    List<Integer> removedForAlice = alicePackets.stream()
                        .filter(ClientboundRemoveEntitiesPacket.class::isInstance)
                        .map(ClientboundRemoveEntitiesPacket.class::cast)
                        .flatMapToInt(p -> p.entityIds().intStream())
                        .boxed()
                        .toList();
                    helper.assertTrue(removedForAlice.contains(preId),
                        "Pre-existing ground ItemEntity must be hidden (removed from client) when entering death screen");
                    aliceHandle.channel().outboundMessages().clear();
                    alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    alice = alice.connection.player;
                    alice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                    alice.teleportTo(overworld, alicePos.x, alicePos.y, alicePos.z, java.util.Set.of(), 0.0F, 0.0F, false);
                    flushPlayerChunks.accept(alice);
                    boolean seesPreItemAfterRespawn = drainPackets.apply(aliceHandle.channel()).stream()
                        .anyMatch(p -> p instanceof ClientboundAddEntityPacket add && add.getId() == preId);
                    helper.assertTrue(seesPreItemAfterRespawn,
                        "After respawning, ground items must become visible again");
                    verifiedPreExistingHiddenAndRespawnRestored = true;
                    preExistingItem.discard();
                }

                // Subtract pre-existing item if any (already discarded above)
                atAlice = overworld.getEntitiesOfClass(ItemEntity.class, aliceBox);
                boolean droppedAtAliceOnly = atAlice.size() == 5 && atBob.isEmpty() && !xpAtAlice.isEmpty() && xpAtBob.isEmpty();
                boolean droppedAtBobOnly = atBob.size() == 5 && atAlice.isEmpty() && !xpAtBob.isEmpty() && xpAtAlice.isEmpty();
                helper.assertTrue(droppedAtAliceOnly || droppedAtBobOnly,
                    "Round " + round + ": all 5 items and XP must drop together at either Alice or Bob (atAlice="
                        + atAlice.size() + ", atBob=" + atBob.size() + ", xpAlice=" + xpAtAlice.size() + ", xpBob=" + xpAtBob.size() + ")");

                if (droppedAtAliceOnly) {
                    aliceWins++;
                } else {
                    bobWins++;
                    if (!verifiedSurvivingObserverSeesRealLoot) {
                        long charlieVisibleItems = charliePackets.stream()
                            .filter(p -> p instanceof ClientboundAddEntityPacket add && add.getType() == EntityTypes.ITEM)
                            .count();
                        helper.assertTrue(charlieVisibleItems == 5,
                            "Surviving observer near Bob must see all 5 real dropped items (got " + charlieVisibleItems + ")");
                        verifiedSurvivingObserverSeesRealLoot = true;
                    }
                    if (!verifiedBobScatter) {
                        java.util.Set<Vec3> velocities = new java.util.HashSet<>();
                        for (ItemEntity item : atBob) {
                            Vec3 v = item.getDeltaMovement();
                            helper.assertTrue(Math.abs(v.y - 0.2D) < 0.01D,
                                "Redirected death item must keep vanilla upward scatter velocity 0.2 (got " + v.y + ")");
                            helper.assertTrue(item.hasPickUpDelay(),
                                "Redirected death item must keep vanilla pickup delay");
                            velocities.add(v);
                        }
                        helper.assertTrue(velocities.size() >= 3,
                            "Redirected death items must scatter in distinct random directions around Bob (distinct=" + velocities.size() + ")");
                        verifiedBobScatter = true;
                    }
                }
            }

            helper.assertTrue(aliceWins > 0 && bobWins > 0,
                "Over 24 rounds, both original victim Alice and teammate Bob must be drawn (aliceWins="
                    + aliceWins + ", bobWins=" + bobWins + ")");
            helper.assertTrue(verifiedBobScatter && verifiedPreExistingHiddenAndRespawnRestored && verifiedSurvivingObserverSeesRealLoot,
                "Scatter physics, death-screen item hiding, respawn restore, and living observer visibility must all be verified");

            // 4. Cross-dimension check: Alice in Overworld, Bob in Nether -> if Bob is drawn, items spawn in Nether at Bob
            boolean verifiedNetherDrop = false;
            for (int round = 0; round < 20 && !verifiedNetherDrop; round++) {
                if (alice.isDeadOrDying()) {
                    alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    alice = alice.connection.player;
                    alice.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                }
                if (bob.isDeadOrDying()) {
                    bob.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    bob = bob.connection.player;
                    bob.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                }

                alice.teleportTo(overworld, alicePos.x, alicePos.y, alicePos.z, java.util.Set.of(), 0.0F, 0.0F, false);
                bob.teleportTo(nether, netherBobPos.x, netherBobPos.y, netherBobPos.z, java.util.Set.of(), 0.0F, 0.0F, false);
                clearDroppedEntities.run();

                alice.getInventory().setItem(0, new ItemStack(Items.NETHERITE_INGOT, 2));
                alice.setExperienceLevels(10);
                alice.hurtServer(overworld, overworld.damageSources().generic(), 50.0F);

                List<ItemEntity> inOverworld = overworld.getEntitiesOfClass(ItemEntity.class, aliceBox);
                List<ItemEntity> inNether = nether.getEntitiesOfClass(ItemEntity.class, netherBobBox);
                List<ExperienceOrb> xpInNether = nether.getEntitiesOfClass(ExperienceOrb.class, netherBobBox);
                if (inNether.size() == 1 && inOverworld.isEmpty() && !xpInNether.isEmpty()) {
                    verifiedNetherDrop = true;
                } else {
                    helper.assertTrue(inOverworld.size() == 1 && inNether.isEmpty(),
                        "Cross-dimension death must drop all loot in exactly one player's dimension (ow="
                            + inOverworld.size() + ", nether=" + inNether.size()
                            + ", aliceDim=" + alice.level().dimension().identifier()
                            + ", alicePos=" + alice.position()
                            + ", bobDim=" + bob.level().dimension().identifier()
                            + ", bobPos=" + bob.position() + ")");
                }
            }
            helper.assertTrue(verifiedNetherDrop, "Cross-dimension draw to Bob in Nether must drop loot in Nether at Bob");

            helper.succeed();
        } finally {
            nether.setChunkForced(120 >> 4, 0, false);
            OIMRConfig.setRandomDeathLoot(prevRandomLoot);
            OIMRConfig.setExcludeCreativeFromDeath(prevExcludeCreative);
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob, creativeCharlie);
        }
    }

    @GameTest
    public void testLavaDamageModeCap1xVsMultiplicative(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        OIMRConfig.MultiMemberScalingMode prevLava = OIMRConfig.getLavaDamageMode();
        ServerPlayer alice = spawnTestPlayer(server, level, "LavaCapAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "LavaCapBob");
        ServerPlayer charlie = spawnTestPlayer(server, level, "LavaCapCharlie");
        try {
            // 1. Default 1x cap at 100% affinity: 3 simultaneous lava hits deal 1x (4.0 HP -> 16.0 HP)
            OIMRConfig.setAffinityPercent(100);
            OIMRConfig.setLavaDamageMode(OIMRConfig.MultiMemberScalingMode.CAP_1X);
            alice.setHealth(20.0F);
            HiveMindManager.onEndServerTick(server);
            alice.hurtServer(level, level.damageSources().lava(), 4.0F);
            bob.hurtServer(level, level.damageSources().lava(), 4.0F);
            charlie.hurtServer(level, level.damageSources().lava(), 4.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 16.0F) < 0.01F
                    && Math.abs(bob.getHealth() - 16.0F) < 0.01F
                    && Math.abs(charlie.getHealth() - 16.0F) < 0.01F,
                "1x lava cap at 100% affinity must cap 3 simultaneous lava hits to 4.0 damage (16.0 HP), got alice="
                    + alice.getHealth());

            // 2. Partial affinity (50%): Alice & Bob in lava, Charlie on land
            OIMRConfig.setAffinityPercent(50);
            HiveMindManager.onConfigChanged(server);
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);
            charlie.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().lava(), 4.0F);
            bob.hurtServer(level, level.damageSources().lava(), 4.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 16.0F) < 0.01F
                    && Math.abs(bob.getHealth() - 16.0F) < 0.01F
                    && Math.abs(charlie.getHealth() - 18.0F) < 0.01F,
                "1x lava cap at 50% affinity must keep Alice/Bob at 16.0 HP and bystander Charlie at 18.0 HP (got a="
                    + alice.getHealth() + ", b=" + bob.getHealth() + ", c=" + charlie.getHealth() + ")");

            // 3. Multiplicative Nx mode at 50% affinity (plus command switching check)
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings lavaDamageMode nx");
            helper.assertTrue(OIMRConfig.getLavaDamageMode() == OIMRConfig.MultiMemberScalingMode.MULTIPLICATIVE,
                "Command /oimr settings lavaDamageMode nx must switch to MULTIPLICATIVE");
            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null, "Default hive must exist");
            hive.clearHazardWindows();
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);
            charlie.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().lava(), 4.0F);
            bob.hurtServer(level, level.damageSources().lava(), 4.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 14.0F) < 0.01F
                    && Math.abs(bob.getHealth() - 14.0F) < 0.01F
                    && Math.abs(charlie.getHealth() - 16.0F) < 0.01F,
                "Nx lava mode at 50% affinity must propagate both lava hits (a=14, b=14, c=16; got a="
                    + alice.getHealth() + ", b=" + bob.getHealth() + ", c=" + charlie.getHealth() + ")");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setLavaDamageMode(prevLava);
            cleanupPlayers(server, alice, bob, charlie);
        }
    }

    @GameTest
    public void testDotDamageModeCap1xVsMultiplicative(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        OIMRConfig.MultiMemberScalingMode prevDot = OIMRConfig.getDotDamageMode();
        ServerPlayer alice = spawnTestPlayer(server, level, "DotCapAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "DotCapBob");
        ServerPlayer charlie = spawnTestPlayer(server, level, "DotCapCharlie");
        try {
            OIMRConfig.setAffinityPercent(100);
            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null, "Default hive must exist");

            // 1. Default 1x cap: 3 simultaneous cactus hits -> 1.0 damage, and 3 Poison ticks -> 1.0 damage
            OIMRConfig.setDotDamageMode(OIMRConfig.MultiMemberScalingMode.CAP_1X);
            alice.setHealth(20.0F);
            HiveMindManager.onEndServerTick(server);
            alice.hurtServer(level, level.damageSources().cactus(), 1.0F);
            bob.hurtServer(level, level.damageSources().cactus(), 1.0F);
            charlie.hurtServer(level, level.damageSources().cactus(), 1.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 19.0F) < 0.01F,
                "1x DoT cap must limit 3 simultaneous cactus hits to 1.0 damage (got " + alice.getHealth() + ")");

            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            HiveMindManager.onEndServerTick(server);
            alice.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            MobEffectInstance aPoison = alice.getEffect(MobEffects.POISON);
            MobEffectInstance bPoison = bob.getEffect(MobEffects.POISON);
            MobEffectInstance cPoison = charlie.getEffect(MobEffects.POISON);
            helper.assertTrue(aPoison != null && bPoison != null && cPoison != null, "Poison must sync to all 3");
            for (int i = 0; i < 25; i++) {
                aPoison.tickServer(level, alice, () -> {});
                bPoison.tickServer(level, bob, () -> {});
                cPoison.tickServer(level, charlie, () -> {});
            }
            helper.assertTrue(Math.abs(alice.getHealth() - 19.0F) < 0.01F,
                "1x DoT cap must limit synced Poison I over 25 ticks to 1.0 damage (got " + alice.getHealth() + ")");

            // 2. Multiplicative Nx mode: 3 simultaneous cactus hits -> 3.0, Poison -> 3.0, OnFire -> 3.0, Regen stays 1x!
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings dotDamageMode nx");
            helper.assertTrue(OIMRConfig.getDotDamageMode() == OIMRConfig.MultiMemberScalingMode.MULTIPLICATIVE,
                "Command /oimr settings dotDamageMode nx must switch to MULTIPLICATIVE");
            alice.removeAllEffects();
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            HiveMindManager.onEndServerTick(server);

            alice.hurtServer(level, level.damageSources().cactus(), 1.0F);
            bob.hurtServer(level, level.damageSources().cactus(), 1.0F);
            charlie.hurtServer(level, level.damageSources().cactus(), 1.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 17.0F) < 0.01F,
                "Nx DoT mode must deal 3.0 damage for 3 simultaneous cactus hits (got " + alice.getHealth() + ")");

            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            alice.igniteForTicks(20);
            hive.resetTickClaims();
            HiveMindManager.onEndServerTick(server);
            helper.assertTrue(Math.abs(alice.getHealth() - 17.0F) < 0.01F,
                "Nx DoT mode must deal 3.0 burning damage across 3 burning players (got " + alice.getHealth() + ")");

            alice.clearFire();
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            HiveMindManager.onEndServerTick(server);
            alice.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            aPoison = alice.getEffect(MobEffects.POISON);
            bPoison = bob.getEffect(MobEffects.POISON);
            cPoison = charlie.getEffect(MobEffects.POISON);
            for (int i = 0; i < 25; i++) {
                aPoison.tickServer(level, alice, () -> {});
                bPoison.tickServer(level, bob, () -> {});
                cPoison.tickServer(level, charlie, () -> {});
            }
            helper.assertTrue(Math.abs(alice.getHealth() - 17.0F) < 0.01F,
                "Nx DoT mode must deal 3.0 Poison damage over 25 ticks across 3 poisoned players (got " + alice.getHealth() + ")");

            // Regeneration must remain 1x even when dotDamageMode is MULTIPLICATIVE
            alice.removeAllEffects();
            alice.setHealth(10.0F);
            HiveMindManager.onEndServerTick(server);
            alice.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            MobEffectInstance aRegen = alice.getEffect(MobEffects.REGENERATION);
            MobEffectInstance bRegen = bob.getEffect(MobEffects.REGENERATION);
            MobEffectInstance cRegen = charlie.getEffect(MobEffects.REGENERATION);
            for (int i = 0; i < 50; i++) {
                aRegen.tickServer(level, alice, () -> {});
                bRegen.tickServer(level, bob, () -> {});
                cRegen.tickServer(level, charlie, () -> {});
            }
            helper.assertTrue(Math.abs(alice.getHealth() - 11.0F) < 0.01F,
                "Regeneration must remain capped at 1x even in Nx DoT mode (got " + alice.getHealth() + ")");

            // 3. Dragon Breath respects dotDamageMode 1x cap vs Nx multiplicative
            OIMRConfig.setDotDamageMode(OIMRConfig.MultiMemberScalingMode.CAP_1X);
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            hive.clearHazardWindows();
            alice.hurtServer(level, level.damageSources().dragonBreath(), 2.0F);
            bob.hurtServer(level, level.damageSources().dragonBreath(), 2.0F);
            charlie.hurtServer(level, level.damageSources().dragonBreath(), 2.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 18.0F) < 0.01F,
                "1x DoT cap must limit 3 simultaneous dragon breath hits to 2.0 damage (got " + alice.getHealth() + ")");

            OIMRConfig.setDotDamageMode(OIMRConfig.MultiMemberScalingMode.MULTIPLICATIVE);
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            hive.clearHazardWindows();
            alice.hurtServer(level, level.damageSources().dragonBreath(), 2.0F);
            bob.hurtServer(level, level.damageSources().dragonBreath(), 2.0F);
            charlie.hurtServer(level, level.damageSources().dragonBreath(), 2.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 14.0F) < 0.01F,
                "Nx DoT mode must propagate 3 simultaneous dragon breath hits (6.0 damage, got " + alice.getHealth() + ")");

            // 4. Partial affinity (50%): DoT cap 1x vs Nx for Poison and Freeze
            OIMRConfig.setAffinityPercent(50);
            HiveMindManager.onConfigChanged(server);
            OIMRConfig.setDotDamageMode(OIMRConfig.MultiMemberScalingMode.CAP_1X);
            alice.removeAllEffects();
            bob.removeAllEffects();
            charlie.removeAllEffects();
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);
            charlie.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            hive.clearHazardWindows();
            alice.hurtServer(level, level.damageSources().freeze(), 2.0F);
            bob.hurtServer(level, level.damageSources().freeze(), 2.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 18.0F) < 0.01F
                    && Math.abs(bob.getHealth() - 18.0F) < 0.01F
                    && Math.abs(charlie.getHealth() - 19.0F) < 0.01F,
                "1x DoT cap at 50% affinity must cap freeze propagation to bystander Charlie (expected c=19.0, got c="
                    + charlie.getHealth() + ")");

            alice.setHealth(20.0F);
            bob.setHealth(20.0F);
            charlie.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            alice.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            bob.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            aPoison = alice.getEffect(MobEffects.POISON);
            bPoison = bob.getEffect(MobEffects.POISON);
            for (int i = 0; i < 25; i++) {
                aPoison.tickServer(level, alice, () -> {});
                bPoison.tickServer(level, bob, () -> {});
            }
            helper.assertTrue(Math.abs(alice.getHealth() - 19.0F) < 0.01F
                    && Math.abs(bob.getHealth() - 19.0F) < 0.01F
                    && Math.abs(charlie.getHealth() - 20.0F) < 0.01F,
                "1x DoT cap at 50% affinity must suppress periodic Poison cascade without hurting bystander (got a="
                    + alice.getHealth() + ", b=" + bob.getHealth() + ", c=" + charlie.getHealth() + ")");

            OIMRConfig.setDotDamageMode(OIMRConfig.MultiMemberScalingMode.MULTIPLICATIVE);
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);
            charlie.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            charlie.damageCooldownTime = 0;
            hive.clearHazardWindows();
            alice.hurtServer(level, level.damageSources().freeze(), 2.0F);
            bob.hurtServer(level, level.damageSources().freeze(), 2.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 17.0F) < 0.01F
                    && Math.abs(bob.getHealth() - 17.0F) < 0.01F
                    && Math.abs(charlie.getHealth() - 18.0F) < 0.01F,
                "Nx DoT mode at 50% affinity must propagate both freeze hits (expected c=18.0, got c="
                    + charlie.getHealth() + ")");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setDotDamageMode(prevDot);
            cleanupPlayers(server, alice, bob, charlie);
        }
    }
}
