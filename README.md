# TimeSMP

A ranked-SMP style Paper plugin: a playtime timer that goes up while online, gets
punished/rewarded by kills and deaths, boosted by quests, and drives a live
leaderboard where your rank decides your max health.

## ⚠️ Important - I could not compile-test this

I don't have network access to PaperMC's Maven repository from this sandbox, so I
was not able to actually run a build and confirm it compiles clean. I've hand-checked
every file against the Paper 1.21 API, but you'll want to run the build yourself
(step 2 below) and paste me any errors if `mvn package` complains about anything -
I'll fix it immediately.

`pom.xml` is pinned to Paper API `1.21.11-R0.1-SNAPSHOT` to match your server's reported
version exactly.

## Build

### Option A - GitHub Actions (no local install needed)

This project already includes `.github/workflows/build.yml`. To use it:

1. Create a new GitHub repo (can be private) and push this folder's contents to it.
2. GitHub will automatically run the build on push (or trigger it manually from the
   **Actions** tab → "Build TimeSMP" → **Run workflow**).
3. Once it finishes (green check), open that workflow run and download the **TimeSMP**
   artifact - it's a zip containing `TimeSMP.jar`.
4. Upload that jar to your PebbleHost `plugins/` folder and restart.

### Option B - Build locally

1. Install a JDK 21 and Maven on a machine with internet access.
2. From this folder, run:
   ```
   mvn clean package
   ```
3. The built jar will be at `target/TimeSMP.jar`. Drop it in your server's `plugins/` folder and restart.

## What it does

- **Global on/off switch**: nothing runs until an admin runs `/timesmp start` - no ticking, no
  quests, no kill/death time changes. `/timesmp pause` and `/timesmp stop` both freeze everything
  (kept as two separate commands since they read as different admin intents, even though they
  currently behave identically). `/timesmp restart` wipes everyone's time and quests back to fresh
  and starts again. This is so you can set the server up without anyone falling behind.
- **Timer**: everyone's banked time ticks up by 1 second for every second they're online -
  except while they're on the death screen (no ticking between dying and respawning), or standing
  in a world listed under `disabled-worlds` in the config (see below).
- **Kills steal time, they don't create it**: a kill takes `kill-reward-hours` directly from the
  victim and gives it to the killer - it's one transfer, not a separate reward and penalty. This is
  capped by `min-time-hours` (default -4h): a victim at -3h with that floor only has 1h left to
  lose, so the killer only gets 1h out of that kill. A death with no player killer (fall, mobs, etc)
  moves no time at all. The killer gets a single floating "+" popup (a `TextDisplay` that rises and
  fades) plus a totem-flavoured particle burst and sound - it only appears once the tombstone below
  has actually landed, not at the instant of death.
- **Death animation**: a two-piece tombstone (dark base ledge + mossy upright headstone) drops from
  above and lands at the death spot with a thud, the victim's name floats above it, and after 60s
  (configurable) it all shrinks away and disappears. Built from Display entities since there's no API
  to literally tip a real entity model over, so it's a rectangular stand-in, not a pixel match for a
  rounded/arch-top custom tombstone model (that'd need a resource pack or custom item model, not just
  transforms). Doesn't touch real item drops.
- **Withdraw / deposit time as items**: `/withdraw <hours>` turns whole hours of your banked time into
  stackable "1 HOUR" clock items (also capped by `min-time-hours` - you can't withdraw yourself below
  the floor). Right-clicking one claims +1h and consumes it. Tradeable, droppable, giveable - a
  physical currency for banked time.
