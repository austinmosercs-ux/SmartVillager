# SmartVillager – Build Roadmap

Each feature is built on its own branch and merged to `main` when stable before the next one starts.

---

## Branch Plan

| # | Branch | What it covers | Status |
|---|---|---| --- |
| 1  | `feature/professions-workblocks` | Register all villager classes, new POIs, workblocks | In Review |
| 2  | `feature/world-village-spawn` | Spawn exactly one village near world origin, force-load its chunks on world start so it stays simulated permanently | |
| 3  | `feature/village-stockpile` | Shared chest/data structure, Elder management | |
| 4  | `feature/villager-backpack` | Per-villager inventory with 15 slots + 4 armor slots — used to hold food, tools, carried items, and equipped gear | |
| 5  | `feature/hunger-system` | Hunger value, depletion by role, eat behavior | |
| 6  | `feature/health-system` | Damage, starvation damage, death + role replacement | |
| 7  | `feature/need-queue` | The core communication system | |
| 8  | `feature/defense-system` | Guard patrol, threat alerts, Hunter rally, shelter behavior | |
| 9  | `feature/day-night-cycle` | Sleep enforcement, night patrol rotation | |
| 10 | `feature/cleric-healing` | Villager + player healing tied to supply | |
| 11 | `feature/production-chain` | Toolsmith / Weaponsmith / Armorer ore→tool→merchant loop | |
| 12 | `feature/prosperity-score` | Population gating, growth unlocks | |
| 13 | `feature/personality-reputation` | Traits, inter-villager trust, village memory | |
| 14 | `feature/builder-expansion` | Build queue, Builder AI, village growth | |
| 15 | `feature/villager-nametags` | Villagers display their profession as a nametag above their head — visible on hover, showing names like "Builder", "Elder", etc. | |
| 16 | `feature/villager-skins` | Custom skins for all 5 new villager professions — Builder, Miner, Hunter, Guard, and Elder each get a unique appearance | |
| 17 | `feature/workblock-textures` | Custom textures for all 5 workblocks — replace placeholder vanilla textures with proper PNGs | |
| 18 | `feature/workblock-recipes` | Crafting recipes for all 5 workblocks so they can be obtained in survival | |

---

## Status Types

| Status | Description |
|---|---|
| _(empty)_ | Not started yet |
| In Progress | Currently in development |
| In Review | Final checks before merge |
| Done | Merged and released |

## Notes

- Branches 1–6 are the foundation. Nothing else runs correctly until these are stable.
- The NeedQueue (branch 7) is the backbone of the mod — take extra time here before moving on.
- Builder expansion (branch 14) depends on the economy and NeedQueue being solid. Build it last.
- Inter-village trade caravans are a stretch goal and not listed above — add a branch if the core systems are stable and there is time.
