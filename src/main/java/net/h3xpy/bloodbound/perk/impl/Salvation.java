package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.entity.PentagramEntity;
import net.h3xpy.bloodbound.network.SalvationBindPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Salvation: hold still with the key down to draw a pentagram on the ground. Once drawn, everything
 * standing in it but the one who drew it is bound in chains for a while: no weapon, no item.
 * <p>
 * Bound means: a player cannot attack, use or eat anything, place anything or shoot; a mob cannot
 * hurt anything at all or loose a projectile. Being bound is told to every client that can see the
 * bound thing, so the chains are drawn round it, and to a bound player's own client, which then
 * refuses the attack and use keys before they are even sent.
 */
public final class Salvation {

    /** The pentagram being set up by a player holding still. */
    private static final class Setup {
        private final int tier;
        private final Vec3 origin;
        private int ticksHeld;

        private Setup(int tier, Vec3 origin) {
            this.tier = tier;
            this.origin = origin;
        }
    }

    /** A pentagram being drawn, and when it binds. */
    private record Cast(UUID ownerId, ResourceKey<Level> dimension, Vec3 centre, double radius, int bindTicks,
            long bindsAt) {}

    private static final Map<UUID, Setup> SETTING = new HashMap<>();
    private static final List<Cast> CASTS = new ArrayList<>();
    /** Everything bound, and until when. */
    private static final Map<UUID, Long> BOUND = new HashMap<>();

    private Salvation() {}

