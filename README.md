# QuickDuel

A standalone Paper plugin: `/duel <player>` to challenge someone, with clickable chat buttons
and a full confirmation GUI, and on accept a two-phase movement-locked countdown before both
players land on the same random LAND-ONLY spot in the Overworld.

## Build

Same process as TimeSMP - push to GitHub and grab the artifact from Actions (workflow already
included at `.github/workflows/build.yml`), or build locally with `mvn clean package` (needs JDK
21 + Maven). Output is `target/QuickDuel.jar`.

`pom.xml` is pinned to the same Paper API version as TimeSMP (`1.21.11-R0.1-SNAPSHOT`). Same
caveat as always: I couldn't compile-test this myself (no network access to PaperMC's repo from
this sandbox) - if the build fails, paste me the error.

## How it works

1. **`/duel <player>`** sends a challenge. The target gets a chat message with clickable
   **[Accept]** / **[Decline]** buttons (real click-to-run-command, not a guess this time - this
   uses Adventure's `ClickEvent` directly, the same reliable mechanism TimeSMP's own chat prefixes
   use), plus `/duel accept` / `/duel decline` work as typed commands too. Only the most recent
   request to a given player is tracked, so accept/decline always resolves to the latest one.
   Requests expire after `request-timeout-seconds` (default 60).

2. **Accepting opens a confirmation GUI** - it does NOT immediately start the duel. The GUI shows:
   - The challenger's head (their real skin), with hover text showing the **biome of the exact
     spot you're about to be teleported to** (not wherever they currently are), their name, and
     their TimeSMP rank if both TimeSMP and PlaceholderAPI are installed.
   - A green **ACCEPT** pane and a red **CANCEL** pane.

   The destination is rolled once, the moment the GUI opens - the biome shown and the spot you
   actually land on are guaranteed to be the same roll, not two different random rolls.

3. **Clicking CANCEL only closes the confirmation** - it does **not** decline the underlying duel
   request. The request stays pending exactly as it was; running `/duel accept` again just reopens
   the GUI. Only `/duel decline` (or clicking the Decline button in chat) actually declines and
   notifies the challenger. Pressing Escape to close the GUI without clicking anything behaves the
   same as clicking CANCEL.

4. **Clicking ACCEPT starts the countdown:**
   - Both players are frozen in place (can't move, can't deal or take damage) with a "5, 4, 3, 2,
     1 - Don't move!" title countdown at their **current** location.
   - They're then teleported to the pre-rolled destination, landing a few blocks apart
     (`spawn-separation`) facing each other.
   - A second "3, 2, 1, GO!" title countdown plays, still frozen the whole time.
   - Only after "GO!" are they unfrozen and able to move/fight.
   - If either player disconnects mid-countdown, it's detected within about a second and both are
     cleanly unfrozen and released from the sequence.

5. **Land only, never water** - the random spot search explicitly excludes water (and lava, and
   the void), re-rolling up to `max-location-attempts` times before falling back to world spawn.
   Always in the Overworld specifically (`teleport-world` in config, default `"world"`),
   regardless of which dimension either player started in.

## Commands

- `/duel <player>` - challenge someone
- `/duel accept` - open the confirmation GUI for your most recent pending request
- `/duel decline` - actually decline your most recent pending request

Permission `quickduel.use` (default: everyone).

## Config (`config.yml`)

- `teleport-world` - exact world name duels happen in (always the Overworld)
- `teleport-radius` - max distance from that world's spawn, in blocks
- `request-timeout-seconds` - how long a challenge stays valid
- `max-location-attempts` - re-roll attempts before falling back to spawn
- `spawn-separation` - how many blocks apart the two duelists land

## Optional integrations

- **PlaceholderAPI** (soft-depend) - if installed, and TimeSMP is also installed with its own
  PlaceholderAPI expansion registered, the confirmation GUI shows the challenger's TimeSMP rank in
  the head's hover text. QuickDuel has no direct dependency on TimeSMP itself - it just asks
  PlaceholderAPI to resolve `%timesmp_rank_tag%` the way anything else would. If either plugin/
  placeholder isn't present, that line is silently omitted - nothing breaks.

## Notes / assumptions

- No arena/return system, no health/inventory reset, no win/loss tracking - just the challenge/
  confirm/countdown/teleport flow you asked for. Say the word if you want any of that layered on.
- Landing in unloaded terrain at a random spot up to `teleport-radius` blocks out can force that
  chunk to generate on the spot, which may cause a brief pause. This isn't meant to be called in
  rapid succession.
- This is a completely separate plugin from TimeSMP and RexonRTP - it doesn't touch either, beyond
  the optional read-only PlaceholderAPI rank lookup described above.
