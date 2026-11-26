package net.iakee.oneinventorymanyregrets.hivemind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.iakee.oneinventorymanyregrets.config.OIMRConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Canonical shared state for a single non-overlapping Hive Mind group.
 */
public final class HiveMind {
    public static final int MAIN_INVENTORY_SIZE = Inventory.INVENTORY_SIZE;
    public static final int ENDER_CHEST_SIZE = 27;
    public static final int DEFAULT_AIR_SUPPLY = 300;

    private record InventoryState(
        List<ItemStackWithSlot> items,
        List<ItemStackWithSlot> enderChest,
        Map<EquipmentSlot, ItemStack> equipment
    ) {
        static final InventoryState EMPTY = new InventoryState(List.of(), List.of(), Map.of());
        static final Codec<InventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStackWithSlot.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(InventoryState::items),
            ItemStackWithSlot.CODEC.listOf().optionalFieldOf("ender_chest", List.of()).forGetter(InventoryState::enderChest),
            Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC)
                .optionalFieldOf("equipment", Map.of())
                .forGetter(InventoryState::equipment)
        ).apply(instance, InventoryState::new));
    }

    private record VitalsState(
        float health,
        float absorption,
        int foodLevel,
        float saturationLevel,
        float exhaustionLevel,
        int foodTickTimer,
        int airSupply,
        int fireTicks,
        int freezeTicks,
        List<MobEffectInstance> effects
    ) {
        static final VitalsState DEFAULT = new VitalsState(20.0F, 0.0F, 20, 5.0F, 0.0F, 0, DEFAULT_AIR_SUPPLY, 0, 0, List.of());
        static final Codec<VitalsState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("health", 20.0F).forGetter(VitalsState::health),
            Codec.FLOAT.optionalFieldOf("absorption", 0.0F).forGetter(VitalsState::absorption),
            Codec.INT.optionalFieldOf("food_level", 20).forGetter(VitalsState::foodLevel),
            Codec.FLOAT.optionalFieldOf("saturation_level", 5.0F).forGetter(VitalsState::saturationLevel),
            Codec.FLOAT.optionalFieldOf("exhaustion_level", 0.0F).forGetter(VitalsState::exhaustionLevel),
            Codec.INT.optionalFieldOf("food_tick_timer", 0).forGetter(VitalsState::foodTickTimer),
            Codec.INT.optionalFieldOf("air_supply", DEFAULT_AIR_SUPPLY).forGetter(VitalsState::airSupply),
            Codec.INT.optionalFieldOf("fire_ticks", 0).forGetter(VitalsState::fireTicks),
            Codec.INT.optionalFieldOf("freeze_ticks", 0).forGetter(VitalsState::freezeTicks),
            MobEffectInstance.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(VitalsState::effects)
        ).apply(instance, VitalsState::new));
    }

    private record PlayerRemainderState(
        double damage,
        double heal,
        double absorption,
        double healthBoost,
        double infestedChanceScale,
        Map<Holder<MobEffect>, Double> effectDurations,
        Map<Holder<MobEffect>, Double> effectTriggers
    ) {
        static final Codec<PlayerRemainderState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("damage", 0.0D).forGetter(PlayerRemainderState::damage),
            Codec.DOUBLE.optionalFieldOf("heal", 0.0D).forGetter(PlayerRemainderState::heal),
            Codec.DOUBLE.optionalFieldOf("absorption", 0.0D).forGetter(PlayerRemainderState::absorption),
            Codec.DOUBLE.optionalFieldOf("health_boost", 0.0D).forGetter(PlayerRemainderState::healthBoost),
            Codec.DOUBLE.optionalFieldOf("infested_chance_scale", 1.0D).forGetter(PlayerRemainderState::infestedChanceScale),
            Codec.unboundedMap(MobEffect.CODEC, Codec.DOUBLE)
                .optionalFieldOf("effect_durations", Map.of())
                .forGetter(PlayerRemainderState::effectDurations),
            Codec.unboundedMap(MobEffect.CODEC, Codec.DOUBLE)
                .optionalFieldOf("effect_triggers", Map.of())
                .forGetter(PlayerRemainderState::effectTriggers)
        ).apply(instance, PlayerRemainderState::new));
    }

    private static final class MutablePlayerRemainders {
        double damage;
        double heal;
        double absorption;
        double healthBoost;
        double infestedChanceScale = 1.0D;
        final Map<Holder<MobEffect>, Double> effectDurations = new LinkedHashMap<>();
        final Map<Holder<MobEffect>, Double> effectTriggers = new LinkedHashMap<>();

        boolean isEmpty() {
            return this.damage <= 0.0D
                && this.heal <= 0.0D
                && this.absorption <= 0.0D
                && this.healthBoost <= 0.0D
                && Math.abs(this.infestedChanceScale - 1.0D) <= 1.0E-9D
                && this.effectDurations.isEmpty()
                && this.effectTriggers.isEmpty();
        }
    }

    private record ProgressState(
        int xpLevel,
        int xpTotal,
        float xpProgress,
        double xpRemainder,
        double xpLevelRemainder,
        int enchantmentSeed,
        Optional<ServerPlayer.RespawnConfig> respawn,
        Map<Identifier, AdvancementProgress> advancements,
        List<ResourceKey<Recipe<?>>> knownRecipes,
        List<ResourceKey<Recipe<?>>> highlightRecipes
    ) {
        static final ProgressState DEFAULT = new ProgressState(0, 0, 0.0F, 0.0D, 0.0D, 0, Optional.empty(), Map.of(), List.of(), List.of());
        static final Codec<ProgressState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("xp_level", 0).forGetter(ProgressState::xpLevel),
            Codec.INT.optionalFieldOf("xp_total", 0).forGetter(ProgressState::xpTotal),
            Codec.FLOAT.optionalFieldOf("xp_progress", 0.0F).forGetter(ProgressState::xpProgress),
            Codec.DOUBLE.optionalFieldOf("xp_remainder", 0.0D).forGetter(ProgressState::xpRemainder),
            Codec.DOUBLE.optionalFieldOf("xp_level_remainder", 0.0D).forGetter(ProgressState::xpLevelRemainder),
            Codec.INT.optionalFieldOf("enchantment_seed", 0).forGetter(ProgressState::enchantmentSeed),
            ServerPlayer.RespawnConfig.CODEC.optionalFieldOf("respawn").forGetter(ProgressState::respawn),
            Codec.unboundedMap(Identifier.CODEC, AdvancementProgress.CODEC)
                .optionalFieldOf("advancements", Map.of())
                .forGetter(ProgressState::advancements),
            Recipe.KEY_CODEC.listOf().optionalFieldOf("known_recipes", List.of()).forGetter(ProgressState::knownRecipes),
            Recipe.KEY_CODEC.listOf().optionalFieldOf("highlight_recipes", List.of()).forGetter(ProgressState::highlightRecipes)
        ).apply(instance, ProgressState::new));
    }

    public static final Codec<HiveMind> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(HiveMind::getId),
        Codec.BOOL.optionalFieldOf("initialized", false).forGetter(HiveMind::isInitialized),
        InventoryState.CODEC.optionalFieldOf("inventory_state", InventoryState.EMPTY).forGetter(HiveMind::encodeInventoryState),
        VitalsState.CODEC.optionalFieldOf("vitals_state", VitalsState.DEFAULT).forGetter(HiveMind::encodeVitalsState),
        ProgressState.CODEC.optionalFieldOf("progress_state", ProgressState.DEFAULT).forGetter(HiveMind::encodeProgressState),
        Codec.unboundedMap(Codec.STRING, PlayerRemainderState.CODEC)
            .optionalFieldOf("player_remainders", Map.of())
            .forGetter(HiveMind::encodePlayerRemainders),
        ItemStack.CODEC.listOf().optionalFieldOf("pending_sync_drops", List.of()).forGetter(HiveMind::encodePendingSyncDrops),
        Codec.LONG.optionalFieldOf("last_active_epoch", 0L).forGetter(HiveMind::getLastActiveEpoch),
        Codec.LONG.optionalFieldOf("last_active_game_time", 0L).forGetter(HiveMind::getLastActiveGameTime),
        Codec.BOOL.optionalFieldOf("orphaned", false).forGetter(HiveMind::isOrphaned),
        Codec.LONG.optionalFieldOf("orphaned_epoch", 0L).forGetter(HiveMind::getOrphanedEpoch)
    ).apply(instance, HiveMind::fromCodec));

    private final String id;
    private boolean initialized;
    private final NonNullList<ItemStack> items;
    private final NonNullList<ItemStack> enderChestItems;
    private final EnumMap<EquipmentSlot, ItemStack> equipmentMap;
    private final FoodData foodData;
    private float health;
    private float absorption;
    private int airSupply;
    private int remainingFireTicks;
    private int ticksFrozen;
    private int experienceLevel;
    private int totalExperience;
    private float experienceProgress;
    private int enchantmentSeed;
    private ServerPlayer.@Nullable RespawnConfig respawnConfig;
    private final Map<Holder<MobEffect>, MobEffectInstance> effects;
    private final Map<Identifier, AdvancementProgress> advancements;
    private final Set<ResourceKey<Recipe<?>>> knownRecipes;
    private final Set<ResourceKey<Recipe<?>>> highlightRecipes;
    private final Map<UUID, MutablePlayerRemainders> playerRemainders;
    private final List<ItemStack> pendingSyncDrops;
    private long lastActiveEpoch;
    private long lastActiveGameTime;
    private boolean orphaned;
    private long orphanedEpoch;

    public enum HazardCapCategory {
        LAVA,
        DROWN,
        FIRE,
        FREEZE,
        POISON,
        WITHER,
        CACTUS,
        SWEET_BERRY_BUSH,
        DRAGON_BREATH
    }

    public static final class HazardWindowState {
        private long windowStartTick = Long.MIN_VALUE;
        private float maxPreCooldownDamage = 0.0F;
        private @Nullable UUID primarySourcePlayer = null;
        private final Map<UUID, Float> sharedDamageReceived = new HashMap<>();

        public long getWindowStartTick() {
            return this.windowStartTick;
        }

        public float getMaxPreCooldownDamage() {
            return this.maxPreCooldownDamage;
        }

        public @Nullable UUID getPrimarySourcePlayer() {
            return this.primarySourcePlayer;
        }

        public float getSharedDamageReceived(UUID playerId) {
            return this.sharedDamageReceived.getOrDefault(playerId, 0.0F);
        }

        public void recordSharedDamageReceived(UUID playerId, float amount) {
            if (amount > 0.0F) {
                this.sharedDamageReceived.merge(playerId, amount, Float::sum);
            }
        }

        public void resetWindow(long gameTime, float preCooldownDamage, @Nullable UUID sourcePlayer) {
            this.windowStartTick = gameTime;
            this.maxPreCooldownDamage = preCooldownDamage;
            this.primarySourcePlayer = sourcePlayer;
            this.sharedDamageReceived.clear();
        }

        public void updateMaxPreCooldownDamage(float preCooldownDamage) {
            if (preCooldownDamage > this.maxPreCooldownDamage) {
                this.maxPreCooldownDamage = preCooldownDamage;
            }
        }
    }

    private long lastFoodTickGameTime = -1L;
    private long lastPeacefulRegenTickGameTime = -1L;
    private long lastEnvironmentTickGameTime = -1L;
    private long lastFreezeIncrementGameTime = -1L;
    private final EnumMap<HazardCapCategory, HazardWindowState> hazardWindows = new EnumMap<>(HazardCapCategory.class);
    private boolean anyMemberUnderwater = false;
    private double xpRemainder = 0.0D;
    private double xpLevelRemainder = 0.0D;
    private final Map<Holder<MobEffect>, EffectTickClaim> primaryEffectTickers = new HashMap<>();

    private record EffectTickClaim(long gameTime, @Nullable UUID tickerUuid) {
    }

    public HiveMind(String id) {
        this.id = id;
        this.initialized = false;
        this.items = NonNullList.withSize(MAIN_INVENTORY_SIZE, ItemStack.EMPTY);
        this.enderChestItems = NonNullList.withSize(ENDER_CHEST_SIZE, ItemStack.EMPTY);
        this.equipmentMap = new EnumMap<>(EquipmentSlot.class);
        this.foodData = new FoodData();
        this.health = 20.0F;
        this.absorption = 0.0F;
        this.airSupply = DEFAULT_AIR_SUPPLY;
        this.remainingFireTicks = 0;
        this.ticksFrozen = 0;
        this.experienceLevel = 0;
        this.totalExperience = 0;
        this.experienceProgress = 0.0F;
        this.enchantmentSeed = nonZeroRandomSeed();
        this.respawnConfig = null;
        this.effects = new LinkedHashMap<>();
        this.advancements = new LinkedHashMap<>();
        this.knownRecipes = new LinkedHashSet<>();
        this.highlightRecipes = new LinkedHashSet<>();
        this.playerRemainders = new HashMap<>();
        this.pendingSyncDrops = new ArrayList<>();
    }

    private static int nonZeroRandomSeed() {
        int seed = ThreadLocalRandom.current().nextInt();
        return seed != 0 ? seed : 1;
    }

    private static double sanitizeRemainder(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static HiveMind fromCodec(
        String id,
        boolean initialized,
        InventoryState invState,
        VitalsState vitals,
        ProgressState progress,
        Map<String, PlayerRemainderState> remainders,
        List<ItemStack> pendingSyncDrops,
        long lastActiveEpoch,
        long lastActiveGameTime,
        boolean orphaned,
        long orphanedEpoch
    ) {
        HiveMind hive = new HiveMind(id);
        hive.initialized = initialized;
        hive.lastActiveEpoch = Math.max(0L, lastActiveEpoch);
        hive.lastActiveGameTime = Math.max(0L, lastActiveGameTime);
        hive.orphaned = orphaned;
        hive.orphanedEpoch = orphaned ? Math.max(0L, orphanedEpoch) : 0L;

        for (ItemStackWithSlot entry : invState.items()) {
            if (entry.isValidInContainer(MAIN_INVENTORY_SIZE)) {
                hive.items.set(entry.slot(), entry.stack().copy());
            }
        }

        for (ItemStackWithSlot entry : invState.enderChest()) {
            if (entry.isValidInContainer(ENDER_CHEST_SIZE)) {
                hive.enderChestItems.set(entry.slot(), entry.stack().copy());
            }
        }

        invState.equipment().forEach((slot, stack) -> {
            if (slot != EquipmentSlot.MAINHAND && !stack.isEmpty()) {
                hive.equipmentMap.put(slot, stack.copy());
            }
        });

        hive.health = Math.max(0.0F, vitals.health());
        hive.absorption = Math.max(0.0F, vitals.absorption());
        hive.foodData.setFoodLevel(Mth.clamp(vitals.foodLevel(), 0, 20));
        hive.foodData.setSaturation(Mth.clamp(vitals.saturationLevel(), 0.0F, 20.0F));
        ((HiveFoodDataFieldsAccess) hive.foodData).oimr$setExhaustionLevel(Math.max(0.0F, vitals.exhaustionLevel()));
        ((HiveFoodDataFieldsAccess) hive.foodData).oimr$setTickTimer(Math.max(0, vitals.foodTickTimer()));
        hive.airSupply = Mth.clamp(vitals.airSupply(), -20, DEFAULT_AIR_SUPPLY);
        hive.remainingFireTicks = Math.max(0, vitals.fireTicks());
        hive.ticksFrozen = Mth.clamp(vitals.freezeTicks(), 0, 140);

        for (MobEffectInstance effect : vitals.effects()) {
            hive.effects.put(effect.getEffect(), new MobEffectInstance(effect));
        }

        hive.experienceLevel = Math.max(0, progress.xpLevel());
        hive.totalExperience = Math.max(0, progress.xpTotal());
        hive.experienceProgress = Mth.clamp(progress.xpProgress(), 0.0F, 1.0F);
        hive.xpRemainder = sanitizeRemainder(progress.xpRemainder());
        hive.xpLevelRemainder = sanitizeRemainder(progress.xpLevelRemainder());
        hive.enchantmentSeed = progress.enchantmentSeed() != 0 ? progress.enchantmentSeed() : nonZeroRandomSeed();
        hive.respawnConfig = progress.respawn().orElse(null);

        progress.advancements().forEach((advId, advProg) -> {
            if (advProg.hasProgress()) {
                hive.advancements.put(advId, advProg);
            }
        });
        hive.knownRecipes.addAll(progress.knownRecipes());
        hive.highlightRecipes.addAll(progress.highlightRecipes());

        remainders.forEach((uuidStr, state) -> {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                MutablePlayerRemainders target = new MutablePlayerRemainders();
                target.damage = sanitizeRemainder(state.damage());
                target.heal = sanitizeRemainder(state.heal());
                target.absorption = sanitizeRemainder(state.absorption());
                target.healthBoost = sanitizeRemainder(state.healthBoost());
                target.infestedChanceScale = Double.isFinite(state.infestedChanceScale())
                    ? Mth.clamp(state.infestedChanceScale(), 0.0D, 1.0D)
                    : 1.0D;
                state.effectDurations().forEach((effect, rem) -> {
                    double sanitized = sanitizeRemainder(rem);
                    if (sanitized > 0.0D) {
                        target.effectDurations.put(effect, sanitized);
                    }
                });
                state.effectTriggers().forEach((effect, rem) -> {
                    double sanitized = sanitizeRemainder(rem);
                    if (sanitized > 0.0D) {
                        target.effectTriggers.put(effect, sanitized);
                    }
                });
                if (!target.isEmpty()) {
                    hive.playerRemainders.put(uuid, target);
                }
            } catch (IllegalArgumentException ignored) {
            }
        });

        for (ItemStack pending : pendingSyncDrops) {
            if (!pending.isEmpty()) {
                hive.pendingSyncDrops.add(pending.copy());
            }
        }

        return hive;
    }

    private List<ItemStack> encodePendingSyncDrops() {
        if (this.pendingSyncDrops.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copies = new ArrayList<>(this.pendingSyncDrops.size());
        for (ItemStack stack : this.pendingSyncDrops) {
            if (!stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return copies;
    }

    private InventoryState encodeInventoryState() {
        EnumMap<EquipmentSlot, ItemStack> equip = new EnumMap<>(EquipmentSlot.class);
        this.equipmentMap.forEach((slot, stack) -> {
            if (slot != EquipmentSlot.MAINHAND && !stack.isEmpty()) {
                equip.put(slot, stack.copy());
            }
        });
        return new InventoryState(encodeSlotList(this.items), encodeSlotList(this.enderChestItems), equip);
    }

    private VitalsState encodeVitalsState() {
        List<MobEffectInstance> effectCopies = new ArrayList<>(this.effects.size());
        for (MobEffectInstance effect : this.effects.values()) {
            effectCopies.add(new MobEffectInstance(effect));
        }
        return new VitalsState(
            this.health,
            this.absorption,
            this.foodData.getFoodLevel(),
            this.foodData.getSaturationLevel(),
            ((HiveFoodDataFieldsAccess) this.foodData).oimr$getExhaustionLevel(),
            ((HiveFoodDataFieldsAccess) this.foodData).oimr$getTickTimer(),
            this.airSupply,
            this.remainingFireTicks,
            this.ticksFrozen,
            effectCopies
        );
    }

    private ProgressState encodeProgressState() {
        Map<Identifier, AdvancementProgress> advMap = new LinkedHashMap<>();
        this.advancements.forEach((advId, prog) -> {
            if (prog.hasProgress()) {
                advMap.put(advId, prog);
            }
        });
        return new ProgressState(
            this.experienceLevel,
            this.totalExperience,
            this.experienceProgress,
            this.xpRemainder,
            this.xpLevelRemainder,
            this.enchantmentSeed,
            Optional.ofNullable(this.respawnConfig),
            advMap,
            List.copyOf(this.knownRecipes),
            List.copyOf(this.highlightRecipes)
        );
    }

    private Map<String, PlayerRemainderState> encodePlayerRemainders() {
        Map<String, PlayerRemainderState> encoded = new LinkedHashMap<>();
        this.playerRemainders.forEach((uuid, rem) -> {
            if (!rem.isEmpty()) {
                encoded.put(uuid.toString(), new PlayerRemainderState(
                    rem.damage,
                    rem.heal,
                    rem.absorption,
                    rem.healthBoost,
                    rem.infestedChanceScale,
                    Map.copyOf(rem.effectDurations),
                    Map.copyOf(rem.effectTriggers)
                ));
            }
        });
        return encoded;
    }

    private static List<ItemStackWithSlot> encodeSlotList(NonNullList<ItemStack> slotList) {
        List<ItemStackWithSlot> list = new ArrayList<>();
        for (int i = 0; i < slotList.size(); i++) {
            ItemStack stack = slotList.get(i);
            if (!stack.isEmpty()) {
                list.add(new ItemStackWithSlot(i, stack.copy()));
            }
        }
        return list;
    }

    public String getId() {
        return this.id;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public void markInitializedEmpty() {
        this.initialized = true;
    }

    public long getLastActiveEpoch() {
        return this.lastActiveEpoch;
    }

    public void setLastActiveEpoch(long lastActiveEpoch) {
        this.lastActiveEpoch = Math.max(0L, lastActiveEpoch);
    }

    public long getLastActiveGameTime() {
        return this.lastActiveGameTime;
    }

    public void setLastActiveGameTime(long lastActiveGameTime) {
        this.lastActiveGameTime = Math.max(0L, lastActiveGameTime);
    }

    public boolean isOrphaned() {
        return this.orphaned;
    }

    public long getOrphanedEpoch() {
        return this.orphanedEpoch;
    }

    public void markOrphaned(long epochSeconds) {
        this.orphaned = true;
        if (this.orphanedEpoch <= 0L) {
            this.orphanedEpoch = Math.max(0L, epochSeconds);
        }
    }

    public void clearOrphaned() {
        this.orphaned = false;
        this.orphanedEpoch = 0L;
    }

    public void touchActivity(long epochSeconds, long gameTime) {
        this.lastActiveEpoch = Math.max(0L, epochSeconds);
        this.lastActiveGameTime = Math.max(0L, gameTime);
        if (this.orphaned) {
            clearOrphaned();
        }
    }

    public void initializeFrom(ServerPlayer player, PersonalInventoryState personal) {
        NonNullList<ItemStack> playerItems = ((HiveInventoryAccess) player.getInventory()).oimr$getItems();
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            ItemStack copy = playerItems.get(i).copy();
            if (OIMRConfig.isHotbarSlotSynced(i)) {
                this.items.set(i, copy);
            } else if (i < PersonalInventoryState.HOTBAR_SIZE) {
                personal.getHotbarItems().set(i, copy);
            }
        }

        NonNullList<ItemStack> playerEnderChest = ((HiveInventoryAccess) player.getEnderChestInventory()).oimr$getItems();
        for (int i = 0; i < ENDER_CHEST_SIZE; i++) {
            this.enderChestItems.set(i, playerEnderChest.get(i).copy());
        }

        this.equipmentMap.clear();
        EnumMap<EquipmentSlot, ItemStack> playerEquip =
            ((HiveEquipmentAccess) ((HiveLivingEntityAccess) player).oimr$getEquipment()).oimr$getEquipmentItems();
        playerEquip.forEach((slot, stack) -> {
            if (slot != EquipmentSlot.MAINHAND && !stack.isEmpty()) {
                if (OIMRConfig.isEquipmentSlotSynced(slot)) {
                    this.equipmentMap.put(slot, stack.copy());
                } else {
                    personal.getEquipmentMap().put(slot, stack.copy());
                }
            }
        });

        this.health = player.getHealth() > 0.0F ? player.getHealth() : player.getMaxHealth();
        this.absorption = Math.max(0.0F, player.getAbsorptionAmount());

        FoodData playerFood = player.getFoodData();
        this.foodData.setFoodLevel(playerFood.getFoodLevel());
        this.foodData.setSaturation(playerFood.getSaturationLevel());
        ((HiveFoodDataFieldsAccess) this.foodData)
            .oimr$setExhaustionLevel(((HiveFoodDataFieldsAccess) playerFood).oimr$getExhaustionLevel());
        ((HiveFoodDataFieldsAccess) this.foodData)
            .oimr$setTickTimer(((HiveFoodDataFieldsAccess) playerFood).oimr$getTickTimer());

        this.airSupply = player.getAirSupply();
        this.remainingFireTicks = Math.max(0, player.getRemainingFireTicks());
        this.ticksFrozen = Math.max(0, player.getTicksFrozen());

        this.experienceLevel = Math.max(0, player.experienceLevel);
        this.totalExperience = Math.max(0, player.totalExperience);
        this.experienceProgress = Mth.clamp(player.experienceProgress, 0.0F, 1.0F);
        int playerSeed = ((HivePlayerProgressAccess) player).oimr$getEnchantmentSeed();
        this.enchantmentSeed = playerSeed != 0 ? playerSeed : nonZeroRandomSeed();
        this.respawnConfig = player.getRespawnConfig();

        this.effects.clear();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            this.effects.put(effect.getEffect(), new MobEffectInstance(effect));
        }

        this.advancements.clear();
        ((HivePlayerAdvancementsAccess) player.getAdvancements()).oimr$exportProgress()
            .forEach((advId, prog) -> {
                if (prog.hasProgress()) {
                    this.advancements.put(advId, prog);
                }
            });

        this.knownRecipes.clear();
        this.knownRecipes.addAll(((HiveServerRecipeBookAccess) player.getRecipeBook()).oimr$exportKnown());
        this.highlightRecipes.clear();
        this.highlightRecipes.addAll(((HiveServerRecipeBookAccess) player.getRecipeBook()).oimr$exportHighlight());

        this.initialized = true;
    }

    public void resetVitalsForRespawn(float maxHealth) {
        this.health = maxHealth > 0.0F ? maxHealth : 20.0F;
        this.absorption = 0.0F;
        this.foodData.setFoodLevel(20);
        this.foodData.setSaturation(5.0F);
        ((HiveFoodDataFieldsAccess) this.foodData).oimr$setExhaustionLevel(0.0F);
        ((HiveFoodDataFieldsAccess) this.foodData).oimr$setTickTimer(0);
        this.airSupply = DEFAULT_AIR_SUPPLY;
        this.remainingFireTicks = 0;
        this.ticksFrozen = 0;
        this.effects.clear();
        this.playerRemainders.clear();
        this.hazardWindows.clear();
    }

    public void resetExperience() {
        this.experienceLevel = 0;
        this.totalExperience = 0;
        this.experienceProgress = 0.0F;
        this.xpRemainder = 0.0D;
        this.xpLevelRemainder = 0.0D;
    }

    public double getXpRemainder() {
        return this.xpRemainder;
    }

    public void setXpRemainder(double xpRemainder) {
        this.xpRemainder = sanitizeRemainder(xpRemainder);
    }

    public double getXpLevelRemainder() {
        return this.xpLevelRemainder;
    }

    public void setXpLevelRemainder(double xpLevelRemainder) {
        this.xpLevelRemainder = sanitizeRemainder(xpLevelRemainder);
    }

    public double getDamageRemainder(UUID playerId) {
        MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
        return rem != null ? rem.damage : 0.0D;
    }

    public void setDamageRemainder(UUID playerId, double value) {
        updatePlayerRemainder(playerId, value, (rem, v) -> rem.damage = v);
    }

    public double getHealRemainder(UUID playerId) {
        MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
        return rem != null ? rem.heal : 0.0D;
    }

    public void setHealRemainder(UUID playerId, double value) {
        updatePlayerRemainder(playerId, value, (rem, v) -> rem.heal = v);
    }

    public double getAbsorptionRemainder(UUID playerId) {
        MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
        return rem != null ? rem.absorption : 0.0D;
    }

    public void setAbsorptionRemainder(UUID playerId, double value) {
        updatePlayerRemainder(playerId, value, (rem, v) -> rem.absorption = v);
    }

    public double getHealthBoostRemainder(UUID playerId) {
        MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
        return rem != null ? rem.healthBoost : 0.0D;
    }

    public void setHealthBoostRemainder(UUID playerId, double value) {
        updatePlayerRemainder(playerId, value, (rem, v) -> rem.healthBoost = v);
    }

    public double getInfestedChanceScale(UUID playerId) {
        MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
        return rem != null ? rem.infestedChanceScale : 1.0D;
    }

    public void setInfestedChanceScale(UUID playerId, double scale) {
        double sanitized = Double.isFinite(scale) ? Mth.clamp(scale, 0.0D, 1.0D) : 1.0D;
        if (Math.abs(sanitized - 1.0D) <= 1.0E-9D) {
            MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
            if (rem != null) {
                rem.infestedChanceScale = 1.0D;
                if (rem.isEmpty()) {
                    this.playerRemainders.remove(playerId);
                }
            }
            return;
        }
        this.playerRemainders.computeIfAbsent(playerId, ignored -> new MutablePlayerRemainders()).infestedChanceScale = sanitized;
    }

    public double getEffectDurationRemainder(UUID playerId, Holder<MobEffect> effect) {
        MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
        return rem != null ? rem.effectDurations.getOrDefault(effect, 0.0D) : 0.0D;
    }

    public void setEffectDurationRemainder(UUID playerId, Holder<MobEffect> effect, double value) {
        updateEffectMapRemainder(playerId, effect, value, rem -> rem.effectDurations);
    }

    public double getEffectTriggerRemainder(UUID playerId, Holder<MobEffect> effect) {
        MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
        return rem != null ? rem.effectTriggers.getOrDefault(effect, 0.0D) : 0.0D;
    }

    public void setEffectTriggerRemainder(UUID playerId, Holder<MobEffect> effect, double value) {
        updateEffectMapRemainder(playerId, effect, value, rem -> rem.effectTriggers);
    }

    private void updatePlayerRemainder(
        UUID playerId,
        double value,
        java.util.function.ObjDoubleConsumer<MutablePlayerRemainders> setter
    ) {
        double sanitized = sanitizeRemainder(value);
        if (sanitized <= 0.0D) {
            MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
            if (rem != null) {
                setter.accept(rem, 0.0D);
                if (rem.isEmpty()) {
                    this.playerRemainders.remove(playerId);
                }
            }
            return;
        }
        MutablePlayerRemainders rem = this.playerRemainders.computeIfAbsent(playerId, ignored -> new MutablePlayerRemainders());
        setter.accept(rem, sanitized);
    }

    private void updateEffectMapRemainder(
        UUID playerId,
        Holder<MobEffect> effect,
        double value,
        java.util.function.Function<MutablePlayerRemainders, Map<Holder<MobEffect>, Double>> mapGetter
    ) {
        double sanitized = sanitizeRemainder(value);
        if (sanitized <= 0.0D) {
            MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
            if (rem != null) {
                mapGetter.apply(rem).remove(effect);
                if (rem.isEmpty()) {
                    this.playerRemainders.remove(playerId);
                }
            }
            return;
        }
        MutablePlayerRemainders rem = this.playerRemainders.computeIfAbsent(playerId, ignored -> new MutablePlayerRemainders());
        mapGetter.apply(rem).put(effect, sanitized);
    }

    public void clearPlayerVitalRemainders(UUID playerId) {
        MutablePlayerRemainders rem = this.playerRemainders.get(playerId);
        if (rem != null) {
            rem.damage = 0.0D;
            rem.heal = 0.0D;
            rem.absorption = 0.0D;
            rem.healthBoost = 0.0D;
            rem.infestedChanceScale = 1.0D;
            if (rem.isEmpty()) {
                this.playerRemainders.remove(playerId);
            }
        }
    }

    public void clearPlayerRemainders(UUID playerId) {
        this.playerRemainders.remove(playerId);
    }

    public void clearAllPlayerRemainders() {
        this.playerRemainders.clear();
    }

    public void clearAllEffectRemainders() {
        Iterator<Map.Entry<UUID, MutablePlayerRemainders>> iterator = this.playerRemainders.entrySet().iterator();
        while (iterator.hasNext()) {
            MutablePlayerRemainders rem = iterator.next().getValue();
            rem.effectDurations.clear();
            rem.effectTriggers.clear();
            if (rem.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public void clearPrimaryEffectTickers() {
        this.primaryEffectTickers.clear();
    }

    public void addPendingSyncDrop(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.pendingSyncDrops.add(stack.copy());
        }
    }

    public List<ItemStack> drainPendingSyncDrops() {
        if (this.pendingSyncDrops.isEmpty()) {
            return List.of();
        }
        List<ItemStack> drained = new ArrayList<>(this.pendingSyncDrops);
        this.pendingSyncDrops.clear();
        return drained;
    }

    public void setExperience(int level, int total, float progress) {
        this.experienceLevel = Math.max(0, level);
        this.totalExperience = Math.max(0, total);
        this.experienceProgress = Mth.clamp(progress, 0.0F, 1.0F);
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    public NonNullList<ItemStack> getEnderChestItems() {
        return this.enderChestItems;
    }

    public EnumMap<EquipmentSlot, ItemStack> getEquipmentMap() {
        return this.equipmentMap;
    }

    public FoodData getFoodData() {
        return this.foodData;
    }

    public float getHealth() {
        return this.health;
    }

    public void setHealth(float health) {
        this.health = Math.max(0.0F, health);
    }

    public float getAbsorption() {
        return this.absorption;
    }

    public void setAbsorption(float absorption) {
        this.absorption = Math.max(0.0F, absorption);
    }

    public int getAirSupply() {
        return this.airSupply;
    }

    public void setAirSupply(int airSupply) {
        this.airSupply = airSupply;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void setRemainingFireTicks(int remainingFireTicks) {
        this.remainingFireTicks = Math.max(0, remainingFireTicks);
    }

    public int getTicksFrozen() {
        return this.ticksFrozen;
    }

    public void setTicksFrozen(int ticksFrozen) {
        this.ticksFrozen = Math.max(0, ticksFrozen);
    }

    public int getExperienceLevel() {
        return this.experienceLevel;
    }

    public int getTotalExperience() {
        return this.totalExperience;
    }

    public float getExperienceProgress() {
        return this.experienceProgress;
    }

    public int getEnchantmentSeed() {
        return this.enchantmentSeed;
    }

    public void setEnchantmentSeed(int enchantmentSeed) {
        this.enchantmentSeed = enchantmentSeed != 0 ? enchantmentSeed : nonZeroRandomSeed();
    }

    public int advanceEnchantmentSeed() {
        this.enchantmentSeed = nextDeterministicSeed(this.enchantmentSeed);
        return this.enchantmentSeed;
    }

    private static int nextDeterministicSeed(int current) {
        // SplitMix32 step for deterministic non-zero enchantment offer progression across the hive.
        int z = current + 0x9E3779B9;
        z = (z ^ (z >>> 16)) * 0x85EBCA6B;
        z = (z ^ (z >>> 13)) * 0xC2B2AE35;
        z = z ^ (z >>> 16);
        return z != 0 ? z : 1;
    }

    public ServerPlayer.@Nullable RespawnConfig getRespawnConfig() {
        return this.respawnConfig;
    }

    public void setRespawnConfig(ServerPlayer.@Nullable RespawnConfig respawnConfig) {
        this.respawnConfig = respawnConfig;
    }

    public Map<Holder<MobEffect>, MobEffectInstance> getEffects() {
        return this.effects;
    }

    public void putEffect(MobEffectInstance effect) {
        this.effects.put(effect.getEffect(), new MobEffectInstance(effect));
    }

    public void removeEffect(Holder<MobEffect> effectHolder) {
        this.effects.remove(effectHolder);
    }

    public void snapshotEffectsFrom(Collection<MobEffectInstance> activeEffects) {
        this.effects.clear();
        for (MobEffectInstance effect : activeEffects) {
            this.effects.put(effect.getEffect(), new MobEffectInstance(effect));
        }
    }

    public Map<Identifier, AdvancementProgress> getAdvancements() {
        return this.advancements;
    }

    public void grantAdvancementCriterion(AdvancementHolder holder, String criterion) {
        AdvancementProgress prog = this.advancements.computeIfAbsent(holder.id(), ignored -> {
            AdvancementProgress fresh = new AdvancementProgress();
            fresh.update(holder.value().requirements());
            return fresh;
        });
        prog.update(holder.value().requirements());
        prog.grantProgress(criterion);
    }

    public void revokeAdvancementCriterion(AdvancementHolder holder, String criterion) {
        AdvancementProgress prog = this.advancements.get(holder.id());
        if (prog != null) {
            prog.update(holder.value().requirements());
            prog.revokeProgress(criterion);
            if (!prog.hasProgress()) {
                this.advancements.remove(holder.id());
            }
        }
    }

    public Set<ResourceKey<Recipe<?>>> getKnownRecipes() {
        return this.knownRecipes;
    }

    public Set<ResourceKey<Recipe<?>>> getHighlightRecipes() {
        return this.highlightRecipes;
    }

    public void addRecipes(Collection<RecipeHolder<?>> recipes) {
        for (RecipeHolder<?> recipe : recipes) {
            if (!recipe.value().isSpecial()) {
                this.knownRecipes.add(recipe.id());
                this.highlightRecipes.add(recipe.id());
            }
        }
    }

    public void removeRecipes(Collection<RecipeHolder<?>> recipes) {
        for (RecipeHolder<?> recipe : recipes) {
            this.knownRecipes.remove(recipe.id());
            this.highlightRecipes.remove(recipe.id());
        }
    }

    public void resetTickClaims() {
        this.lastFoodTickGameTime = -1L;
        this.lastPeacefulRegenTickGameTime = -1L;
        this.lastEnvironmentTickGameTime = -1L;
        this.lastFreezeIncrementGameTime = -1L;
        this.hazardWindows.clear();
        this.anyMemberUnderwater = false;
        this.primaryEffectTickers.clear();
    }

    public HazardWindowState getOrCreateHazardWindow(HazardCapCategory category) {
        return this.hazardWindows.computeIfAbsent(category, ignored -> new HazardWindowState());
    }

    public @Nullable HazardWindowState getHazardWindowIfPresent(HazardCapCategory category) {
        return this.hazardWindows.get(category);
    }

    public void clearHazardWindows() {
        this.hazardWindows.clear();
    }

    public boolean tryClaimFoodTick(long gameTime) {
        if (this.lastFoodTickGameTime == gameTime) {
            return false;
        }
        this.lastFoodTickGameTime = gameTime;
        return true;
    }

    public boolean tryClaimPeacefulRegenTick(long gameTime) {
        if (this.lastPeacefulRegenTickGameTime == gameTime) {
            return false;
        }
        this.lastPeacefulRegenTickGameTime = gameTime;
        return true;
    }

    public boolean isAnyMemberUnderwater() {
        return this.anyMemberUnderwater;
    }

    public void setAnyMemberUnderwater(boolean anyMemberUnderwater) {
        this.anyMemberUnderwater = anyMemberUnderwater;
    }

    public boolean tryClaimEnvironmentTick(long gameTime) {
        if (this.lastEnvironmentTickGameTime == gameTime) {
            return false;
        }
        this.lastEnvironmentTickGameTime = gameTime;
        return true;
    }

    public boolean tryClaimFreezeIncrement(long gameTime) {
        if (this.lastFreezeIncrementGameTime == gameTime) {
            return false;
        }
        this.lastFreezeIncrementGameTime = gameTime;
        return true;
    }

    public boolean isPrimaryEffectTicker(
        ServerPlayer player,
        Holder<MobEffect> effectHolder,
        long gameTime,
        List<ServerPlayer> eligibleMembers
    ) {
        EffectTickClaim claim = this.primaryEffectTickers.get(effectHolder);
        boolean chosenStillEligible = false;
        if (claim != null && claim.gameTime() == gameTime && claim.tickerUuid() != null) {
            for (ServerPlayer member : eligibleMembers) {
                if (member.getUUID().equals(claim.tickerUuid())) {
                    chosenStillEligible = true;
                    break;
                }
            }
        }
        if (!chosenStillEligible) {
            UUID chosen = null;
            for (ServerPlayer member : eligibleMembers) {
                if (chosen == null || member.getUUID().compareTo(chosen) < 0) {
                    chosen = member.getUUID();
                }
            }
            claim = new EffectTickClaim(gameTime, chosen);
            this.primaryEffectTickers.put(effectHolder, claim);
        }
        return player.getUUID().equals(claim.tickerUuid());
    }
}
