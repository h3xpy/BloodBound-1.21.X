package net.h3xpy.bloodbound.perk;

/**
 * Every perk definition. Values are declared in the order they appear in the {@code .desc}
 * translation string, since the description formats them as {@code T1/T2/T3} placeholders.
 */
public final class ModPerks {

    /**
     * Negates all fall damage past {@link #PERFECT_LANDING_MIN_FALL} blocks and grants Speed III
     * on landing, then goes on cooldown.
     * <p>Scaling: [0] speed duration in seconds, [1] cooldown in seconds.
     */
    public static final Perk PERFECT_LANDING = PerkRegistry.register(Perk.builder("perfect_landing")
            .type(PerkType.PASSIVE)
            .scaling(2, 3, 4)
            .scaling(40, 30, 20)
            .cooldown(1)
            .build());

    /**
     * While at or below {@link #CLOSE_CALL_HEALTH_THRESHOLD} health, lets the player dash forward,
     * immune to damage for the duration of the dash.
     * <p>Scaling: [0] cooldown in seconds.
     */
    public static final Perk CLOSE_CALL = PerkRegistry.register(Perk.builder("close_call")
            .type(PerkType.ACTIVE)
            .scaling(12, 8, 6)
            .cooldown(0)
            .build());

    /**
     * Grants Speed while sneaking; the effect is stripped the instant the player stops.
     * <p>Scaling: [0] speed level (I-based, so level 3 means Speed III).
     */
    public static final Perk LOW_PROFILE = PerkRegistry.register(Perk.builder("low_profile")
            .type(PerkType.PASSIVE)
            .scaling(3, 4, 5)
            .build());

    /**
     * Press the slot key for a hard skill check: land it to heal yourself on the spot, miss it and
     * the perk goes on cooldown. Also unlocks the co-op healing ability, which keeps working even
     * while the perk itself is on cooldown.
     * <p>Scaling: [0] self-heal HP, [1] bonus HP per skill check landed while healing someone else.
     */
    public static final Perk SURGICAL_SUTURE = PerkRegistry.register(Perk.builder("surgical_suture")
            .type(PerkType.ACTIVE)
            .scaling(4, 5, 6)
            .scaling(1, 2, 3)
            .flatCooldown(30)
            .grantsHealing()
            .build());

    /**
     * Pours your own health into a nearby player, then leaves you unable to heal for a while.
     * <p>Scaling: [0] HP healed on them, [1] HP paid by you, [2] Broken duration in seconds.
     */
    public static final Perk KEEP_FIGHTING = PerkRegistry.register(Perk.builder("keep_fighting")
            .type(PerkType.ACTIVE)
            .scaling(9, 12, 15)
            .scaling(8, 7, 6)
            .scaling(30, 25, 20)
            .build());

    /**
     * Everyone nearby, the bearer included, shakes off cooldowns and debuffs faster.
     * <p>Scaling: [0] recovery bonus, as a percentage.
     */
    public static final Perk LIGHTBRINGER = PerkRegistry.register(Perk.builder("lightbringer")
            .type(PerkType.PASSIVE)
            .scaling(40, 48, 55)
            .build());

    /**
     * Unlocks the co-op healing ability and speeds it up.
     * <p>Scaling: [0] healing speed bonus, as a percentage.
     */
    public static final Perk CARETAKER = PerkRegistry.register(Perk.builder("caretaker")
            .type(PerkType.PASSIVE)
            .scaling(50, 55, 60)
            .grantsHealing()
            .build());

    /**
     * Arrows hit harder the further they have flown.
     * <p>Scaling: [0] extra damage per block travelled.
     */
    public static final Perk LONGSHOT = PerkRegistry.register(Perk.builder("longshot")
            .type(PerkType.PASSIVE)
            .scaling(0.05, 0.1, 0.15)
            .build());

    /**
     * Crouch still and hold the key to mend yourself, on the same skill-check loop as co-op
     * healing but slower. Letting go of the key ends it.
     * <p>Scaling: [0] seconds for a full heal.
     */
    public static final Perk PATCH_UP = PerkRegistry.register(Perk.builder("patch_up")
            .type(PerkType.ACTIVE)
            .scaling(60, 55, 50)
            .build());

    /**
     * Unlocks healing, and makes it far faster while the healer is untouched.
     * <p>Scaling: [0] speed bonus while at full health, as a percentage.
     */
    public static final Perk WE_CAN_DO_THIS = PerkRegistry.register(Perk.builder("we_can_do_this")
            .type(PerkType.PASSIVE)
            .scaling(100, 110, 120)
            .grantsHealing()
            .build());

    /**
     * At death's door, a window where nothing can hurt you — but nothing can heal you either.
     * <p>Scaling: [0] duration in seconds.
     */
    public static final Perk ADRENALINE = PerkRegistry.register(Perk.builder("adrenaline")
            .type(PerkType.PASSIVE)
            .scaling(5, 7, 10)
            .flatCooldown(60)
            .build());

    /**
     * Wounded, your blows land again and again.
     * <p>Scaling: [0] health threshold in hearts, [1] number of follow-up hits.
     */
    public static final Perk RELENTLESS = PerkRegistry.register(Perk.builder("relentless")
            .type(PerkType.PASSIVE)
            .scaling(6, 6.5, 7)
            .scaling(1, 2, 3)
            .flatCooldown(5)
            .build());

