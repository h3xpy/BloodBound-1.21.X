package net.h3xpy.bloodbound.mark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.MarksPayload;
import net.h3xpy.bloodbound.network.RemoveMarksPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Marks: the trail everything that moves leaves behind it.
 * <p>
 * A sprinting player or a moving mob scuffs a share of the ground around them. The marks are held
 * here rather than in the world — no blocks are touched and nothing is saved — and are only ever
 * sent to players carrying a perk that can read them.
 * <p>
 * Final Blow reads them too, but only the trails of the wounded. A Final Blow player without full
 * mark vision is told about a trail only while its owner is under the line, and is told to take it
 * away again the moment they are not — the one case where a mark is ever mentioned twice.
 */
public final class MarkManager {

    /** Blocks either side of the entity that get scuffed. */
    public static final int RADIUS = 3;
    /**
     * Share of the ground inside that radius a single pass covers. Players leave a heavier trail
     * than mobs: there are far fewer of them, and following one is what the perks are for.
     */
    public static final double COVERAGE = 0.22D;
    public static final double PLAYER_COVERAGE = 0.45D;

    /** The three stages of a mark's life, in ticks: 2 seconds in, 6 held, 4 out. */
    public static final int FADE_IN_TICKS = 40;
    public static final int HOLD_TICKS = 120;
    public static final int FADE_OUT_TICKS = 80;
    public static final int LIFETIME_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;

    /** How often a mob may lay a fresh set down, and how often a player may. */
    private static final int STAMP_INTERVAL = 20;
    private static final int PLAYER_STAMP_INTERVAL = 10;

    /** How long a drop of blood stays on the ground: a quarter second in, five and a half, one out. */
    public static final int BLOOD_FADE_IN_TICKS = 5;
    public static final int BLOOD_HOLD_TICKS = 110;
    public static final int BLOOD_FADE_OUT_TICKS = 25;
    public static final int BLOOD_LIFETIME_TICKS = BLOOD_FADE_IN_TICKS + BLOOD_HOLD_TICKS + BLOOD_FADE_OUT_TICKS;
    /** How far a player can be from blood and still be sent it. */
    private static final double BLOOD_SEND_RANGE = 48.0D;
    /** How far an entity has to have moved since its last pass to leave another. */
    private static final double MOVE_EPSILON = 0.08D;
    /** How far up and down the surface is looked for, from the entity's feet. */
    private static final int SURFACE_SEARCH = 2;

    /** How far a player has to be from a mark to be told about it. */
    private static final double SEND_RANGE = 64.0D;
    /** Ceiling on marks held for one level, so a busy field cannot grow without bound. */
    private static final int MAX_PER_LEVEL = 6000;
    /** How often Final Blow re-reads who is wounded enough to be shown. */
    private static final int FINAL_BLOW_REFRESH = 5;
    /** How long an entity may go unseen before its last position is forgotten. */
    private static final int LAST_STAMP_SWEEP_TICKS = 1200;
    /** How often a failing tick is written to the log, so a recurring fault cannot flood it. */
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private static long lastFailureLog;

    /** One mark: when it was laid, who left it, and whether that was a player or a mob. */
    private record Mark(long laidAt, UUID ownerId, boolean fromPlayer) {}

    /** A mark just laid, with its owner, before it is decided who gets told. */
    private record Laid(MarksPayload.Mark mark, UUID ownerId) {}

    /** Live marks, per level. Keyed by block, so walking the same ground refreshes rather than piles up. */
    private static final Map<ServerLevel, Map<BlockPos, Mark>> MARKS = new HashMap<>();
    /** Where each entity was when it last left a set, so a pass needs real movement behind it. */
    private static final Map<UUID, double[]> LAST_STAMP = new HashMap<>();
    /** Final Blow viewer to the owners whose trails that viewer is currently being shown. */
    private static final Map<UUID, Set<UUID>> SHOWN = new HashMap<>();

    private MarkManager() {}

    /**
     * Lays marks down for everything moving, keeps Final Blow's view current, and sweeps up
     * whatever has expired. Called once a tick for the whole server.
     */
    public static void tick(MinecraftServer server) {
        // Marks are only ever something to look at. Whatever goes wrong laying them, the server
        // carries on: the worst it should cost is a trail, never the tick.
        try {
            tickMarks(server);
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            if (now - lastFailureLog > FAILURE_LOG_INTERVAL_MS) {
                lastFailureLog = now;
                BloodBound.LOGGER.error("BloodBound marks failed a tick; skipping it", e);
            }
        }
    }

