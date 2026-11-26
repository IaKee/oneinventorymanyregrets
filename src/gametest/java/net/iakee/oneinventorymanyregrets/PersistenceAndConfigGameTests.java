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

public class PersistenceAndConfigGameTests {

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
    public void testTeamRemovalMarksOrphanedAndRecreationDoesNotInheritState(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerScoreboard scoreboard = server.getScoreboard();
        String teamName = "orphan_recreate_team";
        String hiveId = HiveMindData.TEAM_HIVE_PREFIX + teamName;
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        ServerPlayer alice = spawnTestPlayer(server, level, "OrphanAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "OrphanBob");
        try {
            scoreboard.addPlayerToTeam(alice.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(bob.getScoreboardName(), team);
            alice.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 12));
            alice.giveExperiencePoints(120);

            HiveMind originalHive = HiveMindData.get(server).getIfPresent(hiveId);
            helper.assertTrue(originalHive != null && !originalHive.isOrphaned() && originalHive.getLastActiveEpoch() > 0L,
                "Active team hive must exist with activity timestamp and not be orphaned");

            // Remove the scoreboard team -> members return to default and team hive is marked orphaned
            scoreboard.removePlayerTeam(team);
            team = null;

            helper.assertTrue(HiveMindManager.getHiveId(alice).equals(HiveMindData.DEFAULT_HIVE_ID)
                    && HiveMindManager.getHiveId(bob).equals(HiveMindData.DEFAULT_HIVE_ID),
                "Removing scoreboard team must detach members back to default hive");
            helper.assertTrue(originalHive.isOrphaned() && originalHive.getOrphanedEpoch() > 0L,
                "Removing scoreboard team must mark its HiveMind as orphaned with timestamp");

            // Dry-run cleanup inspects the orphaned hive without deleting it
            int dryRunCount = server.getCommands().getDispatcher()
                .execute("oimr cleanup dry-run", server.createCommandSourceStack());
            helper.assertTrue(dryRunCount >= 1 && HiveMindData.get(server).getIfPresent(hiveId) != null,
                "Dry-run cleanup must report orphaned team without removing it");

            // Recreating a team with the same name must NOT inherit old diamonds or XP
            team = scoreboard.addPlayerTeam(teamName);
            scoreboard.addPlayerToTeam(bob.getScoreboardName(), team);
            helper.assertTrue(bob.getInventory().getItem(0).isEmpty(),
                "Recreated team with the same name must not inherit items from deleted team");
            helper.assertTrue(bob.experienceLevel == 0 && bob.totalExperience == 0,
                "Recreated team with the same name must not inherit XP from deleted team");
            HiveMind recreatedHive = HiveMindData.get(server).getIfPresent(hiveId);
            helper.assertTrue(recreatedHive != null && recreatedHive != originalHive && !recreatedHive.isOrphaned(),
                "Recreated team must get a fresh non-orphaned HiveMind instance");

            helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(e);
        } finally {
            cleanupPlayers(server, alice, bob);
            if (team != null && scoreboard.getPlayerTeam(teamName) != null) {
                scoreboard.removePlayerTeam(team);
            }
            HiveMindData.get(server).removeHive(hiveId);
        }
    }