    /**
     * Press once to drop a return point where you stand, press again before it lapses to snap back
     * to it with exactly the health you had. Letting it lapse wastes it.
     * <p>Scaling: [0] how long the point stays up in seconds, [1] cooldown in seconds.
     */
    public static final Perk BROKEN_MOVEMENT_DEVICE = PerkRegistry.register(Perk.builder("broken_movement_device")
            .type(PerkType.ACTIVE)
            .scaling(10, 12.5, 15)
            .scaling(50, 40, 30)
            .cooldown(1)
            .build());

    /**
     * Ores pay out more than once, and hostile mobs give up an extra shard.
     * <p>Scaling: [0] chance of one more drop, as a percentage, rerolled after every success.
     */
    public static final Perk RAISE_THE_STAKES = PerkRegistry.register(Perk.builder("raise_the_stakes")
            .type(PerkType.PASSIVE)
            .scaling(25, 30, 35)
            .build());

    /**
     * Drags every other perk's cooldown most of the way to ready, then pays for it with a long one
     * of its own.
     * <p>Scaling: [0] share of each remaining cooldown skipped, as a percentage, [1] own cooldown.
     */
    public static final Perk ANTI_EXHAUSTION_SYRINGE = PerkRegistry.register(
            Perk.builder("anti_exhaustion_syringe")
                    .type(PerkType.ACTIVE)
                    .scaling(70, 80, 90)
                    .scaling(150, 130, 110)
                    .cooldown(1)
                    .build());

    /**
     * A blow you land jumps from the thing you hit to whatever is standing next to it, and on
     * again, weakening as it goes.
     * <p>Scaling: [0] how many times it jumps, [1] damage lost per jump as a percentage,
     * [2] cooldown in seconds.
     */
    public static final Perk ECHOING_WOUNDS = PerkRegistry.register(Perk.builder("echoing_wounds")
            .type(PerkType.PASSIVE)
            .scaling(3, 4, 5)
            .scaling(30, 20, 10)
            .scaling(9, 7, 5)
            .cooldown(2)
            .build());

    /**
     * Mends the tool in your hand over a run of skill checks that get tighter with every one you
     * land.
     * <p>Scaling: [0] durability restored per check as a percentage, [1] how much smaller each
     * check's window gets as a percentage, [2] cooldown in seconds.
     */
    public static final Perk TINKERER = PerkRegistry.register(Perk.builder("tinkerer")
            .type(PerkType.ACTIVE)
            .scaling(3, 6, 9)
            .scaling(12, 10, 8)
            .scaling(100, 90, 80)
            .cooldown(2)
            .build());

    /**
     * Fires a falling projectile and throws the player at wherever it lands. Charges are spent one
     * at a time with no wait between them; running dry is what starts the cooldown.
     * <p>Scaling: [0] charges, [1] horizontal force ceiling, [2] base vertical impulse,
     * [3] projectile speed, [4] projectile gravity, [5] cooldown in seconds.
     */
    public static final Perk LOW_COST_MOVEMENT_DEVICE = PerkRegistry.register(
            Perk.builder("low_cost_movement_device")
                    .type(PerkType.ACTIVE)
                    .scaling(2, 3, 4)
                    .scaling(1.65, 1.9, 2.15)
                    .scaling(0.42, 0.46, 0.5)
                    .scaling(1.05, 1.1, 1.15)
                    .scaling(0.0605, 0.0572, 0.055)
                    .scaling(12, 8, 6)
                    .cooldown(5)
                    .build());

    /**
     * Stand still long enough and everything worth seeing lights up through the walls, for you
     * alone. The lowest tiers are blind to the best of it.
     * <p>Scaling: [0] seconds of standing still, [1] radius in blocks.
     */
    public static final Perk OMNISCIENCE = PerkRegistry.register(Perk.builder("omniscience")
            .type(PerkType.PASSIVE)
            .scaling(3, 2.5, 2)
            .scaling(8, 12, 16)
            .build());

    /**
     * Fires a harpoon of a shot: whatever it catches is dragged back to the player and left barely
     * able to move or fight for a moment.
     * <p>Scaling: [0] cooldown in seconds.
     */
    public static final Perk NO_ONE_GETS_AWAY = PerkRegistry.register(Perk.builder("no_one_gets_away")
            .type(PerkType.ACTIVE)
            .scaling(45, 40, 35)
            .cooldown(0)
            .build());

    /**
     * Every blow lands softer than it should, until they start landing one after another.
     * <p>Scaling: [0] how long a streak survives without a hit, in seconds.
     */
    public static final Perk NASTY_BLADE = PerkRegistry.register(Perk.builder("nasty_blade")
            .type(PerkType.PASSIVE)
            .scaling(3, 4, 5)
            .build());

    /**
     * Anything you throw comes off the first wall it meets, and looks for something to hit on the
     * way out.
     * <p>Scaling: [0] seconds per charge coming back.
     */
    public static final Perk BANK_SHOT = PerkRegistry.register(Perk.builder("bank_shot")
            .type(PerkType.PASSIVE)
            .scaling(14, 12, 10)
            .build());

    /**
     * The blow that should have killed you does not. What follows is a run of skill checks with
     * your life on them.
     * <p>Scaling: [0] health returned as a percentage, [1] and [2] the shortest and longest wait
     * before the trial in seconds, [3] checks that have to be landed, [4] cooldown in seconds.
     */
    public static final Perk GUARDIAN_ANGEL = PerkRegistry.register(Perk.builder("guardian_angel")
            .type(PerkType.PASSIVE)
            .scaling(20, 40, 60)
            .scaling(6, 10, 14)
            .scaling(10, 14, 18)
            .scaling(9, 8, 7)
            .scaling(360, 300, 240)
            .cooldown(4)
            .build());

