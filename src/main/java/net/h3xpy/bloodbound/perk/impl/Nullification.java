package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.h3xpy.bloodbound.ritual.RitualSetup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Nullification: a ritual inside which the world holds still. Nothing can be placed and nothing can
 * be broken; every block it saves costs it ten charges a point of that block's hardness.
 * <p>
 * The destruction is caught at every door there is: a player breaking the block, an explosion, a mob
 * tearing it out, a piston — and, for everything else (modded explosions, drills, commands), at the
 * one place every block change ends up, {@code Level.setBlock}, through {@code LevelMixin}. That last
 * one only turns away what reads as destruction — a block becoming air, a fluid or fire — and lets
 * through what reads as a move, a fall or a decay, since cancelling those would duplicate blocks
 * (a piston or a contraption carrying a block away, sand falling) or freeze nature (leaves, fire).
 * <p>
 * Out of charges it keeps working five more seconds, then breaks. A minute without spending
 * anything and it starts filling back up.
 */
public final class Nullification {

    /** What each ritual has been doing, by ritual id. Not saved: a reload simply starts the clocks. */
    private static final class State {
        private long lastSpentAt;
        private long emptySince = -1L;

        private State(long now) {
            this.lastSpentAt = now;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /**
     * The rituals standing right now, refreshed every tick. Read on every block change on the
     * server, so the common case — none at all — has to cost next to nothing.
     */
    private static volatile List<RitualManager.Ritual> active = List.of();
    /** Spots a placement was just turned away at: putting the air back there is not destruction. */
    private static final Set<BlockPos> REFUSED_PLACEMENTS = new HashSet<>();
    /** Players whose inventory has to be shown the block they did not get to place. */
    private static final Set<ServerPlayer> RESYNC = new HashSet<>();

    private Nullification() {}

    // --- laying it ---

    /** The press does nothing: the work is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.NULLIFICATION);
        if (tier <= 0 || data.isOnCooldown(ModPerks.NULLIFICATION.id(), player.level().getGameTime())) {
            RitualSetup.stop(player, ModPerks.NULLIFICATION.id(), false);
            return;
        }
        RitualSetup.setHolding(player, ModPerks.NULLIFICATION.id(), tier, holding);
    }

    /** Advances the laying, and shows the owner what their ritual has left. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.NULLIFICATION);
        if (tier <= 0) {
            RitualManager.remove(player.getUUID(), ModPerks.NULLIFICATION.id());
            return;
        }

        RitualManager.Ritual ritual = RitualManager.get(player.getUUID(), ModPerks.NULLIFICATION.id());
        if (gameTime % 10L == 0L) {
            PacketDistributor.sendToPlayer(player, ritual == null
                    ? new PerkChargesPayload(ModPerks.NULLIFICATION.id(), -1, 0, 0L)
                    : new PerkChargesPayload(ModPerks.NULLIFICATION.id(), (int) ritual.charges(),
                            Math.round(ritual.capacity()), 0L));
        }

        int held = RitualSetup.advance(player, ModPerks.NULLIFICATION.id(), ModPerks.NULL_SETUP_TICKS);
        if (held < ModPerks.NULL_SETUP_TICKS) {
            return;
        }
        RitualSetup.stop(player, ModPerks.NULLIFICATION.id(), false);
        RitualManager.place(player, ModPerks.NULLIFICATION.id(), RitualManager.Mood.HARMFUL, tier,
                ModPerks.NULLIFICATION.value(ModPerks.NULL_RADIUS, tier),
                (float) ModPerks.NULLIFICATION.value(ModPerks.NULL_CHARGES, tier));
        data.setCooldown(ModPerks.NULLIFICATION.id(), gameTime, ModPerks.NULLIFICATION.cooldownTicks(tier));
        PerkDataManager.sync(player);
        player.displayClientMessage(Component.translatable("bloodbound.message.nullification_placed")
                .withStyle(ChatFormatting.DARK_AQUA), true);
    }

    /** Refills, runs down and breaks every Nullification on the server. Called once a tick. */
    public static void tickRituals(MinecraftServer server, long gameTime) {
        if (!REFUSED_PLACEMENTS.isEmpty()) {
            REFUSED_PLACEMENTS.clear();
        }
        for (ServerPlayer player : RESYNC) {
            PerkDataManager.syncInventory(player);
        }
        RESYNC.clear();

        List<RitualManager.Ritual> standing = new ArrayList<>();
        for (RitualManager.Ritual ritual : RitualManager.all(ModPerks.NULLIFICATION.id())) {
            if (!ritual.isAlive()) {
                continue;
            }
            State state = STATES.computeIfAbsent(ritual.id(), id -> new State(gameTime));
            if (ritual.charges() <= 0.0F) {
                if (state.emptySince < 0L) {
                    state.emptySince = gameTime;
                }
                if (gameTime - state.emptySince >= ModPerks.NULL_EMPTY_GRACE_TICKS) {
                    STATES.remove(ritual.id());
                    RitualManager.remove(ritual.ownerId(), ModPerks.NULLIFICATION.id());
                    ServerPlayer owner = server.getPlayerList().getPlayer(ritual.ownerId());
                    if (owner != null) {
                        owner.displayClientMessage(Component.translatable("bloodbound.message.nullification_spent")
                                .withStyle(ChatFormatting.DARK_GRAY), true);
                    }
                    continue;
                }
            } else if (gameTime - state.lastSpentAt >= ModPerks.NULL_REGEN_DELAY_TICKS
                    && ritual.charges() < ritual.capacity()) {
                ritual.refill(ModPerks.NULL_REGEN_PER_SECOND / 20.0F);
            }
            standing.add(ritual);
        }
        active = standing.isEmpty() ? List.of() : List.copyOf(standing);
        if (standing.isEmpty() && !STATES.isEmpty() && gameTime % 200L == 0L) {
            STATES.clear();
        }
    }

    // --- the protection ---

    /** The Nullification covering this block, or null. */
    @Nullable
    private static RitualManager.Ritual covering(Level level, BlockPos pos) {
        List<RitualManager.Ritual> rituals = active;
        if (rituals.isEmpty()) {
            return null;
        }
        Vec3 centre = Vec3.atCenterOf(pos);
        for (RitualManager.Ritual ritual : rituals) {
            if (ritual.dimension() == level.dimension() && ritual.isAlive() && ritual.covers(centre)) {
                return ritual;
            }
        }
        return null;
    }

    /** Pays for a block saved: ten charges a point of hardness. An empty ritual saves it for free. */
    private static void pay(RitualManager.Ritual ritual, BlockState state, Level level, BlockPos pos) {
        float hardness = Math.max(0.0F, state.getDestroySpeed(level, pos));
        State record = STATES.computeIfAbsent(ritual.id(), id -> new State(level.getGameTime()));
        record.lastSpentAt = level.getGameTime();
        if (ritual.charges() > 0.0F && !ritual.spend(hardness * ModPerks.NULL_COST_PER_HARDNESS)) {
            record.emptySince = level.getGameTime();
        }
    }

    /**
     * Whether a block change is destruction inside a Nullification, and so has to be turned away —
     * in which case it is paid for here. Called by {@code LevelMixin} for every block change.
     */
    public static boolean refusesChange(Level level, BlockPos pos, BlockState next, int flags) {
        if (active.isEmpty() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
            return false;
        }
        // Moves: a piston, or a mod carrying blocks off the way a piston would.
        if ((flags & Block.UPDATE_MOVE_BY_PISTON) != 0) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || current.getBlock() instanceof LiquidBlock || !destroys(current, next)) {
            return false;
        }
        // Falling, burning out and decaying are the world moving, not somebody breaking it.
        if (current.getBlock() instanceof FallingBlock || current.getBlock() instanceof BaseFireBlock
                || (current.getBlock() instanceof LeavesBlock && !current.getValue(LeavesBlock.PERSISTENT))
                || current.getDestroySpeed(level, pos) < 0.0F || REFUSED_PLACEMENTS.contains(pos)) {
            return false;
        }
        RitualManager.Ritual ritual = covering(level, pos);
        if (ritual == null) {
            return false;
        }
        pay(ritual, current, level, pos);
        return true;
    }

