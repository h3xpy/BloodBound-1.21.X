package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.ReactiveCompoundPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Reactive Compound: three seconds of holding the key, weapon in hand and no swinging, pour a vial
 * over the blade. The next thing that blade hits takes more damage and is thrown harder; a player
 * also has every perk turned off for a while. Only then does the cooldown start.
 * <p>
 * The coat is on that one weapon: putting it away, or swapping it for anything else, loses it. The
 * pouring is drawn on the client ({@code ClientReactiveCompound}); everybody else sees the drops
 * fall from the player's hand.
 */
public final class ReactiveCompound {

    private static final DustParticleOptions COMPOUND = new DustParticleOptions(new Vector3f(0.7F, 0.25F, 0.95F), 0.9F);

    /** A pouring under way: the weapon it is going onto, and the tick it started. */
    private record Pouring(ItemStack weapon, long since) {}

    private static final Map<UUID, Pouring> COATING = new HashMap<>();
    /** Coated weapons, by owner: the very stack that was coated. */
    private static final Map<UUID, ItemStack> COATED = new HashMap<>();
    /** What the coated hit just struck, and the tick it did: every knockback that tick is harder. */
    private static final Map<UUID, Long> STRUCK = new HashMap<>();

    private ReactiveCompound() {}

    /** The press alone does nothing: the coating is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    /** A melee weapon: what the compound can be poured on. */
    public static boolean isMeleeWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof MaceItem;
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        UUID id = player.getUUID();
        if (!holding) {
            if (COATING.remove(id) != null) {
                send(player, phaseOf(id));
                player.displayClientMessage(Component.translatable("bloodbound.message.reactive_compound_stopped")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        long gameTime = player.level().getGameTime();
        if (data.getActiveTier(ModPerks.REACTIVE_COMPOUND) <= 0 || data.isOnCooldown(ModPerks.REACTIVE_COMPOUND.id(), gameTime)
                || COATED.containsKey(id) || COATING.containsKey(id)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!isMeleeWeapon(weapon)) {
            player.displayClientMessage(Component.translatable("bloodbound.message.reactive_compound_no_weapon")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        COATING.put(id, new Pouring(weapon, gameTime));
        send(player, ReactiveCompoundPayload.COATING);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    /** Runs the pouring, watches the coated weapon, keeps it dripping. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        UUID id = player.getUUID();
        STRUCK.values().removeIf(at -> gameTime > at);
        if (data.getActiveTier(ModPerks.REACTIVE_COMPOUND) <= 0) {
            // Off for now: the pouring stops. A coat already on stays, unless the perk left the loadout.
            boolean changed = COATING.remove(id) != null;
            if (!data.hasEquipped(ModPerks.REACTIVE_COMPOUND)) {
                changed |= COATED.remove(id) != null;
            }
            if (changed) {
                send(player, phaseOf(id));
            }
            return;
        }

        // A different item in hand, or none: the weapon was put away, and its coat with it.
        ItemStack coated = COATED.get(id);
        if (coated != null && player.getMainHandItem() != coated) {
            COATED.remove(id);
            send(player, ReactiveCompoundPayload.NONE);
            player.displayClientMessage(Component.translatable("bloodbound.message.reactive_compound_lost")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        Pouring pouring = COATING.get(id);
        if (pouring != null) {
            if (!player.isAlive() || player.getMainHandItem() != pouring.weapon()) {
                COATING.remove(id);
                send(player, ReactiveCompoundPayload.NONE);
                return;
            }
            long held = gameTime - pouring.since();
            // The drops everybody else sees, in the pouring part of the three seconds.
            if (held > ModPerks.REACTIVE_COAT_TICKS / 4 && held < ModPerks.REACTIVE_COAT_TICKS * 4 / 5 && held % 3 == 0) {
                Vec3 hand = handPosition(player);
                player.serverLevel().sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR,
                        hand.x, hand.y + 0.4D, hand.z, 1, 0.05D, 0.0D, 0.05D, 0.0D);
            }
            if (held % 20 == 0) {
                player.displayClientMessage(Component.translatable("bloodbound.message.reactive_compound_coating",
                        Math.max(1, (ModPerks.REACTIVE_COAT_TICKS - held + 19) / 20)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            if (held >= ModPerks.REACTIVE_COAT_TICKS) {
                COATING.remove(id);
                COATED.put(id, pouring.weapon());
                send(player, ReactiveCompoundPayload.COATED);
                player.level().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW,
                        SoundSource.PLAYERS, 0.8F, 1.3F);
                player.displayClientMessage(Component.translatable("bloodbound.message.reactive_compound_coated")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return;
        }

        if (coated != null && gameTime % 8 == 0) {
            Vec3 hand = handPosition(player);
            player.serverLevel().sendParticles(COMPOUND, hand.x, hand.y + 0.2D, hand.z, 1, 0.08D, 0.1D, 0.08D, 0.0D);
        }
    }

    private static int phaseOf(UUID id) {
        return COATED.containsKey(id) ? ReactiveCompoundPayload.COATED : ReactiveCompoundPayload.NONE;
    }

    /** Roughly where the main hand is, for the drops others see. */
    private static Vec3 handPosition(ServerPlayer player) {
        double side = player.getMainArm() == HumanoidArm.RIGHT ? -1.0D : 1.0D;
        double yaw = Math.toRadians(player.yBodyRot);
        return player.position().add(Math.cos(yaw) * 0.35D * side - Math.sin(yaw) * 0.3D, 1.0D,
                Math.sin(yaw) * 0.35D * side + Math.cos(yaw) * 0.3D);
    }

    private static void send(ServerPlayer player, int phase) {
        PacketDistributor.sendToPlayer(player, new ReactiveCompoundPayload(phase));
    }

    /** No swinging while the vial is out. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && COATING.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * The coated weapon landing a blow, on anything: more damage, a harder throw, and on a player
     * every perk off for a while. The coat is spent and the cooldown starts.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer attacker) || attacker == victim
                || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        ItemStack coated = COATED.get(attacker.getUUID());
        PlayerPerkData data = PerkDataManager.get(attacker);
        int tier = data.getActiveTier(ModPerks.REACTIVE_COMPOUND);
        if (coated == null || attacker.getMainHandItem() != coated || tier <= 0) {
            return;
        }
        COATED.remove(attacker.getUUID());
        send(attacker, ReactiveCompoundPayload.NONE);

        long gameTime = attacker.level().getGameTime();
        event.setAmount(event.getAmount()
                * (float) (1.0D + ModPerks.REACTIVE_COMPOUND.value(ModPerks.REACTIVE_DAMAGE, tier) / 100.0D));
        STRUCK.put(victim.getUUID(), gameTime);
        data.setCooldown(ModPerks.REACTIVE_COMPOUND.id(), gameTime, ModPerks.REACTIVE_COMPOUND.cooldownTicks(tier));
        PerkDataManager.sync(attacker);

        victim.level().playSound(null, victim.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE,
                SoundSource.PLAYERS, 0.6F, 1.6F);
        attacker.serverLevel().sendParticles(COMPOUND, victim.getX(), victim.getY() + victim.getBbHeight() / 2.0D,
                victim.getZ(), 25, 0.35D, 0.5D, 0.35D, 0.0D);

        if (!(victim instanceof ServerPlayer struck)) {
            return;
        }
        int ticks = ModPerks.REACTIVE_COMPOUND.ticks(ModPerks.REACTIVE_DISABLE, tier);
        PerkDataManager.get(struck).disablePerks(gameTime, ticks);
        PerkDataManager.sync(struck);
        String seconds = String.format("%.1f", ticks / 20.0D);
        struck.displayClientMessage(Component.translatable("bloodbound.message.reactive_compound_hit_victim", seconds)
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        attacker.displayClientMessage(Component.translatable("bloodbound.message.reactive_compound_hit",
                struck.getName(), seconds).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Every knockback the coated hit causes, that same tick, is harder. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        Long at = STRUCK.get(event.getEntity().getUUID());
        if (at != null && at == event.getEntity().level().getGameTime()) {
            event.setStrength((float) (event.getStrength() * ModPerks.REACTIVE_KNOCKBACK));
        }
    }

    /** A death: the pouring and the coat are both gone, and the client stops drawing them at once. */
    public static void cancel(ServerPlayer player) {
        boolean had = COATING.remove(player.getUUID()) != null;
        had |= COATED.remove(player.getUUID()) != null;
        if (had) {
            send(player, ReactiveCompoundPayload.NONE);
        }
    }

    /** Forgets a player's pouring and coating, on logout. */
    public static void clear(UUID playerId) {
        COATING.remove(playerId);
        COATED.remove(playerId);
    }
}