    /**
     * Throws a grenade that blinds everything that watches it go off.
     * <p>Scaling: [0] cooldown in seconds.
     */
    public static final Perk FLASHBANG = PerkRegistry.register(Perk.builder("flashbang")
            .type(PerkType.ACTIVE)
            .scaling(30, 25, 20)
            .cooldown(0)
            .build());

    /**
     * Unlocks healing, mends the bearer faster on their own, and hands that on to whoever they
     * patch up.
     * <p>Scaling: [0] how much faster natural healing runs, [1] HP of healing that passes the perk
     * on, [2] how long it lasts on them in seconds.
     */
    public static final Perk GREEN_HERBS = PerkRegistry.register(Perk.builder("green_herbs")
            .type(PerkType.PASSIVE)
            .scaling(1.5, 2, 2.5)
            .scaling(6, 4, 2)
            .scaling(60, 90, 120)
            .grantsHealing()
            .build());

    /**
     * At home in the dark: always able to see in it, and quick through it.
     * <p>Scaling: [0] cooldown in seconds.
     */
    public static final Perk FROM_THE_DARK = PerkRegistry.register(Perk.builder("from_the_dark")
            .type(PerkType.PASSIVE)
            .scaling(9, 7, 5)
            .cooldown(0)
            .build());

    /**
     * Fires a shot that eats a reserve of charges as it travels, and drops the player wherever it
     * ran out or came to a stop.
     * <p>Scaling: [0] the reserve, [1] the most one shot may spend, [2] shot speed.
     */
    public static final Perk ADVANCED_MOVEMENT_DEVICE = PerkRegistry.register(
            Perk.builder("advanced_movement_device")
                    .type(PerkType.ACTIVE)
                    .scaling(200, 400, 600)
                    .scaling(100, 200, 300)
                    .scaling(1.95, 2.1, 2.25)
                    .build());

    /**
     * Hold the key and fly, for as long as the charges last. They only come back once your feet are
     * on the ground again.
     * <p>Scaling: [0] the reserve.
     */
    public static final Perk BEWARE_THE_POWER_OF_AN_ANGEL = PerkRegistry.register(
            Perk.builder("beware_the_power_of_an_angel")
                    .type(PerkType.ACTIVE)
                    .scaling(12, 18, 24)
                    .build());

    /**
     * Reads the ground for trails, and runs the ones it finds down.
     * <p>Scaling: [0] seconds the Speed lingers after leaving a trail, [1] walking bonus as a
     * percentage.
     */
    public static final Perk CATCHING_UP = PerkRegistry.register(Perk.builder("catching_up")
            .type(PerkType.PASSIVE)
            .scaling(3, 4, 5)
            .scaling(10, 13, 16)
            .grantsMarkVision()
            .build());

    /**
     * Reads the ground for trails, and anything bleeding nearby that somebody else is fighting.
     * <p>Scaling: [0] radius in blocks.
     */
    public static final Perk HUNTERS_INSTINCT = PerkRegistry.register(Perk.builder("hunters_instinct")
            .type(PerkType.PASSIVE)
            .scaling(16, 20, 24)
            .grantsMarkVision()
            .build());

    // --- Advanced Movement Device tuning ---
    public static final int AMD_MAX_CHARGES = 0;
    public static final int AMD_SHOT_BUDGET = 1;
    public static final int AMD_SPEED = 2;

    /** Charges that have to be in the reserve before the key does anything. */
    public static final double AMD_MIN_CHARGES = 20.0D;
    /** Charges the reserve gets back each second, and what a block of flight costs. */
    public static final double AMD_REGEN_PER_SECOND = 7.0D;
    public static final double AMD_COST_PER_BLOCK = 2.5D;
    /** Radius of the shot, in blocks. Wide enough that it catches the wall rather than the gap. */
    public static final double AMD_RADIUS = 0.35D;
    /** Bounces before the shot gives up and takes the player with it. */
    public static final int AMD_BOUNCES = 1;
    /** A backstop on how long one shot may live, in ticks. The budget normally ends it first. */
    public static final int AMD_MAX_FLIGHT_TICKS = 200;

    // --- Beware The Power Of An Angel tuning ---
    public static final int ANGEL_MAX_CHARGES = 0;

    /** Charges a second of flight costs, and how long one charge takes to come back. */
    public static final double ANGEL_DRAIN_PER_SECOND = 3.0D;
    public static final double ANGEL_RECHARGE_SECONDS = 2.0D;
    /**
     * Flying speed the perk grants. A third off vanilla's own 0.05: creative flight covers ground
     * far too fast for something bought by the second.
     */
    public static final float ANGEL_FLY_SPEED = 0.0325F;

    // --- Catching Up tuning ---
    public static final int CATCHING_UP_LINGER = 0;
    public static final int CATCHING_UP_WALK_BONUS = 1;

    /** Speed level granted while following a trail (I). */
    public static final int CATCHING_UP_SPEED_LEVEL = 1;

    // --- Hunter's Instinct tuning ---
    public static final int HUNTERS_INSTINCT_RADIUS = 0;

    /** How long a reveal lasts, in ticks. */
    public static final int HUNTERS_INSTINCT_REVEAL_TICKS = 140;


