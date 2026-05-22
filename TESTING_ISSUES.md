# SmartVillager Fix Roadmap

## Priority 1 — Bug Fixes

### Farmer AI not working
- [ ] Farmers are not pathfinding to pre-generated village farms
- [ ] Farmers are not harvesting or collecting resources from existing farmland
- **Root cause to investigate:** `FarmerSystem.java` — check that the system is scanning for vanilla farmland/crops in range, not just mod-placed plots; confirm the villager's `PATH_TO_STOCKPILE` and crop-seek behaviors are being registered into the Brain correctly via `ProfessionBehaviorRegistry`

### Guard texture broken
- [ ] Guard villager texture/model is rendering incorrectly
- **Note:** User will handle this one — flagged for awareness only

---

## Priority 2 — Trading System Rework + Merchant Removal

Replace the Merchant custom profession with vanilla-style per-villager trading, driven by each villager's actual needs and surpluses.

### Remove Merchant
- [ ] Delete `MerchantSystem.java` and remove it from `ProfessionBehaviorRegistry`
- [ ] Remove the `smartvillager:merchant` profession entry from `ModProfessions.java`
- [ ] Remove `MerchantColor.java`, `MerchantColorLayer.java`, and `SmartVillagerRenderer.java` — no longer needed without the Merchant model
- [ ] Remove `ClientModEvents.java` renderer wiring or replace with a no-op if the class is still needed for other client events
- [ ] Remove all Merchant references from `VillageDetector.chooseProfession()`, `SmartVillage`, and `CLAUDE.md` Key Files table
- [ ] Update `DEV_COMMANDS.md` and `TEST_CHECKLIST.md` to remove any Merchant-specific entries

### New Trading Design
- Any villager can trade with the player directly — no dedicated trade NPC
- Villagers can also buy and sell between each other using the same system
- Villagers initiate trades based on their own needs and surpluses — the AI decides what to offer and what to request, not a hardcoded table
- If a villager **needs** an item they cannot produce themselves, they offer emeralds to buy it from the player or another villager
- If a villager has a **surplus** of an item, they offer to sell it to the player or another villager for emeralds
- Trade offers are generated live from each villager's personal inventory and the village stockpile — no static trade lists

### Emerald Economy
- Each villager starts with **10 emeralds** in their personal inventory as seed money
- The village stockpile starts with 0 emeralds — the starting 10 per villager is all there is on day one
- **Emeralds enter the economy two ways:**
  - Toolsmith mines emerald ore on mining runs and deposits raw emeralds to the stockpile (primary growth source)
  - Player sells items to villagers or donates emeralds directly to the stockpile
- **Emeralds leave the economy** when the player buys from a villager and does not spend them back
- The stockpile acts as the village treasury — villagers withdraw from it before spending personal emeralds
- This creates real scarcity pressure: the economy cannot grow beyond what the Toolsmith can mine and what the player reinvests

### Tasks
- [ ] Update `VillagerInteractionHandler` to allow the vanilla trade screen to open per-villager instead of blocking all trades
- [ ] Build a `VillagerTradeEvaluator` that reads the villager's profession, personal inventory, and stockpile levels to generate dynamic buy/sell offers
- [ ] Wire the trade screen to show only dynamically generated offers — buy offers (villager pays emeralds) and sell offers (villager accepts emeralds)
- [ ] Ensure emerald flow is tracked so villagers do not offer more emeralds than they have
- [ ] Add to `NeedTypes`: `WANT_TO_BUY_FROM_PLAYER` so villagers can flag player-purchasable needs through the NeedQueue

---

## Priority 3 — Villager Autonomy Overhaul

Core redesign: villagers behave like new players starting from scratch, earning everything through real in-game actions.

### Profession Choice
- [ ] Remove fixed role-assignment-at-birth logic from `VillageDetector.chooseProfession()`
- [ ] Villagers choose their own profession based on village need AND personal preference — implement a preference system (weighted random biased toward village gaps)
- [ ] No hard cap on any profession count — multiple Librarians, multiple Farmers, etc. are all valid if that is what the village needs or wants
- [ ] Update `VillageDetector` and `ProfessionBehaviorRegistry` to support dynamic, uncapped profession distribution

### Play Like a Real Player — No Free Items
- [ ] Audit every system that grants items to the stockpile — all item generation must come from a real in-game action (block break, craft, smelt, fish, shear, etc.)
- [ ] Remove any abstract simulation shortcut that directly adds items to the stockpile without simulating the action that produced them
- [ ] Villagers must physically interact with the world: break blocks to gather, use crafting tables to craft, use furnaces to smelt, use fishing rods to fish
- [ ] Gate all item acquisition behind the correct tool being in the villager's personal inventory — no tool, no gather

