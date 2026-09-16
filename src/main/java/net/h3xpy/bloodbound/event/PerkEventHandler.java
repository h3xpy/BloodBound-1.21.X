package net.h3xpy.bloodbound.event;

import java.util.ArrayList;
import java.util.List;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.damage.PerkDamageSource;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.effect.EffectDurations;
import net.h3xpy.bloodbound.effect.MovementTracker;
import net.h3xpy.bloodbound.heal.HealManager;
import net.h3xpy.bloodbound.mark.MarkManager;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.AdvancedMovementDevice;
import net.h3xpy.bloodbound.perk.impl.AntiExhaustionSyringe;
import net.h3xpy.bloodbound.perk.impl.BarbedWire;
import net.h3xpy.bloodbound.perk.impl.BewareThePowerOfAnAngel;
import net.h3xpy.bloodbound.perk.impl.BrokenMovementDevice;
import net.h3xpy.bloodbound.perk.impl.CatchingUp;
import net.h3xpy.bloodbound.perk.impl.Flashbang;
import net.h3xpy.bloodbound.perk.impl.FragNade;
import net.h3xpy.bloodbound.perk.impl.FromTheDark;
import net.h3xpy.bloodbound.perk.impl.GreenHerbs;
import net.h3xpy.bloodbound.perk.impl.HealingRunes;
import net.h3xpy.bloodbound.perk.impl.LowCostMovementDevice;
import net.h3xpy.bloodbound.perk.impl.NoOneGetsAway;
import net.h3xpy.bloodbound.perk.impl.Omniscience;
import net.h3xpy.bloodbound.perk.impl.OutOfBreath;
import net.h3xpy.bloodbound.perk.impl.SurgicalSuture;
import net.h3xpy.bloodbound.perk.impl.TargetFound;
import net.h3xpy.bloodbound.perk.impl.TeamSpirit;
import net.h3xpy.bloodbound.perk.impl.Tinkerer;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.h3xpy.bloodbound.skillcheck.SkillCheckManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Runtime behaviour of the passive perks, plus the per-tick upkeep of an in-progress Close Call dash.
 */
public final class PerkEventHandler {

