# BloodBound

A Dead by Daylight style perk system for Minecraft 1.21.1 (NeoForge 21.1.249).

Kill mobs for **soul shards**, spend them on a **soulweb**, and keep the perks you learn for good.

## How it plays

**Soul shards.** Every mob kill rolls a shard drop: 10% nothing, 40% one, 25% two, 15% three, 7% four,
3% five. Shards are a normal item, so they sit in your inventory and the perk table spends them
straight from there. The weights, whether a player has to land the kill, and whether only hostile
mobs count are all in the config.

**The perk table.** Crafted from a crafting table and a single soul shard, in any arrangement.
Right-click it to open. It has three tabs, and the window grows to fill whatever room your screen
gives it, so the soulweb has space to breathe:

- **Loadout** — every perk you have learned, listed alphabetically with a search box above them,
  and the four slots you can equip them into. Click a learned perk to slot it, click it again (or
  click the slot) to take it off. Swap freely, any time.
- **Addons** — one addon slot per equipped perk. The list keeps every addon of the same perk
  together.
- **Soulweb** — branches of nodes radiating from the centre, holding perks and loot. Buying
  anything commits you to that branch and locks every other one. Once you have bought out the branch
  you committed to, nothing is left to buy and the web rebuilds itself with fresh contents.

**Perks are permanent.** Learning a perk is forever, death included. Each perk has three tiers, and
a higher tier is bought as its own soulweb node. In every description, values that change per tier
are written `tier1/tier2/tier3`, with the tier you own highlighted.

## The perks

