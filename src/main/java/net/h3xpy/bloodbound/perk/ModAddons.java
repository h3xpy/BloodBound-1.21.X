package net.h3xpy.bloodbound.perk;

/**
 * Every addon definition. An addon only does anything while its parent perk is equipped, and only
 * one addon at a time can be equipped across the whole loadout.
 */
public final class ModAddons {

    /** Skill check success zones are 50% wider, but Surgical Suture heals 2 HP less. */
    public static final Addon NEEDLE_AND_THREAD = AddonRegistry.register(
            Addon.of("needle_and_thread", ModPerks.SURGICAL_SUTURE, AddonRarity.UNCOMMON));

    /**
     * A missed skill check grants a 3 second retry window instead of going straight on cooldown.
     * Landing the retry heals and shortens the cooldown; missing it lengthens the cooldown.
     */
    public static final Addon STERILIZER = AddonRegistry.register(
            Addon.of("sterilizer", ModPerks.SURGICAL_SUTURE, AddonRarity.RARE));

    /** Used at full health, a successful skill check grants absorption hearts instead of healing. */
    public static final Addon GEL_DRESSING = AddonRegistry.register(
            Addon.of("gel_dressing", ModPerks.SURGICAL_SUTURE, AddonRarity.UNSTABLE));

    /** More damage per block, and the arrows light the way. */
    public static final Addon TAPED_FLASHLIGHT = AddonRegistry.register(
            Addon.of("taped_flashlight", ModPerks.LONGSHOT, AddonRarity.UNCOMMON));

    /** Arrows fly flat, ignoring gravity. */
    public static final Addon GUNPOWDER = AddonRegistry.register(
            Addon.of("gunpowder", ModPerks.LONGSHOT, AddonRarity.RARE));

    /** Headshots multiply the whole damage, Longshot's distance bonus included. */
    public static final Addon POINT_BLANK = AddonRegistry.register(
            Addon.of("point_blank", ModPerks.LONGSHOT, AddonRarity.EPIC));

    /** Trades Low Profile's night vision for a large burst of crouch speed. */
    public static final Addon BOOTS_OF_SPEED = AddonRegistry.register(
            Addon.of("boots_of_speed", ModPerks.LOW_PROFILE, AddonRarity.RARE));

    /** Crouching slowly mends you as you go. */
    public static final Addon STEEL_TOE_BOOT = AddonRegistry.register(
            Addon.of("steel_toe_boot", ModPerks.LOW_PROFILE, AddonRarity.EPIC));

    /** Three more seconds on the return point's window. */
    public static final Addon BLACK_STRAP = AddonRegistry.register(
            Addon.of("black_strap", ModPerks.BROKEN_MOVEMENT_DEVICE, AddonRarity.COMMON));

    /** A shorter wait between return points. */
    public static final Addon BLACK_CABLE = AddonRegistry.register(
            Addon.of("black_cable", ModPerks.BROKEN_MOVEMENT_DEVICE, AddonRarity.COMMON));

    /** A brief window to take the return back, at the price of a slightly longer cooldown. */
    public static final Addon DIAGNOSTIC_TOOL_C = AddonRegistry.register(
            Addon.of("diagnostic_tool_c", ModPerks.BROKEN_MOVEMENT_DEVICE, AddonRarity.UNCOMMON));

    /** A burst of speed and no trail at all, but a hard stop if the window is wasted. */
    public static final Addon DIAGNOSTIC_TOOL_A = AddonRegistry.register(
            Addon.of("diagnostic_tool_a", ModPerks.BROKEN_MOVEMENT_DEVICE, AddonRarity.RARE));

    /** The return is played back along the path walked rather than jumped in one step. */
    public static final Addon DIAGNOSTIC_TOOL_B = AddonRegistry.register(
            Addon.of("diagnostic_tool_b", ModPerks.BROKEN_MOVEMENT_DEVICE, AddonRarity.EPIC));

    /** A little more durability back per landed check. */
    public static final Addon GRIP_WRENCH = AddonRegistry.register(
            Addon.of("grip_wrench", ModPerks.TINKERER, AddonRarity.COMMON));

    /** A gentler opening check to get the run going. */
    public static final Addon SPRING_CLAMP = AddonRegistry.register(
            Addon.of("spring_clamp", ModPerks.TINKERER, AddonRarity.UNCOMMON));

    /** The checks stop tightening, and a miss no longer costs durability. */
    public static final Addon AUTOMATIC_SCREWDRIVER = AddonRegistry.register(
            Addon.of("automatic_screwdriver", ModPerks.TINKERER, AddonRarity.EPIC));

    /** Repairs the item past what it was ever built to take. */
    public static final Addon DUCT_TAPE = AddonRegistry.register(
            Addon.of("duct_tape", ModPerks.TINKERER, AddonRarity.UNSTABLE));

    /** Charges trickle back one at a time instead of all at once. */
    public static final Addon GEAR_SYSTEM = AddonRegistry.register(
            Addon.of("gear_system", ModPerks.LOW_COST_MOVEMENT_DEVICE, AddonRarity.UNCOMMON));

    /** A harder throw, plain and simple. */
    public static final Addon OVERCLOCKED_MODULE = AddonRegistry.register(
            Addon.of("overclocked_module", ModPerks.LOW_COST_MOVEMENT_DEVICE, AddonRarity.COMMON));

    /** A slower shot that gets a second bite. */
    public static final Addon LOOSE_SCREW = AddonRegistry.register(
            Addon.of("loose_screw", ModPerks.LOW_COST_MOVEMENT_DEVICE, AddonRarity.RARE));

    /** One charge, a longer wait, a far harder throw — and anything you hit on the way pays. */
    public static final Addon BOX_OPENER = AddonRegistry.register(
            Addon.of("box_opener", ModPerks.LOW_COST_MOVEMENT_DEVICE, AddonRarity.UNSTABLE));

