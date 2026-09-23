package net.h3xpy.bloodbound.registry;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.block.PerkTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BloodBound.MODID);

    /** Right-click to open the loadout and soulweb screens. */
    public static final DeferredBlock<PerkTableBlock> PERK_TABLE = BLOCKS.registerBlock(
            "perk_table",
            PerkTableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.5F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 5));

    /**
     * The ritual laid on the ground. Registered only so its model is loaded and baked — nothing ever
     * puts one in the world; the marker entity draws this state where the ritual stands.
     */
    public static final DeferredBlock<Block> RITUAL = BLOCKS.registerBlock(
            "ritual",
            Block::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .sound(SoundType.WOOL));

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