    /** Id of the transient speed modifier Low Profile adds and removes. */
    private static final ResourceLocation LOW_PROFILE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "low_profile_speed");

    /**
     * Movement bonus per speed level, matching what the vanilla Speed effect grants so "Speed III"
     * moves the player exactly as fast as the potion would.
     */
    private static final double SPEED_PER_LEVEL = 0.2D;

    /** How often expired cooldown entries get swept up. */
    private static final int COOLDOWN_PRUNE_INTERVAL = 100;

    /** How often Lightbringer resyncs shifted cooldowns so the HUD keeps up. */
    private static final int COOLDOWN_SYNC_INTERVAL = 10;

    /** How often the banked recovery is taken off harmful effects. */
    private static final int EFFECT_FLUSH_INTERVAL = 20;

    /** Night vision is granted in long slices and refreshed early, so it never starts flashing. */
    private static final int NIGHT_VISION_DURATION = 400;
    private static final int NIGHT_VISION_REFRESH_BELOW = 300;

    private PerkEventHandler() {}

    // --- Perfect Landing ---

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getDistance() <= ModPerks.PERFECT_LANDING_MIN_FALL) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.PERFECT_LANDING);
        if (tier <= 0) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (data.isOnCooldown(ModPerks.PERFECT_LANDING.id(), gameTime)) {
            return;
        }

        float distance = event.getDistance();
        event.setCanceled(true);
        player.resetFallDistance();

        // Both fall addons buy their speed on the way down and pay for it on the ground: what is
        // left of the sprint is shorter than the perk alone would give.
        int duration = ModPerks.PERFECT_LANDING.ticks(0, tier);
        if (data.isAddonActive(ModAddons.MOMENTUM_FORMULA)) {
            duration = Math.round(duration * ModAddons.MOMENTUM_SPEED_DURATION);
        }

        int level = ModPerks.PERFECT_LANDING_SPEED_LEVEL;
        if (data.isAddonActive(ModAddons.DEAD_WEIGHT)) {
            level += ModAddons.DEAD_WEIGHT_SPEED_LEVELS;
            duration = Math.round(duration * ModAddons.DEAD_WEIGHT_SPEED_DURATION);
            crush(player, distance);
        }

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, level - 1,
                false, true, true));

        int cooldown = ModPerks.PERFECT_LANDING.cooldownTicks(tier);
        if (data.isAddonActive(ModAddons.HELIUM_INFLATED_BALLOON)) {
            cooldown = Math.round(cooldown * ModAddons.HELIUM_COOLDOWN);
        }
        data.setCooldown(ModPerks.PERFECT_LANDING.id(), gameTime, cooldown);
        PerkDataManager.sync(player);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP,
                SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /**
     * Dead Weight: whatever was standing where the player came down wears the fall. The distance
     * stands in for the speed — it is what vanilla measures a fall by, and the two only part company
     * once terminal velocity is reached.
     */
    private static void crush(ServerPlayer player, float distance) {
        float damage = Math.min(ModAddons.DEAD_WEIGHT_MAX_DAMAGE,
                distance * ModAddons.DEAD_WEIGHT_DAMAGE_PER_BLOCK);
        if (damage <= 0.0F) {
            return;
        }

        AABB landing = player.getBoundingBox().inflate(0.3D, 0.5D, 0.3D);
        boolean hit = false;
        for (LivingEntity victim : player.serverLevel().getEntitiesOfClass(LivingEntity.class, landing)) {
            if (victim == player || !victim.isAlive()) {
                continue;
            }
            victim.hurt(PerkDamageSource.of(player.damageSources().playerAttack(player), "dead_weight"), damage);
            hit = true;
        }

        if (hit) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }

    // --- Close Call: damage immunity during the dash ---

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // The void and /kill still get through, so a dashing player can never end up unkillable.
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (PerkDataManager.get(player).isDamageImmune(player.level().getGameTime())) {
            event.setCanceled(true);
        }
    }

    // --- Low Profile, and dash upkeep ---

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        long gameTime = player.level().getGameTime();

        tickLowProfile(player, data);
        tickDash(player, data);
        tickLightbringer(player, data, gameTime);
        tickAdrenaline(player, data, gameTime);
        BrokenMovementDevice.tick(player, data, gameTime);
        LowCostMovementDevice.tick(player, data, gameTime);
        Omniscience.tick(player, data, gameTime);
        NastyBladeHandler.tick(player, data, gameTime);
        BankShotHandler.tick(player, data, gameTime);
        GreenHerbs.tick(player, data, gameTime);
        FromTheDark.tick(player, data, gameTime);
        GuardianAngelHandler.tick(player, data, gameTime);
        AdvancedMovementDevice.tick(player, data, gameTime);
        BewareThePowerOfAnAngel.tick(player, data, gameTime);
        CatchingUp.tick(player, data, gameTime);
        BarbedWire.tick(player, data, gameTime);
        TeamSpirit.tick(player, data, gameTime);
        HealingRunes.tick(player, data, gameTime);
        BeyondVisionHandler.tick(player, data);
        BleedingHandler.tickCure(player, gameTime);
        HuntersInstinctHandler.tick(player, data);
        Flashbang.tick(player, data, gameTime);
        AntiExhaustionSyringe.tick(player, data, gameTime);
        MovementTracker.update(player);
        TargetFound.tick(player, data, gameTime);
        FragNade.tickFallGuard(player, gameTime);
        MarkManager.finalBlowStep(player, data, gameTime);

        SkillCheckManager.tick(player);
        HealManager.tick(player);
        Tinkerer.tick(player);

        // Gel Dressing's absorption has to come off the moment the addon or its perk leaves the
        // loadout, so it is checked here rather than only where the loadout changes.
        SurgicalSuture.enforceAbsorption(player, data);
        if (data.consumeLapsedSutureRetry(gameTime)) {
            SurgicalSuture.onRetryLapsed(player, data, gameTime);
        }

        if (gameTime % COOLDOWN_PRUNE_INTERVAL == 0) {
            data.pruneCooldowns(gameTime);
        }
    }

    /**
     * The Low-Cost Movement Device's shots belong to the world rather than to any one player, so
     * they are stepped once for the whole server instead of per player.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        LowCostMovementDevice.tickShots(event.getServer());
        NoOneGetsAway.tickShots(event.getServer());
        Flashbang.tickGrenades(event.getServer());
        AdvancedMovementDevice.tickShots(event.getServer());
        MarkManager.tick(event.getServer());
        OutOfBreath.tickShots(event.getServer());
        HealingRunes.tickRunes(event.getServer());
        FragNade.tickGrenades(event.getServer());
        UnderTheRadarHandler.tick(event.getServer());
    }

    /**
     * Low Profile uses a transient attribute modifier rather than the Speed effect: it grants the
     * same movement bonus, comes off the instant the player stands up, and never clobbers a speed
     * potion the player happens to be drinking.
     */
    private static void tickLowProfile(ServerPlayer player, PlayerPerkData data) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }

        int tier = data.getActiveTier(ModPerks.LOW_PROFILE);
        boolean active = tier > 0 && player.isCrouching();
        AttributeModifier existing = speed.getModifier(LOW_PROFILE_MODIFIER_ID);

        if (!active) {
            if (existing != null) {
                speed.removeModifier(LOW_PROFILE_MODIFIER_ID);
            }
            clearNightVision(player, data);
            data.resetCrouchTracking();
            return;
        }

        boolean boots = data.isAddonActive(ModAddons.BOOTS_OF_SPEED);

        double amount = SPEED_PER_LEVEL * ModPerks.LOW_PROFILE.intValue(0, tier);
        if (boots) {
            amount += ModAddons.BOOTS_OF_SPEED_BONUS;
        }
        if (existing == null || existing.amount() != amount) {
            speed.removeModifier(LOW_PROFILE_MODIFIER_ID);
            speed.addTransientModifier(new AttributeModifier(LOW_PROFILE_MODIFIER_ID, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        // Boots of Speed trades the night vision away for that extra speed.
        if (boots) {
            clearNightVision(player, data);
        } else {
            applyNightVision(player, data);
        }

        if (data.isAddonActive(ModAddons.STEEL_TOE_BOOT)) {
            tickSteelToeBoot(player, data);
        } else {
            data.resetCrouchTracking();
        }
    }

    /**
     * Keeps night vision topped up while crouched. Refreshed well before it would start flashing,
     * which vanilla does in the last ten seconds.
     */
    private static void applyNightVision(ServerPlayer player, PlayerPerkData data) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current == null || current.getDuration() < NIGHT_VISION_REFRESH_BELOW) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION,
                    ModPerks.LOW_PROFILE_NIGHT_VISION_LEVEL - 1, false, false, true));
            data.setLowProfileNightVision(true);
        }
    }

    private static void clearNightVision(ServerPlayer player, PlayerPerkData data) {
        if (!data.lowProfileNightVision()) {
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        // A potion runs far longer than we ever grant, so a long one is not ours to remove.
        if (current != null && current.getDuration() <= NIGHT_VISION_DURATION) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        data.setLowProfileNightVision(false);
    }

    /** Steel Toe Boot: every few blocks walked crouched returns a point of health. */
    private static void tickSteelToeBoot(ServerPlayer player, PlayerPerkData data) {
        float travelled = data.accumulateCrouchDistance(player.getX(), player.getZ());
        while (travelled >= ModAddons.STEEL_TOE_BLOCKS_PER_HEAL) {
            data.spendCrouchDistance((float) ModAddons.STEEL_TOE_BLOCKS_PER_HEAL);
            travelled -= (float) ModAddons.STEEL_TOE_BLOCKS_PER_HEAL;
            player.heal(1.0F);
        }
    }

    /**
     * Adrenaline fires from the tick rather than the damage event, which is what makes "a blow that
     * takes you straight from healthy to dead kills you" fall out for free: there is no tick between
     * the killing blow and death.
     */
    private static void tickAdrenaline(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.ADRENALINE);
        if (tier <= 0 || !player.isAlive()) {
            return;
        }
        if (data.isDamageImmune(gameTime) || data.isOnCooldown(ModPerks.ADRENALINE.id(), gameTime)) {
            return;
        }
        if (player.getHealth() > ModPerks.ADRENALINE_HEALTH_THRESHOLD) {
            return;
        }

        int duration = ModPerks.ADRENALINE.ticks(ModPerks.ADRENALINE_DURATION, tier);
        data.setDamageImmuneUntil(gameTime + duration);
        player.addEffect(new MobEffectInstance(ModEffects.BROKEN, duration, 0, false, true, true));

        // The cooldown is counted from the end of the window, not the start.
        data.setCooldown(ModPerks.ADRENALINE.id(), gameTime,
                duration + ModPerks.ADRENALINE.cooldownTicks(tier));
        PerkDataManager.sync(player);

        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /**
     * Lightbringer speeds up recovery for everyone in range, the bearer included. The bonus is a
     * percentage of a tick, so it is banked until whole ticks are due.
     * <p>
     * Two bearers standing together do not stack: the strongest aura wins, which keeps the maths
     * predictable and stops a stacked group from erasing cooldowns outright.
     */
    private static void tickLightbringer(ServerPlayer player, PlayerPerkData data, long gameTime) {
        double rate = strongestNearbyAura(player);
        if (rate <= 0.0D) {
            return;
        }

        int extra = data.addRecoveryProgress((float) rate);
        if (extra > 0) {
            data.accelerateCooldowns(extra);
            data.accelerateRecharges(extra);
            data.addPendingEffectTicks(extra);
        }

        // The client works its cooldowns out from expiry times it was last sent, so shifting them
        // here without resyncing would leave the HUD counting down at the wrong rate.
        if (gameTime % COOLDOWN_SYNC_INTERVAL == 0 && data.hasAnyCooldown(gameTime)) {
            PerkDataManager.sync(player);
        }

        if (gameTime % EFFECT_FLUSH_INTERVAL == 0) {
            shortenHarmfulEffects(player, data.takePendingEffectTicks());
        }
    }

    /**
     * Takes time off every harmful effect the player has.
     * <p>
     * {@code MobEffectInstance.mapDuration} only computes a value, it does not store one, and the
     * duration field has no setter — so the effect has to be replaced outright. That is why this is
     * batched once a second rather than run every tick.
     */
    private static void shortenHarmfulEffects(ServerPlayer player, int ticks) {
        if (ticks <= 0) {
            return;
        }

        List<MobEffectInstance> harmful = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                    && !effect.isInfiniteDuration()) {
                harmful.add(effect);
            }
        }

        for (MobEffectInstance effect : harmful) {
            int remaining = effect.getDuration() - ticks;
            if (remaining > 0) {
                // In place: removing and re-adding fires the removal event, which Bleeding and
                // Exhausted read as the effect ending and wiped their bars for.
                EffectDurations.set(player, effect, remaining);
            } else {
                player.removeEffect(effect.getEffect());
            }
        }
    }

    /**
     * The best Lightbringer rate covering this player, as a fraction (0.4 for 40%). Public so the
     * perks that refill continuously rather than on a timer can speed their own refill up by it.
     */
    public static double lightbringerRate(ServerPlayer player) {
        return strongestNearbyAura(player);
    }

    private static double strongestNearbyAura(ServerPlayer player) {
        double best = 0.0D;
        for (ServerPlayer other : player.serverLevel().players()) {
            PlayerPerkData data = PerkDataManager.get(other);
            int tier = data.getActiveTier(ModPerks.LIGHTBRINGER);
            if (tier <= 0) {
                continue;
            }
            // The radius belongs to whoever is casting the aura, not to whoever is standing in it.
            double radius = auraRadius(data);
            if (other.distanceToSqr(player) > radius * radius) {
                continue;
            }
            best = Math.max(best, auraRate(other, data, tier, radius));
        }
        return best;
    }

    private static double auraRadius(PlayerPerkData data) {
        return ModPerks.LIGHTBRINGER_RADIUS
                + (data.isAddonActive(ModAddons.DIRTY_SHROUD) ? ModAddons.DIRTY_SHROUD_EXTRA_RADIUS : 0.0D);
    }

    /** What one player's aura is worth, once their addons have had their say. */
    private static double auraRate(ServerPlayer source, PlayerPerkData data, int tier, double radius) {
        int company = companyWithin(source, radius);

        // Gilded Cross throws the perk's own numbers away: nearly worthless in a crowd, and
        // remarkable when there is nobody else to share it with.
        if (data.isAddonActive(ModAddons.GILDED_CROSS)) {
            double[] table = company > 0 ? ModAddons.GILDED_CROSS_CROWDED : ModAddons.GILDED_CROSS_ALONE;
            return table[Math.clamp(tier - 1, 0, table.length - 1)] / 100.0D;
        }

        double rate = ModPerks.LIGHTBRINGER.value(ModPerks.LIGHTBRINGER_BONUS, tier) / 100.0D;
        if (data.isAddonActive(ModAddons.CHARM_OF_THE_FAITHFUL)) {
            rate += company * ModAddons.CHARM_OF_THE_FAITHFUL_PER_PLAYER;
        }
        return rate;
    }

    /** How many players other than the source are standing in their aura. */
    private static int companyWithin(ServerPlayer source, double radius) {
        double radiusSq = radius * radius;
        int count = 0;
        for (ServerPlayer other : source.serverLevel().players()) {
            if (other != source && other.distanceToSqr(source) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    private static void tickDash(ServerPlayer player, PlayerPerkData data) {
        // Fingerless Glove holds the cooldown back between dashes; if the player never calls for
        // the next one, this is where the bill finally arrives.
        long gameTime = player.level().getGameTime();
        if (data.consumeLapsedRedash(gameTime)) {
            int tier = data.getActiveTier(ModPerks.CLOSE_CALL);
            if (tier > 0) {
                data.setCooldown(ModPerks.CLOSE_CALL.id(), gameTime,
                        Math.round(ModPerks.CLOSE_CALL.cooldownTicks(tier) * ModAddons.FINGERLESS_GLOVE_COOLDOWN));
                PerkDataManager.sync(player);
            }
        }

        if (!data.isDashing()) {
            return;
        }
        double speed = ModPerks.closeCallDashSpeed();
        player.setDeltaMovement(data.dashX() * speed, 0.0D, data.dashZ() * speed);
        // Push the velocity to the client so the dash looks the same on both sides.
        player.hurtMarked = true;
        player.resetFallDistance();
        data.decrementDash();

        if (data.isAddonActive(ModAddons.PROTECTIVE_GLOVE)) {
            shoveAside(player, data);
        }
    }

    /** Protective Glove: anything the dash runs through is thrown clear of it. */
    private static void shoveAside(ServerPlayer player, PlayerPerkData data) {
        AABB reach = player.getBoundingBox().inflate(0.4D);
        for (LivingEntity victim : player.serverLevel().getEntitiesOfClass(LivingEntity.class, reach)) {
            if (victim == player || !victim.isAlive()) {
                continue;
            }
            double dx = victim.getX() - player.getX();
            double dz = victim.getZ() - player.getZ();
            // Something standing dead centre has no direction to be thrown in, so it takes the one
            // the dash is already travelling.
            if (dx * dx + dz * dz < 1.0E-4D) {
                dx = data.dashX();
                dz = data.dashZ();
            }
            // knockback pushes away from the point it is handed, so it gets the player's side of it.
            victim.knockback(ModAddons.PROTECTIVE_GLOVE_KNOCKBACK, -dx, -dz);
            victim.hurtMarked = true;
        }
    }
}
