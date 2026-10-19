package net.h3xpy.bloodbound.perk.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.effect.BleedingHandler;
import net.h3xpy.bloodbound.effect.EffectDurations;
import net.h3xpy.bloodbound.effect.ExhaustedHandler;
import net.h3xpy.bloodbound.entity.TripwireEntity;
import net.h3xpy.bloodbound.event.AuraRevealHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.registry.ModEffects;
import net.h3xpy.bloodbound.ritual.TrapRoster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Tripwires: two posts, a wire between them, and whatever walks into it gives itself away.
 * <p>
 * The first post takes a couple of seconds of holding still with the key down. A press of the key
 * then plants the second where the player stands, if it is close enough to the first and nothing
 * solid crosses the line between their tops; until then the owner alone sees that line traced in
 * particles, red while the spot would not do. The cooldown starts once a wire is up.
 */
public final class Tripwires {

    /** A first post being planted. */
    private static final class Setup {
        private final int tier;
        private final Vec3 origin;
        private int ticksHeld;

        private Setup(int tier, Vec3 origin) {
            this.tier = tier;
            this.origin = origin;
        }
    }

    private static final Map<UUID, Setup> SETTING = new HashMap<>();
    /** Each owner's first post, waiting for its second. */
    private static final Map<UUID, TripwireEntity> LONE = new HashMap<>();
    /** Detonator's victims, waiting for their sight to come back to be left weak. */
    private static final Set<UUID> DETONATED = new HashSet<>();

