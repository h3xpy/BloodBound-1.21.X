package net.h3xpy.bloodbound.offering;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Slips an offering, now and then, into the chests structures generate with — every loot table
 * under {@code chests/}, so modded structures that follow the convention are covered too. The odds
 * are {@link Offerings#CHEST_CHANCES}.
 */
public class OfferingLootModifier extends LootModifier {

    public static final MapCodec<OfferingLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, OfferingLootModifier::new));

    public OfferingLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (context.getQueriedLootTableId().getPath().startsWith("chests/")) {
            ItemStack offering = Offerings.roll(context.getRandom(), Offerings.CHEST_CHANCES);
            if (offering != null) {
                generatedLoot.add(offering);
            }
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
