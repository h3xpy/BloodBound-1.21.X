package net.h3xpy.bloodbound.ritual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.entity.RitualEntity;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        private final UUID ownerId;
        private final ResourceLocation perkId;
        private final ResourceKey<Level> dimension;
        private final Vec3 position;
        private final Mood mood;
        private final int tier;
        private final long placedAt;
        private final RitualEntity marker;

        private double radius;
        private float charges;
        /** Ticks each player has spent inside, however the perk chooses to count them. */
        private final Map<UUID, Integer> insideTicks = new HashMap<>();

        private Ritual(UUID ownerId, ResourceLocation perkId, ResourceKey<Level> dimension, Vec3 position,
                Mood mood, int tier, double radius, float charges, long placedAt, RitualEntity marker) {
            this.ownerId = ownerId;
            this.perkId = perkId;
            this.dimension = dimension;
            this.position = position;
            this.mood = mood;
            this.tier = tier;
            this.radius = radius;
            this.charges = charges;
            this.placedAt = placedAt;
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

        public double radius() {
            return radius;
        }

        public void setRadius(double radius) {
            this.radius = radius;
        }

        public float charges() {
            return charges;
        }

        /** Spends charges, and says whether any are left. */
        public boolean spend(float amount) {
            charges = Math.max(0.0F, charges - amount);
            return charges > 0.0F;
        }

        public RitualEntity marker() {
            return marker;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public boolean isAlive() {
            return !marker.isRemoved();
        }

        /** Whether a position is inside the ritual, measured flat and generously in height. */
        public boolean covers(Vec3 point) {
            return point.distanceToSqr(position) <= radius * radius;
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

        /** Everything alive inside the ritual right now. */
        public List<LivingEntity> occupants(ServerLevel level) {
            AABB box = AABB.ofSize(position, radius * 2, radius * 2, radius * 2);
            List<LivingEntity> inside = new ArrayList<>();
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (entity.isAlive() && covers(entity.position())) {
                    inside.add(entity);
                }
            }
            return inside;
        }
    }

    /** Owner, then perk: one ritual each, which is the rule the whole mechanic rests on. */
    private static final Map<UUID, Map<ResourceLocation, Ritual>> RITUALS = new HashMap<>();

    /** How often the ring of flames is drawn, and how many flames it has. */
    private static final int PARTICLE_INTERVAL = 10;
    private static final int RING_POINTS = 24;

    private RitualManager() {}

    /**
     * Lays a ritual where the player stands, taking away whatever they had for this perk already.
     */
    public static Ritual place(ServerPlayer owner, ResourceLocation perkId, Mood mood, int tier,
            double radius, float charges) {
        remove(owner.getUUID(), perkId);

        ServerLevel level = owner.serverLevel();
        Vec3 position = new Vec3(owner.getX(), owner.getY(), owner.getZ());

        RitualEntity marker = new RitualEntity(level);
        marker.moveTo(position.x, position.y, position.z, owner.getYRot(), 0.0F);
        level.addFreshEntity(marker);

        Ritual ritual = new Ritual(owner.getUUID(), perkId, level.dimension(), position, mood, tier,
                radius, charges, level.getGameTime(), marker);
        RITUALS.computeIfAbsent(owner.getUUID(), id -> new HashMap<>()).put(perkId, ritual);

        level.playSound(null, marker.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                SoundSource.PLAYERS, 0.8F, mood == Mood.HELPFUL ? 1.4F : 0.7F);
        return ritual;
    }

    @Nullable
    public static Ritual get(UUID ownerId, ResourceLocation perkId) {
        Map<ResourceLocation, Ritual> mine = RITUALS.get(ownerId);
        return mine == null ? null : mine.get(perkId);
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

    /** Breaks every ritual a player has, for a logout, a death or the perk coming off. */
    public static void removeAll(UUID ownerId) {
        Map<ResourceLocation, Ritual> mine = RITUALS.remove(ownerId);
        if (mine == null) {
            return;
        }
        for (Ritual ritual : mine.values()) {
            breakDown(ritual);
        }
    }

    public static void clear() {
        for (Map<ResourceLocation, Ritual> mine : RITUALS.values()) {
            for (Ritual ritual : mine.values()) {
                breakDown(ritual);
            }
        }
        RITUALS.clear();
    }

    private static void breakDown(Ritual ritual) {
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
    public static void onMarkerHurt(RitualEntity marker) {
        for (Map.Entry<UUID, Map<ResourceLocation, Ritual>> owned : Map.copyOf(RITUALS).entrySet()) {
            for (Map.Entry<ResourceLocation, Ritual> entry : Map.copyOf(owned.getValue()).entrySet()) {
                if (entry.getValue().marker() != marker) {
                    continue;
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
                    remove(ritual.ownerId, ritual.perkId);
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

        for (int i = 0; i < RING_POINTS; i++) {
            double angle = Math.PI * 2.0D * i / RING_POINTS;
            double x = ritual.position.x + Math.cos(angle) * ritual.radius;
            double z = ritual.position.z + Math.sin(angle) * ritual.radius;
            level.sendParticles(ritual.mood.particle, x, ritual.position.y + 0.3D, z, 1,
                    0.0D, 0.05D, 0.0D, 0.0D);
        }
    }
}
