package net.iakee.oneinventorymanyregrets;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.iakee.oneinventorymanyregrets.command.OIMRCommand;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.iakee.oneinventorymanyregrets.hivemind.HiveFoodDataFieldsAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveFoxAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMind;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindData;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HiveMindGameTests {

    private static ServerPlayer spawnTestPlayer(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    private static void cleanupPlayers(MinecraftServer server, ServerPlayer... players) {
        ServerScoreboard scoreboard = server.getScoreboard();
        for (ServerPlayer player : players) {
            if (player != null) {
                PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
                if (team != null) {
                    scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
                }
                server.getPlayerList().remove(player);
            }
        }
        HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
        if (hive != null) {
            hive.getItems().clear();
            hive.getEnderChestItems().clear();
            hive.getEquipmentMap().clear();
            hive.resetVitalsForRespawn(20.0F);
            hive.resetExperience();
            hive.setRespawnConfig(null);
            hive.getAdvancements().clear();
            hive.getKnownRecipes().clear();
            hive.getHighlightRecipes().clear();
        }
    }

    @GameTest
    public void testSharedInventoryArmorAndHeldItem(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice1");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob1");
        try {
            alice.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 16));
            alice.getInventory().setItem(2, new ItemStack(Items.IRON_PICKAXE, 1));
            alice.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE, 1));

            alice.getInventory().setSelectedSlot(0);
            bob.getInventory().setSelectedSlot(2);

            helper.assertTrue(bob.getInventory().getItem(0).getCount() == 16, "Bob should see 16 diamonds in slot 0");
            helper.assertTrue(bob.getInventory().getItem(2).is(Items.IRON_PICKAXE), "Bob should see iron pickaxe in slot 2");
            helper.assertTrue(bob.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "Bob should share chestplate");
            helper.assertTrue(alice.getMainHandItem().is(Items.DIAMOND), "Alice should hold diamond");
            helper.assertTrue(bob.getMainHandItem().is(Items.IRON_PICKAXE), "Bob should hold pickaxe independently");

            // Concurrency test: simultaneous edits to slot 0
            alice.getInventory().getItem(0).shrink(5);
            bob.getInventory().getItem(0).grow(3);
            helper.assertTrue(alice.getInventory().getItem(0).getCount() == 14, "Alice should see 14 diamonds after concurrent edit");
            helper.assertTrue(bob.getInventory().getItem(0).getCount() == 14, "Bob should see 14 diamonds after concurrent edit");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testSharedEffectsAndMilkClear(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice2");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob2");
        try {
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));

            helper.assertTrue(bob.hasEffect(MobEffects.SPEED), "Bob should receive Speed from Alice");
            helper.assertTrue(bob.getEffect(MobEffects.SPEED).getAmplifier() == 1, "Bob should have Speed II");

            ClearAllStatusEffectsConsumeEffect.INSTANCE.apply(level, new ItemStack(Items.MILK_BUCKET), bob);

            helper.assertFalse(bob.hasEffect(MobEffects.SPEED), "Bob should have Speed cleared by milk");
            helper.assertFalse(alice.hasEffect(MobEffects.SPEED), "Alice should also have Speed cleared by Bob's milk");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testSharedHungerWithPartyMultiplier(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice3");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob3");
        try {
            alice.getFoodData().setFoodLevel(20);
            alice.getFoodData().setSaturation(0.0F);
            ((HiveFoodDataFieldsAccess) alice.getFoodData()).oimr$setExhaustionLevel(0.0F);

            // With 2 active survival players, each player's exhaustion is halved (1/2)
            alice.causeFoodExhaustion(2.0F);
            bob.causeFoodExhaustion(2.0F);

            float exhaustion = ((HiveFoodDataFieldsAccess) alice.getFoodData()).oimr$getExhaustionLevel();
            helper.assertTrue(Math.abs(exhaustion - 2.0F) < 0.001F,
                "Combined exhaustion of 2 players should equal 1 player's rate (2.0), got " + exhaustion);

            // Trigger exhaustion > 4.0F -> drops foodLevel from 20 to 19 for both
            alice.causeFoodExhaustion(5.0F); // adds +2.5F -> total 4.5F
            alice.getFoodData().tick(alice);

            helper.assertTrue(alice.getFoodData().getFoodLevel() == 19, "Alice foodLevel should drop to 19");
            helper.assertTrue(bob.getFoodData().getFoodLevel() == 19, "Bob foodLevel should drop to 19 too");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

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
    public void testVanillaTeamsNoOverlap(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam redTeam = scoreboard.getPlayerTeam("testred");
        if (redTeam == null) {
            redTeam = scoreboard.addPlayerTeam("testred");
        }
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice5");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob5");
        try {
            alice.getInventory().setItem(0, new ItemStack(Items.APPLE, 4));
            helper.assertTrue(bob.getInventory().getItem(0).is(Items.APPLE), "Both start in default hivemind");

            scoreboard.addPlayerToTeam(bob.getScoreboardName(), redTeam);

            helper.assertTrue(HiveMindManager.getHiveId(alice).equals(HiveMindData.DEFAULT_HIVE_ID), "Alice stays in default");
            helper.assertTrue(HiveMindManager.getHiveId(bob).equals("team:testred"), "Bob moves to team:testred");
            helper.assertTrue(bob.getInventory().getItem(0).isEmpty(), "Bob should not duplicate default items into new team");

            bob.getInventory().setItem(1, new ItemStack(Items.EMERALD, 3));
            helper.assertTrue(alice.getInventory().getItem(1).isEmpty(), "Alice must not see team:testred items");

            bob.setHealth(10.0F);
            helper.assertTrue(Math.abs(alice.getHealth() - 20.0F) < 0.01F, "Teams must not overlap health");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
            scoreboard.removePlayerTeam(redTeam);
        }
    }

    @GameTest
    public void testSavedDataCodecRoundTrip(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);

        HiveMindData original = new HiveMindData();
        HiveMind hive = original.getOrCreate("team:codec_test");
        hive.markInitializedEmpty();
        hive.getItems().set(4, new ItemStack(Items.GOLDEN_APPLE, 7));
        hive.getEnderChestItems().set(10, new ItemStack(Items.ENDER_PEARL, 12));
        hive.getEquipmentMap().put(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET, 1));
        hive.setHealth(12.5F);
        hive.setAbsorption(4.0F);
        hive.getFoodData().setFoodLevel(15);
        hive.getFoodData().setSaturation(3.5F);
        ((HiveFoodDataFieldsAccess) hive.getFoodData()).oimr$setExhaustionLevel(1.25F);
        hive.setExperience(18, 420, 0.75F);
        hive.setEnchantmentSeed(9876543);
        hive.setAirSupply(180);
        hive.setRemainingFireTicks(90);
        hive.setTicksFrozen(45);
        ServerPlayer.RespawnConfig respawnConfig = new ServerPlayer.RespawnConfig(
            LevelData.RespawnData.of(Level.OVERWORLD, new BlockPos(10, 64, -20), 90.0F, 0.0F),
            true
        );
        hive.setRespawnConfig(respawnConfig);
        AdvancementHolder storyRoot = server.getAdvancements().get(Identifier.withDefaultNamespace("story/mine_stone"));
        if (storyRoot != null) {
            for (String c : storyRoot.value().requirements().names()) {
                hive.grantAdvancementCriterion(storyRoot, c);
            }
        }
        RecipeHolder<?> anyRecipe = server.getRecipeManager().getRecipes().stream()
            .filter(r -> !r.value().isSpecial())
            .findFirst()
            .orElse(null);
        if (anyRecipe != null) {
            hive.addRecipes(List.of(anyRecipe));
        }
        hive.putEffect(new MobEffectInstance(MobEffects.HASTE, 300, 1));

        DataResult<Tag> encoded = HiveMindData.CODEC.encodeStart(ops, original);
        Tag tag = encoded.getOrThrow();
        HiveMindData decoded = HiveMindData.CODEC.parse(ops, tag).getOrThrow();

        HiveMind restored = decoded.getIfPresent("team:codec_test");
        helper.assertTrue(restored != null && restored.isInitialized(), "Restored HiveMind must exist and be initialized");
        helper.assertTrue(restored.getItems().get(4).is(Items.GOLDEN_APPLE) && restored.getItems().get(4).getCount() == 7,
            "Restored item slot 4 mismatch");
        helper.assertTrue(restored.getEnderChestItems().get(10).is(Items.ENDER_PEARL) && restored.getEnderChestItems().get(10).getCount() == 12,
            "Restored ender chest slot 10 mismatch");
        helper.assertTrue(restored.getEquipmentMap().get(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET),
            "Restored helmet mismatch");
        helper.assertTrue(Math.abs(restored.getHealth() - 12.5F) < 0.01F, "Restored health mismatch");
        helper.assertTrue(Math.abs(restored.getAbsorption() - 4.0F) < 0.01F, "Restored absorption mismatch");
        helper.assertTrue(restored.getFoodData().getFoodLevel() == 15, "Restored food level mismatch");
        helper.assertTrue(restored.getAirSupply() == 180 && restored.getRemainingFireTicks() == 90 && restored.getTicksFrozen() == 45,
            "Restored environment state mismatch");
        helper.assertTrue(restored.getExperienceLevel() == 18 && restored.getTotalExperience() == 420
            && Math.abs(restored.getExperienceProgress() - 0.75F) < 0.001F, "Restored XP mismatch");
        helper.assertTrue(restored.getEnchantmentSeed() == 9876543, "Restored enchantment seed mismatch");
        helper.assertTrue(restored.getRespawnConfig() != null
            && restored.getRespawnConfig().respawnData().pos().equals(new BlockPos(10, 64, -20)),
            "Restored respawn config mismatch");
        if (storyRoot != null) {
            AdvancementProgress restoredAdv = restored.getAdvancements().get(storyRoot.id());
            helper.assertTrue(restoredAdv != null && restoredAdv.hasProgress(), "Restored advancement missing");
        }
        if (anyRecipe != null) {
            helper.assertTrue(restored.getKnownRecipes().contains(anyRecipe.id()), "Restored recipe missing");
        }
        helper.assertTrue(restored.getEffects().containsKey(MobEffects.HASTE), "Restored Haste effect missing");

        helper.succeed();
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
    public void testSharedExperienceAndEnderChest(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice7");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob7");
        try {
            // Shared XP & levels (gain, anvil/level spend, enchantment spend)
            alice.giveExperienceLevels(10);
            helper.assertTrue(bob.experienceLevel == 10, "Bob should share level 10 from Alice");

            bob.giveExperiencePoints(15);
            helper.assertTrue(alice.totalExperience == bob.totalExperience && alice.totalExperience > 0,
                "Alice should share XP points gained by Bob");

            alice.onEnchantmentPerformed(new ItemStack(Items.DIAMOND_SWORD), 3);
            helper.assertTrue(bob.experienceLevel == 7 && alice.experienceLevel == 7,
                "Enchantment level cost should sync to both players (expected 7, got bob=" + bob.experienceLevel + ")");

            // Shared Ender Chest with independent containers
            helper.assertTrue(alice.getEnderChestInventory() != bob.getEnderChestInventory(),
                "Players must retain distinct PlayerEnderChestContainer instances");
            alice.getEnderChestInventory().setItem(3, new ItemStack(Items.NETHER_STAR, 2));
            helper.assertTrue(bob.getEnderChestInventory().getItem(3).is(Items.NETHER_STAR)
                && bob.getEnderChestInventory().getItem(3).getCount() == 2,
                "Bob should see Nether Stars placed in Ender Chest by Alice");

            bob.getEnderChestInventory().getItem(3).shrink(1);
            helper.assertTrue(alice.getEnderChestInventory().getItem(3).getCount() == 1,
                "Alice should see Bob's concurrent Ender Chest update");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testSharedAdvancementsAndRecipes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice8");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob8");
        try {
            AdvancementHolder adv = server.getAdvancements().get(Identifier.withDefaultNamespace("story/mine_stone"));
            helper.assertTrue(adv != null, "Expected vanilla advancement story/mine_stone");
            for (String criterion : adv.value().requirements().names()) {
                alice.getAdvancements().award(adv, criterion);
            }
            helper.assertTrue(bob.getAdvancements().getOrStartProgress(adv).isDone(),
                "Bob should receive advancement completed by Alice");

            RecipeHolder<?> recipe = server.getRecipeManager().getRecipes().stream()
                .filter(r -> !r.value().isSpecial() && !bob.getRecipeBook().contains(r.id()))
                .findFirst()
                .orElse(null);
            helper.assertTrue(recipe != null, "Expected an unlearned recipe");
            alice.awardRecipes(List.of(recipe));
            helper.assertTrue(bob.getRecipeBook().contains(recipe.id()),
                "Bob should share recipe unlocked by Alice");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testSharedEnvironmentAirFireFreeze(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice9");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob9");
        try {
            alice.setAirSupply(120);
            helper.assertTrue(bob.getAirSupply() == 120, "Bob air supply should sync to 120, got " + bob.getAirSupply());

            alice.igniteForSeconds(4.0F);
            helper.assertTrue(bob.getRemainingFireTicks() == 80,
                "Bob fire ticks should sync to 80, got " + bob.getRemainingFireTicks());

            BlockPos bobPos = bob.blockPosition();
            level.setBlock(bobPos, Blocks.WATER.defaultBlockState(), 3);
            bob.baseTick();
            bob.clearFire();
            level.setBlock(bobPos, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(alice.getRemainingFireTicks() <= 0,
                "Alice fire should extinguish when Bob is in water and calls clearFire");

            // Concurrency check: freezing in the same tick increments at most +1 across the party
            alice.setTicksFrozen(1);
            bob.setTicksFrozen(2);
            helper.assertTrue(alice.getTicksFrozen() == 1 && bob.getTicksFrozen() == 1,
                "Same-tick freeze increments must cap at +1 across party (alice=" + alice.getTicksFrozen()
                    + ", bob=" + bob.getTicksFrozen() + ")");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testSharedEnchantmentSeedAndRespawnPoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice10");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob10");
        try {
            int initialSeed = alice.getEnchantmentSeed();
            helper.assertTrue(initialSeed != 0 && bob.getEnchantmentSeed() == initialSeed,
                "Players in same Hive Mind must start with identical enchantment seed");

            alice.giveExperienceLevels(5);
            alice.onEnchantmentPerformed(new ItemStack(Items.DIAMOND_PICKAXE), 1);
            helper.assertTrue(bob.getEnchantmentSeed() == alice.getEnchantmentSeed(),
                "Bob's enchantment seed must update when Alice enchants");

            ServerPlayer.RespawnConfig newSpawn = new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(Level.OVERWORLD, new BlockPos(42, 70, -15), 0.0F, 0.0F),
                true
            );
            alice.setRespawnPosition(newSpawn, false);
            helper.assertTrue(bob.getRespawnConfig() != null
                && bob.getRespawnConfig().respawnData().pos().equals(new BlockPos(42, 70, -15)),
                "Bob's respawn position must sync with Alice's");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
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

            Component aliceDeath = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
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
            Component cascadeDeath = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
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

            Component aliceDeath = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
            Component charlieDeath = HiveMindManager.getLastHiveDeathMessage(charlie.getUUID());
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
            Component drownMsg = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
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
            Component starveMsg = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
            Component bobStarveMsg = HiveMindManager.getLastHiveDeathMessage(bob.getUUID());
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

            Component bobMsg = HiveMindManager.getLastHiveDeathMessage(bob.getUUID());
            Component aliceMsg = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
            Component charlieMsg = HiveMindManager.getLastHiveDeathMessage(charlie.getUUID());
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

    private static ServerPlayer spawnTestPlayerWithUuid(MinecraftServer server, ServerLevel level, UUID uuid, String name) {
        GameProfile profile = new GameProfile(uuid, name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    @GameTest
    public void testOfflineJoinAfterWipeAndPostLoadSafetyCheck(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        UUID bobUuid = UUID.randomUUID();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice16");
        ServerPlayer bob = spawnTestPlayerWithUuid(server, level, bobUuid, "HiveBob16");
        ServerPlayer bobRejoined = null;
        ServerPlayer aliceRespawned = null;
        try {
            alice.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 10));
            helper.assertTrue(bob.getInventory().getItem(0).getCount() == 10, "Bob should share 10 diamonds");

            // Bob logs off before the wipe
            server.getPlayerList().remove(bob);

            // Alice dies -> hive HP becomes 0 and shared inventory is dropped/wiped
            alice.kill(level);
            helper.assertTrue(alice.isDeadOrDying(), "Alice must be dead");
            HiveMind hiveAfterDeath = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hiveAfterDeath != null && hiveAfterDeath.getItems().get(0).isEmpty(),
                "Shared inventory must be wiped when Alice dies");

            // Bob logs back in while no alive member is online: must spawn alive with full HP and NO backup items
            bobRejoined = spawnTestPlayerWithUuid(server, level, bobUuid, "HiveBob16");
            helper.assertFalse(bobRejoined.isDeadOrDying(), "Rejoining offline member must not die on join");
            helper.assertTrue(Math.abs(bobRejoined.getHealth() - bobRejoined.getMaxHealth()) < 0.01F,
                "Rejoining member must start with full health when group wiped while offline (got " + bobRejoined.getHealth() + ")");
            helper.assertTrue(bobRejoined.getInventory().getItem(0).isEmpty(),
                "Rejoining member must NOT act as an item backup after wipe");

            // Post-load safety check: Bob takes 5 damage (15 HP), Alice respawns and triggers handleAcceptPlayerLoad + server tick
            bobRejoined.hurtServer(level, level.damageSources().generic(), 5.0F);
            alice.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            aliceRespawned = alice.connection.player;
            aliceRespawned.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            helper.assertTrue(Math.abs(aliceRespawned.getHealth() - 15.0F) < 0.01F,
                "Post-load check must keep Alice synced at 15.0 HP, got " + aliceRespawned.getHealth());

            helper.succeed();
        } finally {
            cleanupPlayers(server,
                aliceRespawned != null ? aliceRespawned : alice,
                bobRejoined != null ? bobRejoined : bob);
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
    public void testFriendlyPetsNonHostilityAndToggle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevFriendlyPets = OIMRConfig.isFriendlyPetsEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice18");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob18");
        Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
        Fox fox = EntityTypes.FOX.create(level, EntitySpawnReason.COMMAND);
        Llama llama = EntityTypes.LLAMA.create(level, EntitySpawnReason.COMMAND);
        try {
            helper.assertTrue(wolf != null && fox != null && llama != null, "Pets should spawn");
            wolf.tame(alice);
            ((HiveFoxAccess) fox).oimr$addTrustedEntity(alice);
            llama.tameWithName(alice);

            OIMRConfig.setFriendlyPets(true);
            helper.assertFalse(wolf.canAttack(bob), "Alice's wolf must not attack teammate Bob when friendlyPets is enabled");
            wolf.setTarget(bob);
            helper.assertTrue(wolf.getTarget() == null, "Alice's wolf target must remain null for teammate Bob");

            helper.assertFalse(fox.canAttack(bob), "Alice's trusted fox must not attack teammate Bob");
            fox.setTarget(bob);
            helper.assertTrue(fox.getTarget() == null, "Alice's trusted fox target must remain null for teammate Bob");

            helper.assertFalse(llama.canAttack(bob), "Alice's llama must not attack teammate Bob");
            llama.setTarget(bob);
            helper.assertTrue(llama.getTarget() == null, "Alice's llama target must remain null for teammate Bob");

            OIMRConfig.setFriendlyPets(false);
            helper.assertTrue(wolf.canAttack(bob), "Alice's wolf may attack Bob when friendlyPets is disabled");
            wolf.setTarget(bob);
            helper.assertTrue(wolf.getTarget() == bob, "Alice's wolf should target Bob when friendlyPets is disabled");

            OIMRConfig.setFriendlyPets(true);
            helper.assertFalse(wolf.canAttack(bob), "Re-enabling friendlyPets must immediately forbid attacking Bob");

            helper.succeed();
        } finally {
            if (wolf != null) {
                wolf.discard();
            }
            if (fox != null) {
                fox.discard();
            }
            if (llama != null) {
                llama.discard();
            }
            OIMRConfig.setFriendlyPets(prevFriendlyPets);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testSplashPotionStackingAndNonSplashMaxMerge(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice19");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob19");
        try {
            Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
            alice.setPos(center);
            bob.setPos(center);

            ItemStack splashItem = new ItemStack(Items.SPLASH_POTION);
            splashItem.set(
                DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), List.of(new MobEffectInstance(MobEffects.SPEED, 1200, 0)), Optional.empty())
            );

            ThrownSplashPotion splash = new ThrownSplashPotion(level, alice, splashItem);
            splash.setPos(center.x, center.y + 0.5, center.z);
            splash.onHitAsPotion(level, splashItem, new BlockHitResult(new Vec3(center.x, center.y + 0.5, center.z), Direction.UP, BlockPos.containing(center), false));
            splash.discard();

            MobEffectInstance aliceSpeed = alice.getEffect(MobEffects.SPEED);
            MobEffectInstance bobSpeed = bob.getEffect(MobEffects.SPEED);
            helper.assertTrue(aliceSpeed != null && bobSpeed != null, "Both teammates must receive Speed from splash");
            helper.assertTrue(aliceSpeed.getDuration() >= 2200 && aliceSpeed.getDuration() <= 2400
                && aliceSpeed.getDuration() == bobSpeed.getDuration(),
                "2-player splash hit must sum each hit's potion duration (expected ~2400, got alice="
                    + aliceSpeed.getDuration() + ", bob=" + bobSpeed.getDuration() + ")");

            // Non-splash shorter duration (600) must keep existing max (~2400), NOT shrink and NOT sum
            int beforeShorter = aliceSpeed.getDuration();
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
            helper.assertTrue(alice.getEffect(MobEffects.SPEED).getDuration() == beforeShorter
                && bob.getEffect(MobEffects.SPEED).getDuration() == beforeShorter,
                "Shorter non-splash effect must keep max duration " + beforeShorter);

            // Non-splash longer duration (4000) must take max (4000), NOT sum
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 4000, 0));
            helper.assertTrue(alice.getEffect(MobEffects.SPEED).getDuration() == 4000
                && bob.getEffect(MobEffects.SPEED).getDuration() == 4000,
                "Longer non-splash effect must update to max duration 4000");

            // Another 2-player splash (~2400) while hive already has 4000 -> must stay 4000 (max(4000, 2400))
            ThrownSplashPotion splash2 = new ThrownSplashPotion(level, alice, splashItem);
            splash2.setPos(center.x, center.y + 0.5, center.z);
            splash2.onHitAsPotion(level, splashItem, new BlockHitResult(new Vec3(center.x, center.y + 0.5, center.z), Direction.UP, BlockPos.containing(center), false));
            splash2.discard();
            helper.assertTrue(alice.getEffect(MobEffects.SPEED).getDuration() == 4000
                && bob.getEffect(MobEffects.SPEED).getDuration() == 4000,
                "Splash totaling ~2400 over existing 4000 must keep max 4000, not stack onto existing");

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testHungerWeightMultiplierSetting(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        float prevWeight = OIMRConfig.getHungerWeight();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice20");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob20");
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings hungerWeight 2.5");
            helper.assertTrue(Math.abs(OIMRConfig.getHungerWeight() - 2.5F) < 0.001F,
                "Command must update hungerWeight to 2.5");

            ((HiveFoodDataFieldsAccess) alice.getFoodData()).oimr$setExhaustionLevel(0.0F);
            // (2.5 * 2.0) / 2 = 2.5
            alice.causeFoodExhaustion(2.0F);
            float exhaustion = ((HiveFoodDataFieldsAccess) alice.getFoodData()).oimr$getExhaustionLevel();
            helper.assertTrue(Math.abs(exhaustion - 2.5F) < 0.001F,
                "Exhaustion with weight=2.5 and party=2 must equal 2.5 (got " + exhaustion + ")");

            helper.succeed();
        } finally {
            OIMRConfig.setHungerWeight(prevWeight);
            cleanupPlayers(server, alice, bob);
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
            Component killMsg = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
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
            Component starveMsg = HiveMindManager.getLastHiveDeathMessage(bob.getUUID());
            Component aliceStarveMsg = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
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

            Component ffMsg = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
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
}
