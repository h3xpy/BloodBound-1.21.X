package net.h3xpy.bloodbound.perk.impl;

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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Tinkerer: mend the tool in your hand over a run of skill checks, each tighter than the last.
 * <p>
 * The run ends when the item is whole, when a check is missed, when the item leaves your hand, or
 * when you press the key again. However it ends, the perk goes on cooldown — walking away early is
 * a choice, not a way out.
 */
public final class Tinkerer {

    /** One run in progress. The stack is held by identity: swapping items ends the run. */
    private static final class Session {
        private final ItemStack stack;
        private final int tier;
        private final float openingZone;
        private float zoneWidth;

        private Session(ItemStack stack, int tier, float openingZone) {
            this.stack = stack;
            this.tier = tier;
            this.openingZone = openingZone;
            this.zoneWidth = openingZone;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Tinkerer() {}

    public static boolean activate(ServerPlayer player, PlayerPerkData data, int tier, long gameTime) {
        if (SESSIONS.containsKey(player.getUUID())) {
            // A second press is the player calling it a day.
            stop(player, "bloodbound.message.tinkerer_stopped", gameTime);
            return true;
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.isDamageableItem()) {
            refuse(player, "bloodbound.message.tinkerer_no_item");
            return false;
        }

        // Duct Tape stretches the durability pool before any mending starts, so the run has
        // somewhere past full to put the extra work.
        if (data.isAddonActive(ModAddons.DUCT_TAPE)) {
            stretchDurability(stack);
        }
        if (!stack.isDamaged()) {
            refuse(player, "bloodbound.message.tinkerer_undamaged");
            return false;
        }

        float opening = SkillCheckDifficulty.EASY.zoneWidth()
                * ModPerks.TINKERER_ZONE_SCALE[Math.clamp(tier - 1, 0, ModPerks.TINKERER_ZONE_SCALE.length - 1)];
        if (data.isAddonActive(ModAddons.SPRING_CLAMP)) {
            opening *= ModAddons.SPRING_CLAMP_MULTIPLIER;
        }

        SESSIONS.put(player.getUUID(), new Session(stack, tier, opening));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS, 0.4F, 1.6F);
        SkillCheckManager.start(player, SkillCheckContext.TINKERER, SkillCheckDifficulty.EASY, opening);
        return true;
    }

    /** A Tinkerer skill check resolved. */
    public static void onSkillCheckResult(ServerPlayer player, boolean success) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        long gameTime = player.level().getGameTime();
        ItemStack stack = session.stack;
        int maxDamage = stack.getMaxDamage();

        if (!success) {
            // The Automatic Screwdriver catches the slip before it costs anything.
            if (!data.isAddonActive(ModAddons.AUTOMATIC_SCREWDRIVER)) {
                int penalty = Math.max(1, Math.round(maxDamage * ModPerks.TINKERER_MISS_PENALTY));
                // Never all the way: Tinkerer mends tools, it does not snap them.
                stack.setDamageValue(Math.min(maxDamage - 1, stack.getDamageValue() + penalty));
            }
            stop(player, "bloodbound.message.tinkerer_failed", gameTime);
            return;
        }

        float repair = (float) ModPerks.TINKERER.value(ModPerks.TINKERER_REPAIR, session.tier) / 100.0F;
        if (data.isAddonActive(ModAddons.GRIP_WRENCH)) {
            repair += ModAddons.GRIP_WRENCH_BONUS;
        }
        int restored = Math.max(1, Math.round(maxDamage * repair));
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - restored));

        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS, 0.35F, 1.9F);

        if (!stack.isDamaged()) {
            stop(player, "bloodbound.message.tinkerer_complete", gameTime);
            return;
        }

        // Each landed check makes the next one harder, down to a floor the Screwdriver raises.
        float shrink = (float) ModPerks.TINKERER.value(ModPerks.TINKERER_SHRINK, session.tier) / 100.0F;
        session.zoneWidth *= 1.0F - shrink;
        if (data.isAddonActive(ModAddons.AUTOMATIC_SCREWDRIVER)) {
            session.zoneWidth = Math.max(session.zoneWidth,
                    session.openingZone * ModAddons.SCREWDRIVER_MIN_ZONE_FRACTION);
        }

        SkillCheckManager.start(player, SkillCheckContext.TINKERER, SkillCheckDifficulty.EASY, session.zoneWidth);
    }

    /** Ends the run the moment the item stops being the one that was picked up. */
    public static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (player.getMainHandItem() != session.stack || !player.isAlive()) {
            SkillCheckManager.cancel(player);
            stop(player, "bloodbound.message.tinkerer_dropped", player.level().getGameTime());
        }
    }

    /** Forgets a player entirely, on logout or death. No cooldown: they have other problems. */
    public static void clear(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static boolean isRunning(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Closes the run and pays the cooldown, however it ended. */
    private static void stop(ServerPlayer player, String messageKey, long gameTime) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        PlayerPerkData data = PerkDataManager.get(player);
        data.setCooldown(ModPerks.TINKERER.id(), gameTime, ModPerks.TINKERER.cooldownTicks(session.tier));
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY), true);
        PerkDataManager.sync(player);
    }

    /**
     * Duct Tape: raises this stack's own maximum durability past what the item was built with. The
     * ceiling is worked out from the item's default, never from the stack's current maximum, so a
     * second run cannot stretch an already stretched tool any further.
     */
    private static void stretchDurability(ItemStack stack) {
        int built = new ItemStack(stack.getItem()).getMaxDamage();
        int stretched = Math.round(built * ModAddons.DUCT_TAPE_OVERSHOOT);
        int extra = stretched - stack.getMaxDamage();
        if (extra <= 0) {
            return;
        }
        // The damage value goes up by as much as the pool did, so the tool is no better off than it
        // was a moment ago — it simply now has more to be mended into.
        stack.set(DataComponents.MAX_DAMAGE, stretched);
        stack.setDamageValue(stack.getDamageValue() + extra);
    }

    private static void refuse(ServerPlayer player, String messageKey) {
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_GRAY), true);
    }
}
