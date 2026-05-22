# SmartVillager Test Checklist

Work through phases in order — each phase depends on the one before it passing.

Legend: `[ ]` = untested · `[x]` = passing · `[~]` = partial / flaky · `[!]` = broken

---

## Phase 1 — Mod Load & Basic Commands

**Goal:** Confirm the mod loaded, a village was detected, and the debug commands return real data.

**Setup:** Load a world with a vanilla village nearby. Stand within 64 blocks of it.

| # | What to do | What to look for |
|---|---|---|
| 1 | Run `/sv status` | Prints a village ID, simulation mode, and prosperity score — no error |
| 2 | Run `/sv roster` | Lists villagers with profession, HP, and hunger values |
| 3 | Run `/sv nearby` | Lists physical villager entities with profession and HP |
| 4 | Run `/sv debug` | Chat confirms toggle is ON |
| 5 | Wait 5 seconds near a villager | A status line appears in chat from that villager (idle/hungry/etc.) |
| 6 | Run `/sv debug` again | Toggle confirms OFF, messages stop |
| 7 | Save and rejoin the world | Run `/sv status` again — same village ID, same prosperity score |

- [ ] 1 — `/sv status` returns data without error
- [ ] 2 — `/sv roster` lists villagers
- [ ] 3 — `/sv nearby` lists entities
- [ ] 4–6 — debug toggle works, thought messages appear and stop
- [ ] 7 — village data persists across rejoin

---

## Phase 1b — Storehouse Initialization

**Goal:** Confirm a physical storehouse chest spawns near the Bell on village registration and the starting inventory is seeded correctly.

**Setup:** Use a fresh world (or delete the village save data) so village registration fires cleanly. Stand within 64 blocks of the Bell.

| # | What to do | What to look for |
|---|---|---|
| 1 | Load into a world with a vanilla village | A chest block appears within ~10 blocks of the Bell |
| 2 | Run `/sv stockpile` | Shows `bread: 8` and `smartvillager:healing_supply: 4` from the seed |
| 3 | Open the chest in the world | Bread is physically inside (reconciled from snapshot when full sim activates) |
| 4 | Run `/sv status` | Chest tracker lists at least one registered position |
| 5 | Watch the Guard patrol route | Guard walks to the storehouse chest area on every patrol loop |
| 6 | Run `/sv threat trigger` | One Guard holds at the chest; others engage |

- [ ] A chest is placed near the Bell on village registration
- [ ] Starting inventory seeded: `bread: 8`, `healing_supply: 4`
- [ ] Chest is registered in the StockpileChestTracker (`/sv stockpile` reads it)
- [ ] Guard patrol visits the storehouse chest once per loop
- [ ] Chest guardian holds at the chest during THREAT_ALERT

---

## Phase 1c — Prosperity-Gated Role Assignment

**Goal:** Confirm that advanced roles only unlock once the village has earned the required prosperity, regardless of how many vanilla villagers are present.

**Setup:** Use a fresh world. Run `/sv prosperity set 0` immediately after detection to lock the village at Tier 0.

| # | What to do | What to look for |
|---|---|---|
| 1 | Load into a village with 6+ existing villagers, set prosperity to 0 | `/sv roster` — only Librarian, Farmer, Guard assigned; extras are Farmer |
| 2 | Confirm no Cleric, Fisherman, or higher roles exist at prosperity 0 | `/sv roster` shows only Tier 0 roles and extra Farmers |
| 3 | Run `/sv prosperity set 50` | Wait for next villager birth or `/sv roster` on next join event — Cleric or Fisherman now eligible |
| 4 | Run `/sv prosperity set 100` | Shepherd and Butcher become eligible at next birth |
| 5 | Run `/sv prosperity set 150` | Leatherworker and Toolsmith become eligible |
| 6 | Run `/sv prosperity set 200` | Weaponsmith, Armorer, Fletcher, Mason all become eligible |
| 7 | Kill the Toolsmith while prosperity is 140 | At next birth, a Farmer is assigned (not a Toolsmith — threshold not met) |
| 8 | Run `/sv prosperity set 150`, trigger another birth | Toolsmith role is now restored |