    /** Better odds on the ore reroll. */
    public static final Addon SHINY_COIN = AddonRegistry.register(
            Addon.of("shiny_coin", ModPerks.RAISE_THE_STAKES, AddonRarity.COMMON));

    /** Worse odds on ore, better odds on shards. */
    public static final Addon SCRATCHED_COIN = AddonRegistry.register(
            Addon.of("scratched_coin", ModPerks.RAISE_THE_STAKES, AddonRarity.UNCOMMON));

    /** A slim chance at a whole block of the ore you just mined. */
    public static final Addon TARNISHED_COIN = AddonRegistry.register(
            Addon.of("tarnished_coin", ModPerks.RAISE_THE_STAKES, AddonRarity.RARE));

    /** Far better odds, but a failed first roll costs you the ore itself. */
    public static final Addon SHATTERED_COIN = AddonRegistry.register(
            Addon.of("shattered_coin", ModPerks.RAISE_THE_STAKES, AddonRarity.UNSTABLE));

    /** A shot from far enough away pins the target and paints them for you. */
    public static final Addon BOUNTY_POSTER = AddonRegistry.register(
            Addon.of("bounty_poster", ModPerks.LONGSHOT, AddonRarity.UNSTABLE));

    /** The echo reaches further between victims. */
    public static final Addon ROTTING_ROPE = AddonRegistry.register(
            Addon.of("rotting_rope", ModPerks.ECHOING_WOUNDS, AddonRarity.COMMON));

    /** Everything the echo touches is left blind and slow. */
    public static final Addon HYSTERIA = AddonRegistry.register(
            Addon.of("hysteria", ModPerks.ECHOING_WOUNDS, AddonRarity.UNCOMMON));

    /** The chain reaches two more victims. */
    public static final Addon BLOODIED_LETTER = AddonRegistry.register(
            Addon.of("bloodied_letter", ModPerks.ECHOING_WOUNDS, AddonRarity.UNCOMMON));

    /** The echo starts weaker and gets stronger with every jump instead of fading. */
    public static final Addon WRAPPED_GLASS = AddonRegistry.register(
            Addon.of("wrapped_glass", ModPerks.ECHOING_WOUNDS, AddonRarity.EPIC));

    /** A chain that carries on pays the wielder back in health. */
    public static final Addon BLOOD_THIRSTY_SKULL = AddonRegistry.register(
            Addon.of("blood_thirsty_skull", ModPerks.ECHOING_WOUNDS, AddonRarity.RARE));

    /** A gentle drift down, and the perk comes back sooner. */
    public static final Addon HELIUM_INFLATED_BALLOON = AddonRegistry.register(
            Addon.of("helium_inflated_balloon", ModPerks.PERFECT_LANDING, AddonRarity.COMMON));

    /** Straight down, hard, and whatever is underneath wears it. */
    public static final Addon DEAD_WEIGHT = AddonRegistry.register(
            Addon.of("dead_weight", ModPerks.PERFECT_LANDING, AddonRarity.RARE));

    /** The longer the drop, the longer the sprint out of it. */
    public static final Addon MOMENTUM_FORMULA = AddonRegistry.register(
            Addon.of("momentum_formula", ModPerks.PERFECT_LANDING, AddonRarity.EPIC));

    /** A harder shove off the mark. */
    public static final Addon LEATHER_GLOVE = AddonRegistry.register(
            Addon.of("leather_glove", ModPerks.CLOSE_CALL, AddonRarity.COMMON));

    /** Whatever is in the way is sent flying. */
    public static final Addon PROTECTIVE_GLOVE = AddonRegistry.register(
            Addon.of("protective_glove", ModPerks.CLOSE_CALL, AddonRarity.EPIC));

    /** Three dashes back to back, and a long wait for them. */
    public static final Addon FINGERLESS_GLOVE = AddonRegistry.register(
            Addon.of("fingerless_glove", ModPerks.CLOSE_CALL, AddonRarity.UNSTABLE));

    /** The price of the sacrifice is paid off sooner. */
    public static final Addon CRACKED_CUP = AddonRegistry.register(
            Addon.of("cracked_cup", ModPerks.KEEP_FIGHTING, AddonRarity.UNCOMMON));

    /** The one you saved runs while you cannot mend. */
    public static final Addon RUNE_OF_SWIFTNESS = AddonRegistry.register(
            Addon.of("rune_of_swiftness", ModPerks.KEEP_FIGHTING, AddonRarity.RARE));

    /** Give your health away and disappear, seeing everything that is still out there. */
    public static final Addon RUNE_OF_STEALTH = AddonRegistry.register(
            Addon.of("rune_of_stealth", ModPerks.KEEP_FIGHTING, AddonRarity.EPIC));

    /** The aura reaches further. */
    public static final Addon DIRTY_SHROUD = AddonRegistry.register(
            Addon.of("dirty_shroud", ModPerks.LIGHTBRINGER, AddonRarity.COMMON));

    /** The more company you keep, the stronger the aura. */
    public static final Addon CHARM_OF_THE_FAITHFUL = AddonRegistry.register(
            Addon.of("charm_of_the_faithful", ModPerks.LIGHTBRINGER, AddonRarity.UNCOMMON));

    /** Nearly worthless in a crowd, and remarkable on your own. */
    public static final Addon GILDED_CROSS = AddonRegistry.register(
            Addon.of("gilded_cross", ModPerks.LIGHTBRINGER, AddonRarity.EPIC));

    /** The chain starts while there is still some health left. */
    public static final Addon WILD_ROSE = AddonRegistry.register(
            Addon.of("wild_rose", ModPerks.RELENTLESS, AddonRarity.COMMON));

    /** Always on, at the price of a long wait. */
    public static final Addon WOODEN_SWORD = AddonRegistry.register(
            Addon.of("wooden_sword", ModPerks.RELENTLESS, AddonRarity.UNCOMMON));

    /** Held back until it is nearly too late, and one blow longer for it. */
    public static final Addon BLOODIED_MATCHETE = AddonRegistry.register(
            Addon.of("bloodied_matchete", ModPerks.RELENTLESS, AddonRarity.RARE));

