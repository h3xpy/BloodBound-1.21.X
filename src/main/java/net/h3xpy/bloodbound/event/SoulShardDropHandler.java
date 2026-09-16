package net.h3xpy.bloodbound.event;

import net.h3xpy.bloodbound.Config;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Rolls soul shards into a mob's death drops.
 */
public final class SoulShardDropHandler {

    private SoulShardDropHandler() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity instanceof Player) {
            return;
        }
        if (Config.HOSTILE_MOBS_ONLY.get() && !(entity instanceof Enemy)) {
            return;
        }

        // getEntity() resolves to the shooter for projectiles, so ranged kills count too.
        Entity killer = event.getSource().getEntity();
        if (Config.REQUIRE_PLAYER_KILL.get() && !(killer instanceof Player)) {
            return;
        }

        int shards = Config.rollShardCount(entity.getRandom().nextDouble());

        // Raise The Stakes tops up every hostile kill, even the ones that rolled nothing.
        if (entity instanceof Enemy && killer instanceof ServerPlayer hunter) {
            PlayerPerkData data = PerkDataManager.get(hunter);
            if (data.getActiveTier(ModPerks.RAISE_THE_STAKES) > 0) {
                shards += data.isAddonActive(ModAddons.SCRATCHED_COIN)
                        ? ModAddons.SCRATCHED_COIN_SHARDS
                        : ModPerks.RAISE_THE_STAKES_BONUS_SHARDS;
            }
        }

        if (shards <= 0) {
            return;
        }

        event.getDrops().add(new ItemEntity(entity.level(),
                entity.getX(), entity.getY() + entity.getBbHeight() / 2.0D, entity.getZ(),
                new ItemStack(ModItems.SOUL_SHARD.get(), shards)));
    }
}