- [ ] At prosperity 0: only Librarian, Farmer, Guard assigned; extras become Farmer
- [ ] Cleric and Fisherman unlock at prosperity ≥ 50
- [ ] Shepherd and Butcher unlock at prosperity ≥ 100
- [ ] Leatherworker and Toolsmith unlock at prosperity ≥ 150
- [ ] Weaponsmith, Armorer, Fletcher, Mason unlock at prosperity ≥ 200
- [ ] A role lost while prosperity is below its tier threshold is replaced with Farmer, not restored

---

## Phase 2 — Stockpile Commands

**Goal:** Confirm the chest network is registered and the stockpile commands read and write real chests.

**Setup:** Stay within 64 blocks of the village. Find the stockpile chests (storehouse area).

| # | What to do | What to look for |
|---|---|---|
| 1 | Run `/sv stockpile` | Lists items currently in the chests (may be empty) |
| 2 | Run `/sv stockpile add bread 20` | Open a stockpile chest — 20 bread physically inside |
| 3 | Run `/sv stockpile` | Shows `bread: 20` |
| 4 | Run `/sv stockpile set bread 5` | Open the chest — bread count is exactly 5 |
| 5 | Run `/sv stockpile clear` | Open the chest — completely empty |
| 6 | Run `/sv stockpile` | Returns empty or nothing |
| 7 | Manually put 10 iron ingots in a stockpile chest by hand | Run `/sv stockpile` — shows `iron_ingot: 10` |
| 8 | Put items in a **second** stockpile chest | Run `/sv stockpile` — totals across both chests are combined |

- [ ] 1 — `/sv stockpile` reads chests without error
- [ ] 2–3 — `add` deposits into a physical chest
- [ ] 4 — `set` corrects the count exactly
- [ ] 5–6 — `clear` empties all chests
- [ ] 7 — player hand-placed items are visible via the command
- [ ] 8 — multiple chests aggregate into a single total

---

## Phase 3 — Hunger System

**Goal:** Confirm hunger depletes, villagers seek food, and starvation damages health.

**Setup:** Run `/sv stockpile add bread 64` so villagers have food to start. Enable `/sv debug`.

| # | What to do | What to look for |
|---|---|---|
| 1 | Run `/sv roster` | Note the hunger values for a Guard and a Librarian |
| 2 | Wait 2–3 in-game minutes, run `/sv roster` again | Guard hunger dropped more than Librarian hunger |
| 3 | Run `/sv stockpile clear` to empty all food | Watch the debug messages — a villager should say HUNGRY |
| 4 | Watch that villager | They stop their job and walk toward a stockpile chest |
| 5 | Run `/sv stockpile add bread 20` | The hungry villager eats and returns to their job |
| 6 | Clear all food again, wait longer | Run `/sv roster` — HP starts dropping for unfed villagers |
| 7 | Run `/sv villager feed` | Hunger fills to max for all nearby villagers |
| 8 | Run `/sv villager heal` | HP restores to full, clears any damage from step 6 |

- [ ] 1–2 — Guard depletes hunger faster than Librarian
- [ ] 3–4 — HUNGRY state overrides job goal; villager walks to chest
- [ ] 5 — villager resumes job after eating
- [ ] 6 — empty food → passive health loss
- [ ] 7–8 — `feed` and `heal` commands work correctly

---

## Phase 4 — Health & Death

**Goal:** Confirm health loss triggers healing behavior and death removes the villager from the roster.

**Setup:** Have a Cleric in the village roster. Stock healing supplies: `/sv stockpile add glass_bottle 10`.

