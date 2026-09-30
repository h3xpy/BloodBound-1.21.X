package net.h3xpy.bloodbound.ritual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.entity.RitualEntity;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.EnhancedPerception;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Rituals: a mark laid on the ground by a perk, working on everything inside a radius around it.
 * <p>
 * A player may have one ritual per perk at a time — laying a second breaks the first — and the
 * laying itself is an unbroken piece of work, which each perk handles its own way. What lives here
 * is everything the rituals share: where they are, who owns them, the marker that shows them, the
 * flames that give them away, and the bookkeeping of who has been standing in one and for how long.
 * <p>
 * Flames tell you what a ritual is for: ordinary fire when it does something to you, soul fire when
 * it does something for you.
 * <p>
 * A ritual outlives its owner's session. Its marker is saved with the chunk it stands in, and the
 * ritual is built back from that marker whenever the chunk loads — after a logout, a restart, or
 * simply the owner walking far enough away. While the chunk is unloaded the ritual is dormant: it
 * still counts as the owner's one ritual for that perk, but it does nothing and cannot be seen.
 */
public final class RitualManager {

    /** How a ritual advertises itself. */
    public enum Mood {
        /** Something is being done to whoever stands here: plain flame. */
        HARMFUL(ParticleTypes.FLAME),
        /** Something is being done for them, or for the one who laid it: soul flame, blue. */
        HELPFUL(ParticleTypes.SOUL_FIRE_FLAME);

        private final ParticleOptions particle;

        Mood(ParticleOptions particle) {
            this.particle = particle;
        }
    }

    /** One ritual on the ground. */
    public static final class Ritual {
        /** The marker's own id, which survives it being saved and read back. */
        private final UUID id;
        private final UUID ownerId;
        private final ResourceLocation perkId;
        private final ResourceKey<Level> dimension;
        private final Vec3 position;
        private final Mood mood;
        private final int tier;
        private final long placedAt;
        /** The marker standing for it now. A fresh one takes its place whenever the chunk reloads. */
        private RitualEntity marker;

        /** The radius it was laid with; a Bad Omen over it widens what {@link #radius} reports. */
        private double radius;
        private float charges;
        private boolean omenBoosted;
        /** Ticks each player has spent inside, however the perk chooses to count them. */
        private final Map<UUID, Integer> insideTicks = new HashMap<>();

        private Ritual(RitualEntity marker) {
            this.id = marker.getUUID();
            this.ownerId = marker.ownerId();
            this.perkId = marker.perkId();
            this.dimension = marker.level().dimension();
            this.position = marker.position();
            this.mood = marker.mood();
            this.tier = marker.tier();
            this.radius = marker.radius();
            this.charges = marker.charges();
            this.omenBoosted = marker.omenBoosted();
            this.placedAt = marker.placedAt();
            this.marker = marker;
        }

        public UUID ownerId() {
            return ownerId;
        }

        public Vec3 position() {
            return position;
        }

        public int tier() {
            return tier;
        }

        public long placedAt() {
            return placedAt;
        }

        /** How far it reaches: the radius it was laid with, widened by a Bad Omen standing over it. */
        public double radius() {
            return (radius + perceptionBonus(this)) * omenWidening(this);
        }

        public void setRadius(double radius) {
            this.radius = radius;
            marker.setRadius(radius);
        }

        public float charges() {
            return charges;
        }

        /** The most charges it can hold. */
        public float capacity() {
            return marker.capacity();
        }

        /** Puts charges back, never past its capacity. */
        public void refill(float amount) {
            charges = Math.min(capacity(), charges + amount);
            marker.setCharges(charges);
        }

        public UUID id() {
            return id;
        }

        /** Spends charges, and says whether any are left. */
        public boolean spend(float amount) {
            charges = Math.max(0.0F, charges - amount);
            marker.setCharges(charges);
            return charges > 0.0F;
        }

        public ResourceLocation perkId() {
            return perkId;
        }

