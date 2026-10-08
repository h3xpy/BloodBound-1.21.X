package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.entity.SanctumBubbleEntity;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Holy Sanctum: a bubble left where the player stood. Nothing gets in from outside — no blow, no
 * shot, no one — while whatever was inside when it went up may leave, though not come back.
 * <p>
 * Its health is spent by what it stops, and it breaks when that runs out or its time does. The
 * cooldown only starts then; until it does, the perk is held on a cooldown long enough to cover the
 * bubble's whole life, so a bubble whose owner logs off still leaves them a sensible wait.
 */
public final class HolySanctum {

    /** One bubble standing. */
    private static final class Bubble {
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final int tier;
        private final Vec3 center;
        private final double radius;
        private final float maxHealth;
        private float health;
        private int ticksLeft;
        /** Whatever was inside when it went up, and has not left since. */
        private final Set<UUID> insiders = new HashSet<>();
        /** The shell everybody sees. */
        @Nullable
        private SanctumBubbleEntity visual;

        private Bubble(ServerPlayer owner, int tier) {
            this.ownerId = owner.getUUID();
            this.dimension = owner.level().dimension();
            this.tier = tier;
            this.center = owner.position().add(0.0D, owner.getBbHeight() / 2.0D, 0.0D);
            this.radius = ModPerks.HOLY_SANCTUM.value(ModPerks.SANCTUM_RADIUS, tier);
            this.maxHealth = (float) ModPerks.HOLY_SANCTUM.value(ModPerks.SANCTUM_HEALTH, tier);
            this.health = maxHealth;
            this.ticksLeft = ModPerks.HOLY_SANCTUM.ticks(ModPerks.SANCTUM_DURATION, tier);
        }

        private boolean contains(Vec3 point) {
            return point.distanceToSqr(center) < radius * radius;
        }

        private boolean contains(Entity entity) {
            return contains(entity.getBoundingBox().getCenter());
        }

        private AABB area() {
            return new AABB(center, center).inflate(radius + ModPerks.SANCTUM_WATCH_MARGIN);
        }
    }

