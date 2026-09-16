package net.h3xpy.bloodbound.effect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.heal.HealManager;
import net.h3xpy.bloodbound.mark.MarkManager;
import net.h3xpy.bloodbound.network.EffectChargesPayload;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Bleeding: a wound that opens every time you run.
 * <p>
 * The charges are held here rather than on the effect — the effect is only a flag. A player spends
 * one per second of running and pays in blood once the bar is empty; the only way out is to hold
 * the heal key, and the emptier the bar the longer that takes. Dressing the wound can be put down
 * and picked up again without losing what was done. A mob has no bar to manage and simply bleeds
 * out, two health a second for as many seconds as it had charges.
 */
public final class BleedingHandler {

    /** The bar that shows how far along a dressing is. Not an effect, only a name for the HUD. */
    public static final ResourceLocation CURE_BAR_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "bleeding_cure");

    /** How long a player has to hold the heal key, in seconds, before the charges are counted off. */
    private static final int CURE_BASE_SECONDS = 20;
    /** Health a running player loses per second once the bar is empty. */
    private static final float EMPTY_RUN_DAMAGE = 5.0F;
    /** Health a bleeding mob loses per second. */
    private static final float MOB_DAMAGE_PER_SECOND = 2.0F;
    /** Ticks of running that cost one charge. */
    private static final int RUN_TICKS_PER_CHARGE = 20;

    /** Charges left, and how many the wound started with — the bar needs both. */
    private record Wound(int charges, int maxCharges) {}

    private static final Map<UUID, Wound> WOUNDS = new HashMap<>();
    /** Players holding the heal key with the intent of dressing their own wound. */
    private static final Set<UUID> CURING = new HashSet<>();
    /** Ticks of dressing banked so far. Kept through a pause, lost only with the wound. */
    private static final Map<UUID, Integer> CURE_PROGRESS = new HashMap<>();
    /** Ticks of running banked towards the next charge. */
    private static final Map<UUID, Integer> RUN_TICKS = new HashMap<>();

    private BleedingHandler() {}

    // --- opening and closing the wound ---

    /**
     * Opens a wound, or deepens one that is already there. Charges never stack past the deeper of
     * the two: two shallow cuts are not one deep one.
     */
    public static void apply(LivingEntity victim, int charges) {
        if (charges <= 0 || !victim.isAlive()) {
            return;
        }

        Wound existing = WOUNDS.get(victim.getUUID());
        int total = existing == null ? charges : Math.max(existing.charges(), charges);
        int max = existing == null ? charges : Math.max(existing.maxCharges(), total);

        // A mob bleeds out on a clock, so the effect carries the time; a player's wound waits to be
        // dressed, so theirs does not run out on its own.
        int duration = victim instanceof Player ? -1 : total * 20;
        victim.addEffect(new MobEffectInstance(ModEffects.BLEEDING, duration, 0, false, true, true));
        if (!victim.hasEffect(ModEffects.BLEEDING)) {
            return;
        }

        Wound wound = new Wound(total, max);
        WOUNDS.put(victim.getUUID(), wound);
        send(victim);

        if (existing == null && victim instanceof ServerPlayer player) {
            // Said once, plainly, with the key the player actually has bound.
            player.displayClientMessage(Component.translatable("bloodbound.message.bleeding_started",
                    Component.keybind("key.bloodbound.heal")).withStyle(ChatFormatting.RED), false);
            sendCure(player, 0, needed(player, wound));
        }
    }

    public static int charges(LivingEntity entity) {
        Wound wound = WOUNDS.get(entity.getUUID());
        return wound == null ? 0 : wound.charges();
    }

    /** The wound closing, however it happened. */
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.BLEEDING)) {
            close(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.BLEEDING)) {
            close(event.getEntity());
        }
    }

    private static void close(LivingEntity entity) {
        if (WOUNDS.remove(entity.getUUID()) != null) {
            CURE_PROGRESS.remove(entity.getUUID());
            RUN_TICKS.remove(entity.getUUID());
            send(entity);
            if (entity instanceof ServerPlayer player) {
                sendCure(player, 0, 0);
            }
        }
    }

    // --- the wound running ---

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide) {
            return;
        }
        Wound wound = WOUNDS.get(entity.getUUID());
        if (wound == null) {
            return;
        }
        if (!entity.hasEffect(ModEffects.BLEEDING) || !entity.isAlive()) {
            close(entity);
            return;
        }

        trail(entity);

        if (entity instanceof ServerPlayer player) {
            tickPlayer(player, wound);
            // The dressing bar stays on screen while it is paused, so the player can see what is kept.
            if (entity.tickCount % 20 == 0 && !CURING.contains(player.getUUID())) {
                sendCure(player, CURE_PROGRESS.getOrDefault(player.getUUID(), 0), needed(player, wound));
            }
        } else if (entity.tickCount % 20 == 0) {
            entity.hurt(entity.damageSources().generic(), MOB_DAMAGE_PER_SECOND);
        }
    }

    /**
     * A player only bleeds when they run — sprinting, or leaping along at a sprint's pace — but
     * running on an empty bar is very expensive. Counted tick by tick rather than sampled once a
     * second, so a run broken up by jumps is billed for all of it.
     */
    private static void tickPlayer(ServerPlayer player, Wound wound) {
        if (!MovementTracker.isRunning(player)) {
            return;
        }
        int run = RUN_TICKS.getOrDefault(player.getUUID(), 0) + 1;
        if (run < RUN_TICKS_PER_CHARGE) {
            RUN_TICKS.put(player.getUUID(), run);
            return;
        }
        RUN_TICKS.put(player.getUUID(), 0);

        if (wound.charges() > 0) {
            WOUNDS.put(player.getUUID(), new Wound(wound.charges() - 1, wound.maxCharges()));
            send(player);
            player.playNotifySound(SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.3F, 0.7F);
            return;
        }
        player.hurt(player.damageSources().generic(), EMPTY_RUN_DAMAGE);
    }

    /**
     * A trail of blood, which everyone can see whether or not they read marks. A drip in the air
     * every few ticks, and drops on the ground that stay there for seven seconds.
     */
    private static void trail(LivingEntity entity) {
        if (entity.tickCount % 4 != 0 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (entity.tickCount % 12 == 0 && entity.onGround()) {
            MarkManager.bleed(level, entity);
        }
        level.sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.02F, 0.02F), 1.2F),
                entity.getX(), entity.getY() + 0.15D, entity.getZ(), 3,
                entity.getBbWidth() * 0.3D, 0.05D, entity.getBbWidth() * 0.3D, 0.0D);
    }

    // --- dressing it ---

    /**
     * The heal key, when the player holding it is the one bleeding. Dressing your own wound needs no
     * perk at all — it is the one piece of healing anybody can do. Letting go only pauses it.
     */
    public static void setCuring(ServerPlayer player, boolean curing) {
        if (curing) {
            CURING.add(player.getUUID());
            return;
        }
        if (CURING.remove(player.getUUID()) && CURE_PROGRESS.getOrDefault(player.getUUID(), 0) > 0) {
            player.displayClientMessage(Component.translatable("bloodbound.message.bleeding_paused")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    public static boolean isBleeding(ServerPlayer player) {
        return WOUNDS.containsKey(player.getUUID());
    }

    /** Ticks the dressing takes. The deeper the wound, the longer; the medic perks shorten it. */
    private static int needed(ServerPlayer player, Wound wound) {
        int seconds = Math.max(1, CURE_BASE_SECONDS - wound.charges());
        return Math.max(1, (int) Math.round(seconds * 20.0D / (1.0D + HealManager.healSpeedBonus(player))));
    }

    /** Advances a dressing in progress. Called once a tick per player. */
    public static void tickCure(ServerPlayer player, long gameTime) {
        if (!CURING.contains(player.getUUID())) {
            return;
        }
        Wound wound = WOUNDS.get(player.getUUID());
        if (wound == null) {
            CURING.remove(player.getUUID());
            return;
        }

        int needed = needed(player, wound);
        int progress = CURE_PROGRESS.getOrDefault(player.getUUID(), 0) + 1;
        if (progress < needed) {
            CURE_PROGRESS.put(player.getUUID(), progress);
            if (gameTime % 2L == 0L) {
                sendCure(player, progress, needed);
            }
            if (gameTime % 10L == 0L) {
                player.displayClientMessage(Component.translatable("bloodbound.message.bleeding_dressing",
                        (needed - progress + 19) / 20), true);
            }
            return;
        }

        player.removeEffect(ModEffects.BLEEDING);
        player.displayClientMessage(Component.translatable("bloodbound.message.bleeding_dressed")
                .withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE,
                SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    private static void send(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        Wound wound = WOUNDS.get(player.getUUID());
        PacketDistributor.sendToPlayer(player, new EffectChargesPayload(
                ModEffects.BLEEDING.getKey().location(),
                wound == null ? 0 : wound.charges(),
                wound == null ? 0 : wound.maxCharges()));
    }

    private static void sendCure(ServerPlayer player, int progress, int needed) {
        PacketDistributor.sendToPlayer(player, new EffectChargesPayload(CURE_BAR_ID, progress, needed));
    }

    /** Forgets an entity entirely, on logout or death. */
    public static void clear(UUID entityId) {
        WOUNDS.remove(entityId);
        CURING.remove(entityId);
        CURE_PROGRESS.remove(entityId);
        RUN_TICKS.remove(entityId);
    }
}