| Perk | Type | What it does |
| --- | --- | --- |
| **Perfect Landing** | Passive | Fall damage past 3 blocks is negated entirely. On landing, Speed III for **2/3/4** seconds, then a **40/30/20** second cooldown. |
| **Close Call** | Active | At 5 hearts or less, dash ~4 blocks forward, immune to damage for the dash. Cooldown **12/8/6** seconds. |
| **Low Profile** | Passive | While sneaking, move as if under Speed **3/4/5** and gain night vision. Both end the instant you stand up. |
| **Surgical Suture** | Active | A hard skill check: land it to heal **4/5/6** HP at no cooldown cost, miss it and go on cooldown for 30 seconds. Unlocks healing, and makes each skill check you land while healing someone heal them **1/2/3** HP extra. |
| **Keep Fighting** | Active | Heals a nearby player **9/12/15** HP for **8/7/6** of your own, then leaves you Broken for **30/25/20** seconds. No cooldown — Broken is the price. |
| **Lightbringer** | Passive | Every player within 16 blocks, you included, recovers from cooldowns, charge timers and debuffs **40/48/55%** faster. |
| **Caretaker** | Passive | Unlocks healing and makes it **50/55/60%** faster. |
| **Longshot** | Passive | Arrows gain **+0.05/0.1/0.15** damage per block travelled before impact. |
| **Patch Up** | Active | Crouch still and press the key to mend yourself on the healing loop, skill checks included; press again to stop. A full heal takes **60/55/50** seconds. Moving or standing up ends it. |
| **We Can Do This** | Passive | Unlocks healing. While you are at full health, you heal others **100/110/120%** faster. |
| **Adrenaline** | Passive | At 3 HP or less: Broken and immune to all damage for **5/7/10** seconds, then a 60 second cooldown. A blow straight from healthy to zero kills you first. |
| **Relentless** | Passive | At **6/6.5/7** hearts or less, attacks chain **1/2/3** follow-up blows every 0.52s for 30%, 15% and 7.5% of the opening hit. Cooldown 5 seconds. |
| **Broken Movement Device** | Active | Press to pin your position and current health; press again within **10/12.5/15** seconds to snap back to both. Let the window lapse and the point is wasted. Cooldown **50/40/30** seconds, counted from when the point is spent or lost. While a point is up you trail end rod particles. |
| **Raise The Stakes** | Passive | Every ore has a **25/30/35%** chance to drop again, rerolled after each success, so the chain is open ended. Hostile mobs also drop one extra soul shard. Silk Touch turns it off. |
| **Anti-Exhaustion Syringe** | Active | Skips **70/80/90%** of what is left on every other perk's cooldown, then goes on cooldown itself for **150/130/110** seconds. |
| **Echoing Wounds** | Passive | Damage you deal jumps to the nearest living thing within 5 blocks, up to **3/4/5** times, losing **30/20/10%** per jump. Never you, never the same target twice. Cooldown **9/7/5** seconds, paid only if the echo found somebody. |
| **Tinkerer** | Active | Hold a damaged item and press the key: land the skill check to restore **3/6/9%** of its durability and get another **12/10/8%** tighter. A miss costs 12% durability and ends the run. Cooldown **100/90/80** seconds. |
| **Bank Shot** | Passive | 3 charges, one back every **14/12/10** seconds. Every projectile you fire spends one to bounce off the first block it hits, with a slight auto-aim on the way out. |
| **No One Gets Away** | Active | Fire a fast, flat shot; whatever it catches is dragged back to your feet with Slowness and Weakness at their highest for a second. Cooldown **45/40/35** seconds. |
| **Nasty Blade** | Passive | Attacks deal 2 less damage. Every hit banks a token — at most one every half second — and restarts a **3/4/5** second clock; each token puts 0.5 damage back. Let the clock run out and the streak is gone. |
| **Omniscience** | Passive | Stand still for **3/2.5/2** seconds and every entity, chest and ore within **8/12/16** blocks is outlined through the walls, for you alone. Tier I is blind to diamond and ancient debris, tier II to ancient debris. Moving puts it away at once. |
| **Low-Cost Movement Device** | Active | Spend a charge on a falling shot; where it hits a block, it throws you there. **2/3/4** charges with no wait between them, pull capped at **1.65/1.9/2.15** across and **0.42/0.46/0.5** up (a level shot keeps a third of the lift, aiming up earns the rest), shot speed **1.05/1.1/1.15**, gravity **0.0605/0.0572/0.055**, no range limit. Only the last charge starts the **12/8/6** second cooldown. |
| **Guardian Angel** | Passive | A blow that would kill you instead leaves you at **20/40/60%** health, glowing gold for everyone to see. Somewhere between **6-10/10-14/14-18** seconds later a run of skill checks begins: land **9/8/7** of them and you walk away, cooldown **360/300/240** seconds. Miss one and the blow catches up with you. |
| **Flashbang** | Active | Throw a grenade that comes off one wall and goes off 2 seconds later, beeping faster as it goes. Anything looking at the blast is Flashed for 4 seconds, a second less for every 10 blocks away — a wall or a turned back is cover. Cooldown **30/25/20** seconds. |
| **Advanced Movement Device** | Active | Fire a shot and be teleported to wherever it stops: it comes off the first wall and stops at the second. Shots draw on a self-refilling reserve of **200/400/600** charges, at most **100/200/300** per shot. Speed **1.95/2.1/2.25**. |
| **Beware The Power Of An Angel** | Active | Press the key to spread your wings and fly, press again to fold them. 3 charges a second until you land; **12/18/24** charges, one back every 2 seconds, and only on the ground. |
| **Catching Up** | Passive | See the marks on the ground. Stepping on marks that are not yours grants Speed I, lingering **3/4/5** seconds after the trail ends. You also walk **10/13/16%** faster while neither sprinting nor crouched. |
| **Hunter's Instinct** | Passive | See the marks on the ground. Anything within **16/20/24** blocks that loses health to something other than you has its aura revealed to you for 7 seconds. |
| **Barbed Wire** | Active | Hold the key **3/2.5/2** seconds without moving to lay a coil at **15/12/9%** opacity. Up to **1/2/3** out at once; a fourth takes the oldest. The first thing that walks in takes **4/6/8** damage, starts Bleeding on **12/10/8** charges and has its aura shown to you for 3 seconds — and the coil is spent. Unequipping the perk removes every coil. An arrow or a thrown item takes a coil apart. Cooldown **45/40/35** seconds, paid only on a coil laid. |
| **Panic Attack** | Passive | A player you hit has every skill check **30/40/50%** smaller and **22/33/44%** faster. No timer — it holds while you stay within **20/24/28** blocks, and ends for good the moment they get clear. |
| **Out Of Breath** | Active | Lob a charge that walls only turn; it goes off on the floor, in a **1.5/2/2.5** block blast, leaving everything caught Exhausted **1/3/5** for **20/25/30** seconds. Cooldown **10/8/6** seconds. |
| **Team Spirit** | Passive | Unlocks healing. One point of health earns the patient Haste **1/2/3** and Strength I for **60/70/80** seconds — but only within **16/20/24** blocks of you. Leave it and there are 3 seconds to come back before the boons are gone for good. |
| **Beyond Vision** | Passive | Harmful effects on you are **80/85/90%** shorter, and whatever cast one has its aura revealed to you for as long as the effect was meant to last. You have 8 hearts instead of 10. |
| **Healing Runes** | Active | Fire a slow rune that ignores gravity; the first living thing it touches is healed **3/4/5** HP. **5/6/7** runes, one back every **6/5.5/5** seconds. |
| **Target Found** | Active | Hold the key **3/2.5/2** seconds without moving to lay a tripwire at **15/12/9%** opacity; up to **5/6/7** out, a new one takes the oldest. When something crosses one within 128 blocks of you, you are told and shown its aura, and after **5/4/3** seconds a 4 second window lets the key take you to the wire. The wire goes when the window does. Further away you only get the warning. Cooldown **15/12/9** seconds. |
| **Under The Radar** | Passive | Nametag hidden and every sound you make silenced, subtitles included. The first aura reveal against you is blocked, along with any other for **4/8/12** seconds; then a **40/30/20** second cooldown. |
| **Final Blow** | Passive | See the marks of anything under **30/40/50%** health. Walking them leaves the owner Broken, lingering **6/8/10** seconds after you step off. Their marks vanish the moment they heal back over the line. |
| **Full Extraction** | Passive | Ores drop smelted, with their rock, and a **20/25/30%** chance of a soul shard, rerolled after every success. Off with Silk Touch. |
| **Frag' Nade** | Active | Hold to wind up a throw (up to 3 seconds, shown on the slot), release to throw. Comes off walls, stops on the floor, beeps 0.75 s if it bounced or 1.5 s if not, then blasts twice a second apart: **1.8/2/2.2** blocks, then **3.75/4.25/4.75** with heavy knockback. **5/6/7** armour-ignoring damage each, thrower included. Cooldown **20/17/14** seconds. |
| **Green Herbs** | Passive | Unlocks healing. Your own natural healing runs **1.5/2/2.5x** as fast. Put **6/4/2** HP into another player and they mend that way too for **60/90/120** seconds, unless they already carry the perk. |
| **From The Dark** | Passive | Permanent night vision. Sprinting in light level 3 or lower grants Speed III for 2 seconds, then a **9/7/5** second cooldown. The price: Flashed lasts twice as long on you. |