    /** Mending yourself goes quicker. */
    public static final Addon BANDAGES_WRAP = AddonRegistry.register(
            Addon.of("bandages_wrap", ModPerks.PATCH_UP, AddonRarity.COMMON));

    /** More chances to prove you know what you are doing. */
    public static final Addon CLEAN_GLOVES = AddonRegistry.register(
            Addon.of("clean_gloves", ModPerks.PATCH_UP, AddonRarity.UNCOMMON));

    /** Everyone standing close by mends alongside you. */
    public static final Addon FIRST_AID_SPRAY_CAN = AddonRegistry.register(
            Addon.of("first_aid_spray_can", ModPerks.PATCH_UP, AddonRarity.RARE));

    /** A streak survives longer between blows. */
    public static final Addon KNIFE_BELT = AddonRegistry.register(
            Addon.of("knife_belt", ModPerks.NASTY_BLADE, AddonRarity.COMMON));

    /** Every token is worth more. */
    public static final Addon RAZOR_BLADE = AddonRegistry.register(
            Addon.of("razor_blade", ModPerks.NASTY_BLADE, AddonRarity.UNCOMMON));

    /** A softer blade still, and nothing it cuts can close the wound. */
    public static final Addon ANTICOAGULANT = AddonRegistry.register(
            Addon.of("anticoagulant", ModPerks.NASTY_BLADE, AddonRarity.EPIC));

    // --- Bandages Wrap tuning ---
    /** How much quicker a self-heal runs. */
    public static final double BANDAGES_WRAP_SPEED = 1.25D;

    // --- Clean Gloves tuning ---
    /** Added to the odds of a skill check on each roll. */
    public static final float CLEAN_GLOVES_CHECK_CHANCE = 0.20F;

    // --- First Aid Spray Can tuning ---
    /** How far the spray carries. */
    public static final double FIRST_AID_SPRAY_RANGE = 8.0D;
    /** How fast bystanders mend, against the player's own rate. */
    public static final float FIRST_AID_SPRAY_RATE = 0.75F;

    // --- Knife Belt tuning ---
    /** Extra seconds on the streak timer. */
    public static final int KNIFE_BELT_EXTRA_SECONDS = 2;

    // --- Razor Blade tuning ---
    /** Damage each token adds back, in place of the perk's own figure. */
    public static final float RAZOR_BLADE_PER_TOKEN = 0.75F;

    // --- Anticoagulant tuning ---
    /** Damage taken off every swing, in place of the perk's own figure. */
    public static final float ANTICOAGULANT_PENALTY = 3.0F;
    /** How long the Broken outlives the streak that caused it. */
    public static final int ANTICOAGULANT_BROKEN_TAIL_TICKS = 300;

    /** Longer to catch your breath before the trial. */
    public static final Addon BLOOD_STAINED_BOOK = AddonRegistry.register(
            Addon.of("blood_stained_book", ModPerks.GUARDIAN_ANGEL, AddonRarity.COMMON));

    /** Half the trial to get through. */
    public static final Addon BELIEVERS_EYE = AddonRegistry.register(
            Addon.of("believers_eye", ModPerks.GUARDIAN_ANGEL, AddonRarity.UNCOMMON));

    /** Every check you land is something kept back from the grave. */
    public static final Addon ENGRAVED_TABLET = AddonRegistry.register(
            Addon.of("engraved_tablet", ModPerks.GUARDIAN_ANGEL, AddonRarity.EPIC));

    // --- Advanced Movement Device ---

    /** More charges coming back, per second. */
    public static final Addon WIRE_SPOOL = AddonRegistry.register(
            Addon.of("wire_spool", ModPerks.ADVANCED_MOVEMENT_DEVICE, AddonRarity.COMMON));

    /** A bigger reserve, a longer shot, and a faster one. */
    public static final Addon SCRAPS = AddonRegistry.register(
            Addon.of("scraps", ModPerks.ADVANCED_MOVEMENT_DEVICE, AddonRarity.UNCOMMON));

    /** Fills fast, holds a little less, and patches you up on arrival. */
    public static final Addon PRIMER_BULB = AddonRegistry.register(
            Addon.of("primer_bulb", ModPerks.ADVANCED_MOVEMENT_DEVICE, AddonRarity.UNCOMMON));

    /** The shot is on a leash: let go of the key and you are there. */
    public static final Addon TENSION_SPRING = AddonRegistry.register(
            Addon.of("tension_spring", ModPerks.ADVANCED_MOVEMENT_DEVICE, AddonRarity.RARE));

    /** No bounce, and it follows where you look. */
    public static final Addon FIELD_RECORDER = AddonRegistry.register(
            Addon.of("field_recorder", ModPerks.ADVANCED_MOVEMENT_DEVICE, AddonRarity.EPIC));

    // --- Beware The Power Of An Angel ---

    /** Charges come back sooner. */
    public static final Addon BRIGHT_FEATHER = AddonRegistry.register(
            Addon.of("bright_feather", ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL, AddonRarity.COMMON));

    /** Faster through the air, and thirstier for it. */
    public static final Addon BELT_PENDANT = AddonRegistry.register(
            Addon.of("belt_pendant", ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL, AddonRarity.UNCOMMON));

    /** A deeper reserve that takes longer to fill. */
    public static final Addon GOLDEN_CROWN = AddonRegistry.register(
            Addon.of("golden_crown", ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL, AddonRarity.RARE));

    /** A bow drawn on the wing. */
    public static final Addon GABRIELS_BOW = AddonRegistry.register(
            Addon.of("gabriels_bow", ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL, AddonRarity.EPIC));

    /** All the flight you could want, and nothing to hide behind while you use it. */
    public static final Addon MARK_OF_THE_BANISHED = AddonRegistry.register(
            Addon.of("mark_of_the_banished", ModPerks.BEWARE_THE_POWER_OF_AN_ANGEL, AddonRarity.UNSTABLE));

