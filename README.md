# SmartVillager

**Version:** 0.2.4 | **NeoForge:** 26.1.2.44-beta | **MC:** 26.1.2

A NeoForge mod that overhauls Minecraft villagers into a self-sustaining society. Villagers have real jobs, communicate through a shared economy, defend their village, and expand it over time — with or without the player.

Vanilla villager trading is completely removed. The village runs its own internal economy.

---

## What it does

- **Economy** — Villagers produce, deposit, and withdraw items from a physical shared chest stockpile. The Librarian monitors supply levels and coordinates production through a NeedQueue.
- **Defense** — Guards run perimeter patrols with mandatory stockpile waypoints, respond to threats, and call for civilian shelter. Iron golems are commissioned by the village when prosperity and iron supply thresholds are met.
- **Expansion** — The Cartographer maps the village and plans build sites. The Mason executes builds from a queue, quarrying materials when stock is low.
- **Hunger & Health** — Every villager has hunger and health that override their job goals. Starvation cascades are real — a village with too many mouths and not enough Farmers will visibly deteriorate.
- **Two-mode simulation** — Full AI runs when a player is within ~128 blocks. Abstract batch simulation keeps the village economy running when chunks are unloaded, with reconciliation on return.

---

## Villager roles

| Role | Group | Primary job |
|---|---|---|
| Librarian | Admin | Monitors stockpile, manages NeedQueue and build queue, drives prosperity |
| Farmer | Food | Crops, bread, wood-gathering subrole |
| Fisherman | Food | Fish, sand/gravel/flint subrole |
| Shepherd | Food | Animals, wool, meat, leather, feathers |
| Butcher | Food | Cooks raw meat; posts NEED_FOOD_BOOST when supply is critical |
| Leatherworker | Food | Leather armor for early Guard equipment |
| Toolsmith | Support | Crafts tools; mines ore as subrole |
| Cleric | Support | Heals villagers and players; brews potions as subrole |
| Mason | Build | Executes build queue; quarries materials as subrole |
| Cartographer | Build | Maps territory, plans build sites, flags threats |
| Weaponsmith | Defense supply | Crafts swords and axes for Guards |
| Armorer | Defense supply | Crafts armor for Guards |
| Fletcher | Defense supply | Crafts arrows and bows for Guards |
| Guard *(custom)* | Defense | Patrols, fights threats, escorts villagers outside village boundary |
| Merchant *(custom)* | Trade | Player shop UI showing live village stock; prices adjust with supply |
| Nitwit | Passive | Eats food, contributes nothing — intentional drag on the economy |

---

## Player interaction

The player is an outsider. The village runs without them.

- **Merchant** — opens a shop showing only what the village currently has in stock; prices shift with supply
- **Cleric** — heals the player on proximity (same supply pool as villager healing — a depleted Cleric has nothing)
- **Librarian** — offers quests sourced from active NeedQueue items when player reputation is high enough
- **Donate** — deposit items directly into any stockpile chest to boost prosperity and restock the Merchant

---

## Completed systems

Hunger, health, NeedQueue, village registry, guard defense, patrol, day/night cycle, Cleric healing, iron golem defense, food chain (Farmer, Fisherman, Shepherd, Butcher, Leatherworker), defense supply (Weaponsmith, Armorer, Fletcher), Mason build system (quarrying, build queue, storehouse chest expansion).

---

## Dev commands

All commands use `/sv` in-game. See [DEV_COMMANDS.md](DEV_COMMANDS.md) for full reference.

```
/sv status          — full village snapshot
/sv roster          — villager list with HP and hunger
/sv stockpile       — view/add/set stockpile items
/sv queue           — inspect or clear the NeedQueue
/sv threat          — manually trigger or clear a threat alert
/sv golem           — list or spawn village-owned iron golems
/sv prosperity      — adjust prosperity score
/sv build           — inspect or clear the build queue
/sv debug           — toggle villager thought broadcasting
/sv villager heal   — heal all nearby roster villagers
/sv villager feed   — fill hunger for all nearby roster villagers
```

---

## Building

```sh
./gradlew build
```

Output jar is in `build/libs/`.