    @GameTest
    public void testCleanupCommandDryRunConfirmAndPersonalStatePruning(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerScoreboard scoreboard = server.getScoreboard();
        HiveMindData data = HiveMindData.get(server);

        String orphanTeamName = "cleanup_orphan_team";
        String orphanHiveId = HiveMindData.TEAM_HIVE_PREFIX + orphanTeamName;
        String emptyTeamName = "cleanup_empty_team";
        String emptyHiveId = HiveMindData.TEAM_HIVE_PREFIX + emptyTeamName;
        PlayerTeam emptyTeam = scoreboard.getPlayerTeam(emptyTeamName);
        if (emptyTeam == null) {
            emptyTeam = scoreboard.addPlayerTeam(emptyTeamName);
        }

        UUID stalePersonalId = UUID.randomUUID();
        UUID recentPersonalId = UUID.randomUUID();
        ServerPlayer emptyLeavePlayer = spawnTestPlayer(server, level, "CleanupEmptyPersonal");
        UUID emptyLeaveId = emptyLeavePlayer.getUUID();
        try {
            // 1. Leaving with an empty PersonalInventoryState prunes it automatically
            helper.assertTrue(data.getPersonalStateIfPresent(emptyLeaveId) != null,
                "Online player gets a PersonalInventoryState while attached");
            server.getPlayerList().remove(emptyLeavePlayer);
            emptyLeavePlayer = null;
            helper.assertTrue(data.getPersonalStateIfPresent(emptyLeaveId) == null,
                "Leaving with an empty PersonalInventoryState must prune it immediately");

            // 2. Seed an orphaned deleted team, an inactive empty team (35 days old), and stale vs recent personal states
            HiveMind orphanHive = data.getOrCreate(orphanHiveId);
            orphanHive.markInitializedEmpty();
            orphanHive.markOrphaned(1_000L);

            long thirtyFiveDaysAgo = java.time.Instant.now().getEpochSecond() - 35L * 86_400L;
            HiveMind emptyHive = data.getOrCreate(emptyHiveId);
            emptyHive.markInitializedEmpty();
            emptyHive.setLastActiveEpoch(thirtyFiveDaysAgo);

            var stalePersonal = data.getOrCreatePersonalState(stalePersonalId);
            stalePersonal.getHotbarItems().set(0, new ItemStack(Items.COAL, 3));
            stalePersonal.setLastActiveEpoch(thirtyFiveDaysAgo);

            var recentPersonal = data.getOrCreatePersonalState(recentPersonalId);
            recentPersonal.getHotbarItems().set(0, new ItemStack(Items.IRON_INGOT, 2));
            recentPersonal.touchActivity(java.time.Instant.now().getEpochSecond(), level.getGameTime());

            // Ensure default hive exists so we verify it is never purged
            data.getOrCreate(HiveMindData.DEFAULT_HIVE_ID);

            int dryRun = server.getCommands().getDispatcher()
                .execute("oimr cleanup dry-run", server.createCommandSourceStack());
            helper.assertTrue(dryRun >= 3, "Dry-run must find orphaned team, stale empty team, and stale personal state");
            helper.assertTrue(data.getIfPresent(orphanHiveId) != null
                    && data.getIfPresent(emptyHiveId) != null
                    && data.getPersonalStateIfPresent(stalePersonalId) != null,
                "Dry-run must not mutate persisted states");

            int executed = server.getCommands().getDispatcher()
                .execute("oimr cleanup confirm", server.createCommandSourceStack());
            helper.assertTrue(executed >= 3, "Confirmed cleanup must report purged entries");
            helper.assertTrue(data.getIfPresent(orphanHiveId) == null, "Orphaned team hive must be purged");
            helper.assertTrue(data.getIfPresent(emptyHiveId) == null, "Inactive empty team hive >30d must be purged");
            helper.assertTrue(data.getPersonalStateIfPresent(stalePersonalId) == null, "Stale personal state >30d must be purged");
            helper.assertTrue(data.getPersonalStateIfPresent(recentPersonalId) != null,
                "Recent non-empty personal state must be preserved by 30-day retention");
            helper.assertTrue(data.getIfPresent(HiveMindData.DEFAULT_HIVE_ID) != null,
                "Default hive must never be purged by cleanup");

            helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(e);
        } finally {
            if (emptyLeavePlayer != null) {
                cleanupPlayers(server, emptyLeavePlayer);
            }
            if (emptyTeam != null && scoreboard.getPlayerTeam(emptyTeamName) != null) {
                scoreboard.removePlayerTeam(emptyTeam);
            }
            data.removeHive(orphanHiveId);
            data.removeHive(emptyHiveId);
            data.removePersonalState(stalePersonalId);
            data.removePersonalState(recentPersonalId);
            data.removePersonalState(emptyLeaveId);
        }
    }

    @GameTest
    public void testReloadCommandFailurePreservesActiveSettings(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        boolean prevSyncHotbar = OIMRConfig.isSyncHotbarEnabled();
        java.nio.file.Path configPath = OIMRConfig.getConfigPath();
        byte[] backupBytes = null;
        boolean hadFile = java.nio.file.Files.isRegularFile(configPath);
        try {
            if (hadFile) {
                backupBytes = java.nio.file.Files.readAllBytes(configPath);
            }
            OIMRConfig.setAffinityPercent(55);
            HiveMindManager.onConfigChanged(server);

            // Corrupt the config file with an invalid unicode escape so Properties.load throws IllegalArgumentException
            java.nio.file.Files.createDirectories(configPath.getParent());
            java.nio.file.Files.writeString(
                configPath,
                "affinity=15\nbroken=\\" + "uZZZZ\n",
                java.nio.charset.StandardCharsets.UTF_8
            );

            int reloadResult = server.getCommands().getDispatcher()
                .execute("oimr settings reload", server.createCommandSourceStack());
            helper.assertTrue(reloadResult == 0,
                "/oimr settings reload must return 0 when config reload fails");
            helper.assertTrue(OIMRConfig.getAffinityPercent() == 55,
                "Failed reload must preserve active in-memory affinity (expected 55, got " + OIMRConfig.getAffinityPercent() + ")");

            helper.succeed();
        } catch (java.io.IOException | com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(e);
        } finally {
            try {
                if (hadFile && backupBytes != null) {
                    java.nio.file.Files.write(configPath, backupBytes);
                } else {
                    java.nio.file.Files.deleteIfExists(configPath);
                }
            } catch (java.io.IOException ignored) {
            }
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setSyncHotbar(prevSyncHotbar);
            HiveMindManager.onConfigChanged(server);
        }
    }
}