    // --- Hunter's Instinct ---

    /** Hears further. */
    public static final Addon EMPTY_SHOTGUN_SHELL = AddonRegistry.register(
            Addon.of("empty_shotgun_shell", ModPerks.HUNTERS_INSTINCT, AddonRarity.COMMON));

    /** Keep your eyes on it and it stays lit. */
    public static final Addon BOUNTY_HUNTER_LICENSE = AddonRegistry.register(
            Addon.of("bounty_hunter_license", ModPerks.HUNTERS_INSTINCT, AddonRarity.UNCOMMON));

    // --- Flashbang ---

    /** Lighter to carry, sooner to throw again. */
    public static final Addon LIGHT_STEEL = AddonRegistry.register(
            Addon.of("light_steel", ModPerks.FLASHBANG, AddonRarity.COMMON));

    /** Barely time to look away. */
    public static final Addon FAST_FUSE = AddonRegistry.register(
            Addon.of("fast_fuse", ModPerks.FLASHBANG, AddonRarity.UNCOMMON));

    /** Two in the bag. */
    public static final Addon WORN_SATCHEL = AddonRegistry.register(
            Addon.of("worn_satchel", ModPerks.FLASHBANG, AddonRarity.RARE));

    /** Hold it, and throw it further. */
    public static final Addon PULLED_PIN = AddonRegistry.register(
            Addon.of("pulled_pin", ModPerks.FLASHBANG, AddonRarity.EPIC));

    // --- Bank Shot ---

    /** Comes back sooner. */
    public static final Addon SLIME_GOO = AddonRegistry.register(
            Addon.of("slime_goo", ModPerks.BANK_SHOT, AddonRarity.COMMON));

    /** A wider look round the corner. */
    public static final Addon BROKEN_ARROW = AddonRegistry.register(
            Addon.of("broken_arrow", ModPerks.BANK_SHOT, AddonRarity.RARE));

    /** What comes off the wall bites. */
    public static final Addon JAGGED_HEAD = AddonRegistry.register(
            Addon.of("jagged_head", ModPerks.BANK_SHOT, AddonRarity.RARE));

    /** The wall throws it where you are looking. */
    public static final Addon PAPER_FAN = AddonRegistry.register(
            Addon.of("paper_fan", ModPerks.BANK_SHOT, AddonRarity.EPIC));

    /** Every shot bounces, nothing is aimed for you, and each wall adds to the blow. */
    public static final Addon ORIGAMI_CRANE = AddonRegistry.register(
            Addon.of("origami_crane", ModPerks.BANK_SHOT, AddonRarity.UNSTABLE));

    // --- Anti-Exhaustion Syringe ---

    /** One shot refills everything you carry. */
    public static final Addon METAL_SYRINGE = AddonRegistry.register(
            Addon.of("metal_syringe", ModPerks.ANTI_EXHAUSTION_SYRINGE, AddonRarity.UNCOMMON));

    /** Two weaker doses instead of one. */
    public static final Addon DILUTED_SERUM = AddonRegistry.register(
            Addon.of("diluted_serum", ModPerks.ANTI_EXHAUSTION_SYRINGE, AddonRarity.RARE));

    /** Barely a dose at all, but it clears everything and you can take it again at once. */
    public static final Addon INHALATOR = AddonRegistry.register(
            Addon.of("inhalator", ModPerks.ANTI_EXHAUSTION_SYRINGE, AddonRarity.UNSTABLE));

    // --- Empty Shotgun Shell tuning ---
    /** Multiplier on Hunter's Instinct range. */
    public static final double EMPTY_SHELL_RANGE = 1.25D;

    // --- Bounty Hunter License tuning ---
    /** Half-angle, in degrees, of what counts as looking straight at a revealed target. */
    public static final double BOUNTY_LICENSE_VIEW_DEGREES = 20.0D;
    /** How long the reveal is topped up by, each tick the player keeps it in view. */
    public static final int BOUNTY_LICENSE_TOP_UP_TICKS = 40;

    // --- Light Steel tuning ---
    /** Multiplier on the Flashbang cooldown. */
    public static final float LIGHT_STEEL_COOLDOWN = 0.70F;

    // --- Fast Fuse tuning ---
    /** How much faster the fuse burns. */
    public static final double FAST_FUSE_SPEED = 1.75D;

    // --- Worn Satchel tuning ---
    /** Grenades that may be thrown before the cooldown lands. */
    public static final int WORN_SATCHEL_CHARGES = 2;

    // --- Pulled Pin tuning ---
    /** The most the throw can be wound up to, and how long holding it takes to get there. */
    public static final double PULLED_PIN_MAX_BONUS = 2.0D;
    public static final int PULLED_PIN_FULL_HOLD_TICKS = 80;

    // --- Slime Goo tuning ---
    /** Multiplier on how long a Bank Shot charge takes to come back. */
    public static final float SLIME_GOO_RECHARGE = 0.75F;

    // --- Broken Arrow tuning ---
    /** Multiplier on the half-angle of the seeking cone. */
    public static final double BROKEN_ARROW_CONE = 1.35D;

    // --- Jagged Head tuning ---
    /** Odds a bounced shot opens a wound, and how deep it is. */
    public static final float JAGGED_HEAD_CHANCE = 0.50F;
    public static final int JAGGED_HEAD_BLEED = 15;

    // --- Paper Fan tuning ---
    /** How hard a bounced shot bends onto the crosshair each tick, as a share of the way there. */
    public static final double PAPER_FAN_DRIFT = 0.35D;
    /** How long a hit reveals the target for, and what hitting an already revealed one is worth. */
    public static final int PAPER_FAN_REVEAL_TICKS = 200;
    public static final float PAPER_FAN_REVEALED_MULTIPLIER = 1.5F;

