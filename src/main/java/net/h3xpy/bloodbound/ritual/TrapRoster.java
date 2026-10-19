package net.h3xpy.bloodbound.ritual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Who has which traps out, for traps that outlive their owner's session.
 * <p>
 * A trap is saved with the world, so it can be anywhere: in a loaded chunk, in one nobody is near,
 * or on the far side of a restart. The roster keeps every trap it has heard of per owner, loaded or
 * not, oldest first, which is what the per-perk limit is counted against — a coil left in a chunk
 * that has since unloaded still counts, and is thrown away as soon as it loads again if newer ones
 * have taken its place.
 * <p>
 * It learns about traps as they join a level, freshly laid or read back from disk, so nothing has
 * to be written anywhere but the traps themselves.
 */
public final class TrapRoster {

    /** What a trap entity has to say about itself. */
    public interface Trap {
        /** Which perk laid it; each has its own limit and its own roster. */
        String trapKind();

        @Nullable
        UUID trapOwnerId();

        /** Game time it was laid at, which is what decides which ones go when there are too many. */
        long placedAt();

        /** How many of these the owner may have out, at the tier this one was laid at. */
        int trapLimit();

        /** A trap that already went off and is only waiting to be taken away is not brought back. */
        default boolean isSpent() {
            return false;
        }

        /** Where it stands, for the spacing rule. A tripwire stands at both of its posts. */
        default List<Vec3> trapPoints() {
            return List.of(((Entity) this).position());
        }
    }

    /**
     * How far round a spot traps are looked for: as far as a tripwire's second post can be from its
     * first, so one whose far post is the close one is still found.
     */
    private static final double SPACING_SEARCH = 24.0D;

    /** One trap that is out, and the entity standing for it while its chunk is loaded. */
    private static final class Entry {
        private final UUID id;
        private final long placedAt;
        @Nullable
        private Entity entity;

        private Entry(UUID id, long placedAt, @Nullable Entity entity) {
            this.id = id;
            this.placedAt = placedAt;
            this.entity = entity;
        }
    }

    /** Kind, then owner, then that owner's traps, oldest first. */
    private static final Map<String, Map<UUID, List<Entry>>> ROSTERS = new HashMap<>();
    /** Traps pushed out while their chunk was unloaded: thrown away the moment it loads. */
    private static final Set<UUID> DISCARDED = new HashSet<>();

