# BloodBound — notes for Claude

BloodBound is a Dead by Daylight–style perk mod for **NeoForge 21.1.248 / Minecraft 1.21.1**, package
`net.h3xpy.bloodbound`. Soul shards buy nodes on a soulweb; perks are kept for good and equipped four
at a time; addons attach to one perk each. `README.md` documents every perk, addon and system.

**Before every task, read `LESSONS.md`**: mistakes already made on this project and how to avoid
them. When you hit a new one, add a short entry there in the same pull request as the fix.

## Who you are working with

The people working on this repo **do not code**. They describe what they want in French — usually a
batch of bugs, buffs/nerfs, reworks and new perks/addons with exact numbers per tier (T1/T2/T3) — and
test in-game themselves. Answer in **French**, report grouped by bugs / changes / new content, give
the real cause of each bug in a sentence, and **flag every assumption** (unspecified rarity or
cooldown, missing texture, judgement calls). When a request is cut off or a file they mention is
missing, say so and give the exact path to use.

## Git workflow (they follow `CONTRIBUTING.md`; you do the git part)

Several people push to this repo, each through their own Claude. Keep `main` releasable:

- **Never commit to `main`.** At the start of a task (“Nouvelle tâche : …”), check `git status`; if
  there is uncommitted work, ask what it is before doing anything. Then `git switch main`,
  `git pull`, and `git switch -c <short-slug>` (e.g. `perk-wireless`, `fix-bounty-poster`).
- Commit and push **only when asked** (“commit and push”). Push the branch with
  `git push -u origin <branch>` and give the PR link:
  `https://github.com/h3xpy/BloodBound-1.21.X/compare/main...<branch>?expand=1`.
  `gh` may not be installed; the PR is opened in the browser.
- Use the git identity already configured on the machine (`git config user.name/user.email`); never
  invent or change one. If none is set, ask for their GitHub username and noreply email.
- Never force-push `main`, never rewrite history that is already pushed, never delete branches
  that are not theirs.
- “Mets ma branche à jour avec main” / conflicts: `git fetch`, `git merge origin/main`, resolve
  (lists like `ModPerks`, `ModAddons`, lang files and README tables usually just need both sides
  kept), build, then push.
- “La PR est mergée”: `git switch main && git pull`, delete the local branch.
- The version (`mod_version` in `gradle.properties`) is only bumped when asked, for a server release.
- CI (`.github/workflows/build.yml`) runs `./gradlew build` on every push and PR. Check a run with
  `curl -s https://api.github.com/repos/h3xpy/BloodBound-1.21.X/actions/runs?per_page=1`.

## Build and check

- `./gradlew compileJava -q`, then `./gradlew build -q`; grep the output for `error:`. The jar lands
  in `build/libs/`.
- After a successful build, boot the dev client on a test world to prove loading and registration:
  1. Make sure no dev game is already running (a `java` process whose command line contains
     `fml.modFolders`). If one is, the user is playing: do not launch, do not kill it.
  2. Find the world name in `run/saves/`. If there is none yet (a fresh clone), launch `runClient`
     without the quick-play arguments and ask the user to create a test world and tell you its name.
  3. Temporarily add after the `client()` line of the `runs` block in `build.gradle`:
     `programArgument '--quickPlaySingleplayer'` and `programArgument '<world>'`.
  4. Run `./gradlew runClient` in the background. Wait for `logged in with entity id` in
     `run/logs/latest.log`. Grep it for `missing sprite|Exception|ERROR]|Failed to load`, and check
     `run/crash-reports/` for new files.
  5. Kill only the JVM you started, and **always** restore it with `git checkout -- build.gradle`.
  This proves nothing about gameplay: say so in the report.

## Adding content (checklist)

All paths under `src/main/java/net/h3xpy/bloodbound/`.

- **Perk**:
  - Register it in `perk/ModPerks.java` with `Perk.builder("snake_id").type(ACTIVE|PASSIVE).scaling(t1,t2,t3)…`.
    Use `.cooldown(index)` or `.flatCooldown(s)`. Its javadoc lists `Scaling: [0] …`.
  - Add a `// --- X tuning ---` block with one `int` index constant per scaling entry, plus flat
    constants.
  - Put the logic in `perk/impl/Name.java`, or `event/NameHandler.java` if it is mostly event
    handlers. Static maps keyed by UUID hold transient state, with a `clear(UUID)`.
  - Wire it up:
    - active perks: `PerkActivationHandler.ACTIONS`;
    - hold/toggle perks: `onSlotKeyHeld`;
    - per-player tick: `PerkEventHandler.onPlayerTick`;
    - world-wide tick: `onServerTick`;
    - logout/death cleanup: `PlayerSyncHandler`;
    - event classes: `NeoForge.EVENT_BUS.register` in `BloodBound.java`.