    // --- Origami Crane tuning ---
    /** Bounces a shot gets, and what each one adds to the damage. */
    public static final int ORIGAMI_BOUNCES = 5;
    public static final float ORIGAMI_DAMAGE_PER_BOUNCE = 0.10F;

    // --- Metal Syringe tuning ---
    /** Multiplier on the syringe's cooldown, in exchange for refilling every charge you hold. */
    public static final float METAL_SYRINGE_COOLDOWN = 1.10F;

    // --- Diluted Serum tuning ---
    /** Doses before the cooldown lands, and what is left of each one. */
    public static final int DILUTED_SERUM_CHARGES = 2;
    public static final float DILUTED_SERUM_POTENCY = 0.65F;

    // --- Inhalator tuning ---
    /** What is left of the dose, and of the cooldown. */
    public static final float INHALATOR_POTENCY = 0.25F;
    public static final float INHALATOR_COOLDOWN = 0.25F;


    // --- Frag' Nade ---

    /** Both blasts reach further. */
    public static final Addon FRAGSTONE_SHARD = AddonRegistry.register(
            Addon.of("fragstone_shard", ModPerks.FRAGNADE, AddonRarity.COMMON));

    /** A shorter fuse once it lands. */
    public static final Addon RAW_ORE = AddonRegistry.register(
            Addon.of("raw_ore", ModPerks.FRAGNADE, AddonRarity.COMMON));

    /** See where it will go before it goes. */
    public static final Addon STAR_MEDAL = AddonRegistry.register(
            Addon.of("star_medal", ModPerks.FRAGNADE, AddonRarity.UNCOMMON));

    /** A longer wait for a bigger, crueller second blast. */
    public static final Addon POWDERED_CRYSTAL = AddonRegistry.register(
            Addon.of("powdered_crystal", ModPerks.FRAGNADE, AddonRarity.RARE));

    /** A wide, weak, slowing first blast, and a harder second one. */
    public static final Addon QUARTZ_PENDANT = AddonRegistry.register(
            Addon.of("quartz_pendant", ModPerks.FRAGNADE, AddonRarity.EPIC));

    /** The second blast carries you instead of hurting you. */
    public static final Addon PURE_TOPAZ = AddonRegistry.register(
            Addon.of("pure_topaz", ModPerks.FRAGNADE, AddonRarity.RARE));

    // --- No One Gets Away ---

    /** A faster harpoon. */
    public static final Addon ENHANCED_GAUNTLET = AddonRegistry.register(
            Addon.of("enhanced_gauntlet", ModPerks.NO_ONE_GETS_AWAY, AddonRarity.COMMON));

    /** The harpoon finds its mark. */
    public static final Addon TRACKING_HEAD = AddonRegistry.register(
            Addon.of("tracking_head", ModPerks.NO_ONE_GETS_AWAY, AddonRarity.RARE));

    /** You go to them. */
    public static final Addon HEAVY_HOOK = AddonRegistry.register(
            Addon.of("heavy_hook", ModPerks.NO_ONE_GETS_AWAY, AddonRarity.EPIC));

    /** Through walls, and seeing everything it passes. */
    public static final Addon SOUL_CHAIN = AddonRegistry.register(
            Addon.of("soul_chain", ModPerks.NO_ONE_GETS_AWAY, AddonRarity.UNSTABLE));

    // --- Longshot ---

    /** Arrows that will not fall and will not slow down. */
    public static final Addon ODD_ARROW = AddonRegistry.register(
            Addon.of("odd_arrow", ModPerks.LONGSHOT, AddonRarity.UNSTABLE));

    /** Arrows that look for something to bleed. */
    public static final Addon CURSED_RISER = AddonRegistry.register(
            Addon.of("cursed_riser", ModPerks.LONGSHOT, AddonRarity.EPIC));

    // --- Fragstone Shard tuning ---
    public static final double FRAGSTONE_SHARD_RADIUS = 1.15D;

    // --- Raw Ore tuning ---
    /** What is left of the fuse. */
    public static final float RAW_ORE_FUSE = 0.70F;

    // --- Powdered Crystal tuning ---
    public static final float POWDERED_CRYSTAL_INTERVAL = 1.80F;
    public static final double POWDERED_CRYSTAL_SECOND_RADIUS = 1.40D;
    public static final int POWDERED_CRYSTAL_BROKEN_TICKS = 120;

    // --- Quartz Pendant tuning ---
    /** The first blast: how much wider, what is left of its damage, and how long it slows. */
    public static final double QUARTZ_PENDANT_FIRST_RADIUS = 2.5D;
    public static final float QUARTZ_PENDANT_FIRST_DAMAGE = 0.30F;
    public static final int QUARTZ_PENDANT_SLOW_TICKS = 80;
    /** What the second blast deals, against its usual. */
    public static final float QUARTZ_PENDANT_SECOND_DAMAGE = 1.40F;

    // --- Pure Topaz tuning ---
    /** How much harder the second blast throws, and how long after landing a fall is still forgiven. */
    public static final double PURE_TOPAZ_KNOCKBACK = 2.5D;
    public static final int PURE_TOPAZ_FALL_GRACE_TICKS = 40;

    // --- Enhanced Gauntlet tuning ---
    public static final double ENHANCED_GAUNTLET_SPEED = 1.5D;

    // --- Tracking Head tuning ---
    /** Share of the way onto the mark the harpoon turns each tick, and where it looks for one. */
    public static final double TRACKING_HEAD_DRIFT = 0.12D;
    public static final double TRACKING_HEAD_CONE = 35.0D;
    public static final double TRACKING_HEAD_RANGE = 16.0D;

    // --- Soul Chain tuning ---
    public static final double SOUL_CHAIN_SPEED = 0.85D;
    public static final float SOUL_CHAIN_MISS_COOLDOWN = 0.5F;
    public static final double SOUL_CHAIN_REVEAL_RADIUS = 2.0D;
    public static final int SOUL_CHAIN_REVEAL_TICKS = 60;

