package net.h3xpy.bloodbound.offering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.h3xpy.bloodbound.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * An offering: laid on the Perk Table, it burns when the player finishes their current soulweb
 * level, and shapes the next one. What it does is its {@link Offering}.
 * <p>
 * Offerings marked with perks or addons carry their marks on the stack. They are rolled the moment
 * the stack is made by a chest, a kill or a trade, and otherwise the first time it sits in a
 * player's inventory, which covers the creative tab and {@code /give}.
 */
public class OfferingItem extends Item {

    private final Offering offering;

    public OfferingItem(Offering offering, Properties properties) {
        super(properties);
        this.offering = offering;
    }

    public Offering offering() {
        return offering;
    }

    /** The perks or addons this stack is marked with; empty for an offering that takes none. */
    public static List<ResourceLocation> marks(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.OFFERING_MARKS.get(), List.of());
    }

    /** Rolls the stack's marks if it should have some and has none yet. Safe to call at any time. */
    public static void ensureMarked(ItemStack stack, RandomSource random) {
        if (!(stack.getItem() instanceof OfferingItem item) || item.offering.marks() == Offering.Marks.NONE
                || stack.has(ModDataComponents.OFFERING_MARKS.get())) {
            return;
        }
        List<ResourceLocation> pool = new ArrayList<>();
        if (item.offering.marks() == Offering.Marks.PERKS) {
            for (Perk perk : PerkRegistry.all()) {
                pool.add(perk.id());
            }
        } else {
            for (Addon addon : AddonRegistry.all()) {
                if (addon.rarity().ordinal() >= item.offering.minAddonRarity().ordinal()) {
                    pool.add(addon.id());
                }
            }
        }
        Collections.shuffle(pool, new Random(random.nextLong()));
        stack.set(ModDataComponents.OFFERING_MARKS.get(),
                List.copyOf(pool.subList(0, Math.min(item.offering.markCount(), pool.size()))));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) {
            ensureMarked(stack, level.getRandom());
        }
    }

    /** Named in the colour of its rarity, as addons are. */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(offering.rarity().textColor());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bloodbound.offering.rarity",
                Component.translatable(offering.rarity().translationKey())).withStyle(offering.rarity().textColor()));
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));

        if (offering.marks() != Offering.Marks.NONE) {
            List<ResourceLocation> marks = marks(stack);
            if (marks.isEmpty()) {
                tooltip.add(Component.translatable("item.bloodbound.offering.unmarked")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                tooltip.add(Component.translatable("item.bloodbound.offering.marked").withStyle(ChatFormatting.GOLD));
                for (ResourceLocation mark : marks) {
                    tooltip.add(Component.literal(" • ").append(markName(mark)).withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        tooltip.add(Component.translatable("item.bloodbound.offering.tooltip_use").withStyle(ChatFormatting.DARK_GRAY));
    }

    private Component markName(ResourceLocation mark) {
        if (offering.marks() == Offering.Marks.PERKS) {
            Perk perk = PerkRegistry.get(mark);
            return perk != null ? perk.displayName() : Component.literal(mark.toString());
        }
        Addon addon = AddonRegistry.get(mark);
        return addon != null
                ? addon.displayName().copy().withStyle(addon.rarity().textColor())
                : Component.literal(mark.toString());
    }
}