## Marks

Everything that moves leaves a trail. A sprinting player scuffs the ground around them; so does any
mob that is going somewhere. A pass covers **40% of the ground within 3 blocks**, fades in over 2
seconds, holds for 6, and fades out over 4. Players leave **orange** marks, mobs **red** ones.

**They cannot be seen without a perk that reads them** — Catching Up and Hunter's Instinct. Nobody
else is even told they exist: marks are held on the server and only sent to players who can read
them, so there is no way to see a trail by any other means, and nothing is laid at all while nobody
in the game can read one.

No blocks are touched and nothing is saved. Marks are drawn as flat scuffs on the block's top face,
jittered per block out of the block's own coordinates — so a trail looks uneven but never flickers,
and there is no texture to ship.

## The status effects

**Broken** — while Broken, **nothing can heal you**: not regeneration, food, potions, golden apples,
or any BloodBound perk. Every heal in the game funnels through one event, so the block is total
rather than a list of exceptions. It trails blue-to-red `dust_color_transition` particles.

**Exposed** — the victim is pinned at a single point of health and gets the health they had back
the moment it ends, so one hit finishes them while it lasts. Healing is blocked for the duration,
and anything with more than 100 maximum health cannot be Exposed at all.

**Aura Revealed** — the victim is outlined through walls, but **only on the screen of whoever
revealed them**. It is the vanilla glow, with the flag set on that one client rather than sent to
everybody, so no other player sees a thing.

**Flashed** — the victim's screen is solid white, fading back over the last second. A mob loses
whatever it was chasing and cannot pick a new target up until it ends. Everything Flashed trails
`electric_spark` particles, so it is obvious from the outside who is blind.


**Bleeding** — runs on a bar of charges rather than a clock. A player loses one per second of running,
sprint-jumping included; once the bar is empty, running costs **5 HP a second** instead. The way out is
written under the bar: hold the heal key for `20 − charges` seconds — **no healing perk is needed**, letting
go only pauses it, and the perks that make you a faster medic make this faster too. The screen reddens
at the edges as the bar empties. A mob has no bar to manage and simply bleeds out at 2 HP a second for
as many seconds as it had charges. Anything Bleeding trails blood behind it, visible to everyone.

