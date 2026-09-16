package net.h3xpy.bloodbound.skillcheck;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.GuardianAngelHandler;
import net.h3xpy.bloodbound.event.PanicAttackHandler;
import net.h3xpy.bloodbound.heal.HealManager;
import net.h3xpy.bloodbound.network.SkillCheckResultPayload;
import net.h3xpy.bloodbound.network.StartSkillCheckPayload;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.SurgicalSuture;
import net.h3xpy.bloodbound.perk.impl.Tinkerer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Owns every running skill check. The server drives the timing and decides hit or miss; the client
 * only draws the dial and reports the moment the key was pressed.
 */
public final class SkillCheckManager {

    /** Earliest point in the sweep a success zone may start, leaving room to react. */
    private static final float MIN_ZONE_START = 0.45F;
    /** Latest point the zone may end. */
    private static final float MAX_ZONE_END = 0.94F;

    /**
     * How far the client's reported needle position may differ from the server's before the server
     * distrusts it. Wide enough to absorb ordinary latency, tight enough that it cannot be abused.
     */
    private static final float PROGRESS_TOLERANCE = 0.12F;

    private static final Map<UUID, ActiveSkillCheck> ACTIVE = new HashMap<>();
    private static int nextId = 1;

    private SkillCheckManager() {}

    public static boolean hasActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    @Nullable
    public static ActiveSkillCheck active(ServerPlayer player) {
        return ACTIVE.get(player.getUUID());
    }

    /** Raises a skill check and tells the client to draw it. Does nothing if one is already running. */
    public static void start(ServerPlayer player, SkillCheckContext context, SkillCheckDifficulty difficulty) {
        start(player, context, difficulty, difficulty.zoneWidth());
    }

    /**
     * As above, with the success zone named outright rather than taken from the difficulty. Tinkerer
     * tightens its own window as a run goes on, so it works out the width itself.
     */
    public static void start(ServerPlayer player, SkillCheckContext context, SkillCheckDifficulty difficulty,
            float requestedZoneWidth) {
        if (hasActive(player)) {
            return;
        }

        PlayerPerkData data = PerkDataManager.get(player);
        float zoneWidth = requestedZoneWidth;
        if (data.isAddonActive(ModAddons.NEEDLE_AND_THREAD)) {
            zoneWidth *= ModAddons.NEEDLE_ZONE_MULTIPLIER;
        }

        int duration = difficulty.durationTicks();

        // Panic Attack: somebody who hit you recently and has not left is still in your head. The
        // window narrows and the needle hurries, which compounds — the same way Tinkerer's run does.
        int panic = PanicAttackHandler.tierAffecting(player);
        if (panic > 0) {
            zoneWidth *= 1.0F
                    - (float) ModPerks.PANIC_ATTACK.value(ModPerks.PANIC_SHRINK, panic) / 100.0F;
            duration = Math.max(6, (int) Math.round(duration
                    / (1.0D + ModPerks.PANIC_ATTACK.value(ModPerks.PANIC_SPEED, panic) / 100.0D)));
        }

        float span = MAX_ZONE_END - MIN_ZONE_START - zoneWidth;
        float zoneStart = MIN_ZONE_START + (span <= 0 ? 0 : player.getRandom().nextFloat() * span);

        ActiveSkillCheck check = new ActiveSkillCheck(nextId++, context, zoneStart, zoneWidth, duration);
        ACTIVE.put(player.getUUID(), check);

        PacketDistributor.sendToPlayer(player, new StartSkillCheckPayload(
                check.id(), check.zoneStart(), check.zoneWidth(), check.durationTicks()));
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    /** Advances the running check; letting it run out counts as a miss. */
    public static void tick(ServerPlayer player) {
        ActiveSkillCheck check = ACTIVE.get(player.getUUID());
        if (check == null) {
            return;
        }
        check.tick();
        if (check.isExpired()) {
            resolve(player, check, false);
        }
    }

    /** The player pressed the skill check key. */
    public static void handleInput(ServerPlayer player, int id, float clientProgress) {
        ActiveSkillCheck check = ACTIVE.get(player.getUUID());
        if (check == null || check.id() != id) {
            return;
        }

        // Trust the client's reading only when it broadly agrees with ours, so a laggy but honest
        // player is not punished and a lying one gains nothing.
        float serverProgress = check.progress();
        float progress = Math.abs(clientProgress - serverProgress) <= PROGRESS_TOLERANCE
                ? clientProgress
                : serverProgress;

        resolve(player, check, check.isInZone(progress));
    }

    /** Drops any running check without resolving it, e.g. when a heal is interrupted. */
    public static void cancel(ServerPlayer player) {
        ActiveSkillCheck check = ACTIVE.remove(player.getUUID());
        if (check != null) {
            PacketDistributor.sendToPlayer(player, new SkillCheckResultPayload(check.id(), false));
        }
    }

    /** Forgets a player entirely, on logout or dimension change. */
    public static void clear(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    private static void resolve(ServerPlayer player, ActiveSkillCheck check, boolean success) {
        ACTIVE.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SkillCheckResultPayload(check.id(), success));

        player.playNotifySound(
                success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.ITEM_BREAK,
                SoundSource.PLAYERS, 0.7F, success ? 1.4F : 0.8F);

        switch (check.context()) {
            case SELF_HEAL, SELF_HEAL_RETRY ->
                    SurgicalSuture.onSelfHealResult(player, check.context(), success);
            case HEAL -> HealManager.onSkillCheckResult(player, success);
            case TINKERER -> Tinkerer.onSkillCheckResult(player, success);
            case GUARDIAN -> GuardianAngelHandler.onSkillCheckResult(player, success);
        }
    }
}
