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

public class EffectsAndEnvironmentGameTests {

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

            // 1. At 50% affinity, splash potion hitting only Alice propagates fine-grained Fast Walking II (400t) to Bob
            //    and non-subdivided effects like Blindness scale duration by 50%.
            OIMRConfig.setAffinityPercent(50);
            alice.setPos(center);
            bob.setPos(center.x + 25.0, center.y, center.z);

            ItemStack speedSplash = new ItemStack(Items.SPLASH_POTION);
            speedSplash.set(
                DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.empty(), List.of(
                    new MobEffectInstance(MobEffects.SPEED, 400, 0),
                    new MobEffectInstance(MobEffects.BLINDNESS, 400, 0)
                ), Optional.empty())
            );
            ThrownSplashPotion splash1 = new ThrownSplashPotion(level, alice, speedSplash);
            splash1.setPos(center.x, center.y + 0.5, center.z);
            splash1.onHitAsPotion(level, speedSplash, new BlockHitResult(new Vec3(center.x, center.y + 0.5, center.z), Direction.UP, BlockPos.containing(center), false));
            splash1.discard();

            MobEffectInstance aliceSpeed = alice.getEffect(MobEffects.SPEED);
            MobEffectInstance bobFastWalk = bob.getEffect(OIMRMobEffects.FAST_WALKING);
            MobEffectInstance bobBlind = bob.getEffect(MobEffects.BLINDNESS);
            helper.assertTrue(aliceSpeed != null && bobFastWalk != null && bobFastWalk.getAmplifier() == 1,
                "At 50% affinity Bob must receive Fast Walking II (amp 1) from Speed I splash hitting Alice");
            helper.assertTrue(bobBlind != null && Math.abs(bobBlind.getDuration() - 200) <= 2,
                "Bob's splash Blindness duration must be 50% of Alice's (got " + (bobBlind != null ? bobBlind.getDuration() : -1) + ")");

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

            // 1. Effect duration accumulation at 1% affinity: 40 ticks of Blindness -> 0.4 ticks per application
            OIMRConfig.setAffinityPercent(1);
            alice.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.BLINDNESS),
                "0.4 ticks of Blindness must not apply yet");
            helper.assertTrue(Math.abs(hive.getEffectDurationRemainder(bob.getUUID(), MobEffects.BLINDNESS) - 0.4D) < 1.0E-5D,
                "Bob must store 0.4 Blindness duration remainder");

            alice.removeEffect(MobEffects.BLINDNESS);
            alice.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0)); // 0.8 total
            helper.assertFalse(bob.hasEffect(MobEffects.BLINDNESS),
                "0.8 ticks of Blindness must still wait in remainder");

            alice.removeEffect(MobEffects.BLINDNESS);
            alice.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0)); // 1.2 total -> applies 1 tick, keeps 0.2
            MobEffectInstance bobBlind = bob.getEffect(MobEffects.BLINDNESS);
            helper.assertTrue(bobBlind != null && bobBlind.getDuration() == 1,
                "1.2 total ticks must apply 1 tick of Blindness to Bob (got " + (bobBlind != null ? bobBlind.getDuration() : -1) + ")");
            helper.assertTrue(Math.abs(hive.getEffectDurationRemainder(bob.getUUID(), MobEffects.BLINDNESS) - 0.2D) < 1.0E-5D,
                "0.2 ticks remainder must stay stored after applying 1 tick");

            // Two more 40-tick hits: 0.2 + 0.4 + 0.4 = 1.0 -> applies 1 tick, 0.0 remainder
            bob.removeEffect(MobEffects.BLINDNESS);
            alice.removeEffect(MobEffects.BLINDNESS);
            alice.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            alice.removeEffect(MobEffects.BLINDNESS);
            alice.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            MobEffectInstance bobBlind2 = bob.getEffect(MobEffects.BLINDNESS);
            helper.assertTrue(bobBlind2 != null && bobBlind2.getDuration() == 1
                && hive.getEffectDurationRemainder(bob.getUUID(), MobEffects.BLINDNESS) < 1.0E-5D,
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

            // 1. Full amplifier preserved on duration-scaled effects (Blindness III 200t at 1% -> Blindness III 2t)
            //    and Speed III (200t) at 1% maps to Fast Walking I (30t = 200 * 0.15)
            OIMRConfig.setAffinityPercent(1);
            alice.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 2));
            MobEffectInstance bobBlind = bob.getEffect(MobEffects.BLINDNESS);
            helper.assertTrue(bobBlind != null && bobBlind.getAmplifier() == 2 && bobBlind.getDuration() == 2,
                "Bob must receive full Blindness III (amp 2) for 2 ticks at 1% affinity (got amp="
                    + (bobBlind != null ? bobBlind.getAmplifier() : -1) + ", dur=" + (bobBlind != null ? bobBlind.getDuration() : -1) + ")");
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 2));
            MobEffectInstance bobFastWalk = bob.getEffect(OIMRMobEffects.FAST_WALKING);
            helper.assertTrue(bobFastWalk != null && bobFastWalk.getAmplifier() == 0 && bobFastWalk.getDuration() == 30,
                "Speed III (200t) at 1% affinity must grant Fast Walking I for 30 ticks (got dur="
                    + (bobFastWalk != null ? bobFastWalk.getDuration() : -1) + ")");

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
    public void testUnderwaterBreathModeCap1xVsMultiplicative(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        OIMRConfig.MultiMemberScalingMode prevBreath = OIMRConfig.getUnderwaterBreathMode();
        ServerPlayer alice = spawnTestPlayer(server, level, "AirCapAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "AirCapBob");
        BlockPos waterPosA = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos waterPosB = helper.absolutePos(new BlockPos(3, 2, 1));
        try {
            OIMRConfig.setAffinityPercent(100);
            level.setBlock(waterPosA, Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(waterPosA.above(), Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(waterPosB, Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(waterPosB.above(), Blocks.WATER.defaultBlockState(), 3);
            alice.teleportTo(waterPosA.getX() + 0.5, waterPosA.getY(), waterPosA.getZ() + 0.5);
            bob.teleportTo(waterPosB.getX() + 0.5, waterPosB.getY(), waterPosB.getZ() + 0.5);
            alice.baseTick();
            bob.baseTick();
            helper.assertTrue(alice.isEyeInFluid(FluidTags.WATER) && bob.isEyeInFluid(FluidTags.WATER),
                "Both players must have eyes submerged in water");

            HiveMind hive = HiveMindData.get(server).getIfPresent(HiveMindData.DEFAULT_HIVE_ID);
            helper.assertTrue(hive != null, "Default hive must exist");

            // 1. Default 1x cap: 2 submerged players lose 1 air per tick
            OIMRConfig.setUnderwaterBreathMode(OIMRConfig.MultiMemberScalingMode.CAP_1X);
            hive.setAirSupply(300);
            HiveMindManager.resyncPlayerFromHive(alice);
            HiveMindManager.resyncPlayerFromHive(bob);
            int before1x = hive.getAirSupply();
            HiveMindManager.onEndServerTick(server);
            int lost1x = before1x - hive.getAirSupply();
            helper.assertTrue(lost1x == 1,
                "1x underwaterBreathMode must drain 1 air per tick with 2 submerged members (lost=" + lost1x + ")");

            // 2. Multiplicative Nx mode: 2 submerged players lose 2 air per tick
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings underwaterBreathMode nx");
            helper.assertTrue(OIMRConfig.getUnderwaterBreathMode() == OIMRConfig.MultiMemberScalingMode.MULTIPLICATIVE,
                "Command /oimr settings underwaterBreathMode nx must switch to MULTIPLICATIVE");
            hive.setAirSupply(300);
            HiveMindManager.resyncPlayerFromHive(alice);
            HiveMindManager.resyncPlayerFromHive(bob);
            HiveMindManager.onConfigChanged(server);
            int beforeNx = hive.getAirSupply();
            // Advance game time / tick once cleanly
            alice.tickCount++;
            hive.resetTickClaims();
            HiveMindManager.onEndServerTick(server);
            int lostNx = beforeNx - hive.getAirSupply();
            helper.assertTrue(lostNx == 2,
                "Nx underwaterBreathMode must drain 2 air per tick with 2 submerged members (lost=" + lostNx + ")");

            // 3. Water Breathing exempts submerged member from contributing to Nx drain
            bob.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0));
            // At 100% affinity, syncEffects shares Water Breathing so 0 drowners remain -> air refills
            hive.setAirSupply(200);
            hive.resetTickClaims();
            HiveMindManager.onEndServerTick(server);
            helper.assertTrue(hive.getAirSupply() > 200,
                "Water Breathing must allow air refill instead of draining (got " + hive.getAirSupply() + ")");

            helper.succeed();
        } finally {
            level.setBlock(waterPosA, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(waterPosA.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(waterPosB, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(waterPosB.above(), Blocks.AIR.defaultBlockState(), 3);
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setUnderwaterBreathMode(prevBreath);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testReplicatedMechanicalEffectsAtPartialAffinityAndCleanup(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        boolean prevSyncEffects = OIMRConfig.isSyncEffectsEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "ReplAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "ReplBob");
        try {
            OIMRConfig.setSyncEffects(true);
            OIMRConfig.setAffinityPercent(50);
            HiveMindManager.onConfigChanged(server);

            // 1. Invisibility -> Translucency (reduces mob visibility radius proportionally, not full invisibility)
            alice.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0));
            alice.updateDataBeforeSync();
            bob.updateDataBeforeSync();
            helper.assertFalse(bob.hasEffect(MobEffects.INVISIBILITY),
                "Bob must not receive full Invisibility at 50% affinity");
            MobEffectInstance bobTrans = bob.getEffect(OIMRMobEffects.TRANSLUCENCY);
            helper.assertTrue(bobTrans != null && bobTrans.getDuration() >= 398,
                "Bob must receive Translucency with full ~400t duration at 50% affinity");
            double bobVis = bob.getVisibilityPercent(level, null);
            double aliceVis = alice.getVisibilityPercent(level, null);
            helper.assertTrue(bobVis > aliceVis && bobVis < 0.99D,
                "Translucency at 50% must yield intermediate mob visibility (alice=" + aliceVis + ", bob=" + bobVis + ")");

            // 2. Water Breathing -> Aquatic Respiration (probabilistic ~50% air preservation)
            alice.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 400, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.WATER_BREATHING),
                "Bob must not receive full Water Breathing at 50% affinity");
            helper.assertTrue(bob.hasEffect(OIMRMobEffects.AQUATIC_RESPIRATION),
                "Bob must receive Aquatic Respiration at 50% affinity");
            int air = 300;
            for (int i = 0; i < 200; i++) {
                air = ((HiveLivingEntityAccess) bob).oimr$decreaseAirSupply(air);
            }
            int lostAir = 300 - air;
            helper.assertTrue(lostAir > 30 && lostAir < 170,
                "Aquatic Respiration at 50% must lose ~100 air over 200 ticks (lost=" + lostAir + ")");

            // 3. Slow Falling -> Cushioned Fall (reduces gravity and ~50% fall damage)
            alice.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.SLOW_FALLING),
                "Bob must not receive full Slow Falling at 50% affinity");
            helper.assertTrue(bob.hasEffect(OIMRMobEffects.CUSHIONED_FALL),
                "Bob must receive Cushioned Fall at 50% affinity");
            bob.setHealth(20.0F);
            bob.causeFallDamage(13.0F, 1.0F, level.damageSources().fall()); // normal 10 dmg -> ~5 dmg
            float afterCushionedFall = bob.getHealth();
            helper.assertTrue(afterCushionedFall > 12.0F && afterCushionedFall < 19.0F,
                "Cushioned Fall at 50% must halve fall damage (expected ~15 HP, got " + afterCushionedFall + ")");

            // 4. Fire Resistance -> Ember Ward (50% fire/lava reduction instead of binary immunity)
            alice.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.FIRE_RESISTANCE),
                "Bob must not receive full Fire Resistance at 50% affinity");
            helper.assertTrue(bob.hasEffect(OIMRMobEffects.EMBER_WARD),
                "Bob must receive Ember Ward at 50% affinity");
            bob.setHealth(20.0F);
            bob.damageCooldownTime = 0;
            bob.hurtServer(level, level.damageSources().lava(), 4.0F);
            helper.assertTrue(Math.abs(bob.getHealth() - 18.0F) < 0.05F,
                "Ember Ward at 50% must halve 4.0 lava damage to 2.0 (expected 18.0 HP, got " + bob.getHealth() + ")");

            // 5. Glowing -> Faint Glow (intermittent pulse across 20-tick cycle)
            alice.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0));
            helper.assertTrue(bob.hasEffect(OIMRMobEffects.FAINT_GLOW) && !bob.hasEffect(MobEffects.GLOWING),
                "Bob must receive Faint Glow instead of full Glowing at 50% affinity");
            int glowTicks = 0;
            for (int t = 0; t < 20; t++) {
                bob.tickCount = t;
                if (bob.isCurrentlyGlowing()) {
                    glowTicks++;
                }
            }
            helper.assertTrue(glowTicks == 10,
                "Faint Glow at 50% must glow for 10 of 20 ticks (got " + glowTicks + ")");

            // 6. Night Vision, Levitation, Dolphin's Grace, Conduit Power, Breath of the Nautilus counterparts
            alice.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.BREATH_OF_THE_NAUTILUS, 200, 0));
            helper.assertTrue(bob.hasEffect(OIMRMobEffects.TWILIGHT_VISION)
                    && bob.hasEffect(OIMRMobEffects.PARTIAL_LEVITATION)
                    && bob.hasEffect(OIMRMobEffects.FLUID_GRACE)
                    && bob.hasEffect(OIMRMobEffects.CONDUIT_ECHO)
                    && bob.hasEffect(OIMRMobEffects.NAUTILUS_ECHO),
                "All mechanical vanilla effects must replicate to their dedicated custom counterparts at 50% affinity");

            // 7. Cleanup checks:
            // (a) Removing source Invisibility clears Bob's Translucency
            alice.removeEffect(MobEffects.INVISIBILITY);
            helper.assertFalse(bob.hasEffect(OIMRMobEffects.TRANSLUCENCY),
                "Removing source Invisibility must clear replicated Translucency on Bob");

            // (b) Switching affinity to 100% cleans up all replicated effects and syncs originals
            alice.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0));
            helper.assertTrue(bob.hasEffect(OIMRMobEffects.TRANSLUCENCY), "Bob must regain Translucency at 50%");
            OIMRConfig.setAffinityPercent(100);
            HiveMindManager.onConfigChanged(server);
            for (Holder<net.minecraft.world.effect.MobEffect> rep : OIMRMobEffects.allReplicatedEffects()) {
                helper.assertFalse(bob.hasEffect(rep) || alice.hasEffect(rep),
                    "Switching to 100% affinity must clear all replicated effects (" + rep.getRegisteredName() + ")");
            }
            helper.assertTrue(bob.hasEffect(MobEffects.INVISIBILITY),
                "Switching to 100% affinity must restore full Invisibility from teammate");

            // (c) Switching to 50% -> 0% cleans up replicated effects without leakage
            OIMRConfig.setAffinityPercent(50);
            HiveMindManager.onConfigChanged(server);
            alice.removeAllEffects();
            bob.removeAllEffects();
            alice.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0));
            helper.assertTrue(bob.hasEffect(OIMRMobEffects.TWILIGHT_VISION), "Bob must get Twilight Vision at 50%");
            OIMRConfig.setAffinityPercent(0);
            HiveMindManager.onConfigChanged(server);
            helper.assertFalse(bob.hasEffect(OIMRMobEffects.TWILIGHT_VISION),
                "Switching to 0% affinity must clear replicated effects");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setSyncEffects(prevSyncEffects);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testCoarseFineNumericEffectsAndTranslucencySyncPayload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        boolean prevSyncEffects = OIMRConfig.isSyncEffectsEnabled();
        ServerPlayer alice = spawnTestPlayer(server, level, "FineAlice");
        ServerPlayer bob = spawnTestPlayer(server, level, "FineBob");
        try {
            OIMRConfig.setSyncEffects(true);

            // 1. Speed I at 40% affinity -> 0 coarse Speed + Fast Walking II (+8% movement speed)
            OIMRConfig.setAffinityPercent(40);
            HiveMindManager.onConfigChanged(server);
            double baseSpeed = bob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0));
            helper.assertFalse(bob.hasEffect(MobEffects.SPEED),
                "Speed I at 40% affinity must not grant coarse Speed I");
            MobEffectInstance fineWalk40 = bob.getEffect(OIMRMobEffects.FAST_WALKING);
            helper.assertTrue(fineWalk40 != null && fineWalk40.getAmplifier() == 1 && fineWalk40.getDuration() == 400,
                "Speed I at 40% must grant Fast Walking II for full 400t (got amp="
                    + (fineWalk40 != null ? fineWalk40.getAmplifier() : -1) + ")");
            double speedAt40 = bob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(speedAt40 / baseSpeed - 1.08D) < 1.0E-4D,
                "Fast Walking II must increase movement speed by exactly +8% (ratio=" + (speedAt40 / baseSpeed) + ")");

            // 2. Speed II at 60% affinity -> 2 * 0.6 * 5 = 6 fine units -> 1 coarse Speed I (+20%) + 1 Fast Walking I (+4%) = +24%
            OIMRConfig.setAffinityPercent(60);
            HiveMindManager.onConfigChanged(server);
            alice.removeAllEffects();
            bob.removeAllEffects();
            alice.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 1));
            MobEffectInstance coarseSpeed = bob.getEffect(MobEffects.SPEED);
            MobEffectInstance fineWalk = bob.getEffect(OIMRMobEffects.FAST_WALKING);
            helper.assertTrue(coarseSpeed != null && coarseSpeed.getAmplifier() == 0,
                "Speed II at 60% must grant coarse Speed I (amp 0)");
            helper.assertTrue(fineWalk != null && fineWalk.getAmplifier() == 0,
                "Speed II at 60% must grant Fast Walking I (amp 0) as the 6th fine step");
            double speedAt60 = bob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(speedAt60 / baseSpeed - 1.248D) < 1.0E-4D,
                "Speed I (1.20x) + Fast Walking I (1.04x) must yield 1.248x movement speed (ratio=" + (speedAt60 / baseSpeed) + ")");

            // 3. Resistance II (40%) at 60% affinity -> Resistance I (20%) + Warding Aura I (4%) = 24% reduction (10.0 -> 7.6)
            alice.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 1));
            helper.assertTrue(bob.hasEffect(MobEffects.RESISTANCE) && bob.getEffect(MobEffects.RESISTANCE).getAmplifier() == 0
                    && bob.hasEffect(OIMRMobEffects.WARDING_AURA) && bob.getEffect(OIMRMobEffects.WARDING_AURA).getAmplifier() == 0,
                "Resistance II at 60% must split into Resistance I + Warding Aura I");
            bob.setHealth(20.0F);
            bob.damageCooldownTime = 0;
            bob.hurtServer(level, level.damageSources().generic(), 10.0F);
            helper.assertTrue(Math.abs(bob.getHealth() - 12.4F) < 0.05F,
                "Resistance I + Warding Aura I must reduce 10.0 damage by 24% to 7.6 (expected 12.4 HP, got " + bob.getHealth() + ")");

            // 4. Haste II at 60% -> Haste I + Nimble Hands I (+24% mining speed); Jump Boost II at 60% -> Jump Boost I + Spring Step I (0.12F)
            float unbuffedBreak = bob.getDestroySpeed(Blocks.DIRT.defaultBlockState());
            alice.addEffect(new MobEffectInstance(MobEffects.HASTE, 400, 1));
            alice.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 400, 1));
            helper.assertTrue(bob.hasEffect(MobEffects.HASTE) && bob.hasEffect(OIMRMobEffects.NIMBLE_HANDS),
                "Haste II at 60% must split into Haste I + Nimble Hands I");
            float bobBreak = bob.getDestroySpeed(Blocks.DIRT.defaultBlockState());
            helper.assertTrue(Math.abs(bobBreak / unbuffedBreak - 1.24F) < 0.02F,
                "Haste I + Nimble Hands I must boost mining speed by +24% (ratio=" + (bobBreak / unbuffedBreak) + ")");
            helper.assertTrue(Math.abs(bob.getJumpBoostPower() - 0.12F) < 1.0E-4F,
                "Jump Boost I + Spring Step I must yield 0.12F jump boost power (got " + bob.getJumpBoostPower() + ")");

            // 5. Strength, Weakness, Slowness, Mining Fatigue, Luck, Unluck fine counterparts all resolve
            alice.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.LUCK, 200, 0));
            alice.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 200, 0));
            helper.assertTrue(bob.hasEffect(OIMRMobEffects.MIGHT_SURGE)
                    && bob.hasEffect(OIMRMobEffects.FEEBLE_GRIP)
                    && bob.hasEffect(OIMRMobEffects.HEAVY_STEP)
                    && bob.hasEffect(OIMRMobEffects.WEARY_HANDS)
                    && bob.hasEffect(OIMRMobEffects.MINOR_FORTUNE)
                    && bob.hasEffect(OIMRMobEffects.MINOR_JINX),
                "All numeric vanilla effects must map to their fine counterparts at 60% affinity");

            // 6. Translucency sync payload round-trip and removal cleanup
            net.iakee.oneinventorymanyregrets.network.OIMRTranslucencySyncPayload payload =
                net.iakee.oneinventorymanyregrets.network.OIMRTranslucencySyncPayload.of(List.of(bob.getUUID()));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            net.iakee.oneinventorymanyregrets.network.OIMRTranslucencySyncPayload.STREAM_CODEC.encode(buf, payload);
            net.iakee.oneinventorymanyregrets.network.OIMRTranslucencySyncPayload decoded =
                net.iakee.oneinventorymanyregrets.network.OIMRTranslucencySyncPayload.STREAM_CODEC.decode(buf);
            helper.assertTrue(decoded.translucentPlayerIds().equals(List.of(bob.getUUID())),
                "OIMRTranslucencySyncPayload must round-trip translucent player UUIDs");

            alice.removeEffect(MobEffects.SPEED);
            helper.assertFalse(bob.hasEffect(OIMRMobEffects.FAST_WALKING) || bob.hasEffect(MobEffects.SPEED),
                "Removing source Speed must clear both fine Fast Walking and replicated coarse Speed on Bob");

            helper.succeed();
        } finally {
            OIMRConfig.setAffinityPercent(prevAffinity);
            OIMRConfig.setSyncEffects(prevSyncEffects);
            cleanupPlayers(server, alice, bob);
        }
    }
}