**Exhausted** — a stamina bar of `5 − (level−1)/2` charges. Running, sprint-jumping included, spends one
a second; easing off returns one every two. Run it to nothing and the sprint is gone, with **Slowness III**
on top, until **3 charges** are back. Nobody Exhausted can be given Speed. For a mob, moving at all is
what spends it.

Equipped perks show in the bottom-left HUD with their cooldowns, each with its active addon beside
it at 70% of the perk icon size. Its size is yours to set, from half to two and a half times, under
Options → Mods → BloodBound → Config.

A perk with a window running rather than a cooldown — a Broken Movement Device return point, a Nasty
Blade streak — counts that window down in cyan over its slot instead, so the number on screen is
always the one that matters right now. Low-Cost Movement Device charges show as pips along the
bottom of the slot; Nasty Blade tokens as a gold count in its corner.

## Death messages

Every perk that can kill says so in chat: Barbed Wire, Echoing Wounds, Relentless, Frag' Nade, Dead
Weight, Box Opener, Bleeding and a failed Guardian Angel. Each has three versions — nobody to blame,
killed by another player (or bled out after fighting one), and killed by your own perk — under
`death.attack.bloodbound.<name>`, `.player` and `.self`. Only the message changes; the damage stays
the vanilla type it always was, so nothing that reacts to player attacks behaves differently.

## Activation keys

Each of the four loadout slots has **its own rebindable key**, listed under a *BloodBound* category
in Options → Controls. Defaults are `X`, `C`, `V`, `B` for slots 1–4.

A slot's key only ever fires the perk sitting in that slot, so moving a perk between slots moves
which key triggers it. Passive perks ignore their key. Where a slot holds an active perk, its key is
shown next to it in both the loadout tab and the HUD.

Two more keys, same category:

- **Skill Check** — right click by default, answers a skill check dial.
- **Heal Teammate** — `G` by default, held down to heal.

## Skill checks

A dial appears with a needle sweeping it once and a success zone to hit. Press the skill check key
while the needle is inside the zone. Letting it run out counts as a miss.

The server owns the timing and the verdict. The client reports where it saw the needle, which the
server accepts only when it broadly agrees with its own reading — so ordinary latency does not cost
you a check, and a doctored client gains nothing.

## Healing

Any perk that grants healing — Surgical Suture, Caretaker or We Can Do This — unlocks it. Stand within 3 blocks of a
player who is **crouched and holding still**, hold the heal key, and they recover 1 HP every 1.5
seconds — a full 20 HP heal in 30 seconds.

While healing, a skill check fires on a 30% roll each second. Land it and the target gains an extra
point of health; miss it and they lose one and the heal stalls for a second. A missed check can
never be what kills them.

The heal breaks the instant either player moves horizontally, the target stands up, they drift out
of range, or the key is released. Vertical movement is deliberately ignored, so jumping does not
cancel the heal.

## Addons

Addons are bonuses attached to **one specific perk**. They are bought on the soulweb, kept
permanently, and managed from the Addons tab.

**One addon per equipped perk** — the Addons tab shows a slot for each of your four loadout slots,
and an addon can only ever go in the slot of the perk it belongs to. An addon fitted to a perk that
is not equipped does nothing, and the tab says so rather than silently swallowing it. The list puts
the addons you can actually fit right now — the ones whose perk is equipped — at the top, then
groups the rest by perk, cheapest rarity first inside each group.

An addon's base price is set by its rarity: common 1, uncommon 3, rare 5, epic 8, unstable 10. That
base then goes through the same level curve as everything else on the web. Every rolled web offers
between 1 and 3 addon nodes, weighted so the rarer ones show up less often, and only ever for perks
you already own and addons you do not.