        public RitualEntity marker() {
            return marker;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        /** Standing, and in a loaded chunk: the only state in which a ritual does anything. */
        public boolean isAlive() {
            return !marker.isRemoved();
        }

        /** Out of reach in an unloaded chunk, waiting for it to load again. */
        public boolean isDormant() {
            Entity.RemovalReason reason = marker.getRemovalReason();
            return reason != null && reason.shouldSave();
        }

        /**
         * Whether a position is inside the ritual. Under a Bad Omen that means inside any of the
         * owner's rituals it watches over: they share their circles, so each works in all of them.
         */
        public boolean covers(Vec3 point) {
            if (coversOwn(point)) {
                return true;
            }
            for (Ritual sibling : linked(this)) {
                if (sibling.coversOwn(point)) {
                    return true;
                }
            }
            return false;
        }

        /** Whether a position is inside this ritual's own circle. */
        public boolean coversOwn(Vec3 point) {
            double reach = radius();
            return point.distanceToSqr(position) <= reach * reach;
        }

        /** Counts another tick inside for this player, or forgets them when they step out. */
        public int trackInside(UUID playerId, boolean inside, boolean consecutive) {
            if (!inside) {
                if (consecutive) {
                    insideTicks.remove(playerId);
                    return 0;
                }
                return insideTicks.getOrDefault(playerId, 0);
            }
            int ticks = insideTicks.getOrDefault(playerId, 0) + 1;
            insideTicks.put(playerId, ticks);
            return ticks;
        }

        /** Everything alive inside the ritual right now, its Bad Omen siblings' circles included. */
        public List<LivingEntity> occupants(ServerLevel level) {
            List<LivingEntity> inside = new ArrayList<>();
            collectOwn(level, inside);
            for (Ritual sibling : linked(this)) {
                sibling.collectOwn(level, inside);
            }
            return inside;
        }

        private void collectOwn(ServerLevel level, List<LivingEntity> into) {
            double reach = radius();
            AABB box = AABB.ofSize(position, reach * 2, reach * 2, reach * 2);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (entity.isAlive() && coversOwn(entity.position()) && !into.contains(entity)) {
                    into.add(entity);
                }
            }
        }
    }

    /** Owner, then perk: one ritual each, which is the rule the whole mechanic rests on. */
    private static final Map<UUID, Map<ResourceLocation, Ritual>> RITUALS = new HashMap<>();
    /** Markers of rituals broken while their chunk was unloaded: thrown away the moment it loads. */
    private static final Set<UUID> BROKEN_UNSEEN = new HashSet<>();

    /** How often the ring of flames is drawn, and how many flames it has. */
    private static final int PARTICLE_INTERVAL = 10;
    private static final int RING_POINTS = 24;
    /** A Bad Omen's ring runs to eighty blocks; it still gets a flame every few of them. */
    private static final int MAX_RING_POINTS = 120;

    private RitualManager() {}

    /**
     * Lays a ritual where the player stands, taking away whatever they had for this perk already.
     */
    public static Ritual place(ServerPlayer owner, ResourceLocation perkId, Mood mood, int tier,
            double radius, float charges) {
        remove(owner.getUUID(), perkId);

        ServerLevel level = owner.serverLevel();
        RitualEntity marker = new RitualEntity(level);
        marker.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
        marker.stamp(owner.getUUID(), perkId, mood, tier, radius, charges, level.getGameTime());

        // On the books before the marker joins, so the join finds it already there and leaves it be.
        Ritual ritual = new Ritual(marker);
        RITUALS.computeIfAbsent(owner.getUUID(), id -> new HashMap<>()).put(perkId, ritual);
        level.addFreshEntity(marker);

        if (perkId.equals(ModPerks.BAD_OMEN.id())) {
            for (Ritual watched : List.copyOf(RITUALS.get(owner.getUUID()).values())) {
                boostCharges(watched);
            }
        } else {
            boostCharges(ritual);
        }

        level.playSound(null, marker.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                SoundSource.PLAYERS, 0.8F, mood == Mood.HELPFUL ? 1.4F : 0.7F);
        return ritual;
    }

    /**
     * A marker joining a level: laid just now, or read back with its chunk. One read back is built
     * into a ritual again — unless it was broken while nobody could see it, or its owner has laid a
     * newer one for the same perk since, in which case it is thrown away before it ever appears.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof RitualEntity marker)) {
            return;
        }
        if (BROKEN_UNSEEN.remove(marker.getUUID()) || marker.ownerId() == null || marker.perkId() == null) {
            event.setCanceled(true);
            return;
        }

        Ritual current = get(marker.ownerId(), marker.perkId());
        if (current != null && current.id.equals(marker.getUUID())) {
            current.marker = marker;
            return;
        }
        if (current != null && current.placedAt >= marker.placedAt()) {
            event.setCanceled(true);
            return;
        }
        if (current != null) {
            remove(marker.ownerId(), marker.perkId());
        }
        RITUALS.computeIfAbsent(marker.ownerId(), id -> new HashMap<>()).put(marker.perkId(), new Ritual(marker));
    }

    // --- Bad Omen ---

    /**
     * The owner's Bad Omen standing over a ritual, or null. Only the owner's own, only while it is
     * standing, and never over itself.
     */
    @Nullable
    public static Ritual omenOver(Ritual ritual) {
        if (ritual.perkId.equals(ModPerks.BAD_OMEN.id())) {
            return null;
        }
        Ritual omen = get(ritual.ownerId, ModPerks.BAD_OMEN.id());
        if (omen == null || !omen.isAlive() || omen.dimension != ritual.dimension) {
            return null;
        }
        // The omen's own radius is never widened, so it is read straight off the field.
        return omen.position.distanceToSqr(ritual.position) <= omen.radius * omen.radius ? omen : null;
    }

