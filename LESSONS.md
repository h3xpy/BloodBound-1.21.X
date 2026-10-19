# Lessons learned — read before working on BloodBound

Mistakes that already happened on this project, by any of the Claudes working on it, and the rule
that keeps them from happening again. Read this before starting a task. When you find a new one,
add it here in the same pull request as the fix: one short entry, what happened and the rule.

Older, broader rules are in `CLAUDE.md` ("Invariants that past bugs came from").

---

## Tooling

### Line endings change on pull, and silent edit failures follow
`core.autocrlf` is `true` on these Windows machines. After a `git pull`, every file the pull
touched is checked out with CRLF line endings. A scripted edit (perl, sed, regex) whose pattern
contains `\n` then matches nothing **and reports no error**. Several edits were silently skipped.
- Do not check with `grep -c $'\r'`: Git Bash's grep reads files in text mode and reports 0 on a
  CRLF file. Check with perl instead: `perl -ne 'if (/\r/) { print "$ARGV\n"; close ARGV }' <files>`.
- Before scripting, normalise the files you are about to edit: `perl -pi -e 's/\r$//' <file>`. Git
  stores LF either way, so the diff stays clean.
- `sed -i "Na\\ line1\\ line2"` in Git Bash glues the appended lines into one. Add several lines
  with the Edit tool.
- In `perl -pi -e 's|…|…|'`, a `\|` in the pattern is not a literal pipe but alternation with
  nothing, which matches at the start of every line: the replacement was written in front of every
  line of the README, then of a Java file. With `|` as the delimiter, never put `||` or `\|` in the
  pattern; pick another delimiter or use the Edit tool.

## Riding

### The server hears a rider's keys a tick late
`ServerPlayer.jumping` (and the crouch state) only change when the client sends its input, which it
does only once it is riding. On the tick a player climbs on, the server still has the old values,
so "jump to get down" read a jump held from before as released, then saw it pressed a tick later and
threw the rider straight off. Undersized only arms the jump/crouch dismount a few ticks after
mounting, once both have been seen released (`Undersized.ARMED`, `PlayerRidingMixin`).
- After every scripted edit, grep for each expected change and compare the counts.
- The Edit tool (exact string replacement) copes with either line ending.

## Gravity and "down" (The Hanged Man, anything that flips or changes gravity)

Inverting the gravity attribute is not enough. Vanilla assumes "down" in many places that have
nothing to do with gravity. Each one below was a separate bug report. If you change gravity or
orientation again, go through the whole list:

- **Server fall tracking:** `ServerGamePacketListenerImpl.handleMovePlayer` resets the fall
  distance on every move packet that goes **up**. It also calls `jumpFromGround()` when a player
  leaves the ground going up. Falling upwards therefore never accumulated, and fall damage never
  landed.
- **Player fall damage does not come from `move()`:** `ServerPlayer.checkFallDamage` is empty. The
  server applies fall damage through `doCheckFallDamage`, called from `handleMovePlayer`. Test
  fall damage on a dedicated path, not by reading `Entity.move` alone.
- **"The block under the feet"** is `Entity.getOnPos(float)`: friction, soul sand, honey, magma,
  slime, footsteps, landing. Mirror that one method and all of them follow.
- **Ladders:** `LivingEntity.onClimbable()` reads the block at `blockPosition()`, the bottom of the
  hitbox. Upside down that is the head, so the player could not climb past the end of a ladder.
- **Particles:** sprint dust (`spawnSprintParticle`) and landing dust (`checkFallDamage`) spawn at
  `getY()`, the bottom of the hitbox. Upside down, that is right in the player's face.
- **Controls:** creative flight and swimming use jump = +Y and sneak = −Y in world terms. With the
  view rolled over, they feel reversed.
- **Steps, knockback, slime and bed bounces, the void** all assume +Y is up.

## "Not active" is not "unequipped"

Reactive Compound turns a player's perks off for a while, through `getActiveTier`, which then
returns 0. Barbed Wire, Spring Pad and Target Found used to read a tier of 0 as "the perk left the
loadout" and took every trap away, so a disable would have wiped them.
- Use `getActiveTier` for "does it work right now". To decide whether to tear down what a perk left
  in the world, use `PlayerPerkData.hasEquipped(perk)`.

## Tuning a first-person animation blind does not work

The first Reactive Compound animation was placed by reasoning alone: the off arm filled the screen,
the vial never tipped and the weapon was hidden. What worked was looking at it:
- a temporary client class, registered only under a `-Dbloodbound.animdebug` system property, that
  freezes the animation at a few points after joining and calls `Screenshot.grab(...)` for each;
- the quick-play arguments plus `jvmArgument '-Dbloodbound.animdebug=true'` added to `build.gradle`
  for the run (restored afterwards), and the PNGs in `run/screenshots/` read and compared;