- **Addon**: register it in `perk/ModAddons.java` with `Addon.of("id", ModPerks.X, AddonRarity.Y)`,
  grouped under `// --- PerkName ---`, followed by its tuning constants.
- **Lang**: `perk.bloodbound.<id>` and `.desc` (placeholders `%1$s`… in scaling order, `%%` for a
  literal percent), or `addon.bloodbound.<id>` and `.desc`. Always in both `en_us.json` and
  `fr_fr.json`. Validate both as JSON afterwards.
- **README**: add a row to the perk or addon table, with tier values in bold (`**t1/t2/t3**`).
- **Icons** are drawn by the users:
  - perks: `textures/gui/sprites/perk/<id>_tier1..3.png`;
  - addons: `textures/gui/sprites/addon/<id>.png`.
  Check they exist and list any that are missing.
- **Blockbench exports** dropped in the source tree do not compile (no package, old API). Convert
  them: give them a package, use `ResourceLocation.fromNamespaceAndPath` and the 1.21
  `renderToBuffer(..., int color)`. The model goes in `client/model/`, the texture in
  `textures/entity/`, and the layer is registered in `BloodBoundClient`.
- **Death messages**: a perk that can kill wraps its damage in `PerkDamageSource.of(source, "name")`,
  with lang keys `death.attack.bloodbound.<name>`, `.player` and `.self`.

## Invariants that past bugs came from

- **Keys:** `ActivatePerkPayload` fires on a fresh press only. Hold-driven perks use `onSlotKeyHeld`.
  For these, `activate()` returns false.
- **Effect durations** are changed in place (`effect/EffectDurations.set`), never by removing and
  re-adding the effect: removal fires `MobEffectEvent.Remove`, and the Bleeding/Exhausted/Aura
  handlers treat that as the effect ending.
- **Virtual projectiles** (harpoons, grenades, runes…) are simulated server-side in static lists and
  drawn with particles. Bank Shot support goes through `BankShotHandler.spendBounce/seek/steer/reflect`.
- **Aura reveals** always go through `AuraRevealHandler.reveal`. That is where Under The Radar,
  Enhanced Perception and its addons apply.
- **Player movement is client-authoritative:** read real speed from `effect/MovementTracker`, not from
  `getDeltaMovement()`. To pull a player, change their velocity; never teleport them every tick
  (it stutters).
- **Anything the client animates must run on both sides.** Bow draw speed-ups live in
  `event/DrawSpeed` and `client/ClientDrawSpeed`; server-only did nothing visible.
- **Never iterate a level's live entity table** (`getAllEntities()`). The server runs C2ME and
  Distant Horizons, which change it off-thread, and that crashed it. Query with
  `getEntitiesOfClass(type, box)` around the players that matter, or iterate copies.
- **Rituals and traps are saved with the world:**
  - `RitualEntity`, `BarbedWireEntity` and `TargetFoundEntity` carry their data in NBT.
    `RitualManager.onJoin` and `TrapRoster.onJoin` rebuild the state.
  - A marker unloaded with its chunk is dormant, not broken.
  - On logout, only the laying in progress is dropped. On server stop, the maps are forgotten but no
    entity is discarded.
  - Ritual loops use `RitualManager.all(perkId)`, and the owner may be offline.
- **Mixins** are declared in `bloodbound.mixins.json`. `mixin/LevelMixin` hooks `Level.setBlock` for
  Nullification. Keep its empty fast path and its exclusions (piston/contraption moves, falling
  blocks, fire, natural leaves): cancelling those duplicates blocks.
- **Charges** shown on the HUD go through `PerkChargesPayload(perkId, charges, max, rechargeAt)`.
  `-1, 0` hides them.

## Editing tips

- `fr_fr.json` holds French text: edit it with exact string replacement (the Edit tool). Bulk regex
  tools have corrupted its UTF-8 before.
- Check a vanilla/NeoForge API in the sources jar before relying on it (`build/moddev/artifacts/`).
  Several bugs came from assuming when an event fires.
- After a scripted multi-file edit, grep for each expected change: a pattern that silently matched
  nothing is the usual failure.