    // --- Odd Arrow tuning ---
    /** Share of its speed an arrow leaves with, how much it gains a tick, and the most it reaches. */
    public static final double ODD_ARROW_START_SPEED = 0.4D;
    public static final double ODD_ARROW_ACCELERATION = 1.06D;
    public static final double ODD_ARROW_MAX_SPEED = 4.0D;
    /** Extra ticks of draw per tick held: one, which is twice as fast. */
    public static final double ODD_ARROW_DRAW = 1.0D;
    /** How far across its line it weaves, as a share of its speed, and how long one S takes. */
    public static final double ODD_ARROW_WAVE_AMPLITUDE = 0.35D;
    public static final int ODD_ARROW_WAVE_PERIOD = 20;

    // --- Cursed Riser tuning ---
    public static final double CURSED_RISER_DRIFT = 0.08D;
    public static final double CURSED_RISER_CONE = 30.0D;
    public static final double CURSED_RISER_RANGE = 24.0D;
    public static final int CURSED_RISER_BLEED = 18;

    // --- Wire Spool tuning ---
    /** Multiplier on how fast the reserve fills. */
    public static final double WIRE_SPOOL_REGEN = 1.75D;

    // --- Scraps tuning ---
    /** Multiplier on the reserve and on one shot's budget, and on the shot's speed. */
    public static final double SCRAPS_CHARGES = 1.20D;
    public static final double SCRAPS_SPEED = 1.40D;

    // --- Primer Bulb tuning ---
    /** Multiplier on how fast the reserve fills, and on how much of it there is. */
    public static final double PRIMER_BULB_REGEN = 2.50D;
    public static final double PRIMER_BULB_CHARGES = 0.92D;
    /** The most a teleport can mend, in HP. Anything from nothing to this. */
    public static final int PRIMER_BULB_MAX_HEAL = 4;

    // --- Field Recorder tuning ---
    /**
     * How hard the shot pulls onto the line the player is looking down, per tick, as a share of the
     * way there. High enough to steer round a corner, short of turning it into a homing shot.
     */
    public static final double FIELD_RECORDER_DRIFT = 0.35D;
    /** Multiplier on what a block of flight costs. */
    public static final double FIELD_RECORDER_COST = 1.5D;

    // --- Tension Spring tuning ---
    /** Cooldown after being pulled to the shot, so a release cannot be chained into a new shot. */
    public static final int TENSION_SPRING_COOLDOWN_TICKS = 20;

    // --- Bright Feather tuning ---
    /** Seconds per charge, replacing the perk's own. */
    public static final double BRIGHT_FEATHER_RECHARGE_SECONDS = 1.25D;

    // --- Belt Pendant tuning ---
    /** Multiplier on flying speed, and the charges a second of it now costs. */
    public static final double BELT_PENDANT_SPEED = 1.75D;
    public static final double BELT_PENDANT_DRAIN = 4.0D;

    // --- Golden Crown tuning ---
    /** Multiplier on the reserve, and the seconds per charge that pays for it. */
    public static final double GOLDEN_CROWN_CHARGES = 1.25D;
    public static final double GOLDEN_CROWN_RECHARGE_SECONDS = 2.25D;

    // --- Gabriel's Bow tuning ---
    /** Extra ticks of draw earned per tick held, so a bow comes up this much faster. */
    public static final double GABRIELS_BOW_DRAW = 0.75D;
    /** Multiplier on arrow speed. The damage the speed would have bought is taken back off. */
    public static final double GABRIELS_BOW_ARROW_SPEED = 2.50D;

    // --- Mark Of The Banished tuning ---
    /** Multiplier on the reserve. */
    public static final double BANISHED_CHARGES = 5.0D;

    // --- Blood Stained Book tuning ---
    /** Extra seconds before the trial begins. */
    public static final int BLOOD_STAINED_BOOK_EXTRA_SECONDS = 10;

    // --- Believer's Eye tuning ---
    /** What the number of checks is divided by. */
    public static final int BELIEVERS_EYE_FEWER_CHECKS = 2;

    // --- Engraved Tablet tuning ---
    /** Share of the player's experience each landed check keeps back from the grave. */
    public static final float ENGRAVED_TABLET_XP_PER_TOKEN = 0.12F;

    // --- Leather Glove tuning ---
    /** Multiplier on how far the dash carries. */
    public static final double LEATHER_GLOVE_DISTANCE = 1.4D;

    // --- Protective Glove tuning ---
    /** How hard anything caught by the dash is thrown. */
    public static final double PROTECTIVE_GLOVE_KNOCKBACK = 2.0D;

    // --- Fingerless Glove tuning ---
    /** How many dashes one activation is worth. */
    public static final int FINGERLESS_GLOVE_DASHES = 3;
    /** How long the player has to call for the next one. */
    public static final int FINGERLESS_GLOVE_WINDOW_TICKS = 40;
    /** Multiplier on the cooldown, once the chain is done with. */
    public static final float FINGERLESS_GLOVE_COOLDOWN = 3.5F;

    // --- Cracked Cup tuning ---
    /** Multiplier on the Broken the perk leaves behind. */
    public static final float CRACKED_CUP_BROKEN = 0.75F;

    // --- Rune of Stealth tuning ---
    public static final int RUNE_OF_STEALTH_INVISIBILITY_TICKS = 200;
    public static final int RUNE_OF_STEALTH_RESISTANCE_TICKS = 100;
    /** How far the reveal reaches, and how long it holds. */
    public static final double RUNE_OF_STEALTH_REVEAL_RANGE = 16.0D;
    public static final int RUNE_OF_STEALTH_REVEAL_TICKS = 200;
    /** Multiplier on the Broken the perk leaves behind. */
    public static final float RUNE_OF_STEALTH_BROKEN = 1.1F;

    // --- Dirty Shroud tuning ---
    /** Blocks added to Lightbringer's radius. */
    public static final double DIRTY_SHROUD_EXTRA_RADIUS = 8.0D;

