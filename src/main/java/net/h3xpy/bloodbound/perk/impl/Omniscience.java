package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.List;

import net.h3xpy.bloodbound.event.UnderTheRadarHandler;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.network.OmnisciencePayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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

    private Omniscience() {}

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
            if (data.isOmniscienceRevealed()) {
                conceal(player, data);
            }
            return;
        }

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
        double radius = ModPerks.OMNISCIENCE.value(ModPerks.OMNISCIENCE_RADIUS, tier);
        AABB box = player.getBoundingBox().inflate(radius);

        List<BlockPos> ores = new ArrayList<>();
        List<BlockPos> containers = new ArrayList<>();
        scanBlocks(player, tier, radius, ores, containers);

        List<Integer> entities = new ArrayList<>();
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity != player && entity.isAlive()
                    && !(entity instanceof ServerPlayer hidden && UnderTheRadarHandler.blocksReveal(hidden))) {
                entities.add(entity.getId());
            }
        }

        data.setOmniscienceRevealed(true, gameTime + ModPerks.OMNISCIENCE_REFRESH_TICKS);
        PacketDistributor.sendToPlayer(player, new OmnisciencePayload(ores, containers, entities));
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
                        if (containers.size() < ModPerks.OMNISCIENCE_MAX_BLOCKS) {
                            containers.add(cursor.immutable());
                        }
                    } else if (state.is(Tags.Blocks.ORES) && isVisibleAtTier(state, tier)) {
                        if (ores.size() < ModPerks.OMNISCIENCE_MAX_BLOCKS) {
                            ores.add(cursor.immutable());
                        }
                    }
                }
            }
        }
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
        PacketDistributor.sendToPlayer(player, OmnisciencePayload.clear());
    }
}
