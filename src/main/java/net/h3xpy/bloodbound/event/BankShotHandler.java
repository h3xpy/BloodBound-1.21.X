package net.h3xpy.bloodbound.event;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.client.ClientBankShots;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.network.BankShotMarkPayload;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Bank Shot: anything you throw comes off the first wall it meets, and looks for something to hit
 * on the way out.
 * <p>
 * The bounce is stamped onto the projectile as it is fired, so it keeps working even if the shooter
 * unequips the perk, dies or logs out while it is still in the air. The perks' own shots are not
 * entities and cannot be stamped, so they ask {@link #spendBounce}, {@link #seek} and
 * {@link #steer} directly.
 * <p>
 * What makes a bounce predictable is that the shot goes where the player would expect: only at
 * something it can actually see, onto whatever sits closest to the line it came off the wall on,
 * and — for anything gravity pulls on — aimed high enough to still arrive.
 */
public final class BankShotHandler {

    private static final String KEY_BOUNCES = "BloodBoundBankShot";
    /** Ticks left of watching a bounced arrow, and the heading it left the wall on. */
    private static final String KEY_WATCH = "BloodBoundBankWatch";
    /** Ticks of forced client sync still owed after a bounce. */
    private static final String KEY_SYNC = "BloodBoundBankSync";
    /** Who fired it, so the addons that change a bounce can be read off their owner. */
    private static final String KEY_OWNER = "BloodBoundBankOwner";
    /** How many walls it has come off, which Origami Crane prices the hit by. */
    private static final String KEY_HITS = "BloodBoundBankHits";
    /** Set on a shot Paper Fan has taken control of. */
    private static final String KEY_PAPER = "BloodBoundBankPaper";
    private static final String KEY_HEADING_X = "BloodBoundBankX";
    private static final String KEY_HEADING_Y = "BloodBoundBankY";
    private static final String KEY_HEADING_Z = "BloodBoundBankZ";

    /**
     * How far out of the wall the shot is stood. Vanilla decides an arrow is stuck by testing where
     * it ended its tick against the block's own shape, so anything less than its own half-width
     * risks the bounce being read as a landing.
     */
    private static final double CLEARANCE = 0.35D;
    /** How many times the clearance is stepped out when the first attempt is still inside something. */
    private static final int CLEARANCE_ATTEMPTS = 4;
    /**
     * How long a bounced shot is watched for vanilla deciding it landed after all. Short on purpose:
     * the only landing worth undoing is the one caused by ending the bounce tick inside the wall.
     */
    private static final int WATCH_TICKS = 2;
    /**
     * Ticks the client is handed the shot's real state after a bounce. One is enough when the client
     * turned the same corner; the extra two catch the times it picked a different mark and drifted.
     */
    private static final int SYNC_TICKS = 3;
    /** How far Paper Fan looks down the crosshair for the point to steer the shot onto. */
    private static final double PAPER_FAN_REACH = 96.0D;

    private BankShotHandler() {}

    // --- stamping the shot at launch ---

    @SubscribeEvent
    public static void onProjectileSpawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        // This event also fires for shots read back out of a saved chunk, which would charge the
        // player again for an arrow they loosed days ago. Only a shot on its very first tick, and
        // never one already carrying a stamp, is a shot being fired now.
        CompoundTag tag = projectile.getPersistentData();
        if (projectile.tickCount > 0 || tag.contains(KEY_BOUNCES)) {
            return;
        }
        if (!(projectile.getOwner() instanceof ServerPlayer shooter) || !spendBounce(shooter)) {
            return;
        }
        int bounces = bounceCount(shooter);
        tag.putInt(KEY_BOUNCES, bounces);
        tag.putUUID(KEY_OWNER, shooter.getUUID());

        // Told now rather than at the wall: every client flies its own copy of a projectile, and one
        // that does not know about the bounce runs into the wall, sticks, and jerks backwards when
        // the server drags it out. Knowing in advance, it caroms at the same moment the server does.
        PacketDistributor.sendToPlayersTrackingEntity(projectile,
                new BankShotMarkPayload(projectile.getId(), bounces));
    }

    /**
     * Takes one charge for a bounce, if the player has the perk and a charge to spend.
     *
     * @return true when a bounce was paid for
     */
    public static boolean spendBounce(ServerPlayer player) {
        PlayerPerkData data = PerkDataManager.get(player);
        if (data.getActiveTier(ModPerks.BANK_SHOT) <= 0) {
            return false;
        }
        // Origami Crane throws the meter away entirely: every shot bounces, always.
        if (data.isAddonActive(ModAddons.ORIGAMI_CRANE)) {
            return true;
        }
        int charges = data.bankCharges(ModPerks.BANK_SHOT_CHARGES);
        if (charges <= 0) {
            return false;
        }

        data.setBankCharges(charges - 1);
        sendCharges(player, data, charges - 1);
        PerkDataManager.sync(player);
        return true;
    }

    /** How many bounces a shot leaves the bow with. */
    private static int bounceCount(ServerPlayer shooter) {
        return PerkDataManager.get(shooter).isAddonActive(ModAddons.ORIGAMI_CRANE)
                ? ModAddons.ORIGAMI_BOUNCES
                : ModPerks.BANK_SHOT_BOUNCES;
    }

    // --- the bounce ---

    /**
     * Runs on both sides. The server owns the shot, but the client is simulating a copy of it and
     * has to turn the same corner at the same moment — otherwise it sticks the shot in the wall and
     * the next packet from the server yanks it back out.
     */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)
                || hit.getType() == HitResult.Type.MISS) {
            return;
        }

        boolean client = projectile.level().isClientSide;
        CompoundTag tag = projectile.getPersistentData();
        // Persistent data never reaches the client, so each side reads the bounce from its own
        // record: the tag on the server, the marks it was sent on the client.
        int bounces = client ? ClientBankShots.bounces(projectile.getId()) : tag.getInt(KEY_BOUNCES);
        if (bounces <= 0) {
            return;
        }
        if (client) {
            ClientBankShots.spend(projectile.getId());
        } else {
            tag.putInt(KEY_BOUNCES, bounces - 1);
        }

        // The impact never happens: the shot carries on, off the wall.
        event.setCanceled(true);

        Direction face = hit.getDirection();
        Vec3 heading = reflect(projectile.getDeltaMovement(), face).scale(ModPerks.BANK_SHOT_BOUNCE_SPEED);
        projectile.setPos(clearOf(projectile, hit.getLocation(), face));

        // The addons that change a bounce belong to whoever fired it. The client does not know them,
        // which is fine: it turns the plain corner, and the server hands it the real line a tick on.
        ServerPlayer owner = client ? null : owner(projectile, tag);
        PlayerPerkData ownerData = owner == null ? null : PerkDataManager.get(owner);
        boolean paperFan = ownerData != null && ownerData.isAddonActive(ModAddons.PAPER_FAN);
        boolean origami = ownerData != null && ownerData.isAddonActive(ModAddons.ORIGAMI_CRANE);

        LivingEntity mark = null;
        if (paperFan) {
            // Paper Fan: no gravity from here on, and the tick steers it onto the crosshair.
            projectile.setNoGravity(true);
            tag.putBoolean(KEY_PAPER, true);
        } else if (!origami) {
            double cone = ModPerks.BANK_SHOT_CONE_DEGREES;
            if (ownerData != null && ownerData.isAddonActive(ModAddons.BROKEN_ARROW)) {
                cone *= ModAddons.BROKEN_ARROW_CONE;
            }
            mark = seek(projectile.level(), projectile.position(), heading, projectile.getOwner(), cone);
            if (mark != null) {
                heading = steer(projectile.position(), heading, mark, projectile.getGravity());
            }
        }
        aim(projectile, heading);

        if (client) {
            return;
        }
        tag.putInt(KEY_WATCH, WATCH_TICKS);
        tag.putDouble(KEY_HEADING_X, heading.x);
        tag.putDouble(KEY_HEADING_Y, heading.y);
        tag.putDouble(KEY_HEADING_Z, heading.z);
        // The client is told about the carom once this tick is finished, not here: vanilla still has
        // the rest of the move to run after the impact, so anything sent now is already stale.
        tag.putInt(KEY_SYNC, SYNC_TICKS);
        tag.putInt(KEY_HITS, tag.getInt(KEY_HITS) + 1);

        if (projectile.level() instanceof ServerLevel level) {
            level.playSound(null, projectile.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(),
                    SoundSource.PLAYERS, 0.6F, mark != null || paperFan ? 1.6F : 1.0F);
        }
    }

    /** Whoever fired the shot, if they are still about. */
    @Nullable
    private static ServerPlayer owner(Projectile projectile, CompoundTag tag) {
        if (!tag.hasUUID(KEY_OWNER) || !(projectile.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(tag.getUUID(KEY_OWNER));
    }

    /**
     * What a shot that has already come off a wall does when it finally finds something. Origami
     * Crane prices the blow by the walls behind it; Jagged Head opens a wound; Paper Fan hits a
     * revealed target harder, and reveals whatever it hits.
     */
    @SubscribeEvent
    public static void onBouncedHit(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide
                || !(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }
        CompoundTag tag = projectile.getPersistentData();
        int hits = tag.getInt(KEY_HITS);
        if (hits <= 0) {
            return;
        }

        ServerPlayer shooter = owner(projectile, tag);
        if (shooter == null) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(shooter);

        if (data.isAddonActive(ModAddons.ORIGAMI_CRANE)) {
            event.setAmount(event.getAmount() * (1.0F + hits * ModAddons.ORIGAMI_DAMAGE_PER_BOUNCE));
        }
        if (data.isAddonActive(ModAddons.JAGGED_HEAD)
                && shooter.getRandom().nextFloat() < ModAddons.JAGGED_HEAD_CHANCE) {
            BleedingHandler.apply(target, ModAddons.JAGGED_HEAD_BLEED);
        }
        if (data.isAddonActive(ModAddons.PAPER_FAN)) {
            // Read before revealing, so the first hit reveals and only a second one is worth more.
            if (target.hasEffect(ModEffects.AURA_REVEALED)) {
                event.setAmount(event.getAmount() * ModAddons.PAPER_FAN_REVEALED_MULTIPLIER);
            }
            AuraRevealHandler.reveal(shooter, target, ModAddons.PAPER_FAN_REVEAL_TICKS);
        }
    }

    /**
     * Run once the shot has finished its own tick: keep a freshly bounced arrow flying if vanilla
     * decided it had landed after all, steer a Paper Fan shot, then hand the client the state the
     * shot actually ended the tick on.
     */
    @SubscribeEvent
    public static void onProjectileTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Projectile projectile) || projectile.level().isClientSide) {
            return;
        }
        CompoundTag tag = projectile.getPersistentData();

        int watch = tag.getInt(KEY_WATCH);
        if (watch > 0) {
            tag.putInt(KEY_WATCH, watch - 1);
            // Only a shot that ended up buried in geometry is rescued. An arrow resting against a
            // surface it genuinely flew into is a landing, and undoing that sent arrows through walls.
            if (projectile instanceof AbstractArrow arrow && arrow.inGround && insideBlock(arrow)) {
                arrow.inGround = false;
                arrow.setDeltaMovement(tag.getDouble(KEY_HEADING_X), tag.getDouble(KEY_HEADING_Y),
                        tag.getDouble(KEY_HEADING_Z));
                arrow.hasImpulse = true;
                tag.putInt(KEY_SYNC, Math.max(tag.getInt(KEY_SYNC), 1));
            }
        }

        if (tag.getBoolean(KEY_PAPER)) {
            steerOntoCrosshair(projectile, tag);
        }

        int sync = tag.getInt(KEY_SYNC);
        if (sync <= 0) {
            return;
        }
        tag.putInt(KEY_SYNC, sync - 1);
        if (projectile.level() instanceof ServerLevel level) {
            level.getChunkSource().broadcastAndSend(projectile, new ClientboundTeleportEntityPacket(projectile));
            level.getChunkSource().broadcastAndSend(projectile, new ClientboundSetEntityMotionPacket(projectile));
        }
    }

    /**
     * Paper Fan: bends the shot towards whatever the shooter is looking at, at the speed it already
     * has. The client cannot predict a curve it knows nothing about, so it is told every tick.
     */
    private static void steerOntoCrosshair(Projectile projectile, CompoundTag tag) {
        if (projectile instanceof AbstractArrow arrow && arrow.inGround) {
            tag.remove(KEY_PAPER);
            return;
        }
        ServerPlayer owner = owner(projectile, tag);
        if (owner == null || owner.level() != projectile.level()) {
            return;
        }

        Vec3 eyes = owner.getEyePosition();
        Vec3 far = eyes.add(owner.getLookAngle().scale(PAPER_FAN_REACH));
        HitResult aimed = projectile.level().clip(new ClipContext(eyes, far, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 point = aimed.getType() == HitResult.Type.MISS ? far : aimed.getLocation();

        Vec3 velocity = projectile.getDeltaMovement();
        double speed = Math.max(0.5D, velocity.length());
        Vec3 toward = point.subtract(projectile.position());
        if (toward.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 wanted = toward.normalize().scale(speed);
        Vec3 turned = velocity.add(wanted.subtract(velocity).scale(ModAddons.PAPER_FAN_DRIFT));
        aim(projectile, turned.normalize().scale(speed));
        tag.putInt(KEY_SYNC, Math.max(tag.getInt(KEY_SYNC), 1));
    }

    /** True when the shot is sitting inside something solid rather than up against it. */
    private static boolean insideBlock(Entity entity) {
        return !entity.level().noCollision(AABB.ofSize(entity.position(), 0.02D, 0.02D, 0.02D));
    }

    /**
     * A point outside the block that was struck. The surface point is the start; if the projectile
     * would still be inside something there, it is pushed out again until it is not.
     */
    private static Vec3 clearOf(Projectile projectile, Vec3 surface, Direction face) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        double size = Math.max(0.1D, projectile.getBbWidth());

        Vec3 candidate = surface;
        for (int attempt = 1; attempt <= CLEARANCE_ATTEMPTS; attempt++) {
            candidate = surface.add(normal.scale(CLEARANCE * attempt));
            if (projectile.level().noCollision(AABB.ofSize(candidate, size, size, size))) {
                break;
            }
        }
        return candidate;
    }

    // --- finding and hitting a mark ---

    /** The living thing a bounced shot turns onto, or null. */
    @Nullable
    public static LivingEntity seek(Level level, Vec3 position, Vec3 heading, @Nullable Entity owner) {
        return seek(level, position, heading, owner, ModPerks.BANK_SHOT_CONE_DEGREES);
    }

    /**
     * As above, with the cone named outright — Broken Arrow widens it.
     * <p>
     * Picks whatever sits closest to the shot's own line rather than whatever is nearest: the
     * nearest thing in a wide cone is often off to one side, and a shot that swings hard onto it
     * is exactly what made the bounce feel random. Anything behind a wall, the shooter, and armour
     * stands are never chosen.
     */
    @Nullable
    public static LivingEntity seek(Level level, Vec3 position, Vec3 heading, @Nullable Entity owner,
            double coneDegrees) {
        if (heading.lengthSqr() < 1.0E-8D) {
            return null;
        }
        Vec3 direction = heading.normalize();
        double range = ModPerks.BANK_SHOT_SEEK_RANGE;
        double minimumDot = Math.cos(Math.toRadians(coneDegrees));
        AABB box = AABB.ofSize(position, range * 2, range * 2, range * 2);

        LivingEntity best = null;
        double bestDot = -2.0D;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == owner || !candidate.isAlive() || candidate instanceof ArmorStand
                    || candidate.isSpectator()) {
                continue;
            }
            Vec3 centre = candidate.getBoundingBox().getCenter();
            Vec3 offset = centre.subtract(position);
            double distance = offset.length();
            if (distance > range || distance < 1.0E-4D) {
                continue;
            }
            double dot = offset.scale(1.0D / distance).dot(direction);
            if (dot < minimumDot || dot <= bestDot) {
                continue;
            }
            if (level.clip(new ClipContext(position, centre, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != HitResult.Type.MISS) {
                continue;
            }
            best = candidate;
            bestDot = dot;
        }
        return best;
    }

    /** Turns a heading onto a mark, at the speed it already had, for a shot nothing pulls down. */
    public static Vec3 steer(Vec3 position, Vec3 heading, LivingEntity mark) {
        return steer(position, heading, mark, 0.0D);
    }

    /**
     * As above, for a shot gravity pulls on: it is aimed above the mark by as far as it will have
     * fallen by the time it gets there, which is what stops a steered arrow dropping short.
     */
    public static Vec3 steer(Vec3 position, Vec3 heading, LivingEntity mark, double gravity) {
        double speed = Math.max(1.0E-3D, heading.length());
        Vec3 target = mark.getBoundingBox().getCenter();
        if (gravity > 0.0D) {
            double ticks = target.distanceTo(position) / speed;
            target = target.add(0.0D, 0.5D * gravity * ticks * ticks, 0.0D);
        }
        return target.subtract(position).normalize().scale(speed);
    }

    /** Points the projectile along its new heading, model and all. */
    private static void aim(Projectile projectile, Vec3 heading) {
        projectile.setDeltaMovement(heading);
        projectile.hasImpulse = true;

        double flat = heading.horizontalDistance();
        projectile.setYRot((float) (Mth.atan2(heading.x, heading.z) * (180.0F / Math.PI)));
        projectile.setXRot((float) (Mth.atan2(heading.y, flat) * (180.0F / Math.PI)));
        // Both the previous and current rotation, or vanilla lerps the model round from where the
        // shot was pointing before the wall.
        projectile.yRotO = projectile.getYRot();
        projectile.xRotO = projectile.getXRot();
    }

    public static Vec3 reflect(Vec3 velocity, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(-velocity.x, velocity.y, velocity.z);
            case Y -> new Vec3(velocity.x, -velocity.y, velocity.z);
            case Z -> new Vec3(velocity.x, velocity.y, -velocity.z);
        };
    }

    // --- charges ---

    /** Hands a charge back when one is due. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.BANK_SHOT);
        if (tier <= 0) {
            return;
        }

        // Origami Crane has no charges, so there is nothing to count and nothing to wait for.
        if (data.isAddonActive(ModAddons.ORIGAMI_CRANE)) {
            if (data.getCooldownRemaining(ModPerks.BANK_SHOT.id(), gameTime) > 0) {
                data.setCooldown(ModPerks.BANK_SHOT.id(), gameTime, 0);
                PerkDataManager.sync(player);
            }
            if (gameTime % 20L == 0L) {
                PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.BANK_SHOT.id(), -1, 0, 0L));
            }
            return;
        }

        int max = ModPerks.BANK_SHOT_CHARGES;
        int charges = data.bankCharges(max);
        boolean changed = false;

        // Short of a charge always means a timer running towards the next one, which is what makes
        // them come back one at a time rather than all together.
        if (charges < max && data.bankRechargeAt() <= 0) {
            data.setBankRechargeAt(gameTime + rechargeTicks(data, tier));
            changed = true;
        }

        if (charges < max && data.bankRechargeAt() > 0 && gameTime >= data.bankRechargeAt()) {
            charges++;
            data.setBankCharges(charges);
            data.setBankRechargeAt(charges < max ? gameTime + rechargeTicks(data, tier) : 0L);
            changed = true;
        }

        // Rewritten from the charge count every tick: an empty perk shows the wait for the next
        // charge where its cooldown would go, and one with any left never does.
        int gate = charges <= 0 ? (int) Math.max(1L, data.bankRechargeAt() - gameTime) : 0;
        if (data.getCooldownRemaining(ModPerks.BANK_SHOT.id(), gameTime) != gate) {
            data.setCooldown(ModPerks.BANK_SHOT.id(), gameTime, gate);
            changed = true;
        }

        if (changed || gameTime % 20L == 0L) {
            sendCharges(player, data, charges);
        }
        if (changed) {
            PerkDataManager.sync(player);
        }
    }

    /** How long one charge takes to come back. Slime Goo shortens it. */
    private static int rechargeTicks(PlayerPerkData data, int tier) {
        int ticks = ModPerks.BANK_SHOT.ticks(ModPerks.BANK_SHOT_RECHARGE, tier);
        return data.isAddonActive(ModAddons.SLIME_GOO)
                ? Math.max(1, Math.round(ticks * ModAddons.SLIME_GOO_RECHARGE))
                : ticks;
    }

    private static void sendCharges(ServerPlayer player, PlayerPerkData data, int charges) {
        int max = ModPerks.BANK_SHOT_CHARGES;
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.BANK_SHOT.id(),
                charges, max, charges < max ? data.bankRechargeAt() : 0L));
    }
}
