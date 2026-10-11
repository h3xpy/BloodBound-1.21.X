package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.entity.BarbedWireEntity;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.h3xpy.bloodbound.ritual.TrapRoster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Barbed Wire: a coil laid down over a few seconds of standing perfectly still.
 * <p>
 * The setting up is the cost. Hold the key, do not move, and at the end of it there is a trap where
 * you were standing; move, or let go, and there is nothing and no cooldown either. Only the trap
 * that actually gets laid is paid for.
 */
public final class BarbedWire {

    /** One coil being laid. */
    private static final class Setup {
        private final int tier;
        private final Vec3 origin;
        private int ticksHeld;

        private Setup(int tier, Vec3 origin) {
            this.tier = tier;
            this.origin = origin;
        }
    }

    private static final Map<UUID, Setup> SETTING = new HashMap<>();

    private BarbedWire() {}

    /** The key going down starts the work; the key coming up abandons it. */
    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.BARBED_WIRE);
        if (!holding || tier <= 0) {
            if (SETTING.remove(player.getUUID()) != null) {
                player.displayClientMessage(Component.translatable("bloodbound.message.barbed_wire_stopped")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return;
        }
        if (data.isOnCooldown(ModPerks.BARBED_WIRE.id(), player.level().getGameTime())) {
            return;
        }
        // Only ever started, never restarted: a second report of the key being down must not throw
        // away the seconds already spent.
        SETTING.putIfAbsent(player.getUUID(), new Setup(tier, player.position()));
    }

    /**
     * A press on its own does nothing: the perk runs off the hold. Reported as having fired so the
     * state sync goes out and the HUD picks the work up.
     */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        // Nothing. The press repeats while the key is held, and each repeat used to restart the work
        // from zero, so a coil could never be finished. The held report alone drives it.
        return false;
    }

    /** Advances the work, and finishes it. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        // Taking the perk out of the loadout takes every coil it laid with it.
        if (data.getActiveTier(ModPerks.BARBED_WIRE) <= 0) {
            if (data.hasEquipped(ModPerks.BARBED_WIRE)) {
                // Only disabled for a while: the coils stay down, the one being laid does not.
                SETTING.remove(player.getUUID());
            } else if (TrapRoster.hasAny(BarbedWireEntity.KIND, player.getUUID()) || SETTING.containsKey(player.getUUID())) {
                clear(player.getUUID());
            }
            return;
        }

        Setup setup = SETTING.get(player.getUUID());
        if (setup == null) {
            return;
        }
        if (!player.isAlive()) {
            SETTING.remove(player.getUUID());
            return;
        }

        // Standing still is the whole of it. A step in any direction and the coil is dropped.
        if (player.position().distanceToSqr(setup.origin)
                > ModPerks.BARBED_STILL_EPSILON * ModPerks.BARBED_STILL_EPSILON * 400.0D) {
            SETTING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("bloodbound.message.barbed_wire_moved")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        int needed = ModPerks.BARBED_WIRE.ticks(ModPerks.BARBED_SETUP, setup.tier);
        if (data.isAddonActive(ModAddons.FENCE_PLIERS)) {
            needed = Math.round(needed * ModAddons.FENCE_PLIERS_SETUP);
        }
        setup.ticksHeld++;

        if (setup.ticksHeld % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY() + 0.1D, player.getZ(), 2, 0.3D, 0.02D, 0.3D, 0.0D);
        }
        if (setup.ticksHeld % 5 == 0) {
            player.displayClientMessage(Component.translatable("bloodbound.message.barbed_wire_setting",
                    Math.max(1, (needed - setup.ticksHeld + 19) / 20)), true);
        }

        if (setup.ticksHeld >= needed) {
            SETTING.remove(player.getUUID());
            place(player, data, setup.tier, gameTime);
        }
    }

    private static void place(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        ServerLevel level = player.serverLevel();
        BarbedWireEntity wire = new BarbedWireEntity(level, player, tier);
        wire.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        // Joining the level is what puts it on the roster, and pushes the oldest coil out past the limit.
        level.addFreshEntity(wire);
        int limit = wire.trapLimit();

        data.setCooldown(ModPerks.BARBED_WIRE.id(), gameTime, ModPerks.BARBED_WIRE.cooldownTicks(tier));
        PerkDataManager.sync(player);

        level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.7F, 0.9F);
        player.displayClientMessage(Component.translatable("bloodbound.message.barbed_wire_placed",
                TrapRoster.count(BarbedWireEntity.KIND, player.getUUID()), limit).withStyle(ChatFormatting.GRAY), true);
    }

    /** Takes every coil the player has out away, wherever it is: the perk came off. */
    public static void clear(UUID playerId) {
        SETTING.remove(playerId);
        TrapRoster.removeAll(BarbedWireEntity.KIND, playerId);
    }

    // --- Dirty Blade: slowed for as long as the bleeding lasts ---

    /** Victims of a Dirty Blade coil, slowed until their Bleeding is gone. */
    private static final Set<LivingEntity> DIRTY = new HashSet<>();

    public static void slowWhileBleeding(LivingEntity victim) {
        DIRTY.add(victim);
        topUpSlow(victim);
    }

    /** Keeps every Dirty Blade victim slowed while they bleed. Called once a tick for the whole server. */
    public static void tickDirty() {
        if (DIRTY.isEmpty()) {
            return;
        }
        Iterator<LivingEntity> iterator = DIRTY.iterator();
        while (iterator.hasNext()) {
            LivingEntity victim = iterator.next();
            if (victim.isRemoved() || !victim.isAlive() || !victim.hasEffect(ModEffects.BLEEDING)) {
                iterator.remove();
                continue;
            }
            topUpSlow(victim);
        }
    }

    /** Slowness II in short slices, so it ends within a moment of the bleeding. */
    private static void topUpSlow(LivingEntity victim) {
        MobEffectInstance slow = victim.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        int amplifier = ModAddons.DIRTY_BLADE_SLOW_LEVEL - 1;
        if (slow == null || (slow.getAmplifier() <= amplifier && slow.getDuration() < ModAddons.DIRTY_BLADE_SLOW_TICKS / 2)) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ModAddons.DIRTY_BLADE_SLOW_TICKS,
                    amplifier, false, true, true));
        }
    }

    /** Forgets every Dirty Blade victim, on server shutdown. */
    public static void clearDirty() {
        DIRTY.clear();
    }

    /** A logout: the work in hand is dropped, the coils already down stay where they are. */
    public static void logout(UUID playerId) {
        SETTING.remove(playerId);
    }
}