| # | What to do | What to look for |
|---|---|---|
| 1 | Clear all food (`/sv stockpile clear`), wait until a villager is low HP | Run `/sv roster` — see reduced HP |
| 2 | Watch the low-HP villager | They enter SEEK_HEALING and walk toward the Cleric |
| 3 | Refeed with `/sv stockpile add bread 32` | Villager recovers, resumes job |
| 4 | Run `/sv threat trigger`, let a Guard take damage in combat | Cleric should path to the injured Guard after the fight |
| 5 | Run `/sv threat clear` | |
| 6 | Kill a villager directly (sword) | Run `/sv roster` — that villager is gone from the list |
| 7 | Wait and watch | Village should eventually flag the vacant role for replacement |

- [ ] 1–2 — low HP triggers SEEK_HEALING; villager walks to Cleric
- [ ] 3 — villager recovers and resumes work
- [ ] 4 — Cleric heals Guards after a fight
- [ ] 6 — death removes villager from roster permanently
- [ ] 7 — vacant role is flagged for replacement

---

## Phase 5 — NeedQueue

**Goal:** Confirm the Librarian posts shortage requests and villagers accept and complete them.

**Setup:** Let the village run in full simulation for at least 5 minutes with a sparse stockpile.

| # | What to do | What to look for |
|---|---|---|
| 1 | Run `/sv queue` after 5 minutes | At least one open request visible (shortage the Librarian detected) |
| 2 | Watch the queue over time | A villager accepts it — status moves to in-progress |
| 3 | Wait longer | Completed request disappears from the queue |
| 4 | Clear all food, wait 2 minutes | Run `/sv queue` — expect a `NEED_FOOD_BOOST` request |
| 5 | Drain the Cleric's healing supply, hurt a villager | Run `/sv queue` — expect a `NEED_HEALING` request |
| 6 | Run `/sv queue clear` | All open requests gone; no crash |
| 7 | Save and rejoin | Run `/sv queue` — in-progress requests survived the restart |

- [ ] 1 — Librarian posts shortage requests automatically
- [ ] 2–3 — requests move from open → in-progress → done
- [ ] 4 — `NEED_FOOD_BOOST` appears when food is critically low
- [ ] 5 — `NEED_HEALING` appears when a villager is injured
- [ ] 6 — `queue clear` works without crash
- [ ] 7 — queue persists across restart

---

## Phase 6 — Food Chain

**Goal:** Confirm each food-chain role produces its output and deposits it to the stockpile.

**Setup:** Run `/sv stockpile clear` so you can watch counts rise from zero. Enable `/sv debug`.

### Farmer
| # | What to do | What to look for |
|---|---|---|
| 1 | Watch the Farmer villager | They till soil, plant, and harvest crops |
| 2 | Run `/sv stockpile` after a harvest | `bread`, `wheat`, `carrot`, or `potato` count increased |
| 3 | Wait until crops are immature | Farmer switches to chopping trees — logs and saplings appear in stockpile |

- [ ] Farmer harvests and deposits crops/bread
- [ ] Farmer replants after harvesting
- [ ] Farmer chops trees as subrole and deposits logs/saplings

### Fisherman
| # | What to do | What to look for |
|---|---|---|
| 4 | Watch the Fisherman | They path to water and fish |
| 5 | Run `/sv stockpile` after a trip | `cooked_cod` or `cooked_salmon` count increased |
| 6 | Run `/sv stockpile` after more time | `sand`, `gravel`, or `flint` count increased from subrole gathering |

- [ ] Fisherman deposits cooked fish
- [ ] Fisherman deposits sand/gravel/flint from water-edge subrole

### Shepherd & Butcher
| # | What to do | What to look for |
|---|---|---|
| 7 | Watch the Shepherd near an animal pen | They shear sheep and cull animals |
| 8 | Run `/sv stockpile` | `wool`, `feathers`, `leather` counts rising |
| 9 | Trigger `NEED_FOOD_BOOST`: `/sv stockpile set cooked_beef 0` and drain all cooked meat | Butcher posts the request; Shepherd produces more raw meat |
| 10 | Run `/sv stockpile` | `cooked_beef` or other cooked meat count rising via Butcher |

