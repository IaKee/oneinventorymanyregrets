package net.iakee.oneinventorymanyregrets;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.iakee.oneinventorymanyregrets.command.OIMRCommand;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
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

public class HiveMindGameTests {

    private static ServerPlayer spawnTestPlayer(MinecraftServer server, ServerLevel level, String name) {
        return spawnTestPlayerWithChannel(server, level, name).player();
    }

    private record TestPlayerHandle(ServerPlayer player, EmbeddedChannel channel) {
    }

    private static TestPlayerHandle spawnTestPlayerWithChannel(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return new TestPlayerHandle(player, channel);
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
            hive.resetTickClaims();
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
    public void testConcurrentSharedToolsAndRangedWeaponsDoNotInterrupt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        TestPlayerHandle aliceHandle = spawnTestPlayerWithChannel(server, level, "HiveAliceConcurrentItem");
        TestPlayerHandle bobHandle = spawnTestPlayerWithChannel(server, level, "HiveBobConcurrentItem");
        ServerPlayer alice = aliceHandle.player();
        ServerPlayer bob = bobHandle.player();
        boolean previousHotbarSync = OIMRConfig.isSyncHotbarEnabled();
        try {
            OIMRConfig.setSyncHotbar(true);
            alice.getInventory().setSelectedSlot(0);
            bob.getInventory().setSelectedSlot(0);

            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            alice.getInventory().setItem(0, pickaxe);
            bob.containerMenu.broadcastChanges();
            ((HiveServerPlayerGameModeAccess) bob.gameMode).oimr$setDestroyingBlockForTest(true);

            ItemStack damagedPickaxe = pickaxe.copy();
            damagedPickaxe.setDamageValue(1);
            helper.assertTrue(HiveMindManager.shouldDeferSharedHeldItemUpdate(
                    bob,
                    new ClientboundSetPlayerInventoryPacket(0, damagedPickaxe)
                ),
                "A teammate's durability update must not interrupt Bob while he mines with the shared pickaxe");

            // 2-arg connection.send(packet, listener) must also be deferred while mining
            bobHandle.channel().outboundMessages().clear();
            bob.connection.send(new ClientboundSetPlayerInventoryPacket(0, damagedPickaxe), null);
            helper.assertTrue(bobHandle.channel().outboundMessages().isEmpty(),
                "ServerCommonPacketListenerImpl.send(packet, listener) must defer held-item update while mining");

            // ClientboundContainerSetContentPacket must mask slot 0 while keeping other slots updated
            alice.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 5));
            alice.getInventory().setItem(0, damagedPickaxe);
            bobHandle.channel().outboundMessages().clear();
            bob.inventoryMenu.sendAllDataToRemote();
            ClientboundContainerSetContentPacket maskedContent = null;
            Object rawMsg;
            while ((rawMsg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (rawMsg instanceof ClientboundContainerSetContentPacket content) {
                    maskedContent = content;
                }
            }
            helper.assertTrue(maskedContent != null,
                "sendAllDataToRemote() must emit ClientboundContainerSetContentPacket");
            int hotbar0MenuIdx = bob.inventoryMenu.slots.stream()
                .filter(s -> s.container == bob.getInventory() && s.getContainerSlot() == 0)
                .findFirst()
                .orElseThrow()
                .index;
            int hotbar1MenuIdx = bob.inventoryMenu.slots.stream()
                .filter(s -> s.container == bob.getInventory() && s.getContainerSlot() == 1)
                .findFirst()
                .orElseThrow()
                .index;
            helper.assertTrue(maskedContent.items().get(hotbar0MenuIdx).getDamageValue() == 0,
                "Masked ContainerSetContentPacket must preserve undamaged pickaxe in slot 0 during mining");
            helper.assertTrue(maskedContent.items().get(hotbar1MenuIdx).is(Items.COBBLESTONE)
                    && maskedContent.items().get(hotbar1MenuIdx).getCount() == 5,
                "Masked ContainerSetContentPacket must still sync non-protected slot 1");

            // Replacing slot 0 with a different item type must sync immediately (not ghost-mine)
            ItemStack dirt = new ItemStack(Items.DIRT, 1);
            alice.getInventory().setItem(0, dirt);
            helper.assertFalse(HiveMindManager.shouldDeferSharedHeldItemUpdate(
                    bob,
                    new ClientboundSetPlayerInventoryPacket(0, dirt)
                ),
                "Replacing the held tool with a different item must sync immediately");

            ((HiveServerPlayerGameModeAccess) bob.gameMode).oimr$setDestroyingBlockForTest(false);
            for (ItemStack rangedWeapon : List.of(new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW))) {
                alice.getInventory().setItem(0, rangedWeapon);
                bob.containerMenu.broadcastChanges();
                bob.startUsingItem(InteractionHand.MAIN_HAND);

                ItemStack changedWeapon = rangedWeapon.copy();
                changedWeapon.setDamageValue(1);
                helper.assertTrue(HiveMindManager.shouldDeferSharedHeldItemUpdate(
                        bob,
                        new ClientboundSetPlayerInventoryPacket(0, changedWeapon)
                    ),
                    rangedWeapon.getHoverName().getString()
                        + " must keep charging when another player updates the same shared item");
                bob.stopUsingItem();
            }

            ItemStack lastUsePickaxe = new ItemStack(Items.IRON_PICKAXE);
            lastUsePickaxe.setDamageValue(lastUsePickaxe.getMaxDamage() - 1);
            alice.getInventory().setItem(0, lastUsePickaxe);
            bob.containerMenu.broadcastChanges();
            ((HiveServerPlayerGameModeAccess) bob.gameMode).oimr$setDestroyingBlockForTest(true);
            helper.assertFalse(HiveMindManager.shouldDeferSharedHeldItemUpdate(
                    bob,
                    new ClientboundSetPlayerInventoryPacket(0, ItemStack.EMPTY)
                ),
                "The authoritative empty stack must be sent immediately when the shared tool breaks");

            alice.getInventory().setItem(0, ItemStack.EMPTY);
            helper.assertFalse(HiveMindManager.shouldDeferSharedHeldItemUpdate(
                    bob,
                    new ClientboundSetPlayerInventoryPacket(0, lastUsePickaxe)
                ),
                "A stale usable packet must never revive a shared item that is empty on the server");