    private TrapRoster() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Trap trap)) {
            return;
        }
        Entity entity = event.getEntity();
        if (DISCARDED.remove(entity.getUUID()) || trap.isSpent() || trap.trapOwnerId() == null) {
            event.setCanceled(true);
            return;
        }
        if (!adopt(entity, trap)) {
            event.setCanceled(true);
        }
    }

    /**
     * Puts a trap on its owner's roster, and throws out the oldest ones past the limit.
     *
     * @return false when the trap itself is one too many and must not be added
     */
    private static boolean adopt(Entity entity, Trap trap) {
        List<Entry> mine = roster(trap.trapKind(), trap.trapOwnerId());
        prune(mine);

        Entry existing = null;
        for (Entry entry : mine) {
            if (entry.id.equals(entity.getUUID())) {
                existing = entry;
                break;
            }
        }
        if (existing != null) {
            existing.entity = entity;
        } else {
            mine.add(new Entry(entity.getUUID(), trap.placedAt(), entity));
            mine.sort(Comparator.comparingLong(entry -> entry.placedAt));
        }

        boolean keep = true;
        while (mine.size() > Math.max(1, trap.trapLimit())) {
            Entry oldest = mine.remove(0);
            if (oldest.entity == entity) {
                keep = false;
            } else if (oldest.entity != null && !oldest.entity.isRemoved()) {
                oldest.entity.discard();
            } else {
                DISCARDED.add(oldest.id);
            }
        }
        return keep;
    }

    /**
     * Traps never stack and always keep a clear block between them: nothing may go in the block of
     * another trap, whoever's and whatever kind, nor in any of the blocks touching it.
     */
    public static boolean tooClose(Level level, Vec3 spot) {
        BlockPos block = BlockPos.containing(spot);
        AABB around = new AABB(block).inflate(SPACING_SEARCH);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, around, candidate -> candidate instanceof Trap)) {
            if (entity.isRemoved() || ((Trap) entity).isSpent()) {
                continue;
            }
            for (Vec3 point : ((Trap) entity).trapPoints()) {
                BlockPos other = BlockPos.containing(point);
                if (Math.abs(other.getX() - block.getX()) <= 1 && Math.abs(other.getY() - block.getY()) <= 1
                        && Math.abs(other.getZ() - block.getZ()) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The spacing rule, said to the player: true when where they stand is too close to a trap, and
     * nothing should be laid.
     */
    public static boolean refuseCrowded(ServerPlayer player) {
        if (!tooClose(player.level(), player.position())) {
            return false;
        }
        player.displayClientMessage(Component.translatable("bloodbound.message.trap_too_close")
                .withStyle(ChatFormatting.RED), true);
        return true;
    }

    /** How many traps of this kind the owner has out, loaded or not. */
    public static int count(String kind, UUID ownerId) {
        List<Entry> mine = roster(kind, ownerId);
        prune(mine);
        return mine.size();
    }

    /** The owner's traps of this kind that are loaded right now, oldest first. */
    public static List<Entity> loaded(String kind, UUID ownerId) {
        List<Entry> mine = roster(kind, ownerId);
        prune(mine);
        List<Entity> found = new ArrayList<>();
        for (Entry entry : mine) {
            if (entry.entity != null) {
                found.add(entry.entity);
            }
        }
        return found;
    }

    /**
     * Takes the owner's oldest traps of this kind away until no more than {@code limit} are left,
     * wherever they are: what raised the limit (an addon) came off.
     */
    public static void trimTo(String kind, UUID ownerId, int limit) {
        List<Entry> mine = roster(kind, ownerId);
        prune(mine);
        while (mine.size() > Math.max(0, limit)) {
            Entry oldest = mine.remove(0);
            if (oldest.entity != null && !oldest.entity.isRemoved()) {
                oldest.entity.discard();
            } else {
                DISCARDED.add(oldest.id);
            }
        }
    }

    /** Takes every trap of this kind away from the owner, wherever it is: the perk came off. */
    public static void removeAll(String kind, UUID ownerId) {
        Map<UUID, List<Entry>> byOwner = ROSTERS.get(kind);
        List<Entry> mine = byOwner == null ? null : byOwner.remove(ownerId);
        if (mine == null) {
            return;
        }
        for (Entry entry : mine) {
            if (entry.entity != null && !entry.entity.isRemoved()) {
                entry.entity.discard();
            } else {
                DISCARDED.add(entry.id);
            }
        }
    }

    /** Whether the owner has anything of this kind on the roster at all. */
    public static boolean hasAny(String kind, UUID ownerId) {
        Map<UUID, List<Entry>> byOwner = ROSTERS.get(kind);
        return byOwner != null && byOwner.containsKey(ownerId);
    }

    /**
     * Drops what is gone for good, and lets go of the entity behind a trap whose chunk unloaded: the
     * trap still counts, and a fresh entity takes the place when the chunk comes back.
     */
    private static void prune(List<Entry> mine) {
        mine.removeIf(entry -> {
            if (entry.entity == null || !entry.entity.isRemoved()) {
                return false;
            }
            Entity.RemovalReason reason = entry.entity.getRemovalReason();
            if (reason != null && reason.shouldSave()) {
                entry.entity = null;
                return false;
            }
            return true;
        });
    }

    private static List<Entry> roster(String kind, UUID ownerId) {
        return ROSTERS.computeIfAbsent(kind, k -> new HashMap<>()).computeIfAbsent(ownerId, id -> new ArrayList<>());
    }

    /** Forgets everything, on server shutdown. The traps themselves are saved with their chunks. */
    public static void clear() {
        ROSTERS.clear();
        DISCARDED.clear();
    }
}
