package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.UnderTheRadarHandler;
import net.h3xpy.bloodbound.network.OmnisciencePayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.h3xpy.bloodbound.ritual.RitualManager;
import net.h3xpy.bloodbound.ritual.RitualSetup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Eavesdrop: a ritual that does nothing to anyone, and tells you everything.
 * <p>
 * Watched from up to a hundred blocks away: every chest around it, and everything that moves near
 * it, is outlined for the owner alone. Standing about in its reach for half a minute is what
 * eventually gives it away — and like any ritual, a blow is what breaks it.
 */
public final class Eavesdrop {

    /** Where the moving things were last seen, per ritual owner, so movement can be measured. */
    private static final Map<UUID, Map<Integer, Vec3>> LAST_SEEN = new HashMap<>();
    /** The chests round each ritual, found rarely and remembered: the sweep is far too dear to repeat. */
    private static final Map<UUID, List<BlockPos>> CHESTS = new HashMap<>();
    /** Owners whose screen is currently holding this perk's outlines. */
    private static final Set<UUID> REPORTING = new HashSet<>();
    /** Spyware: what each owner is still being shown after it stopped moving, and until when. */
    private static final Map<UUID, Map<Integer, Long>> LINGERING = new HashMap<>();

    /** How often the chests around a ritual are looked for again. */
    private static final int CHEST_SCAN_INTERVAL = 100;

    private Eavesdrop() {}

    /** The press does nothing: the work is the hold. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.EAVESDROP);
        if (tier <= 0 || data.isOnCooldown(ModPerks.EAVESDROP.id(), player.level().getGameTime())) {
            RitualSetup.stop(player, ModPerks.EAVESDROP.id(), false);
            return;
        }
        RitualSetup.setHolding(player, ModPerks.EAVESDROP.id(), tier, holding);
    }

    /** Advances the laying. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.EAVESDROP);
        if (tier <= 0) {
            drop(player.getUUID());
            return;
        }

        int needed = ModPerks.EAVESDROP.ticks(ModPerks.EAVESDROP_SETUP, tier);
        int held = RitualSetup.advance(player, ModPerks.EAVESDROP.id(), needed);
        if (held < needed) {
            return;
        }

        RitualSetup.stop(player, ModPerks.EAVESDROP.id(), false);
        LAST_SEEN.remove(player.getUUID());
        CHESTS.remove(player.getUUID());
        // Laid at its own radius: Enhanced Perception is added live by the ritual, so it follows the
        // perk and Adware being fitted or taken off after the ritual is down.
        RitualManager.place(player, ModPerks.EAVESDROP.id(), RitualManager.Mood.HELPFUL, tier,
                ModPerks.EAVESDROP.value(ModPerks.EAVESDROP_RADIUS, tier), 0.0F);
        data.setCooldown(ModPerks.EAVESDROP.id(), gameTime, ModPerks.EAVESDROP.cooldownTicks(tier));
        PerkDataManager.sync(player);
        player.displayClientMessage(Component.translatable("bloodbound.message.eavesdrop_placed")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /** Works every ritual on the server. Called once a tick. */
    public static void tickTraps(MinecraftServer server, long gameTime) {
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            RitualManager.Ritual trap = RitualManager.get(owner.getUUID(), ModPerks.EAVESDROP.id());
            if (trap == null || !trap.isAlive()) {
                // The ritual is gone, however it went, or out of reach in an unloaded chunk. The
                // outlines it drew are the owner's screen until they are taken back, and nothing else
                // will do it.
                stopReporting(owner);
            }
        }

        // Every ritual keeps giving itself away to loiterers, its owner online or not; only one whose
        // owner is about has anybody to report to.
        for (RitualManager.Ritual trap : RitualManager.all(ModPerks.EAVESDROP.id())) {
            if (!trap.isAlive()) {
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(trap.ownerId());
            ServerLevel level = server.getLevel(trap.dimension());
            if (level == null) {
                continue;
            }

            watchers(level, trap);

            if (owner == null || gameTime % ModPerks.EAVESDROP_SWEEP_INTERVAL != 0L) {
                continue;
            }
            boolean watching = owner.level().dimension() == trap.dimension()
                    && owner.position().distanceToSqr(trap.position())
                            <= ModPerks.EAVESDROP_OWNER_RANGE * ModPerks.EAVESDROP_OWNER_RANGE;
            if (!watching) {
                // Too far to hear it: the outlines go, the ritual stays.
                stopReporting(owner);
                continue;
            }
            report(level, trap, owner, gameTime);
        }
    }

    /** Takes back whatever this ritual was showing its owner. Safe to call when nothing is up. */
    private static void stopReporting(ServerPlayer owner) {
        if (REPORTING.remove(owner.getUUID())) {
            PacketDistributor.sendToPlayer(owner,
                    OmnisciencePayload.clear(OmnisciencePayload.SOURCE_EAVESDROP));
        }
    }

