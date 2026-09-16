package net.h3xpy.bloodbound;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.h3xpy.bloodbound.command.BloodBoundCommand;
import net.h3xpy.bloodbound.event.EffectHandler;
import net.h3xpy.bloodbound.event.LongshotHandler;
import net.h3xpy.bloodbound.event.PerkEventHandler;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.event.EchoingWoundsHandler;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.effect.ExhaustedHandler;
import net.h3xpy.bloodbound.event.BeyondVisionHandler;
import net.h3xpy.bloodbound.event.FullExtractionHandler;
import net.h3xpy.bloodbound.event.GabrielsBowHandler;
import net.h3xpy.bloodbound.event.PanicAttackHandler;
import net.h3xpy.bloodbound.event.GuardianAngelHandler;
import net.h3xpy.bloodbound.event.HuntersInstinctHandler;
import net.h3xpy.bloodbound.event.NastyBladeHandler;
import net.h3xpy.bloodbound.event.RaiseTheStakesHandler;
import net.h3xpy.bloodbound.event.RelentlessHandler;
import net.h3xpy.bloodbound.event.PlayerSyncHandler;
import net.h3xpy.bloodbound.event.SoulShardDropHandler;
import net.h3xpy.bloodbound.network.ModNetwork;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.FragNade;
import net.h3xpy.bloodbound.registry.ModAttachments;
import net.h3xpy.bloodbound.registry.ModBlocks;
import net.h3xpy.bloodbound.registry.ModCreativeTabs;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.h3xpy.bloodbound.registry.ModItems;
import net.h3xpy.bloodbound.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * BloodBound: a Dead by Daylight style perk system. Mobs drop soul shards, shards buy nodes on a
 * soulweb, and the perks those nodes teach are kept for good and equipped four at a time.
 */
@Mod(BloodBound.MODID)
public class BloodBound {
    public static final String MODID = "bloodbound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BloodBound(IEventBus modEventBus, ModContainer modContainer) {
        // Fills the perk registry before anything can look a perk up.
        ModPerks.bootstrap();
        ModAddons.bootstrap();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModEffects.register(modEventBus);
        ModEntities.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);

        NeoForge.EVENT_BUS.register(SoulShardDropHandler.class);
        NeoForge.EVENT_BUS.register(PerkEventHandler.class);
        NeoForge.EVENT_BUS.register(PlayerSyncHandler.class);
        NeoForge.EVENT_BUS.register(EffectHandler.class);
        NeoForge.EVENT_BUS.register(LongshotHandler.class);
        NeoForge.EVENT_BUS.register(RelentlessHandler.class);
        NeoForge.EVENT_BUS.register(RaiseTheStakesHandler.class);
        NeoForge.EVENT_BUS.register(EchoingWoundsHandler.class);
        NeoForge.EVENT_BUS.register(AuraRevealHandler.class);
        NeoForge.EVENT_BUS.register(NastyBladeHandler.class);
        NeoForge.EVENT_BUS.register(BankShotHandler.class);
        NeoForge.EVENT_BUS.register(GuardianAngelHandler.class);
        NeoForge.EVENT_BUS.register(HuntersInstinctHandler.class);
        NeoForge.EVENT_BUS.register(GabrielsBowHandler.class);
        NeoForge.EVENT_BUS.register(PanicAttackHandler.class);
        NeoForge.EVENT_BUS.register(BeyondVisionHandler.class);
        NeoForge.EVENT_BUS.register(BleedingHandler.class);
        NeoForge.EVENT_BUS.register(ExhaustedHandler.class);
        NeoForge.EVENT_BUS.register(FullExtractionHandler.class);
        NeoForge.EVENT_BUS.register(FragNade.class);
        NeoForge.EVENT_BUS.addListener(BloodBoundCommand::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
