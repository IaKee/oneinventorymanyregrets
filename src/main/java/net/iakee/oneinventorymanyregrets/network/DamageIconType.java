package net.iakee.oneinventorymanyregrets.network;

import net.iakee.oneinventorymanyregrets.OIMRMod;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Visual icon categories for original damage sources shown in the HUD killfeed.
 */
public enum DamageIconType {
    MELEE("melee"),
    PROJECTILE("projectile"),
    TRIDENT("trident"),
    MACE("mace"),
    FIRE("fire"),
    LAVA("lava"),
    DROWN("drown"),
    FALL("fall"),
    EXPLOSION("explosion"),
    FREEZE("freeze"),
    STARVE("starve"),
    WITHER("wither"),
    MAGIC("magic"),
    LIGHTNING("lightning"),
    THORNS("thorns"),
    CRUSH("crush"),
    SONIC("sonic"),
    VOID("void"),
    GENERIC("generic");

    private final Identifier spriteId;

    DamageIconType(String spriteName) {
        this.spriteId = Identifier.fromNamespaceAndPath(OIMRMod.MOD_ID, "hud/damage/" + spriteName);
    }

    public Identifier spriteId() {
        return this.spriteId;
    }

    public static DamageIconType fromDamageSource(DamageSource source) {
        ResourceKey<DamageType> key = source.typeHolder().unwrapKey().orElse(null);
        if (key != null) {
            if (key.equals(DamageTypes.LAVA)) {
                return LAVA;
            }
            if (key.equals(DamageTypes.IN_FIRE)
                || key.equals(DamageTypes.ON_FIRE)
                || key.equals(DamageTypes.CAMPFIRE)
                || key.equals(DamageTypes.HOT_FLOOR)
                || key.equals(DamageTypes.FIREBALL)
                || key.equals(DamageTypes.UNATTRIBUTED_FIREBALL)
                || key.equals(DamageTypes.SULFUR_CUBE_HOT)) {
                return FIRE;
            }
            if (key.equals(DamageTypes.DROWN) || key.equals(DamageTypes.DRY_OUT)) {
                return DROWN;
            }
            if (key.equals(DamageTypes.FALL)
                || key.equals(DamageTypes.ENDER_PEARL)
                || key.equals(DamageTypes.FLY_INTO_WALL)
                || key.equals(DamageTypes.STALAGMITE)) {
                return FALL;
            }
            if (key.equals(DamageTypes.EXPLOSION)
                || key.equals(DamageTypes.PLAYER_EXPLOSION)
                || key.equals(DamageTypes.BAD_RESPAWN_POINT)
                || key.equals(DamageTypes.FIREWORKS)) {
                return EXPLOSION;
            }
            if (key.equals(DamageTypes.FREEZE)) {
                return FREEZE;
            }
            if (key.equals(DamageTypes.STARVE)) {
                return STARVE;
            }
            if (key.equals(DamageTypes.WITHER) || key.equals(DamageTypes.WITHER_SKULL)) {
                return WITHER;
            }
            if (key.equals(DamageTypes.MAGIC)
                || key.equals(DamageTypes.INDIRECT_MAGIC)
                || key.equals(DamageTypes.DRAGON_BREATH)) {
                return MAGIC;
            }
            if (key.equals(DamageTypes.LIGHTNING_BOLT)) {
                return LIGHTNING;
            }
            if (key.equals(DamageTypes.TRIDENT)) {
                return TRIDENT;
            }
            if (key.equals(DamageTypes.MACE_SMASH)) {
                return MACE;
            }
            if (key.equals(DamageTypes.ARROW)
                || key.equals(DamageTypes.MOB_PROJECTILE)
                || key.equals(DamageTypes.SPIT)
                || key.equals(DamageTypes.THROWN)
                || key.equals(DamageTypes.WIND_CHARGE)) {
                return PROJECTILE;
            }
            if (key.equals(DamageTypes.CACTUS)
                || key.equals(DamageTypes.SWEET_BERRY_BUSH)
                || key.equals(DamageTypes.THORNS)
                || key.equals(DamageTypes.STING)) {
                return THORNS;
            }
            if (key.equals(DamageTypes.IN_WALL)
                || key.equals(DamageTypes.CRAMMING)
                || key.equals(DamageTypes.FALLING_ANVIL)
                || key.equals(DamageTypes.FALLING_BLOCK)
                || key.equals(DamageTypes.FALLING_STALACTITE)) {
                return CRUSH;
            }
            if (key.equals(DamageTypes.SONIC_BOOM)) {
                return SONIC;
            }
            if (key.equals(DamageTypes.FELL_OUT_OF_WORLD) || key.equals(DamageTypes.OUTSIDE_BORDER)) {
                return VOID;
            }
            if (key.equals(DamageTypes.PLAYER_ATTACK)
                || key.equals(DamageTypes.MOB_ATTACK)
                || key.equals(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || key.equals(DamageTypes.SPEAR)) {
                return MELEE;
            }
            if (key.equals(DamageTypes.GENERIC) || key.equals(DamageTypes.GENERIC_KILL)) {
                return GENERIC;
            }
        }

        // Tag-based fallback for custom/modded damage types
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return FIRE;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return DROWN;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return FALL;
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return FREEZE;
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING)) {
            return LIGHTNING;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return PROJECTILE;
        }
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return MAGIC;
        }
        if (source.getEntity() != null) {
            return MELEE;
        }
        return GENERIC;
    }
}