    /**
     * Enhanced Perception's reach, for the rituals that listen with their owner's ears: Eavesdrop.
     * Read live rather than baked in when the ritual is laid, so taking the perk or Adware off
     * takes the extra reach away on the spot. An owner who is not online lends nothing.
     */
    private static double perceptionBonus(Ritual ritual) {
        if (!ritual.perkId.equals(ModPerks.EAVESDROP.id()) || ritual.marker.level().getServer() == null) {
            return 0.0D;
        }
        ServerPlayer owner = ritual.marker.level().getServer().getPlayerList().getPlayer(ritual.ownerId);
        return owner == null ? 0.0D : EnhancedPerception.radius(owner, 0.0D);
    }

    /** How much wider a ritual is for the Bad Omen over it: 1 when there is none. */
    private static double omenWidening(Ritual ritual) {
        Ritual omen = omenOver(ritual);
        return omen == null ? 1.0D
                : 1.0D + ModPerks.BAD_OMEN.value(ModPerks.OMEN_RADIUS_BONUS, omen.tier) / 100.0D;
    }

    /** The owner's other rituals under the same Bad Omen, whose circles this one shares. */
    public static List<Ritual> linked(Ritual ritual) {
        Ritual omen = omenOver(ritual);
        Map<ResourceLocation, Ritual> mine = RITUALS.get(ritual.ownerId);
        if (omen == null || mine == null) {
            return List.of();
        }
        List<Ritual> siblings = new ArrayList<>();
        for (Ritual other : mine.values()) {
            if (other != ritual && other != omen && other.isAlive() && omenOver(other) == omen) {
                siblings.add(other);
            }
        }
        return siblings;
    }

    /**
     * A ritual with charges under a Bad Omen holds more of them. Paid once per ritual, whichever
     * came first, and remembered on the marker so a reload does not pay it again.
     */
    private static void boostCharges(Ritual ritual) {
        if (ritual.charges <= 0.0F || ritual.omenBoosted) {
            return;
        }
        Ritual omen = omenOver(ritual);
        if (omen == null) {
            return;
        }
        float factor = 1.0F + (float) ModPerks.BAD_OMEN.value(ModPerks.OMEN_CHARGE_BONUS, omen.tier) / 100.0F;
        ritual.charges *= factor;
        ritual.omenBoosted = true;
        ritual.marker.setCharges(ritual.charges);
        ritual.marker.setCapacity(ritual.marker.capacity() * factor);
        ritual.marker.setOmenBoosted(true);
    }

    @Nullable
    public static Ritual get(UUID ownerId, ResourceLocation perkId) {
        Map<ResourceLocation, Ritual> mine = RITUALS.get(ownerId);
        return mine == null ? null : mine.get(perkId);
    }

    /** Every ritual of one perk, whoever laid it and whether or not they are online. */
    public static List<Ritual> all(ResourceLocation perkId) {
        List<Ritual> found = new ArrayList<>();
        for (Map<ResourceLocation, Ritual> mine : RITUALS.values()) {
            Ritual ritual = mine.get(perkId);
            if (ritual != null) {
                found.add(ritual);
            }
        }
        return found;
    }

    /** Breaks one ritual, if it is there. */
    public static void remove(UUID ownerId, ResourceLocation perkId) {
        Map<ResourceLocation, Ritual> mine = RITUALS.get(ownerId);
        if (mine == null) {
            return;
        }
        Ritual ritual = mine.remove(perkId);
        if (ritual != null) {
            breakDown(ritual);
        }
        if (mine.isEmpty()) {
            RITUALS.remove(ownerId);
        }
    }

    /** Breaks every ritual a player has, for a death or the perk coming off. */
    public static void removeAll(UUID ownerId) {
        Map<ResourceLocation, Ritual> mine = RITUALS.remove(ownerId);
        if (mine == null) {
            return;
        }
        for (Ritual ritual : mine.values()) {
            breakDown(ritual);
        }
    }