- [ ] Shepherd deposits wool, feathers, leather, raw meat
- [ ] NEED_FOOD_BOOST causes Shepherd to increase output
- [ ] Butcher converts raw meat to cooked meat

### Leatherworker
| # | What to do | What to look for |
|---|---|---|
| 11 | Add leather: `/sv stockpile add leather 16` | |
| 12 | Wait, then run `/sv stockpile` | `leather_helmet`, `leather_chestplate`, `leather_leggings`, or `leather_boots` count > 0 |

- [ ] Leatherworker produces leather armor pieces from stockpile leather

---

## Phase 7 — Defense Supply Chain

**Goal:** Confirm Weaponsmith, Armorer, and Fletcher produce their items from stockpile inputs.

**Setup:** Stock the raw materials each role needs.

```
/sv stockpile add iron_ingot 32
/sv stockpile add iron_ore 16
/sv stockpile add coal 16
/sv stockpile add feathers 32
/sv stockpile add oak_log 32
/sv stockpile add flint 32
```

### Weaponsmith
| # | What to do | What to look for |
|---|---|---|
| 1 | Wait, then run `/sv stockpile` | `iron_sword` or `iron_axe` count > 0 |
| 2 | Drain all ingots: `/sv stockpile set iron_ingot 0` but leave raw ore | Weaponsmith smelts ore — ingot count rises before weapon is crafted |
| 3 | Add both ore and ingots scarce, add a second profession | Weaponsmith gets ingots before Armorer does |

- [ ] Weaponsmith crafts swords and axes from ingots
- [ ] Weaponsmith smelts raw ore when ingot supply is low
- [ ] Weaponsmith has smelt priority over Armorer

### Armorer
| # | What to do | What to look for |
|---|---|---|
| 4 | Wait, then run `/sv stockpile` | `iron_helmet`, `iron_chestplate`, `iron_leggings`, or `iron_boots` count > 0 |
| 5 | Check Armorer only smelts when ore is plentiful | With scarce ore, only Weaponsmith smelts |

- [ ] Armorer crafts iron armor pieces
- [ ] Armorer smelt subrole only activates when ore is abundant

### Fletcher
| # | What to do | What to look for |
|---|---|---|
| 6 | Wait, then run `/sv stockpile` | `arrow` count > 0, capped at 64 |
| 7 | Drain feathers: `/sv stockpile set feather 0` | Run `/sv queue` — `NEED_MATERIALS` request for feathers appears |

- [ ] Fletcher produces arrows up to a 64-arrow buffer
- [ ] Fletcher posts NEED_MATERIALS when feather or flint supply is low

---

## Phase 8 — Guard Defense

**Goal:** Confirm Guards patrol correctly, THREAT_ALERT works, and civilians shelter.

**Setup:** Have at least one Guard in the roster. Stand near the village in daytime.

| # | What to do | What to look for |
|---|---|---|
| 1 | Watch the Guard during the day | They walk a patrol route around the village perimeter |
| 2 | Watch the patrol closely | Guard passes through the stockpile chest area on every loop |
| 3 | Run `/sv threat trigger` | Non-combat villagers start walking toward the Bell |
| 4 | Watch the Guards | One Guard stays at the stockpile (chest guardian); others engage |
| 5 | Spawn a hostile mob near the stockpile | Guard prioritizes the mob near chests over one farther away |
| 6 | Run `/sv threat clear` | Guards return to patrol; civilians resume jobs |
| 7 | Leave a mob alive and do nothing | Alert auto-clears after 60 seconds if no threats remain |
| 8 | Let a Guard take combat damage | After the fight, Cleric should path to the Guard |

- [ ] Guards patrol the village perimeter during the day
- [ ] Stockpile chest cluster is a mandatory patrol waypoint
- [ ] THREAT_ALERT shelters non-combat villagers at the Bell
- [ ] One Guard per Bell holds position at stockpile during alert
- [ ] Guards prioritize threats near stockpile first
- [ ] Alert clears manually and auto-clears after 60 seconds
- [ ] Cleric heals Guards after combat