### Villager World Interaction
- [ ] Implement block-breaking behavior: Farmer breaks mature crops, Toolsmith/Mason break stone/ore, Fisherman targets water-adjacent blocks for clay/sand
- [ ] Implement block-placing behavior: Mason places building blocks at build site, Farmer tills and plants
- [ ] Implement crafting table usage: villagers path to a crafting table in the village to craft items rather than receiving them from thin air
- [ ] Implement furnace usage: Weaponsmith, Armorer, Toolsmith path to and use a furnace to smelt ore into ingots
- [ ] Ensure the storehouse has a crafting table and furnace registered as worksite blocks that any villager can use for their role

---

## Priority 4 — Subrole System Rework

Now that villagers choose their own professions freely, subroles need to reflect open-ended capability rather than a fixed complement system.

- [ ] Decouple subroles from the fixed collaboration map — subroles should be a pool of secondary behaviors any villager can pick up based on village need, not a one-to-one pairing
- [ ] Subroles are now dynamic: a villager checks the NeedQueue during idle time and picks up any task they are capable of, regardless of their primary profession
- [ ] Example: any villager with an axe can do wood gathering as a subrole, not just Farmers; any villager near the smoker can assist Butcher if the queue has a cook request
- [ ] Update `ProfessionBehaviorRegistry` to register subrole behaviors as a shared pool that idle villagers can pull from, rather than hardcoded secondary tasks per profession
- [ ] Document the new subrole architecture in `CLAUDE.md` once the design is finalized

---

## Priority 5 — Guard Role Removal + Defense Rework

Remove the custom Guard profession entirely and split defense responsibilities across three existing vanilla professions.

### Remove Guard
- [ ] Remove the `smartvillager:guard` custom profession from `ModProfessions.java`
- [ ] Remove Bell POI association for Guard
- [ ] Remove `GuardDefenseSystem.java`, `PatrolSystem.java`, and `EscortSystem.java` — replace with new role-specific behavior below
- [ ] Remove all Guard references from `VillageDetector.chooseProfession()`, `SmartVillage`, and `CLAUDE.md`
- [ ] Remove the per-Bell Guard capacity cap (3 per Bell) — no longer needed without a dedicated Guard profession
- [ ] Update `DEV_COMMANDS.md` and `TEST_CHECKLIST.md` to remove Guard-specific entries

### New Defense Design

**Weaponsmith — melee combat and patrol**
- Primary defender; patrols the village perimeter and stockpile during the day
- Engages hostile mobs in melee using weapons from their own inventory (self-equipped from what they craft)
- Broadcasts `THREAT_ALERT` on threat detection
- During `THREAT_ALERT`, one Weaponsmith holds position at the stockpile rather than pursuing
- After combat, returns to patrol; still performs weapon crafting during idle time

**Fletcher — ranged support**
- Provides ranged fire during raids using bows and arrows from their own inventory
- Does not pursue threats — holds a position near the stockpile or a structure and fires from range
- During peaceful periods, focuses on arrow and bow production
- During `THREAT_ALERT`, activates ranged defense immediately without leaving their post

**Armorer — equipment logistics**
- Does not engage in combat directly
- Monitors Weaponsmith and Fletcher equipment condition; crafts and delivers replacements when gear is damaged or lost
- During `THREAT_ALERT`, stays back and prepares emergency replacement gear
- After a fight, paths to damaged defenders and hands off repaired or replacement equipment

### Tasks
- [ ] Implement `WeaponsmithPatrolBehavior` — perimeter patrol with stockpile waypoint, threat detection, melee engagement
- [ ] Implement `FletcherRangedDefenseBehavior` — stationary ranged fire during `THREAT_ALERT`, returns to crafting after
- [ ] Implement `ArmorerEquipmentLogisticsBehavior` — monitor defender gear, craft and deliver replacements, emergency rearm during fights
- [ ] Wire all three behaviors into `ProfessionBehaviorRegistry` under their respective profession keys
- [ ] Preserve `THREAT_ALERT` broadcast and civilian `SHELTER` state — these still apply, just triggered by Weaponsmith now
- [ ] Preserve Iron Golem commissioning logic — Librarian still commissions golems at prosperity/iron thresholds, golem still guards stockpile

---

## Notes

- All changes should preserve the NeedQueue as the communication backbone — villager autonomy should route through NeedQueue, not bypass it
- The "play like a real player" goal means the abstract simulation batch updates need to simulate actual actions (craft tick, smelt tick, mine tick) rather than just adding item counts
- Keep `DEV_COMMANDS.md` and `TEST_CHECKLIST.md` updated as each item above is implemented