    /** Sends the owner what the ritual can hear: the chests around it, and whatever is moving. */
    private static void report(ServerLevel level, RitualManager.Ritual trap, ServerPlayer owner,
            long gameTime) {
        List<BlockPos> chests = CHESTS.get(owner.getUUID());
        if (chests == null || gameTime % CHEST_SCAN_INTERVAL == 0L) {
            chests = scanChests(level, trap);
            CHESTS.put(owner.getUUID(), chests);
        }

        Map<Integer, Vec3> seen = LAST_SEEN.computeIfAbsent(owner.getUUID(), id -> new HashMap<>());
        Map<Integer, Vec3> now = new HashMap<>();
        List<Integer> moving = new ArrayList<>();

        for (LivingEntity entity : trap.occupants(level)) {
            if (entity == owner || (entity instanceof ServerPlayer hidden
                    && UnderTheRadarHandler.blocksReveal(hidden))) {
                continue;
            }
            Vec3 previous = seen.get(entity.getId());
            now.put(entity.getId(), entity.position());
            // Standing still is being quiet: only what moves between two sweeps is reported.
            if (previous != null && previous.distanceToSqr(entity.position())
                    > ModPerks.EAVESDROP_MOVED * ModPerks.EAVESDROP_MOVED) {
                moving.add(entity.getId());
            }
        }
        LAST_SEEN.put(owner.getUUID(), now);

        // Enhanced Perception's addons, which this ritual draws for itself rather than through an aura
        // reveal: Spyware keeps a mover outlined a little after it stops, Ransomware tires it.
        PlayerPerkData data = PerkDataManager.get(owner);
        Map<Integer, Long> lingering = LINGERING.computeIfAbsent(owner.getUUID(), id -> new HashMap<>());
        if (data.isAddonActive(ModAddons.SPYWARE)) {
            for (int id : moving) {
                lingering.put(id, gameTime + ModAddons.SPYWARE_EXTRA_TICKS);
            }
            lingering.values().removeIf(until -> until <= gameTime);
            for (Map.Entry<Integer, Long> entry : lingering.entrySet()) {
                if (now.containsKey(entry.getKey()) && !moving.contains(entry.getKey())) {
                    moving.add(entry.getKey());
                }
            }
        } else {
            lingering.clear();
        }
        if (data.isAddonActive(ModAddons.RANSOMWARE)) {
            for (LivingEntity entity : trap.occupants(level)) {
                if (moving.contains(entity.getId())) {
                    entity.addEffect(new MobEffectInstance(ModEffects.EXHAUSTED, ModAddons.RANSOMWARE_EXHAUST_TICKS,
                            ModAddons.RANSOMWARE_EXHAUST_LEVEL - 1, false, true, true));
                }
            }
        }

        REPORTING.add(owner.getUUID());
        PacketDistributor.sendToPlayer(owner, new OmnisciencePayload(
                OmnisciencePayload.SOURCE_EAVESDROP, List.of(), chests, moving));
    }

    /**
     * Every chest and barrel in the ritual's reach — and, under a Bad Omen, in the reach of every
     * ritual it shares its circle with.
     */
    private static List<BlockPos> scanChests(ServerLevel level, RitualManager.Ritual trap) {
        List<BlockPos> chests = new ArrayList<>();
        scanCircle(level, trap, chests);
        for (RitualManager.Ritual sibling : RitualManager.linked(trap)) {
            scanCircle(level, sibling, chests);
        }
        return chests;
    }

    private static void scanCircle(ServerLevel level, RitualManager.Ritual circle, List<BlockPos> chests) {
        int reach = (int) Math.ceil(circle.radius());
        double radiusSq = circle.radius() * circle.radius();
        BlockPos centre = BlockPos.containing(circle.position());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    if (x * x + y * y + z * z > radiusSq) {
                        continue;
                    }
                    cursor.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Tags.Blocks.CHESTS) || state.is(Tags.Blocks.BARRELS)) {
                        BlockPos found = cursor.immutable();
                        if (!chests.contains(found)) {
                            chests.add(found);
                        }
                    }
                }
            }
        }
    }

    /** Loitering in its reach for half a minute, all told, is what finds it. */
    private static void watchers(ServerLevel level, RitualManager.Ritual trap) {
        for (ServerPlayer nearby : level.players()) {
            if (nearby.getUUID().equals(trap.ownerId())) {
                continue;
            }
            boolean inside = trap.covers(nearby.position());
            int ticks = trap.trackInside(nearby.getUUID(), inside, false);
            if (inside && ticks >= ModPerks.EAVESDROP_REVEAL_AFTER_TICKS) {
                RitualManager.revealTo(nearby, trap, ModPerks.EAVESDROP_REVEAL_TICKS);
            }
        }
    }

    /** Takes a player's ritual away, and everything remembered about it. */
    private static void drop(UUID ownerId) {
        LAST_SEEN.remove(ownerId);
        LINGERING.remove(ownerId);
        CHESTS.remove(ownerId);
        RitualManager.remove(ownerId, ModPerks.EAVESDROP.id());
    }

    /** A logout: the laying in hand is dropped, the ritual already down stays where it is. */
    public static void logout(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.EAVESDROP.id(), false);
        REPORTING.remove(player.getUUID());
        LAST_SEEN.remove(player.getUUID());
        LINGERING.remove(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        RitualSetup.stop(player, ModPerks.EAVESDROP.id(), false);
        drop(player.getUUID());
        REPORTING.add(player.getUUID());
        stopReporting(player);
    }
}