---

## Phase 9 — Day/Night Cycle

**Goal:** Confirm villagers sleep at night and Guard night-rotation behavior is correct.

**Setup:** Set time to dusk with `/time set 13000` and watch the transition.

| # | What to do | What to look for |
|---|---|---|
| 1 | Set time to night: `/time set 13000` | Non-Guard villagers walk home and sleep |
| 2 | Watch Toolsmith and Mason specifically | They do not go out to mine or quarry |
| 3 | Watch the Guards | One or two remain active; night-rotation Guard stays near stockpile |
| 4 | Watch the Cleric | Still moving/available, not sleeping |
| 5 | Clear all food, wait | A hungry villager should wake and eat before going back to sleep |
| 6 | Set time to day: `/time set 1000` | All villagers resume their jobs |

- [ ] Non-Guard villagers sleep at night
- [ ] Toolsmith and Mason do not gather resources at night
- [ ] Guards remain active on rotation; night Guard stations at stockpile
- [ ] Cleric stays available at night
- [ ] Hungry villager wakes to eat before sleeping

---

## Phase 10 — Iron Golem

**Goal:** Confirm the golem commission conditions, behavior, and replacement logic.

**Setup:** Start with a clean state: `/sv prosperity set 0` and `/sv stockpile clear`.

| # | What to do | What to look for |
|---|---|---|
| 1 | Run `/sv golem list` | No golems listed |
| 2 | Add 36 ingots but keep prosperity at 0: `/sv stockpile set iron_ingot 36` | Wait 5 seconds — no golem spawns (prosperity too low) |
| 3 | Set prosperity: `/sv prosperity set 250` | Within ~2 seconds, Armorer consumes ingots and golem spawns |
| 4 | Run `/sv golem list` | One golem UUID listed with full HP |
| 5 | Open a stockpile chest | Confirm iron_ingot count dropped by 36 |
| 6 | Watch the golem during peaceful time | It idles at the storehouse entrance |
| 7 | Run `/sv threat trigger` | Golem activates and moves toward threats |
| 8 | Run `/sv threat clear` | Golem returns to storehouse |
| 9 | Kill the golem with `/kill` or in combat | Run `/sv golem list` — UUID removed; slot is vacant |
| 10 | Re-stock ingots and wait one in-game day | Librarian commissions a replacement after the cooldown |
| 11 | Try `/sv golem spawn` when already at cap | Command refuses and prints the cap message |

- [ ] No golem without both conditions met
- [ ] Golem spawns when prosperity ≥ 250 AND ingots ≥ 36
- [ ] 36 ingots are consumed from the stockpile on spawn
- [ ] Golem idles at storehouse; activates on THREAT_ALERT; returns after
- [ ] Golem does not leave village boundary
- [ ] Death removes UUID; replacement commissioned after one-day cooldown
- [ ] `/sv golem spawn` respects cap

---

## Phase 11 — Mason Build System

**Goal:** Confirm Mason quarries materials and executes build tasks from the queue.

**Setup:** Clear the build queue: `/sv build clear`. Stock a pickaxe: `/sv stockpile add iron_pickaxe 1`.

| # | What to do | What to look for |
|---|---|---|
| 1 | Watch the Mason when the queue is empty | They go out and quarry — cobblestone/gravel/sand appears in stockpile |
| 2 | Remove the pickaxe: `/sv stockpile set iron_pickaxe 0` | Run `/sv queue` — `NEED_TOOLS` request appears |
| 3 | Re-add the pickaxe | Mason resumes quarrying |
| 4 | Check `/sv build list` | Shows any queued tasks |
| 5 | Wait for Mason to execute a task | They fetch materials from stockpile, path to the site, and place the block |
| 6 | If a `PLACE_CHEST` task completes | A new chest appears in the world; `/sv stockpile` shows it in the network |
| 7 | Run `/sv build clear` | Queue empties without crash |