    private static void tickMarks(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();

        // Where things were last seen is only needed while they keep being seen. Anything absent
        // for a minute — unloaded, gone to another dimension — is forgotten rather than kept for ever.
        if (gameTime % LAST_STAMP_SWEEP_TICKS == 0) {
            LAST_STAMP.values().removeIf(last -> gameTime - (long) last[3] > LAST_STAMP_SWEEP_TICKS);
        }

        // Nothing is laid while nobody can read it, so an empty room costs nothing at all.
        boolean watched = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerPerkData data = PerkDataManager.get(player);
            if (data.canSeeMarks() || data.getActiveTier(ModPerks.FINAL_BLOW) > 0) {
                watched = true;
                break;
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (watched && gameTime % PLAYER_STAMP_INTERVAL == 0) {
                stampLevel(level, gameTime, gameTime % STAMP_INTERVAL == 0);
            }
            expire(level, gameTime);
            if (watched && gameTime % FINAL_BLOW_REFRESH == 0) {
                refreshFinalBlow(level);
            }
        }
    }

    /**
     * Lays this pass's marks in one level.
     * <p>
     * Only what a watcher could ever be shown is looked at: the mobs within sending range of a
     * player who reads marks, found through the level's own spatial lookup. This used to walk
     * {@code getAllEntities()}, which is a live view over the level's whole entity table — on a
     * server where other threads load and unload entities (C2ME, Distant Horizons), the table
     * changed under the loop and the iterator ran off the end of it, taking the server down.
     */
    private static void stampLevel(ServerLevel level, long gameTime, boolean mobsToo) {
        List<ServerPlayer> watchers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            PlayerPerkData data = PerkDataManager.get(player);
            if (data.canSeeMarks() || data.getActiveTier(ModPerks.FINAL_BLOW) > 0) {
                watchers.add(player);
            }
        }
        // Nobody here can read a trail, so nobody here needs one laid.
        if (watchers.isEmpty()) {
            return;
        }

        List<Laid> laid = new ArrayList<>();

        // Players are few, and a copy of the list, so a login mid-pass cannot upset it.
        for (ServerPlayer player : List.copyOf(level.players())) {
            consider(level, player, gameTime, laid);
        }

