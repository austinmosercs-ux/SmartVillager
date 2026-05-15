# SmartVillager – Build Roadmap

Each feature is built on its own branch and merged to `main` when stable before the next one starts.

---

## Branch Plan

| # | Branch | What it covers | Status |
|---|---|---| --- |
| 1 | `feature/professions-workblocks` | Register all villager classes, new POIs, workblocks | In Review |
| 2 | `feature/village-inventory` | Shared chest/data structure, Elder management | |
| 3 | `feature/hunger-system` | Hunger value, depletion by role, eat behavior | |
| 4 | `feature/health-system` | Damage, starvation damage, death + role replacement | |
| 5 | `feature/need-queue` | The core communication system | |
| 6 | `feature/defense-system` | Guard patrol, threat alerts, Hunter rally, shelter behavior | |
| 7 | `feature/day-night-cycle` | Sleep enforcement, night patrol rotation | |
| 8 | `feature/cleric-healing` | Villager + player healing tied to supply | |
| 9 | `feature/production-chain` | Toolsmith / Weaponsmith / Armorer ore→tool→merchant loop | |
| 10 | `feature/prosperity-score` | Population gating, growth unlocks | |
| 11 | `feature/personality-reputation` | Traits, inter-villager trust, village memory | |
| 12 | `feature/builder-expansion` | Build queue, Builder AI, village growth | |
| 13 | `feature/workblock-textures` | Custom textures for all 5 workblocks — replace placeholder vanilla textures with proper PNGs | |
| 14 | `feature/workblock-recipes` | Crafting recipes for all 5 workblocks so they can be obtained in survival | |

---

## Status Types

| Status | Description |
|---|---|
| _(empty)_ | Not started yet |
| In Progress | Currently in development |
| In Review | Final checks before merge |
| Done | Merged and released |

## Notes

- Branches 1–4 are the foundation. Nothing else runs correctly until these are stable.
- The NeedQueue (branch 5) is the backbone of the mod — take extra time here before moving on.
- Builder expansion (branch 12) depends on the economy and NeedQueue being solid. Build it last.
- Inter-village trade caravans are a stretch goal and not listed above — add a branch if the core systems are stable and there is time.
