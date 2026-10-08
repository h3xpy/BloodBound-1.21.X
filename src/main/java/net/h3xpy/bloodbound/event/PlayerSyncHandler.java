package net.h3xpy.bloodbound.event;

import net.h3xpy.bloodbound.advancement.AchievementTracker;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.effect.ExhaustedHandler;
import net.h3xpy.bloodbound.effect.MovementTracker;
import net.h3xpy.bloodbound.heal.HealManager;
import net.h3xpy.bloodbound.mark.MarkManager;
import net.h3xpy.bloodbound.perk.impl.AdvancedMovementDevice;
import net.h3xpy.bloodbound.perk.impl.BadOmen;
import net.h3xpy.bloodbound.perk.impl.BarbedWire;
import net.h3xpy.bloodbound.perk.impl.BewareThePowerOfAnAngel;
import net.h3xpy.bloodbound.perk.impl.BlessingOfLife;
import net.h3xpy.bloodbound.perk.impl.ChainedUp;
import net.h3xpy.bloodbound.perk.impl.Eavesdrop;
import net.h3xpy.bloodbound.perk.impl.Flashbang;
import net.h3xpy.bloodbound.perk.impl.FragNade;
import net.h3xpy.bloodbound.perk.impl.GreenHerbs;
import net.h3xpy.bloodbound.perk.impl.HealingRunes;
import net.h3xpy.bloodbound.perk.impl.IceBlock;
import net.h3xpy.bloodbound.perk.impl.InevitableDeath;
import net.h3xpy.bloodbound.perk.impl.LowCostMovementDevice;
import net.h3xpy.bloodbound.perk.impl.NoOneGetsAway;
import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.h3xpy.bloodbound.perk.impl.HolySanctum;
import net.h3xpy.bloodbound.perk.impl.Nullification;
import net.h3xpy.bloodbound.perk.impl.Omniscience;
import net.h3xpy.bloodbound.perk.impl.OutOfBreath;
import net.h3xpy.bloodbound.perk.impl.ShortCircuit;
import net.h3xpy.bloodbound.perk.impl.SinOfObliviousness;
import net.h3xpy.bloodbound.perk.impl.TargetFound;
import net.h3xpy.bloodbound.perk.impl.TeamSpirit;
import net.h3xpy.bloodbound.perk.impl.Tinkerer;
import net.h3xpy.bloodbound.perk.impl.Wireless;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.h3xpy.bloodbound.ritual.TrapRoster;
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
            BarbedWire.logout(player.getUUID());
            OutOfBreath.clear(player.getUUID());
            HealingRunes.clear(player.getUUID());
            TeamSpirit.clear(player.getUUID());
            PanicAttackHandler.clear(player.getUUID());
            BeyondVisionHandler.clear(player.getUUID());
            BleedingHandler.clear(player.getUUID());
            ExhaustedHandler.clear(player.getUUID());
            Flashbang.clearWinding(player.getUUID());
            TargetFound.logout(player.getUUID());
            IceBlock.release(player);
            // Traps and rituals stay behind: they are saved with the world and wait for the owner.
            BlessingOfLife.logout(player);
            Eavesdrop.logout(player);
            InevitableDeath.logout(player);
            SinOfObliviousness.logout(player);
            BadOmen.logout(player);
            Nullification.logout(player);
            ShortCircuit.clear(player);
            ChainedUp.clear(player.getUUID());
            Wireless.clear(player.getUUID());
            FragNade.clear(player.getUUID());
            UnderTheRadarHandler.clear(player.getUUID());
            MovementTracker.clear(player.getUUID());
            Omniscience.clear(player.getUUID());
            AchievementTracker.clear(player.getUUID());
            HangedMan.clear(player);
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
            IceBlock.release(player);
            BlessingOfLife.clear(player);
            Eavesdrop.clear(player);
            InevitableDeath.clear(player);
            SinOfObliviousness.clear(player);
            BewareThePowerOfAnAngel.clear(player);
            BadOmen.clear(player);
            Nullification.clear(player);
            HangedMan.clear(player);
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

    /**
     * Marks belong to a running world and nothing else, so they go when it does. Rituals and traps
     * are only forgotten here, not broken: they are saved with their chunks and come back with them.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MarkManager.clear();
        UnderTheRadarHandler.clear();
        RitualManager.clear();
        TrapRoster.clear();
        Nullification.clear();
        HolySanctum.clear();
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