        // Mobs are looked at half as often, and only round the players who could be told.
        if (mobsToo) {
            double reach = SEND_RANGE + RADIUS;
            Set<Integer> seen = new HashSet<>();
            for (ServerPlayer watcher : watchers) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, watcher.getBoundingBox().inflate(reach))) {
                    if (seen.add(mob.getId())) {
                        consider(level, mob, gameTime, laid);
                    }
                }
            }
        }

        if (!laid.isEmpty()) {
            broadcast(level, laid);
        }
    }

    /** One entity's turn: it leaves a set if it is the kind that does, and has moved to leave it. */
    private static void consider(ServerLevel level, LivingEntity living, long gameTime, List<Laid> laid) {
        if (!living.isAlive()) {
            return;
        }
        boolean player = living instanceof Player;
        if (player) {
            // Sprinting, or off the ground: a jump scuffs the ground it left and the ground it
            // lands on, which is what a real trail looks like.
            if (living.isSpectator() || !(living.isSprinting() || !living.onGround())) {
                return;
            }
        } else if (!living.onGround()) {
            return;
        }
        if (!hasMoved(living, gameTime)) {
            return;
        }
        stamp(level, living, player, gameTime, laid);
    }

    /**
     * True when the entity has gone somewhere since its last pass, which also records where it is
     * — and when, so entries for things that have wandered off or unloaded can be swept up.
     */
    private static boolean hasMoved(LivingEntity entity, long gameTime) {
        double[] last = LAST_STAMP.get(entity.getUUID());
        double[] now = {entity.getX(), entity.getY(), entity.getZ(), gameTime};
        LAST_STAMP.put(entity.getUUID(), now);
        if (last == null) {
            return false;
        }
        return Math.abs(now[0] - last[0]) > MOVE_EPSILON
                || Math.abs(now[1] - last[1]) > MOVE_EPSILON
                || Math.abs(now[2] - last[2]) > MOVE_EPSILON;
    }

    /** Scuffs a share of the ground around one entity. */
    private static void stamp(ServerLevel level, LivingEntity entity, boolean fromPlayer, long gameTime,
            List<Laid> laid) {
        Map<BlockPos, Mark> marks = MARKS.computeIfAbsent(level, key -> new HashMap<>());
        if (marks.size() >= MAX_PER_LEVEL) {
            return;
        }

        RandomSource random = entity.getRandom();
        BlockPos feet = entity.blockPosition();
        int radiusSq = RADIUS * RADIUS;

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > radiusSq
                        || random.nextDouble() >= (fromPlayer ? PLAYER_COVERAGE : COVERAGE)) {
                    continue;
                }
                BlockPos surface = surfaceUnder(level, feet.offset(dx, 0, dz));
                if (surface == null) {
                    continue;
                }

                Mark existing = marks.get(surface);
                // Re-treading ground a mark is already fading in on would restart it and never let
                // it settle, so only a mark past its prime is worth laying again.
                if (existing != null && gameTime - existing.laidAt() < FADE_IN_TICKS + HOLD_TICKS) {
                    continue;
                }
                marks.put(surface, new Mark(gameTime, entity.getUUID(), fromPlayer));
                laid.add(new Laid(new MarksPayload.Mark(surface, kindOf(fromPlayer)), entity.getUUID()));
            }
        }
    }

    private static byte kindOf(boolean fromPlayer) {
        return fromPlayer ? MarksPayload.KIND_PLAYER : MarksPayload.KIND_MOB;
    }

    /**
     * The block whose top face a mark would lie on, searched from the entity's own level outwards,
     * or null where there is nothing to mark — over a drop, or inside a wall.
     */
    private static BlockPos surfaceUnder(ServerLevel level, BlockPos from) {
        for (int dy = 0; dy >= -SURFACE_SEARCH - 1; dy--) {
            BlockPos candidate = from.offset(0, dy - 1, 0);
            if (!level.getBlockState(candidate).isFaceSturdy(level, candidate, Direction.UP)) {
                continue;
            }
            // Nothing solid may be standing on it, or the mark would be buried.
            BlockPos above = candidate.above();
            return level.getBlockState(above).getCollisionShape(level, above).isEmpty() ? candidate : null;
        }
        return null;
    }

    /**
     * A few drops of blood under something that is Bleeding. Nothing is kept on the server — blood
     * changes nothing — and unlike a trail it is sent to everybody nearby, perk or not.
     */
    public static void bleed(ServerLevel level, LivingEntity entity) {
        RandomSource random = entity.getRandom();
        BlockPos feet = entity.blockPosition();
        List<MarksPayload.Mark> drops = new ArrayList<>();
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            BlockPos surface = surfaceUnder(level, feet.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1));
            if (surface != null) {
                drops.add(new MarksPayload.Mark(surface, MarksPayload.KIND_BLOOD));
            }
        }
        if (drops.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) <= BLOOD_SEND_RANGE * BLOOD_SEND_RANGE) {
                PacketDistributor.sendToPlayer(player, new MarksPayload(drops));
            }
        }
    }

    /**
     * Tells every nearby player who can read marks about the ones just laid. Full mark vision is
     * told everything; Final Blow alone is only told about the wounded.
     */
    private static void broadcast(ServerLevel level, List<Laid> laid) {
        for (ServerPlayer player : level.players()) {
            PlayerPerkData data = PerkDataManager.get(player);
            boolean full = data.canSeeMarks();
            int finalBlow = data.getActiveTier(ModPerks.FINAL_BLOW);
            if (!full && finalBlow <= 0) {
                continue;
            }
            double threshold = finalBlow > 0 ? threshold(finalBlow) : 0.0D;
            Set<UUID> shown = full ? null : SHOWN.computeIfAbsent(player.getUUID(), id -> new HashSet<>());

            List<MarksPayload.Mark> inRange = new ArrayList<>();
            for (Laid entry : laid) {
                BlockPos pos = entry.mark().pos();
                if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                        > SEND_RANGE * SEND_RANGE) {
                    continue;
                }
                if (!full) {
                    if (entry.ownerId().equals(player.getUUID())
                            || healthShare(level, entry.ownerId()) >= threshold) {
                        continue;
                    }
                    shown.add(entry.ownerId());
                }
                inRange.add(entry.mark());
            }
            if (!inRange.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new MarksPayload(inRange));
            }
        }
    }

    /**
     * Keeps a Final Blow player's view of the wounded current: a trail whose owner has just dropped
     * under the line appears, and one whose owner has healed back over it goes.
     */
    private static void refreshFinalBlow(ServerLevel level) {
        Map<BlockPos, Mark> marks = MARKS.getOrDefault(level, Map.of());

        for (ServerPlayer player : level.players()) {
            PlayerPerkData data = PerkDataManager.get(player);
            int tier = data.getActiveTier(ModPerks.FINAL_BLOW);
            if (tier <= 0 || data.canSeeMarks()) {
                SHOWN.remove(player.getUUID());
                continue;
            }
            double threshold = threshold(tier);

            Map<UUID, List<Map.Entry<BlockPos, Mark>>> byOwner = new HashMap<>();
            for (Map.Entry<BlockPos, Mark> entry : marks.entrySet()) {
                BlockPos pos = entry.getKey();
                UUID owner = entry.getValue().ownerId();
                if (owner.equals(player.getUUID()) || player.distanceToSqr(pos.getX() + 0.5D,
                        pos.getY() + 0.5D, pos.getZ() + 0.5D) > SEND_RANGE * SEND_RANGE) {
                    continue;
                }
                byOwner.computeIfAbsent(owner, id -> new ArrayList<>()).add(entry);
            }

            Set<UUID> low = new HashSet<>();
            for (UUID owner : byOwner.keySet()) {
                if (healthShare(level, owner) < threshold) {
                    low.add(owner);
                }
            }

            Set<UUID> shown = SHOWN.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
            List<MarksPayload.Mark> appear = new ArrayList<>();
            List<BlockPos> vanish = new ArrayList<>();
            for (UUID owner : low) {
                if (!shown.contains(owner)) {
                    for (Map.Entry<BlockPos, Mark> entry : byOwner.get(owner)) {
                        appear.add(new MarksPayload.Mark(entry.getKey(), kindOf(entry.getValue().fromPlayer())));
                    }
                }
            }
            for (UUID owner : shown) {
                if (!low.contains(owner)) {
                    for (Map.Entry<BlockPos, Mark> entry : byOwner.getOrDefault(owner, List.of())) {
                        vanish.add(entry.getKey());
                    }
                }
            }
            shown.clear();
            shown.addAll(low);

            if (!appear.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new MarksPayload(appear));
            }
            if (!vanish.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new RemoveMarksPayload(vanish));
            }
        }
    }

    /**
     * Final Blow: standing on a wounded creature's trail keeps it Broken, for as long as the player
     * stays on it and a few seconds after. Called once a tick per player.
     */
    public static void finalBlowStep(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.FINAL_BLOW);
        if (tier <= 0) {
            return;
        }
        Map<BlockPos, Mark> marks = MARKS.get(player.serverLevel());
        if (marks == null) {
            return;
        }
        Mark mark = marks.get(player.blockPosition().below());
        if (mark == null || mark.ownerId().equals(player.getUUID()) || gameTime - mark.laidAt() < FADE_IN_TICKS) {
            return;
        }
        Entity owner = player.serverLevel().getEntity(mark.ownerId());
        if (!(owner instanceof LivingEntity living) || !living.isAlive()
                || healthShare(player.serverLevel(), mark.ownerId()) >= threshold(tier)) {
            return;
        }
        // Topped up every tick on the trail, so it runs out the linger's length after the last step.
        living.addEffect(new MobEffectInstance(ModEffects.BROKEN,
                ModPerks.FINAL_BLOW.ticks(ModPerks.FINAL_BLOW_LINGER, tier), 0, false, true, true));
    }

    private static double threshold(int tier) {
        return ModPerks.FINAL_BLOW.value(ModPerks.FINAL_BLOW_THRESHOLD, tier) / 100.0D;
    }

    /** How much of its health a mark's owner has left, or all of it if it is not around. */
    private static double healthShare(ServerLevel level, UUID ownerId) {
        Entity entity = level.getEntity(ownerId);
        if (entity instanceof LivingEntity living && living.isAlive() && living.getMaxHealth() > 0.0F) {
            return living.getHealth() / living.getMaxHealth();
        }
        return 1.0D;
    }

    private static void expire(ServerLevel level, long gameTime) {
        Map<BlockPos, Mark> marks = MARKS.get(level);
        if (marks == null || marks.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Mark>> iterator = marks.entrySet().iterator();
        while (iterator.hasNext()) {
            if (gameTime - iterator.next().getValue().laidAt() >= LIFETIME_TICKS) {
                iterator.remove();
            }
        }
    }

    /**
     * Whether the ground here carries a visible mark somebody other than this player left, which is
     * what Catching Up reads. A mark still fading in does not count: a mark you can act on is one
     * you can see.
     */
    public static boolean hasForeignMark(ServerPlayer player, BlockPos pos) {
        return foreignMarkOwner(player, pos) != null;
    }

    /** As above, naming whoever left the mark, or null when there is none to follow. */
    @Nullable
    public static UUID foreignMarkOwner(ServerPlayer player, BlockPos pos) {
        Map<BlockPos, Mark> marks = MARKS.get(player.serverLevel());
        if (marks == null) {
            return null;
        }
        Mark mark = marks.get(pos);
        if (mark == null || mark.ownerId().equals(player.getUUID())) {
            return null;
        }
        return player.level().getGameTime() - mark.laidAt() >= FADE_IN_TICKS ? mark.ownerId() : null;
    }

    /** Forgets everything, on server shutdown. */
    public static void clear() {
        MARKS.clear();
        LAST_STAMP.clear();
        SHOWN.clear();
    }

    public static void clear(UUID entityId) {
        LAST_STAMP.remove(entityId);
        SHOWN.remove(entityId);
    }
}
