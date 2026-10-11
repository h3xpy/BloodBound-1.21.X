package net.h3xpy.bloodbound.advancement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PerkDataManager;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.offering.Offerings;
import net.h3xpy.bloodbound.perk.AddonRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * What every BloodBound advancement is worth on the quest board, and which perk it belongs to.
 * <p>
 * The advancement itself is still earned as before; once earned, it can be claimed once, from the
 * Perk Table, for what its difficulty pays. Every difficulty, and which perk each advancement is
 * filed under, is in the one table below.
 */
public final class AchievementRewards {

    /** How hard an advancement is to earn, and what claiming it pays. */
    public enum Difficulty {
        /** Soul shards only. */
        EASY("easy", 15, null, ChatFormatting.GREEN),
        /** More soul shards. */
        MEDIUM("medium", 40, null, ChatFormatting.YELLOW),
        /** Soul shards and a common or uncommon offering. */
        HARD("hard", 60, new double[] { 60.0D, 40.0D, 0.0D, 0.0D, 0.0D }, ChatFormatting.GOLD),
        /** A heap of soul shards and a rare, epic or, rarely, unstable offering. */
        LEGENDARY("legendary", 120, new double[] { 0.0D, 0.0D, 70.0D, 25.0D, 5.0D }, ChatFormatting.LIGHT_PURPLE);

        private final String name;
        private final int shards;
        /** Odds of each offering rarity, common to unstable, in percent; null for no offering. */
        @Nullable
        private final double[] offeringWeights;
        private final ChatFormatting color;

        Difficulty(String name, int shards, @Nullable double[] offeringWeights, ChatFormatting color) {
            this.name = name;
            this.shards = shards;
            this.offeringWeights = offeringWeights;
            this.color = color;
        }

        public int shards() {
            return shards;
        }

        public boolean givesOffering() {
            return offeringWeights != null;
        }

        public ChatFormatting color() {
            return color;
        }

        public String translationKey() {
            return "bloodbound.wiki.difficulty." + name;
        }
    }

    /**
     * One advancement on the board.
     *
     * @param perkId the perk it is filed under, or null for the general ones
     */
    public record Entry(ResourceLocation id, @Nullable ResourceLocation perkId, Difficulty difficulty) {
        public String titleKey() {
            return "advancement." + id.getNamespace() + "." + id.getPath() + ".title";
        }

        public String descriptionKey() {
            return "advancement." + id.getNamespace() + "." + id.getPath() + ".description";
        }
    }

    private static final Map<ResourceLocation, Entry> ALL = new LinkedHashMap<>();

    static {
        // General.
        add("root", null, Difficulty.EASY);
        add("tier1_perk", null, Difficulty.EASY);
        add("tier2_perk", null, Difficulty.MEDIUM);
        add("tier3_perk", null, Difficulty.HARD);
        add("first_addon", null, Difficulty.EASY);
        add("unstable_addon", null, Difficulty.HARD);
        add("full_kit", null, Difficulty.EASY);
        add("collector", null, Difficulty.MEDIUM);
        add("completionist", null, Difficulty.HARD);
        add("unstable_genius", null, Difficulty.LEGENDARY);
        add("weaver", null, Difficulty.MEDIUM);
        add("p25", null, Difficulty.MEDIUM);
        add("p50", null, Difficulty.HARD);
        add("p100", null, Difficulty.LEGENDARY);
        add("green_thumb", null, Difficulty.MEDIUM);
        add("bad_luck", null, Difficulty.HARD);
        add("shard_hoarder", null, Difficulty.HARD);

        // Feats, by perk.
        add("hyperfocused", "tinkerer", Difficulty.HARD);
        add("crowd_control", "fragnade", Difficulty.MEDIUM);
        add("vertigo", "perfect_landing", Difficulty.HARD);
        add("not_that_low_cost", "low_cost_movement_device", Difficulty.HARD);
        add("cheater", "omniscience", Difficulty.MEDIUM);
        add("perfect_extraction", "full_extraction", Difficulty.MEDIUM);
        add("and_im_out", "broken_movement_device", Difficulty.HARD);
        add("save_your_tears", "surgical_suture", Difficulty.MEDIUM);
        add("should_have_died_here", "close_call", Difficulty.MEDIUM);
        add("w_support", "keep_fighting", Difficulty.MEDIUM);
        add("trick_shot", "bank_shot", Difficulty.MEDIUM);
        add("no_you_dont", "no_one_gets_away", Difficulty.HARD);
        add("instant_karma", "crime_and_punishment", Difficulty.MEDIUM);
        add("i_know_everything", "call_of_death", Difficulty.HARD);
        add("bloody_massacre", "echoing_wounds", Difficulty.LEGENDARY);
        add("the_sun", "flashbang", Difficulty.HARD);
        add("heroes_never_die", "guardian_angel", Difficulty.LEGENDARY);
        add("background_player", "healing_runes", Difficulty.MEDIUM);
        add("fifty_cal", "longshot", Difficulty.HARD);
        add("feral_frenzy", "nasty_blade", Difficulty.HARD);
        add("thats_sick", "out_of_breath", Difficulty.MEDIUM);
        add("thats_embarrassing", "under_the_radar", Difficulty.EASY);
        add("i_know_this_guy", "hunters_instinct", Difficulty.EASY);
        add("come_on", "adrenaline", Difficulty.MEDIUM);
        add("got_no_time", "anti_exhaustion_syringe", Difficulty.HARD);
        add("jackpot", "raise_the_stakes", Difficulty.HARD);
        add("my_eyes", "from_the_dark", Difficulty.EASY);
        add("the_angel_has_fallen", "beware_the_power_of_an_angel", Difficulty.EASY);
        add("get_back_here", "catching_up", Difficulty.MEDIUM);
        add("aww_too_bad", "panic_attack", Difficulty.EASY);
        add("get_in_there", "team_spirit", Difficulty.HARD);
        add("i_dont_think_so", "beyond_vision", Difficulty.EASY);
        add("go_home", "spring_pad", Difficulty.HARD);
        add("dont_worry_im_here", "holy_sanctum", Difficulty.MEDIUM);
        add("upside_down", "the_hanged_man", Difficulty.EASY);
        add("i_am_a_surgeon", "steady_hands", Difficulty.HARD);
        add("bip", "short_circuit", Difficulty.HARD);
        add("watch_your_step", "barbed_wire", Difficulty.LEGENDARY);
        add("ya_aint_going_nowhere", "chained_up", Difficulty.EASY);
        add("not_on_my_watch", "nullification", Difficulty.HARD);
        add("chilly", "ice_block", Difficulty.HARD);
        add("execution", "final_blow", Difficulty.EASY);
        add("gotcha", "target_found", Difficulty.MEDIUM);
        add("herbalist", "green_herbs", Difficulty.MEDIUM);
        add("still_standing", "relentless", Difficulty.MEDIUM);
    }

