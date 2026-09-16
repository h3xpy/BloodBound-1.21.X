package net.h3xpy.bloodbound.heal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.GreenHerbs;
import net.h3xpy.bloodbound.perk.impl.TeamSpirit;
import net.h3xpy.bloodbound.skillcheck.SkillCheckContext;
import net.h3xpy.bloodbound.skillcheck.SkillCheckDifficulty;
import net.h3xpy.bloodbound.skillcheck.SkillCheckManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The co-op healing ability, unlocked by perks such as Surgical Suture.
 * <p>
 * Stand near a player who is crouched and holding still, hold the heal key, and they mend at one
 * point of health every {@link #TICKS_PER_POINT} ticks. Skill checks interrupt at random: land one
 * for a bonus point, miss it and the target loses one and the heal stalls.
 */
public final class HealManager {

    /** How far the healer can stand from the target. */
    public static final double HEAL_RANGE = 3.0D;
    /** A full 20 HP heal takes 30 seconds, so one point every 1.5 seconds. */
    public static final int TICKS_PER_POINT = 30;
    /** Skill checks are rolled once a second. */
    private static final int CHECK_INTERVAL = 20;
    /** Odds of a skill check on each roll. */
    private static final float CHECK_CHANCE = 0.30F;
    /** How long healing stalls after a missed check. */
    private static final int BLOCK_TICKS = 20;
    /** Horizontal movement per tick tolerated before the heal breaks. */
    private static final double MOVE_EPSILON = 0.02D;

    /** Hard ceiling on how long one heal may run. A full 20 HP heal takes 600 ticks. */
    private static final int MAX_SESSION_TICKS = 1200;

    private static final Map<UUID, HealSession> SESSIONS = new HashMap<>();
    /** Healers currently holding the heal key down. */
    private static final Set<UUID> HOLDING = new HashSet<>();
    /** Players currently holding the slot key of their Patch Up. Its own set, so releasing one key
     * can never cut short a heal the other one started. */
    private static final Set<UUID> SELF_HOLDING = new HashSet<>();

    private HealManager() {}

    /** The heal key was pressed or released. */
    public static void setHolding(ServerPlayer healer, boolean holding) {
        // A bleeding player has their own wound to see to first. Dressing it needs no perk at all, so
        // the key never reaches the co-op heal while there is blood to stop.
        if (BleedingHandler.isBleeding(healer)) {
            BleedingHandler.setCuring(healer, holding);
            return;
        }
        if (holding) {
            HOLDING.add(healer.getUUID());
            tryStart(healer);
        } else {
            HOLDING.remove(healer.getUUID());
            // Only the heal key's own session; a Patch Up running on the slot key is not its business.
            HealSession session = SESSIONS.get(healer.getUUID());
            if (session != null && !session.isSelfHeal()) {
                stop(healer, "bloodbound.message.heal_stopped");
            }
        }
    }

    private static void tryStart(ServerPlayer healer) {
        if (SESSIONS.containsKey(healer.getUUID())) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(healer);
        if (!data.hasHealingAbility()) {
            healer.displayClientMessage(Component.translatable("bloodbound.message.no_healing_ability")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        ServerPlayer target = findTarget(healer);
        if (target == null) {
            healer.displayClientMessage(Component.translatable("bloodbound.message.no_heal_target")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        SESSIONS.put(healer.getUUID(), new HealSession(healer, target, healer.level().getGameTime(), false));
        healer.level().playSound(null, healer.blockPosition(), SoundEvents.WOOL_PLACE,
                SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /**
     * Patch Up: the player mends themselves for as long as they hold their slot key. Runs on the
     * same loop and the same skill checks, just slower and without a second player involved.
     */
    public static void setSelfHealHolding(ServerPlayer player, boolean holding) {
        if (holding) {
            SELF_HOLDING.add(player.getUUID());
            tryStartSelfHeal(player);
            return;
        }
        SELF_HOLDING.remove(player.getUUID());
        HealSession session = SESSIONS.get(player.getUUID());
        if (session != null && session.isSelfHeal()) {
            stop(player, "bloodbound.message.heal_stopped");
        }
    }

    /**
     * Patch Up, pressed: starts mending if nothing is running, and stops a self-heal that is.
     */
    public static void toggleSelfHeal(ServerPlayer player) {
        HealSession session = SESSIONS.get(player.getUUID());
        if (session != null && session.isSelfHeal()) {
            SELF_HOLDING.remove(player.getUUID());
            stop(player, "bloodbound.message.heal_stopped");
            return;
        }
        SELF_HOLDING.remove(player.getUUID());
        setSelfHealHolding(player, true);
    }

    private static void tryStartSelfHeal(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) {
            return;
        }
        if (!player.isCrouching()) {
            player.displayClientMessage(Component.translatable("bloodbound.message.patch_up_needs_crouch")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            player.displayClientMessage(Component.translatable("bloodbound.message.patch_up_full_health")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        SESSIONS.put(player.getUUID(), new HealSession(player, player, player.level().getGameTime(), true));
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE,
                SoundSource.PLAYERS, 0.6F, 0.9F);
    }

    /** Nearest crouched, injured player in range. */
    @Nullable
    private static ServerPlayer findTarget(ServerPlayer healer) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer candidate : healer.serverLevel().players()) {
            if (candidate == healer || !isValidTarget(candidate)) {
                continue;
            }
            double distance = candidate.distanceToSqr(healer);
            if (distance <= HEAL_RANGE * HEAL_RANGE && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isValidTarget(ServerPlayer target) {
        return target.isAlive() && target.isCrouching() && target.getHealth() < target.getMaxHealth();
    }

    /** Called once per tick for each healer that has a session running. */
    public static void tick(ServerPlayer healer) {
        HealSession session = SESSIONS.get(healer.getUUID());
        if (session == null) {
            return;
        }

        long gameTime = healer.level().getGameTime();
        ServerPlayer target = session.target();

        // A full heal takes 30 seconds, so anything past this means the session is stuck — most
        // likely because a client stopped reporting the key as released after losing focus.
        if (session.hasOverrun(gameTime, MAX_SESSION_TICKS)) {
            HOLDING.remove(healer.getUUID());
            SELF_HOLDING.remove(healer.getUUID());
            stop(healer, "bloodbound.message.heal_interrupted");
            return;
        }
        if (target.getHealth() >= target.getMaxHealth()) {
            stop(healer, "bloodbound.message.heal_complete");
            return;
        }
        // Both kinds run only while their own key is held. A self-heal has no second player, so it
        // is the only one that skips the range check.
        boolean holdingKey = session.isSelfHeal()
                ? SELF_HOLDING.contains(healer.getUUID())
                : HOLDING.contains(healer.getUUID());
        boolean stillValid = healer.isAlive() && !target.isRemoved() && isValidTarget(target) && holdingKey
                && (session.isSelfHeal() || target.distanceToSqr(healer) <= HEAL_RANGE * HEAL_RANGE);
        if (!stillValid) {
            stop(healer, "bloodbound.message.heal_interrupted");
            return;
        }
        if (session.hasEitherMoved(MOVE_EPSILON)) {
            stop(healer, "bloodbound.message.heal_interrupted");
            return;
        }
        session.rememberPositions();

        if (session.isBlocked(gameTime)) {
            return;
        }

        int ticksPerPoint = ticksPerPoint(healer, session);
        if (session.advance(ticksPerPoint)) {
            mend(healer, target, 1.0F);
        }

        PlayerPerkData data = PerkDataManager.get(healer);
        if (session.isSelfHeal() && data.isAddonActive(ModAddons.FIRST_AID_SPRAY_CAN)) {
            mendBystanders(healer, session, ticksPerPoint);
        }

        // Only roll a new check when nothing else is on screen for this player.
        float checkChance = CHECK_CHANCE
                + (data.isAddonActive(ModAddons.CLEAN_GLOVES) ? ModAddons.CLEAN_GLOVES_CHECK_CHANCE : 0.0F);
        if (!SkillCheckManager.hasActive(healer) && session.shouldRollCheck(gameTime, CHECK_INTERVAL)
                && healer.getRandom().nextFloat() < checkChance) {
            SkillCheckManager.start(healer, SkillCheckContext.HEAL, SkillCheckDifficulty.NORMAL);
        }

        if (gameTime % 5 == 0) {
            healer.displayClientMessage(Component.translatable("bloodbound.message.healing",
                    target.getName(), (int) target.getHealth(), (int) target.getMaxHealth()), true);
        }
    }

    /**
     * First Aid Spray Can: everyone standing close by mends alongside the player, on their own
     * slower clock. Nothing is asked of them — they only have to be there.
     */
    private static void mendBystanders(ServerPlayer healer, HealSession session, int ticksPerPoint) {
        int slowed = Math.max(1, Math.round(ticksPerPoint / ModAddons.FIRST_AID_SPRAY_RATE));
        if (!session.advanceBystanders(slowed)) {
            return;
        }

        double rangeSq = ModAddons.FIRST_AID_SPRAY_RANGE * ModAddons.FIRST_AID_SPRAY_RANGE;
        for (ServerPlayer other : healer.serverLevel().players()) {
            if (other != healer && other.isAlive() && other.distanceToSqr(healer) <= rangeSq) {
                mend(healer, other, 1.0F);
            }
        }
    }

    /**
     * Puts health into a patient and tells Green Herbs about it, which is the only place that keeps
     * count of how much one player has put into another.
     */
    private static void mend(ServerPlayer healer, ServerPlayer target, float amount) {
        target.heal(amount);
        GreenHerbs.recordHealing(healer, target, amount);
        TeamSpirit.recordHealing(healer, target, amount);
    }

    /** Result of a skill check raised during a heal. */
    public static void onSkillCheckResult(ServerPlayer healer, boolean success) {
        HealSession session = SESSIONS.get(healer.getUUID());
        if (session == null) {
            return;
        }
        ServerPlayer target = session.target();
        long gameTime = healer.level().getGameTime();

        if (success) {
            float amount = 1.0F + assistBonus(healer);
            mend(healer, target, amount);
            return;
        }

        // A miss costs the target a point, but can never be what kills them.
        target.setHealth(Math.max(1.0F, target.getHealth() - 1.0F));
        session.block(gameTime, BLOCK_TICKS);
        healer.displayClientMessage(Component.translatable("bloodbound.message.heal_missed")
                .withStyle(ChatFormatting.RED), true);
    }

    /**
     * How long a single point of health takes. Patch Up runs on its own timer; co-op healing takes
     * the base rate and divides it by whatever speed bonuses the healer brings.
     */
    private static int ticksPerPoint(ServerPlayer healer, HealSession session) {
        PlayerPerkData data = PerkDataManager.get(healer);

        if (session.isSelfHeal()) {
            int patchUp = data.getActiveTier(ModPerks.PATCH_UP);
            if (patchUp <= 0) {
                return TICKS_PER_POINT;
            }
            double seconds = ModPerks.PATCH_UP.value(ModPerks.PATCH_UP_FULL_HEAL_SECONDS, patchUp);
            if (data.isAddonActive(ModAddons.BANDAGES_WRAP)) {
                seconds /= ModAddons.BANDAGES_WRAP_SPEED;
            }
            // The medic perks make you a faster medic on yourself too: Caretaker and We Can Do This
            // speed Patch Up up exactly as they speed a co-op heal.
            seconds /= 1.0D + healSpeedBonus(healer);
            return Math.max(1, (int) Math.round(seconds * 20.0D / healer.getMaxHealth()));
        }

        // We Can Do This only pays out while the healer themselves is untouched, which healSpeedBonus
        // settles along with everything else.
        return Math.max(1, (int) Math.round(TICKS_PER_POINT / (1.0D + healSpeedBonus(healer))));
    }

    /**
     * What the healer's perks are worth as a share off every healing job they do, co-op or not.
     * Bleeding reads it too: the perks that make you a faster medic make you a faster one on
     * yourself.
     */
    public static double healSpeedBonus(ServerPlayer healer) {
        PlayerPerkData data = PerkDataManager.get(healer);
        double bonus = 0.0D;
        int caretaker = data.getActiveTier(ModPerks.CARETAKER);
        if (caretaker > 0) {
            bonus += ModPerks.CARETAKER.value(ModPerks.CARETAKER_BONUS, caretaker) / 100.0D;
        }
        int weCanDoThis = data.getActiveTier(ModPerks.WE_CAN_DO_THIS);
        if (weCanDoThis > 0 && healer.getHealth() >= healer.getMaxHealth()) {
            bonus += ModPerks.WE_CAN_DO_THIS.value(ModPerks.WE_CAN_DO_THIS_BONUS, weCanDoThis) / 100.0D;
        }
        return bonus;
    }

    /** Extra healing per landed check, from the healer's Surgical Suture tier. */
    private static float assistBonus(ServerPlayer healer) {
        int tier = PerkDataManager.get(healer).getActiveTier(ModPerks.SURGICAL_SUTURE);
        return tier > 0
                ? (float) ModPerks.SURGICAL_SUTURE.value(ModPerks.SURGICAL_ASSIST_BONUS, tier)
                : 0.0F;
    }

    public static void stop(ServerPlayer healer, String messageKey) {
        HealSession session = SESSIONS.remove(healer.getUUID());
        if (session == null) {
            return;
        }
        if (session.isSelfHeal()) {
            // A self-heal that ends, however it ends, leaves Patch Up ready to be pressed again.
            SELF_HOLDING.remove(healer.getUUID());
        }
        SkillCheckManager.cancel(healer);
        healer.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_GRAY), true);
    }

    /** Forgets a player entirely, on logout or death. */
    public static void clear(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        HOLDING.remove(player.getUUID());
        SELF_HOLDING.remove(player.getUUID());
        // Also drop any session where this player was the one being healed.
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().target() == player);
    }

    public static boolean isHealing(ServerPlayer healer) {
        return SESSIONS.containsKey(healer.getUUID());
    }
}