    /** A block giving way to nothing: air, a fluid, or fire eating it. */
    private static boolean destroys(BlockState current, BlockState next) {
        return next.isAir() || next.getBlock() instanceof LiquidBlock
                || (next.getBlock() instanceof BaseFireBlock && !(current.getBlock() instanceof BaseFireBlock));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || covering(level, event.getPos()) == null) {
            return;
        }
        event.setCanceled(true);
        // The placement is undone by putting back what was there, which must not read as breaking.
        REFUSED_PLACEMENTS.add(event.getPos().immutable());
        if (event.getEntity() instanceof ServerPlayer player) {
            // The item comes back on the server; the client took it out of the hand already.
            RESYNC.add(player);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        RitualManager.Ritual ritual = covering(level, event.getPos());
        if (ritual != null) {
            event.setCanceled(true);
            pay(ritual, event.getState(), level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (active.isEmpty()) {
            return;
        }
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
                return false;
            }
            RitualManager.Ritual ritual = covering(level, pos);
            if (ritual == null) {
                return false;
            }
            pay(ritual, state, level, pos);
            return true;
        });
    }

    /** The Wither, the dragon, a ravager: anything that tears through blocks itself. */
    @SubscribeEvent
    public static void onMobDestroy(LivingDestroyBlockEvent event) {
        Level level = event.getEntity().level();
        RitualManager.Ritual ritual = level.isClientSide ? null : covering(level, event.getPos());
        if (ritual != null && !event.getState().isAir()) {
            event.setCanceled(true);
            pay(ritual, event.getState(), level, event.getPos());
        }
    }

    /** A piston inside, or pushing or crushing anything inside, does not move at all. */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (active.isEmpty() || !(event.getLevel() instanceof Level level)) {
            return;
        }
        if (covering(level, event.getPos()) != null || covering(level, event.getFaceOffsetPos()) != null) {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver structure = event.getStructureHelper();
        if (structure == null || !structure.resolve()) {
            return;
        }
        for (BlockPos pos : structure.getToPush()) {
            if (covering(level, pos) != null) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : structure.getToDestroy()) {
            if (covering(level, pos) != null) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // --- cleanup ---

    /** A logout: the laying in hand is dropped, the ritual already down stays where it is. */
    public static void logout(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.NULLIFICATION.id(), false);
        RESYNC.remove(player);
    }

    public static void clear(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.NULLIFICATION.id(), false);
        RitualManager.remove(player.getUUID(), ModPerks.NULLIFICATION.id());
    }

    /** Forgets everything, on server shutdown. */
    public static void clear() {
        STATES.clear();
        REFUSED_PLACEMENTS.clear();
        RESYNC.clear();
        active = List.of();
    }
}
