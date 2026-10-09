package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.entity.SpringPadEntity;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.ritual.TrapRoster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * Spring Pad: a pad laid over a moment of standing still, which throws whatever steps on it high
 * into the air — its layer too — a few times before it gives.
 * <p>
 * Laid the way Barbed Wire is: hold the key without moving, and only a pad that actually goes down is
 * paid for. The pads themselves are {@link SpringPadEntity}, saved with the world.
 */
public final class SpringPad {

    /** One pad being laid. */
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
    /** Safety Protocol: owners thrown by their own pad, whose landing is covered until when. */
    private static final Map<UUID, Long> COVERED = new HashMap<>();

    private SpringPad() {}

    /** The press alone does nothing: the pad is laid off the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    /** The key going down starts the work; the key coming up abandons it. */
    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.SPRING_PAD);
        if (!holding || tier <= 0) {
            if (SETTING.remove(player.getUUID()) != null) {
                player.displayClientMessage(Component.translatable("bloodbound.message.spring_pad_stopped")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return;
        }
        if (data.isOnCooldown(ModPerks.SPRING_PAD.id(), player.level().getGameTime())) {
            return;
        }
        SETTING.putIfAbsent(player.getUUID(), new Setup(tier, player.position()));
    }

    /** Advances the work, and finishes it. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        // Taking the perk out of the loadout takes every pad it laid with it.
        if (data.getActiveTier(ModPerks.SPRING_PAD) <= 0) {
            if (TrapRoster.hasAny(SpringPadEntity.KIND, player.getUUID()) || SETTING.containsKey(player.getUUID())) {
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
        if (player.position().distanceToSqr(setup.origin)
                > ModPerks.BARBED_STILL_EPSILON * ModPerks.BARBED_STILL_EPSILON * 400.0D) {
            SETTING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("bloodbound.message.spring_pad_moved")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        int needed = ModPerks.SPRING_PAD.ticks(ModPerks.SPRING_SETUP, setup.tier);
        setup.ticksHeld++;
        if (setup.ticksHeld % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME,
                    player.getX(), player.getY() + 0.1D, player.getZ(), 2, 0.3D, 0.02D, 0.3D, 0.0D);
        }
        if (setup.ticksHeld % 5 == 0) {
            player.displayClientMessage(Component.translatable("bloodbound.message.spring_pad_setting",
                    String.format("%.1f", Math.max(0, needed - setup.ticksHeld) / 20.0D)), true);
        }
        if (setup.ticksHeld >= needed) {
            SETTING.remove(player.getUUID());
            place(player, data, setup.tier, gameTime);
        }
    }

    private static void place(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        ServerLevel level = player.serverLevel();
        SpringPadEntity pad = new SpringPadEntity(level, player, tier);
        pad.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        // Joining the level is what puts it on the roster, and pushes the oldest pad out past the limit.
        level.addFreshEntity(pad);

        data.setCooldown(ModPerks.SPRING_PAD.id(), gameTime, ModPerks.SPRING_PAD.cooldownTicks(tier));
        PerkDataManager.sync(player);

        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.7F, 1.2F);
        player.displayClientMessage(Component.translatable("bloodbound.message.spring_pad_placed",
                TrapRoster.count(SpringPadEntity.KIND, player.getUUID()), pad.trapLimit())
                .withStyle(ChatFormatting.GRAY), true);
    }

    /** A pad threw its own layer. With Safety Protocol, that flight ends without a scratch. */
    public static void onOwnerLaunched(ServerPlayer owner) {
        if (PerkDataManager.get(owner).isAddonActive(ModAddons.SAFETY_PROTOCOL)) {
            COVERED.put(owner.getUUID(), owner.level().getGameTime() + ModAddons.SAFETY_PROTOCOL_TICKS);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Long until = COVERED.remove(player.getUUID());
        if (until != null && player.level().getGameTime() <= until) {
            event.setCanceled(true);
        }
    }

    /** Takes every pad the player has out away, wherever it is: the perk came off. */
    public static void clear(UUID playerId) {
        SETTING.remove(playerId);
        COVERED.remove(playerId);
        TrapRoster.removeAll(SpringPadEntity.KIND, playerId);
    }

    /** A logout: the work in hand is dropped, the pads already down stay where they are. */
    public static void logout(UUID playerId) {
        SETTING.remove(playerId);
        COVERED.remove(playerId);
    }
}
