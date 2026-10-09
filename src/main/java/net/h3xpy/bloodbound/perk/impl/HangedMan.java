package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.mixin.ServerGamePacketListenerImplAccessor;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * The Hanged Man: the slot key turns the bearer's gravity upside down, and a second press sets it
 * right. The cooldown only starts once they are the right way up again.
 * <p>
 * The whole state is one modifier on the gravity attribute. That attribute is synced to the player
 * and to everyone watching them, so every side can tell who is upside down without a payload of its
 * own, and a transient modifier is never saved: a relog or a restart always lands the right way up.
 * What the modifier cannot do alone is done around it — the ceiling counting as ground and the jump
 * pushing down ({@code EntityMixin}, {@code LivingEntityMixin}), the eyes moving to where the head now
 * is ({@link #onEntitySize}), and on the client the view, the controls and the model
 * ({@code ClientHangedMan}).
 */
public final class HangedMan {

    private static final ResourceLocation GRAVITY_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "hanged_man");
    /** Multiplies the total gravity by -1, so it inverts whatever else is acting on it. */
    private static final AttributeModifier INVERTED = new AttributeModifier(GRAVITY_ID, -2.0D,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** Tarot Card XII: the same turn, put on whatever was near when its owner flipped, for a while. */
    private static final ResourceLocation TAROT_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "hanged_man_tarot");
    private static final AttributeModifier TAROT = new AttributeModifier(TAROT_ID, -2.0D,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** What Tarot Card XII turned over, and the tick each one comes back the right way up. */
    private static final Map<LivingEntity, Long> TURNED = new HashMap<>();
    /** Upside Down Card: players whose next landing after a flip hurts twice as much, and until when. */
    private static final Map<UUID, Long> HARD_LANDING = new HashMap<>();
    /** How long after a flip the landing still counts as the flip's, should it never come. */
    private static final int HARD_LANDING_TICKS = 200;

    private HangedMan() {}

    /**
     * Whether this entity hangs upside down: a player under the perk, or anything a Tarot Card turned
     * over. Safe on either side.
     */
    public static boolean isInverted(Entity entity) {
        // getAttributes() is still null while the entity is being built, which is when the first
        // size event fires.
        if (!(entity instanceof LivingEntity living) || living.getAttributes() == null) {
            return false;
        }
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        return gravity != null && (gravity.hasModifier(GRAVITY_ID) || gravity.hasModifier(TAROT_ID));
    }

    /** Whether this player turned themselves over with the perk, rather than being turned by a card. */
    private static boolean isSelfInverted(LivingEntity living) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(GRAVITY_ID);
    }

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (isSelfInverted(player)) {
            setRight(player);
            data.setCooldown(ModPerks.HANGED_MAN.id(), gameTime, cooldownTicks(data, tier));
        } else {
            invert(player);
            if (data.isAddonActive(ModAddons.TAROT_CARD_XII)) {
                turnNearby(player, gameTime);
            }
        }
        if (data.isAddonActive(ModAddons.UPSIDE_DOWN_CARD)) {
            HARD_LANDING.put(player.getUUID(), gameTime + HARD_LANDING_TICKS);
        }
        return true;
    }

    /** The cooldown once set right: shorter with Frayed Rope, none at all with the Upside Down Card. */
    private static int cooldownTicks(PlayerPerkData data, int tier) {
        if (data.isAddonActive(ModAddons.UPSIDE_DOWN_CARD)) {
            return 0;
        }
        int ticks = ModPerks.HANGED_MAN.cooldownTicks(tier);
        if (data.isAddonActive(ModAddons.FRAYED_ROPE)) {
            ticks = Math.round(ticks * ModAddons.FRAYED_ROPE_COOLDOWN);
        }
        return ticks;
    }

    // --- Tarot Card XII ---

    /** Turns over everything around the player for a few seconds; whatever is already over is left be. */
    private static void turnNearby(ServerPlayer player, long gameTime) {
        double radius = ModAddons.TAROT_CARD_RADIUS;
        AABB area = player.getBoundingBox().inflate(radius);
        for (LivingEntity living : player.serverLevel().getEntitiesOfClass(LivingEntity.class, area)) {
            if (living == player || !living.isAlive() || living.isSpectator() || isInverted(living)
                    || living.distanceToSqr(player) > radius * radius) {
                continue;
            }
            AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
            if (gravity == null) {
                continue;
            }
            gravity.addOrUpdateTransientModifier(TAROT);
            living.resetFallDistance();
            living.refreshDimensions();
            TURNED.put(living, gameTime + ModAddons.TAROT_CARD_TICKS);
            player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL,
                    living.getX(), living.getY() + living.getBbHeight() / 2.0D, living.getZ(),
                    10, 0.3D, 0.5D, 0.3D, 0.05D);
        }
    }

    /** Sets right whatever a Tarot Card turned, once its time is up. Called once a tick for the whole server. */
    public static void tickTurned(MinecraftServer server) {
        if (TURNED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Long>> iterator = TURNED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = iterator.next();
            LivingEntity living = entry.getKey();
            if (living.isRemoved() || !living.isAlive()) {
                iterator.remove();
                continue;
            }
            if (living.level().getGameTime() >= entry.getValue()) {
                iterator.remove();
                AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
                if (gravity != null) {
                    gravity.removeModifier(TAROT_ID);
                }
                living.resetFallDistance();
                living.refreshDimensions();
                continue;
            }
            // A turned player falling up looks to the server like one hovering; see tick().
            if (living instanceof ServerPlayer player) {
                ((ServerGamePacketListenerImplAccessor) player.connection).bloodbound$setAboveGroundTickCount(0);
            }
        }
    }

    /** Forgets what the cards turned, on server shutdown; the turn itself is never saved. */
    public static void clearTurned() {
        TURNED.clear();
        HARD_LANDING.clear();
    }

    // --- landings ---

    /**
     * Upturned Hourglass: no fall hurts while the player hangs from the ceiling by their own perk.
     * Upside Down Card: the first landing after a flip hurts twice as much.
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        if (data.getActiveTier(ModPerks.HANGED_MAN) <= 0) {
            HARD_LANDING.remove(player.getUUID());
            return;
        }
        if (isSelfInverted(player) && data.isAddonActive(ModAddons.UPTURNED_HOURGLASS)) {
            event.setCanceled(true);
            return;
        }
        Long until = HARD_LANDING.remove(player.getUUID());
        if (until != null && player.level().getGameTime() <= until && data.isAddonActive(ModAddons.UPSIDE_DOWN_CARD)) {
            event.setDamageMultiplier(event.getDamageMultiplier() * ModAddons.UPSIDE_DOWN_CARD_FALL);
        }
    }

    /** Sets the player right if the perk left the loadout while they hung, and keeps them from being kicked. */
    public static void tick(ServerPlayer player, PlayerPerkData data) {
        if (!isSelfInverted(player)) {
            return;
        }
        if (data.getActiveTier(ModPerks.HANGED_MAN) <= 0) {
            setRight(player);
            return;
        }
        // Falling upwards looks, to the server, like hovering in the air: without this, a long fall
        // into the sky gets the player kicked for flying.
        ((ServerGamePacketListenerImplAccessor) player.connection).bloodbound$setAboveGroundTickCount(0);
    }

    /** Sets the player right without any cooldown, on death or logout. */
    public static void clear(ServerPlayer player) {
        if (isSelfInverted(player)) {
            setRight(player);
        }
    }

    /**
     * A bed is lain in the right way up: sleeping on the ceiling would leave the player floating off
     * the mattress all night. Lying down sets them right, and the perk's cooldown starts, as if they
     * had pressed the key themselves.
     */
    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getProblem() != null || !isSelfInverted(player)) {
            return;
        }
        setRight(player);
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.HANGED_MAN);
        if (tier > 0) {
            data.setCooldown(ModPerks.HANGED_MAN.id(), player.level().getGameTime(),
                    cooldownTicks(data, tier));
            PerkDataManager.sync(player);
        }
    }

    /** The eyes go where the head now is: as far from the top of the hitbox as they were from its foot. */
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (isInverted(event.getEntity())) {
            EntityDimensions size = event.getNewSize();
            event.setNewSize(size.withEyeHeight(size.height() - size.eyeHeight()));
        }
    }

    private static void invert(ServerPlayer player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        gravity.addOrUpdateTransientModifier(INVERTED);
        // A fall already under way is cancelled by the flip, not carried into the ceiling.
        player.resetFallDistance();
        player.refreshDimensions();
        effects(player, 0.7F);
    }

    private static void setRight(ServerPlayer player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        gravity.removeModifier(GRAVITY_ID);
        player.resetFallDistance();
        player.refreshDimensions();
        effects(player, 1.3F);
    }

    private static void effects(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 0.8F, pitch);
        player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + player.getBbHeight() / 2.0D, player.getZ(),
                20, 0.3D, 0.5D, 0.3D, 0.05D);
    }
}