- the class and its registration deleted before committing.
In such a harness, aim the camera with `player.connection.teleport(x, y, z, yaw, pitch)`: a plain
`teleportTo` followed by `setYRot`/`setXRot` on the server never reaches the client, and the shots
came out pointing the old way. Build the test scene on a temporary platform high in the sky (and
remove it after): the test world's terrain hid the subject behind trees and hills more than once.
An arm with an item in it: draw the arm the way `ItemInHandRenderer.renderPlayerArm` does, then
`translateAndRotate` on the model's arm and the `ItemInHandLayer` offsets before rendering the item.

## Entities bigger than their hitbox

### A hitbox that contains the player's eyes steals every click
Picking (crosshair targeting) treats any hitbox containing the eye position as hit at distance 0.
If a large entity such as the Holy Sanctum bubble had a hitbox of its own size, anyone standing
inside could no longer mine, place, or hit anything.
- Keep such entities' hitbox tiny and non-pickable. Find clicks on them yourself: the shell is
  found with a ray–sphere test in `client/ClientSanctumHits`, and only when it is nearer than what
  the crosshair already hits.

### Rendering a big entity
Set `noCulling`, override `getBoundingBoxForCulling()` and `shouldRenderAtSqrDistance()`.
Otherwise the entity disappears as soon as its tiny real hitbox leaves the view.

### Shapes that last should not be particles
A shape that stays on screen (a bubble, a wall) drawn as a particle cloud reads as noise, and a hit
on it is invisible. Use an entity with a renderer, and carry what the client needs to show
(health, last hit) in synced entity data.

### "Keep things out" has to cover every kind of entity
Holy Sanctum first stopped only creatures and projectiles. Primed TNT, falling blocks, items and
vehicles are neither, so TNT rolled in and exploded inside. A barrier has to handle every entity
type explicitly (`HolySanctum.bounceOff`), checking the whole tick's movement so a fast one cannot
skip through.

## Both sides

### A refused action must resync the client
Cancelling a placement (`EntityPlaceEvent`, or `RightClickBlock` on the server) puts the block back
in the stack **on the server**. The client had already taken it out of the hand and is never told
otherwise, so the block looks consumed. After any such cancel, send the inventory once the tick is
over: `PerkDataManager.syncInventory(player)`. Holy Sanctum and Nullification both do this.

### Anything the client animates must be changed on both sides
Speeding up a bow draw on the server alone did nothing visible. The client's draw animation, and
therefore the moment the player lets go, never changed. See `event/DrawSpeed` and
`client/ClientDrawSpeed`.

### Client-only classes
Never reference `net.minecraft.client.*` from code that can run on a dedicated server. Put client
behaviour in `client/` and register it from `BloodBoundClient`.

## Vanilla trading

- A wandering trader draws its five common trades **until five are not null**, so a listing that
  returns null costs nothing. Its single rare trade picks **one** listing **with no retry**: a null
  there means no rare trade at all.
- Adding listings never changes villagers and traders that already exist. Only new ones, or
  villagers reaching a new level, get them.

## State that switches off

### Compare with what was applied, not with the clock
Vigilance's icon never went out. Each tick worked out "was it on before?" from the same expiry time
as "is it on now?", so on the tick it ran out both were already false and the switch-off (speed
removal, message to the client) never ran.
- Keep a set of who the effect is applied to, and compare against that. On death and logout, tell
  the client too: it keeps its own copy.

## Perks that feed each other

### An anti-spam gate swallows hits another perk deals
Nasty Blade banks at most one token every half second, so a flurry of clicks does not count. Echoing
Wounds lands its jumps 3 ticks apart, all inside that gate, so none of them ever banked a token and
the two perks looked broken together. When a perk limits "hits per second", decide explicitly what
happens to hits another perk makes (`EchoingWoundsHandler.isEchoing()` tells them apart).

## Entities loaded from disk

### "Just fired" is `!event.loadedFromDisk()`, not `tickCount == 0`
`EntityJoinLevelEvent` also fires when a chunk loads entities back from the save, and `tickCount` is
not saved, so a reloaded arrow is at tick 0 too. Arrows lying in the ground were taken for fresh
shots every time their chunk loaded: Gunpowder's sniper sound played with nobody shooting, Bank Shot
could charge a bounce, and Gabriel's Bow cut the arrow's damage again. Check
`event.loadedFromDisk()` in any handler meant for a projectile being fired.

## Pulling a player

### Steer a player along a path open-loop, not from where the server sees them
Sacrificial Knife's pull set the target's velocity to "next point on the curve minus where they are
now". For a mob that is exact. For a player, "where they are now" is what their client last reported,
a tick or two old, so the server asked for two steps at once, the client overshot, and the next tick
pulled it back: a visible zigzag. For players, send the curve's own step (point k minus point k-1)
every tick and let the client carry it; only server-moved entities can be steered from their position.