| Addon | Perk | Rarity | Effect |
| --- | --- | --- | --- |
| **Needle And Thread** | Surgical Suture | Uncommon | Skill check success zones 50% wider, but the self-heal is halved. |
| **Sterilizer** | Surgical Suture | Rare | A missed check grants a 3 second retry instead of a cooldown. Land the retry: healed, cooldown halved. Miss it or let it lapse: cooldown 30% longer. |
| **Gel Dressing** | Surgical Suture | Unstable | Used at full health, a landed check grants a yellow heart instead, up to 3. Unequipping the addon or its perk removes them at once. |
| **Taped Flashlight** | Longshot | Uncommon | Another +0.05 damage per block, and arrows give off light (level 10) until they despawn or are picked up. |
| **Gunpowder** | Longshot | Rare | Arrows ignore gravity. |
| **Point Blank** | Longshot | Epic | Headshots on players and mobs deal 1.75x damage, distance bonus included, with a sound cue. |
| **Boots of Speed** | Low Profile | Rare | No more night vision, but crouching is a further 75% faster. |
| **Steel Toe Boot** | Low Profile | Epic | Every 5 blocks walked crouched heals 1 HP. |
| **Black Strap** | Broken Movement Device | Common | The return point stays up 3 seconds longer. |
| **Black Cable** | Broken Movement Device | Common | The cooldown is 30% shorter. |
| **Diagnostic Tool C** | Broken Movement Device | Uncommon | 4 seconds after returning, press again to take the return back — position and health both. Cooldown 10% longer. |
| **Diagnostic Tool A** | Broken Movement Device | Rare | Speed V for 1 second then Speed I for the rest of the window, and no particles at all. Waste the window and you get Slowness III for 2 seconds. |
| **Diagnostic Tool B** | Broken Movement Device | Epic | The return rewinds along the path you walked over 3 seconds, and only while you hold the key. Let go and you stop where the rewind had reached, perk on cooldown. |
| **Grip Wrench** | Tinkerer | Common | Every landed check repairs a further 2% of durability. |
| **Spring Clamp** | Tinkerer | Uncommon | The opening check is 50% wider, and every check after it starts from there. |
| **Automatic Screwdriver** | Tinkerer | Epic | The checks never tighten below 40% of the opening one, and a miss costs no durability. |
| **Duct Tape** | Tinkerer | Unstable | Stretches the item's durability pool to 110% of what it was built with, so a run mends it past its own maximum. Never compounds. |
| **Overclocked Module** | Low-Cost Movement Device | Common | Every throw is 30% harder. |
| **Gear System** | Low-Cost Movement Device | Uncommon | Charges come back one at a time every 4 seconds instead of all at once. |
| **Loose Screw** | Low-Cost Movement Device | Rare | The shot flies slower and bounces once for a short second hop that throws you again. |
| **Box Opener** | Low-Cost Movement Device | Unstable | One charge, 3 seconds more cooldown, throws 50% harder, and anything you crash into mid-flight takes up to 18 damage from the impact. |
| **Shiny Coin** | Raise The Stakes | Common | The reroll chance goes up by 7.77 points. |
| **Scratched Coin** | Raise The Stakes | Uncommon | The reroll chance goes down by 10 points, but hostile mobs give 2 extra shards instead of 1. |
| **Tarnished Coin** | Raise The Stakes | Rare | 4% chance an ore also drops a whole storage block of itself; the reroll chance drops 4 points to pay for it. |
| **Shattered Coin** | Raise The Stakes | Unstable | The reroll chance jumps to 50/55/60%, but a failed first roll costs you the ore. |
| **Bandages Wrap** | Patch Up | Common | Mending yourself runs 25% quicker. |
| **Clean Gloves** | Patch Up | Uncommon | Skill checks are 20 points more likely on each roll. |
| **First Aid Spray Can** | Patch Up | Rare | Every other player within 8 blocks mends alongside you, at 75% of your rate. |
| **Knife Belt** | Nasty Blade | Common | The streak survives 2 seconds longer between hits. |
| **Razor Blade** | Nasty Blade | Uncommon | Every token is worth 0.75 damage instead of 0.5. |
| **Anticoagulant** | Nasty Blade | Epic | Attacks deal 3 less instead of 2, and everything hit is Broken for the streak plus 15 seconds. |
| **Blood Stained Book** | Guardian Angel | Common | 10 more seconds before the skill checks begin. |
| **Believer's Eye** | Guardian Angel | Uncommon | Two fewer skill checks are needed to survive. |
| **Engraved Tablet** | Guardian Angel | Epic | Every check landed banks a token; on your next death you keep 12% of your experience per token, up to all of it. |
| **Wire Spool** | Advanced Movement Device | Common | The reserve fills 75% faster. |
| **Scraps** | Advanced Movement Device | Uncommon | Reserve and shot budget 20% larger, shot 40% faster. |
| **Primer Bulb** | Advanced Movement Device | Uncommon | Reserve fills 150% faster but holds 8% less; arriving mends 0-4 HP. |
| **Tension Spring** | Advanced Movement Device | Rare | Hold the key while the shot flies; letting go puts you where it is, then a 1 second cooldown. |
| **Field Recorder** | Advanced Movement Device | Epic | No bounce — the first wall is the landing — and the shot pulls hard onto your crosshair; flight costs 50% more charges. |
| **Bright Feather** | Beware The Power Of An Angel | Common | A charge back every 1.25 seconds instead of 2. |
| **Belt Pendant** | Beware The Power Of An Angel | Uncommon | Fly 75% faster, at 4 charges a second instead of 3. |
| **Golden Crown** | Beware The Power Of An Angel | Rare | 25% more charges, one back every 2.25 seconds. |
| **Gabriel's Bow** | Beware The Power Of An Angel | Epic | While flying, bows draw 75% faster and arrows leave 150% faster — with no extra damage from the speed. |
| **Mark Of The Banished** | Beware The Power Of An Angel | Unstable | 400% more charges, but you are Exposed for as long as you are airborne. |
| **Empty Shotgun Shell** | Hunter's Instinct | Common | The range is 25% further. |
| **Bounty Hunter License** | Hunter's Instinct | Uncommon | A revealed aura does not fade while you keep looking straight at it. |
| **Light Steel** | Flashbang | Common | The cooldown is 30% shorter. |
| **Fast Fuse** | Flashbang | Uncommon | The grenade goes off 75% sooner. |
| **Worn Satchel** | Flashbang | Rare | Two grenades before the cooldown lands. |
| **Pulled Pin** | Flashbang | Epic | Thrown on the key coming up, up to 200% further after 4 seconds of holding. |
| **Slime Goo** | Bank Shot | Common | Charges come back 25% sooner. |
| **Broken Arrow** | Bank Shot | Rare | The seeking cone is 35% wider. |
| **Jagged Head** | Bank Shot | Rare | A hit after a bounce has a 50% chance to start Bleeding on 15 charges. |
| **Paper Fan** | Bank Shot | Epic | After a bounce the shot ignores gravity and bends hard onto your crosshair; what it hits is revealed for 10 seconds, and a revealed target takes 50% more damage. |
| **Origami Crane** | Bank Shot | Unstable | No charge limit, 5 bounces a shot, +10% damage per bounce — and no auto-aim at all. |
| **Metal Syringe** | Anti-Exhaustion Syringe | Uncommon | Also refills every charge you hold on any perk; cooldown 10% longer. |
| **Diluted Serum** | Anti-Exhaustion Syringe | Rare | Two doses before the cooldown, each 35% weaker. |
| **Inhalator** | Anti-Exhaustion Syringe | Unstable | Dose 75% weaker, cooldown 75% shorter, and every harmful effect on you is cleared. |
| **Fragstone Shard** | Frag' Nade | Common | Both blasts are 15% wider. |
| **Raw Ore** | Frag' Nade | Common | Goes off 30% sooner after landing. |
| **Star Medal** | Frag' Nade | Uncommon | While winding up, private particles show the path and both blast sizes. |
| **Powdered Crystal** | Frag' Nade | Rare | 80% longer between blasts; the second is 40% wider and Broken for 6 seconds. |
| **Quartz Pendant** | Frag' Nade | Epic | First blast 150% wider, 70% less damage and Slowness I for 4 seconds; the second deals 40% more. |
| **Pure Topaz** | Frag' Nade | Rare | The second blast spares you and throws 2.5× harder; no fall damage until 2 seconds after you land. |
| **Enhanced Gauntlet** | No One Gets Away | Common | The shot flies 50% faster. |
| **Tracking Head** | No One Gets Away | Rare | The shot bends slightly onto its target. |
| **Heavy Hook** | No One Gets Away | Epic | Drags you to the catch instead; it still gets the debuffs. |
| **Soul Chain** | No One Gets Away | Unstable | Through blocks, 15% slower, reveals anything within 2 blocks for 3 seconds; a miss halves the cooldown. |
| **Odd Arrow** | Longshot | Unstable | No gravity, 60% slower start that accelerates, S-shaped flight, bows draw twice as fast. |
| **Cursed Riser** | Longshot | Epic | Arrows bend slightly onto their target and start Bleeding on 18 charges. |
| **Leather Glove** | Close Call | Common | The dash carries 40% further. |
| **Protective Glove** | Close Call | Epic | Anything the dash runs through is thrown clear of it. |
| **Fingerless Glove** | Close Call | Unstable | 2 seconds to press again and dash once more, up to 3 in a row. Cooldown 250% longer, paid once the run ends. |
| **Cracked Cup** | Keep Fighting | Uncommon | The Broken is 25% shorter. |
| **Rune of Swiftness** | Keep Fighting | Rare | The player you heal gains Speed I for as long as your Broken lasts. |
| **Rune of Stealth** | Keep Fighting | Epic | Invisibility I for 10s, Resistance I for 5s, and every entity within 16 blocks revealed to you for 10s. Broken 10% longer. |
| **Dirty Shroud** | Lightbringer | Common | The aura reaches 8 blocks further. |
| **Charm of the Faithful** | Lightbringer | Uncommon | Every other player in the aura adds 15% to its recovery rate. |
| **Gilded Cross** | Lightbringer | Epic | The aura is worth 20/28/35% in company, 80/88/95% alone. |
| **Wild Rose** | Relentless | Common | Starts at 7/7.5/8 hearts or less. |
| **Wooden Sword** | Relentless | Uncommon | Always on, whatever your health, but the cooldown is 150% longer. |
| **Bloodied Matchete** | Relentless | Rare | Holds off until 4.5/5/5.5 hearts, and lands one more blow for 15/7.5/3.25% of the opening hit. |
| **Rotting Rope** | Echoing Wounds | Common | The echo reaches 50% further between victims. |
| **Hysteria** | Echoing Wounds | Uncommon | Echo victims get Blindness I and Slowness I for 3 seconds. Cooldown 1 second longer. |
| **Bloodied Letter** | Echoing Wounds | Uncommon | The chain reaches 2 more victims. |
| **Blood Thirsty Skull** | Echoing Wounds | Rare | Past the first victim, every further jump heals you 2 HP. |
| **Wrapped Glass** | Echoing Wounds | Epic | The first echo carries 80% of the opening blow, then every jump hits for 120/125/130% of the last instead of fading. |
| **Helium Inflated Balloon** | Perfect Landing | Common | Fall 35% slower, steer 30% better in the air, cooldown 40% shorter. |
| **Dead Weight** | Perfect Landing | Rare | Fall 75% faster, land with a Speed a third stronger but a third shorter, and crush whatever you land on for up to 30. |
| **Momentum Formula** | Perfect Landing | Epic | Past 3 blocks of fall you drop 2.5x as fast, and the Speed you land with lasts 40% less. |
| **Bounty Poster** | Longshot | Unstable | A hit from over 27 blocks away leaves the target Exposed for 20 seconds and reveals their aura to you for the same. |