- [ ] Mason quarries cobblestone, gravel, and sand when queue is empty
- [ ] Mason posts NEED_TOOLS when no pickaxe is available
- [ ] Mason executes build queue tasks (fetch → path → place)
- [ ] PLACE_CHEST task registers the new chest in the stockpile network
- [ ] `/sv build clear` works without crash

---

## Phase 12 — Prosperity Score

**Goal:** Confirm the prosperity score changes for the right reasons and gates spawns.

| # | What to do | What to look for |
|---|---|---|
| 1 | Run `/sv prosperity set 0` | `/sv status` shows 0 |
| 2 | Run `/sv prosperity add 100` | `/sv status` shows 100 |
| 3 | Hand-place items directly into a stockpile chest | `/sv status` — prosperity increments |
| 4 | Kill a villager | `/sv status` — prosperity drops |
| 5 | Clear all food, let villagers starve | `/sv status` — prosperity drops over time |

- [ ] `/sv prosperity set` and `add` work
- [ ] Player donation to a stockpile chest increases prosperity
- [ ] Villager death decreases prosperity
- [ ] Food shortage decreases prosperity

---

## Phase 13 — Abstract Simulation (Two-Mode)

**Goal:** Confirm the village keeps running while chunks are unloaded and reconciles correctly on return.

**Setup:** Note current stockpile counts and villager HP via `/sv roster` and `/sv stockpile`.

| # | What to do | What to look for |
|---|---|---|
| 1 | Walk more than 200 blocks away from the village | `/sv status` should show simulation mode = ABSTRACT (if reachable from that distance) |
| 2 | Wait 5 real-world minutes | |
| 3 | Walk back within 64 blocks | Village chunks reload; villagers appear |
| 4 | Run `/sv roster` | HP and hunger changed from what you noted — abstract sim was running |
| 5 | Run `/sv stockpile` | Counts changed — production and consumption occurred during absence |
| 6 | If a villager died during abstract sim | They are not present; roster reflects the death |
| 7 | Stock 36 ingots + prosperity ≥ 250, then walk away for 5 minutes | Return — golem may have spawned or abstract HP tracked correctly |

- [ ] Simulation mode switches to ABSTRACT when player leaves
- [ ] Abstract sim applies production/consumption to stockpile
- [ ] Villager HP and hunger change during abstract sim
- [ ] Deaths during abstract sim are applied on reconciliation
- [ ] Golem abstract HP tracked; death recorded if it occurred offscreen

---

## Phase 14 — Cleric & Player Healing

**Goal:** Confirm the Cleric heals villagers and heals the player without any menu.

**Setup:** Stock healing supplies: `/sv stockpile add glass_bottle 10`. Ensure a Cleric is in the roster.

| # | What to do | What to look for |
|---|---|---|
| 1 | Hurt yourself (fall damage or mob) | Walk into the village — Cleric walks toward you and heals you |
| 2 | No menu, no clicking required | Healing triggers on proximity alone |
| 3 | Deplete the Cleric's supply: `/sv stockpile set glass_bottle 0` | Walk in injured — Cleric cannot heal you |
| 4 | Hurt a villager (use `/sv threat trigger` and let a Guard get hit) | After the fight, Cleric paths to the Guard and heals |
| 5 | Let supplies run out | Run `/sv queue` — `NEED_MATERIALS` request from Cleric appears |

- [ ] Cleric heals the player on proximity, no interaction needed
- [ ] Depleted supply prevents Cleric from healing player
- [ ] Cleric paths to injured Guards after combat
- [ ] Cleric posts NEED_MATERIALS when supplies are low

---

## Phase 15b — Escort System

**Goal:** Confirm Guards accept NEED_ESCORT requests, accompany the requester, and return to patrol.

**Setup:** Have at least one Guard in the roster and a Mason or Toolsmith. Enable `/sv debug`.

