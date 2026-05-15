# SmartVillager – Build Roadmap

Each feature is built on its own branch and merged to `main` when stable before the next one starts.

---

## Branch Plan

### Phase 1 — Foundation
Nothing in Phase 2 or later runs correctly until all of these are stable.

| # | Branch | What it covers | Status |
|---|---|---|---|
| 1 | `feature/professions-workblocks` | Register Guard and Merchant as the only two custom professions; override behavior entry points for all vanilla professions; workblocks serve as job site anchors only, not profession gates | In Review |
| 2 | `feature/village-registration` | Detect all naturally spawning vanilla villages and register them with the mod; assign starting rosters and biome-based Merchant colors at registration time; implement two-mode simulation — full simulation (chunks loaded, villagers physically move) when player is within ~128 blocks, abstract simulation (chunks unloaded, village state tracked as data with batch updates every few minutes) when player is far away; state is reconciled and full simulation resumes when player returns | In Review |
| 3 | `feature/village-stockpile` | Shared chest/data structure attached to the village; Librarian as coordinator — tracks supply levels, flags shortages, drives NeedQueue requests | In Review |
| 4 | `feature/villager-backpack` | Per-villager inventory with 15 slots + 4 armor slots — holds food, tools, carried items, and equipped gear | In Review |
| 5 | `feature/hunger-system` | Hunger value per villager; depletion rate varies by role (Guard and active gatherers burn faster); eat behavior routing to shared food supply | In Review |
| 6 | `feature/health-system` | Combat, environmental, and starvation damage; permanent death and role replacement; SEEK_HEALING behavior | |
| 7 | `feature/need-queue` | Core village communication — NeedRequest structure (type, urgency, reward, poster); queue scoped per village; idle-state polling; request acceptance and completion | |

---

### Phase 2 — Core Behaviors
Build these once the foundation is solid. Branches 11–14 can be developed in parallel once branch 7 is merged.

| # | Branch | What it covers | Status |
|---|---|---|---|
| 8 | `feature/guard-defense` | Guard perimeter patrol routes; threat detection; THREAT_ALERT broadcast; non-combat villagers enter SHELTER state; Cleric notified after fight | |
| 9 | `feature/day-night-cycle` | Sleep enforcement for all non-Guard villagers; Guard night rotation; no resource gathering runs allowed after dark | |
| 10 | `feature/cleric-healing` | Villager healing triggered by health threshold or NEED_HEALING request; potion brewing subrole; player proximity healing from same supply pool | |
| 11 | `feature/food-chain` | Farmer (crops primary + wood subrole), Fisherman (fish primary + sand/clay/flint subrole), Shepherd (animal tending primary + byproduct supply subrole), Butcher (meat processing primary + husbandry subrole), Leatherworker (leather goods primary + flex subrole) — all deposit to shared inventory | |
| 12 | `feature/toolsmith-mining` | Toolsmith primary tool crafting; mining subrole: reads Cartographer cave data, posts NEED_ESCORT if area is flagged, mines ore and coal, deposits surplus to shared inventory for Weaponsmith and Armorer | |
| 13 | `feature/defense-supply` | Weaponsmith (weapons primary + smelting subrole), Armorer (armor primary + emergency rearm subrole), Fletcher (arrows primary + material gathering subrole) — all equip Guards from shared inventory, coordinate to avoid duplicating smelt work | |
| 14 | `feature/mason-build` | Mason build queue check (fed by Librarian); fetch materials; pathfind to site; place blocks; quarrying subrole when queue is empty or materials are low; posts NEED_ESCORT if quarry area is flagged | |

---

### Phase 3 — Collaboration Systems
These systems connect the Phase 2 behaviors into a functioning village network.

| # | Branch | What it covers | Status |
|---|---|---|---|
| 15 | `feature/escort-system` | Guard responds to NEED_ESCORT from Toolsmith, Mason, Cartographer, and Fisherman; escort state machine (accept → accompany → protect → return to patrol) | |
| 16 | `feature/cartographer-system` | Cartographer mapping primary role; scouting subrole; threat flagging to Guards; cave and ore data shared with Toolsmith; build site plans fed to Librarian and Mason; village memory updates | |
| 17 | `feature/merchant-shop` | Merchant shop UI showing live village inventory only; dynamic pricing based on supply levels; Merchant supply monitor subrole posting NEED_RESTOCK to NeedQueue | |

---

### Phase 4 — Economy and Growth

| # | Branch | What it covers | Status |
|---|---|---|---|
| 18 | `feature/prosperity-score` | Prosperity score tracking; population gating (new villagers require food + beds + available role); Librarian unlocking advanced recipes at thresholds | |
| 19 | `feature/personality-reputation` | Villager personality traits (brave, cautious, greedy, generous); NeedQueue decision bias; inter-villager trust and reputation; player reputation score affecting Merchant prices and Guard behavior | |
| 20 | `feature/village-memory` | Village tracks threat locations and villager death sites; Toolsmith and Mason avoid flagged areas until Guard clears them; Cartographer maintains and shares the memory map | |

---

### Phase 5 — Polish

| # | Branch | What it covers | Status |
|---|---|---|---|
| 21 | `feature/villager-nametags` | Each villager displays their profession as a nametag on hover — Guard, Merchant, Farmer, Mason, Librarian, etc. | |
| 22 | `feature/villager-skins` | Custom skins for Guard and Merchant; Merchant uses the Wandering Trader model with biome-based robe colors assigned at village registration; Guard gets a unique skin; all vanilla professions keep their vanilla skins | |
| 23 | `feature/creative-tab` | SmartVillager creative tab grouping all mod items in one place in the creative inventory | |

---

## Status Types

| Status | Description |
|---|---|
| _(empty)_ | Not started yet |
| In Progress | Currently in development |
| In Review | Final checks before merge |
| Done | Merged and released |

---

## Notes

- **Branches 1–7** are the foundation — nothing else runs correctly until all of these are merged.
- **Branch 7 (NeedQueue)** is the backbone of the entire mod — take extra time here. Every system in Phase 2 and beyond depends on it.
- **Branches 11–14** can be developed in parallel once Phase 1 is stable — they are independent of each other.
- **Branch 14 (Mason build)** should be the last of the Phase 2 branches to finish — it pulls on the tool supply (12) and defense supply (13) chains which need to be flowing first.
- **Branch 16 (Cartographer)** ties together multiple chains — build it after the chains it feeds (food, toolsmith, mason) are working.
- **Branch 17 (Merchant shop)** is the only player-facing trade feature — build it after the production chains are producing real inventory.
- Inter-village trade caravans are a stretch goal and not listed — add a branch if the core is stable and there is time.
