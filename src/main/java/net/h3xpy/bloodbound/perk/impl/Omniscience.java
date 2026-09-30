package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.advancement.ModAdvancements;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.UnderTheRadarHandler;
import net.h3xpy.bloodbound.network.OmnisciencePayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Omniscience: hold still and everything worth seeing lights up through the walls, for the one
 * player who earned it.
 * <p>
 * The scan is the expensive part, so it only runs once the player has actually settled, and then
 * only every couple of seconds. Moving at all puts the whole thing away on the spot.
 */
public final class Omniscience {

    /** Spyware: players whose reveal is still up after they moved, and the tick it comes down. */
    private static final Map<UUID, Long> LINGER_UNTIL = new HashMap<>();

    private Omniscience() {}

    /**
     * Spyware: a reveal does not go out the moment the player moves — it holds for a few seconds
     * more. The addon is written once in {@code AuraRevealHandler} for perks that reveal an aura;
     * Omniscience draws its own outlines instead, so it has to honour the same promise itself.
     *
     * @return true while the reveal should be left standing
     */
    private static boolean lingers(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (!data.isAddonActive(ModAddons.SPYWARE)) {
            return false;
        }
        Long until = LINGER_UNTIL.get(player.getUUID());
        if (until == null) {
            LINGER_UNTIL.put(player.getUUID(), gameTime + ModAddons.SPYWARE_EXTRA_TICKS);
            return true;
        }
        if (gameTime < until) {
            return true;
        }
        LINGER_UNTIL.remove(player.getUUID());
        return false;
    }

    public static void clear(UUID playerId) {
        LINGER_UNTIL.remove(playerId);
    }

    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.OMNISCIENCE);
        if (tier <= 0) {
            if (data.isOmniscienceRevealed()) {
                conceal(player, data);
            }
            return;
        }

        if (data.hasOmniscienceMoved(player.getX(), player.getY(), player.getZ(),
                ModPerks.OMNISCIENCE_MOVE_EPSILON)) {
            data.markOmniscienceMoved(player.getX(), player.getY(), player.getZ(), gameTime);
            if (data.isOmniscienceRevealed() && !lingers(player, data, gameTime)) {
                conceal(player, data);
            }
            return;
        }
        // Standing still again: whatever was lingering goes back to being a live reveal.
        LINGER_UNTIL.remove(player.getUUID());

        long stillFor = gameTime - data.omniscienceStillSince();
        if (stillFor < ModPerks.OMNISCIENCE.ticks(ModPerks.OMNISCIENCE_STILL_SECONDS, tier)) {
            return;
        }

        // Already showing, and not yet due a refresh: nothing to do this tick.
        if (data.isOmniscienceRevealed() && gameTime < data.omniscienceRefreshAt()) {
            return;
        }

        reveal(player, data, tier, gameTime);
    }

    private static void reveal(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        double radius = EnhancedPerception.radius(player,
                ModPerks.OMNISCIENCE.value(ModPerks.OMNISCIENCE_RADIUS, tier));
        AABB box = player.getBoundingBox().inflate(radius);

        List<BlockPos> ores = new ArrayList<>();
        List<BlockPos> containers = new ArrayList<>();
        scanBlocks(player, tier, radius, ores, containers);

        boolean ransomware = data.isAddonActive(ModAddons.RANSOMWARE);
        List<Integer> entities = new ArrayList<>();
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity != player && entity.isAlive()
                    && !(entity instanceof ServerPlayer hidden && UnderTheRadarHandler.blocksReveal(hidden))) {
                entities.add(entity.getId());
                // Ransomware, as everywhere else: being seen is tiring. Omniscience never goes
                // through the aura-reveal code, so it says so here.
                if (ransomware) {
                    entity.addEffect(new MobEffectInstance(ModEffects.EXHAUSTED,
                            ModAddons.RANSOMWARE_EXHAUST_TICKS, ModAddons.RANSOMWARE_EXHAUST_LEVEL - 1,
                            false, true, true));
                }
            }
        }

        data.setOmniscienceRevealed(true, gameTime + ModPerks.OMNISCIENCE_REFRESH_TICKS);
        PacketDistributor.sendToPlayer(player, new OmnisciencePayload(
                OmnisciencePayload.SOURCE_OMNISCIENCE, ores, containers, entities));
    }

    /** Walks the cube around the player once, sorting what it finds into ores and containers. */
    private static void scanBlocks(ServerPlayer player, int tier, double radius,
            List<BlockPos> ores, List<BlockPos> containers) {
        BlockPos centre = player.blockPosition();
        int reach = (int) Math.ceil(radius);
        double radiusSq = radius * radius;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    if (x * x + y * y + z * z > radiusSq) {
                        continue;
                    }
                    cursor.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
                    if (!player.level().isLoaded(cursor)) {
                        continue;
                    }

                    BlockState state = player.level().getBlockState(cursor);
                    if (state.is(Tags.Blocks.CHESTS) || state.is(Tags.Blocks.BARRELS)) {
                        containers.add(cursor.immutable());
                    } else if (state.is(Tags.Blocks.ORES) && isVisibleAtTier(state, tier)) {
                        ores.add(cursor.immutable());
                        if (state.is(Blocks.ANCIENT_DEBRIS)) {
                            ModAdvancements.grant(player, ModAdvancements.CHEATER);
                        }
                    }
                }
            }
        }

        // The cap used to be applied as the sweep went, which walks the cube from one corner: past a
        // certain radius it filled up before ever reaching the other side, and the reveal showed
        // only what lay one way. Keeping the nearest instead is even all round.
        keepNearest(ores, centre);
        keepNearest(containers, centre);
    }

    /** Trims a list to the blocks closest to the player, which are the ones worth showing. */
    private static void keepNearest(List<BlockPos> found, BlockPos centre) {
        if (found.size() <= ModPerks.OMNISCIENCE_MAX_BLOCKS) {
            return;
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(centre)));
        found.subList(ModPerks.OMNISCIENCE_MAX_BLOCKS, found.size()).clear();
    }

    /** The best ore stays hidden until the perk is worth it. */
    private static boolean isVisibleAtTier(BlockState state, int tier) {
        if (state.is(Blocks.ANCIENT_DEBRIS)) {
            return tier >= ModPerks.OMNISCIENCE_DEBRIS_TIER;
        }
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return tier >= ModPerks.OMNISCIENCE_DIAMOND_TIER;
        }
        return true;
    }

    /** Puts the reveal away. Cheap enough to call whenever, but only sends when it was up. */
    public static void conceal(ServerPlayer player, PlayerPerkData data) {
        if (!data.isOmniscienceRevealed()) {
            return;
        }
        data.setOmniscienceRevealed(false, 0L);
        PacketDistributor.sendToPlayer(player,
                OmnisciencePayload.clear(OmnisciencePayload.SOURCE_OMNISCIENCE));
    }
}