| # | What to do | What to look for |
|---|---|---|
| 1 | Watch Guard during normal patrol | Guard walks the perimeter including stockpile checkpoint |
| 2 | Post a NEED_ESCORT manually or wait for Mason to post one | Run `/sv queue` — `NEED_ESCORT` appears |
| 3 | Watch a Guard | They accept and path toward the requesting villager |
| 4 | Watch the Guard + requester together | Guard moves with the requester; does not resume patrol |
| 5 | When the requester returns near the Bell | Guard breaks escort and resumes patrol route |

- [ ] Guard accepts NEED_ESCORT from queue
- [ ] Guard accompanies requester during escort
- [ ] Guard skips patrol while on escort duty
- [ ] Guard returns to patrol after escort completes

---

## Phase 15c — Cartographer System

**Goal:** Confirm the Cartographer surveys the village, scouts outside it, and queues build tasks.

**Setup:** Have a Cartographer in the roster. Set prosperity ≥ 150 for build planning.

| # | What to do | What to look for |
|---|---|---|
| 1 | Watch the Cartographer during the day | They walk outward waypoints around the village perimeter |
| 2 | Run `/sv queue` | After a scouting run, `NEED_ESCORT` may appear if the area is flagged |
| 3 | Set prosperity ≥ 150 and let the Cartographer run | Run `/sv build list` — a PLACE_CHEST task should appear |
| 4 | Confirm no duplicate PLACE_CHEST tasks | Only one PLACE_CHEST task exists at a time |

- [ ] Cartographer walks survey waypoints during daytime
- [ ] Cartographer posts NEED_ESCORT before scouting in unknown areas
- [ ] Cartographer queues PLACE_CHEST when prosperity ≥ 150 and queue is empty
- [ ] No duplicate build tasks are created

---

## Phase 15d — Merchant Shop

**Goal:** Confirm the Merchant opens a dynamic shop showing only items currently in stock.

**Setup:** Stock the village with several item types via `/sv stockpile add`.

| # | What to do | What to look for |
|---|---|---|
| 1 | Right-click the Merchant villager | Trade screen opens (not vanilla villager screen) |
| 2 | Run `/sv stockpile clear` then right-click Merchant | Trade screen is empty — no hardcoded trades |
| 3 | Add bread × 64: `/sv stockpile add bread 64` | Bread appears in trades at base price |
| 4 | Add bread × 8 more (low stock): `/sv stockpile add bread 8` | Bread price increases (low stock surcharge) |
| 5 | Add bread × 64 again (high stock) | Bread price decreases (high stock discount) |
| 6 | Watch the Merchant during full sim | They path toward you when you are within ~24 blocks |

- [ ] Right-click opens dynamic trade screen
- [ ] Empty stockpile → empty shop
- [ ] High stock (≥ 64 units) reduces price; low stock (≤ 8 units) increases it
- [ ] Merchant approaches player within 24 blocks

---

## Phase 15e — Prosperity Score Events

**Goal:** Confirm discrete events trigger correct prosperity changes.

| # | What to do | What to look for |
|---|---|---|
| 1 | Note current prosperity via `/sv status` | |
| 2 | Kill a villager | `/sv status` — prosperity drops by 30 |
| 3 | `/sv threat trigger` then `/sv threat clear` | Prosperity increases by 20 (threat defeated) |
| 4 | Hand-place an item in a stockpile chest | Prosperity increases by 5 (player donation) |
| 5 | Complete a Mason build task | Prosperity increases by 15 |

- [ ] Villager death: −30 prosperity
- [ ] Threat defeated: +20 prosperity
- [ ] Player donation to stockpile chest: +5 prosperity
- [ ] Structure built (Mason task complete): +15 prosperity

---

## Phase 15f — Personality Traits

**Goal:** Confirm villager personality traits affect escort and queue behavior.

**Setup:** Enable `/sv debug`. Observe villagers with different trait types.