    /**
     * Forgets every ritual, on server shutdown — without breaking any: the markers are saved with
     * their chunks, and the rituals come back from them next time.
     */
    public static void clear() {
        RITUALS.clear();
        BROKEN_UNSEEN.clear();
    }

    private static void breakDown(Ritual ritual) {
        if (ritual.isDormant()) {
            // Nothing to see and nothing to discard: the marker is on disk. It is turned away when
            // its chunk next loads instead.
            BROKEN_UNSEEN.add(ritual.id);
            return;
        }
        if (ritual.marker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, ritual.position.x, ritual.position.y + 0.3D,
                    ritual.position.z, 12, 0.3D, 0.2D, 0.3D, 0.02D);
            level.playSound(null, ritual.marker.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.7F, 1.1F);
        }
        ritual.marker.discard();
    }

    /**
     * A marker that has been hurt, however it was hurt: the ritual it stands for goes with it.
     * <p>
     * Any ritual can be broken by anybody who finds it — which is what its flames, and the aura it
     * eventually gives away, are for.
     */
    public static void onMarkerHurt(RitualEntity marker, DamageSource source) {
        for (Map.Entry<UUID, Map<ResourceLocation, Ritual>> owned : Map.copyOf(RITUALS).entrySet()) {
            for (Map.Entry<ResourceLocation, Ritual> entry : Map.copyOf(owned.getValue()).entrySet()) {
                if (entry.getValue().marker() != marker) {
                    continue;
                }
                // A Bad Omen keeps the rituals under it whole, and shows itself to whoever tried.
                Ritual omen = omenOver(entry.getValue());
                if (omen != null) {
                    if (source.getEntity() instanceof ServerPlayer culprit) {
                        revealTo(culprit, omen, ModPerks.OMEN_REVEAL_TICKS);
                        culprit.displayClientMessage(Component.translatable("bloodbound.message.bad_omen_guarded")
                                .withStyle(ChatFormatting.DARK_PURPLE), true);
                    }
                    return;
                }
                remove(owned.getKey(), entry.getKey());
                if (marker.level() instanceof ServerLevel level) {
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(owned.getKey());
                    if (owner != null) {
                        owner.displayClientMessage(Component
                                .translatable("bloodbound.message.ritual_broken")
                                .withStyle(ChatFormatting.DARK_RED), true);
                    }
                }
                return;
            }
        }
        marker.discard();
    }

    /** Shows a ritual to one player for a while. Safe to call every tick: it only tops the glow up. */
    public static void revealTo(ServerPlayer viewer, Ritual ritual, int ticks) {
        AuraRevealHandler.revealMarker(viewer, ritual.marker, ticks);
    }

    /** The server tick: keeps the flames going and sweeps up rituals whose marker has gone. */
    public static void tick(MinecraftServer server) {
        if (RITUALS.isEmpty()) {
            return;
        }
        long gameTime = server.overworld().getGameTime();

        for (Map<ResourceLocation, Ritual> mine : List.copyOf(RITUALS.values())) {
            for (Ritual ritual : List.copyOf(mine.values())) {
                if (!ritual.isAlive()) {
                    // Unloaded is not gone: the ritual waits for its chunk. Anything else broke it.
                    if (!ritual.isDormant()) {
                        remove(ritual.ownerId, ritual.perkId);
                    }
                    continue;
                }
                if (gameTime % PARTICLE_INTERVAL == 0L) {
                    drawFlames(server, ritual);
                }
            }
        }
    }

    /** A flame at the heart of it, and a ring of them at the edge so the reach is never a guess. */
    private static void drawFlames(MinecraftServer server, Ritual ritual) {
        ServerLevel level = server.getLevel(ritual.dimension);
        if (level == null) {
            return;
        }

        level.sendParticles(ritual.mood.particle, ritual.position.x, ritual.position.y + 0.4D,
                ritual.position.z, 6, 0.25D, 0.3D, 0.25D, 0.01D);

        double reach = ritual.radius();
        int points = Math.clamp((int) Math.round(reach * 1.5D), RING_POINTS, MAX_RING_POINTS);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            double x = ritual.position.x + Math.cos(angle) * reach;
            double z = ritual.position.z + Math.sin(angle) * reach;
            level.sendParticles(ritual.mood.particle, x, ritual.position.y + 0.3D, z, 1,
                    0.0D, 0.05D, 0.0D, 0.0D);
        }
    }
}