    private static final DustParticleOptions VALID = new DustParticleOptions(new Vector3f(0.85F, 0.85F, 0.95F), 0.7F);
    private static final DustParticleOptions INVALID = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 0.7F);

    private Tripwires() {}

    // --- the key ---

    public static void setHolding(ServerPlayer player, boolean holding) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.TRIPWIRES);
        if (!holding || tier <= 0) {
            if (SETTING.remove(player.getUUID()) != null) {
                player.displayClientMessage(Component.translatable("bloodbound.message.tripwires_stopped")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return;
        }
        // With a first post out, the key plants the second.
        TripwireEntity lone = lonePost(player);
        if (lone != null) {
            plantSecond(player, data, lone, tier);
            return;
        }
        if (data.isOnCooldown(ModPerks.TRIPWIRES.id(), player.level().getGameTime())) {
            return;
        }
        // Traps keep a clear block between them: refused before the seconds of setting up, not after.
        if (!SETTING.containsKey(player.getUUID()) && TrapRoster.refuseCrowded(player)) {
            return;
        }
        SETTING.putIfAbsent(player.getUUID(), new Setup(tier, player.position()));
    }

    /** Nothing: the hold does the work, and the press repeats while the key is down. */
    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        return false;
    }

    // --- planting ---

    private static void plantFirst(ServerPlayer player, int tier) {
        if (TrapRoster.refuseCrowded(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        TripwireEntity post = new TripwireEntity(level, player, tier);
        post.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        // Joining the level is what puts it on the roster, and pulls the oldest wire past the limit.
        level.addFreshEntity(post);
        LONE.put(player.getUUID(), post);
        level.playSound(null, player.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable("bloodbound.message.tripwires_first",
                ModPerks.TRIPWIRES.intValue(ModPerks.TRIPWIRE_RANGE, tier)).withStyle(ChatFormatting.GRAY), true);
    }

    private static void plantSecond(ServerPlayer player, PlayerPerkData data, TripwireEntity lone, int tier) {
        if (TrapRoster.refuseCrowded(player)) {
            return;
        }
        if (!canPlant(player, lone, tier)) {
            player.displayClientMessage(Component.translatable("bloodbound.message.tripwires_invalid")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        LONE.remove(player.getUUID());
        lone.setEnd(player.position());

        long gameTime = player.level().getGameTime();
        data.setCooldown(ModPerks.TRIPWIRES.id(), gameTime, ModPerks.TRIPWIRES.cooldownTicks(tier));
        PerkDataManager.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 0.9F, 1.0F);
        player.displayClientMessage(Component.translatable("bloodbound.message.tripwires_placed",
                TrapRoster.count(TripwireEntity.KIND, player.getUUID()) * 2, lone.trapLimit() * 2)
                .withStyle(ChatFormatting.GRAY), true);
    }

    /** Whether the second post may go where the player stands. */
    private static boolean canPlant(ServerPlayer player, TripwireEntity lone, int tier) {
        if (player.level() != lone.level() || !player.onGround()) {
            return false;
        }
        double range = ModPerks.TRIPWIRES.value(ModPerks.TRIPWIRE_RANGE, tier);
        double distance = player.position().distanceTo(lone.position());
        if (distance > range || distance < ModPerks.TRIPWIRE_MIN_LENGTH) {
            return false;
        }
        Vec3 from = lone.startTop();
        Vec3 to = secondTop(player);
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    private static Vec3 secondTop(ServerPlayer player) {
        return player.position().add(0.0D, ModPerks.TRIPWIRE_POST_HEIGHT, 0.0D);
    }

    private static TripwireEntity lonePost(ServerPlayer player) {
        TripwireEntity lone = LONE.get(player.getUUID());
        if (lone != null && (lone.isRemoved() || lone.hasEnd())) {
            LONE.remove(player.getUUID());
            return null;
        }
        return lone;
    }

    // --- the wire going off ---

    /** Something walked into a wire standing up. */
    public static void onTripped(ServerLevel level, TripwireEntity wire, ServerPlayer owner, LivingEntity victim) {
        PlayerPerkData data = PerkDataManager.get(owner);
        int tier = wire.tier();

        // What it carries already drags on: every harmful effect's time left is drawn out.
        double multiplier = ModPerks.TRIPWIRES.value(ModPerks.TRIPWIRE_EFFECT_MULTIPLIER, tier);
        for (MobEffectInstance effect : new ArrayList<>(victim.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !effect.isInfiniteDuration()) {
                EffectDurations.set(victim, effect, (int) Math.round(effect.getDuration() * multiplier));
            }
        }

        AuraRevealHandler.reveal(owner, victim, ModPerks.TRIPWIRES.ticks(ModPerks.TRIPWIRE_REVEAL, tier));

        if (data.isAddonActive(ModAddons.BOTTLE_OF_VENOM)) {
            ExhaustedHandler.applyCharges(victim, ModAddons.BOTTLE_OF_VENOM_CHARGES, ModAddons.BOTTLE_OF_VENOM_TICKS);
        }
        if (data.isAddonActive(ModAddons.PLASTIC_WIRE)) {
            victim.addEffect(new MobEffectInstance(ModEffects.BROKEN, ModAddons.PLASTIC_WIRE_BROKEN_TICKS, 0, false, true, true));
        }
        if (data.isAddonActive(ModAddons.SILVER_WIRE)) {
            BleedingHandler.apply(victim, wire.takeBleed(ModAddons.SILVER_WIRE_BLEED));
        }
        if (data.isAddonActive(ModAddons.DETONATOR)) {
            int blinded = Flashbang.blind(owner, victim, ModAddons.DETONATOR_FLASH_TICKS);
            victim.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, blinded,
                    ModAddons.DETONATOR_STRENGTH_AMPLIFIER, false, true, true));
            DETONATED.add(victim.getUUID());
            level.sendParticles(ParticleTypes.FLASH, victim.getX(), victim.getY() + 1.0D, victim.getZ(), 1, 0, 0, 0, 0);
        }

        level.playSound(null, victim.blockPosition(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1.0F, 1.0F);
        owner.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        owner.displayClientMessage(Component.translatable("bloodbound.message.tripwires_triggered",
                victim.getName()).withStyle(ChatFormatting.GOLD), false);
    }

    /** The wire has been crossed its last time. */
    public static void onBroken(TripwireEntity wire, ServerPlayer owner) {
        wire.level().playSound(null, wire.blockPosition(), SoundEvents.TRIPWIRE_DETACH, SoundSource.PLAYERS, 0.8F, 1.0F);
        owner.displayClientMessage(Component.translatable("bloodbound.message.tripwires_broken")
                .withStyle(ChatFormatting.DARK_GRAY), true);
    }

    /** Detonator: the sight coming back is what leaves them weak. */
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.FLASHED)) {
            weaken(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(ModEffects.FLASHED)) {
            weaken(event.getEntity());
        }
    }

    private static void weaken(LivingEntity victim) {
        if (victim.level().isClientSide || !DETONATED.remove(victim.getUUID())) {
            return;
        }
        victim.removeEffect(MobEffects.DAMAGE_BOOST);
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ModAddons.DETONATOR_WEAKNESS_TICKS,
                ModAddons.DETONATOR_WEAKNESS_AMPLIFIER, false, true, true));
    }

    // --- upkeep ---

    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        if (data.getActiveTier(ModPerks.TRIPWIRES) <= 0) {
            if (data.hasEquipped(ModPerks.TRIPWIRES)) {
                // Only disabled for a while: the wires stay up, the post being planted does not.
                SETTING.remove(player.getUUID());
                return;
            }
            if (TrapRoster.hasAny(TripwireEntity.KIND, player.getUUID()) || SETTING.containsKey(player.getUUID())) {
                clear(player.getUUID());
            }
            return;
        }
        tickSetup(player, gameTime);
        tickPreview(player, data);
    }

    private static void tickSetup(ServerPlayer player, long gameTime) {
        Setup setup = SETTING.get(player.getUUID());
        if (setup == null) {
            return;
        }
        if (!player.isAlive()) {
            SETTING.remove(player.getUUID());
            return;
        }
        // Sideways only: sinking into snow or bobbing on a block is not walking away.
        if (player.position().subtract(setup.origin).horizontalDistanceSqr()
                > ModPerks.BARBED_STILL_EPSILON * ModPerks.BARBED_STILL_EPSILON * 400.0D) {
            SETTING.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("bloodbound.message.tripwires_moved")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        setup.ticksHeld++;
        if (setup.ticksHeld % 4 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 0.1D, player.getZ(), 3, 0.3D, 0.02D, 0.3D, 0.0D);
        }
        if (setup.ticksHeld % 5 == 0) {
            player.displayClientMessage(Component.translatable("bloodbound.message.tripwires_setting",
                    Math.max(1, (ModPerks.TRIPWIRE_SETUP_TICKS - setup.ticksHeld + 19) / 20)), true);
        }
        if (setup.ticksHeld >= ModPerks.TRIPWIRE_SETUP_TICKS) {
            SETTING.remove(player.getUUID());
            plantFirst(player, setup.tier);
        }
    }

    /** Traces, for the owner's eyes only, where the wire would run if the second post went in now. */
    private static void tickPreview(ServerPlayer player, PlayerPerkData data) {
        if (player.tickCount % 3 != 0) {
            return;
        }
        TripwireEntity lone = lonePost(player);
        if (lone == null || lone.level() != player.level()) {
            return;
        }
        boolean valid = canPlant(player, lone, data.getActiveTier(ModPerks.TRIPWIRES));
        Vec3 from = lone.startTop();
        Vec3 to = secondTop(player);
        int steps = Math.max(2, (int) Math.ceil(from.distanceTo(to) * 3.0D));
        ServerLevel level = player.serverLevel();
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.lerp(to, i / (double) steps);
            level.sendParticles(player, valid ? VALID : INVALID, true, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    /** Forgets a player entirely, and takes their wires with them: the perk came off. */
    public static void clear(UUID playerId) {
        SETTING.remove(playerId);
        LONE.remove(playerId);
        TrapRoster.removeAll(TripwireEntity.KIND, playerId);
    }

    /** A logout: the post being planted and a lone first post go, the finished wires stay up. */
    public static void logout(UUID playerId) {
        SETTING.remove(playerId);
        TripwireEntity lone = LONE.remove(playerId);
        if (lone != null && !lone.hasEnd()) {
            lone.discard();
        }
    }

    /** A death: the same as a logout. */
    public static void onDeath(UUID playerId) {
        logout(playerId);
    }
}
