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
import net.h3xpy.bloodbound.perk.PerkGlow;
import net.h3xpy.bloodbound.perk.impl.BlessingOfLife;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The co-op healing ability, unlocked by perks such as Surgical Suture.
 * <p>
 * Look straight at a player or a creature within {@link #HEAL_RANGE} blocks and hold the heal key:
 * they mend at one point of health every {@link #TICKS_PER_POINT} ticks, both of you free to move.
 * Skill checks interrupt at random: land one for a bonus point, miss it and the target loses one
 * and the heal stalls. It is the one kind of healing that gets through Broken.
 */
public final class HealManager {

    /** How far the healer can stand from the target. */
    public static final double HEAL_RANGE = 6.0D;
    /** How long the healer may look away from the target before the heal breaks. */
    private static final int LOOK_AWAY_TICKS = 10;
    /** Set while a co-op heal puts health in, so Broken lets it through. */
    private static boolean mendingThroughBroken;
    /** A full 20 HP heal takes 30 seconds, so one point every 1.5 seconds. */
    public static final int TICKS_PER_POINT = 30;
    /**
     * Every kind of mending in the mod runs this many times faster than its written rate: co-op
     * healing, Patch Up and dressing a Bleeding wound alike. One knob rather than three, so the
     * numbers the perks advertise keep their proportions.
     */
    public static final double GLOBAL_SPEED = 2.0D;
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
        // Standing in a Blessing Of Life is a licence to heal in itself, perk or no perk.
        if (!data.hasHealingAbility() && !BlessingOfLife.isBlessed(healer)) {
            healer.displayClientMessage(Component.translatable("bloodbound.message.no_healing_ability")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }

        LivingEntity target = findTarget(healer);
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

    /** The injured player or creature under the healer's crosshair, within range and in sight. */
    @Nullable
    private static LivingEntity findTarget(ServerPlayer healer) {
        LivingEntity looked = lookedAt(healer);
        return looked != null && isValidTarget(looked) ? looked : null;
    }

    /** Whatever living thing the healer's crosshair rests on within range, walls stopping the look. */
    @Nullable
    private static LivingEntity lookedAt(ServerPlayer healer) {
        Vec3 eyes = healer.getEyePosition();
        Vec3 end = eyes.add(healer.getLookAngle().scale(HEAL_RANGE));
        BlockHitResult wall = healer.level().clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, healer));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        AABB sweep = healer.getBoundingBox().expandTowards(end.subtract(eyes)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(healer, eyes, end, sweep,
                entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity != healer,
                eyes.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean isValidTarget(LivingEntity target) {
        return target.isAlive() && !(target instanceof ArmorStand) && target.getHealth() < target.getMaxHealth();
    }

    /** Called once per tick for each healer that has a session running. */
    public static void tick(ServerPlayer healer) {
        HealSession session = SESSIONS.get(healer.getUUID());
        if (session == null) {
            return;
        }

        long gameTime = healer.level().getGameTime();
        LivingEntity target = session.target();

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
                && (session.isSelfHeal() ? healer.isCrouching()
                        : target.level() == healer.level() && target.distanceToSqr(healer) <= HEAL_RANGE * HEAL_RANGE);
        if (!stillValid) {
            stop(healer, "bloodbound.message.heal_interrupted");
            return;
        }
        if (session.isSelfHeal()) {
            // Patch Up still asks the player to crouch and hold still.
            if (session.hasEitherMoved(MOVE_EPSILON)) {
                stop(healer, "bloodbound.message.heal_interrupted");
                return;
            }
            session.rememberPositions();
        } else if (session.lookedAway(lookedAt(healer) != target) > LOOK_AWAY_TICKS) {
            // Both may move, but the healer has to keep their eyes on the patient.
            stop(healer, "bloodbound.message.heal_interrupted");
            return;
        }

        glowHealing(healer, session.isSelfHeal(), true);

        if (session.isBlocked(gameTime)) {
            return;
        }

        int ticksPerPoint = ticksPerPoint(healer, session);
        if (session.advance(ticksPerPoint)) {
            mend(healer, target, 1.0F, !session.isSelfHeal());
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
                mend(healer, other, 1.0F, false);
            }
        }
    }

    /**
     * Puts health into a patient and tells Green Herbs about it, which is the only place that keeps
     * count of how much one player has put into another.
     *
     * @param throughBroken a co-op heal, the one kind that Broken does not stop
     */
    private static void mend(ServerPlayer healer, LivingEntity target, float amount, boolean throughBroken) {
        mendingThroughBroken = throughBroken;
        try {
            target.heal(amount);
        } finally {
            mendingThroughBroken = false;
        }
        if (target instanceof ServerPlayer patient) {
            GreenHerbs.recordHealing(healer, patient, amount);
            TeamSpirit.recordHealing(healer, patient, amount);
        }
    }

    /** Whether the heal going on right now is a co-op one, which Broken lets through. */
    public static boolean isMendingThroughBroken() {
        return mendingThroughBroken;
    }

    /**
     * Result of a skill check raised during a heal.
     *
     * @param multiplier applied to the health a landed check puts in (Steady Hands' great zone)
     */
    public static void onSkillCheckResult(ServerPlayer healer, boolean success, float multiplier) {
        HealSession session = SESSIONS.get(healer.getUUID());
        if (session == null) {
            return;
        }
        LivingEntity target = session.target();
        long gameTime = healer.level().getGameTime();

        if (success) {
            float amount = (1.0F + assistBonus(healer)) * multiplier;
            mend(healer, target, amount, !session.isSelfHeal());
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
                return Math.max(1, (int) Math.round(TICKS_PER_POINT / GLOBAL_SPEED));
            }
            double seconds = ModPerks.PATCH_UP.value(ModPerks.PATCH_UP_FULL_HEAL_SECONDS, patchUp);
            if (data.isAddonActive(ModAddons.BANDAGES_WRAP)) {
                seconds /= ModAddons.BANDAGES_WRAP_SPEED;
            }
            // The medic perks make you a faster medic on yourself too: Caretaker and We Can Do This
            // speed Patch Up up exactly as they speed a co-op heal.
            seconds /= GLOBAL_SPEED * (1.0D + healSpeedBonus(healer));
            return Math.max(1, (int) Math.round(seconds * 20.0D / healer.getMaxHealth()));
        }

        // We Can Do This only pays out while the healer themselves is untouched, which healSpeedBonus
        // settles along with everything else.
        return Math.max(1, (int) Math.round(TICKS_PER_POINT / (GLOBAL_SPEED * (1.0D + healSpeedBonus(healer)))));
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
        // Whose ritual it is makes no difference: what counts is standing in one.
        if (BlessingOfLife.isBlessed(healer)) {
            bonus += ModPerks.BLESSING_HEAL_BONUS;
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

    /**
     * The medic perks light up while they are at work: Caretaker and We Can Do This on any heal (the
     * latter only while the healer is untouched), Green Herbs and Team Spirit on someone else.
     */
    private static void glowHealing(ServerPlayer healer, boolean selfHeal, boolean on) {
        PerkGlow.set(healer, ModPerks.CARETAKER, on);
        PerkGlow.set(healer, ModPerks.WE_CAN_DO_THIS, on && healer.getHealth() >= healer.getMaxHealth());
        PerkGlow.set(healer, ModPerks.GREEN_HERBS, on && !selfHeal);
        PerkGlow.set(healer, ModPerks.TEAM_SPIRIT, on && !selfHeal);
    }

    public static void stop(ServerPlayer healer, String messageKey) {
        HealSession session = SESSIONS.remove(healer.getUUID());
        glowHealing(healer, false, false);
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
