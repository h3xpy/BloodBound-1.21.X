package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.entity.TargetFoundEntity;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Target Found: tripwires that tell you who is coming, and give you a way to meet them.
 * <p>
 * Laid the way Barbed Wire is — a few seconds of holding still with the key down. When one is
 * tripped and you are close enough to matter, the intruder lights up and, after a short wait, you
 * get a four-second window in which the key takes you straight to the wire. The wire goes when the
 * window does, whether you used it or not. Too far away, and all you get is the warning.
 */
public final class TargetFound {

    /** One wire being laid. */
    private static final class Setup {
        private final int tier;
        private final Vec3 origin;
        private int ticksHeld;

        private Setup(int tier, Vec3 origin) {
            this.tier = tier;
            this.origin = origin;
        }
    }

    /** A tripped wire waiting on its owner. */
    private static final class Pending {
        private final TargetFoundEntity wire;
        private final ResourceKey<Level> dimension;
        private final Vec3 position;
        private final long opensAt;
        private final long closesAt;
        private boolean announced;

        private Pending(TargetFoundEntity wire, long opensAt, long closesAt) {
            this.wire = wire;
            this.dimension = wire.level().dimension();
            this.position = wire.position();
            this.opensAt = opensAt;
            this.closesAt = closesAt;
        }
    }

    private static final Map<UUID, Setup> SETTING = new HashMap<>();
    private static final Map<UUID, Deque<TargetFoundEntity>> PLACED = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private TargetFound() {}