    /**
     * Stand still with the key down to lay a trap. Whatever walks into it bleeds, and gives itself
     * away doing it.
     * <p>Scaling: [0] seconds of setting up, [1] opacity as a percentage, [2] traps you may have
     * out, [3] cooldown in seconds, [4] damage on trigger, [5] Bleeding charges on trigger.
     */
    public static final Perk BARBED_WIRE = PerkRegistry.register(Perk.builder("barbed_wire")
            .type(PerkType.ACTIVE)
            .scaling(3, 2.5, 2)
            .scaling(9, 6, 3)
            .scaling(1, 2, 3)
            .scaling(45, 40, 35)
            .scaling(4, 6, 8)
            .scaling(12, 10, 8)
            .cooldown(3)
            .build());

    /**
     * A player you have hit does not steady their hands again while you are anywhere near them.
     * <p>Scaling: [0] how much smaller their checks get as a percentage, [1] how much faster they
     * sweep as a percentage, [2] the radius it holds over in blocks.
     */
    public static final Perk PANIC_ATTACK = PerkRegistry.register(Perk.builder("panic_attack")
            .type(PerkType.PASSIVE)
            .scaling(30, 40, 50)
            .scaling(22, 33, 44)
            .scaling(20, 24, 28)
            .build());

    /**
     * Lobs a charge that comes off the walls and only goes off when it finds the floor. Anything in
     * the blast is left short of breath.
     * <p>Scaling: [0] blast radius in blocks, [1] Exhausted level, [2] its duration in seconds,
     * [3] cooldown in seconds.
     */
    public static final Perk OUT_OF_BREATH = PerkRegistry.register(Perk.builder("out_of_breath")
            .type(PerkType.ACTIVE)
            .scaling(1.5, 2, 2.5)
            .scaling(1, 3, 5)
            .scaling(20, 25, 30)
            .scaling(10, 8, 6)
            .cooldown(3)
            .build());

    /**
     * Unlocks healing, and turns it into something the whole group feels — for as long as they stay
     * with you.
     * <p>Scaling: [0] Haste level, [1] how long the boons last in seconds, [2] the radius they hold
     * over in blocks.
     */
    public static final Perk TEAM_SPIRIT = PerkRegistry.register(Perk.builder("team_spirit")
            .type(PerkType.PASSIVE)
            .scaling(1, 2, 3)
            .scaling(60, 70, 80)
            .scaling(16, 20, 24)
            .grantsHealing()
            .build());

    /**
     * Almost nothing sticks to you, and whoever tried is not hidden from you either. The price is
     * paid in hearts.
     * <p>Scaling: [0] how much of every harmful effect is shrugged off, as a percentage.
     */
    public static final Perk BEYOND_VISION = PerkRegistry.register(Perk.builder("beyond_vision")
            .type(PerkType.PASSIVE)
            .scaling(80, 85, 90)
            .build());

    /**
     * Fires a slow rune that mends whatever it touches.
     * <p>Scaling: [0] health per rune, [1] runes held, [2] seconds per rune coming back.
     */
    public static final Perk HEALING_RUNES = PerkRegistry.register(Perk.builder("healing_runes")
            .type(PerkType.ACTIVE)
            .scaling(3, 4, 5)
            .scaling(5, 6, 7)
            .scaling(6, 5.5, 5)
            .build());

    // --- Barbed Wire tuning ---
    public static final int BARBED_SETUP = 0;
    public static final int BARBED_OPACITY = 1;
    public static final int BARBED_TRAPS = 2;
    public static final int BARBED_COOLDOWN = 3;
    public static final int BARBED_DAMAGE = 4;
    public static final int BARBED_BLEED = 5;

    /** How far the player may drift while setting a trap before it counts as moving. */
    public static final double BARBED_STILL_EPSILON = 0.02D;
    /** The trap's footprint, in blocks, and how long a triggered aura reveal lasts. */
    public static final double BARBED_RADIUS = 0.6D;
    public static final int BARBED_REVEAL_TICKS = 60;

    // --- Panic Attack tuning ---
    public static final int PANIC_SHRINK = 0;
    public static final int PANIC_SPEED = 1;
    public static final int PANIC_RADIUS = 2;

    // --- Out Of Breath tuning ---
    public static final int BREATH_RADIUS = 0;
    public static final int BREATH_LEVEL = 1;
    public static final int BREATH_DURATION = 2;
    public static final int BREATH_COOLDOWN = 3;

    /** How the charge flies: speed, the pull on it, its size, and how often it comes off a wall. */
    public static final double BREATH_SPEED = 1.1D;
    public static final double BREATH_GRAVITY = 0.05D;
    public static final double BREATH_PROJECTILE_RADIUS = 0.2D;
    public static final double BREATH_BOUNCE_SPEED = 0.55D;
    /** A backstop on how long it may stay in the air, in ticks. */
    public static final int BREATH_MAX_FLIGHT_TICKS = 200;

    // --- Team Spirit tuning ---
    public static final int SPIRIT_HASTE = 0;
    public static final int SPIRIT_DURATION = 1;
    public static final int SPIRIT_RADIUS = 2;

    /** Health that has to go in before the boons are earned, and the Strength level they carry. */
    public static final float SPIRIT_MIN_HEAL = 1.0F;
    public static final int SPIRIT_STRENGTH_LEVEL = 1;
    /** How long someone who has left the radius has to get back inside it, in ticks. */
    public static final int SPIRIT_GRACE_TICKS = 60;

    // --- Beyond Vision tuning ---
    public static final int VISION_REDUCTION = 0;

    /** Health the player is held to, in half-hearts: eight hearts of the usual ten. */
    public static final double VISION_MAX_HEALTH = 16.0D;