## Soulweb rules

Each web mixes perks, perk upgrades, addons, loot and experience.

**Pricing.** Every reward has a base price B and a final price F. A perk or perk upgrade goes from 8
to 200; an addon from 1/3/5/8/10 to 25/75/125/200/250 by rarity; loot from its per-entry value to
twenty-five times that, the same ratio everything else lands on. The web level walks the price from
one to the other:

```
P = B + (F − B) / (1 + e^(−0.3 × (L − 12)))
```

so a perk costs 15 at level 1, 29 at level 5, 76 at level 10, 104 at level 12, 184 at level 20 and
196 at level 25.

**Price swings start at level 5.** Below that every node is priced exactly on the curve. From level
5 on, each one takes a random swing of up to ±7 shards.

**Every web owes you two perks and an addon.** At least two nodes carry a perk or perk upgrade and
at least one carries an addon — as far as there is anything left to offer, that is: a web can only
hand out an addon for a perk you already own and do not already have the addon for.

**Perk tiers come in order.** Only the next tier you are missing is ever offered, so Tier II cannot
turn up before Tier I, and the same perk never appears twice on one web.

**Loot quality is gated by level.** The rare pool cannot appear at all on web levels 1–3. From
level 4 it starts at 5% and gains another 5% every 3 levels, up to a 35% ceiling.

