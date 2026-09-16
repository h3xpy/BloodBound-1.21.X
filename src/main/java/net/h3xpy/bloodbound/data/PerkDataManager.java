package net.h3xpy.bloodbound.data;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.soulweb.Soulweb;
import net.h3xpy.bloodbound.soulweb.SoulwebNode;
import net.h3xpy.bloodbound.soulweb.NodeReward;
import net.h3xpy.bloodbound.menu.PerkTableMenu;
import net.h3xpy.bloodbound.network.SyncPerkDataPayload;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRegistry;
import net.h3xpy.bloodbound.perk.Perk;
import net.h3xpy.bloodbound.perk.PerkRegistry;
import net.h3xpy.bloodbound.registry.ModAttachments;
import net.h3xpy.bloodbound.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side operations on a player's BloodBound state. Everything the client can ask for goes
 * through here so it can be validated in one place.
 */
public final class PerkDataManager {

    private PerkDataManager() {}

    public static PlayerPerkData get(Player player) {
        return player.getData(ModAttachments.PERK_DATA);
    }

    /** Pushes the player's full state to their client. */
    public static void sync(ServerPlayer player) {
        PlayerPerkData data = get(player);
        PacketDistributor.sendToPlayer(player, new SyncPerkDataPayload(data.serializeNBT(player.registryAccess())));
    }

    // --- soul shards ---

    public static int countShards(Player player) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.SOUL_SHARD.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Removes {@code amount} shards, or nothing at all if the player cannot afford it. */
    public static boolean consumeShards(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (countShards(player) < amount) {
            return false;
        }
        int remaining = amount;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.SOUL_SHARD.get())) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
                if (stack.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        inventory.setChanged();

        // The perk table menu has no slots, so nothing would otherwise tell the client its
        // inventory changed and the shard counter on screen would sit at a stale number.
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
        return true;
    }

    // --- loadout ---

    /** @param perkId the perk to equip, or null to clear the slot */
    public static void setLoadoutSlot(ServerPlayer player, int slot, @Nullable ResourceLocation perkId) {
        if (!isAtPerkTable(player) || slot < 0 || slot >= PlayerPerkData.LOADOUT_SIZE) {
            return;
        }
        PlayerPerkData data = get(player);
        if (perkId != null && !data.isUnlocked(perkId)) {
            return;
        }
        data.setLoadoutSlot(slot, perkId);
        sync(player);
    }

    /**
     * @param perkId  the perk whose addon slot is being changed
     * @param addonId the addon to fit, or null to empty the slot
     */
    public static void setEquippedAddon(ServerPlayer player, ResourceLocation perkId,
            @Nullable ResourceLocation addonId) {
        if (!isAtPerkTable(player)) {
            return;
        }
        PlayerPerkData data = get(player);

        if (addonId != null) {
            Addon addon = AddonRegistry.get(addonId);
            // An addon can only ever go in its own perk's slot, and only if it has been bought.
            if (addon == null || !addon.perkId().equals(perkId) || !data.isAddonUnlocked(addonId)) {
                return;
            }
            // And only if that perk is actually in the loadout. Without this the addon would sit in
            // the slot of a perk that is not there, reading as fitted everywhere while doing nothing.
            if (data.getActiveTier(perkId) <= 0) {
                return;
            }
        }
        data.setEquippedAddon(perkId, addonId);
        sync(player);
    }

    // --- soulweb ---

    public static void purchaseNode(ServerPlayer player, int index) {
        if (!isAtPerkTable(player)) {
            return;
        }
        PlayerPerkData data = get(player);
        Soulweb web = data.getOrCreateSoulweb(player.getRandom(), player.registryAccess());

        if (!web.isUnlockable(index)) {
            return;
        }
        SoulwebNode node = web.node(index);
        if (node == null) {
            return;
        }
        if (!consumeShards(player, node.cost())) {
            player.displayClientMessage(
                    Component.translatable("bloodbound.message.not_enough_shards", node.cost())
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        web.markPurchased(index);
        grantReward(player, data, node.reward());

        // The perk table menu has no slots of its own, so the server never broadcasts the player's
        // inventory while it is open. The shards came off inside consumeShards; this covers an item
        // reward going in, which would otherwise stay invisible until the screen was closed.
        player.inventoryMenu.broadcastChanges();

        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.7F, 1.2F);

        // Nothing left to buy on the committed branch, so the entity rebuilds the web.
        if (web.isExhausted()) {
            data.rerollSoulweb(player.getRandom(), player.registryAccess());
            player.displayClientMessage(
                    Component.translatable("bloodbound.message.web_reset").withStyle(ChatFormatting.DARK_RED), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN,
                    SoundSource.PLAYERS, 0.25F, 1.6F);
        }
        sync(player);
    }

    private static void grantReward(ServerPlayer player, PlayerPerkData data, NodeReward reward) {
        if (reward instanceof NodeReward.PerkReward perkReward) {
            if (perkReward.perkId() == null || PerkRegistry.get(perkReward.perkId()) == null) {
                // The perk was removed or renamed since the web was rolled; skip it rather than
                // writing a dangling id into the player's unlocked map.
                return;
            }
            boolean wasUnknown = !data.isUnlocked(perkReward.perkId());
            data.unlock(perkReward.perkId(), perkReward.tier());

            // Equipping a brand new perk straight away saves a trip through the loadout tab.
            if (wasUnknown) {
                int free = data.firstFreeSlot();
                if (free >= 0) {
                    data.setLoadoutSlot(free, perkReward.perkId());
                }
            }

            Perk perk = PerkRegistry.get(perkReward.perkId());
            if (perk != null) {
                player.displayClientMessage(
                        Component.translatable("bloodbound.message.perk_unlocked",
                                perk.displayName(), perkReward.tier()).withStyle(ChatFormatting.GOLD),
                        false);
            }
        } else if (reward instanceof NodeReward.AddonReward addonReward) {
            Addon addon = AddonRegistry.get(addonReward.addonId());
            if (addon == null) {
                return;
            }
            boolean wasUnknown = !data.isAddonUnlocked(addon.id());
            data.unlockAddon(addon.id());

            // Fit it straight away if this perk's addon slot is still empty.
            if (wasUnknown && data.equippedAddon(addon.perkId()) == null) {
                data.setEquippedAddon(addon.perkId(), addon.id());
            }
            player.displayClientMessage(
                    Component.translatable("bloodbound.message.addon_unlocked", addon.displayName())
                            .withStyle(ChatFormatting.AQUA),
                    false);
        } else if (reward instanceof NodeReward.XpReward xpReward) {
            player.giveExperiencePoints(xpReward.amount());
        } else if (reward instanceof NodeReward.ItemReward itemReward) {
            ItemStack stack = itemReward.stack().copy();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** Guards every action that spends shards or changes the loadout. */
    private static boolean isAtPerkTable(ServerPlayer player) {
        return player.containerMenu instanceof PerkTableMenu menu && menu.stillValid(player);
    }
}
