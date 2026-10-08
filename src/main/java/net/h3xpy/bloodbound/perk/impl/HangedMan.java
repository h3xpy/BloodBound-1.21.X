package net.h3xpy.bloodbound.perk.impl;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.mixin.ServerGamePacketListenerImplAccessor;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

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

    private HangedMan() {}

    /** Whether this entity is a player hanging upside down. Safe on either side. */
    public static boolean isInverted(Entity entity) {
        // getAttributes() is still null while the entity is being built, which is when the first
        // size event fires.
        if (!(entity instanceof Player player) || player.getAttributes() == null) {
            return false;
        }
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(GRAVITY_ID);
    }

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (isInverted(player)) {
            setRight(player);
            data.setCooldown(ModPerks.HANGED_MAN.id(), gameTime, ModPerks.HANGED_MAN.cooldownTicks(tier));
        } else {
            invert(player);
        }
        return true;
    }

    /** Sets the player right if the perk left the loadout while they hung, and keeps them from being kicked. */
    public static void tick(ServerPlayer player, PlayerPerkData data) {
        if (!isInverted(player)) {
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
        if (isInverted(player)) {
            setRight(player);
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
