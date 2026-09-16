package net.h3xpy.bloodbound.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.skillcheck.SkillCheckContext;
import net.h3xpy.bloodbound.skillcheck.SkillCheckDifficulty;
import net.h3xpy.bloodbound.skillcheck.SkillCheckManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Guardian Angel: the blow that should have killed you does not — but the reprieve has to be earned.
 * <p>
 * You are pulled back from the edge and left glowing gold, which everyone can see. A few seconds
 * later a run of skill checks starts, and it does not stop until you have landed enough of them.
 * Land them all and you walk away; miss one and the blow catches up with you.
 */
public final class GuardianAngelHandler {

    /** One reprieve in progress. */
    private static final class Trial {
        private final int tier;
        private final int checksNeeded;
        private final long trialAt;
        private int checksLanded;

        private Trial(int tier, int checksNeeded, long trialAt) {
            this.tier = tier;
            this.checksNeeded = checksNeeded;
            this.trialAt = trialAt;
        }
    }

    private static final Map<UUID, Trial> TRIALS = new HashMap<>();
    /** Engraved Tablet's tally of landed checks, spent the next time the player dies. */
    private static final Map<UUID, Integer> TOKENS = new HashMap<>();
    /** Experience held back through a death, handed over once the player is standing again. */
    private static final Map<UUID, Integer> KEPT_XP = new HashMap<>();

    private GuardianAngelHandler() {}

    // --- catching the killing blow ---