    // --- laying a wire ---

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.TARGET_FOUND);
        if (!holding || tier <= 0) {
            if (SETTING.remove(player.getUUID()) != null) {
                player.displayClientMessage(Component.translatable("bloodbound.message.target_found_stopped")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return;
        }
        // A wire waiting on its owner makes the key mean "take me there", not "lay another".
        if (PENDING.containsKey(player.getUUID())
                || data.isOnCooldown(ModPerks.TARGET_FOUND.id(), player.level().getGameTime())) {
            return;
        }
        SETTING.putIfAbsent(player.getUUID(), new Setup(tier, player.position()));
    }

    /** Nothing: the press repeats while the key is held, so only the hold may drive the work. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    // --- the window ---

    /**
     * The key, while a tripped wire is waiting. Checked ahead of the cooldown, since a wire tripped
     * just after another was laid is exactly when the perk is on cooldown.
     *
     * @return true when the key was spent taking the player to the wire
     */
    public static boolean tryTeleport(ServerPlayer player, long gameTime) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || gameTime < pending.opensAt) {
            return false;
        }
        PENDING.remove(player.getUUID());
        pending.wire.discard();

        if (player.level().dimension() != pending.dimension) {
            return true;
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
                30, 0.3D, 0.6D, 0.3D, 0.2D);
        player.teleportTo(pending.position.x, pending.position.y, pending.position.z);
        player.resetFallDistance();
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
        player.displayClientMessage(Component.translatable("bloodbound.message.target_found_arrived")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Something walked across a wire. */
    public static void onTripped(ServerLevel level, TargetFoundEntity wire, LivingEntity victim) {
        ServerPlayer owner = wire.ownerId() == null ? null
                : level.getServer().getPlayerList().getPlayer(wire.ownerId());
        if (owner == null) {
            wire.discard();
            return;
        }
        PlayerPerkData data = PerkDataManager.get(owner);
        int tier = data.getActiveTier(ModPerks.TARGET_FOUND);
        if (tier <= 0) {
            wire.discard();
            return;
        }

        boolean close = owner.level() == level
                && owner.distanceToSqr(wire) <= ModPerks.TARGET_RANGE * ModPerks.TARGET_RANGE;
        owner.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, close ? 1.5F : 0.8F);

        if (!close) {
            // Out of reach: the warning is all there is, and the wire has done its job.
            owner.displayClientMessage(Component.translatable("bloodbound.message.target_found_far",
                    victim.getName()).withStyle(ChatFormatting.GRAY), false);
            wire.discard();
            return;
        }

        long now = level.getGameTime();
        int delay = ModPerks.TARGET_FOUND.ticks(ModPerks.TARGET_DELAY, tier);
        AuraRevealHandler.reveal(owner, victim, delay + ModPerks.TARGET_WINDOW_TICKS);
        owner.displayClientMessage(Component.translatable("bloodbound.message.target_found_triggered",
                victim.getName(), (delay + 19) / 20).withStyle(ChatFormatting.GOLD), false);

        // A newer trip replaces the one still waiting; that older wire has had its chance.
        Pending previous = PENDING.put(owner.getUUID(), new Pending(wire, now + delay,
                now + delay + ModPerks.TARGET_WINDOW_TICKS));
        if (previous != null && previous.wire != wire) {
            previous.wire.discard();
        }
    }

    // --- upkeep ---

    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (data.getActiveTier(ModPerks.TARGET_FOUND) <= 0) {
            // Out of the loadout, and every wire goes with it.
            if (PLACED.containsKey(player.getUUID()) || PENDING.containsKey(player.getUUID())
                    || SETTING.containsKey(player.getUUID())) {
                clear(player.getUUID());
            }
            return;
        }

        tickWindow(player, gameTime);
        tickSetup(player, data, gameTime);
    }

    private static void tickWindow(ServerPlayer player, long gameTime) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (gameTime >= pending.closesAt) {
            PENDING.remove(player.getUUID());
            pending.wire.discard();
            player.displayClientMessage(Component.translatable("bloodbound.message.target_found_missed")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        if (gameTime < pending.opensAt) {
            return;
        }
        if (!pending.announced) {
            pending.announced = true;
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.8F);
        }
        if (gameTime % 5L == 0L) {
            player.displayClientMessage(Component.translatable("bloodbound.message.target_found_window",
                    (pending.closesAt - gameTime + 19) / 20).withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void tickSetup(ServerPlayer player, PlayerPerkData data, long gameTime) {
        Setup setup = SETTING.get(player.getUUID());
        if (setup == null) {
            return;
        }
        if (!player.isAlive()) {
            SETTING.remove(player.getUUID());
            return;
        }
        if (player.position().distanceToSqr(setup.origin)
                > ModPerks.BARBED_STILL_EPSILON * ModPerks.BARBED_STILL_EPSILON * 400.0D) {
            SETTING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("bloodbound.message.target_found_moved")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        int needed = ModPerks.TARGET_FOUND.ticks(ModPerks.TARGET_SETUP, setup.tier);
        setup.ticksHeld++;
        if (setup.ticksHeld % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 0.1D, player.getZ(), 3, 0.3D, 0.02D, 0.3D, 0.0D);
        }
        if (setup.ticksHeld % 5 == 0) {
            player.displayClientMessage(Component.translatable("bloodbound.message.target_found_setting",
                    Math.max(1, (needed - setup.ticksHeld + 19) / 20)), true);
        }
        if (setup.ticksHeld >= needed) {
            SETTING.remove(player.getUUID());
            place(player, data, setup.tier, gameTime);
        }
    }

    private static void place(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        ServerLevel level = player.serverLevel();
        TargetFoundEntity wire = new TargetFoundEntity(level, player, tier);
        wire.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(wire);

        Deque<TargetFoundEntity> mine = PLACED.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        mine.removeIf(TargetFoundEntity::isRemoved);
        mine.addLast(wire);
        int limit = ModPerks.TARGET_FOUND.intValue(ModPerks.TARGET_TRAPS, tier);
        while (mine.size() > limit) {
            mine.removeFirst().discard();
        }

        data.setCooldown(ModPerks.TARGET_FOUND.id(), gameTime, ModPerks.TARGET_FOUND.cooldownTicks(tier));
        PerkDataManager.sync(player);

        level.playSound(null, player.blockPosition(), SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable("bloodbound.message.target_found_placed",
                mine.size(), limit).withStyle(ChatFormatting.GRAY), true);
    }

    /** Forgets a player entirely, and takes their wires with them. */
    public static void clear(UUID playerId) {
        SETTING.remove(playerId);
        Pending pending = PENDING.remove(playerId);
        if (pending != null) {
            pending.wire.discard();
        }
        Deque<TargetFoundEntity> mine = PLACED.remove(playerId);
        if (mine != null) {
            new ArrayList<>(mine).forEach(TargetFoundEntity::discard);
        }
    }
}