    // --- the key ---

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.SALVATION);
        if (!holding || tier <= 0) {
            if (SETTING.remove(player.getUUID()) != null) {
                player.displayClientMessage(Component.translatable("bloodbound.message.salvation_stopped")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return;
        }
        if (data.isOnCooldown(ModPerks.SALVATION.id(), player.level().getGameTime())) {
            return;
        }
        SETTING.putIfAbsent(player.getUUID(), new Setup(tier, player.position()));
    }

    /** Nothing: the hold does the work. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    /** Counts the hold down, and draws the pentagram once it is done. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        Setup setup = SETTING.get(player.getUUID());
        if (setup == null) {
            return;
        }
        if (!player.isAlive() || data.getActiveTier(ModPerks.SALVATION) <= 0) {
            SETTING.remove(player.getUUID());
            return;
        }
        // Sideways only: sinking into snow or bobbing on a block is not walking away.
        if (player.position().subtract(setup.origin).horizontalDistanceSqr()
                > ModPerks.BARBED_STILL_EPSILON * ModPerks.BARBED_STILL_EPSILON * 400.0D) {
            SETTING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("bloodbound.message.salvation_moved")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        setup.ticksHeld++;
        if (setup.ticksHeld % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.1D,
                    player.getZ(), 3, 0.4D, 0.02D, 0.4D, 0.0D);
        }
        if (setup.ticksHeld % 5 == 0) {
            player.displayClientMessage(Component.translatable("bloodbound.message.salvation_setting",
                    Math.max(1, (ModPerks.SALVATION_SETUP_TICKS - setup.ticksHeld + 19) / 20)), true);
        }
        if (setup.ticksHeld >= ModPerks.SALVATION_SETUP_TICKS) {
            SETTING.remove(player.getUUID());
            draw(player, data, setup.tier, gameTime);
        }
    }

    private static void draw(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        ServerLevel level = player.serverLevel();
        float radius = (float) ModPerks.SALVATION.value(ModPerks.SALVATION_RADIUS, tier);
        level.addFreshEntity(new PentagramEntity(level, player.getX(), player.getY(), player.getZ(), radius));
        CASTS.add(new Cast(player.getUUID(), level.dimension(), player.position(), radius,
                ModPerks.SALVATION.ticks(ModPerks.SALVATION_BIND, tier), gameTime + ModPerks.SALVATION_DRAW_TICKS));
        data.setCooldown(ModPerks.SALVATION.id(), gameTime, ModPerks.SALVATION.cooldownTicks(tier));
        PerkDataManager.sync(player);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    // --- the bind ---

    /** Binds what stands in each finished pentagram, and lets go of what has served its time. */
    public static void tickServer(MinecraftServer server) {
        if (!CASTS.isEmpty()) {
            Iterator<Cast> iterator = CASTS.iterator();
            while (iterator.hasNext()) {
                Cast cast = iterator.next();
                ServerLevel level = server.getLevel(cast.dimension());
                if (level == null) {
                    iterator.remove();
                } else if (level.getGameTime() >= cast.bindsAt()) {
                    iterator.remove();
                    bind(level, cast);
                }
            }
        }
        if (!BOUND.isEmpty()) {
            long now = server.overworld().getGameTime();
            BOUND.values().removeIf(until -> now >= until);
        }
    }

    private static void bind(ServerLevel level, Cast cast) {
        Vec3 c = cast.centre();
        double r = cast.radius();
        AABB area = new AABB(c.x - r, c.y - ModPerks.SALVATION_HEIGHT, c.z - r, c.x + r, c.y + ModPerks.SALVATION_HEIGHT, c.z + r);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5F, 0.6F);
        int bound = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target.getUUID().equals(cast.ownerId()) || !target.isAlive() || target.isSpectator()
                    || target instanceof ArmorStand
                    || target.position().subtract(c).horizontalDistanceSqr() > r * r) {
                continue;
            }
            BOUND.put(target.getUUID(), level.getGameTime() + cast.bindTicks());
            if (target instanceof Player player) {
                player.stopUsingItem();
            }
            if (target instanceof Mob mob) {
                mob.setTarget(null);
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new SalvationBindPayload(target.getId(), cast.bindTicks()));
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + target.getBbHeight() / 2.0D,
                    target.getZ(), 15, 0.3D, 0.4D, 0.3D, 0.1D);
            level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 0.7F);
            if (target instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("bloodbound.message.salvation_bound",
                        cast.bindTicks() / 20).withStyle(ChatFormatting.DARK_RED), true);
            }
            bound++;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(cast.ownerId());
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("bloodbound.message.salvation_cast", bound)
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Whether this entity is bound right now, on the server. */
    public static boolean isBound(Entity entity) {
        Long until = BOUND.get(entity.getUUID());
        return until != null && entity.level().getGameTime() < until;
    }

    // --- what being bound forbids ---

    /**
     * Whether this is a weapon: anything that adds to its holder's attack damage (a sword, an axe, a
     * trident, a mace…). A bound player may still hit with bare hands or with anything else.
     */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value() && entry.modifier().amount() > 0.0D) {
                return true;
            }
        }
        return false;
    }

    /** A blow with a weapon is refused; bare hands, or anything that is not a weapon, still land. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isBound(event.getEntity()) && isWeapon(event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() && isBound(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Blocks may still be opened (a door, a chest); nothing in hand may be used on them. */
    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() && isBound(event.getEntity())) {
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onUseOnEntity(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide() && isBound(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Eating, drinking, drawing a bow, raising a shield. */
    @SubscribeEvent
    public static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (!event.getEntity().level().isClientSide() && isBound(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Nothing a bound thing shoots or throws leaves its hands. */
    @SubscribeEvent
    public static void onProjectile(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && !event.loadedFromDisk()
                && event.getEntity() instanceof Projectile projectile
                && projectile.getOwner() != null && isBound(projectile.getOwner())) {
            event.setCanceled(true);
        }
    }

    /** A bound mob hurts nothing: no bite, no blast. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Mob mob && isBound(mob)) {
            event.setCanceled(true);
        }
    }

    /** Somebody coming within sight of a bound thing is told, or they would not see the chains. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Long until = BOUND.get(event.getTarget().getUUID());
        if (until != null && event.getEntity() instanceof ServerPlayer watcher) {
            long left = until - event.getTarget().level().getGameTime();
            if (left > 0) {
                PacketDistributor.sendToPlayer(watcher, new SalvationBindPayload(event.getTarget().getId(), (int) left));
            }
        }
    }

    /** Forgets a player: the hold in hand, and their chains. */
    public static void clear(ServerPlayer player) {
        SETTING.remove(player.getUUID());
        if (BOUND.remove(player.getUUID()) != null && player.connection != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SalvationBindPayload(player.getId(), 0));
        }
    }
}
