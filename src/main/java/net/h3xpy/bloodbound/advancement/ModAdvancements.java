package net.h3xpy.bloodbound.advancement;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.perk.Addon;
import net.h3xpy.bloodbound.perk.AddonRarity;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's advancements, and the moments that earn them.
 * <p>
 * Each one is a datapack advancement whose only criterion is {@code impossible}, so nothing grants
 * them by accident — they are handed out from here, when the soulweb pays out, and from the perks
 * themselves for the feats. What a feat asks for is checked where it happens; the ones that take
 * more than one moment to add up are tracked in {@link AchievementTracker}.
 */
public final class ModAdvancements {

    /** The criterion every one of them is built on. */
    private static final String CRITERION = "granted";

    private static final ResourceLocation ROOT = id("root");
    private static final ResourceLocation TIER1_PERK = id("tier1_perk");
    private static final ResourceLocation TIER2_PERK = id("tier2_perk");
    private static final ResourceLocation TIER3_PERK = id("tier3_perk");
    private static final ResourceLocation FIRST_ADDON = id("first_addon");
    private static final ResourceLocation UNSTABLE_ADDON = id("unstable_addon");

    // --- feats ---

    public static final ResourceLocation HYPERFOCUSED = id("hyperfocused");
    public static final ResourceLocation CROWD_CONTROL = id("crowd_control");
    public static final ResourceLocation VERTIGO = id("vertigo");
    public static final ResourceLocation NOT_THAT_LOW_COST = id("not_that_low_cost");
    public static final ResourceLocation CHEATER = id("cheater");
    public static final ResourceLocation PERFECT_EXTRACTION = id("perfect_extraction");
    public static final ResourceLocation AND_IM_OUT = id("and_im_out");
    public static final ResourceLocation SAVE_YOUR_TEARS = id("save_your_tears");
    public static final ResourceLocation SHOULD_HAVE_DIED_HERE = id("should_have_died_here");
    public static final ResourceLocation W_SUPPORT = id("w_support");
    public static final ResourceLocation TRICK_SHOT = id("trick_shot");
    public static final ResourceLocation NO_YOU_DONT = id("no_you_dont");
    public static final ResourceLocation INSTANT_KARMA = id("instant_karma");
    public static final ResourceLocation I_KNOW_EVERYTHING = id("i_know_everything");
    public static final ResourceLocation BLOODY_MASSACRE = id("bloody_massacre");
    public static final ResourceLocation THE_SUN = id("the_sun");
    public static final ResourceLocation HEROES_NEVER_DIE = id("heroes_never_die");
    public static final ResourceLocation BACKGROUND_PLAYER = id("background_player");
    public static final ResourceLocation FIFTY_CAL = id("fifty_cal");
    public static final ResourceLocation FERAL_FRENZY = id("feral_frenzy");
    public static final ResourceLocation THATS_SICK = id("thats_sick");
    public static final ResourceLocation THATS_EMBARRASSING = id("thats_embarrassing");
    public static final ResourceLocation I_KNOW_THIS_GUY = id("i_know_this_guy");
    public static final ResourceLocation COME_ON = id("come_on");
    public static final ResourceLocation GOT_NO_TIME = id("got_no_time");
    public static final ResourceLocation JACKPOT = id("jackpot");
    public static final ResourceLocation MY_EYES = id("my_eyes");
    public static final ResourceLocation THE_ANGEL_HAS_FALLEN = id("the_angel_has_fallen");
    public static final ResourceLocation GET_BACK_HERE = id("get_back_here");
    public static final ResourceLocation AWW_TOO_BAD = id("aww_too_bad");
    public static final ResourceLocation GET_IN_THERE = id("get_in_there");
    public static final ResourceLocation I_DONT_THINK_SO = id("i_dont_think_so");

    private ModAdvancements() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BloodBound.MODID, path);
    }

    /** A perk bought off the web, at the tier it was bought at. */
    public static void onPerkBought(ServerPlayer player, int tier) {
        award(player, ROOT);
        award(player, switch (tier) {
            case 1 -> TIER1_PERK;
            case 2 -> TIER2_PERK;
            default -> TIER3_PERK;
        });
    }

    /** An addon bought off the web. An unstable one is worth a second line of its own. */
    public static void onAddonBought(ServerPlayer player, Addon addon) {
        award(player, ROOT);
        award(player, FIRST_ADDON);
        if (addon.rarity() == AddonRarity.UNSTABLE) {
            award(player, UNSTABLE_ADDON);
        }
    }

    /**
     * A feat pulled off. The root goes with it, so the tab is there to find it in even for somebody
     * who has somehow never bought anything.
     */
    public static void grant(ServerPlayer player, ResourceLocation feat) {
        award(player, ROOT);
        award(player, feat);
    }

    /** Hands one over. Already-earned advancements ignore this, so it is safe to call every time. */
    private static void award(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder advancement = server.getAdvancements().get(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }
}