    /**
     * Read after armour and enchantments have had their say but before the health comes off, so
     * "would have killed you" means what it says rather than what the raw blow looked like.
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // The void and /kill still get through: the perk buys a second chance, not immortality.
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (event.getNewDamage() < player.getHealth() + player.getAbsorptionAmount()
                || TRIALS.containsKey(player.getUUID())) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.GUARDIAN_ANGEL);
        long gameTime = player.level().getGameTime();
        if (tier <= 0 || data.isOnCooldown(ModPerks.GUARDIAN_ANGEL.id(), gameTime)) {
            return;
        }

        event.setNewDamage(0.0F);

        float percent = (float) ModPerks.GUARDIAN_ANGEL.value(ModPerks.GUARDIAN_ANGEL_HEAL_PERCENT, tier) / 100.0F;
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * percent));

        // Somewhere inside the tier's window, so the wait cannot be counted off and the trial
        // never starts at a moment the player had already planned for.
        int shortest = ModPerks.GUARDIAN_ANGEL.ticks(ModPerks.GUARDIAN_ANGEL_DELAY_MIN, tier);
        int longest = ModPerks.GUARDIAN_ANGEL.ticks(ModPerks.GUARDIAN_ANGEL_DELAY_MAX, tier);
        int delay = shortest + player.getRandom().nextInt(Math.max(1, longest - shortest + 1));
        if (data.isAddonActive(ModAddons.BLOOD_STAINED_BOOK)) {
            delay += ModAddons.BLOOD_STAINED_BOOK_EXTRA_SECONDS * 20;
        }

        int needed = ModPerks.GUARDIAN_ANGEL.intValue(ModPerks.GUARDIAN_ANGEL_CHECKS, tier);
        if (data.isAddonActive(ModAddons.BELIEVERS_EYE)) {
            // Never below one: a trial with nothing to land would not be a trial.
            needed = Math.max(1, needed - ModAddons.BELIEVERS_EYE_FEWER_CHECKS);
        }

        TRIALS.put(player.getUUID(), new Trial(tier, needed, gameTime + delay));
        markSaved(player, true);

        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.05D);
        player.displayClientMessage(Component.translatable("bloodbound.message.guardian_angel_saved", needed)
                .withStyle(ChatFormatting.GOLD), true);
    }

    // --- the trial ---

    /** Opens the trial once the grace period is up, and keeps a check on screen until it is over. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        Trial trial = TRIALS.get(player.getUUID());
        if (trial == null) {
            return;
        }
        if (!player.isAlive()) {
            TRIALS.remove(player.getUUID());
            markSaved(player, false);
            return;
        }
        if (gameTime < trial.trialAt || SkillCheckManager.hasActive(player)) {
            return;
        }

        // Rolled from the tick rather than chained off the last result, so a check that could not be
        // raised — because a heal already had one on screen — is simply tried again next tick.
        SkillCheckManager.start(player, SkillCheckContext.GUARDIAN, SkillCheckDifficulty.NORMAL,
                SkillCheckDifficulty.NORMAL.zoneWidth() * ModPerks.GUARDIAN_ANGEL_ZONE_SCALE);
    }

    /** A Guardian Angel skill check resolved. */
    public static void onSkillCheckResult(ServerPlayer player, boolean success) {
        Trial trial = TRIALS.get(player.getUUID());
        if (trial == null) {
            return;
        }

        if (!success) {
            fail(player, trial);
            return;
        }

        trial.checksLanded++;
        if (PerkDataManager.get(player).isAddonActive(ModAddons.ENGRAVED_TABLET)) {
            TOKENS.merge(player.getUUID(), 1, Integer::sum);
        }

        if (trial.checksLanded < trial.checksNeeded) {
            player.displayClientMessage(Component.translatable("bloodbound.message.guardian_angel_progress",
                    trial.checksLanded, trial.checksNeeded).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        survive(player, trial);
    }

    private static void survive(ServerPlayer player, Trial trial) {
        TRIALS.remove(player.getUUID());
        markSaved(player, false);

        PlayerPerkData data = PerkDataManager.get(player);
        data.setCooldown(ModPerks.GUARDIAN_ANGEL.id(), player.level().getGameTime(),
                ModPerks.GUARDIAN_ANGEL.cooldownTicks(trial.tier));
        PerkDataManager.sync(player);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        player.displayClientMessage(Component.translatable("bloodbound.message.guardian_angel_survived")
                .withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * The blow catches up. The cooldown goes on first: without it the killing damage below would be
     * caught by the perk it just got past, and the trial would start over forever.
     */
    private static void fail(ServerPlayer player, Trial trial) {
        TRIALS.remove(player.getUUID());
        markSaved(player, false);

        PerkDataManager.get(player).setCooldown(ModPerks.GUARDIAN_ANGEL.id(),
                player.level().getGameTime(), ModPerks.GUARDIAN_ANGEL.cooldownTicks(trial.tier));

        player.displayClientMessage(Component.translatable("bloodbound.message.guardian_angel_failed")
                .withStyle(ChatFormatting.DARK_RED), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0F, 0.5F);
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    // --- Engraved Tablet ---

    /**
     * Every check landed under the angel's eye is a share of what death would otherwise take. Held
     * here rather than dropped with the rest, and handed back on the far side of the respawn.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TRIALS.remove(player.getUUID());
        markSaved(player, false);

        Integer tokens = TOKENS.remove(player.getUUID());
        if (tokens == null || tokens <= 0) {
            return;
        }

        int carried = experiencePoints(player);
        if (carried <= 0) {
            return;
        }

        // Twelve percent a token, and never more than the lot: nine checks banked is everything the
        // player was carrying.
        float share = Math.min(1.0F, tokens * ModAddons.ENGRAVED_TABLET_XP_PER_TOKEN);
        int kept = Math.round(carried * share);
        if (kept > 0) {
            KEPT_XP.put(player.getUUID(), kept);
        }
    }

    /**
     * Experience the player is actually carrying, in points.
     * <p>
     * Worked out from the level and the bar rather than read off { totalExperience}: that field
     * only counts what { giveExperiencePoints} has handed over, so anything set by a command,
     * or carried through an earlier death, is missing from it — which is why the tablet looked like
     * it did nothing.
     */
    private static int experiencePoints(ServerPlayer player) {
        return pointsForLevel(player.experienceLevel)
                + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /** Vanilla's own level-to-points curve, in its three pieces. */
    private static int pointsForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5D * level * level - 40.5D * level + 360.0D);
        }
        return (int) (4.5D * level * level - 162.5D * level + 2220.0D);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Integer kept = KEPT_XP.remove(player.getUUID());
        if (kept != null && kept > 0) {
            player.giveExperiencePoints(kept);
            player.displayClientMessage(Component.translatable("bloodbound.message.engraved_tablet_kept", kept)
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** Forgets a player entirely, on logout. */
    public static void clear(UUID playerId) {
        TRIALS.remove(playerId);
        TOKENS.remove(playerId);
        KEPT_XP.remove(playerId);
    }

    // --- the gold glow ---

    /**
     * Puts the player on the mod's own scoreboard team, which exists only to colour the outline
     * gold. The team is made on first use and left in place; being on it means nothing else.
     */
    private static void markSaved(ServerPlayer player, boolean saved) {
        player.setGlowingTag(saved);

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(ModPerks.GUARDIAN_ANGEL_TEAM);
        if (saved) {
            if (team == null) {
                team = scoreboard.addPlayerTeam(ModPerks.GUARDIAN_ANGEL_TEAM);
                team.setColor(ChatFormatting.GOLD);
            }
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            return;
        }

        // Only ever take them off our own team, and only if they are actually on it:
        // removePlayerFromTeam throws otherwise, and this is called from paths that may run twice
        // over one rescue — a failed trial kills the player, and the death clears up after it.
        if (team != null && scoreboard.getPlayersTeam(player.getScoreboardName()) == team) {
            scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
        }
    }
}