    private AchievementRewards() {}

    private static void add(String path, @Nullable String perk, Difficulty difficulty) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, path);
        ResourceLocation perkId = perk == null ? null : ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, perk);
        ALL.put(id, new Entry(id, perkId, difficulty));
    }

    @Nullable
    public static Entry get(ResourceLocation id) {
        return ALL.get(id);
    }

    public static List<Entry> all() {
        return List.copyOf(ALL.values());
    }

    /** The advancements filed under a perk, or the general ones for null. */
    public static List<Entry> forPerk(@Nullable ResourceLocation perkId) {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : ALL.values()) {
            if (perkId == null ? entry.perkId() == null : perkId.equals(entry.perkId())) {
                found.add(entry);
            }
        }
        return found;
    }

    /** Whether the player has earned this advancement. */
    public static boolean isDone(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /**
     * Pays out an earned advancement, once. Anything that is not earned, already claimed, or not on
     * the board is turned away without a word: the screen only offers what can be claimed.
     */
    public static void claim(ServerPlayer player, ResourceLocation id) {
        Entry entry = get(id);
        PlayerPerkData data = PerkDataManager.get(player);
        if (entry == null || data.isAchievementClaimed(id) || !isDone(player, id)) {
            return;
        }
        data.markAchievementClaimed(id);

        Difficulty difficulty = entry.difficulty();
        PerkDataManager.giveShards(player, difficulty.shards());
        Component reward = Component.translatable("bloodbound.wiki.claimed_shards", difficulty.shards());
        if (difficulty.offeringWeights != null) {
            ItemStack offering = rollOffering(player.getRandom(), difficulty.offeringWeights);
            if (offering != null) {
                Component name = offering.getHoverName();
                if (!player.getInventory().add(offering)) {
                    player.drop(offering, false);
                }
                reward = Component.translatable("bloodbound.wiki.claimed_both", difficulty.shards(), name);
            }
        }
        PerkDataManager.syncInventory(player);
        PerkDataManager.sync(player);
        PerkDataManager.sendWiki(player);

        player.sendSystemMessage(Component.translatable("bloodbound.wiki.claimed",
                Component.translatable(entry.titleKey()), reward).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.8F, 1.2F);
    }

    @Nullable
    private static ItemStack rollOffering(RandomSource random, double[] weights) {
        double roll = random.nextDouble() * 100.0D;
        AddonRarity[] rarities = AddonRarity.values();
        for (int i = 0; i < weights.length && i < rarities.length; i++) {
            roll -= weights[i];
            if (roll < 0.0D) {
                return Offerings.pick(random, rarities[i]);
            }
        }
        return null;
    }
}