The common pool runs to arrows, torches, coal, ores, food and small experience drops. The rare pool
adds diamonds, emeralds, golden apples, ender pearls, blaze rods, ghast tears, diamond tools, large
experience drops, and **equipment rolled with up to 3 compatible enchantments** — iron sword and
pickaxe, diamond axe and shovel, bow, crossbow, iron chestplate, diamond helmet and boots, plus
enchanted books.

## Two implementation notes

- **Low Profile** uses a transient attribute modifier instead of the Speed potion effect. It grants
  the identical movement bonus (+20% per level, same as the potion), but it comes off the moment you
  stand up and it never overwrites a speed potion you happen to be drinking.
- **Close Call's** dash runs over 6 ticks with damage immunity for 10, so the distance is consistent
  whether you dash on the ground or in the air. Void damage and `/kill` still get through.

## Admin commands

`/bloodbound`, operator only (permission level 2). Every subcommand takes a player selector and
syncs the target immediately, so their screen and HUD update on the spot.

| Command | What it does |
| --- | --- |
| `perk grant <targets> <perk> [tier]` | Teaches a perk. Omit the tier for max. Sets the tier outright, so it can lower one too. |
| `perk revoke <targets> <perk>` | Forgets a perk, unequipping it and dropping its fitted addon. |
| `perk unlockall <targets>` | Every perk at tier III. |
| `addon grant\|revoke <targets> <addon>` | Grants or removes one addon. |
| `addon unlockall <targets>` | Every addon. |
| `shards <targets> <amount>` | Hands over soul shards. |
| `web reroll <targets>` | Rolls a fresh soulweb. |
| `web level <targets> <level>` | Sets the web level and rolls a matching web — the quickest way to test the rare-pool gating. |
| `cooldowns clear <targets>` | Clears every perk cooldown. |
| `reset <targets>` | Wipes all BloodBound progress. |
| `info <target>` | Prints learned perks, tiers, fitted addons, web level and shard count. |