    // --- Charm of the Faithful tuning ---
    /** Recovery added per other player standing in the aura. */
    public static final double CHARM_OF_THE_FAITHFUL_PER_PLAYER = 0.15D;

    // --- Gilded Cross tuning ---
    /** Recovery rate in company, and alone, per perk tier. */
    public static final double[] GILDED_CROSS_CROWDED = { 20.0D, 28.0D, 35.0D };
    public static final double[] GILDED_CROSS_ALONE = { 80.0D, 88.0D, 95.0D };

    // --- Wild Rose tuning ---
    /** Health at which the chain starts, in hearts, per perk tier. */
    public static final double[] WILD_ROSE_HEARTS = { 7.0D, 7.5D, 8.0D };

    // --- Wooden Sword tuning ---
    /** Multiplier on the cooldown, for dropping the health condition entirely. */
    public static final float WOODEN_SWORD_COOLDOWN = 2.5F;

    // --- Bloodied Matchete tuning ---
    /** Health at which the chain starts, in hearts, per perk tier. */
    public static final double[] BLOODIED_MATCHETE_HEARTS = { 4.5D, 5.0D, 5.5D };
    /** Share of the opening blow the extra hit lands for, per perk tier. */
    public static final float[] BLOODIED_MATCHETE_EXTRA_HIT = { 0.15F, 0.075F, 0.0325F };

    // --- Rotting Rope tuning ---
    /** Multiplier on how far the echo looks for its next victim. */
    public static final double ROTTING_ROPE_RANGE = 1.5D;

    // --- Hysteria tuning ---
    /** How long the blindness and slowness last on an echo victim. */
    public static final int HYSTERIA_EFFECT_TICKS = 60;
    /** Extra seconds on the perk's cooldown. */
    public static final int HYSTERIA_EXTRA_COOLDOWN_SECONDS = 1;

    // --- Bloodied Letter tuning ---
    /** Extra victims the chain may reach. */
    public static final int BLOODIED_LETTER_EXTRA_JUMPS = 2;

    // --- Wrapped Glass tuning ---
    /** Share of the opening blow the first echo carries. */
    public static final float WRAPPED_GLASS_FIRST_HIT = 0.8F;
    /** What each jump multiplies the last one by, per perk tier. */
    public static final float[] WRAPPED_GLASS_GROWTH = { 1.20F, 1.25F, 1.30F };

    // --- Blood Thirsty Skull tuning ---
    /** Health returned per jump, from the second one on. */
    public static final float BLOOD_THIRSTY_SKULL_HEAL = 2.0F;

    // --- Helium Inflated Balloon tuning ---
    /** Share of gravity held back on the way down, which is also the share taken off the fall speed. */
    public static final double HELIUM_FALL_REDUCTION = 0.35D;
    /** Extra steering while airborne, against vanilla's own air acceleration. */
    public static final double HELIUM_AIR_CONTROL = 0.30D;
    /** Multiplier on Perfect Landing's cooldown. */
    public static final float HELIUM_COOLDOWN = 0.6F;

    // --- Dead Weight tuning ---
    /** Extra gravity on the way down, as a share of the usual pull. */
    public static final double DEAD_WEIGHT_EXTRA_FALL = 0.75D;
    /** Extra Speed levels on landing. Speed III to IV is a third more movement. */
    public static final int DEAD_WEIGHT_SPEED_LEVELS = 1;
    /** Damage dealt to whatever is landed on, per block fallen, and the most it can reach. */
    public static final float DEAD_WEIGHT_DAMAGE_PER_BLOCK = 1.5F;
    public static final float DEAD_WEIGHT_MAX_DAMAGE = 30.0F;
    /** What is left of the Speed on landing: the weight costs you the run-off. */
    public static final float DEAD_WEIGHT_SPEED_DURATION = 0.67F;

    // --- Momentum Formula tuning ---
    /** How much faster the fall runs once it has started in earnest. */
    public static final double MOMENTUM_FALL_MULTIPLIER = 2.5D;
    /** Blocks that have to be behind you before the acceleration takes hold. */
    public static final float MOMENTUM_MIN_FALL = 3.0F;
    /** What is left of the Speed on landing: all that speed is spent on the way down. */
    public static final float MOMENTUM_SPEED_DURATION = 0.6F;

    // --- Gear System tuning ---
    /** Ticks between two charges coming back. */
    public static final int GEAR_SYSTEM_RECHARGE_TICKS = 80;

    // --- Overclocked Module tuning ---
    /** Multiplier on every impulse. */
    public static final double OVERCLOCKED_MODULE_IMPULSE = 1.3D;

    // --- Loose Screw tuning ---
    /** How many times the shot may bounce off a block and carry on. */
    public static final int LOOSE_SCREW_BOUNCES = 1;
    /**
     * How fast the shot flies with this addon fitted, against the perk's own figure. A bouncing
     * shot is aimed at a wall a few blocks off rather than across the field, so it is thrown
     * slower than a plain one and stays easier to place.
     */
    public static final double LOOSE_SCREW_SPEED = 1.0D;
    /** How much speed the shot keeps coming off a bounce. */
    public static final double LOOSE_SCREW_BOUNCE_SPEED = 0.65D;

    // --- Box Opener tuning ---
    /** The only charge the device gets. */
    public static final int BOX_OPENER_CHARGES = 1;
    /** Extra seconds on the cooldown. */
    public static final int BOX_OPENER_EXTRA_COOLDOWN_SECONDS = 3;
    /** Multiplier on every impulse. */
    public static final double BOX_OPENER_IMPULSE = 1.5D;
    /** How long after a launch a collision still counts as a ram. */
    public static final int BOX_OPENER_IMPACT_TICKS = 40;
    /** Damage per block travelled per tick at the moment of impact. */
    public static final double BOX_OPENER_DAMAGE_PER_SPEED = 8.0D;
    /** However fast the ram, it stops here. */
    public static final float BOX_OPENER_MAX_DAMAGE = 18.0F;

