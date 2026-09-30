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
 * <p>
 * The tougher the mob, the better its odds of a big payout. A zombie's twenty health is the yardstick
 * and rolls exactly as the configured weights say; anything frailer leans towards the small end of
 * them, anything sturdier towards the large end, and from fifty health up the roll is skipped for a
 * flat six.
 */
public final class SoulShardDropHandler {

    /** Max health that rolls the configured weights as they stand: a zombie's. */
    private static final double REFERENCE_HEALTH = 20.0D;
    /** From this much max health a kill pays {@link #GUARANTEED_SHARDS}, no roll. */
    private static final double GUARANTEED_HEALTH = 50.0D;
    private static final int GUARANTEED_SHARDS = 6;

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

        int shards = rollFor(entity);

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

    /**
     * The shard roll, leaned by the mob's max health. The roll is bent rather than the weights
     * replaced, so a server's own weights still decide what a zombie drops and everything else is
     * measured against that: raised to the power reference / health, it drifts towards 0 for a
     * silverfish and towards 1 for an enderman.
     */
    private static int rollFor(LivingEntity entity) {
        double health = Math.max(1.0D, entity.getMaxHealth());
        if (health >= GUARANTEED_HEALTH) {
            return GUARANTEED_SHARDS;
        }
        double roll = Math.pow(entity.getRandom().nextDouble(), REFERENCE_HEALTH / health);
        return Config.rollShardCount(Math.min(roll, 0.999999D));
    }
}
