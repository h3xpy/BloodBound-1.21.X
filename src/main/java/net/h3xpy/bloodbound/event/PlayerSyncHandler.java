package net.h3xpy.bloodbound.event;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.heal.HealManager;
import net.h3xpy.bloodbound.perk.impl.LowCostMovementDevice;
import net.h3xpy.bloodbound.mark.MarkManager;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.effect.ExhaustedHandler;
import net.h3xpy.bloodbound.effect.MovementTracker;
import net.h3xpy.bloodbound.perk.impl.AdvancedMovementDevice;
import net.h3xpy.bloodbound.perk.impl.BarbedWire;
import net.h3xpy.bloodbound.perk.impl.BewareThePowerOfAnAngel;
import net.h3xpy.bloodbound.perk.impl.Flashbang;
import net.h3xpy.bloodbound.perk.impl.FragNade;
import net.h3xpy.bloodbound.perk.impl.TargetFound;
import net.h3xpy.bloodbound.perk.impl.HealingRunes;
import net.h3xpy.bloodbound.perk.impl.OutOfBreath;
import net.h3xpy.bloodbound.perk.impl.TeamSpirit;
import net.h3xpy.bloodbound.perk.impl.GreenHerbs;
import net.h3xpy.bloodbound.perk.impl.NoOneGetsAway;
import net.h3xpy.bloodbound.perk.impl.Tinkerer;
import net.h3xpy.bloodbound.skillcheck.SkillCheckManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Keeps the client copy of the perk data in step with the server, and tears down any in-flight
 * skill check or heal when a player leaves, dies or changes world.
 */
public final class PlayerSyncHandler {

    private PlayerSyncHandler() {}

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PerkDataManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillCheckManager.clear(player);
            HealManager.clear(player);
            Tinkerer.clear(player);
            RelentlessHandler.clear(player.getUUID());
            LowCostMovementDevice.clear(player.getUUID());
            NoOneGetsAway.clear(player.getUUID());
            EchoingWoundsHandler.clear(player.getUUID());
            Flashbang.clear(player.getUUID());
            GreenHerbs.clear(player.getUUID());
            GuardianAngelHandler.clear(player.getUUID());
            AdvancedMovementDevice.clear(player.getUUID());
            BewareThePowerOfAnAngel.clear(player);
            GabrielsBowHandler.clear(player.getUUID());
            MarkManager.clear(player.getUUID());
            BarbedWire.clear(player.getUUID());
            OutOfBreath.clear(player.getUUID());
            HealingRunes.clear(player.getUUID());
            TeamSpirit.clear(player.getUUID());
            PanicAttackHandler.clear(player.getUUID());
            BeyondVisionHandler.clear(player.getUUID());
            BleedingHandler.clear(player.getUUID());
            ExhaustedHandler.clear(player.getUUID());
            Flashbang.clearWinding(player.getUUID());
            TargetFound.clear(player.getUUID());
            FragNade.clear(player.getUUID());
            UnderTheRadarHandler.clear(player.getUUID());
            MovementTracker.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        // Bleeding and Exhausted follow mobs too, so their bookkeeping goes on any death.
        BleedingHandler.onDeath(event.getEntity());
        ExhaustedHandler.onDeath(event.getEntity());
        MarkManager.clear(event.getEntity().getUUID());

        if (event.getEntity() instanceof ServerPlayer player) {
            SkillCheckManager.clear(player);
            HealManager.clear(player);
            Tinkerer.clear(player);
            GreenHerbs.clear(player.getUUID());
            AdvancedMovementDevice.clear(player.getUUID());
            BewareThePowerOfAnAngel.clear(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Perks survive death; the cooldowns they were sitting on do not.
            PerkDataManager.get(player).clearCooldowns();
            PerkDataManager.sync(player);
        }
    }

    /** Marks belong to a running world and nothing else, so they go when it does. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MarkManager.clear();
        UnderTheRadarHandler.clear();
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillCheckManager.clear(player);
            HealManager.clear(player);
            PerkDataManager.sync(player);
        }
    }
}
