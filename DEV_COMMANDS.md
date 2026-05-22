# SmartVillager Dev Commands

All commands use the `/sv` prefix. Commands require a village within 256 blocks of the player.

---

## Village Inspection

### `/sv status`
Full village overview in one snapshot.

Shows: village ID (truncated), simulation mode, prosperity score (with golem threshold marker), threat alert state, golem count vs cap, roster breakdown by profession, NeedQueue summary, build queue task count, and active shortages.

---

### `/sv roster`
Lists every villager in the nearest village roster.

For each villager shows their profession, HP, hunger, and UUID prefix. If the villager entity is not currently loaded (abstract sim), shows abstract HP instead of live values.

---

### `/sv nearby [radius]`
Lists all Villager entities within `radius` blocks (default 16, max 128).

Shows profession, HP, hunger, and UUID prefix. This scans physical entities — it will show villagers from any village or unregistered villagers. Use `/sv roster` to see the village roster specifically.

---

## Villager State

### `/sv villager heal`
Heals all roster villagers within 64 blocks to full HP.

Updates both the mod `VillagerHealth` attachment and the vanilla entity health. Clears the `seekingHealing` flag.

### `/sv villager feed`
Fills the hunger bar of all roster villagers within 64 blocks to max.

Updates the `VillagerHunger` attachment directly.

### `/sv debug`
Toggle villager thought broadcasting (on/off).

When on, every 5 seconds any villager within 32 blocks broadcasts a one-line status message showing their current priority state (idle, hungry, injured, seeking healing). Toggle again to turn off.

---

## Stockpile

### `/sv stockpile`
Shows all items currently in the village stockpile, sorted by quantity descending.

During full simulation reads from physical chest contents. During abstract sim reads from the snapshot map.

### `/sv stockpile add <item> <amount>`
Deposits items into the stockpile. Item name can be bare (`iron_ingot`) or namespaced (`minecraft:iron_ingot`). Caps at 9999 per call.

### `/sv stockpile set <item> <amount>`
Sets the stockpile count for a specific item to exactly `amount`. Withdraws all existing stock of that item first, then deposits the target. Amount can be 0 to fully remove an item.

### `/sv stockpile clear`
Removes all items from the stockpile (all item types, all quantities).

---

## NeedQueue

### `/sv queue`
Lists all open and in-progress NeedRequests.

Each entry shows: request type, item (if applicable), priority level, and poster UUID prefix.

### `/sv queue clear`
Cancels all open (unaccepted) NeedRequests.

In-progress requests (already accepted by a villager) are not cancelled — those must resolve naturally or expire.

---

## Defense

### `/sv threat trigger`
Manually activates THREAT_ALERT for the nearest village.

Civilians will start sheltering toward the Bell. Guards will engage. Useful for testing the shelter and combat response without spawning mobs.

### `/sv threat clear`
Manually clears an active THREAT_ALERT.

The alert would normally clear after 60 seconds with no hostile mobs detected. Use this to reset immediately without waiting.

---

## Prosperity

### `/sv prosperity add <amount>`
Adds `amount` to the village prosperity score. Range 1–99999.

### `/sv prosperity set <amount>`
Sets the village prosperity score to exactly `amount`. Range 0–99999.

Use `/sv prosperity set 250` to immediately meet the iron golem commissioning threshold, then stock iron ingots with `/sv stockpile set iron_ingot 36` to trigger a golem spawn on the next IronGolemSystem tick.

---

## Iron Golems

### `/sv golem list`
Lists all village-owned golem UUIDs.

For each golem shows live HP (if the entity is loaded) or abstract HP (if in abstract sim).

### `/sv golem spawn`
Force-spawns a village-owned iron golem at the storehouse, bypassing the prosperity and iron ingot cost checks.

Respects the golem cap — will refuse if the village is already at cap. Use `/kill` on the existing golem first if needed.

---

## Build Queue

### `/sv build list`
Shows all pending build tasks.

Each entry shows: task index, task type, target block position, and progress (ticks done / ticks required).

### `/sv build clear`
Removes all tasks from the build queue.

---

## Common Testing Workflows

### Trigger a golem commission
```
/sv prosperity set 250
/sv stockpile set iron_ingot 36
```
The IronGolemSystem checks conditions every 40 ticks (~2 seconds). The Armorer will consume the ingots and spawn a golem.

### Simulate a food shortage
```
/sv stockpile set bread 0
/sv stockpile set cooked_beef 0
/sv stockpile set cooked_chicken 0
/sv stockpile set cooked_mutton 0
/sv stockpile set cooked_porkchop 0
/sv stockpile set cooked_salmon 0
/sv stockpile set cooked_cod 0
```
Villagers will deplete hunger and trigger `NEED_FOOD_BOOST` → Shepherd increases output → Butcher cooks → food replenishes. Watch with `/sv debug`.

### Test the defense chain
```
/sv threat trigger
```
Then observe: civilians path toward the Bell, Guards engage, and after clearing run `/sv threat clear` to reset.

### Verify NeedQueue routing
```
/sv queue
```
Run after a few minutes of full simulation to see what shortages the Librarian has detected and which requests villagers have accepted.

### Reset a village for a clean test
```
/sv stockpile clear
/sv queue clear
/sv build clear
/sv prosperity set 0
```
