package net.h3xpy.bloodbound;

import net.h3xpy.bloodbound.client.ClientCameraShake;
import net.h3xpy.bloodbound.client.ClientHangedMan;
import net.h3xpy.bloodbound.client.ClientReactiveCompound;
import net.h3xpy.bloodbound.client.ClientDrawSpeed;
import net.h3xpy.bloodbound.client.ClientSanctumHits;
import net.h3xpy.bloodbound.client.ClientEventHandler;
import net.h3xpy.bloodbound.client.ClientUnderTheRadar;
import net.h3xpy.bloodbound.client.ModKeyMappings;
import net.h3xpy.bloodbound.client.hud.EffectBarsOverlay;
import net.h3xpy.bloodbound.client.hud.FlashedOverlay;
import net.h3xpy.bloodbound.client.hud.PerkHudLayer;
import net.h3xpy.bloodbound.client.hud.SkillCheckOverlay;
import net.h3xpy.bloodbound.client.model.BarbedWireModel;
import net.h3xpy.bloodbound.client.model.IceShellModel;
import net.h3xpy.bloodbound.client.model.TargetFoundModel;
import net.h3xpy.bloodbound.client.render.BarbedWireRenderer;
import net.h3xpy.bloodbound.client.model.AngelWingsModel;
import net.h3xpy.bloodbound.client.render.AngelWingsLayer;
import net.h3xpy.bloodbound.client.render.ChainAnchorRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.h3xpy.bloodbound.client.render.SpringPadRenderer;
import net.h3xpy.bloodbound.client.render.IceShellRenderer;
import net.h3xpy.bloodbound.client.render.RitualRenderer;
import net.h3xpy.bloodbound.client.render.SanctumBubbleRenderer;
import net.h3xpy.bloodbound.client.render.TargetFoundRenderer;
import net.h3xpy.bloodbound.client.screen.PerkTableScreen;
import net.h3xpy.bloodbound.registry.ModEntities;
import net.h3xpy.bloodbound.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only setup: the perk table screen, the perk HUD and the activation key.
 */
@Mod(value = BloodBound.MODID, dist = Dist.CLIENT)
public class BloodBoundClient {

    public BloodBoundClient(ModContainer container, IEventBus modEventBus) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Puts a Config button on the mod's entry in the mods list, using NeoForge's own screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(ModKeyMappings::register);
        modEventBus.addListener(BloodBoundClient::registerScreens);
        modEventBus.addListener(BloodBoundClient::registerGuiLayers);
        modEventBus.addListener(BloodBoundClient::registerEntityRenderers);
        modEventBus.addListener(BloodBoundClient::addPlayerLayers);
        modEventBus.addListener(BloodBoundClient::registerLayers);

        NeoForge.EVENT_BUS.register(ClientEventHandler.class);
        NeoForge.EVENT_BUS.register(ClientUnderTheRadar.class);
        NeoForge.EVENT_BUS.register(ClientDrawSpeed.class);
        NeoForge.EVENT_BUS.register(ClientSanctumHits.class);
        NeoForge.EVENT_BUS.register(ClientCameraShake.class);
        NeoForge.EVENT_BUS.register(ClientHangedMan.class);
        NeoForge.EVENT_BUS.register(ClientReactiveCompound.class);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PERK_TABLE.get(), PerkTableScreen::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BarbedWireModel.LAYER_LOCATION, BarbedWireModel::createBodyLayer);
        event.registerLayerDefinition(TargetFoundModel.LAYER_LOCATION, TargetFoundModel::createBodyLayer);
        event.registerLayerDefinition(IceShellModel.LAYER_LOCATION, IceShellModel::createBodyLayer);
        event.registerLayerDefinition(AngelWingsModel.LAYER_LOCATION, AngelWingsModel::createBodyLayer);
    }

    /** Angel wings, on every player model: the wide-armed one and the slim one. */
    private static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new AngelWingsLayer(renderer, event.getEntityModels()));
            }
        }
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BARBED_WIRE.get(), BarbedWireRenderer::new);
        event.registerEntityRenderer(ModEntities.TARGET_FOUND.get(), TargetFoundRenderer::new);
        event.registerEntityRenderer(ModEntities.RITUAL.get(), RitualRenderer::new);
        event.registerEntityRenderer(ModEntities.ICE_SHELL.get(), IceShellRenderer::new);
        event.registerEntityRenderer(ModEntities.CHAIN_ANCHOR.get(), ChainAnchorRenderer::new);
        event.registerEntityRenderer(ModEntities.SPRING_PAD.get(), SpringPadRenderer::new);
        event.registerEntityRenderer(ModEntities.SANCTUM_BUBBLE.get(), SanctumBubbleRenderer::new);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, PerkHudLayer.ID, new PerkHudLayer());
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, SkillCheckOverlay.ID, new SkillCheckOverlay());
        // Above the hotbar, where a player already looks for their own condition.
        event.registerAbove(VanillaGuiLayers.HOTBAR, EffectBarsOverlay.ID, new EffectBarsOverlay());
        // Above everything: being flashed means seeing nothing, the rest of the interface included.
        event.registerAbove(VanillaGuiLayers.CHAT, FlashedOverlay.ID, new FlashedOverlay());
    }
}
