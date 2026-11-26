package net.iakee.oneinventorymanyregrets.effect;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Custom replicated {@link MobEffect} counterparts used when partial Hive Mind affinity
 * ({@code 0% < affinity < 100%}) is active:
 * <ul>
 *   <li><b>Mechanical / binary counterparts (10):</b> replace all-or-nothing vanilla mechanics
 *       with affinity-scaled behavior over full source duration.</li>
 *   <li><b>Fine-grained numeric counterparts (10, 5:1 subdivision):</b> each vanilla level equals
 *       {@value #FINE_SUBDIVISIONS} fine levels, enabling coarse+fine interpolation between integer
 *       vanilla levels (e.g. {@code Speed I + Fast Walking II} for {@code 1.4x} Speed).</li>
 *   <li><b>Third-party / unmapped effects:</b> fallback cleanly to deterministic duration scaling
 *       ({@code duration * affinityFactor}) with remainder accumulation and full amplifier.</li>
 * </ul>
 */
public final class OIMRMobEffects {
    public static final int FINE_SUBDIVISIONS = 5;

    // 10 mechanical / binary counterparts
    public static final Holder<MobEffect> TRANSLUCENCY = register("translucency", MobEffectCategory.BENEFICIAL, 0xD8E8F8);
    public static final Holder<MobEffect> TWILIGHT_VISION = register("twilight_vision", MobEffectCategory.BENEFICIAL, 0x5577CC);
    public static final Holder<MobEffect> AQUATIC_RESPIRATION = register("aquatic_respiration", MobEffectCategory.BENEFICIAL, 0x3CA8B8);
    public static final Holder<MobEffect> CUSHIONED_FALL = register("cushioned_fall", MobEffectCategory.BENEFICIAL, 0xEDE8C8);
    public static final Holder<MobEffect> FAINT_GLOW = register("faint_glow", MobEffectCategory.NEUTRAL, 0xC8B860);
    public static final Holder<MobEffect> EMBER_WARD = register("ember_ward", MobEffectCategory.BENEFICIAL, 0xD97B38);
    public static final Holder<MobEffect> PARTIAL_LEVITATION = register("partial_levitation", MobEffectCategory.HARMFUL, 0xB8A8D8);
    public static final Holder<MobEffect> FLUID_GRACE = register("fluid_grace", MobEffectCategory.BENEFICIAL, 0x6BB5D6);
    public static final Holder<MobEffect> CONDUIT_ECHO = register("conduit_echo", MobEffectCategory.BENEFICIAL, 0x3BC4C4);
    public static final Holder<MobEffect> NAUTILUS_ECHO = register("nautilus_echo", MobEffectCategory.BENEFICIAL, 0x2F889E);

    // 10 fine-grained numeric counterparts (5 fine levels == 1 vanilla coarse level)
    public static final Holder<MobEffect> FAST_WALKING = register(
        "fast_walking",
        new ReplicatedMobEffect(MobEffectCategory.BENEFICIAL, 0x7CAFC6)
            .addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.fast_walking"),
                0.04D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
    );
    public static final Holder<MobEffect> HEAVY_STEP = register(
        "heavy_step",
        new ReplicatedMobEffect(MobEffectCategory.HARMFUL, 0x6D7B8D)
            .addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.heavy_step"),
                -0.03D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
    );
    public static final Holder<MobEffect> NIMBLE_HANDS = register(
        "nimble_hands",
        new ReplicatedMobEffect(MobEffectCategory.BENEFICIAL, 0xE6CF68)
            .addAttributeModifier(
                Attributes.ATTACK_SPEED,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.nimble_hands"),
                0.02D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
    );
    public static final Holder<MobEffect> WEARY_HANDS = register(
        "weary_hands",
        new ReplicatedMobEffect(MobEffectCategory.HARMFUL, 0x5B5748)
            .addAttributeModifier(
                Attributes.ATTACK_SPEED,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.weary_hands"),
                -0.02D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
    );
    public static final Holder<MobEffect> MIGHT_SURGE = register(
        "might_surge",
        new ReplicatedMobEffect(MobEffectCategory.BENEFICIAL, 0xE67864)
            .addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.might_surge"),
                0.6D,
                AttributeModifier.Operation.ADD_VALUE
            )
    );
    public static final Holder<MobEffect> FEEBLE_GRIP = register(
        "feeble_grip",
        new ReplicatedMobEffect(MobEffectCategory.HARMFUL, 0x6B6863)
            .addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.feeble_grip"),
                -0.8D,
                AttributeModifier.Operation.ADD_VALUE
            )
    );
    public static final Holder<MobEffect> SPRING_STEP = register(
        "spring_step",
        new ReplicatedMobEffect(MobEffectCategory.BENEFICIAL, 0xA8E078)
            .addAttributeModifier(
                Attributes.SAFE_FALL_DISTANCE,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.spring_step"),
                0.2D,
                AttributeModifier.Operation.ADD_VALUE
            )
    );
    public static final Holder<MobEffect> WARDING_AURA = register("warding_aura", MobEffectCategory.BENEFICIAL, 0x9E7B99);
    public static final Holder<MobEffect> MINOR_FORTUNE = register(
        "minor_fortune",
        new ReplicatedMobEffect(MobEffectCategory.BENEFICIAL, 0x6EB848)
            .addAttributeModifier(
                Attributes.LUCK,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.minor_fortune"),
                0.2D,
                AttributeModifier.Operation.ADD_VALUE
            )
    );
    public static final Holder<MobEffect> MINOR_JINX = register(
        "minor_jinx",
        new ReplicatedMobEffect(MobEffectCategory.HARMFUL, 0xB89844)
            .addAttributeModifier(
                Attributes.LUCK,
                Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "effect.minor_jinx"),
                -0.2D,
                AttributeModifier.Operation.ADD_VALUE
            )
    );

    private static final Map<Holder<MobEffect>, Holder<MobEffect>> MECHANICAL_COUNTERPARTS = new LinkedHashMap<>();
    private static final Map<Holder<MobEffect>, Holder<MobEffect>> FINE_COUNTERPARTS = new LinkedHashMap<>();
    private static final Set<Holder<MobEffect>> REPLICATED_EFFECTS = new LinkedHashSet<>();

    static {
        mapMechanical(MobEffects.INVISIBILITY, TRANSLUCENCY);
        mapMechanical(MobEffects.NIGHT_VISION, TWILIGHT_VISION);
        mapMechanical(MobEffects.WATER_BREATHING, AQUATIC_RESPIRATION);
        mapMechanical(MobEffects.SLOW_FALLING, CUSHIONED_FALL);
        mapMechanical(MobEffects.GLOWING, FAINT_GLOW);
        mapMechanical(MobEffects.FIRE_RESISTANCE, EMBER_WARD);
        mapMechanical(MobEffects.LEVITATION, PARTIAL_LEVITATION);
        mapMechanical(MobEffects.DOLPHINS_GRACE, FLUID_GRACE);
        mapMechanical(MobEffects.CONDUIT_POWER, CONDUIT_ECHO);
        mapMechanical(MobEffects.BREATH_OF_THE_NAUTILUS, NAUTILUS_ECHO);

        mapFine(MobEffects.SPEED, FAST_WALKING);
        mapFine(MobEffects.SLOWNESS, HEAVY_STEP);
        mapFine(MobEffects.HASTE, NIMBLE_HANDS);
        mapFine(MobEffects.MINING_FATIGUE, WEARY_HANDS);
        mapFine(MobEffects.STRENGTH, MIGHT_SURGE);
        mapFine(MobEffects.WEAKNESS, FEEBLE_GRIP);
        mapFine(MobEffects.JUMP_BOOST, SPRING_STEP);
        mapFine(MobEffects.RESISTANCE, WARDING_AURA);
        mapFine(MobEffects.LUCK, MINOR_FORTUNE);
        mapFine(MobEffects.UNLUCK, MINOR_JINX);
    }

    private OIMRMobEffects() {
    }

    private static void mapMechanical(Holder<MobEffect> original, Holder<MobEffect> replicated) {
        MECHANICAL_COUNTERPARTS.put(original, replicated);
        REPLICATED_EFFECTS.add(replicated);
    }

    private static void mapFine(Holder<MobEffect> original, Holder<MobEffect> fineCounterpart) {
        FINE_COUNTERPARTS.put(original, fineCounterpart);
        REPLICATED_EFFECTS.add(fineCounterpart);
    }

    private static Holder<MobEffect> register(String id, MobEffectCategory category, int color) {
        return register(id, new ReplicatedMobEffect(category, color));
    }

    private static Holder<MobEffect> register(String id, MobEffect effect) {
        return Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, id),
            effect
        );
    }

    /**
     * Triggers static initialization and registry registration during mod startup.
     */
    public static void register() {
        // Static fields register all custom replicated MobEffects.
    }

    public static @Nullable Holder<MobEffect> getReplicatedCounterpart(Holder<MobEffect> original) {
        return MECHANICAL_COUNTERPARTS.get(original);
    }

    public static @Nullable Holder<MobEffect> getFineCounterpart(Holder<MobEffect> original) {
        return FINE_COUNTERPARTS.get(original);
    }

    public static boolean isReplicatedEffect(Holder<MobEffect> effect) {
        return REPLICATED_EFFECTS.contains(effect);
    }

    public static Set<Holder<MobEffect>> allReplicatedEffects() {
        return Set.copyOf(REPLICATED_EFFECTS);
    }

    /**
     * Removes any custom partial-affinity replicated effects from the given entity.
     */
    public static void clearReplicatedEffects(LivingEntity entity) {
        for (Holder<MobEffect> replicated : REPLICATED_EFFECTS) {
            if (entity.hasEffect(replicated)) {
                entity.removeEffect(replicated);
            }
        }
    }

    public static final class ReplicatedMobEffect extends MobEffect {
        public ReplicatedMobEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
