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

public class ProgressionAndSocialGameTests {

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
}