- **Quests**: one of 4 random types is assigned at a time (a fresh random type + random target within
  its configured range each time). Completing one banks between 5-8 hours (scaled by how hard the
  rolled target was), then the next quest becomes available 12 hours later.
  - Kill 3-8 different players
  - Play 1-3 hours in a day (expires and rerolls if not finished within 24h)
  - Play 1-3 hours without dying (progress resets to 0 on death, quest itself doesn't reroll)
  - Deal 20-40 hearts of damage
- **Leaderboard & hearts**: ranks are purely by banked time, recalculated every few seconds.
  #1 = 20 hearts, #2-5 = 19 hearts, smoothly stepping down to #40 = 11 hearts, everyone else = 10
  (default). Anyone with a **negative** balance is forced to 8 hearts regardless of rank until
  they're positive again. Anyone standing in a `disabled-worlds` world is always flat 10 hearts.
- **Rank tag everywhere**: `[#N] PlayerName` in the tab list (also sorted BY rank, #1 first), in chat,
  and above their head as a real in-world nametag. Colour is #1 = red, #2-40 = orange, everyone else
  = yellow - same three-tier colouring in all three places.
- **Kills/deaths tracked** as running per-player counters (only while the timer is RUNNING and not in
  a disabled world), shown on the sidebar.
- **Sidebar scoreboard**: optional, toggleable per-player (`/timeboard`) independently of the nametag
  system above (turning it off doesn't blind you to others' rank-coloured nametags). Shows, top to
  bottom: a red/orange "CLANKER SMP" title, current timer status if not running, rank, time, kills,
  deaths, quest + progress, and your own name at the very bottom.
- **Extra inventory (top 20)**: `/extrainventory` (aliases `/extrainv`, `/einv`, `/inv`, `/inventory`)
  opens a bonus inventory only players ranked in the top 20 can use. #1 gets 27 slots, #20 gets 5,
  interpolated evenly in between; everyone outside the top 20 is told they need to rank up. Whatever's
  in it drops on the ground when you die, same as your normal inventory.
- **Join book**: every player gets a written book on join explaining the mechanics, unless they
  already have one in their inventory (checked via a hidden tag, so renaming/re-lore-ing a copy still
  counts - it won't hand out infinite copies).
- **Disabled worlds**: any world named in `disabled-worlds` in the config is fully exempt - no
  ticking, no kill/death time stealing, no quest damage tracking, flat 10 hearts. Meant for a
  Multiverse minigame/lobby world that shouldn't touch the SMP economy at all.
- **Anti-AFK**: kicks a player if they haven't left- or right-clicked (real mouse clicks - detected
  via the arm-swing animation event plus block/air interact events) within `afk-kick.timeout-minutes`
  (default 5). Deliberately click-only: movement, chat, and commands do NOT reset the timer, so an
  auto-walk/auto-jump macro won't save you - only genuine clicks do. Shows an actionbar countdown for
  the last `afk-kick.warning-seconds` before the kick. Runs independently of `/timesmp start|stop` -
  it's not part of the time economy, so it's active even before the timer's been started. Players
  with `timesmp.afk.bypass` (default OP) are exempt.
- **Combat log** (own config file, `combatlog.yml` - kept separate from `config.yml` on purpose):
  getting hit by another player tags BOTH of you for `combat-duration-seconds` (default 30),
  refreshing back to full on every hit, with a red actionbar countdown. While tagged: ender pearls get
  a real vanilla cooldown (the red swirl - genuinely unusable) for `pearl-cooldown-seconds`; any mace
  hit landed while *already* tagged is cancelled outright (your first hit, which starts the tag, still
  lands - see the note below on why this isn't done the same way as pearls); and Elytra gliding is
  blocked entirely. If a tagged player disconnects, their real inventory AND their extra-inventory
  drop where they stood, and whoever tagged them most recently still gets the normal kill-steal -
  **applied immediately on the disconnect, not deferred until they rejoin**. Item-drop punishment
  happens regardless of the main timer's state; the actual time-steal only happens if it's RUNNING
  (same rule a normal kill follows). Has its own `disabled-worlds` list, independent from
  `config.yml`'s, so a world can have the time economy off but combat-tag rules still on, or vice
  versa.
- **Tab-completion** on every command: `/timesmp` suggests its subcommands, then online player names
  for `settime`/`addtime`/`removetime`/`reroll`, then example hour values for the time-edit ones;
  `/playtime` and `/withdraw` get the same treatment (player names / example hours respectively).
  This is what shows the in-game argument dropdown (like vanilla `/gamemode`) instead of typing blind.

## Commands

Player:
- `/playtime [player]` - check banked time
- `/quest` - see your current quest and progress
- `/top` - top 10 leaderboard
- `/timeboard` - toggle your personal sidebar scoreboard
- `/extrainventory` (or `/extrainv`, `/einv`, `/inv`, `/inventory`) - open your rank-based bonus inventory (top 20 only)
- `/withdraw <hours>` - convert whole hours of banked time into tradeable clock items

Admin (`timesmp.admin`, defaults to OP):
- `/timesmp start` - begin/resume ticking
- `/timesmp pause` - temporarily freeze everything
- `/timesmp stop` - freeze everything (same effect as pause today, kept separate for intent)
- `/timesmp restart` - reset everyone's time + quests to fresh, then start
- `/timesmp reload` - reload config.yml
- `/timesmp save` - force-save player data
- `/timesmp settime <player> <hours>` / `addtime` / `removetime` - adjust anyone's banked time,
  **ignoring the -4h floor on purpose** (works on offline players too, as long as they've joined
  before) - this is your tool for correcting cheaters or compensating people who got cheated on
- `/timesmp reroll <player>` - force a new random quest

## Config

Everything - kill-reward/floor hours, quest ranges and reward range, the heart tiers, refresh/autosave
intervals, death-animation duration, disabled-worlds - is tunable in `config.yml` (generated on first
run). Comments explain each value. Combat-log settings live in their own separate `combatlog.yml`,
also generated on first run - see the comments in there for tag duration, cooldowns, elytra-block,
combat-log punishment, and its own independent `disabled-worlds` list.

## Assumptions I made where the spec was ambiguous

- "Deal 20-40 hearts of damage" quest counts damage to **any living entity**, not just other
  players (change this if you only want it to count PvP damage - it's a one-line tweak in
  `DamageListener`).
- The daily-playtime quest is the only one with a hard deadline (24h, matching its own wording).
  If it's not finished in time it just rerolls into a fresh quest rather than "failing" you.
  The no-death-streak quest resets its progress on death but never expires/rerolls.
  The kill-count and damage quests have no deadline either.
- Above-the-head nametags are now implemented directly (not just tab/chat) - see the architecture
  note in `ScoreboardManager` if you're curious how that avoids conflicting with the personal sidebar.
- The tab list ordering uses Paper's `Player#setPlayerListOrder`. You confirmed the first version
  sorted backwards (rank #1 at the bottom), so it's now inverted (`Integer.MAX_VALUE - rank`) -
  should show #1 at the top now, but I still can't runtime-verify it myself, so double check.
- Health increases top the player up to full (config: `heal-on-increase`); health decreases only
  clamp current health down if it's above the new max.
- The extra-inventory GUI is internally a fixed 27-slot (3-row) chest no matter your rank - slots
  you haven't unlocked show as a grey "Locked" pane instead of the GUI literally resizing (Minecraft
  chest inventories can only be sized in multiples of 9). Your unlocked slot count is locked in for
  the duration of a viewing session - if your rank changes while you have it open, the new count
  applies next time you open it, not live mid-session. This avoids a bunch of item-desync edge cases
  from resizing a GUI while someone's actively using it.
- The guide book's text is stored verbatim (typos included) in `config.yml` under `guide-book.pages`.
  It already matches the new steal-on-kill mechanic ("steal 2 hours of their time"), but doesn't
  mention withdraw/hour-tokens or the top-20 extra inventory - say the word if you want lines added.
  The "Page X of 3" headers got removed from the page text itself since the book UI already shows
  that in the corner automatically - it was showing up twice before.
- "/timesmp stop" and "/timesmp pause" currently do the exact same thing mechanically (freeze
  ticking). You asked for both as distinct commands, so they exist as distinct commands, but there's
  no behavioural difference yet - let me know if you want stop to do something extra (e.g. announce
  the event has ended, lock everyone's inventories, etc).
- Anti-AFK activity is click-only by design (per your instructions) - chat messages, running
  commands, and plain movement do NOT reset the AFK timer, only left/right mouse clicks do. If that
  turns out too strict in practice (e.g. someone actively building/mining who just happens to go a
  few minutes without clicking air/an entity) say the word and I can loosen it.
- "Mace cooldown" can't work the same way pearl cooldown does - vanilla's item-cooldown system (the
  red swirl) only blocks right-click "use" actions, not raw melee swings, so it wouldn't actually stop
  someone from attacking with a mace. Instead, any mace hit landed while a player is ALREADY tagged
  gets its damage cancelled outright (their very first hit, which starts the tag, still lands - it's
  only follow-up hits during an active tag that get blocked). It also still gets the cosmetic
  cooldown swirl applied for visual feedback, but that swirl isn't what's actually enforcing it.
- Known limitation, not new to this feature: several classes (including the new combat ones) cache
  the config object once at startup rather than re-reading it on every access. `/timesmp reload`
  re-reads config.yml/combatlog.yml from disk into fresh objects, but a few already-constructed
  classes may keep using their original cached values until an actual server restart. This predates
  combat-log - I haven't done a full audit/fix of it yet since it's a broader refactor across most
  files; say the word if you want that cleaned up properly.
