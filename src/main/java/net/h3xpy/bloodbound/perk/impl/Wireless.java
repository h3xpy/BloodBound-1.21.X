package net.h3xpy.bloodbound.perk.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.PerkEventHandler;
import net.h3xpy.bloodbound.network.PerkChargesPayload;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Wireless: a reserve of charges that each stretch the player's reach a little, spent by the block
 * on everything the player touches.
 * <p>
 * The reach is an attribute modifier on both interaction ranges, so the client aims with it and the
 * server accepts what it aims at. It is only ever rewritten when a whole charge comes or goes: the
 * attribute travels to the client every time it changes, and a modifier that crept up by a fraction
 * every tick would be a packet every tick.
 * <p>
 * Only the stretch is paid for: an interaction costs by the block it reached past the player's own
 * reach, and one within it costs nothing.
 */
public final class Wireless {

    private static final ResourceLocation REACH_ID =
            ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, "wireless_reach");

    /** The tick each player last paid for an interaction, so one click is only charged once. */
    private static final Map<UUID, Long> LAST_PAID = new HashMap<>();

    private Wireless() {}

    /** Fills the reserve and keeps the reach in step with it. Called once a tick per player. */
    public static void tick(ServerPlayer player, PlayerPerkData data, long gameTime) {
        int tier = data.getActiveTier(ModPerks.WIRELESS);
        if (tier <= 0) {
            setReach(player, 0.0D);
            return;
        }

        float max = maxCharges(tier);
        float charges = data.perkCharges(ModPerks.WIRELESS.id(), max);
        if (charges < max) {
            double rate = ModPerks.WIRELESS_REGEN_PER_SECOND * (1.0D + PerkEventHandler.lightbringerRate(player));
            charges = Math.min(max, charges + (float) (rate / 20.0D));
            data.setPerkCharges(ModPerks.WIRELESS.id(), charges);
        }

        setReach(player, Math.floor(charges) * ModPerks.WIRELESS_REACH_PER_CHARGE);
        if (charges < max || gameTime % 20L == 0L) {
            send(player, charges, max);
        }
    }

    private static float maxCharges(int tier) {
        return (float) ModPerks.WIRELESS.value(ModPerks.WIRELESS_CHARGES, tier);
    }

    /** Writes the bonus onto both reaches, only when it actually differs from what is there. */
    private static void setReach(ServerPlayer player, double amount) {
        apply(player, Attributes.BLOCK_INTERACTION_RANGE, amount);
        apply(player, Attributes.ENTITY_INTERACTION_RANGE, amount);
    }

    private static void apply(ServerPlayer player, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(REACH_ID);
        if (amount <= 0.0D) {
            if (existing != null) {
                instance.removeModifier(REACH_ID);
            }
            return;
        }
        if (existing == null || existing.amount() != amount) {
            instance.removeModifier(REACH_ID);
            instance.addTransientModifier(new AttributeModifier(REACH_ID, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // --- paying for a reach ---

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            pay(player, new AABB(event.getPos()), Attributes.BLOCK_INTERACTION_RANGE);
        }
    }

    /**
     * A block is paid for once it is broken, not when the dig starts. The server checks the reach
     * again as the block comes out, so charges spent at the first swing took the reach the dig
     * needed to finish away from under it.
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            pay(player, new AABB(event.getPos()), Attributes.BLOCK_INTERACTION_RANGE);
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            pay(player, event.getTarget().getBoundingBox(), Attributes.ENTITY_INTERACTION_RANGE);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            pay(player, event.getTarget().getBoundingBox(), Attributes.ENTITY_INTERACTION_RANGE);
        }
    }

    /**
     * Takes four charges a block for however far past the player's own reach they had to go. The
     * distance is measured the way vanilla measures reach: from the eyes to the nearest point of
     * the block or the creature. Anything within the ordinary reach is free.
     */
    private static void pay(ServerPlayer player, AABB target, Holder<Attribute> range) {
        PlayerPerkData data = PerkDataManager.get(player);
        int tier = data.getActiveTier(ModPerks.WIRELESS);
        AttributeInstance reach = player.getAttribute(range);
        if (tier <= 0 || reach == null) {
            return;
        }
        // Both hands get their own right-click event; one click is paid for once.
        long now = player.level().getGameTime();
        Long last = LAST_PAID.put(player.getUUID(), now);
        if (last != null && last == now) {
            return;
        }

        AttributeModifier bonus = reach.getModifier(REACH_ID);
        double ownReach = reach.getValue() - (bonus == null ? 0.0D : bonus.amount());
        double distance = Math.sqrt(target.distanceToSqr(player.getEyePosition()));
        double beyond = distance - ownReach;
        if (beyond <= 0.0D) {
            return;
        }

        float max = maxCharges(tier);
        double cost = beyond * ModPerks.WIRELESS_COST_PER_BLOCK;
        float charges = Math.max(0.0F, data.perkCharges(ModPerks.WIRELESS.id(), max) - (float) cost);
        data.setPerkCharges(ModPerks.WIRELESS.id(), charges);
        send(player, charges, max);
    }

    private static void send(ServerPlayer player, float charges, float max) {
        PacketDistributor.sendToPlayer(player, new PerkChargesPayload(ModPerks.WIRELESS.id(),
                (int) charges, Math.round(max), 0L));
    }

    public static void clear(UUID playerId) {
        LAST_PAID.remove(playerId);
    }
}