            helper.succeed();
        } finally {
            ((HiveServerPlayerGameModeAccess) bob.gameMode).oimr$setDestroyingBlockForTest(false);
            bob.stopUsingItem();
            OIMRConfig.setSyncHotbar(previousHotbarSync);
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
        hive.setXpRemainder(0.375D);
        hive.setXpLevelRemainder(0.625D);
        UUID remPlayer = UUID.fromString("11111111-2222-3333-4444-555555555555");
        hive.setDamageRemainder(remPlayer, 0.45D);
        hive.setHealRemainder(remPlayer, 0.25D);
        hive.setAbsorptionRemainder(remPlayer, 0.35D);
        hive.setHealthBoostRemainder(remPlayer, 0.75D);
        hive.setInfestedChanceScale(remPlayer, 0.5D);
        hive.setEffectDurationRemainder(remPlayer, MobEffects.SPEED, 0.8D);
        hive.setEffectTriggerRemainder(remPlayer, MobEffects.BAD_OMEN, 0.5D);
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
        helper.assertTrue(Math.abs(restored.getXpRemainder() - 0.375D) < 1.0E-6D
            && Math.abs(restored.getXpLevelRemainder() - 0.625D) < 1.0E-6D,
            "Restored XP remainders mismatch");
        helper.assertTrue(Math.abs(restored.getDamageRemainder(remPlayer) - 0.45D) < 1.0E-6D
            && Math.abs(restored.getHealRemainder(remPlayer) - 0.25D) < 1.0E-6D
            && Math.abs(restored.getAbsorptionRemainder(remPlayer) - 0.35D) < 1.0E-6D
            && Math.abs(restored.getHealthBoostRemainder(remPlayer) - 0.75D) < 1.0E-6D
            && Math.abs(restored.getInfestedChanceScale(remPlayer) - 0.5D) < 1.0E-6D
            && Math.abs(restored.getEffectDurationRemainder(remPlayer, MobEffects.SPEED) - 0.8D) < 1.0E-6D
            && Math.abs(restored.getEffectTriggerRemainder(remPlayer, MobEffects.BAD_OMEN) - 0.5D) < 1.0E-6D,
            "Restored per-player remainders mismatch");
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
            alice.setExperienceLevels(10);
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
        BlockPos bobPos = helper.absolutePos(new BlockPos(2, 2, 2));
        try {
            alice.teleportTo(bobPos.getX() + 0.5, bobPos.getY(), bobPos.getZ() + 0.5);
            bob.teleportTo(bobPos.getX() + 0.5, bobPos.getY(), bobPos.getZ() + 0.5);

            alice.setAirSupply(120);
            helper.assertTrue(bob.getAirSupply() == 120, "Bob air supply should sync to 120, got " + bob.getAirSupply());

            alice.igniteForSeconds(4.0F);
            helper.assertTrue(bob.getRemainingFireTicks() == 80,
                "Bob fire ticks should sync to 80, got " + bob.getRemainingFireTicks());

            level.setBlock(bobPos, Blocks.WATER.defaultBlockState(), 3);
            bob.baseTick();
            helper.assertTrue(bob.isInWater(), "Bob must detect water in loaded test area");
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
            level.setBlock(bobPos, Blocks.AIR.defaultBlockState(), 3);
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

    @GameTest
    public void testSharedVillagerReputationPricingAndGolemAggro(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam isolatedTeam = scoreboard.addPlayerTeam("oimr_villager_iso");
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice22");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob22");
        ServerPlayer charlie = spawnTestPlayer(server, level, "HiveCharlie22");
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
        ZombieVillager zombieVillager = EntityTypes.ZOMBIE_VILLAGER.create(level, EntitySpawnReason.COMMAND);
        Villager curedVillager = null;
        try {
            helper.assertTrue(villager != null && golem != null && zombieVillager != null,
                "Villager, IronGolem, and ZombieVillager must spawn");

            scoreboard.addPlayerToTeam(charlie.getScoreboardName(), isolatedTeam);

            Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
            alice.setPos(pos.x, pos.y, pos.z);
            bob.setPos(pos.x + 1.0, pos.y, pos.z);
            charlie.setPos(pos.x + 15.0, pos.y, pos.z);
            villager.setPos(pos.x + 0.5, pos.y, pos.z + 0.5);
            villager.setVillagerData(
                villager.getVillagerData()
                    .withProfession(level.registryAccess(), VillagerProfession.LIBRARIAN)
                    .withLevel(1)
            );
            MerchantOffers customOffers = new MerchantOffers();
            customOffers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BOOKSHELF, 1), 16, 1, 0.2F));
            villager.setOffers(customOffers);
            level.addFreshEntity(villager);

            // 1. Cure reputation is shared with teammates and isolated from other teams
            level.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, alice, villager);
            helper.assertTrue(villager.getPlayerReputation(alice) == 125 && villager.getPlayerReputation(bob) == 125,
                "Alice and Bob must share 125 cure reputation (alice=" + villager.getPlayerReputation(alice)
                    + ", bob=" + villager.getPlayerReputation(bob) + ")");
            helper.assertTrue(villager.getPlayerReputation(charlie) == 0,
                "Charlie in isolated team must remain at 0 reputation");

            villager.mobInteract(alice, InteractionHand.MAIN_HAND);
            int aliceDiscountPrice = villager.getOffers().getFirst().getCostA().getCount();
            alice.doCloseContainer();

            villager.mobInteract(bob, InteractionHand.MAIN_HAND);
            int bobDiscountPrice = villager.getOffers().getFirst().getCostA().getCount();
            bob.doCloseContainer();

            charlie.setPos(pos.x + 1.0, pos.y, pos.z);
            villager.mobInteract(charlie, InteractionHand.MAIN_HAND);
            int charlieBasePrice = villager.getOffers().getFirst().getCostA().getCount();
            charlie.doCloseContainer();
            charlie.setPos(pos.x + 15.0, pos.y, pos.z);

            helper.assertTrue(aliceDiscountPrice == 1 && bobDiscountPrice == 1 && charlieBasePrice == 20,
                "Alice & Bob must see identical discounted price 1 while Charlie sees base 20 (alice="
                    + aliceDiscountPrice + ", bob=" + bobDiscountPrice + ", charlie=" + charlieBasePrice + ")");

            // 2. Cumulative hit penalty without recursion: Alice hits -> +5 markup, Bob hits -> +10 markup for both
            villager.getGossips().clear();
            villager.hurtServer(level, level.damageSources().playerAttack(alice), 1.0F);
            helper.assertTrue(villager.getPlayerReputation(alice) == -25 && villager.getPlayerReputation(bob) == -25,
                "First hit must drop shared reputation to -25 (alice=" + villager.getPlayerReputation(alice)
                    + ", bob=" + villager.getPlayerReputation(bob) + ")");

            villager.mobInteract(alice, InteractionHand.MAIN_HAND);
            int aliceAfterFirstHit = villager.getOffers().getFirst().getCostA().getCount();
            alice.doCloseContainer();
            villager.mobInteract(bob, InteractionHand.MAIN_HAND);
            int bobAfterFirstHit = villager.getOffers().getFirst().getCostA().getCount();
            bob.doCloseContainer();
            helper.assertTrue(aliceAfterFirstHit == 25 && bobAfterFirstHit == 25,
                "First hit must raise price to 25 for both Alice and Bob (got alice="
                    + aliceAfterFirstHit + ", bob=" + bobAfterFirstHit + ")");

            villager.damageCooldownTime = 0;
            villager.hurtServer(level, level.damageSources().playerAttack(bob), 1.0F);
            helper.assertTrue(villager.getPlayerReputation(alice) == -50 && villager.getPlayerReputation(bob) == -50,
                "Second hit by Bob must stack reputation penalty to -50 for both (got alice="
                    + villager.getPlayerReputation(alice) + ", bob=" + villager.getPlayerReputation(bob) + ")");

            villager.mobInteract(alice, InteractionHand.MAIN_HAND);
            int aliceAfterSecondHit = villager.getOffers().getFirst().getCostA().getCount();
            int bobAfterSecondHit;
            // Real-time update while Alice still has MerchantMenu open: Bob heals reputation via cure event
            level.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, bob, villager);
            int liveUpdatedPrice = villager.getOffers().getFirst().getCostA().getCount();
            // Hero of the Village gained by Bob also refreshes Alice's open trade screen immediately
            bob.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 200, 0));
            int liveHeroPrice = villager.getOffers().getFirst().getCostA().getCount();
            alice.doCloseContainer();
            bob.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);

            // Re-check second hit cumulative markup separately after resetting gossips? Already observed 30 before cure:
            villager.mobInteract(bob, InteractionHand.MAIN_HAND);
            bobAfterSecondHit = villager.getOffers().getFirst().getCostA().getCount();
            bob.doCloseContainer();

            helper.assertTrue(aliceAfterSecondHit == 30,
                "Second hit by Bob must raise price further to 30 (got " + aliceAfterSecondHit + ")");
            helper.assertTrue(liveUpdatedPrice == 5,
                "Alice's open trade screen must update immediately to 5 when Bob adds +125 cure rep (got " + liveUpdatedPrice + ")");
            helper.assertTrue(liveHeroPrice == 1,
                "Alice's open trade screen must update immediately to 1 when Bob gets Hero of the Village (got " + liveHeroPrice + ")");
            helper.assertTrue(bobAfterSecondHit == 5,
                "Bob must see the same 5 price after Hero of the Village is cleared (got " + bobAfterSecondHit + ")");

            // 3. Shared Iron Golem aggro when reputation <= -100
            villager.getGossips().clear();
            level.onReputationEvent(ReputationEventType.VILLAGER_KILLED, alice, villager);
            helper.assertTrue(villager.getPlayerReputation(bob) <= -100,
                "Killing a villager by Alice must drop Bob's reputation <= -100 (got " + villager.getPlayerReputation(bob) + ")");
            golem.setPos(pos.x + 1.0, pos.y, pos.z + 0.5);
            level.addFreshEntity(golem);
            alice.setPos(pos.x + 30.0, pos.y, pos.z);
            bob.setPos(pos.x + 1.2, pos.y, pos.z + 0.5);
            DefendVillageTargetGoal defendGoal = new DefendVillageTargetGoal(golem);
            helper.assertTrue(defendGoal.canUse(),
                "Iron Golem must target teammate Bob when Hive Mind reputation <= -100");
            defendGoal.start();
            helper.assertTrue(golem.getTarget() == bob,
                "Iron Golem target must be Bob after Alice angered the village");

            // 4. Offline ZombieVillager cure still awards Hive Mind reputation
            alice.setPos(pos.x, pos.y, pos.z);
            zombieVillager.setPos(pos.x + 4.0, pos.y, pos.z + 4.0);
            level.addFreshEntity(zombieVillager);
            zombieVillager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
            alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 1));
            zombieVillager.mobInteract(alice, InteractionHand.MAIN_HAND);
            server.getPlayerList().remove(alice);
            alice = null;

            zombieVillager.setVillagerConversionTime(1);
            zombieVillager.tick();

            List<Villager> nearby = level.getEntitiesOfClass(
                Villager.class,
                new AABB(pos.x + 4.0, pos.y, pos.z + 4.0, pos.x + 4.0, pos.y, pos.z + 4.0).inflate(3.0),
                v -> v != villager
            );
            helper.assertTrue(!nearby.isEmpty(), "Converted villager must spawn after zombie villager cure");
            curedVillager = nearby.getFirst();
            helper.assertTrue(curedVillager.getPlayerReputation(bob) == 125,
                "Bob must receive 125 cure reputation even when starter Alice logged off before cure finished (got "
                    + curedVillager.getPlayerReputation(bob) + ")");

            helper.succeed();
        } finally {
            if (villager != null) {
                villager.discard();
            }
            if (golem != null) {
                golem.discard();
            }
            if (zombieVillager != null && !zombieVillager.isRemoved()) {
                zombieVillager.discard();
            }
            if (curedVillager != null) {
                curedVillager.discard();
            }
            cleanupPlayers(server, alice, bob, charlie);
            scoreboard.removePlayerTeam(isolatedTeam);
        }
    }

    @GameTest
    public void testSharedEnchantmentTableOffersXpAndDeterminism(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevExclude = OIMRConfig.isExcludeCreativeFromDeathEnabled();
        boolean prevWipe = OIMRConfig.isWipeCreativeInventoryOnDeathEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice23");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob23");
        BlockPos tablePos = helper.absolutePos(new BlockPos(5, 2, 5));
        try {
            level.setBlock(tablePos, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        level.setBlock(tablePos.offset(dx, 0, dz), Blocks.BOOKSHELF.defaultBlockState(), 3);
                    }
                }
            }
            alice.setPos(tablePos.getX() + 0.5, tablePos.getY() + 1.0, tablePos.getZ() + 0.5);
            bob.setPos(tablePos.getX() + 0.5, tablePos.getY() + 1.0, tablePos.getZ() + 0.5);

            alice.setExperienceLevels(30);
            helper.assertTrue(bob.experienceLevel == 30, "Bob must start at shared level 30");

            alice.openMenu(level.getBlockState(tablePos).getMenuProvider(level, tablePos));
            bob.openMenu(level.getBlockState(tablePos).getMenuProvider(level, tablePos));
            helper.assertTrue(alice.containerMenu instanceof EnchantmentMenu && bob.containerMenu instanceof EnchantmentMenu,
                "Both players must have EnchantmentMenu open");
            EnchantmentMenu aliceMenu = (EnchantmentMenu) alice.containerMenu;
            EnchantmentMenu bobMenu = (EnchantmentMenu) bob.containerMenu;

            aliceMenu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            aliceMenu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
            bobMenu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            bobMenu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));

            helper.assertTrue(aliceMenu.getEnchantmentSeed() == bobMenu.getEnchantmentSeed()
                && aliceMenu.getEnchantmentSeed() != 0,
                "Enchantment menus must share the same non-zero seed");
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(aliceMenu.costs[i] == bobMenu.costs[i]
                    && aliceMenu.enchantClue[i] == bobMenu.enchantClue[i]
                    && aliceMenu.levelClue[i] == bobMenu.levelClue[i],
                    "Offers at slot " + i + " must match between Alice and Bob");
            }

            // 1. Concurrent low-XP enchant guard: 1 level -> Alice enchants slot 0 (cost 1), Bob is blocked at 0
            alice.setExperienceLevels(1);
            int seed0 = alice.getEnchantmentSeed();
            helper.assertTrue(aliceMenu.costs[0] >= 1, "Expected positive level cost for slot 0");
            // With 15 bookshelves slot 0 cost might be > 1, so set levels to exact aliceMenu.costs[0] when testing slot 0,
            // or use exact level requirement if <= 1; to always test insufficient XP after 1st enchant:
            int req0 = aliceMenu.costs[0];
            alice.setExperienceLevels(req0);
            boolean firstOk = aliceMenu.clickMenuButton(alice, 0);
            helper.assertTrue(firstOk, "Alice must succeed enchanting slot 0 at exact required level " + req0);
            helper.assertTrue(alice.experienceLevel == req0 - 1 && bob.experienceLevel == req0 - 1,
                "Bob must receive identical -1 level XP debit (expected " + (req0 - 1) + ", got bob=" + bob.experienceLevel + ")");
            helper.assertTrue(alice.getEnchantmentSeed() == bob.getEnchantmentSeed()
                && alice.getEnchantmentSeed() != seed0
                && bobMenu.getEnchantmentSeed() == alice.getEnchantmentSeed(),
                "Alice, Bob, and Bob's open menu must immediately share the new deterministic seed");

            if (bobMenu.costs[0] > bob.experienceLevel || bobMenu.costs[0] == 0) {
                helper.assertFalse(bobMenu.clickMenuButton(bob, 0),
                    "Bob must not be able to enchant after shared XP dropped below requirement");
            }

            // 2. Deterministic multi-player sequential enchanting with 40 levels
            alice.setExperienceLevels(40);
            aliceMenu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
            aliceMenu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));
            bobMenu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
            bobMenu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 6));

            int seedBefore = alice.getEnchantmentSeed();
            helper.assertTrue(aliceMenu.clickMenuButton(alice, 2), "Alice must enchant pickaxe at slot 2");
            int seedAfterAlice = alice.getEnchantmentSeed();
            helper.assertTrue(bobMenu.getEnchantmentSeed() == seedAfterAlice && bob.experienceLevel == 37,
                "Bob's menu and XP (37) must update immediately after Alice enchants");

            helper.assertTrue(bobMenu.clickMenuButton(bob, 2), "Bob must enchant pickaxe at slot 2 using updated seed & XP");
            int seedAfterBob = bob.getEnchantmentSeed();
            helper.assertTrue(alice.experienceLevel == 34 && bob.experienceLevel == 34,
                "Both players must share 34 levels after two slot-2 enchants (alice=" + alice.experienceLevel + ", bob=" + bob.experienceLevel + ")");
            helper.assertTrue(aliceMenu.getEnchantmentSeed() == seedAfterBob,
                "Alice's menu must immediately update to Bob's post-enchant seed");

            // Determinism check: advancing from seedBefore reproduces exact sequence seedAfterAlice -> seedAfterBob
            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null, "Default hive must exist");
            hive.setEnchantmentSeed(seedBefore);
            helper.assertTrue(hive.advanceEnchantmentSeed() == seedAfterAlice
                && hive.advanceEnchantmentSeed() == seedAfterBob,
                "Enchantment seed progression must be strictly deterministic");

            alice.doCloseContainer();
            bob.doCloseContainer();

            // 3. Death while holding an item in EnchantmentMenu returns it to preserved inventory when creative survivor keeps items
            OIMRConfig.setExcludeCreativeFromDeath(true);
            OIMRConfig.setWipeCreativeInventoryOnDeath(false);
            bob.setGameMode(GameType.CREATIVE);
            alice.openMenu(level.getBlockState(tablePos).getMenuProvider(level, tablePos));
            ((EnchantmentMenu) alice.containerMenu).getSlot(0).set(new ItemStack(Items.NETHERITE_SWORD));
            alice.hurtServer(level, level.damageSources().generic(), 50.0F);
            helper.assertTrue(bob.getInventory().contains(new ItemStack(Items.NETHERITE_SWORD)),
                "Item inside victim Alice's EnchantmentMenu must return to preserved shared inventory on death");

            helper.succeed();
        } finally {
            OIMRConfig.setExcludeCreativeFromDeath(prevExclude);
            OIMRConfig.setWipeCreativeInventoryOnDeath(prevWipe);
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
            Component aliceDeath = HiveMindManager.getLastHiveDeathMessage(alice.getUUID());
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
    public void testPerSlotAndHotbarSync(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevHotbar = OIMRConfig.isSyncHotbarEnabled();
        boolean prevMain = OIMRConfig.isSyncMainHandEnabled();
        boolean prevOff = OIMRConfig.isSyncOffHandEnabled();
        boolean prevHead = OIMRConfig.isSyncHelmetEnabled();
        boolean prevChest = OIMRConfig.isSyncChestplateEnabled();
        boolean prevLegs = OIMRConfig.isSyncLeggingsEnabled();
        boolean prevFeet = OIMRConfig.isSyncBootsEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice25");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob25");
        try {
            // 1. Hotbar OFF, MainHand OFF -> hotbar 0..8 personal, main inv 9..35 shared
            OIMRConfig.setSyncHotbar(false);
            OIMRConfig.setSyncMainHand(false);
            alice.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            bob.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            alice.getInventory().setItem(9, new ItemStack(Items.EMERALD, 5));

            helper.assertTrue(alice.getInventory().getItem(0).is(Items.DIAMOND_SWORD)
                && bob.getInventory().getItem(0).is(Items.IRON_PICKAXE),
                "Hotbar slot 0 must stay personal when syncHotbar and syncMainHand are OFF");
            helper.assertTrue(bob.getInventory().getItem(9).is(Items.EMERALD)
                && bob.getInventory().getItem(9).getCount() == 5,
                "Main inventory slot 9 must stay shared even when hotbar sync is OFF");

            // 2. Hotbar OFF, MainHand ON -> slot 0 shared, slot 1 personal
            OIMRConfig.setSyncMainHand(true);
            alice.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
            alice.getInventory().setItem(1, new ItemStack(Items.TORCH, 10));
            bob.getInventory().setItem(1, new ItemStack(Items.COOKED_BEEF, 3));
            helper.assertTrue(bob.getInventory().getItem(0).is(Items.NETHERITE_SWORD),
                "Hotbar slot 0 must sync when syncMainHand is ON");
            helper.assertTrue(alice.getInventory().getItem(1).is(Items.TORCH)
                && bob.getInventory().getItem(1).is(Items.COOKED_BEEF),
                "Hotbar slot 1 must stay personal when syncHotbar is OFF");

            // 3. Equipment slots toggled individually
            OIMRConfig.setSyncHelmet(false);
            OIMRConfig.setSyncChestplate(true);
            OIMRConfig.setSyncLeggings(false);
            OIMRConfig.setSyncBoots(true);
            OIMRConfig.setSyncOffHand(false);

            alice.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            bob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            alice.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            alice.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
            bob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
            alice.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            alice.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            bob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

            helper.assertTrue(alice.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
                && bob.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "Helmet must be personal when syncHelmet is OFF");
            helper.assertTrue(bob.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE),
                "Chestplate must be shared when syncChestplate is ON");
            helper.assertTrue(alice.getItemBySlot(EquipmentSlot.LEGS).is(Items.GOLDEN_LEGGINGS)
                && bob.getItemBySlot(EquipmentSlot.LEGS).is(Items.CHAINMAIL_LEGGINGS),
                "Leggings must be personal when syncLeggings is OFF");
            helper.assertTrue(bob.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS),
                "Boots must be shared when syncBoots is ON");
            helper.assertTrue(alice.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD)
                && bob.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TOTEM_OF_UNDYING),
                "Offhand must be personal when syncOffHand is OFF");

            helper.succeed();
        } finally {
            OIMRConfig.setSyncHotbar(prevHotbar);
            OIMRConfig.setSyncMainHand(prevMain);
            OIMRConfig.setSyncOffHand(prevOff);
            OIMRConfig.setSyncHelmet(prevHead);
            OIMRConfig.setSyncChestplate(prevChest);
            OIMRConfig.setSyncLeggings(prevLegs);
            OIMRConfig.setSyncBoots(prevFeet);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testXpWeightScalingAndUnchangedSpend(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        float prevXpWeight = OIMRConfig.getXpWeight();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice26");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob26");
        try {
            OIMRConfig.setXpWeight(1.0F);
            alice.setExperienceLevels(0);
            alice.setExperiencePoints(0);

            // 2 players at 1.0x weight: 1 XP twice accumulates 0.5 + 0.5 = 1 XP
            alice.giveExperiencePoints(1);
            helper.assertTrue(alice.totalExperience == 0,
                "First 1-XP orb with 2 players at 1.0x should accumulate 0.5 remainder (got " + alice.totalExperience + ")");
            bob.giveExperiencePoints(1);
            helper.assertTrue(alice.totalExperience == 1 && bob.totalExperience == 1,
                "Second 1-XP orb should grant 1 total XP across party (got " + alice.totalExperience + ")");

            // 2.0x weight with 2 players -> (2.0 * 10) / 2 = 10 XP
            OIMRConfig.setXpWeight(2.0F);
            alice.giveExperiencePoints(10);
            helper.assertTrue(alice.totalExperience == 11 && bob.totalExperience == 11,
                "2.0x XP weight with 2 players must grant 10 XP for +10 gain (got " + alice.totalExperience + ")");

            // Enchantment spending remains unchanged (1x cost) regardless of xpWeight
            OIMRConfig.setXpWeight(0.5F);
            alice.setExperienceLevels(10);
            alice.onEnchantmentPerformed(new ItemStack(Items.DIAMOND_SWORD), 3);
            helper.assertTrue(alice.experienceLevel == 7 && bob.experienceLevel == 7,
                "Enchantment XP cost must remain 1x unaltered (expected 7, got " + alice.experienceLevel + ")");

            helper.succeed();
        } finally {
            OIMRConfig.setXpWeight(prevXpWeight);
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
            helper.assertTrue(bob.getEffect(MobEffects.SPEED).getAmplifier() == 1
                && Math.abs(bob.getEffect(MobEffects.SPEED).getDuration() - 100) <= 2,
                "Bob must receive full Speed II (amp 1) for 100 ticks at 50% affinity (got amp="
                    + bob.getEffect(MobEffects.SPEED).getAmplifier() + ", dur=" + bob.getEffect(MobEffects.SPEED).getDuration() + ")");

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
    public void testCreativeTeammateDoesNotBlockPoisonFireOrSaturation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer creativeFirst = spawnTestPlayerWithUuid(server, level, UUID.fromString("00000000-0000-0000-0000-000000000001"), "HiveCrea29");
        ServerPlayer survivalSecond = spawnTestPlayerWithUuid(server, level, UUID.fromString("00000000-0000-0000-0000-000000000002"), "HiveSurv29");
        try {
            OIMRConfig.setAffinityPercent(100);
            creativeFirst.setGameMode(GameType.CREATIVE);
            survivalSecond.setGameMode(GameType.SURVIVAL);

            // 1. Poison must still tick on Survival teammate even when Creative teammate has lowest UUID
            survivalSecond.setHealth(20.0F);
            survivalSecond.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            for (int i = 0; i < 30; i++) {
                MobEffectInstance cPoison = creativeFirst.getEffect(MobEffects.POISON);
                if (cPoison != null) {
                    cPoison.tickServer(level, creativeFirst, () -> {});
                }
                MobEffectInstance sPoison = survivalSecond.getEffect(MobEffects.POISON);
                if (sPoison != null) {
                    sPoison.tickServer(level, survivalSecond, () -> {});
                }
            }
            helper.assertTrue(survivalSecond.getHealth() < 20.0F,
                "Creative teammate must not block Poison damage on Survival teammate (hp=" + survivalSecond.getHealth() + ")");

            // 2. Fire damage must still tick on Survival teammate even when Creative teammate precedes them
            survivalSecond.removeAllEffects();
            survivalSecond.damageCooldownTime = 0;
            survivalSecond.setHealth(20.0F);
            survivalSecond.igniteForTicks(20);
            HiveMindManager.onEndServerTick(server);
            helper.assertTrue(survivalSecond.getHealth() < 20.0F,
                "Creative teammate must not block fire burn damage on Survival teammate (hp=" + survivalSecond.getHealth() + ")");

            // 3. Saturation effect must tick 1x (not 2x) across Survival + Creative teammates
            survivalSecond.clearFire();
            survivalSecond.getFoodData().setFoodLevel(10);
            survivalSecond.addEffect(new MobEffectInstance(MobEffects.SATURATION, 5, 0));
            MobEffectInstance cSat = creativeFirst.getEffect(MobEffects.SATURATION);
            MobEffectInstance sSat = survivalSecond.getEffect(MobEffects.SATURATION);
            if (cSat != null) {
                cSat.tickServer(level, creativeFirst, () -> {});
            }
            if (sSat != null) {
                sSat.tickServer(level, survivalSecond, () -> {});
            }
            helper.assertTrue(survivalSecond.getFoodData().getFoodLevel() == 11,
                "Saturation must tick exactly 1x per tick across party (expected 11, got " + survivalSecond.getFoodData().getFoodLevel() + ")");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, creativeFirst, survivalSecond);
        }
    }

    @GameTest
    public void testSplashPotionSub100AndInstantHealth1xAndLingeringCloud(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice30");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob30");
        AreaEffectCloud cloud = null;
        try {
            Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));

            // 1. At 50% affinity, splash potion hitting only Alice propagates 50% duration to Bob
            OIMRConfig.setAffinityPercent(50);
            alice.setPos(center);
            bob.setPos(center.x + 25.0, center.y, center.z);

            ItemStack speedSplash = new ItemStack(Items.SPLASH_POTION);
            speedSplash.set(
                DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), List.of(new MobEffectInstance(MobEffects.SPEED, 400, 0)), Optional.empty())
            );
            ThrownSplashPotion splash1 = new ThrownSplashPotion(level, alice, speedSplash);
            splash1.setPos(center.x, center.y + 0.5, center.z);
            splash1.onHitAsPotion(level, speedSplash, new BlockHitResult(new Vec3(center.x, center.y + 0.5, center.z), Direction.UP, BlockPos.containing(center), false));
            splash1.discard();

            MobEffectInstance aliceSpeed = alice.getEffect(MobEffects.SPEED);
            MobEffectInstance bobSpeed = bob.getEffect(MobEffects.SPEED);
            helper.assertTrue(aliceSpeed != null && bobSpeed != null,
                "At 50% affinity Bob must receive scaled Speed from splash hitting Alice");
            helper.assertTrue(Math.abs(bobSpeed.getDuration() - Math.round(aliceSpeed.getDuration() * 0.5F)) <= 2,
                "Bob's splash Speed duration must be 50% of Alice's (alice=" + aliceSpeed.getDuration() + ", bob=" + bobSpeed.getDuration() + ")");

            // 2. At 100% affinity, Instant Health splash hitting BOTH players heals 1x (4 HP), not 2x (8 HP)
            OIMRConfig.setAffinityPercent(100);
            alice.removeAllEffects();
            bob.removeAllEffects();
            alice.setHealth(10.0F);
            bob.setPos(center);
            bob.setHealth(10.0F);

            ItemStack healSplash = new ItemStack(Items.SPLASH_POTION);
            healSplash.set(
                DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), List.of(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0)), Optional.empty())
            );
            ThrownSplashPotion splash2 = new ThrownSplashPotion(level, alice, healSplash);
            splash2.setPos(center.x, center.y + 0.5, center.z);
            splash2.onHitAsPotion(level, healSplash, new BlockHitResult(new Vec3(center.x, center.y + 0.5, center.z), Direction.UP, BlockPos.containing(center), false));
            splash2.discard();

            helper.assertTrue(alice.getHealth() > 10.0F && alice.getHealth() <= 14.01F && Math.abs(alice.getHealth() - bob.getHealth()) < 0.01F,
                "Instant Health splash hitting both players must heal at most 1x (<= 14.0), got alice=" + alice.getHealth() + ", bob=" + bob.getHealth());

            // 3. Lingering AreaEffectCloud Instant Health hitting both players in one pulse heals 1x (not 2x)
            alice.setHealth(10.0F);
            bob.setHealth(10.0F);
            cloud = new AreaEffectCloud(level, center.x, center.y, center.z);
            cloud.setRadius(3.0F);
            cloud.setWaitTime(0);
            cloud.setDuration(100);
            cloud.setPotionContents(new PotionContents(Optional.empty(), Optional.empty(), List.of(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0)), Optional.empty()));
            level.addFreshEntity(cloud);
            // Advance tickCount to multiple of 5 and tick
            for (int i = 0; i < 5; i++) {
                cloud.tick();
            }
            helper.assertTrue(alice.getHealth() > 10.0F && alice.getHealth() <= 14.01F && Math.abs(alice.getHealth() - bob.getHealth()) < 0.01F,
                "AreaEffectCloud Instant Health hitting both players must heal 1x (<= 14.0), got alice=" + alice.getHealth());

            helper.succeed();
        } finally {
            if (cloud != null) {
                cloud.discard();
            }
            OIMRConfig.setAffinityPercent(prevAffinity);
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
    public void testBondSyncPayloadFromConfigAndCodec(GameTestHelper helper) {
        int prevAffinity = OIMRConfig.getAffinityPercent();
        float prevHungerWeight = OIMRConfig.getHungerWeight();
        try {
            OIMRConfig.setAffinityPercent(100);
            OIMRConfig.setHungerWeight(1.0F);
            OIMRBondSyncPayload canonical = OIMRBondSyncPayload.fromConfig();
            helper.assertTrue(
                canonical.hungerPercent() == 100 && canonical.effectsPercent() == 100 && canonical.damagePercent() == 100,
                "Canonical config must produce 100/100/100 bond sync payload"
            );

            OIMRConfig.setAffinityPercent(65);
            OIMRConfig.setHungerWeight(0.5F);
            OIMRBondSyncPayload partial = OIMRBondSyncPayload.fromConfig();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            OIMRBondSyncPayload.STREAM_CODEC.encode(buf, partial);
            OIMRBondSyncPayload decoded = OIMRBondSyncPayload.STREAM_CODEC.decode(buf);
            helper.assertTrue(
                decoded.hungerPercent() == 50 && decoded.effectsPercent() == 65 && decoded.damagePercent() == 65,
                "Decoded bond sync payload must preserve hunger=50, effects=65, damage=65 (got "
                    + decoded.hungerPercent() + "/" + decoded.effectsPercent() + "/" + decoded.damagePercent() + ")"
            );

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setHungerWeight(prevHungerWeight);
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
    public void testFractionalEffectDurationAndXpFlushOnSolo(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        float prevXpWeight = OIMRConfig.getXpWeight();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice34");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob34");
        try {
            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null, "Default hive must exist");

            // 1. Effect duration accumulation at 1% affinity: 40 ticks -> 0.4 ticks per application
            OIMRConfig.setAffinityPercent(1);
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.SPEED),
                "0.4 ticks of Speed must not apply yet");
            helper.assertTrue(Math.abs(hive.getEffectDurationRemainder(bob.getUUID(), MobEffects.SPEED) - 0.4D) < 1.0E-5D,
                "Bob must store 0.4 Speed duration remainder");

            alice.removeEffect(MobEffects.SPEED);
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0)); // 0.8 total
            helper.assertFalse(bob.hasEffect(MobEffects.SPEED),
                "0.8 ticks of Speed must still wait in remainder");

            alice.removeEffect(MobEffects.SPEED);
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0)); // 1.2 total -> applies 1 tick, keeps 0.2
            MobEffectInstance bobSpeed = bob.getEffect(MobEffects.SPEED);
            helper.assertTrue(bobSpeed != null && bobSpeed.getDuration() == 1,
                "1.2 total ticks must apply 1 tick of Speed to Bob (got " + (bobSpeed != null ? bobSpeed.getDuration() : -1) + ")");
            helper.assertTrue(Math.abs(hive.getEffectDurationRemainder(bob.getUUID(), MobEffects.SPEED) - 0.2D) < 1.0E-5D,
                "0.2 ticks remainder must stay stored after applying 1 tick");

            // Two more 40-tick hits: 0.2 + 0.4 + 0.4 = 1.0 -> applies 1 tick, 0.0 remainder
            bob.removeEffect(MobEffects.SPEED);
            alice.removeEffect(MobEffects.SPEED);
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0));
            alice.removeEffect(MobEffects.SPEED);
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0));
            MobEffectInstance bobSpeed2 = bob.getEffect(MobEffects.SPEED);
            helper.assertTrue(bobSpeed2 != null && bobSpeed2.getDuration() == 1
                && hive.getEffectDurationRemainder(bob.getUUID(), MobEffects.SPEED) < 1.0E-5D,
                "0.2 + 0.4 + 0.4 = 1.0 must apply 1 tick and clear remainder");

            // 2. XP remainder accumulated with 2 players (0.5) must still flush when Bob leaves and weight=1.0
            OIMRConfig.setXpWeight(1.0F);
            alice.setExperienceLevels(0);
            alice.setExperiencePoints(0);
            alice.giveExperiencePoints(1); // 0.5 remainder stored
            helper.assertTrue(alice.totalExperience == 0 && Math.abs(hive.getXpRemainder() - 0.5D) < 1.0E-5D,
                "1 XP with 2 players must leave 0.5 remainder");

            server.getPlayerList().remove(bob);
            bob = null;
            alice.giveExperiencePoints(1); // 0.5 + 1.0 = 1.5 -> grants 1 XP, 0.5 remainder
            helper.assertTrue(alice.totalExperience == 1 && Math.abs(hive.getXpRemainder() - 0.5D) < 1.0E-5D,
                "Solo player with 0.5 owed XP + 1 XP must receive 1 XP and keep 0.5 remainder (got xp="
                    + alice.totalExperience + ", rem=" + hive.getXpRemainder() + ")");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setXpWeight(prevXpWeight);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testPeriodicEffectsFullAmplifierDominantSourceAndAffinityReset(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice35");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob35");
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        try {
            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null && zombie != null, "Default hive and zombie must exist");
            level.addFreshEntity(zombie);

            // 1. Full amplifier preserved at low affinity (Speed III for 200 ticks at 1% -> Speed III for 2 ticks)
            OIMRConfig.setAffinityPercent(1);
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 2));
            MobEffectInstance bobSpeed = bob.getEffect(MobEffects.SPEED);
            helper.assertTrue(bobSpeed != null && bobSpeed.getAmplifier() == 2 && bobSpeed.getDuration() == 2,
                "Bob must receive full Speed III (amp 2) for 2 ticks at 1% affinity (got amp="
                    + (bobSpeed != null ? bobSpeed.getAmplifier() : -1) + ", dur=" + (bobSpeed != null ? bobSpeed.getDuration() : -1) + ")");

            // 2. Periodic Poison I accumulates in separate remainder without touching HP, then applies 25 ticks and ticks damage!
            bob.setHealth(20.0F);
            for (int i = 0; i < 12; i++) {
                alice.removeEffect(MobEffects.POISON);
                alice.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0)); // 2 ticks per hit -> 24 ticks total (< 25)
            }
            helper.assertFalse(bob.hasEffect(MobEffects.POISON),
                "24 accumulated ticks of Poison I must wait for 25-tick interval");
            helper.assertTrue(Math.abs(bob.getHealth() - 20.0F) < 0.001F,
                "Bob's real HP must remain untouched while Poison remainder is pending");

            alice.removeEffect(MobEffects.POISON);
            alice.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0)); // 26 ticks -> applies 25 ticks, keeps 1
            MobEffectInstance bobPoison = bob.getEffect(MobEffects.POISON);
            helper.assertTrue(bobPoison != null && bobPoison.getDuration() == 25,
                "Reaching 25 ticks must apply 25-tick Poison I (got " + (bobPoison != null ? bobPoison.getDuration() : -1) + ")");
            bobPoison.tickServer(level, bob, () -> {});
            helper.assertTrue(bob.getHealth() < 20.0F,
                "Applied 25-tick Poison I must immediately tick real damage on Bob (got hp=" + bob.getHealth() + ")");

            // 3. Dominant indirect damage source wins over smaller final hit (0.9 Zombie vs 0.1 Cactus -> Zombie credited!)
            bob.removeAllEffects();
            bob.setHealth(1.0F);
            for (int i = 0; i < 9; i++) {
                alice.setHealth(20.0F);
                alice.damageCooldownTime = 0;
                alice.hurtServer(level, level.damageSources().mobAttack(zombie), 10.0F); // 0.1 * 9 = 0.9 from Zombie
            }
            alice.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            alice.hurtServer(level, level.damageSources().cactus(), 10.0F); // +0.1 from Cactus -> 1.0 kills Bob
            helper.assertTrue(bob.isDeadOrDying(), "Bob must die when accumulated damage reaches 1.0");
            Component deathMsg = HiveMindManager.getLastHiveDeathMessage(bob.getUUID());
            helper.assertTrue(deathMsg != null
                && deathMsg.getContents() instanceof TranslatableContents tc
                && "death.oimr.affinity.player".equals(tc.getKey()),
                "Lethal accumulated damage dominated by Zombie (0.9 vs 0.1 Cactus) must credit Zombie attacker (got " + deathMsg + ")");

            // 4. Invalid >100% affinity in config file resets to 100% on load/startup check
            java.nio.file.Path configPath = OIMRConfig.getConfigPath();
            OIMRConfig.setAffinityPercent(prevAffinity);
            String originalContent = java.nio.file.Files.exists(configPath) ? java.nio.file.Files.readString(configPath) : null;
            try {
                java.nio.file.Files.createDirectories(configPath.getParent());
                java.nio.file.Files.writeString(configPath, "affinity=150\n");
                OIMRConfig.load();
                helper.assertTrue(OIMRConfig.getAffinityPercent() == 100,
                    "Affinity > 100% in config must reset to 100% (got " + OIMRConfig.getAffinityPercent() + ")");
                helper.assertTrue(OIMRConfig.consumeInvalidAffinityResetPending(),
                    "Config load must flag invalid affinity reset so players are notified in chat");
            } finally {
                if (originalContent != null) {
                    java.nio.file.Files.writeString(configPath, originalContent);
                    OIMRConfig.load();
                } else {
                    OIMRConfig.resetToDefaults();
                }
            }

            helper.succeed();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (zombie != null) {
                zombie.discard();
            }
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testSpecialEffectsSub100SaturationOmenHealthBoostAndTriggers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        float prevHungerWeight = OIMRConfig.getHungerWeight();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice36");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob36");
        try {
            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null, "Default hive must exist");

            // 1. Saturation & Hunger at 50% affinity stay 1x on shared FoodData
            OIMRConfig.setAffinityPercent(50);
            OIMRConfig.setHungerWeight(1.0F);
            alice.getFoodData().setFoodLevel(10);
            alice.getFoodData().setSaturation(0.0F);
            alice.addEffect(new MobEffectInstance(MobEffects.SATURATION, 5, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.SATURATION),
                "Saturation must not propagate to teammates below 100% affinity");
            alice.getEffect(MobEffects.SATURATION).tickServer(level, alice, () -> {});
            helper.assertTrue(alice.getFoodData().getFoodLevel() == 11 && bob.getFoodData().getFoodLevel() == 11,
                "Saturation must feed shared FoodData exactly 1x (expected 11, got " + alice.getFoodData().getFoodLevel() + ")");

            // Even if both players independently gain Saturation, only 1 ticks per game tick
            bob.addEffect(new MobEffectInstance(MobEffects.SATURATION, 5, 0));
            alice.getEffect(MobEffects.SATURATION).tickServer(level, alice, () -> {});
            bob.getEffect(MobEffects.SATURATION).tickServer(level, bob, () -> {});
            helper.assertTrue(alice.getFoodData().getFoodLevel() == 12,
                "Duplicate Saturation across teammates must still tick 1x on shared FoodData (expected 12, got "
                    + alice.getFoodData().getFoodLevel() + ")");

            // Hunger effect applies un-diluted 1x exhaustion when not propagated
            alice.removeAllEffects();
            bob.removeAllEffects();
            ((HiveFoodDataFieldsAccess) alice.getFoodData()).oimr$setExhaustionLevel(0.0F);
            alice.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.HUNGER),
                "Hunger effect must not propagate below 100% affinity");
            alice.getEffect(MobEffects.HUNGER).tickServer(level, alice, () -> {});
            float hungerExh = ((HiveFoodDataFieldsAccess) alice.getFoodData()).oimr$getExhaustionLevel();
            helper.assertTrue(Math.abs(hungerExh - 0.005F) < 1.0E-5F,
                "Hunger effect must apply full 1x exhaustion (0.005) on shared FoodData (got " + hungerExh + ")");

            // 2. Bad Omen accumulates to 1.0 with full duration; Raid Omen does not propagate
            alice.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 600, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.RAID_OMEN),
                "Raid Omen must not propagate directly below 100% affinity");

            alice.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 12000, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.BAD_OMEN),
                "First Bad Omen I at 50% must accumulate 0.5 trigger debt");
            alice.removeEffect(MobEffects.BAD_OMEN);
            alice.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 12000, 0));
            MobEffectInstance bobBadOmen = bob.getEffect(MobEffects.BAD_OMEN);
            helper.assertTrue(bobBadOmen != null && bobBadOmen.getDuration() == 12000 && bobBadOmen.getAmplifier() == 0,
                "Second Bad Omen I at 50% must grant Bad Omen I with full 12000-tick duration (got "
                    + (bobBadOmen != null ? bobBadOmen.getDuration() : -1) + ")");

            // 3. Health Boost scales max health (+2.0 at 50%) and accumulates below 1.0 at 1%
            alice.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 200, 0));
            helper.assertTrue(Math.abs(alice.getMaxHealth() - 24.0F) < 0.01F
                && Math.abs(bob.getMaxHealth() - 22.0F) < 0.01F,
                "Health Boost I at 50% must grant +2.0 max HP to Bob (got alice="
                    + alice.getMaxHealth() + ", bob=" + bob.getMaxHealth() + ")");

            // 4. Oozing accumulates 0.5 + 0.5 = 1.0; Infested records scaled chance
            alice.addEffect(new MobEffectInstance(MobEffects.OOZING, 600, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.OOZING),
                "First Oozing at 50% must wait at 0.5 trigger remainder");
            alice.removeEffect(MobEffects.OOZING);
            alice.addEffect(new MobEffectInstance(MobEffects.OOZING, 600, 0));
            helper.assertTrue(bob.hasEffect(MobEffects.OOZING) && bob.getEffect(MobEffects.OOZING).getDuration() == 600,
                "Second Oozing at 50% must grant full Oozing");

            alice.addEffect(new MobEffectInstance(MobEffects.INFESTED, 600, 0));
            helper.assertTrue(bob.hasEffect(MobEffects.INFESTED)
                && Math.abs(HiveMindManager.getInfestedChanceScale(bob) - 0.5D) < 1.0E-5D
                && Math.abs(HiveMindManager.getInfestedChanceScale(alice) - 1.0D) < 1.0E-5D,
                "Infested at 50% must scale Bob's spawn chance to 0.5 while Alice keeps 1.0");

            // 5. At 1% affinity: Absorption (< 1.0) and Health Boost (< 1.0) wait, Darkness waits for 22 ticks
            alice.removeAllEffects();
            bob.removeAllEffects();
            bob.setAbsorptionAmount(0.0F);
            OIMRConfig.setAffinityPercent(1);
            alice.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.ABSORPTION) || bob.getAbsorptionAmount() > 0.0F,
                "1% Absorption (0.04 shield) must accumulate instead of rendering phantom golden heart");
            alice.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 200, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.HEALTH_BOOST) || bob.getMaxHealth() > 20.0F,
                "1% Health Boost (0.04 max HP) must accumulate instead of granting full +4 max HP");

            for (int i = 0; i < 10; i++) {
                alice.removeEffect(MobEffects.DARKNESS);
                alice.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0)); // 2 ticks each -> 20 ticks (< 22)
            }
            helper.assertFalse(bob.hasEffect(MobEffects.DARKNESS),
                "20 accumulated ticks of Darkness must wait for 22-tick blend unit");
            alice.removeEffect(MobEffects.DARKNESS);
            alice.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0)); // 22 ticks -> applies 22 ticks
            helper.assertTrue(bob.hasEffect(MobEffects.DARKNESS) && bob.getEffect(MobEffects.DARKNESS).getDuration() == 22,
                "Reaching 22 ticks must apply 22-tick Darkness");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setHungerWeight(prevHungerWeight);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testAbsorptionGoldenAppleExpiresBelow90Seconds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice37");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob37");
        try {
            // 1. 100% Affinity: Absorption at 1805 ticks must tick past 1800 (1m30s) and eventually expire
            OIMRConfig.setAffinityPercent(100);
            alice.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1805, 0));
            HiveMindManager.onEndServerTick(server);

            for (int i = 0; i < 10; i++) {
                alice.baseTick();
                bob.baseTick();
                HiveMindManager.onEndServerTick(server);
            }
            MobEffectInstance aliceEff = alice.getEffect(MobEffects.ABSORPTION);
            MobEffectInstance bobEff = bob.getEffect(MobEffects.ABSORPTION);
            helper.assertTrue(aliceEff != null && aliceEff.getDuration() == 1795,
                "Alice Absorption must advance past 1800 ticks (expected 1795, got " + (aliceEff != null ? aliceEff.getDuration() : -1) + ")");
            helper.assertTrue(bobEff != null && bobEff.getDuration() == 1795,
                "Bob Absorption must advance past 1800 ticks (expected 1795, got " + (bobEff != null ? bobEff.getDuration() : -1) + ")");

            // Verify full expiration clears absorption shield on both players
            alice.removeEffect(MobEffects.ABSORPTION);
            alice.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2, 0));
            HiveMindManager.onEndServerTick(server);
            alice.baseTick();
            bob.baseTick();
            HiveMindManager.onEndServerTick(server);
            alice.baseTick();
            bob.baseTick();
            HiveMindManager.onEndServerTick(server);
            helper.assertFalse(alice.hasEffect(MobEffects.ABSORPTION) || bob.hasEffect(MobEffects.ABSORPTION),
                "Absorption must cleanly expire from both players at 0 ticks");
            helper.assertTrue(alice.getAbsorptionAmount() == 0.0F && bob.getAbsorptionAmount() == 0.0F,
                "Absorption shield must return to 0.0F after expiration");

            // 2. 50% Affinity: periodic 600-tick client resync must NOT re-grant absorption to teammates
            OIMRConfig.setAffinityPercent(50);
            alice.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1802, 0));
            bob.removeEffect(MobEffects.ABSORPTION);
            bob.setAbsorptionAmount(0.0F);
            for (int i = 0; i < 4; i++) {
                alice.baseTick();
            }
            helper.assertFalse(bob.hasEffect(MobEffects.ABSORPTION) || bob.getAbsorptionAmount() > 0.0F,
                "Periodic 600-tick effect update on Alice must not re-grant Absorption to Bob at 50% affinity");

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
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice38");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob38");
        ServerPlayer charlie = spawnTestPlayer(server, level, "HiveCharlie38");
        try {
            OIMRConfig.setAffinityPercent(100);
            alice.setHealth(20.0F);
            HiveMindManager.onEndServerTick(server);

            // Every exposed player contributes lava damage: 3 * 4.0F = 12.0F shared damage.
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
            cleanupPlayers(server, alice, bob, charlie);
        }
    }

    @GameTest
    public void testPeacefulRegenDoesNotScaleWithPlayerCount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        Difficulty prevDifficulty = server.getWorldData().getDifficulty();
        boolean prevNaturalRegen = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice39");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob39");
        ServerPlayer charlie = spawnTestPlayer(server, level, "HiveCharlie39");
        try {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            level.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, true, server);

            // 1. 100% Affinity: 3 hurt players at tickCount=20 -> only +1.0 HP, +1 food, +1.0 saturation
            OIMRConfig.setAffinityPercent(100);
            alice.setHealth(10.0F);
            alice.getFoodData().setFoodLevel(10);
            alice.getFoodData().setSaturation(0.0F);
            HiveMindManager.onEndServerTick(server);

            alice.tickCount = 20;
            bob.tickCount = 20;
            charlie.tickCount = 20;
            alice.aiStep();
            bob.aiStep();
            charlie.aiStep();

            helper.assertTrue(Math.abs(alice.getHealth() - 11.0F) < 0.01F,
                "Peaceful regen at 100% affinity with 3 players must heal +1.0 HP (11.0), got " + alice.getHealth());
            helper.assertTrue(alice.getFoodData().getFoodLevel() == 11,
                "Peaceful regen with 3 players must restore +1 food (11), got " + alice.getFoodData().getFoodLevel());
            helper.assertTrue(Math.abs(alice.getFoodData().getSaturationLevel() - 1.0F) < 0.01F,
                "Peaceful regen with 3 players must restore +1.0 saturation (1.0), got " + alice.getFoodData().getSaturationLevel());

            // 2. 50% Affinity: Alice and Bob each heal +1.0 HP (NOT +1.5 HP!), food +1
            OIMRConfig.setAffinityPercent(50);
            alice.setHealth(10.0F);
            bob.setHealth(10.0F);
            alice.getFoodData().setFoodLevel(10);
            alice.tickCount = 40;
            bob.tickCount = 40;
            charlie.tickCount = 40;
            alice.aiStep();
            bob.aiStep();
            charlie.aiStep();

            helper.assertTrue(Math.abs(alice.getHealth() - 11.0F) < 0.01F
                    && Math.abs(bob.getHealth() - 11.0F) < 0.01F,
                "Peaceful regen at 50% affinity must heal each player +1.0 HP without cascading (got alice="
                    + alice.getHealth() + ", bob=" + bob.getHealth() + ")");
            helper.assertTrue(alice.getFoodData().getFoodLevel() == 11,
                "Peaceful food regen at 50% affinity must still stay 1x (11), got " + alice.getFoodData().getFoodLevel());

            helper.succeed();
        } finally {
            server.setDifficulty(prevDifficulty, true);
            level.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, prevNaturalRegen, server);
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob, charlie);
        }
    }

    @GameTest
    public void testSharedEnderPearlTeleportAndToggle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevPearl = OIMRConfig.isSharedEnderPearlEnabled();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice40");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob40");
        ServerPlayer spectator = spawnTestPlayer(server, level, "HiveSpec40");
        BlockPos padCenter = helper.absolutePos(new BlockPos(2, 2, 2));
        try {
            OIMRConfig.setAffinityPercent(100);
            OIMRConfig.setSharedEnderPearl(true);
            spectator.setGameMode(GameType.SPECTATOR);

            level.setBlockAndUpdate(padCenter.below(), Blocks.STONE.defaultBlockState());
            alice.teleportTo(padCenter.getX() + 0.5, padCenter.getY() + 10.0, padCenter.getZ() + 0.5);
            bob.teleportTo(padCenter.getX() + 40.5, padCenter.getY() + 2.0, padCenter.getZ() + 40.5);
            Vec3 specPos = new Vec3(padCenter.getX() + 80.5, padCenter.getY() + 5.0, padCenter.getZ() + 80.5);
            spectator.teleportTo(specPos.x, specPos.y, specPos.z);

            ThrownEnderpearl pearl = new ThrownEnderpearl(level, alice, new ItemStack(Items.ENDER_PEARL));
            pearl.setPos(padCenter.getX() + 0.5, padCenter.getY() + 0.5, padCenter.getZ() + 0.5);
            pearl.setDeltaMovement(0.0, -1.0, 0.0);
            level.addFreshEntity(pearl);
            for (int i = 0; i < 5 && !pearl.isRemoved(); i++) {
                pearl.tick();
            }

            helper.assertTrue(pearl.isRemoved(), "Ender pearl must have impacted and been discarded");
            helper.assertTrue(alice.position().distanceTo(bob.position()) < 1.0D,
                "Bob must teleport to the same landing position as Alice (dist=" + alice.position().distanceTo(bob.position()) + ")");
            helper.assertTrue(spectator.position().distanceTo(specPos) < 0.5D,
                "Spectator teammate must NOT be teleported by shared Ender Pearl");
            helper.assertTrue(Math.abs(alice.getHealth() - 15.0F) < 0.01F && Math.abs(bob.getHealth() - 15.0F) < 0.01F,
                "Ender pearl must deal 1x 5.0 damage shared across hive (15.0 HP), got alice="
                    + alice.getHealth() + ", bob=" + bob.getHealth());

            // Toggle OFF -> Bob stays put
            OIMRConfig.setSharedEnderPearl(false);
            Vec3 bobStayPos = new Vec3(padCenter.getX() + 40.5, padCenter.getY() + 2.0, padCenter.getZ() + 40.5);
            bob.teleportTo(bobStayPos.x, bobStayPos.y, bobStayPos.z);

            ThrownEnderpearl pearl2 = new ThrownEnderpearl(level, alice, new ItemStack(Items.ENDER_PEARL));
            pearl2.setPos(padCenter.getX() + 0.5, padCenter.getY() + 0.5, padCenter.getZ() + 0.5);
            pearl2.setDeltaMovement(0.0, -1.0, 0.0);
            level.addFreshEntity(pearl2);
            for (int i = 0; i < 5 && !pearl2.isRemoved(); i++) {
                pearl2.tick();
            }
            helper.assertTrue(bob.position().distanceTo(bobStayPos) < 0.5D,
                "When sharedEnderPearl is OFF, Bob must not teleport with Alice");

            helper.succeed();
        } finally {
            level.setBlockAndUpdate(padCenter.below(), Blocks.AIR.defaultBlockState());
            OIMRConfig.setSharedEnderPearl(prevPearl);
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob, spectator);
        }
    }

    @GameTest
    public void testSharedPortalTeleportAndToggle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        ServerLevel endLevel = server.getLevel(Level.END);
        boolean prevPortals = OIMRConfig.isSharedPortalsEnabled();
        ServerPlayer alice = spawnTestPlayer(server, overworld, "HiveAlice41");
        ServerPlayer bob = spawnTestPlayer(server, overworld, "HiveBob41");
        try {
            helper.assertTrue(endLevel != null, "End dimension must exist");
            OIMRConfig.setSharedPortals(true);

            alice.setPos(10.0, 70.0, 10.0);
            bob.setPos(200.0, 70.0, 200.0);
            alice.setPortalCooldown(0);
            bob.setPortalCooldown(0);
            alice.setAsInsidePortal((Portal) Blocks.END_PORTAL, alice.blockPosition());
            alice.baseTick();

            helper.assertTrue(alice.level().dimension() == Level.END,
                "Alice must enter The End via End Portal");
            helper.assertTrue(bob.level().dimension() == Level.END,
                "Bob must teleport into The End together with Alice");
            helper.assertTrue(alice.position().distanceTo(bob.position()) < 1.0D,
                "Alice and Bob must land at the same End portal destination");
            helper.assertTrue(bob.isOnPortalCooldown(),
                "Bob must receive portal cooldown to prevent portal bounce loops");

            // Toggle OFF -> only Alice teleports
            OIMRConfig.setSharedPortals(false);
            alice.teleportTo(overworld, 10.0, 70.0, 10.0, java.util.Set.of(), 0.0F, 0.0F, false);
            bob.teleportTo(overworld, 200.0, 70.0, 200.0, java.util.Set.of(), 0.0F, 0.0F, false);
            alice.setPortalCooldown(0);
            bob.setPortalCooldown(0);
            alice.setAsInsidePortal((Portal) Blocks.END_PORTAL, alice.blockPosition());
            alice.baseTick();

            helper.assertTrue(alice.level().dimension() == Level.END, "Alice must enter The End");
            helper.assertTrue(bob.level().dimension() == Level.OVERWORLD,
                "When sharedPortals is OFF, Bob must stay in Overworld");

            helper.succeed();
        } finally {
            OIMRConfig.setSharedPortals(prevPortals);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testUnderwaterSyncPayloadAndNewConfigDefaults(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevPearl = OIMRConfig.isSharedEnderPearlEnabled();
        boolean prevPortals = OIMRConfig.isSharedPortalsEnabled();
        boolean prevUnderwater = OIMRConfig.isUnderwaterMuffleEnabled();
        boolean prevMobSounds = OIMRConfig.isSharedMobSoundsEnabled();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "HiveAlice41");
        ServerPlayer bob = spawnTestPlayer(server, level, "HiveBob41");
        BlockPos waterPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos eyeWaterPos = waterPos.above();
        try {
            OIMRConfig.setAffinityPercent(100);
            OIMRConfig.setUnderwaterMuffle(true);
            OIMRUnderwaterSyncPayload payload = OIMRUnderwaterSyncPayload.fromState(true);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            OIMRUnderwaterSyncPayload.STREAM_CODEC.encode(buf, payload);
            OIMRUnderwaterSyncPayload decoded = OIMRUnderwaterSyncPayload.STREAM_CODEC.decode(buf);
            helper.assertTrue(decoded.enabled() && decoded.hiveUnderwater(),
                "Underwater sync payload must round-trip enabled=true, hiveUnderwater=true");

            // Submerge Alice (both feet & eye blocks) and keep Bob on land
            level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(eyeWaterPos, Blocks.WATER.defaultBlockState(), 3);
            alice.teleportTo(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5);
            bob.teleportTo(waterPos.getX() + 6.5, waterPos.getY(), waterPos.getZ() + 0.5);
            alice.baseTick();
            bob.baseTick();
            helper.assertTrue(alice.isEyeInFluid(FluidTags.WATER),
                "Alice must detect eye-in-water after baseTick in water column");
            HiveMindManager.onEndServerTick(server);
            HiveMind hive = HiveMindManager.getHiveForPlayer(alice);
            helper.assertTrue(hive != null && hive.isAnyMemberUnderwater(),
                "Hive underwater state must activate when an alive non-spectator member's eye is in water");

            // Spectator underwater must not trigger hive underwater state
            alice.setGameMode(GameType.SPECTATOR);
            HiveMindManager.onEndServerTick(server);
            helper.assertFalse(hive.isAnyMemberUnderwater(),
                "Spectator member in water must not activate hive underwater state");
            alice.setGameMode(GameType.SURVIVAL);

            // Dead teammate on join gets updated state without throwing or clearing alive underwater state
            bob.setHealth(0.0F);
            HiveMindManager.syncUnderwaterOnJoin(bob);
            helper.assertTrue(hive.isAnyMemberUnderwater(),
                "syncUnderwaterOnJoin for a dead teammate must keep active hive underwater state");
            bob.setHealth(20.0F);

            // Removing water resets hive underwater state cleanly
            level.setBlock(waterPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(eyeWaterPos, Blocks.AIR.defaultBlockState(), 3);
            alice.baseTick();
            HiveMindManager.onEndServerTick(server);
            helper.assertFalse(hive.isAnyMemberUnderwater(),
                "Hive underwater state must clear once no alive member has eyes in water");

            OIMRConfig.setSharedEnderPearl(false);
            OIMRConfig.setSharedPortals(false);
            OIMRConfig.setUnderwaterMuffle(false);
            OIMRConfig.setSharedMobSounds(false);
            OIMRConfig.load();
            helper.assertFalse(OIMRConfig.isSharedEnderPearlEnabled()
                    || OIMRConfig.isSharedPortalsEnabled()
                    || OIMRConfig.isUnderwaterMuffleEnabled()
                    || OIMRConfig.isSharedMobSoundsEnabled(),
                "New config toggles must persist false across OIMRConfig.load()");

            helper.succeed();
        } finally {
            level.setBlock(waterPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(eyeWaterPos, Blocks.AIR.defaultBlockState(), 3);
            OIMRConfig.setSharedEnderPearl(prevPearl);
            OIMRConfig.setSharedPortals(prevPortals);
            OIMRConfig.setUnderwaterMuffle(prevUnderwater);
            OIMRConfig.setSharedMobSounds(prevMobSounds);
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testReplicatedMobSoundsOrientationDistanceProbabilityAndAffinity(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevMobSounds = OIMRConfig.isSharedMobSoundsEnabled();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        TestPlayerHandle aliceHandle = spawnTestPlayerWithChannel(server, level, "HiveAlice42");
        TestPlayerHandle bobHandle = spawnTestPlayerWithChannel(server, level, "HiveBob42");
        TestPlayerHandle carolHandle = spawnTestPlayerWithChannel(server, level, "HiveCarol42");
        ServerPlayer alice = aliceHandle.player();
        ServerPlayer bob = bobHandle.player();
        ServerPlayer carol = carolHandle.player();
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        try {
            helper.assertTrue(zombie != null, "Zombie must spawn");
            OIMRConfig.setSharedMobSounds(true);
            OIMRConfig.setAffinityPercent(50);

            // Alice and Carol are both near the Zombie; Bob is 200 blocks away.
            // A single sound must NEVER be duplicated to Bob across the two nearby listeners.
            alice.teleportTo(0.0, 64.0, 0.0);
            carol.teleportTo(2.0, 64.0, 0.0);
            bob.teleportTo(200.0, 64.0, 0.0);
            zombie.setPos(4.0, 64.0, 0.0);
            level.addFreshEntity(zombie);

            aliceHandle.channel().outboundMessages().clear();
            carolHandle.channel().outboundMessages().clear();
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 40; i++) {
                zombie.playAmbientSound();
            }

            List<ClientboundSoundPacket> replicated = new java.util.ArrayList<>();
            Object msg;
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp && sp.getSource() == SoundSource.HOSTILE) {
                    replicated.add(sp);
                }
            }
            long carolTotal = 0;
            long carolReplicated = 0;
            while ((msg = carolHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp && sp.getSource() == SoundSource.HOSTILE) {
                    carolTotal++;
                    if (Math.abs(sp.getVolume() - 0.5F) < 0.01F) {
                        carolReplicated++;
                    }
                }
            }

            helper.assertTrue(!replicated.isEmpty() && replicated.size() <= 35,
                "With 1/3 chance over 40 zombie sounds and 2 nearby listeners, Bob must receive replicated sounds without per-listener duplication (got "
                    + replicated.size() + ")");
            helper.assertTrue(carolTotal == 40 && carolReplicated == 0,
                "Nearby teammate Carol inside native range must receive only the 40 native packets and 0 replicated packets (total="
                    + carolTotal + ", replicated=" + carolReplicated + ")");

            ClientboundSoundPacket sample = replicated.getFirst();
            Vec3 soundPos = new Vec3(sample.getX(), sample.getY(), sample.getZ());
            double distFromBob = soundPos.distanceTo(bob.position());
            helper.assertTrue(Math.abs(distFromBob - 2.0D) < 0.05D,
                "Replicated sound distance from Bob must equal closest listener (Carol) distance to mob (2.0), got " + distFromBob);
            helper.assertTrue(sample.getX() < bob.getX() && Math.abs(sample.getX() - 198.0D) < 0.05D,
                "Replicated sound orientation must point toward the mob (West of Bob at x=198), got x=" + sample.getX());
            helper.assertTrue(Math.abs(sample.getVolume() - 0.5F) < 0.05F,
                "Replicated sound volume must be scaled by 50% affinity (0.5), got " + sample.getVolume());

            // Dead nearby player must NOT originate replicated sounds
            carol.teleportTo(500.0, 64.0, 0.0);
            alice.setHealth(0.0F);
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 20; i++) {
                zombie.playAmbientSound();
            }
            int deadSourceCount = 0;
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp && sp.getSource() == SoundSource.HOSTILE) {
                    deadSourceCount++;
                }
            }
            helper.assertTrue(deadSourceCount == 0,
                "Dead teammate near mob must not originate replicated mob sounds (got " + deadSourceCount + ")");
            alice.setHealth(20.0F);

            // Toggle OFF -> 0 replicated sounds
            OIMRConfig.setSharedMobSounds(false);
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 20; i++) {
                zombie.playAmbientSound();
            }
            int offCount = 0;
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp && sp.getSource() == SoundSource.HOSTILE) {
                    offCount++;
                }
            }
            helper.assertTrue(offCount == 0,
                "When sharedMobSounds is OFF, Bob must receive 0 replicated mob sounds (got " + offCount + ")");

            helper.succeed();
        } finally {
            if (zombie != null) {
                zombie.discard();
            }
            OIMRConfig.setSharedMobSounds(prevMobSounds);
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob, carol);
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
    public void testDamageAndFoodKillfeedPayloadsAndOriginTracking(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        ServerPlayer alice = spawnTestPlayer(server, level, "FeedAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "FeedBob");
        record ObservedFeed(UUID recipientId, Object payload) {}
        java.util.List<ObservedFeed> observed = new java.util.ArrayList<>();
        BlockPos cakePos = new BlockPos(0, 65, 0);
        try {
            OIMRNetworking.setTestFeedObserver((recipient, payload) -> observed.add(new ObservedFeed(recipient.getUUID(), payload)));

            // 1. Payload codec round-trips
            OIMRDamageFeedPayload dmgSample = new OIMRDamageFeedPayload("FeedAlice", DamageIconType.EXPLOSION, 7.5F);
            FriendlyByteBuf dmgBuf = new FriendlyByteBuf(Unpooled.buffer());
            OIMRDamageFeedPayload.STREAM_CODEC.encode(dmgBuf, dmgSample);
            OIMRDamageFeedPayload dmgDecoded = OIMRDamageFeedPayload.STREAM_CODEC.decode(dmgBuf);
            helper.assertTrue("FeedAlice".equals(dmgDecoded.playerName())
                    && dmgDecoded.iconType() == DamageIconType.EXPLOSION
                    && Math.abs(dmgDecoded.amount() - 7.5F) < 0.001F,
                "Damage feed payload must round-trip cleanly");

            OIMRFoodFeedPayload foodSample = new OIMRFoodFeedPayload("FeedBob", new ItemStack(Items.COOKED_BEEF), 6);
            RegistryFriendlyByteBuf foodBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            OIMRFoodFeedPayload.STREAM_CODEC.encode(foodBuf, foodSample);
            OIMRFoodFeedPayload foodDecoded = OIMRFoodFeedPayload.STREAM_CODEC.decode(foodBuf);
            helper.assertTrue("FeedBob".equals(foodDecoded.playerName())
                    && foodDecoded.foodItem().is(Items.COOKED_BEEF)
                    && foodDecoded.recoveredFoodPoints() == 6,
                "Food feed payload must round-trip cleanly");

            // 2. Full affinity (100%): original fall damage on Alice notifies both Alice & Bob once with FALL + 4.0F
            OIMRConfig.setAffinityPercent(100);
            HiveMindManager.onConfigChanged(server);
            alice.setHealth(20.0F);
            observed.clear();
            alice.hurtServer(level, level.damageSources().fall(), 4.0F);

            long aliceDmgCount = observed.stream()
                .filter(e -> e.payload() instanceof OIMRDamageFeedPayload p
                    && e.recipientId().equals(alice.getUUID())
                    && "FeedAlice".equals(p.playerName())
                    && p.iconType() == DamageIconType.FALL
                    && Math.abs(p.amount() - 4.0F) < 0.01F)
                .count();
            long bobDmgCount = observed.stream()
                .filter(e -> e.payload() instanceof OIMRDamageFeedPayload p
                    && e.recipientId().equals(bob.getUUID())
                    && "FeedAlice".equals(p.playerName())
                    && p.iconType() == DamageIconType.FALL
                    && Math.abs(p.amount() - 4.0F) < 0.01F)
                .count();
            helper.assertTrue(aliceDmgCount == 1 && bobDmgCount == 1 && observed.size() == 2,
                "100% affinity original hit must emit exactly 1 damage feed event per online group member (size=" + observed.size() + ")");

            // 3. Partial affinity (50%): arrow hit on Alice emits ONE original PROJECTILE feed event, not Bob's shared damage
            OIMRConfig.setAffinityPercent(50);
            HiveMindManager.onConfigChanged(server);
            alice.setHealth(20.0F);
            bob.setHealth(20.0F);
            alice.damageCooldownTime = 0;
            bob.damageCooldownTime = 0;
            observed.clear();
            Arrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
            helper.assertTrue(arrow != null, "Arrow must spawn");
            alice.hurtServer(level, level.damageSources().arrow(arrow, null), 6.0F);
            helper.assertTrue(bob.getHealth() == 17.0F, "Bob must take 3.0 shared damage at 50% affinity");
            long projectileEvents = observed.stream()
                .filter(e -> e.payload() instanceof OIMRDamageFeedPayload p
                    && "FeedAlice".equals(p.playerName())
                    && p.iconType() == DamageIconType.PROJECTILE
                    && Math.abs(p.amount() - 6.0F) < 0.01F)
                .count();
            helper.assertTrue(projectileEvents == 2 && observed.size() == 2,
                "Partial affinity must only emit feed for original hit on Alice (observed=" + observed.size() + ")");

            // 4. Food consumption via Consumable (partial hunger recovery + full hunger 0 recovery)
            OIMRConfig.setAffinityPercent(100);
            HiveMindManager.onConfigChanged(server);
            alice.getFoodData().setFoodLevel(14);
            alice.getFoodData().setSaturation(0.0F);
            observed.clear();

            ItemStack beef = new ItemStack(Items.COOKED_BEEF, 2);
            var consumable = beef.get(DataComponents.CONSUMABLE);
            helper.assertTrue(consumable != null, "Cooked beef must have Consumable component");
            consumable.onConsume(level, alice, beef);

            long foodEvents = observed.stream()
                .filter(e -> e.payload() instanceof OIMRFoodFeedPayload p
                    && "FeedAlice".equals(p.playerName())
                    && p.foodItem().is(Items.COOKED_BEEF)
                    && p.recoveredFoodPoints() == 6)
                .count();
            helper.assertTrue(foodEvents == 2 && observed.size() == 2,
                "Eating cooked beef from 14->20 must emit recoveredFoodPoints=6 (3 carninhas) for both members");

            // Eating golden apple at full hunger (20/20) emits 0 recovered food points with golden apple icon
            observed.clear();
            ItemStack gapple = new ItemStack(Items.GOLDEN_APPLE, 1);
            var gappleConsumable = gapple.get(DataComponents.CONSUMABLE);
            helper.assertTrue(gappleConsumable != null, "Golden apple must have Consumable component");
            gappleConsumable.onConsume(level, alice, gapple);
            long gappleEvents = observed.stream()
                .filter(e -> e.payload() instanceof OIMRFoodFeedPayload p
                    && "FeedAlice".equals(p.playerName())
                    && p.foodItem().is(Items.GOLDEN_APPLE)
                    && p.recoveredFoodPoints() == 0)
                .count();
            helper.assertTrue(gappleEvents == 2 && observed.size() == 2,
                "Eating golden apple at full hunger must emit recoveredFoodPoints=0 with GOLDEN_APPLE item");

            // 5. Cake slice consumption emits Items.CAKE with recoveredFoodPoints=2 (1 carninha)
            alice.getFoodData().setFoodLevel(16);
            level.setBlock(cakePos, Blocks.CAKE.defaultBlockState(), 3);
            observed.clear();
            var hit = new BlockHitResult(Vec3.atCenterOf(cakePos), Direction.UP, cakePos, false);
            level.getBlockState(cakePos).useWithoutItem(level, alice, hit);
            long cakeEvents = observed.stream()
                .filter(e -> e.payload() instanceof OIMRFoodFeedPayload p
                    && "FeedAlice".equals(p.playerName())
                    && p.foodItem().is(Items.CAKE)
                    && p.recoveredFoodPoints() == 2)
                .count();
            helper.assertTrue(cakeEvents == 2 && observed.size() == 2,
                "Eating a cake slice must emit Items.CAKE and recoveredFoodPoints=2 (1 carninha)");

            // 6. Air bubble drain feed: codec round-trip + submerged Alice emits -1 bubble for both members when 1st bubble pops
            OIMRAirFeedPayload airSample = new OIMRAirFeedPayload("FeedAlice", 1);
            FriendlyByteBuf airBuf = new FriendlyByteBuf(Unpooled.buffer());
            OIMRAirFeedPayload.STREAM_CODEC.encode(airBuf, airSample);
            OIMRAirFeedPayload airDecoded = OIMRAirFeedPayload.STREAM_CODEC.decode(airBuf);
            helper.assertTrue("FeedAlice".equals(airDecoded.playerName()) && airDecoded.drainedBubbles() == 1,
                "Air feed payload must round-trip cleanly");

            BlockPos airFeet = new BlockPos(3, 65, 3);
            BlockPos airEye = airFeet.above();
            level.setBlock(airFeet, Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(airEye, Blocks.WATER.defaultBlockState(), 3);
            try {
                alice.teleportTo(airFeet.getX() + 0.5, airFeet.getY(), airFeet.getZ() + 0.5);
                bob.teleportTo(10.5, 65.0, 10.5);
                alice.baseTick();
                HiveMind hive = HiveMindManager.getHiveForPlayer(alice);
                helper.assertTrue(hive != null, "Alice must belong to a hive");
                hive.setAirSupply(300);
                observed.clear();

                // 27 ticks (300 -> 273) do not pop a bubble yet
                for (int t = 0; t < 27; t++) {
                    alice.baseTick();
                    bob.baseTick();
                    HiveMindManager.onEndServerTick(server);
                    server.getPlayerList().getPlayers().forEach(p -> p.tickCount++);
                    // Advance overworld game time via tick is not needed if we reset tick claim or step world;
                    // use tryClaimEnvironmentTick indirectly by resetting tick claims each simulated tick:
                    hive.resetTickClaims();
                }
                helper.assertTrue(hive.getAirSupply() == 273 && observed.isEmpty(),
                    "Before reaching 272 air, no bubble should have popped yet (air=" + hive.getAirSupply() + ", observed=" + observed.size() + ")");

                // 28th tick (273 -> 272) pops the first bubble!
                HiveMindManager.onEndServerTick(server);
                long airEvents = observed.stream()
                    .filter(e -> e.payload() instanceof OIMRAirFeedPayload p
                        && "FeedAlice".equals(p.playerName())
                        && p.drainedBubbles() == 1)
                    .count();
                helper.assertTrue(hive.getAirSupply() == 272 && airEvents == 2 && observed.size() == 2,
                    "Popping the first shared air bubble (273->272) must emit OIMRAirFeedPayload(-1, FeedAlice) to both members (air="
                        + hive.getAirSupply() + ", airEvents=" + airEvents + ")");
            } finally {
                level.setBlock(airFeet, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(airEye, Blocks.AIR.defaultBlockState(), 3);
            }

            helper.succeed();
        } finally {
            OIMRNetworking.setTestFeedObserver(null);
            level.setBlock(cakePos, Blocks.AIR.defaultBlockState(), 3);
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testSyncEffectsToggleAndImmediateLiveConfigUpdates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        boolean prevSyncEffects = OIMRConfig.isSyncEffectsEnabled();
        boolean prevSyncHotbar = OIMRConfig.isSyncHotbarEnabled();
        boolean prevSyncHelmet = OIMRConfig.isSyncHelmetEnabled();
        float prevHungerWeight = OIMRConfig.getHungerWeight();
        float prevXpWeight = OIMRConfig.getXpWeight();

        TestPlayerHandle aliceHandle = spawnTestPlayerWithChannel(server, level, "LiveAlice");
        TestPlayerHandle bobHandle = spawnTestPlayerWithChannel(server, level, "LiveBob");
        ServerPlayer alice = aliceHandle.player();
        ServerPlayer bob = bobHandle.player();
        try {
            OIMRConfig.setAffinityPercent(100);
            OIMRConfig.setSyncEffects(false);
            HiveMindManager.onConfigChanged(server);

            // 1. Bond sync payload reflects effectsPercent = 0 immediately when syncEffects is disabled
            OIMRBondSyncPayload bondDisabled = OIMRBondSyncPayload.fromConfig();
            helper.assertTrue(bondDisabled.hungerPercent() == 100
                    && bondDisabled.effectsPercent() == 0
                    && bondDisabled.damagePercent() == 100,
                "Disabling syncEffects must immediately report effectsPercent=0 in OIMRBondSyncPayload");

            // 2. Duration-based effects are NOT synced between Alice and Bob while syncEffects=false
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
            helper.assertTrue(alice.hasEffect(MobEffects.SPEED), "Alice must receive her own Speed effect");
            helper.assertFalse(bob.hasEffect(MobEffects.SPEED), "Bob must NOT receive Speed when syncEffects=false");

            // Splash potion with duration effect must also stay per-player when syncEffects=false
            ItemStack splashRegen = PotionContents.createItemStack(Items.SPLASH_POTION, net.minecraft.world.item.alchemy.Potions.REGENERATION);
            ThrownSplashPotion potion = new ThrownSplashPotion(level, alice, splashRegen);
            potion.setPos(alice.getX(), alice.getY() + 0.5, alice.getZ());
            bob.teleportTo(alice.getX() + 10.0, alice.getY(), alice.getZ() + 10.0);
            HiveMindManager.beginSplashPotionImpact();
            boolean captured = HiveMindManager.captureSplashPotionEffect(alice, new MobEffectInstance(MobEffects.REGENERATION, 160, 0));
            HiveMindManager.endSplashPotionImpact(potion, alice);
            potion.discard();
            helper.assertFalse(captured, "captureSplashPotionEffect must return false when syncEffects=false");

            // Removal on Alice does not clear Bob's personal effect
            bob.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0));
            alice.removeEffect(MobEffects.NIGHT_VISION);
            helper.assertTrue(bob.hasEffect(MobEffects.NIGHT_VISION),
                "Removing Night Vision from Alice must not clear Bob's Night Vision when syncEffects=false");
            bob.removeEffect(MobEffects.NIGHT_VISION);

            // Periodic Poison ticks independently for Alice and Bob when syncEffects=false
            MobEffectInstance alicePoison = new MobEffectInstance(MobEffects.POISON, 100, 0);
            MobEffectInstance bobPoison = new MobEffectInstance(MobEffects.POISON, 100, 0);
            alice.addEffect(alicePoison);
            bob.addEffect(bobPoison);
            helper.assertTrue(HiveMindManager.shouldApplyPeriodicEffectTick(alice, MobEffects.POISON)
                    && HiveMindManager.shouldApplyPeriodicEffectTick(bob, MobEffects.POISON),
                "Both players must tick personal Poison independently when syncEffects=false");
            alice.removeEffect(MobEffects.POISON);
            bob.removeEffect(MobEffects.POISON);

            // 3. Re-enabling syncEffects=true + onConfigChanged immediately syncs alive.getFirst()'s effects
            //    without getting overwritten by Bob's empty effect list!
            OIMRConfig.setSyncEffects(true);
            HiveMindManager.onConfigChanged(server);
            helper.assertTrue(alice.hasEffect(MobEffects.SPEED) && bob.hasEffect(MobEffects.SPEED),
                "Re-enabling syncEffects must immediately propagate Alice's active Speed to Bob without wiping Alice");

            // 4. Raising affinity 50 -> 100 via onConfigChanged preserves rep health & resyncs teammates immediately
            OIMRConfig.setAffinityPercent(50);
            HiveMindManager.onConfigChanged(server);
            alice.setHealth(12.0F);
            bob.setHealth(20.0F);
            OIMRConfig.setAffinityPercent(100);
            HiveMindManager.onConfigChanged(server);
            helper.assertTrue(Math.abs(alice.getHealth() - 12.0F) < 0.01F && Math.abs(bob.getHealth() - 12.0F) < 0.01F,
                "Raising affinity to 100 via onConfigChanged must immediately sync all members to rep health (alice="
                    + alice.getHealth() + ", bob=" + bob.getHealth() + ")");

            // 5. Live slot/weight/config toggles immediately drop shared items, re-route, and avoid duplication
            BlockPos dropCheckPos = helper.absolutePos(new BlockPos(2, 2, 2));
            alice.teleportTo(dropCheckPos.getX() + 0.5, dropCheckPos.getY(), dropCheckPos.getZ() + 0.5);
            bob.teleportTo(dropCheckPos.getX() + 4.5, dropCheckPos.getY(), dropCheckPos.getZ() + 0.5);
            alice.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 4));
            alice.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            OIMRConfig.setHungerWeight(2.5F);
            OIMRConfig.setXpWeight(0.5F);
            HiveMindManager.applyBooleanSetting(server, alice, OIMRConfig.KEY_SYNC_HOTBAR, false);
            HiveMindManager.applyBooleanSetting(server, alice, OIMRConfig.KEY_SYNC_HELMET, false);

            helper.assertTrue(bob.getInventory().getItem(1).isEmpty() && alice.getInventory().getItem(1).isEmpty(),
                "Disabling syncHotbar must clear shared hotbar slot 1 and switch to empty personal slots");
            helper.assertTrue(bob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && alice.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                "Disabling syncHelmet must clear shared helmet slot and switch to empty personal equipment");

            AABB dropArea = new AABB(dropCheckPos).inflate(8.0D);
            List<ItemEntity> droppedItems = level.getEntitiesOfClass(ItemEntity.class, dropArea);
            int droppedDiamonds = droppedItems.stream()
                .filter(e -> e.getItem().is(Items.DIAMOND))
                .mapToInt(e -> e.getItem().getCount())
                .sum();
            int droppedHelmets = droppedItems.stream()
                .filter(e -> e.getItem().is(Items.IRON_HELMET))
                .mapToInt(e -> e.getItem().getCount())
                .sum();
            helper.assertTrue(droppedDiamonds == 4 && droppedHelmets == 1,
                "Disabling syncHotbar and syncHelmet must canonically drop the 4 diamonds and 1 iron helmet (diamonds="
                    + droppedDiamonds + ", helmets=" + droppedHelmets + ")");

            HiveMindManager.applyBooleanSetting(server, alice, OIMRConfig.KEY_SYNC_HOTBAR, true);
            HiveMindManager.applyBooleanSetting(server, alice, OIMRConfig.KEY_SYNC_HELMET, true);
            helper.assertTrue(bob.getInventory().getItem(1).isEmpty()
                    && bob.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                "Re-enabling syncHotbar & syncHelmet must NOT duplicate previously shared items");
            droppedItems.forEach(Entity::discard);

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setSyncEffects(prevSyncEffects);
            OIMRConfig.setSyncHotbar(prevSyncHotbar);
            OIMRConfig.setSyncHelmet(prevSyncHelmet);
            OIMRConfig.setHungerWeight(prevHungerWeight);
            OIMRConfig.setXpWeight(prevXpWeight);
            HiveMindManager.onConfigChanged(server);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testRuntimeSlotSyncDisableDropsSharedItemsWithoutDuplication(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevHotbar = OIMRConfig.isSyncHotbarEnabled();
        boolean prevMain = OIMRConfig.isSyncMainHandEnabled();
        boolean prevOff = OIMRConfig.isSyncOffHandEnabled();
        boolean prevHead = OIMRConfig.isSyncHelmetEnabled();
        boolean prevChest = OIMRConfig.isSyncChestplateEnabled();
        boolean prevLegs = OIMRConfig.isSyncLeggingsEnabled();
        boolean prevBoots = OIMRConfig.isSyncBootsEnabled();

        ServerPlayer alice = spawnTestPlayer(server, level, "SlotSyncAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "SlotSyncBob");
        BlockPos alicePos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos bobPos = helper.absolutePos(new BlockPos(18, 2, 2));
        BlockPos offlinePos = helper.absolutePos(new BlockPos(34, 2, 2));
        PlayerTeam offlineTeam = null;
        ServerPlayer charlie = null;
        try {
            HiveMindManager.resetConfigToDefaults(server, alice);
            alice.teleportTo(alicePos.getX() + 0.5, alicePos.getY(), alicePos.getZ() + 0.5);
            bob.teleportTo(bobPos.getX() + 0.5, bobPos.getY(), bobPos.getZ() + 0.5);

            // Populate every sync-toggleable slot
            alice.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
            alice.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 4));
            alice.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            alice.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            alice.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            alice.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            alice.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));

            // 1. Bob disables syncHotbar (while syncMainHand=true) -> slot 1 drops at Bob, slot 0 stays synced
            HiveMindManager.applyBooleanSetting(server, bob, OIMRConfig.KEY_SYNC_HOTBAR, false);
            List<ItemEntity> bobDrops1 = level.getEntitiesOfClass(ItemEntity.class, new AABB(bobPos).inflate(4.0D));
            int bobDiamonds = bobDrops1.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(bobDiamonds == 4,
                "Disabling syncHotbar by Bob must drop slot 1's 4 diamonds at Bob (got " + bobDiamonds + ")");
            helper.assertTrue(alice.getInventory().getItem(0).is(Items.NETHERITE_SWORD)
                    && bob.getInventory().getItem(0).is(Items.NETHERITE_SWORD),
                "Hotbar slot 0 must stay synced while syncMainHand=true");
            bobDrops1.forEach(Entity::discard);

            // Personal slot 1 remains isolated when syncHotbar toggles ON and back OFF
            alice.getInventory().setItem(1, new ItemStack(Items.EMERALD, 1));
            HiveMindManager.applyBooleanSetting(server, alice, OIMRConfig.KEY_SYNC_HOTBAR, true);
            helper.assertTrue(alice.getInventory().getItem(1).isEmpty() && bob.getInventory().getItem(1).isEmpty(),
                "Re-enabling syncHotbar must NOT bring back previously dropped diamonds or leak Alice's personal emerald");
            HiveMindManager.applyBooleanSetting(server, alice, OIMRConfig.KEY_SYNC_HOTBAR, false);
            helper.assertTrue(alice.getInventory().getItem(1).is(Items.EMERALD)
                    && alice.getInventory().getItem(1).getCount() == 1
                    && bob.getInventory().getItem(1).isEmpty(),
                "Disabling syncHotbar again must restore Alice's personal emerald without duplication");

            // 2. Disabling syncMainHand while Alice draws a bow stops use & drops sword canonically
            alice.getInventory().setItem(9, new ItemStack(Items.ARROW, 1));
            alice.getInventory().setItem(0, new ItemStack(Items.BOW));
            alice.getInventory().setSelectedSlot(0);
            alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(alice.isUsingItem(), "Alice must be actively drawing the bow before syncMainHand is disabled");

            HiveMindManager.applyBooleanSetting(server, alice, OIMRConfig.KEY_SYNC_MAIN_HAND, false);
            helper.assertFalse(alice.isUsingItem(),
                "Disabling syncMainHand must cancel active item use on main hand before dropping slot 0");
            List<ItemEntity> aliceDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(alicePos).inflate(4.0D));
            long droppedBows = aliceDrops.stream().filter(e -> e.getItem().is(Items.BOW)).count();
            helper.assertTrue(droppedBows == 1 && alice.getInventory().getItem(0).isEmpty() && bob.getInventory().getItem(0).isEmpty(),
                "Disabling syncMainHand must drop the shared bow once and clear slot 0");
            helper.assertTrue(alice.getInventory().getItem(9).is(Items.ARROW) && alice.getInventory().getItem(9).getCount() == 1,
                "Stopping bow draw on syncMainHand disable must refund the reserved arrow to shared inventory");
            aliceDrops.forEach(Entity::discard);

            // 3. Console/RCON (initiator == null) drops equipment slots to deterministic priority member
            ServerPlayer priorityMember = alice.getUUID().compareTo(bob.getUUID()) < 0 ? alice : bob;
            BlockPos priorityPos = priorityMember == alice ? alicePos : bobPos;
            for (String key : List.of(
                OIMRConfig.KEY_SYNC_OFF_HAND,
                OIMRConfig.KEY_SYNC_HELMET,
                OIMRConfig.KEY_SYNC_CHESTPLATE,
                OIMRConfig.KEY_SYNC_LEGGINGS,
                OIMRConfig.KEY_SYNC_BOOTS
            )) {
                HiveMindManager.applyBooleanSetting(server, null, key, false);
            }
            List<ItemEntity> priorityDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(priorityPos).inflate(4.0D));
            helper.assertTrue(priorityDrops.size() == 5,
                "Console/RCON disabling all 5 equipment slots must drop all 5 shared items at the priority member (got "
                    + priorityDrops.size() + ")");
            priorityDrops.forEach(Entity::discard);

            // Re-enabling all slot syncs must NOT resurrect any dropped item
            HiveMindManager.resetConfigToDefaults(server, alice);
            helper.assertTrue(alice.getInventory().getItem(0).isEmpty()
                    && alice.getOffhandItem().isEmpty()
                    && alice.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && alice.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && alice.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                    && alice.getItemBySlot(EquipmentSlot.FEET).isEmpty(),
                "Re-enabling all slot syncs must never duplicate previously dropped shared items");

            // 4. Offline hive queues pending sync drop and drops canonically when next member attaches
            ServerScoreboard scoreboard = server.getScoreboard();
            offlineTeam = scoreboard.addPlayerTeam("offline_drop_team");
            charlie = spawnTestPlayer(server, level, "SlotSyncCharlie");
            charlie.teleportTo(offlinePos.getX() + 0.5, offlinePos.getY(), offlinePos.getZ() + 0.5);
            scoreboard.addPlayerToTeam(charlie.getScoreboardName(), offlineTeam);
            HiveMindManager.syncAllPlayerTeams(server);
            charlie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            server.getPlayerList().remove(charlie);
            charlie = null;

            HiveMindManager.applyBooleanSetting(server, null, OIMRConfig.KEY_SYNC_CHESTPLATE, false);
            HiveMind offlineHive = HiveMindData.get(server).getIfPresent(HiveMindData.TEAM_HIVE_PREFIX + "offline_drop_team");
            helper.assertTrue(offlineHive != null && offlineHive.getEquipmentMap().getOrDefault(EquipmentSlot.CHEST, ItemStack.EMPTY).isEmpty(),
                "Offline hive must clear chestplate from shared equipment immediately when syncChestplate is disabled");

            charlie = spawnTestPlayer(server, level, "SlotSyncCharlie2");
            charlie.teleportTo(offlinePos.getX() + 0.5, offlinePos.getY(), offlinePos.getZ() + 0.5);
            scoreboard.addPlayerToTeam(charlie.getScoreboardName(), offlineTeam);
            HiveMindManager.syncAllPlayerTeams(server);

            List<ItemEntity> offlineDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(offlinePos).inflate(4.0D));
            long droppedIronChestplates = offlineDrops.stream().filter(e -> e.getItem().is(Items.IRON_CHESTPLATE)).count();
            helper.assertTrue(droppedIronChestplates == 1,
                "Queued pending sync drop must drop canonically when a member joins the offline hive");
            offlineDrops.forEach(Entity::discard);

            helper.succeed();
        } finally {
            if (charlie != null) {
                cleanupPlayers(server, charlie);
            }
            if (offlineTeam != null) {
                server.getScoreboard().removePlayerTeam(offlineTeam);
            }
            OIMRConfig.setSyncHotbar(prevHotbar);
            OIMRConfig.setSyncMainHand(prevMain);
            OIMRConfig.setSyncOffHand(prevOff);
            OIMRConfig.setSyncHelmet(prevHead);
            OIMRConfig.setSyncChestplate(prevChest);
            OIMRConfig.setSyncLeggings(prevLegs);
            OIMRConfig.setSyncBoots(prevBoots);
            HiveMindManager.onConfigChanged(server);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testAuthoritativeAmmoReservationForBowAndCrossbow(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        TestPlayerHandle aliceHandle = spawnTestPlayerWithChannel(server, level, "AmmoAlice");
        TestPlayerHandle bobHandle = spawnTestPlayerWithChannel(server, level, "AmmoBob");
        ServerPlayer alice = aliceHandle.player();
        ServerPlayer bob = bobHandle.player();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        try {
            HiveMindManager.resetConfigToDefaults(server, alice);
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            alice.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            bob.teleportTo(pos.getX() + 6.5, pos.getY(), pos.getZ() + 0.5);
            alice.getInventory().setSelectedSlot(0);
            bob.getInventory().setSelectedSlot(0);

            // 1. Group has 1 Bow (shared in slot 0) / or 2 Bows (slot 0 & 1) and 1 Arrow (slot 9)
            alice.getInventory().setItem(0, new ItemStack(Items.BOW));
            alice.getInventory().setItem(1, new ItemStack(Items.BOW));
            bob.getInventory().setSelectedSlot(1);
            alice.getInventory().setItem(9, new ItemStack(Items.ARROW, 1));
            alice.containerMenu.broadcastChanges();
            bob.containerMenu.broadcastChanges();

            // Alice starts drawing Bow -> arrow immediately reserved on server, Bob cannot see or draw it
            aliceHandle.channel().outboundMessages().clear();
            InteractionResult aliceDraw = alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(aliceDraw.consumesAction() && alice.isUsingItem(),
                "Alice must start drawing bow when 1 arrow is available");
            helper.assertTrue(bob.getInventory().getItem(9).isEmpty() && bob.getProjectile(bob.getMainHandItem()).isEmpty(),
                "While Alice is drawing, the last arrow must immediately be unavailable to Bob on server");

            // Alice's client still receives visual preservation of her reserved arrow while drawing
            aliceHandle.channel().outboundMessages().clear();
            alice.inventoryMenu.sendAllDataToRemote();
            ClientboundContainerSetContentPacket aliceMidDrawContent = null;
            Object rawPacket;
            while ((rawPacket = aliceHandle.channel().outboundMessages().poll()) != null) {
                if (rawPacket instanceof ClientboundContainerSetContentPacket content) {
                    aliceMidDrawContent = content;
                }
            }
            int slot9MenuIdx = alice.inventoryMenu.slots.stream()
                .filter(s -> s.container == alice.getInventory() && s.getContainerSlot() == 9)
                .findFirst()
                .orElseThrow()
                .index;
            helper.assertTrue(aliceMidDrawContent != null && aliceMidDrawContent.items().get(slot9MenuIdx).is(Items.ARROW)
                    && aliceMidDrawContent.items().get(slot9MenuIdx).getCount() == 1,
                "Drawing player's client must keep seeing the reserved arrow during the draw");

            InteractionResult bobDraw = bob.gameMode.useItem(bob, level, bob.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertFalse(bobDraw.consumesAction() || bob.isUsingItem(),
                "Bob must fail to start drawing while Alice has reserved the last arrow");

            // Alice charges and fires -> exactly 1 arrow entity spawned, 0 left in inventory
            for (int i = 0; i < 12; i++) {
                alice.doTick();
            }
            alice.releaseUsingItem();
            List<AbstractArrow> firedArrows = level.getEntitiesOfClass(AbstractArrow.class, new AABB(pos).inflate(32.0D));
            helper.assertTrue(firedArrows.size() == 1,
                "Firing reserved bow must spawn exactly 1 arrow (got " + firedArrows.size() + ")");
            helper.assertTrue(alice.getInventory().getItem(9).isEmpty() && bob.getInventory().getItem(9).isEmpty(),
                "Shared inventory must have 0 arrows after firing");
            firedArrows.forEach(Entity::discard);

            // 2. Cancel with full inventory -> canonical drop of the reserved Spectral Arrow
            alice.getInventory().setItem(9, new ItemStack(Items.SPECTRAL_ARROW, 1));
            alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(alice.isUsingItem() && bob.getInventory().getItem(9).isEmpty(),
                "Spectral arrow must be reserved during Alice's draw");
            for (int i = 2; i < 36; i++) {
                alice.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 1));
            }
            alice.stopUsingItem();
            List<ItemEntity> fullInvDrops = level.getEntitiesOfClass(ItemEntity.class, alice.getBoundingBox().inflate(4.0D));
            long droppedSpectral = fullInvDrops.stream().filter(e -> e.getItem().is(Items.SPECTRAL_ARROW)).count();
            helper.assertTrue(droppedSpectral == 1,
                "Cancelling bow draw with a full inventory must canonically drop the reserved arrow at the player (got "
                    + droppedSpectral + ")");
            fullInvDrops.forEach(Entity::discard);
            for (int i = 2; i < 36; i++) {
                alice.getInventory().setItem(i, ItemStack.EMPTY);
            }

            // 3. Cancel with space -> returns to shared inventory; Disconnect while drawing -> returns to shared inventory
            alice.getInventory().setItem(9, new ItemStack(Items.ARROW, 1));
            alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            alice.stopUsingItem();
            helper.assertTrue(bob.getInventory().getItem(9).is(Items.ARROW) && bob.getInventory().getItem(9).getCount() == 1,
                "Cancelling draw with inventory space must return the reserved arrow to shared inventory");

            // 4. Crossbow + Firework Rocket in offhand
            alice.getInventory().setItem(0, new ItemStack(Items.CROSSBOW));
            alice.getInventory().setItem(1, new ItemStack(Items.CROSSBOW));
            alice.getInventory().setItem(9, ItemStack.EMPTY);
            alice.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET, 1));

            InteractionResult aliceCrossbowDraw = alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(aliceCrossbowDraw.consumesAction() && alice.isUsingItem(),
                "Alice must start charging crossbow with 1 firework in offhand");
            helper.assertTrue(bob.getOffhandItem().isEmpty(),
                "Bob must see offhand firework immediately reserved while Alice charges crossbow");
            InteractionResult bobCrossbowDraw = bob.gameMode.useItem(bob, level, bob.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertFalse(bobCrossbowDraw.consumesAction() || bob.isUsingItem(),
                "Bob must fail to start charging second crossbow while last firework is reserved");

            int chargeTicks = CrossbowItem.getChargeDuration(alice.getMainHandItem(), alice) + 2;
            for (int i = 0; i < chargeTicks; i++) {
                alice.doTick();
            }
            alice.releaseUsingItem();
            ChargedProjectiles charged = alice.getMainHandItem().get(DataComponents.CHARGED_PROJECTILES);
            helper.assertTrue(charged != null && charged.contains(Items.FIREWORK_ROCKET) && alice.getOffhandItem().isEmpty(),
                "Completed crossbow charge must load the reserved firework without duplicating it in offhand");

            helper.succeed();
        } finally {
            level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testIndividualCooldownsAndImmediateLastThrowableConsumption(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = spawnTestPlayer(server, level, "ThrowAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "ThrowBob");
        BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 6));
        try {
            HiveMindManager.resetConfigToDefaults(server, alice);
            alice.teleportTo(pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5);
            bob.teleportTo(pos.getX() + 6.5, pos.getY() + 2.0, pos.getZ() + 0.5);
            alice.getInventory().setSelectedSlot(0);
            bob.getInventory().setSelectedSlot(0);

            // 1. Single Ender Pearl: Alice throws -> immediate consumption + individual cooldown; Bob fails in same tick
            ItemStack singlePearl = new ItemStack(Items.ENDER_PEARL, 1);
            alice.getInventory().setItem(0, singlePearl);
            InteractionResult alicePearl = alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(alicePearl.consumesAction(), "Alice must succeed throwing the last ender pearl");
            helper.assertTrue(alice.getInventory().getItem(0).isEmpty() && bob.getInventory().getItem(0).isEmpty(),
                "Last ender pearl must immediately be empty in shared inventory");
            helper.assertTrue(alice.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL)),
                "Alice must receive her personal ender pearl cooldown");
            helper.assertFalse(bob.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL)),
                "Bob must NOT receive Alice's ender pearl cooldown");

            // Bob attempts in the exact same tick using both current slot and stale stack reference
            InteractionResult bobFresh = bob.gameMode.useItem(bob, level, bob.getMainHandItem(), InteractionHand.MAIN_HAND);
            InteractionResult bobStale = bob.gameMode.useItem(bob, level, singlePearl, InteractionHand.MAIN_HAND);
            helper.assertFalse(bobFresh.consumesAction() || bobStale.consumesAction(),
                "Bob must not be able to throw the already-consumed last ender pearl in the same tick");
            List<ThrownEnderpearl> pearls = level.getEntitiesOfClass(ThrownEnderpearl.class, new AABB(pos).inflate(16.0D));
            helper.assertTrue(pearls.size() == 1,
                "Only 1 ThrownEnderpearl may exist when 2 players race the last pearl (got " + pearls.size() + ")");
            pearls.forEach(Entity::discard);

            // 2. Single Snowball: Alice throws -> Bob fails in same tick
            ItemStack singleSnowball = new ItemStack(Items.SNOWBALL, 1);
            alice.getInventory().setItem(0, singleSnowball);
            InteractionResult aliceSnow = alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            InteractionResult bobSnow = bob.gameMode.useItem(bob, level, singleSnowball, InteractionHand.MAIN_HAND);
            helper.assertTrue(aliceSnow.consumesAction() && !bobSnow.consumesAction(),
                "Only Alice may throw the last snowball when racing in the same tick");
            List<Snowball> snowballs = level.getEntitiesOfClass(Snowball.class, new AABB(pos).inflate(16.0D));
            helper.assertTrue(snowballs.size() == 1,
                "Only 1 Snowball entity may spawn from 1 shared snowball (got " + snowballs.size() + ")");
            snowballs.forEach(Entity::discard);

            // 3. Two Ender Pearls: Alice throws 1, gets cooldown; Bob is NOT blocked and immediately throws the 2nd
            alice.getInventory().setItem(0, new ItemStack(Items.ENDER_PEARL, 2));
            // Wait out Alice's cooldown first so she can throw again, or verify fresh throw with Wind Charge / fresh cooldown
            for (int i = 0; i < 25; i++) {
                alice.doTick();
                bob.doTick();
            }
            helper.assertFalse(alice.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL)),
                "Alice's cooldown should expire after 25 ticks");

            InteractionResult aliceFirstOfTwo = alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(aliceFirstOfTwo.consumesAction(), "Alice throws 1st of 2 ender pearls");
            helper.assertTrue(alice.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))
                    && !bob.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL)),
                "Bob's cooldown must remain independent when Alice throws 1st of 2 pearls");

            InteractionResult bobSecondOfTwo = bob.gameMode.useItem(bob, level, bob.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(bobSecondOfTwo.consumesAction(),
                "Bob must immediately be able to throw the 2nd ender pearl without being blocked by Alice's cooldown");
            helper.assertTrue(bob.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))
                    && alice.getInventory().getItem(0).isEmpty(),
                "After both throw, both have independent cooldowns and 0 pearls remain");

            List<ThrownEnderpearl> twoPearls = level.getEntitiesOfClass(ThrownEnderpearl.class, new AABB(pos).inflate(16.0D));
            helper.assertTrue(twoPearls.size() == 2,
                "Two ender pearls shared between two players must spawn 2 projectiles (got " + twoPearls.size() + ")");
            twoPearls.forEach(Entity::discard);

            // 4. Wind Charge cooldown independence & last-item guard
            alice.getInventory().setItem(0, new ItemStack(Items.WIND_CHARGE, 1));
            InteractionResult aliceWind = alice.gameMode.useItem(alice, level, alice.getMainHandItem(), InteractionHand.MAIN_HAND);
            InteractionResult bobWind = bob.gameMode.useItem(bob, level, bob.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(aliceWind.consumesAction() && !bobWind.consumesAction(),
                "Last wind charge must be consumed immediately and not usable by Bob");
            helper.assertTrue(alice.getCooldowns().isOnCooldown(new ItemStack(Items.WIND_CHARGE))
                    && !bob.getCooldowns().isOnCooldown(new ItemStack(Items.WIND_CHARGE)),
                "Wind charge cooldown must remain individual per player");
            level.getEntitiesOfClass(WindCharge.class, new AABB(pos).inflate(16.0D)).forEach(Entity::discard);

            helper.succeed();
        } finally {
            cleanupPlayers(server, alice, bob);
        }
    }
}