    // --- Healing Runes tuning ---
    public static final int RUNES_HEAL = 0;
    public static final int RUNES_CHARGES = 1;
    public static final int RUNES_RECHARGE = 2;

    /** How a rune flies: slow, straight, and wide enough to be worth aiming.  */
    public static final double RUNES_SPEED = 0.55D;
    public static final double RUNES_RADIUS = 0.5D;
    public static final int RUNES_MAX_FLIGHT_TICKS = 120;


    /**
     * Tripwires that tell you who is coming, and give you a way to meet them.
     * <p>Scaling: [0] seconds of setting up, [1] opacity as a percentage, [2] wires out at once,
     * [3] cooldown in seconds, [4] seconds before the window to follow opens.
     */
    public static final Perk TARGET_FOUND = PerkRegistry.register(Perk.builder("target_found")
            .type(PerkType.ACTIVE)
            .scaling(3, 2.5, 2)
            .scaling(9, 6, 3)
            .scaling(5, 6, 7)
            .scaling(15, 12, 9)
            .scaling(5, 4, 3)
            .cooldown(3)
            .build());

    /**
     * Nobody sees your name, and almost nobody hears you coming.
     * <p>Scaling: [0] seconds a blocked reveal keeps every other one off, [1] cooldown in seconds.
     */
    public static final Perk UNDER_THE_RADAR = PerkRegistry.register(Perk.builder("under_the_radar")
            .type(PerkType.PASSIVE)
            .scaling(4, 8, 12)
            .scaling(40, 30, 20)
            .cooldown(1)
            .build());

    /**
     * The wounded leave trails you can read, and walking them keeps the wound open.
     * <p>Scaling: [0] health share under which a trail shows, as a percentage, [1] seconds Broken
     * lingers after leaving it.
     */
    public static final Perk FINAL_BLOW = PerkRegistry.register(Perk.builder("final_blow")
            .type(PerkType.PASSIVE)
            .scaling(30, 40, 50)
            .scaling(6, 8, 10)
            .build());

    /**
     * Ores come out smelted, with their rock, and now and then a soul shard.
     * <p>Scaling: [0] chance of a shard on each roll, as a percentage.
     */
    public static final Perk FULL_EXTRACTION = PerkRegistry.register(Perk.builder("full_extraction")
            .type(PerkType.PASSIVE)
            .scaling(20, 25, 30)
            .build());

    /**
     * A grenade thrown as hard as it was wound up, that goes off twice.
     * <p>Scaling: [0] first blast radius, [1] second blast radius, [2] damage per blast, [3] cooldown
     * in seconds.
     */
    public static final Perk FRAGNADE = PerkRegistry.register(Perk.builder("fragnade")
            .type(PerkType.ACTIVE)
            .scaling(1.8, 2, 2.2)
            .scaling(3.75, 4.25, 4.75)
            .scaling(5, 6, 7)
            .scaling(20, 17, 14)
            .cooldown(3)
            .build());

    // --- Target Found tuning ---
    public static final int TARGET_SETUP = 0;
    public static final int TARGET_OPACITY = 1;
    public static final int TARGET_TRAPS = 2;
    public static final int TARGET_COOLDOWN = 3;
    public static final int TARGET_DELAY = 4;

    /** How close the owner has to be for a trip to be worth following, and the window to follow in. */
    public static final double TARGET_RANGE = 128.0D;
    public static final int TARGET_WINDOW_TICKS = 80;
    /** The wire's footprint, in blocks either side. */
    public static final double TARGET_TRAP_RADIUS = 0.6D;

    // --- Under The Radar tuning ---
    public static final int RADAR_WINDOW = 0;
    public static final int RADAR_COOLDOWN = 1;

    // --- Final Blow tuning ---
    public static final int FINAL_BLOW_THRESHOLD = 0;
    public static final int FINAL_BLOW_LINGER = 1;

    // --- Full Extraction tuning ---
    public static final int EXTRACTION_SHARD_CHANCE = 0;
    /** A backstop on the reroll, so a chance pushed to a hundred cannot loop for ever. */
    public static final int EXTRACTION_MAX_SHARDS = 16;

    // --- Frag' Nade tuning ---
    public static final int FRAG_FIRST_RADIUS = 0;
    public static final int FRAG_SECOND_RADIUS = 1;
    public static final int FRAG_DAMAGE = 2;
    public static final int FRAG_COOLDOWN = 3;

    /** How long the throw can be wound, and the speed it leaves at from a tap to a full wind. */
    public static final int FRAG_HOLD_MAX_TICKS = 60;
    public static final double FRAG_MIN_SPEED = 0.45D;
    public static final double FRAG_MAX_SPEED = 1.6D;
    /** The pull on it, its size, and the speed kept off a wall. */
    public static final double FRAG_GRAVITY = 0.05D;
    public static final double FRAG_RADIUS = 0.2D;
    public static final double FRAG_BOUNCE_SPEED = 0.6D;
    /** Beeping after landing: short if it came off a wall first, long if it did not. */
    public static final int FRAG_BEEP_BOUNCED_TICKS = 15;
    public static final int FRAG_BEEP_DIRECT_TICKS = 30;
    /** Ticks between the two blasts, and the throw the second one gives. */
    public static final int FRAG_INTERVAL_TICKS = 20;
    public static final double FRAG_KNOCKBACK = 1.4D;
    public static final double FRAG_KNOCKBACK_LIFT = 0.45D;
    /** A backstop on how long it may stay in the air. */
    public static final int FRAG_MAX_FLIGHT_TICKS = 300;

