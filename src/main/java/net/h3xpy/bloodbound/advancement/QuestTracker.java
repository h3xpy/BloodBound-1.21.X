package net.h3xpy.bloodbound.advancement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.RelentlessHandler;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRarity;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.h3xpy.bloodbound.perk.impl.HangedMan;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Keeps track of the quest board advancements added after the first batch: the collection goals,
 * and the feats whose moment spans more than one event. Each perk tells this class when its part
 * happens; whatever has to add up over a long time is kept in the player's data, so it survives a
 * logout, while what only lasts a few seconds lives here and is forgotten with the server.
 */
public final class QuestTracker {

    // --- the numbers each one asks for ---

    private static final int COLLECTOR_PERKS = 10;
    private static final int COMPLETIONIST_PERKS = 5;
    private static final int UNSTABLE_GENIUS_ADDONS = 3;
    private static final int GREEN_THUMB_OFFERINGS = 10;
    private static final int SHARD_HOARDER_SHARDS = 500;
    /** Web levels for Weaver, P25, P50 and P100. */
    private static final int[] WEB_LEVELS = { 10, 25, 50, 100 };
    /** Ticks a launch stays "the pad's doing", for Go Home!: a fall can take a while to come. */
    private static final int PAD_FALL_TICKS = 300;
    private static final int GO_HOME_KILLS = 3;
    private static final float DONT_WORRY_HEALTH = 8.0F;
    private static final int UPSIDE_DOWN_TICKS = 5 * 60 * 20;
    private static final int SURGEON_STREAK = 5;
    private static final double WATCH_YOUR_STEP_GAP = 5.0D;
    private static final int WATCH_YOUR_STEP_TICKS = 20 * 20;
    private static final int GOTCHA_TICKS = 3 * 20;
    private static final float NOT_ON_MY_WATCH_BLOCKS = 1000.0F;
    private static final float HERBALIST_HEALTH = 100.0F;
    private static final int STILL_STANDING_TICKS = 60 * 20;
    /** How often the player checks that do not need a whole tick's attention run. */
    private static final int CHECK_INTERVAL = 20;

    // --- progress names in the player's data ---

    private static final String OFFERINGS_BURNT = "offerings_burnt";
    private static final String BLOCKS_SAVED = "blocks_saved";
    private static final String HERBAL_HEALING = "herbal_healing";

    // --- short-lived state ---

    /** Something a Spring Pad threw, and whose pad it was, until when. */
    private record Launched(UUID padId, UUID ownerId, long until) {}

    /** The last coil that caught something, per victim: whose, where, and when. */
    private record Caught(UUID ownerId, BlockPos where, long at) {}

    /** A Target Found teleport: whom it went after, and until when a hit counts. */
    private record Hunt(UUID victimId, long until) {}

    private static final Map<UUID, Launched> LAUNCHED = new HashMap<>();
    private static final Map<UUID, Integer> PAD_KILLS = new HashMap<>();
    private static final Map<UUID, Long> UPSIDE_DOWN_SINCE = new HashMap<>();
    private static final Map<UUID, Integer> SURGEON = new HashMap<>();
    private static final Map<UUID, Caught> CAUGHT = new HashMap<>();
    private static final Map<UUID, Hunt> HUNTS = new HashMap<>();
    /** Someone kept Broken by Final Blow: by whom, and until when a kill still counts. */
    private record Execution(UUID playerId, long until) {}

    /** Victims kept Broken by Final Blow. */
    private static final Map<UUID, Execution> EXECUTIONER = new HashMap<>();
    /** How long after the last step on a trail a kill still counts for Execution: Broken lingers that long at most. */
    private static final int EXECUTION_TICKS = 12 * 20;
    private static final Map<UUID, Long> BELOW_THRESHOLD_SINCE = new HashMap<>();

    private QuestTracker() {}

    // --- collection, the web, the offerings ---

    /**
     * Everything that can be read straight off the player's data: perks and addons owned, the
     * loadout, the web level, offerings burnt. Cheap, and safe to call whenever any of it may have
     * changed.
     */
    public static void checkCollection(ServerPlayer player) {
        PlayerPerkData data = PerkDataManager.get(player);

        int owned = 0;
        int maxed = 0;
        for (Perk perk : PerkRegistry.all()) {
            int tier = data.getUnlockedTier(perk);
            if (tier > 0) {
                owned++;
            }
            if (tier >= Perk.MAX_TIER) {
                maxed++;
            }
        }
        if (owned >= COLLECTOR_PERKS) {
            ModAdvancements.grant(player, ModAdvancements.COLLECTOR);
        }
        if (maxed >= COMPLETIONIST_PERKS) {
            ModAdvancements.grant(player, ModAdvancements.COMPLETIONIST);
        }

        int unstable = 0;
        for (Addon addon : AddonRegistry.all()) {
            if (addon.rarity() == AddonRarity.UNSTABLE && data.isAddonUnlocked(addon.id())) {
                unstable++;
            }
        }
        if (unstable >= UNSTABLE_GENIUS_ADDONS) {
            ModAdvancements.grant(player, ModAdvancements.UNSTABLE_GENIUS);
        }

        int kitted = 0;
        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            ResourceLocation perkId = data.getLoadoutSlot(slot);
            if (perkId != null && data.getUnlockedTier(perkId) > 0 && data.equippedAddon(perkId) != null) {
                kitted++;
            }
        }
        if (kitted >= PlayerPerkData.LOADOUT_SIZE) {
            ModAdvancements.grant(player, ModAdvancements.FULL_KIT);
        }

