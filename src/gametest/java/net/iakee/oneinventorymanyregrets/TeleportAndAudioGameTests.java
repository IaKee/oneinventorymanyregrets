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

public class TeleportAndAudioGameTests {

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
            OIMRConfig.setSharedMobSoundFilter(OIMRConfig.MobSoundFilter.HOSTILE);
            OIMRConfig.setSharedMobSoundVolumePercent(35);
            OIMRConfig.load();
            helper.assertFalse(OIMRConfig.isSharedEnderPearlEnabled()
                    || OIMRConfig.isSharedPortalsEnabled()
                    || OIMRConfig.isUnderwaterMuffleEnabled()
                    || OIMRConfig.isSharedMobSoundsEnabled(),
                "New config toggles must persist false across OIMRConfig.load()");
            helper.assertTrue(OIMRConfig.getSharedMobSoundFilter() == OIMRConfig.MobSoundFilter.HOSTILE
                    && OIMRConfig.getSharedMobSoundVolumePercent() == 35,
                "Mob sound filter and volume must persist across OIMRConfig.load()");

            helper.succeed();
        } finally {
            level.setBlock(waterPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(eyeWaterPos, Blocks.AIR.defaultBlockState(), 3);
            OIMRConfig.setSharedEnderPearl(prevPearl);
            OIMRConfig.setSharedPortals(prevPortals);
            OIMRConfig.setUnderwaterMuffle(prevUnderwater);
            OIMRConfig.setSharedMobSounds(prevMobSounds);
            OIMRConfig.setSharedMobSoundFilter(OIMRConfig.MobSoundFilter.ALL);
            OIMRConfig.setSharedMobSoundVolumePercent(OIMRConfig.MAX_SHARED_MOB_SOUND_VOLUME);
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob);
        }
    }

    @GameTest
    public void testReplicatedMobSoundsOrientationDistanceProbabilityAndAffinity(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        boolean prevMobSounds = OIMRConfig.isSharedMobSoundsEnabled();
        OIMRConfig.MobSoundFilter prevFilter = OIMRConfig.getSharedMobSoundFilter();
        int prevVolumePercent = OIMRConfig.getSharedMobSoundVolumePercent();
        int prevAffinity = OIMRConfig.getAffinityPercent();
        TestPlayerHandle aliceHandle = spawnTestPlayerWithChannel(server, level, "HiveAlice42");
        TestPlayerHandle bobHandle = spawnTestPlayerWithChannel(server, level, "HiveBob42");
        TestPlayerHandle carolHandle = spawnTestPlayerWithChannel(server, level, "HiveCarol42");
        ServerPlayer alice = aliceHandle.player();
        ServerPlayer bob = bobHandle.player();
        ServerPlayer carol = carolHandle.player();
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        try {
            helper.assertTrue(zombie != null && cow != null, "Zombie and Cow must spawn");
            OIMRConfig.setSharedMobSounds(true);
            OIMRConfig.setSharedMobSoundFilter(OIMRConfig.MobSoundFilter.ALL);
            OIMRConfig.setSharedMobSoundVolumePercent(100);
            OIMRConfig.setAffinityPercent(50);

            // Alice and Carol are both near the Zombie and Cow; Bob is 200 blocks away.
            // A single sound must NEVER be duplicated to Bob across the two nearby listeners.
            alice.teleportTo(0.0, 64.0, 0.0);
            carol.teleportTo(2.0, 64.0, 0.0);
            bob.teleportTo(200.0, 64.0, 0.0);
            zombie.setPos(4.0, 64.0, 0.0);
            cow.setPos(4.0, 64.0, 0.0);
            level.addFreshEntity(zombie);
            level.addFreshEntity(cow);

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

            // Pre-affinity volume reduction (50% volume * 50% affinity = 0.25) and neutral channel preservation
            OIMRConfig.setSharedMobSoundVolumePercent(50);
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 40; i++) {
                zombie.playAmbientSound();
                cow.playAmbientSound();
            }
            List<ClientboundSoundPacket> hostilePackets = new java.util.ArrayList<>();
            List<ClientboundSoundPacket> neutralPackets = new java.util.ArrayList<>();
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp) {
                    if (sp.getSource() == SoundSource.HOSTILE) {
                        hostilePackets.add(sp);
                    } else if (sp.getSource() == SoundSource.NEUTRAL) {
                        neutralPackets.add(sp);
                    }
                }
            }
            helper.assertTrue(!hostilePackets.isEmpty() && !neutralPackets.isEmpty(),
                "ALL filter must propagate both HOSTILE and NEUTRAL mob sounds on their original channels");
            helper.assertTrue(Math.abs(hostilePackets.getFirst().getVolume() - 0.25F) < 0.05F,
                "Pre-affinity 50% volume * 50% affinity must produce 0.25 volume, got " + hostilePackets.getFirst().getVolume());
            helper.assertTrue(Math.abs(neutralPackets.getFirst().getVolume() - 0.1F) < 0.02F,
                "Neutral cow ambient sound (base 0.4) with 50% volume * 50% affinity must produce 0.1 volume, got "
                    + neutralPackets.getFirst().getVolume());

            // HOSTILE-only filter blocks NEUTRAL and keeps HOSTILE on HOSTILE channel
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings sharedMobSoundFilter hostile");
            helper.assertTrue(OIMRConfig.getSharedMobSoundFilter() == OIMRConfig.MobSoundFilter.HOSTILE,
                "Command /oimr settings sharedMobSoundFilter hostile must update config");
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 40; i++) {
                zombie.playAmbientSound();
                cow.playAmbientSound();
            }
            int hostileOnlyHostileCount = 0;
            int hostileOnlyNeutralCount = 0;
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp) {
                    if (sp.getSource() == SoundSource.HOSTILE) {
                        hostileOnlyHostileCount++;
                    } else if (sp.getSource() == SoundSource.NEUTRAL) {
                        hostileOnlyNeutralCount++;
                    }
                }
            }
            helper.assertTrue(hostileOnlyHostileCount > 0 && hostileOnlyNeutralCount == 0,
                "HOSTILE filter must propagate only HOSTILE sounds (hostile=" + hostileOnlyHostileCount + ", neutral=" + hostileOnlyNeutralCount + ")");

            // NEUTRAL-only filter blocks HOSTILE and keeps NEUTRAL on NEUTRAL channel
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings sharedMobSoundFilter neutral");
            helper.assertTrue(OIMRConfig.getSharedMobSoundFilter() == OIMRConfig.MobSoundFilter.NEUTRAL,
                "Command /oimr settings sharedMobSoundFilter neutral must update config");
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 40; i++) {
                zombie.playAmbientSound();
                cow.playAmbientSound();
            }
            int neutralOnlyHostileCount = 0;
            int neutralOnlyNeutralCount = 0;
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp) {
                    if (sp.getSource() == SoundSource.HOSTILE) {
                        neutralOnlyHostileCount++;
                    } else if (sp.getSource() == SoundSource.NEUTRAL) {
                        neutralOnlyNeutralCount++;
                    }
                }
            }
            helper.assertTrue(neutralOnlyHostileCount == 0 && neutralOnlyNeutralCount > 0,
                "NEUTRAL filter must propagate only NEUTRAL sounds (hostile=" + neutralOnlyHostileCount + ", neutral=" + neutralOnlyNeutralCount + ")");

            // NONE filter and 0% volume command each block all replicated sounds
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings sharedMobSoundFilter none");
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 30; i++) {
                zombie.playAmbientSound();
                cow.playAmbientSound();
            }
            int noneFilterCount = 0;
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp && (sp.getSource() == SoundSource.HOSTILE || sp.getSource() == SoundSource.NEUTRAL)) {
                    noneFilterCount++;
                }
            }
            helper.assertTrue(noneFilterCount == 0,
                "NONE filter must propagate 0 mob sounds (got " + noneFilterCount + ")");

            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings sharedMobSoundFilter all");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "oimr settings sharedMobSoundVolume 0");
            helper.assertTrue(OIMRConfig.getSharedMobSoundVolumePercent() == 0,
                "Command /oimr settings sharedMobSoundVolume 0 must update config");
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 30; i++) {
                zombie.playAmbientSound();
                cow.playAmbientSound();
            }
            int zeroVolumeCount = 0;
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp && (sp.getSource() == SoundSource.HOSTILE || sp.getSource() == SoundSource.NEUTRAL)) {
                    zeroVolumeCount++;
                }
            }
            helper.assertTrue(zeroVolumeCount == 0,
                "0% replicated sound volume must propagate 0 mob sounds (got " + zeroVolumeCount + ")");
            OIMRConfig.setSharedMobSoundVolumePercent(100);

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

            // Toggle OFF -> 0 replicated sounds even when filter is ALL and volume is 100%
            OIMRConfig.setSharedMobSounds(false);
            bobHandle.channel().outboundMessages().clear();
            for (int i = 0; i < 20; i++) {
                zombie.playAmbientSound();
                cow.playAmbientSound();
            }
            int offCount = 0;
            while ((msg = bobHandle.channel().outboundMessages().poll()) != null) {
                if (msg instanceof ClientboundSoundPacket sp && (sp.getSource() == SoundSource.HOSTILE || sp.getSource() == SoundSource.NEUTRAL)) {
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
            if (cow != null) {
                cow.discard();
            }
            OIMRConfig.setSharedMobSounds(prevMobSounds);
            OIMRConfig.setSharedMobSoundFilter(prevFilter);
            OIMRConfig.setSharedMobSoundVolumePercent(prevVolumePercent);
            OIMRConfig.setAffinityPercent(prevAffinity);
            cleanupPlayers(server, alice, bob, carol);
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
}