    /** The speed a throw leaves at, for a wind-up from nothing to full. Shared with the preview. */
    public static double fragSpeed(double share) {
        return FRAG_MIN_SPEED + (FRAG_MAX_SPEED - FRAG_MIN_SPEED) * Math.clamp(share, 0.0D, 1.0D);
    }

    // --- Perfect Landing tuning ---
    /** Fall distance, in blocks, above which the perk kicks in. */
    public static final float PERFECT_LANDING_MIN_FALL = 3.0F;
    /** Speed level granted on landing (III). */
    public static final int PERFECT_LANDING_SPEED_LEVEL = 3;

    // --- Close Call tuning ---
    /** Health at or below which the dash may be used, in half-hearts (5 hearts). */
    public static final float CLOSE_CALL_HEALTH_THRESHOLD = 10.0F;
    /** Roughly how many blocks the dash covers. */
    public static final double CLOSE_CALL_DASH_DISTANCE = 4.0D;
    /** How long the dash lasts. The distance is spread evenly over these ticks. */
    public static final int CLOSE_CALL_DASH_TICKS = 6;
    /** How long the player stays immune to damage, counted from the start of the dash. */
    public static final int CLOSE_CALL_INVULNERABILITY_TICKS = 10;

    /** Horizontal speed, in blocks per tick, that covers the dash distance in the dash window. */
    public static double closeCallDashSpeed() {
        return CLOSE_CALL_DASH_DISTANCE / CLOSE_CALL_DASH_TICKS;
    }

    // --- Surgical Suture tuning ---
    /** Index of the self-heal amount in the scaling list. */
    public static final int SURGICAL_SELF_HEAL = 0;
    /** Index of the bonus healing granted per skill check landed on someone else. */
    public static final int SURGICAL_ASSIST_BONUS = 1;

    // --- Keep Fighting tuning ---
    public static final int KEEP_FIGHTING_TARGET_HEAL = 0;
    public static final int KEEP_FIGHTING_SELF_COST = 1;
    public static final int KEEP_FIGHTING_BROKEN_SECONDS = 2;
    /** How close the other player has to be. */
    public static final double KEEP_FIGHTING_RANGE = 4.0D;

    // --- Lightbringer tuning ---
    /** Index of the recovery bonus percentage. */
    public static final int LIGHTBRINGER_BONUS = 0;
    /** Radius of the aura, in blocks. */
    public static final double LIGHTBRINGER_RADIUS = 16.0D;

    // --- Caretaker tuning ---
    /** Index of the healing speed bonus percentage. */
    public static final int CARETAKER_BONUS = 0;

    // --- Longshot tuning ---
    /** Index of the extra damage per block travelled. */
    public static final int LONGSHOT_PER_BLOCK = 0;

    // --- Low Profile extras ---
    /** Night vision level granted alongside the speed, unless Boots of Speed replaces it. */
    public static final int LOW_PROFILE_NIGHT_VISION_LEVEL = 1;

    // --- Patch Up tuning ---
    /** Index of the seconds a full self-heal takes. */
    public static final int PATCH_UP_FULL_HEAL_SECONDS = 0;

    // --- We Can Do This tuning ---
    /** Index of the healing speed bonus that applies while the healer is at full health. */
    public static final int WE_CAN_DO_THIS_BONUS = 0;

    // --- Adrenaline tuning ---
    /** Index of the immunity duration in seconds. */
    public static final int ADRENALINE_DURATION = 0;
    /** Health at or below which Adrenaline fires, in half-hearts. */
    public static final float ADRENALINE_HEALTH_THRESHOLD = 3.0F;

    // --- Relentless tuning ---
    /** Index of the health threshold, in hearts. */
    public static final int RELENTLESS_HEARTS = 0;
    /** Index of how many follow-up hits the chain lands. */
    public static final int RELENTLESS_HITS = 1;
    /** Share of the opening blow each follow-up deals, in order. */
    public static final float[] RELENTLESS_CHAIN_FRACTIONS = { 0.30F, 0.15F, 0.075F };
    /** Delay between follow-up hits, in seconds. */
    public static final double RELENTLESS_INTERVAL_SECONDS = 0.52D;

    // --- Broken Movement Device tuning ---
    /** Index of how long the return point stays up, in seconds. */
    public static final int BROKEN_DEVICE_WINDOW = 0;
    /** Index of the cooldown, in seconds. */
    public static final int BROKEN_DEVICE_COOLDOWN = 1;
    /** How many ticks pass between two puffs of the trail left while a point is up. */
    public static final int BROKEN_DEVICE_TRAIL_INTERVAL = 2;

    // --- Raise The Stakes tuning ---
    /** Index of the chance, as a percentage, that an ore drops one more time. */
    public static final int RAISE_THE_STAKES_CHANCE = 0;
    /** Extra soul shards a hostile mob gives up. */
    public static final int RAISE_THE_STAKES_BONUS_SHARDS = 1;
    /**
     * Ceiling on the reroll chain. At the highest tier a run this long is a one in a hundred
     * million, so it never bites in play — it only stops a bad config from freezing the server.
     */
    public static final int RAISE_THE_STAKES_MAX_EXTRA_DROPS = 16;

