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

public class InventoryIntegrityGameTests {

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
                "Queued pending sync drop must drop canonically when a member joins the offline hive (drops="
                    + droppedIronChestplates + ", totalNearby=" + offlineDrops.size()
                    + ", charliePos=" + charlie.position() + ", offlinePos=" + offlinePos
                    + ", charlieHive=" + HiveMindManager.getHiveId(charlie)
                    + ", syncChest=" + OIMRConfig.isSyncChestplateEnabled() + ")");
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