    private static final DustParticleOptions SHELL_HIT = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.85F), 1.6F);
    /** Points drawn on the shell each time it is drawn. */
    private static final int SHELL_POINTS = 70;
    /** The golden angle, which spreads points evenly over a sphere. */
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));

    private static final List<Bubble> BUBBLES = new ArrayList<>();
    /** Players whose inventory has to be shown the block they were not allowed to place. */
    private static final Set<ServerPlayer> RESYNC = new HashSet<>();

    private HolySanctum() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        Bubble bubble = new Bubble(player, tier);
        for (Entity inside : player.serverLevel().getEntitiesOfClass(Entity.class, bubble.area(), bubble::contains)) {
            bubble.insiders.add(inside.getUUID());
        }
        BUBBLES.add(bubble);
        bubble.visual = new SanctumBubbleEntity(player.serverLevel(), bubble.center, bubble.radius);
        player.serverLevel().addFreshEntity(bubble.visual);

        // Held for the bubble's whole life; the real cooldown is set when it breaks.
        data.setCooldown(ModPerks.HOLY_SANCTUM.id(), gameTime,
                bubble.ticksLeft + ModPerks.HOLY_SANCTUM.cooldownTicks(tier));
        sendHealth(player, bubble);

        ServerLevel level = player.serverLevel();
        level.playSound(null, bubble.center.x, bubble.center.y, bubble.center.z, SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    /** Steps every bubble standing. Called once a tick for the whole server. */
    public static void tick(MinecraftServer server) {
        if (!RESYNC.isEmpty()) {
            RESYNC.forEach(PerkDataManager::syncInventory);
            RESYNC.clear();
        }
        if (BUBBLES.isEmpty()) {
            return;
        }
        Iterator<Bubble> iterator = BUBBLES.iterator();
        while (iterator.hasNext()) {
            Bubble bubble = iterator.next();
            ServerLevel level = server.getLevel(bubble.dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (--bubble.ticksLeft <= 0) {
                iterator.remove();
                shatter(server, level, bubble);
                continue;
            }
            guard(level, bubble);
            if (bubble.health <= 0.0F) {
                iterator.remove();
                shatter(server, level, bubble);
                continue;
            }
        }
    }

    /** Turns back whatever is on its way in, and lets go of whatever has left. */
    private static void guard(ServerLevel level, Bubble bubble) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, bubble.area())) {
            if (entity.isSpectator() || entity.isRemoved()) {
                continue;
            }
            if (entity instanceof Projectile projectile) {
                stopProjectile(level, bubble, projectile);
                continue;
            }
            if (!(entity instanceof LivingEntity living)) {
                // Primed TNT, falling blocks, thrown items, boats and minecarts: none of them is a
                // shot or a creature, and they all used to sail straight in.
                if (!(entity instanceof SanctumBubbleEntity) && !(entity instanceof ExperienceOrb)
                        && !bubble.insiders.contains(entity.getUUID())) {
                    bounceOff(bubble, entity);
                }
                continue;
            }
            if (living.isPassenger()) {
                continue;
            }
            boolean inside = bubble.contains(living);
            if (bubble.insiders.contains(living.getUUID())) {
                if (!inside) {
                    // Out is out: whatever leaves cannot come back in.
                    bubble.insiders.remove(living.getUUID());
                }
                continue;
            }
            if (inside) {
                pushOut(bubble, living);
            }
        }
    }

    /**
     * Pushes an intruder back out through the shell. Always by its velocity: players move on their
     * own client, and setting them somewhere every tick would only make them stutter.
     */
    private static void pushOut(Bubble bubble, LivingEntity living) {
        Vec3 away = living.getBoundingBox().getCenter().subtract(bubble.center);
        Vec3 direction = away.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
        living.setDeltaMovement(direction.scale(ModPerks.SANCTUM_PUSH_SPEED));
        living.hurtMarked = true;
    }

    /**
     * Something that is neither a creature nor a shot crossed the shell this tick, from outside: it
     * is put back where it was a tick ago and sent off the shell the way it came, like a ball off a
     * wall. Checked along the whole of the tick's movement, so a fast one cannot skip through.
     * Whatever was already inside, or appeared there, is left alone.
     */
    private static void bounceOff(Bubble bubble, Entity entity) {
        Vec3 before = new Vec3(entity.xo, entity.yo, entity.zo);
        Vec3 now = entity.position();
        if (bubble.contains(before) || !(bubble.contains(now) || crossesShell(bubble, before, now))) {
            return;
        }
        Vec3 normal = before.subtract(bubble.center);
        normal = normal.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : normal.normalize();
        Vec3 motion = entity.getDeltaMovement();
        double inward = motion.dot(normal);
        Vec3 reflected = inward < 0.0D ? motion.subtract(normal.scale(2.0D * inward)) : motion;
        entity.setPos(before);
        entity.setDeltaMovement(reflected.scale(0.5D).add(normal.scale(0.1D)));
        entity.hasImpulse = true;
    }

    /** A shot that is crossing into the bubble, or will this tick, stops at the shell. */
    private static void stopProjectile(ServerLevel level, Bubble bubble, Projectile projectile) {
        Vec3 now = projectile.position();
        Vec3 before = new Vec3(projectile.xo, projectile.yo, projectile.zo);
        Vec3 next = now.add(projectile.getDeltaMovement());
        boolean enteredNow = !bubble.contains(before) && bubble.contains(now);
        boolean entersNext = !bubble.contains(now) && crossesShell(bubble, now, next);
        if (!enteredNow && !entersNext) {
            return;
        }
        float damage = projectile instanceof AbstractArrow arrow
                ? (float) Mth.ceil(arrow.getDeltaMovement().length() * arrow.getBaseDamage())
                : ModPerks.SANCTUM_OTHER_PROJECTILE_DAMAGE;
        projectile.discard();
        hit(level, bubble, damage, now);
    }

    /** Whether a straight line from outside the bubble reaches into it. */
    private static boolean crossesShell(Bubble bubble, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-8D) {
            return false;
        }
        double along = Mth.clamp(bubble.center.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return bubble.contains(from.add(segment.scale(along)));
    }

    /**
     * Blows from outside never reach anybody inside: the bubble takes them instead. Where a blow
     * came from is where its attacker stands — a bow fired from outside counts as outside even once
     * its arrow is in — or, with nobody to blame, where it came from. Damage with no origin at all
     * (falling, burning, poison) is nothing a wall could stop.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (BUBBLES.isEmpty() || event.getEntity().level().isClientSide()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Vec3 origin = origin(event.getSource());
        if (origin == null) {
            return;
        }
        for (Bubble bubble : BUBBLES) {
            if (bubble.dimension == victim.level().dimension() && bubble.contains(victim) && !bubble.contains(origin)) {
                event.setCanceled(true);
                hit((ServerLevel) victim.level(), bubble, event.getAmount(), victim.getBoundingBox().getCenter());
                return;
            }
        }
    }

    // --- the blocks inside are out of reach from outside ---

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (shields(event.getEntity(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            resync(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (shields(event.getEntity(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && shields(event.getPlayer(), level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Clicking a block outside can still put one inside; that is turned away as well. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && event.getEntity() != null
                && shields(event.getEntity(), level, event.getPos())) {
            event.setCanceled(true);
            resync(event.getEntity());
        }
    }

    /**
     * A refused placement keeps its block on the server, but the client took it out of the hand the
     * moment it clicked and nothing tells it otherwise: without this the block looked consumed. The
     * whole inventory goes once the tick is over, when the server has put the stack back.
     */
    private static void resync(@Nullable Entity entity) {
        if (entity instanceof ServerPlayer player) {
            RESYNC.add(player);
        }
    }

    /** An explosion outside leaves the blocks inside standing. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (BUBBLES.isEmpty() || event.getLevel().isClientSide()) {
            return;
        }
        Vec3 blast = event.getExplosion().center();
        for (Bubble bubble : BUBBLES) {
            if (bubble.dimension == event.getLevel().dimension() && !bubble.contains(blast)) {
                event.getAffectedBlocks().removeIf(pos -> bubble.contains(Vec3.atCenterOf(pos)));
                event.getAffectedEntities().removeIf(bubble::contains);
            }
        }
    }

    /**
     * Whether a bubble stands between this actor and this block: the block inside one, the actor
     * outside it. Only the server keeps the bubbles, so the client never refuses on its own and the
     * server's refusal puts back whatever it guessed.
     */
    private static boolean shields(Entity actor, Level level, BlockPos pos) {
        if (BUBBLES.isEmpty() || level.isClientSide() || actor == null) {
            return false;
        }
        Vec3 block = Vec3.atCenterOf(pos);
        for (Bubble bubble : BUBBLES) {
            if (bubble.dimension == level.dimension() && bubble.contains(block) && !bubble.contains(actor)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Vec3 origin(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getSourcePosition() != null) {
            return source.getSourcePosition();
        }
        Entity attacker = source.getEntity();
        if (attacker != null) {
            return attacker.getBoundingBox().getCenter();
        }
        return source.getSourcePosition();
    }

    /**
     * A player swung at the shell — anybody, the one who raised it included. The blow is worth what
     * their hand is worth at that moment, attack cooldown and all, and only lands within their reach
     * of the surface.
     */
    public static void strike(ServerPlayer player, SanctumBubbleEntity visual) {
        Bubble bubble = null;
        for (Bubble candidate : BUBBLES) {
            if (candidate.visual == visual) {
                bubble = candidate;
                break;
            }
        }
        if (bubble == null || player.level() != visual.level() || player.isSpectator()) {
            return;
        }
        Vec3 eyes = player.getEyePosition();
        double fromSurface = Math.abs(eyes.distanceTo(bubble.center) - bubble.radius);
        if (fromSurface > player.entityInteractionRange() + 1.0D) {
            return;
        }
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * player.getAttackStrengthScale(0.5F);
        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, true);
        if (damage > 0.0F) {
            hit((ServerLevel) player.level(), bubble, damage, eyes.add(player.getLookAngle().scale(bubble.radius)));
        }
    }

    private static void hit(ServerLevel level, Bubble bubble, float damage, Vec3 near) {
        bubble.health -= damage;
        Vec3 towards = near.subtract(bubble.center);
        if (bubble.visual != null) {
            bubble.visual.onHit(towards, Math.max(0.0F, bubble.health) / bubble.maxHealth);
        }
        Vec3 onShell = towards.lengthSqr() < 1.0E-4D
                ? bubble.center
                : bubble.center.add(towards.normalize().scale(bubble.radius));
        level.sendParticles(SHELL_HIT, onShell.x, onShell.y, onShell.z, 12, 0.25D, 0.25D, 0.25D, 0.0D);
        level.playSound(null, onShell.x, onShell.y, onShell.z, SoundEvents.AMETHYST_BLOCK_HIT,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bubble.ownerId);
        if (owner != null && bubble.health > 0.0F) {
            sendHealth(owner, bubble);
        }
    }

    /** The bubble is gone, worn through or run out: now the cooldown starts. */
    private static void shatter(MinecraftServer server, ServerLevel level, Bubble bubble) {
        if (bubble.visual != null) {
            bubble.visual.discard();
        }
        drawShell(level, bubble, SHELL_HIT, SHELL_POINTS);
        level.sendParticles(ParticleTypes.END_ROD, bubble.center.x, bubble.center.y, bubble.center.z,
                30, bubble.radius * 0.5D, bubble.radius * 0.5D, bubble.radius * 0.5D, 0.05D);
        level.playSound(null, bubble.center.x, bubble.center.y, bubble.center.z, SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 1.2F, 0.8F);

        ServerPlayer owner = server.getPlayerList().getPlayer(bubble.ownerId);
        if (owner == null) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(owner);
        data.setCooldown(ModPerks.HOLY_SANCTUM.id(), owner.level().getGameTime(),
                ModPerks.HOLY_SANCTUM.cooldownTicks(bubble.tier));
        PerkDataManager.sync(owner);
        PacketDistributor.sendToPlayer(owner, new PerkChargesPayload(ModPerks.HOLY_SANCTUM.id(), -1, 0, 0L));
    }

    /** The bubble's health, shown on the owner's HUD as the perk's charges. */
    private static void sendHealth(ServerPlayer owner, Bubble bubble) {
        PacketDistributor.sendToPlayer(owner, new PerkChargesPayload(ModPerks.HOLY_SANCTUM.id(),
                Mth.ceil(bubble.health), Mth.ceil(bubble.maxHealth), 0L));
    }

    private static void drawShell(ServerLevel level, Bubble bubble, DustParticleOptions dust, int points) {
        // A fresh twist each time, so the points do not sit on the same spots and read as a mesh.
        double twist = level.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < points; i++) {
            double y = 1.0D - 2.0D * (i + 0.5D) / points;
            double ring = Math.sqrt(1.0D - y * y);
            double angle = GOLDEN_ANGLE * i + twist;
            level.sendParticles(dust,
                    bubble.center.x + Math.cos(angle) * ring * bubble.radius,
                    bubble.center.y + y * bubble.radius,
                    bubble.center.z + Math.sin(angle) * ring * bubble.radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Forgets every bubble, when the server stops. */
    public static void clear() {
        for (Bubble bubble : BUBBLES) {
            if (bubble.visual != null) {
                bubble.visual.discard();
            }
        }
        BUBBLES.clear();
    }
}