        ResourceLocation[] webAdvancements = {
            ModAdvancements.WEAVER, ModAdvancements.P25, ModAdvancements.P50, ModAdvancements.P100 };
        for (int i = 0; i < WEB_LEVELS.length; i++) {
            if (data.webLevel() >= WEB_LEVELS[i]) {
                ModAdvancements.grant(player, webAdvancements[i]);
            }
        }

        if (data.questProgress(OFFERINGS_BURNT) >= GREEN_THUMB_OFFERINGS) {
            ModAdvancements.grant(player, ModAdvancements.GREEN_THUMB);
        }
    }

    /** An offering burnt at the end of a level. */
    public static void onOfferingBurnt(ServerPlayer player) {
        PerkDataManager.get(player).addQuestProgress(OFFERINGS_BURNT, 1.0F);
        checkCollection(player);
    }

    // --- every tick ---

    /** The checks that look at the player as they are: effects, inventory, health, gravity. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        UUID id = player.getUUID();

        // Upside Down: five minutes on end, by the player's own perk.
        if (HangedMan.isSelfInverted(player)) {
            long since = UPSIDE_DOWN_SINCE.computeIfAbsent(id, key -> gameTime);
            if (gameTime - since >= UPSIDE_DOWN_TICKS) {
                ModAdvancements.grant(player, ModAdvancements.UPSIDE_DOWN);
            }
        } else {
            UPSIDE_DOWN_SINCE.remove(id);
        }

        // Still Standing: a minute on end at or under Relentless's own threshold. Wooden Sword lifts
        // the threshold altogether, so it does not count.
        int relentless = data.getActiveTier(ModPerks.RELENTLESS);
        if (relentless > 0 && !data.isAddonActive(ModAddons.WOODEN_SWORD) && player.isAlive()
                && player.getHealth() <= RelentlessHandler.threshold(data, relentless)) {
            long since = BELOW_THRESHOLD_SINCE.computeIfAbsent(id, key -> gameTime);
            if (gameTime - since >= STILL_STANDING_TICKS) {
                ModAdvancements.grant(player, ModAdvancements.STILL_STANDING);
            }
        } else {
            BELOW_THRESHOLD_SINCE.remove(id);
        }

        if (gameTime % CHECK_INTERVAL != 0L) {
            return;
        }
        if (player.hasEffect(ModEffects.EXHAUSTED) && player.hasEffect(ModEffects.BROKEN)
                && player.hasEffect(ModEffects.BLEEDING)) {
            ModAdvancements.grant(player, ModAdvancements.BAD_LUCK);
        }
        if (PerkDataManager.countShards(player) >= SHARD_HOARDER_SHARDS) {
            ModAdvancements.grant(player, ModAdvancements.SHARD_HOARDER);
        }
    }

    // --- Spring Pad: Go Home! ---

    /** Something was thrown by a pad; if the fall ends it, it counts for that pad. */
    public static void onPadLaunch(UUID padId, UUID ownerId, LivingEntity rider, long gameTime) {
        if (!rider.getUUID().equals(ownerId)) {
            LAUNCHED.put(rider.getUUID(), new Launched(padId, ownerId, gameTime + PAD_FALL_TICKS));
        }
    }

    // --- Holy Sanctum: Don't Worry, I'm Here ---

    /** A bubble went up around this player, raised by someone else. */
    public static void onSheltered(ServerPlayer owner, LivingEntity sheltered) {
        if (sheltered instanceof ServerPlayer other && other != owner && other.getHealth() <= DONT_WORRY_HEALTH) {
            ModAdvancements.grant(owner, ModAdvancements.DONT_WORRY_IM_HERE);
        }
    }

    // --- Steady Hands: I AM A SURGEON ---

    /** A skill check with a great zone resolved: only greats keep the streak going. */
    public static void onGreatZoneCheck(ServerPlayer player, boolean great) {
        if (!great) {
            SURGEON.remove(player.getUUID());
            return;
        }
        int streak = SURGEON.merge(player.getUUID(), 1, Integer::sum);
        if (streak >= SURGEON_STREAK) {
            ModAdvancements.grant(player, ModAdvancements.I_AM_A_SURGEON);
        }
    }

    // --- Barbed Wire: Watch Your Step ---

    /** A coil caught something: two of the same owner's, far enough apart and close enough in time, earn it. */
    public static void onCoilCaught(@Nullable ServerPlayer owner, @Nullable UUID ownerId, LivingEntity victim,
            BlockPos where, long gameTime) {
        if (ownerId == null) {
            return;
        }
        Caught previous = CAUGHT.put(victim.getUUID(), new Caught(ownerId, where, gameTime));
        if (owner != null && previous != null && previous.ownerId().equals(ownerId)
                && gameTime - previous.at() <= WATCH_YOUR_STEP_TICKS
                && Math.sqrt(previous.where().distSqr(where)) > WATCH_YOUR_STEP_GAP) {
            ModAdvancements.grant(owner, ModAdvancements.WATCH_YOUR_STEP);
        }
    }

    // --- Nullification: Not On My Watch ---

    /** A block saved by a player's Nullification. Counted while they are online to hear of it. */
    public static void onBlockSaved(MinecraftServer server, UUID ownerId) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null && PerkDataManager.get(owner).addQuestProgress(BLOCKS_SAVED, 1.0F) >= NOT_ON_MY_WATCH_BLOCKS) {
            ModAdvancements.grant(owner, ModAdvancements.NOT_ON_MY_WATCH);
        }
    }

    // --- Final Blow: Execution ---

    /** Final Blow has just kept this one Broken, on this player's behalf. */
    public static void onFinalBlowBroken(ServerPlayer player, LivingEntity victim) {
        EXECUTIONER.put(victim.getUUID(), new Execution(player.getUUID(), player.level().getGameTime() + EXECUTION_TICKS));
    }

    // --- Target Found: Gotcha ---

    public static void onTargetTeleport(ServerPlayer player, @Nullable UUID victimId, long gameTime) {
        if (victimId != null) {
            HUNTS.put(player.getUUID(), new Hunt(victimId, gameTime + GOTCHA_TICKS));
        }
    }

    // --- Green Herbs: Herbalist ---

    public static void onHerbalHealing(ServerPlayer healer, float amount) {
        if (PerkDataManager.get(healer).addQuestProgress(HERBAL_HEALING, amount) >= HERBALIST_HEALTH) {
            ModAdvancements.grant(healer, ModAdvancements.HERBALIST);
        }
    }

    // --- the moments in between ---

    /** Gotcha: the blow has to land, so it is read once the damage is done. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || HUNTS.isEmpty()) {
            return;
        }
        Hunt hunt = HUNTS.get(attacker.getUUID());
        if (hunt != null && hunt.victimId().equals(event.getEntity().getUUID())
                && attacker.level().getGameTime() <= hunt.until()) {
            HUNTS.remove(attacker.getUUID());
            ModAdvancements.grant(attacker, ModAdvancements.GOTCHA);
        }
    }

    /** Go Home! and Execution are both decided by how something died. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim.getServer() == null) {
            return;
        }
        MinecraftServer server = victim.getServer();
        long now = victim.level().getGameTime();

        Launched launched = LAUNCHED.remove(victim.getUUID());
        if (launched != null && now <= launched.until() && event.getSource().is(DamageTypeTags.IS_FALL)) {
            int kills = PAD_KILLS.merge(launched.padId(), 1, Integer::sum);
            ServerPlayer owner = server.getPlayerList().getPlayer(launched.ownerId());
            if (owner != null && kills >= GO_HOME_KILLS) {
                ModAdvancements.grant(owner, ModAdvancements.GO_HOME);
            }
        }

        Execution execution = EXECUTIONER.remove(victim.getUUID());
        if (execution != null && now <= execution.until() && victim.hasEffect(ModEffects.BROKEN)
                && event.getSource().getEntity() instanceof ServerPlayer killer
                && killer.getUUID().equals(execution.playerId())) {
            ModAdvancements.grant(killer, ModAdvancements.EXECUTION);
        }
    }

    /** Lets go of short-lived records that ran out. Called once a second for the whole server. */
    public static void prune(long gameTime) {
        LAUNCHED.values().removeIf(launched -> gameTime > launched.until());
        CAUGHT.values().removeIf(caught -> gameTime - caught.at() > WATCH_YOUR_STEP_TICKS);
        HUNTS.values().removeIf(hunt -> gameTime > hunt.until());
        EXECUTIONER.values().removeIf(execution -> gameTime > execution.until());
    }

    /** Forgets a player's running streaks and timers, on logout or death. */
    public static void clear(UUID playerId) {
        UPSIDE_DOWN_SINCE.remove(playerId);
        BELOW_THRESHOLD_SINCE.remove(playerId);
        SURGEON.remove(playerId);
        HUNTS.remove(playerId);
    }

    /** Forgets everything short-lived, on server shutdown. */
    public static void clearAll() {
        LAUNCHED.clear();
        PAD_KILLS.clear();
        UPSIDE_DOWN_SINCE.clear();
        SURGEON.clear();
        CAUGHT.clear();
        HUNTS.clear();
        EXECUTIONER.clear();
        BELOW_THRESHOLD_SINCE.clear();
    }
}
