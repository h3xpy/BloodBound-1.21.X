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
- Check with `grep -c $'\r' <file>`. Before scripting, normalise the files you are about to edit:
  `sed -i 's/\r$//' <file>`. Git stores LF either way, so the diff stays clean.
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
