package net.h3xpy.bloodbound;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side tuning for soul shard drops and soulweb generation.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- soul shards ---

    static {
        BUILDER.comment("Soul shards: the currency mobs drop and the perk table spends.").push("soul_shards");
    }

    /**
     * Relative weights for how many shards a kill drops. The index is the shard count, so the
     * default reads as 10% for 0 shards, 40% for 1, 25% for 2, 15% for 3, 7% for 4 and 3% for 5.
     */
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> SHARD_DROP_WEIGHTS = BUILDER
            .comment("Drop weights by shard count. Entry 0 is the weight of dropping nothing, entry 1 the weight",
                    "of dropping one shard, and so on. Weights do not have to add up to 100.")
            .defineList("dropWeights", List.of(10, 40, 25, 15, 7, 3), () -> 1,
                    value -> value instanceof Integer weight && weight >= 0);

    public static final ModConfigSpec.BooleanValue REQUIRE_PLAYER_KILL = BUILDER
            .comment("Only drop shards when a player gets the kill.")
            .define("requirePlayerKill", true);

    public static final ModConfigSpec.BooleanValue HOSTILE_MOBS_ONLY = BUILDER
            .comment("Restrict shard drops to hostile mobs.")
            .define("hostileMobsOnly", true);

    static {
        BUILDER.pop();
        BUILDER.comment("Soulweb: the tree of perks and loot bought with soul shards.").push("soulweb");
    }

    // --- soulweb ---

    public static final ModConfigSpec.IntValue SOULWEB_BRANCHES = BUILDER
            .comment("How many branches radiate from the centre of the web. Buying a node commits the",
                    "player to that branch and locks every other one until the web resets.")
            .defineInRange("branches", 5, 3, 8);

    public static final ModConfigSpec.IntValue SOULWEB_MIN_DEPTH = BUILDER
            .comment("Fewest nodes a branch can hold.")
            .defineInRange("minDepth", 2, 1, 4);

    public static final ModConfigSpec.IntValue SOULWEB_MAX_DEPTH = BUILDER
            .comment("Most nodes a branch can hold.")
            .defineInRange("maxDepth", 4, 1, 4);

    public static final ModConfigSpec.IntValue SOULWEB_MAX_PERK_NODES = BUILDER
            .comment("Upper bound on how many nodes of a freshly rolled web hold a perk instead of loot.")
            .defineInRange("maxPerkNodes", 3, 1, 8);

    public static final ModConfigSpec.IntValue SOULWEB_MAX_ADDON_NODES = BUILDER
            .comment("Upper bound on how many nodes of a freshly rolled web hold an addon. Each web rolls",
                    "a random count from zero up to this. Only addons for perks the player already owns,",
                    "and does not already have, are offered.")
            .defineInRange("maxAddonNodes", 3, 0, 8);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    /**
     * Rolls a shard count from the configured weights.
     *
     * @param roll a value in [0, 1)
     */
    public static int rollShardCount(double roll) {
        List<? extends Integer> weights = SHARD_DROP_WEIGHTS.get();
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        if (total <= 0) {
            return 0;
        }
        int target = (int) (roll * total);
        for (int count = 0; count < weights.size(); count++) {
            target -= weights.get(count);
            if (target < 0) {
                return count;
            }
        }
        return 0;
    }
}
