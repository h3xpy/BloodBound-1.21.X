package net.h3xpy.bloodbound.damage;

import net.h3xpy.bloodbound.BloodBound;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * The mod's own damage types, defined as datapack files under {@code data/bloodbound/damage_type}.
 * <p>
 * A perk only needs one of these when vanilla has nothing that behaves the way it should — Frag'
 * Nade is in the armour and enchantment bypass tags, so a blast counts for its full worth whatever
 * the victim is wearing.
 */
public final class ModDamageTypes {

    public static final ResourceKey<DamageType> FRAG_NADE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "frag_nade"));

    private ModDamageTypes() {}
}