    // --- Echoing Wounds tuning ---
    /** Index of how many times the echo jumps. */
    public static final int ECHOING_WOUNDS_BOUNCES = 0;
    /** Index of the damage lost per jump, as a percentage. */
    public static final int ECHOING_WOUNDS_FALLOFF = 1;
    /** Index of the cooldown, in seconds. */
    public static final int ECHOING_WOUNDS_COOLDOWN = 2;
    /** How far the echo looks for its next victim, in blocks. */
    public static final double ECHOING_WOUNDS_RADIUS = 5.0D;
    /** Ticks between two jumps, so the chain reads as a chain rather than one lump of damage. */
    public static final int ECHOING_WOUNDS_INTERVAL_TICKS = 3;

    // --- Tinkerer tuning ---
    /** Index of the durability restored per landed check, as a percentage. */
    public static final int TINKERER_REPAIR = 0;
    /** Index of how much smaller each check's window gets, as a percentage. */
    public static final int TINKERER_SHRINK = 1;
    /** Index of the cooldown, in seconds. */
    public static final int TINKERER_COOLDOWN = 2;
    /** Share of an item's durability lost on a missed check. */
    public static final float TINKERER_MISS_PENALTY = 0.12F;
    /** How much of the easy check's window Tinkerer opens with, per tier. */
    public static final float[] TINKERER_ZONE_SCALE = {0.75F, 0.80F, 0.85F};

    // --- Low-Cost Movement Device tuning ---
    public static final int DEVICE_CHARGES = 0;
    public static final int DEVICE_HORIZONTAL_MAX = 1;
    public static final int DEVICE_VERTICAL_BASE = 2;
    public static final int DEVICE_PROJECTILE_SPEED = 3;
    public static final int DEVICE_PROJECTILE_GRAVITY = 4;
    public static final int DEVICE_COOLDOWN = 5;

    /** Radius of the projectile, in blocks. */
    public static final double DEVICE_PROJECTILE_RADIUS = 0.25D;
    /**
     * How much of the tier's gravity actually pulls the shot down each tick. The shot travels at
     * about a block a tick where an arrow does three, so it spends three times as long in the air
     * and the raw figure would bend it into a rainbow long before it got anywhere.
     */
    public static final double DEVICE_GRAVITY_SCALE = 0.5D;
    /**
     * The shot is thrown a little above the line of sight and allowed to fall back onto it, which
     * gives it a visible arc without moving where it lands. The loft is worked out so the two
     * cancel at {@link #DEVICE_LOFT_REFERENCE} blocks, using the half of the gravity that was
     * added to bend the flight in the first place.
     */
    public static final double DEVICE_LOFT_SHARE = 0.5D;
    public static final double DEVICE_LOFT_REFERENCE = 25.0D;
    /**
     * Ticks a shot may stay in the air. There is no range limit any more, so this is only here to
     * stop one fired at the sky from living for ever.
     */
    public static final int DEVICE_MAX_FLIGHT_TICKS = 400;
    /**
     * Lift given to a throw that starts on the ground, whatever the shot's own angle says. Ground
     * friction eats a horizontal launch almost at once, so without this the player scrapes along
     * the floor instead of being thrown.
     */
    public static final double DEVICE_GROUND_CLEARANCE = 0.36D;
    /**
     * How much quicker the shot leaves the barrel than the tier's own figure. Less time in the air
     * also means less time falling, so the flight reads flatter as well as faster.
     */
    public static final double DEVICE_SPEED_SCALE = 1.35D;
    /**
     * Share of the tier's lift a dead level shot still gets. The rest is earned by aiming upwards,
     * so shooting a wall in front of you throws you at it rather than over it.
     */
    public static final double DEVICE_LEVEL_LIFT = 0.35D;
    /** An impact this far above the player's eyes counts as overhead and earns the extra lift. */
    public static final double DEVICE_OVERHEAD_MARGIN = 1.0D;
    /** Extra upward impulse per block of overhead impact, and the most it can add. */
    public static final double DEVICE_OVERHEAD_PER_BLOCK = 0.12D;
    public static final double DEVICE_OVERHEAD_MAX = 0.6D;

    // --- Guardian Angel tuning ---
    public static final int GUARDIAN_ANGEL_HEAL_PERCENT = 0;
    public static final int GUARDIAN_ANGEL_DELAY_MIN = 1;
    public static final int GUARDIAN_ANGEL_DELAY_MAX = 2;
    public static final int GUARDIAN_ANGEL_CHECKS = 3;
    public static final int GUARDIAN_ANGEL_COOLDOWN = 4;
    /** Name of the scoreboard team the gold glow comes from. */
    public static final String GUARDIAN_ANGEL_TEAM = "bloodbound_guardian_angel";
    /** How much of a normal check the trial leaves: tight enough to hurt, wide enough to land. */
    public static final float GUARDIAN_ANGEL_ZONE_SCALE = 0.68F;

    // --- Flashbang tuning ---
    public static final int FLASHBANG_COOLDOWN = 0;
    /** How hard it is thrown, and how fast it falls. */
    public static final double FLASHBANG_SPEED = 0.9D;
    public static final double FLASHBANG_GRAVITY = 0.045D;
    /** Radius of the grenade, and how many times it comes off a wall. */
    public static final double FLASHBANG_RADIUS = 0.2D;
    public static final int FLASHBANG_BOUNCES = 1;
    /** Speed kept off a bounce, and how long it lives before going off. */
    public static final double FLASHBANG_BOUNCE_SPEED = 0.45D;
    public static final int FLASHBANG_FUSE_TICKS = 40;
    /**
     * The blind lasts {@code 4 - distance / 10} seconds, so anything past this is out of it. What
     * really decides it is whether the blast was being looked at: a solid block in the way, or a
     * back turned, is all the cover anyone needs.
     */
    public static final double FLASHBANG_BASE_SECONDS = 4.0D;
    public static final double FLASHBANG_FALLOFF_PER_BLOCK = 1.0D / 10.0D;
    /** Half-angle of what counts as looking at the blast, in degrees. */
    public static final double FLASHBANG_VIEW_DEGREES = 60.0D;
    public static final double FLASHBANG_RANGE = FLASHBANG_BASE_SECONDS / FLASHBANG_FALLOFF_PER_BLOCK;