Perk and addon ids tab-complete.

## Adding a perk

1. Declare it in [`ModPerks`](src/main/java/net/h3xpy/bloodbound/perk/ModPerks.java) with one
   `.scaling(t1, t2, t3)` call per value that changes between tiers, in the order the description
   uses them. Mark which one is the cooldown with `.cooldown(index)`.
2. Add `perk.bloodbound.<id>` and `perk.bloodbound.<id>.desc` to the lang files, with one `%s` per
   scaling value.
3. Drop three icons at `assets/bloodbound/textures/gui/sprites/perk/<id>_tier1.png`, `_tier2`, `_tier3`.
4. Implement it:
   - **Passive** — add the logic to
     [`PerkEventHandler`](src/main/java/net/h3xpy/bloodbound/event/PerkEventHandler.java), gated on
     `data.getActiveTier(perk)`.
   - **Active** — mark it `.type(PerkType.ACTIVE)`, then add one entry to the `ACTIONS` map in
     [`PerkActivationHandler`](src/main/java/net/h3xpy/bloodbound/event/PerkActivationHandler.java).
     The slot key, equip check, tier lookup, cooldown gate and client sync are all handled for you;
     the action just does its thing, sets its own cooldown, and returns whether it fired.
   - **Held** — a perk that runs for as long as its key is down, like Patch Up, stays out of
     `ACTIONS` and goes in `onSlotKeyHeld` instead. The client already reports every slot key's
     press and release; only the transitions travel.

It will start showing up on soulwebs on its own.

## Art

The **item and block** textures are procedural placeholders — replace them freely, nothing in the
code depends on how they look.

**Perk icons are hand-drawn, one per tier** — three square PNGs per perk:

```
assets/bloodbound/textures/gui/sprites/perk/<perk id>_tier1.png
assets/bloodbound/textures/gui/sprites/perk/<perk id>_tier2.png
assets/bloodbound/textures/gui/sprites/perk/<perk id>_tier3.png
```

Wherever a perk is drawn, the icon shown is the one for the tier in play: the tier you own in the
loadout and HUD, the tier a node grants on the soulweb.

Three rules:

- The filename must be **`<perk id>_tier<n>`** — `Perk` derives the sprite ids from the perk id.
- Filenames must be **entirely lowercase**. Minecraft resource paths only allow `[a-z0-9._/-]`;
  an uppercase letter makes the file unloadable.
- Images must be **square**. Any resolution works (the shipped set is 128×128).

**Addon icons** are one square PNG each, with no per-tier variants:

```
assets/bloodbound/textures/gui/sprites/addon/<addon id>.png
```

So the three shipped addons want `needle_and_thread.png`, `sterilizer.png` and `gel_dressing.png`.
Same rules: lowercase filename matching the addon id, square image, any resolution.

Icons go through the GUI sprite atlas (`blitSprite`), so they are scaled to whatever size each
screen needs. Changing your source resolution later needs no code change.

**Status effect icons are the exception**: Minecraft loads those from its own `mob_effects` atlas,
not the GUI one, so they must sit at `assets/bloodbound/textures/mob_effect/<effect id>.png` —
hence `mob_effect/broken.png`.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. `./gradlew runClient` launches a dev client.