| # | What to do | What to look for |
|---|---|---|
| 1 | Watch a BRAVE Toolsmith when ore is low | May attempt to mine without posting NEED_ESCORT |
| 2 | Watch a CAUTIOUS Toolsmith in the same situation | Always posts NEED_ESCORT before going out |
| 3 | Check a GREEDY Merchant's prices | Slightly higher base prices than neutral Merchant |

- [ ] BRAVE villagers have higher escort request threshold
- [ ] CAUTIOUS villagers always post NEED_ESCORT
- [ ] GREEDY Merchants apply a price multiplier

---

## Phase 15g — Village Memory

**Goal:** Confirm threat locations are recorded, flagged for Mason/Toolsmith, and cleared by Guards.

| # | What to do | What to look for |
|---|---|---|
| 1 | Run `/sv threat trigger` near a quarry site | Threat is recorded in village memory |
| 2 | Watch the Mason during the alert | Mason avoids going to the quarry while area is flagged |
| 3 | Run `/sv threat clear` | Guard clears the threat record |
| 4 | Watch the Mason after threat clears | Mason resumes quarrying in the previously flagged area |

- [ ] Threat locations are recorded during THREAT_ALERT
- [ ] Mason skips quarry positions in flagged threat areas
- [ ] Guard clearing a threat removes the flag from village memory

---

## Phase 15h — Skin Renderer

**Goal:** Confirm Guard and Merchant villagers render with custom profession textures and Merchants use the Wandering Trader appearance.

**Setup:** Ensure PNG textures exist at `assets/smartvillager/textures/entity/villager/profession/guard.png` and `merchant.png`. Without them the purple/black missing-texture placeholder will show.

| # | What to do | What to look for |
|---|---|---|
| 1 | Look at a Guard villager | Their profession overlay uses the custom Guard texture (or missing-texture if no PNG yet) |
| 2 | Look at a Merchant villager | Their body renders with the Wandering Trader texture |
| 3 | Check Merchant in a Plains village vs a Desert village | Plains Merchant shows WHITE tint; Desert Merchant shows CYAN tint |
| 4 | Right-click any non-Guard/non-Merchant villager | Vanilla profession texture renders normally — no broken layers |

- [ ] Guard villager shows custom profession texture (no crash)
- [ ] Merchant villager renders with Wandering Trader texture overlay
- [ ] Merchant robe tint matches the village's biome (plains=white, desert=cyan, savanna=orange, taiga=blue)
- [ ] Vanilla profession villagers are unaffected

---

## Phase 15 — Regression Scenarios

Run these after any significant code change to catch breakage across systems.

### Golem Commission
```
/sv prosperity set 250
/sv stockpile set iron_ingot 36
```
Wait ~2 seconds. **Expect:** golem spawns, 36 ingots consumed.

- [ ] Passes

### Food Shortage Cascade
```
/sv stockpile set bread 0
/sv stockpile set cooked_beef 0
/sv stockpile set cooked_chicken 0
/sv stockpile set cooked_mutton 0
/sv stockpile set cooked_porkchop 0
/sv stockpile set cooked_salmon 0
/sv stockpile set cooked_cod 0
```
Wait. **Expect:** villagers go hungry → NEED_FOOD_BOOST posted → Shepherd increases output → Butcher cooks → food returns.

- [ ] Passes

### Defense Chain
```
/sv threat trigger
```
Watch. **Expect:** civilians shelter at Bell, one Guard holds stockpile, others engage. Run `/sv threat clear` → Guards return to patrol.

- [ ] Passes

### NeedQueue Routing
Let full simulation run 5+ minutes.
```
/sv queue
```
**Expect:** at least one open request that the Librarian detected.

- [ ] Passes

### Clean Reset
```
/sv stockpile clear
/sv queue clear
/sv build clear
/sv prosperity set 0
```
**Expect:** no crash; village continues ticking from zero state.

- [ ] Passes

### Abstract Sim Round-Trip
Note roster HP and stockpile counts, walk 200+ blocks away, wait 5 minutes, return.
**Expect:** HP/hunger/stockpile differ from start; state is consistent.

- [ ] Passes