    // --- Shiny Coin tuning ---
    /** Added to Raise The Stakes' reroll chance. */
    public static final float SHINY_COIN_BONUS = 0.0777F;

    // --- Scratched Coin tuning ---
    /** Taken off Raise The Stakes' reroll chance. */
    public static final float SCRATCHED_COIN_PENALTY = 0.10F;
    /** Shards a hostile mob gives up instead of the perk's usual one. */
    public static final int SCRATCHED_COIN_SHARDS = 2;

    // --- Tarnished Coin tuning ---
    /** Chance an ore gives up a whole storage block of itself. */
    public static final float TARNISHED_COIN_CHANCE = 0.04F;
    /** Taken off the reroll chance to pay for it. */
    public static final float TARNISHED_COIN_PENALTY = 0.04F;

    // --- Shattered Coin tuning ---
    /** The reroll chance this addon forces, by perk tier. */
    public static final float[] SHATTERED_COIN_CHANCE = { 0.50F, 0.55F, 0.60F };

    // --- Bounty Poster tuning ---
    /** How far an arrow must have flown for the bounty to stick. */
    public static final double BOUNTY_POSTER_RANGE = 27.0D;
    /** How long both Exposed and the aura reveal last. */
    public static final int BOUNTY_POSTER_DURATION_TICKS = 400;

    // --- Grip Wrench tuning ---
    /** Extra share of durability returned by a landed check. */
    public static final float GRIP_WRENCH_BONUS = 0.02F;

    // --- Spring Clamp tuning ---
    /** Multiplier on the opening check's window. */
    public static final float SPRING_CLAMP_MULTIPLIER = 1.5F;

    // --- Automatic Screwdriver tuning ---
    /** The checks never shrink below this share of the opening one. */
    public static final float SCREWDRIVER_MIN_ZONE_FRACTION = 0.4F;

    // --- Duct Tape tuning ---
    /** How far past its built maximum an item's durability pool is stretched. */
    public static final float DUCT_TAPE_OVERSHOOT = 1.1F;

    // --- Black Strap tuning ---
    /** Ticks added to the return point's window. */
    public static final int BLACK_STRAP_EXTRA_TICKS = 60;

    // --- Black Cable tuning ---
    /** Multiplier on the perk's cooldown. */
    public static final float BLACK_CABLE_COOLDOWN = 0.7F;

    // --- Diagnostic Tool C tuning ---
    /** How long the player has to take the return back, in ticks. */
    public static final int TOOL_C_UNDO_WINDOW_TICKS = 80;
    /** Multiplier on the perk's cooldown, paid for the second chance. */
    public static final float TOOL_C_COOLDOWN = 1.1F;

    // --- Diagnostic Tool A tuning ---
    /** How long the opening burst of speed lasts, in ticks. */
    public static final int TOOL_A_BURST_TICKS = 20;
    /** Speed level of the opening burst (V). */
    public static final int TOOL_A_BURST_LEVEL = 5;
    /** Speed level held for the rest of the window (I). */
    public static final int TOOL_A_TRAVEL_LEVEL = 1;
    /** Slowness level suffered when the window is wasted (III). */
    public static final int TOOL_A_PENALTY_LEVEL = 3;
    /** How long that slowness lasts, in ticks. */
    public static final int TOOL_A_PENALTY_TICKS = 40;

    // --- Diagnostic Tool B tuning ---
    /** How long a full rewind back to the point takes, in ticks. */
    public static final int TOOL_B_REWIND_TICKS = 60;
    /**
     * How many positions of the walked path are kept. The longest window Black Strap can buy is
     * 18 seconds, so this covers it with room to spare.
     */
    public static final int TOOL_B_TRAIL_LIMIT = 400;

    // --- Boots of Speed tuning ---
    /** Extra movement multiplier while crouched, on top of Low Profile's own. */
    public static final double BOOTS_OF_SPEED_BONUS = 0.75D;

    // --- Steel Toe Boot tuning ---
    /** Blocks crouched per point of health returned. */
    public static final double STEEL_TOE_BLOCKS_PER_HEAL = 5.0D;

    // --- Taped Flashlight tuning ---
    /** Added on top of Longshot's own per-block damage. */
    public static final double TAPED_FLASHLIGHT_BONUS_PER_BLOCK = 0.05D;
    /** Light level the arrow gives off in flight. */
    public static final int TAPED_FLASHLIGHT_LIGHT_LEVEL = 10;

    // --- Point Blank tuning ---
    /** Damage multiplier on a headshot. */
    public static final float POINT_BLANK_MULTIPLIER = 1.75F;
    /**
     * Fraction of the target's height, measured from its feet, above which a hit counts as a
     * headshot.
     */
    public static final double POINT_BLANK_HEAD_FRACTION = 0.8D;

    // --- Needle And Thread tuning ---
    /** Multiplier applied to every skill check success zone. */
    public static final float NEEDLE_ZONE_MULTIPLIER = 1.5F;
    /** What is left of the Surgical Suture self-heal. */
    public static final float NEEDLE_HEAL_MULTIPLIER = 0.5F;

    // --- Sterilizer tuning ---
    /** How long the player has to start the retry skill check, in ticks. */
    public static final int STERILIZER_RETRY_WINDOW_TICKS = 60;
    /** Cooldown multiplier when the retry lands. */
    public static final float STERILIZER_SUCCESS_COOLDOWN = 0.5F;
    /** Cooldown multiplier when the retry is missed or never taken. */
    public static final float STERILIZER_FAILURE_COOLDOWN = 1.3F;

    // --- Gel Dressing tuning ---
    /** Absorption HP granted per successful check (one heart). */
    public static final float GEL_ABSORPTION_PER_SUCCESS = 2.0F;
    /** Absorption HP ceiling (three hearts). */
    public static final float GEL_ABSORPTION_MAX = 6.0F;

    private ModAddons() {}

    /** Touching this class runs the static initialisers, filling {@link AddonRegistry}. */
    public static void bootstrap() {}
}