    // --- Green Herbs tuning ---
    public static final int GREEN_HERBS_REGEN = 0;
    public static final int GREEN_HERBS_SHARE_HEAL = 1;
    public static final int GREEN_HERBS_SHARE_SECONDS = 2;
    /**
     * Ticks vanilla takes to give back one point of health on a full stomach. The perk's multiplier
     * is measured against this.
     */
    public static final int GREEN_HERBS_NATURAL_REGEN_TICKS = 80;
    /** Food the player needs before natural healing runs at all, vanilla's own threshold. */
    public static final int GREEN_HERBS_MIN_FOOD = 18;

    // --- From The Dark tuning ---
    public static final int FROM_THE_DARK_COOLDOWN = 0;
    /** Light at or below which the dark counts as dark. */
    public static final int FROM_THE_DARK_MAX_LIGHT = 3;
    /** What sprinting in it is worth, and for how long. */
    public static final int FROM_THE_DARK_SPEED_LEVEL = 3;
    public static final int FROM_THE_DARK_SPEED_TICKS = 40;
    /** How much longer being Flashed lasts on someone who lives in the dark. */
    public static final int FROM_THE_DARK_FLASH_MULTIPLIER = 2;

    // --- Bank Shot tuning ---
    /** Index of how long one charge takes to come back, in seconds. */
    public static final int BANK_SHOT_RECHARGE = 0;
    /** How many shots the perk holds, and how many times each one bounces. */
    public static final int BANK_SHOT_CHARGES = 3;
    public static final int BANK_SHOT_BOUNCES = 1;
    /** Speed a shot keeps coming off the wall. */
    public static final double BANK_SHOT_BOUNCE_SPEED = 0.9D;
    /**
     * Half-angle of the cone the bounced shot looks down, in degrees: a target counts if the angle
     * between the shot's new heading and the line to it is no wider than this.
     */
    public static final double BANK_SHOT_CONE_DEGREES = 45.0D;
    /** How far down that cone it looks. */
    public static final double BANK_SHOT_SEEK_RANGE = 6.0D;

    // --- No One Gets Away tuning ---
    /** Index of the cooldown, in seconds. */
    public static final int NO_ONE_GETS_AWAY_COOLDOWN = 0;
    /** How fast the shot travels, in blocks per tick, and how far it carries. */
    public static final double HARPOON_SPEED = 2.4D;
    public static final double HARPOON_RANGE = 40.0D;
    /** Radius of the shot, in blocks. */
    public static final double HARPOON_RADIUS = 0.4D;
    /** How far in front of the player the catch is dropped. */
    public static final double HARPOON_DROP_DISTANCE = 1.5D;
    /** How long the catch is left unable to move or fight. */
    public static final int HARPOON_HOLD_TICKS = 20;
    /** Slowness and Weakness at their highest, which is what "255" amounts to in game terms. */
    public static final int HARPOON_EFFECT_AMPLIFIER = 254;

    // --- Nasty Blade tuning ---
    /** Index of how long a streak lasts without a fresh hit. */
    public static final int NASTY_BLADE_TIMER = 0;
    /** Damage taken off every swing, before any tokens are counted. */
    public static final float NASTY_BLADE_PENALTY = 2.0F;
    /** Damage each token adds back. */
    public static final float NASTY_BLADE_PER_TOKEN = 0.5F;
    /** Ticks the blade needs between two tokens, so a flurry of hits cannot bank the lot. */
    public static final int NASTY_BLADE_TOKEN_COOLDOWN = 10;

    // --- Omniscience tuning ---
    /** Index of how long the player has to stand still. */
    public static final int OMNISCIENCE_STILL_SECONDS = 0;
    /** Index of the reveal radius, in blocks. */
    public static final int OMNISCIENCE_RADIUS = 1;
    /** Movement per tick tolerated before the reveal drops. */
    public static final double OMNISCIENCE_MOVE_EPSILON = 0.01D;
    /** How often the reveal is rebuilt while the player holds still. */
    public static final int OMNISCIENCE_REFRESH_TICKS = 40;
    /** Most blocks one reveal will report, so a big radius cannot flood the connection. */
    public static final int OMNISCIENCE_MAX_BLOCKS = 400;
    /** Tier at which diamond stops being hidden, and the tier at which ancient debris does. */
    public static final int OMNISCIENCE_DIAMOND_TIER = 2;
    public static final int OMNISCIENCE_DEBRIS_TIER = 3;

    // --- Anti-Exhaustion Syringe tuning ---
    /** Index of the share of each remaining cooldown that is skipped. */
    public static final int SYRINGE_REDUCTION = 0;
    /** Index of the syringe's own cooldown, in seconds. */
    public static final int SYRINGE_COOLDOWN = 1;

    private ModPerks() {}

    /** Touching this class runs the static initialisers, filling {@link PerkRegistry}. */
    public static void bootstrap() {}
}
