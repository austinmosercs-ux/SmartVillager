# Villager Society Mod – Project Context

## What this mod does
This mod overhauls Minecraft villagers to function as a real society. Villagers choose their own professions, earn everything through real in-game actions, communicate with each other to solve problems, trade among themselves and with the player, defend their village, and expand it over time. The goal is emergent behavior — a village that feels alive and self-sustaining.

Villagers operate their own internal economy using emeralds as currency. The player is an outsider who can observe, assist, donate, and trade — but the village operates with or without them.

---

## Villager Classes

### Design philosophy
All roles use existing vanilla professions with new behaviors. No custom professions exist. Every vanilla villager keeps their workblock as a job site anchor — the block tells them where to go during work hours, but profession is assigned at birth based on a weighted need calculation influenced by village gaps and personal personality trait.

Subroles are drawn from a shared behavior pool rather than hardcoded per profession. A villager checks the NeedQueue during idle time and picks up any task they are capable of, based on what tools they are carrying and what the village needs. A Toolsmith with an axe in their inventory can chop wood as a subrole. A Weaponsmith who has just finished crafting can patrol as a subrole. Collaboration is emergent, not hardcoded.

Defense is handled by three vanilla professions working as a team: Weaponsmith (melee/patrol), Fletcher (ranged support), and Armorer (equipment logistics). There is no dedicated Guard profession.

---

### Villager Classes — detailed breakdown

#### Food Supply Group

##### Farmer
**Primary role:** Grow and harvest crops — primary food source for the village
- Tills soil, plants seeds, harvests mature crops (wheat, carrots, potatoes, beetroot)
- Bakes bread when wheat supply allows; deposits all food to shared inventory
- Maintains farmland — replants after every harvest, expands plots when Mason builds new farmland
- Must physically break mature crop blocks and till with a hoe — no items granted without real actions

**Subrole (shared pool):** Gather wood and plant materials
- When crops are not yet mature and primary work is idle, chops nearby trees for logs and saplings
- Deposits logs and saplings to shared inventory — Fletcher uses wood for arrows, Mason uses it for early construction
- Replants saplings after chopping to keep the wood supply sustainable

**Deposits:** Food (crops, bread), logs, saplings
**Withdraws:** Hoe, axe (from Toolsmith)
**Key collaborators:** Butcher (food chain), Toolsmith (tool supply), Fletcher (wood supply), Mason (early wood for building)

---

##### Fisherman
**Primary role:** Fish for food — supplements village food supply independently of farmland
- Paths to nearest water source, physically uses fishing rod, returns and deposits catch to shared inventory
- Critical in early village when farmland is limited, or as a food buffer when crops fail

**Subrole (shared pool):** Gather water-adjacent resources
- Collects clay, sand, gravel, and flint while at the water's edge
- Deposits to shared inventory — Mason uses sand and gravel for building, Fletcher uses flint for arrows
- Reports water source locations to Cartographer for village mapping

**Deposits:** Fish (raw and cooked), clay, sand, gravel, flint
**Withdraws:** Fishing rod (from Toolsmith)
**Key collaborators:** Butcher (can process fish if needed), Cartographer (water location data), Mason (sand/gravel for building), Fletcher (flint for arrows)

---

##### Butcher
**Primary role:** Process meat and manage the village meat supply
- Collects raw meat from Shepherd's animal pens, cooks it at the smoker, deposits cooked food to shared inventory
- Monitors food levels — if supply drops critically, posts `NEED_FOOD_BOOST` to NeedQueue, prompting Shepherd to increase culling rate

**Subrole (shared pool):** Assist Shepherd with animal husbandry
- When meat supply is adequate, helps breed animals and maintain the animal pen
- Ensures animal population stays healthy and growing so future supply is sustainable
- If Shepherd is injured or absent, takes over basic animal care

**Deposits:** Cooked meat (beef, pork, chicken, mutton)
**Withdraws:** Raw meat from Shepherd via shared inventory
**Key collaborators:** Shepherd (raw materials supplier), Farmer and Fisherman (shared food production responsibility)

---

##### Shepherd
**Primary role:** Tend animals for wool and meat
- Breeds and maintains sheep, cows, pigs, and chickens in the village animal pen
- Shears sheep for wool when fleece is full; culls animals at sustainable intervals for meat supply
- Deposits wool, raw meat, feathers, and leather to shared inventory

**Subrole (shared pool):** Supply the production chain with animal byproducts
- Maintains chicken population specifically to supply feathers to Fletcher's arrow crafting
- Collects leather from cows for Leatherworker and Armorer's early armor tier
- Deposits all byproducts — Butcher, Fletcher, and Leatherworker all depend on this output

**Deposits:** Wool, raw meat, feathers, leather, eggs
**Withdraws:** Shears (from Toolsmith), food for animals from shared supply if needed
**Key collaborators:** Butcher (meat chain), Fletcher (feather supply for arrows), Leatherworker (leather supply), Armorer (early leather armor tier)

---

##### Leatherworker
**Primary role:** Process leather into goods and early armor
- Draws leather from shared inventory (supplied by Shepherd)
- Crafts leather armor pieces at a crafting table — provides early protection for Weaponsmiths before Armorer is producing iron armor
- Deposits leather goods to shared inventory

**Subrole (shared pool):** Flex worker in the animal supply chain
- When leather supply is adequate and primary work is idle, assists Butcher with smoker operations or Shepherd with pen maintenance
- Acts as a buffer in the food/animal group when one member is injured or overloaded

**Deposits:** Leather armor pieces, leather goods
**Withdraws:** Leather from Shepherd via shared inventory
**Key collaborators:** Shepherd (leather source), Armorer (hands off leather armor to Weaponsmith before iron is available)

---

#### Build and Map Group

##### Mason
**Primary role:** Build and expand village structures
- Checks the build queue (managed by Librarian, planned by Cartographer) for pending construction tasks
- Fetches required materials from shared inventory; if stock is low, triggers own quarrying subrole first
- Pathfinds to the build site and physically places blocks to complete the structure
- State machine: `IDLE → CHECK_QUEUE → FETCH_MATERIALS → PATHFIND_TO_SITE → BUILD → IDLE`
- Requires tools from Toolsmith — waits or posts `NEED_TOOLS` to NeedQueue if none available

**Subrole (shared pool):** Quarry stone and raw building materials
- When the build queue is empty or materials are low, switches to quarrying
- Physically mines stone, cobblestone, gravel, and sand at designated quarry zones; deposits all surplus to shared inventory
- Posts `NEED_ESCORT` if the quarry area is flagged as dangerous by Cartographer before heading out

**Deposits:** Stone, cobblestone, gravel, surplus building materials
**Withdraws:** Tools (from Toolsmith), building materials from shared inventory
**Key collaborators:** Cartographer (build plans and site locations), Toolsmith (tool supply), Weaponsmith (escort when quarrying in flagged areas), Librarian (build queue management)

---

##### Cartographer
**Primary role:** Map the village, plan build sites, and track all territory
- Surveys the village boundary, records all existing structure locations, identifies where new structures are needed
- Plans new build sites based on population and prosperity (more villagers → more houses; more Toolsmiths → mine entrance; etc.)
- Shares build site plans with Mason via the Librarian's build queue
- Maps cave systems, underground ore areas, and nearby structure loot locations (dungeons, mineshafts, temples) — shares all data with Toolsmith

**Subrole (shared pool):** Scout new areas and flag threats
- Ventures outside the village boundary to explore terrain (posts `NEED_ESCORT` first if the area is unknown)
- Marks hostile mob spawn points, cave entrances, resource-rich zones, and loot structures
- Feeds threat data to Weaponsmiths so they can extend patrol routes to cover newly identified danger areas
- Updates village memory with explored territory, cleared threats, and newly discovered resources

**Deposits:** Data — location records, threat flags, build plans, loot structure locations (no physical items)
**Withdraws:** Paper, maps (tools of the trade)
**Key collaborators:** Mason (build site plans), Weaponsmith (threat map and patrol route data), Toolsmith (cave and loot location data), Librarian (all map data feeds into village memory and build queue)

---

#### Defense Group

##### Weaponsmith
**Primary role:** Village melee defense and patrol
- Runs perimeter patrol routes around the village boundary during the day; patrol waypoints include the stockpile chest cluster as a mandatory stop on every loop
- On threat detection, broadcasts `THREAT_ALERT` — non-combat villagers enter `SHELTER` state, Cleric prepares to assist; one Weaponsmith is designated chest guardian and holds position at the stockpile rather than pursuing the threat
- Engages hostile mobs in melee, prioritizing threats closest to the stockpile first, then threats closest to non-combat villagers
- After combat, returns to patrol; Cleric paths to the Weaponsmith to heal
- Remains active at night on rotation — the only non-Cleric villager with a night work goal; night-rotation Weaponsmith stations at or near the stockpile rather than the full perimeter
- Responds to `NEED_ESCORT` from Toolsmith, Mason, or Cartographer heading outside safe village boundaries

**Subrole (primary craft):** Craft weapons
- During idle patrol time or when no threats are active, works the grindstone to craft and repair swords and axes
- Draws iron ore and ingots from shared inventory; smelts if raw ore is available at a furnace
- Equips themselves first from what they craft; deposits surplus weapons to shared inventory for other Weaponsmiths
- Upgrades weapon tier as ore supply grows (stone → iron → diamond)

**Deposits:** Swords, axes, smelted ingots (surplus)
**Withdraws:** Iron ore, coal, ingots from shared inventory; weapons and armor for personal use
**Key collaborators:** Toolsmith (ore supply), Armorer (full kit coordination), Fletcher (ranged support during fights), Cleric (post-combat healing), Cartographer (threat and patrol route data)

---

##### Armorer
**Primary role:** Equipment logistics — armor crafting and defender rearming
- Monitors Weaponsmith and Fletcher equipment condition — checks if defenders have complete armor sets and functional gear
- Draws ingots from shared inventory; crafts helmet, chestplate, leggings, and boots at a crafting table; delivers directly to defenders
- Upgrades armor tier progressively — starts with leather (from Leatherworker) then iron, then diamond as prosperity grows
- Does not engage in direct combat — stays back and manages gear during fights
- During `THREAT_ALERT`, prepares emergency replacement gear so defenders can rearm immediately after a fight

**Subrole (shared pool):** Smelt ingots and commission Iron Golems
- When defenders are fully armored, smelts raw ore as a buffer reserve for rapid rearming after fights
- When Librarian signals that prosperity and iron thresholds are met, builds the Iron Golem by consuming 36 ingots
- Coordinates with Weaponsmith to avoid both smelting when ore supply is limited — Weaponsmith has smelt priority

**Deposits:** Armor pieces (helmet, chestplate, leggings, boots), smelted ingots (surplus)
**Withdraws:** Ingots, leather (early tier), coal from shared inventory
**Key collaborators:** Weaponsmith (armor recipient and smelt coordination), Leatherworker (early armor supply), Toolsmith (ore deposits), Librarian (Iron Golem commission trigger)

---

##### Fletcher
**Primary role:** Ranged defense and arrow/bow crafting
- During `THREAT_ALERT`, takes a stationary position near the stockpile or a structure and provides ranged fire with bow and arrows — does not pursue threats
- Does not engage in melee; holds their defensive post and fires from range until threat is resolved
- After threat is resolved, returns to crafting
- During peaceful periods, crafts arrows in bulk from feathers (Shepherd), sticks/logs (Farmer), and flint (Fisherman/Mason)
- Crafts bows when material supply allows; deposits surplus arrows and bows to shared inventory

**Subrole (shared pool):** Gather wood and feathers directly when shared inventory supply is low
- If feathers or sticks are below threshold in the chest, paths to Shepherd's pen or helps Farmer chop wood
- Posts `NEED_MATERIALS: feathers` to NeedQueue if Shepherd is not producing enough to sustain arrow supply

**Deposits:** Arrows, bows
**Withdraws:** Feathers (Shepherd), sticks/logs (Farmer), flint (Fisherman/Mason) from shared inventory
**Key collaborators:** Shepherd (feather supply), Farmer (wood supply), Fisherman and Mason (flint supply), Weaponsmith (coordinates ranged vs melee coverage during fights)

---

#### Support and Production Group

##### Toolsmith
**Primary role:** Craft tools for the village workforce
- Monitors NeedQueue for tool requests (Mason needs pickaxes, Farmer needs hoes, Fisherman needs rods)
- Draws ingots from shared inventory; crafts the appropriate tool at a crafting table; deposits or delivers directly to the requesting villager
- Tracks which villagers have tools — a Mason without a pickaxe cannot quarry, a Farmer without a hoe cannot farm; these are treated as high-urgency requests

**Subrole (shared pool):** Mine ore and emeralds
- When tool demand is met and shared inventory ore supply is low, switches to mining
- Uses Cartographer's cave map and loot structure data to identify ore-rich areas and dungeon/mineshaft chests before heading out
- Posts `NEED_ESCORT` if the target area is flagged as dangerous
- Mines iron ore, coal, and emerald ore; loots chests in mapped structures for additional emeralds; deposits all surplus to shared inventory
- Weaponsmith and Armorer draw from ore surplus to equip defenders; emeralds flow into the village economy

**Deposits:** Pickaxes, axes, hoes, fishing rods, shovels, iron ore, coal, emeralds, surplus ore
**Withdraws:** Ingots (smelted), coal (for own smelting) from shared inventory
**Key collaborators:** Mason (primary tool consumer), Farmer (hoe/axe supply), Fisherman (fishing rod), Weaponsmith and Armorer (ore deposits feed their crafting), Cartographer (cave and loot maps for mining routes), Weaponsmith (escort during dangerous mining runs)

---

##### Cleric
**Primary role:** Heal injured villagers and manage the medical supply
- Monitors all villagers' health — when a villager posts `NEED_HEALING` or drops below a health threshold, Cleric paths to them and applies healing
- After a fight, moves to injured Weaponsmiths and other combat-damaged villagers and heals using the medical supply pool
- Heals the player on proximity when they enter the village — uses the same supply pool, so a depleted Cleric has nothing left for the player
- Stays available at night for combat emergencies

**Subrole (shared pool):** Brew potions and maintain supply stockpile
- When no active healing is needed, brews healing potions from ingredients in shared inventory at the brewing stand
- Maintains a medical supply reserve large enough to handle a full fight without running dry
- If ingredients are unavailable, posts `NEED_MATERIALS: [ingredient]` to NeedQueue — the Librarian may convert this into a player quest

**Deposits:** Healing potions and supplies (to shared inventory medical reserve)
**Withdraws:** Brewing ingredients, glass bottles, fuel from shared inventory
**Key collaborators:** Weaponsmith (primary healing recipient after combat), all villagers (general health maintenance), Librarian (supply shortages flagged for quest generation or NeedQueue escalation)

---

##### Librarian
**Primary role:** Village administrator — coordinate priorities, manage NeedQueue, log all events
- Monitors shared inventory supply levels continuously; posts NeedQueue requests when supplies drop below threshold — acts as the trigger for the whole village economy
- Logs all village events: deaths, completed builds, threats defeated, food shortages, fights
- Manages the build queue: populates it based on village population and prosperity needs; feeds it to Cartographer and Mason
- At prosperity thresholds, unlocks advanced recipes: enchanted tools for Toolsmith, better weapon tiers for Weaponsmith
- Triggers Iron Golem commissioning when prosperity and iron thresholds are met

**Subrole (shared pool):** Give player quests and manage village reputation
- When a player with sufficient reputation is present, offers quests sourced from active NeedQueue items (clear a dungeon, deliver materials, escort a Toolsmith on a mining run)
- Rewards come from village stock — Librarian controls what the village can afford to give
- Tracks player reputation score — donations, completed quests, and successful trades raise it; attacking villagers drops it; reputation affects trade prices across all villagers

**Deposits:** Nothing physical — Librarian's output is coordination, information, and event logs
**Withdraws:** Nothing beyond personal food and survival needs
**Key collaborators:** Every other villager — the Librarian is the administrative hub that the entire NeedQueue, build queue, and village memory routes through

---

#### Passive

##### Nitwit
- No primary role, no subrole, no job behaviors whatsoever
- Wanders, eats, sleeps
- Consumes food at a normal rate and occupies a bed, contributing nothing
- Intentional drag on the economy — a village with too many Nitwits will struggle to maintain food supply and grow slowly

---

### Full collaboration map

```
FOOD CHAIN
Farmer (crops/wood) ─────────────────────────────────────┐
Fisherman (fish/sand/flint) ─────────────────────────────├──► Shared Inventory ──► All villagers eat
Shepherd (meat/wool/leather/feathers) ──► Butcher (cooks)─┘
Leatherworker (leather goods) ───────────────────────────►

TOOL / ORE CHAIN
Toolsmith mines ore + emeralds ──────────────────────────► deposits surplus ──► Shared Inventory
Toolsmith loots mapped structure chests ─────────────────► deposits emeralds ──► Shared Inventory (treasury)
Toolsmith crafts tools ──────────────────────────────────► Mason, Farmer, Fisherman withdraw
Weaponsmith draws ore ──► smelts ──► crafts weapons ──────► equips self + deposits surplus
Armorer draws ingots ────────────► crafts armor ──────────► delivers to Weaponsmith + Fletcher
Leatherworker ───────────────────► crafts leather armor ──► Weaponsmith (early tier)
Fletcher draws feathers/wood/flint ──► crafts arrows/bows ► equips self + deposits surplus

BUILD CHAIN
Cartographer surveys ──────────► plans build sites ──► feeds Librarian build queue
Mason checks queue ────────────► fetches materials ──► builds structures ──► village grows
Mason quarrying subrole ───────► deposits stone surplus ──► Shared Inventory

DEFENSE CHAIN
Cartographer flags threats ────────────────────────────── Weaponsmith extends patrol to flagged area
Weaponsmith patrols ──► passes stockpile on every loop ── Iron Golem stationed at storehouse
Weaponsmith detects threat ──► THREAT_ALERT ────────────► all non-combat villagers SHELTER
                                                         ► one Weaponsmith holds at stockpile
                                                         ► Fletcher holds position + fires ranged
                                                         ► Iron Golem engages threats near chests
Weaponsmith engages mob ──► fight resolves ─────────────► Cleric heals Weaponsmith
Armorer prepares emergency gear ────────────────────────► Weaponsmith + Fletcher rearm
Librarian checks prosperity + iron supply ──────────────► Armorer builds Iron Golem (36 ingots)
Iron Golem dies ───────────────────────────────────────► IronGolemSystem vacates slot ──► Librarian commissions replacement
Cartographer marks area cleared ───────────────────────► Toolsmith / Mason resume work in area

ESCORT CHAIN
Toolsmith or Mason posts NEED_ESCORT ──► Weaponsmith accepts ──► escorts out and back
Cartographer posts NEED_ESCORT for scouting runs ───────────► Weaponsmith escorts

TRADE CHAIN
Any villager assesses personal inventory + stockpile ──► generates dynamic buy/sell offers
Villager has surplus ──► offers to sell to player or other villagers for emeralds
Villager needs item ──► offers emeralds to buy from player or other villagers
Emeralds from Toolsmith mining / loot ──► Shared Inventory treasury ──► villagers withdraw to fund trades
Player sells to village ──► emeralds enter village pool ──► economy grows
Player buys from village ──► emeralds leave village pool ──► creates scarcity pressure

ADMIN CHAIN
Librarian monitors inventory ──► posts NeedQueue requests ──► producers respond
Librarian manages build queue ─────────────────────────► Mason and Cartographer pull tasks
Librarian logs events ─────────────────────────────────► village memory ──► prosperity score
Cleric flags low medical supply ───────────────────────► Librarian escalates to player quest
```

Each producer takes what their primary role needs first. Surplus flows to shared inventory and becomes available to other roles via direct withdrawal or NeedQueue request. No role calls another directly — everything routes through the shared inventory and the NeedQueue.

---

## Player Interaction
The player is a trading partner and optional participant — not required for village survival.

- **Any villager** — right-clicking any villager opens a dynamic trade screen showing only what that villager currently has in surplus (sell offers) or needs (buy offers with emeralds). Offers are generated live from their personal inventory and the village stockpile. Prices shift based on supply levels and player reputation
- **Cleric** — walks up to the player and heals them on village entry, no menu. Only works if the Cleric has enough supplies — proximity triggered, no interaction required
- **Librarian** — can give the player quests sourced from active NeedQueue items (clear a dungeon, deliver materials, escort a Toolsmith on a mining run). Rewards come from village stock
- **Donating** — player can deposit materials or emeralds into the village stockpile chest directly, boosting prosperity and injecting money into the village economy
- **Village reputation** — player reputation affects trade prices across all villagers, whether the Librarian offers quests, and whether Weaponsmiths are friendly or hostile on approach

The village does not need the player to survive. The player is an optional participant.

---

## Core Systems

### 1. Villager AI Goal Stack
Each villager evaluates goals in priority order:
1. **Survival** — eating when hungry, seeking the Cleric when injured, sheltering from threats
2. **Job task** — class-specific behavior (mine, build, craft, patrol, etc.)
3. **Social** — post needs, respond to requests, trade, negotiate

A starving or badly injured villager will not perform their job until their survival need is met. Use the vanilla `Brain` / `BehaviorControl` system. Each class registers its own set of behavior tasks into the brain. Extend `VillagerEntity` or the loader equivalent — do not build new entities from scratch.

### 2. Hunger System
Every villager has a hunger value that depletes over time.

- Depletion rate varies by role — physically demanding roles (Toolsmith mining, Mason quarrying, Weaponsmith patrolling) burn hunger faster than passive roles (Librarian, Nitwit)
- When hunger drops below a threshold the villager enters a `HUNGRY` state, overrides their job goal, and paths to the village food supply to eat
- If the food supply is empty they cannot eat and begin losing health passively over time
- Nitwits consume food at a normal rate despite contributing nothing — this is intentional and creates real pressure on struggling villages
- A village with too many mouths and not enough Farmers will visibly deteriorate — villagers slow down, get hurt, stop doing their jobs, and the prosperity score drops

### 3. Health System
Every villager has a health value that can be reduced by combat, environment, and starvation.

- **Combat damage** — Weaponsmiths take damage fighting mobs; Fletcher can take damage if threats reach their position
- **Environmental damage** — Toolsmith and Mason can be hurt by cave-ins, lava, or mob encounters while gathering; Mason can take fall damage during construction
- **Starvation damage** — any villager who cannot eat due to empty food supply loses health passively
- When health drops below a threshold the villager enters a `SEEK_HEALING` state, overrides their job goal, and paths to the Cleric
- If no Cleric is available or the Cleric has no supplies, the villager continues working but at reduced speed and effectiveness
- A villager that reaches zero health dies permanently, triggering the role replacement system
- The Cleric heals the player on proximity using the same supply pool — if the village is struggling the Cleric may have nothing left for the player

### 4. Village Inventory (Shared Economy)
The stockpile is a network of physical chests placed in the world inside the village storehouse area. This is not a virtual data structure — villagers physically walk to a chest, open it, and deposit or withdraw items.

**Physical chest network:**
- `StockpileChestTracker` maintains a list of `BlockPos` for every registered stockpile chest in the village
- At village registration, the storehouse location is chosen near the village center and initial chests are placed by the mod or detected from existing vanilla village structures
- As the village prospers, Mason can extend the storehouse — additional chests are registered with `StockpileChestTracker` and immediately become part of the network
- `VillageStockpile` reads and writes to the actual chest `TileEntity` at each registered position; it iterates the list and aggregates item counts across all chests when the Librarian queries supply levels
- Villagers use a `PATH_TO_STOCKPILE` behavior to walk to the nearest registered chest before depositing or withdrawing — they do not teleport items in
- The player can deposit into any registered stockpile chest directly — the mod detects the interaction and credits the village prosperity score

**Workstation tracking:**
- `WorkstationTracker` maintains a list of `BlockPos` for all registered crafting tables, furnaces, smokers, and brewing stands in the village
- Villagers must physically path to a workstation to craft, smelt, cook, or brew — no items are created without the villager being at the correct block
- Workstations are registered at village init from detected vanilla structures and expanded by Mason builds

**Supply monitoring:**
- The Librarian scans all registered chest contents on a periodic tick to detect shortages
- Low supply triggers NeedQueue posts via `LibrarianCoordinator` — the chest network is the source of truth
- Trade offers shown to players and other villagers are drawn from live stockpile counts

**Abstract simulation:**
- When chunks are unloaded, `VillageStockpile` snapshots chest contents into a lightweight item-count map stored on `SmartVillage`
- The abstract batch update approximates what villagers would have done — simulating the actions (mine tick, craft tick, smelt tick) rather than directly adding items — and applies results to the snapshot
- On reconciliation, the snapshot is written back to the physical chests when chunks reload

**Defense:**
- The stockpile chest cluster is a high-value target — Weaponsmiths include it on every patrol loop
- Iron golems are stationed permanently at the storehouse (see Core System 13)

### 5. NeedQueue (Communication System)
The backbone of the mod. A server-side queue scoped to each village.

- Villagers post a `NeedRequest` containing: `requestType`, `reward`, `urgency`, `poster`
- Other villagers check the queue during idle state and accept matching jobs
- Example flows:
  - Toolsmith detects hostile mobs while mining → posts `NEED_ESCORT` → Weaponsmith accepts → escorts Toolsmith to mine and back
  - Weaponsmith needs ore and chest is empty → posts `WANT_TO_BUY: iron` → Toolsmith prioritizes a mining run
  - Mason needs stone and has none → posts `NEED_MATERIALS: stone` → Mason's own subrole triggers a quarry run, or another Mason drops off surplus
  - Injured Weaponsmith has no Cleric available → posts `NEED_HEALING` → Cleric prioritizes them on return
  - Villager needs an item they cannot produce → posts `WANT_TO_BUY_FROM_PLAYER: [item]` → Librarian may surface this as a player quest
- Keep this system decoupled — economy, defense, and expansion route through the NeedQueue, not through direct calls to each other

### 6. Defense System
- Weaponsmiths run continuous perimeter patrol routes around the village boundary; stockpile chest cluster is a mandatory waypoint on every loop
- On threat detection, a Weaponsmith broadcasts `THREAT_ALERT` to the village
- Non-combat villagers enter a `SHELTER` goal state and pathfind to the nearest building
- Fletcher villagers take a stationary defensive position and provide ranged fire — they do not pursue threats
- During `THREAT_ALERT`, one Weaponsmith is designated chest guardian — holds position at the stockpile and does not pursue the threat; remaining Weaponsmiths engage
- Armorer stays back and prepares emergency replacement gear
- Cleric moves to assist injured defenders after a fight
- Alert clears after a cooldown with no threats detected
- Village memory logs where threats originated — Toolsmith and Mason avoid flagged areas until Weaponsmiths clear them
- Weaponsmiths also respond to `NEED_ESCORT` from Toolsmith or Mason heading out to gather materials
- Iron golems defend the stockpile area alongside Weaponsmiths (see Core System 13)

### 7. Village Expansion
- Mason villagers check a build queue coordinated by the Librarian
- Build queue populates based on village needs (more villagers = more houses, more Toolsmiths = mine entrance structure, etc.)
- Cartographer plans build sites and shares locations with Mason
- Mason state machine: `IDLE → CHECK_QUEUE → FETCH_MATERIALS → PATHFIND_TO_SITE → BUILD → IDLE`
- Mason requires tools from the village inventory (produced by Toolsmith) — waits or posts a NeedRequest if none available

### 8. Village Prosperity Score
A hidden score tracking overall village health. Drives growth and unlocks.

Increases with: successful trades (villager-to-villager and player trades), buildings completed, threats defeated, food surplus, player donations, villagers at full health and hunger
Decreases with: villager deaths, unmet needs, food shortage, villagers starving or injured with no Cleric, failed build attempts

Drives:
- New villager spawns (only when food, beds, and jobs exist to support them)
- Building tier unlocks
- Librarian unlocking enchanted tool recipes for Toolsmiths and Weaponsmiths
- Iron Golem commissioning threshold

### 9. Population & Role Assignment
- Villages start small and grow only when they can support new members (food + beds + available job)
- No custom professions exist — all roles are vanilla professions with overridden behavior
- When a new villager is born, the village calculates a weighted profession score for each role based on: current gap (how many of this role the village has vs needs), resource availability (a second Weaponsmith is useless if there is no iron), and the newborn's personality trait (brave villagers weight toward Weaponsmith; generous toward Cleric; cautious toward Farmer or Fisherman)
- The highest-scoring profession is assigned — this is the village's "choice" expressed through weighted need, not random
- No hard caps on any profession — the weighting system naturally produces sensible distributions; if the village has five Farmers and one Weaponsmith, the next birth will heavily weight Weaponsmith
- Farmer and Weaponsmith always have a baseline weight so the village never spawns with zero food production or zero defense
- Death consequences ripple through the economy — losing the only Toolsmith slows ore and tool supply, losing all Weaponsmiths increases threat frequency and patrol stops, losing the only Farmer starts a starvation cascade

### 10. Day/Night Cycle Behavior
- Villagers go home and sleep at night — enforced, not optional
- Toolsmith and Mason do not go out to gather materials at night
- Weaponsmiths remain active at night on rotation — the only non-Cleric villagers with a night work goal; night-rotation Weaponsmith stations at the stockpile rather than the full perimeter
- Night raids are more dangerous — the village is at reduced defense capacity
- Cleric stays available at night for emergencies
- A hungry villager will wake and eat before sleeping if food is available

### 11. Villager Personality & Reputation
- Villagers have simple personality traits: brave, cautious, greedy, generous
- Traits bias their NeedQueue decisions — a brave Toolsmith may attempt a mining run solo, a cautious one always posts `NEED_ESCORT` first
- Traits also affect hunger and health behavior — a stubborn villager might ignore the `SEEK_HEALING` goal longer than they should
- Traits influence profession assignment at birth (brave → Weaponsmith weight, generous → Cleric weight, cautious → Farmer/Fisherman weight, greedy → Toolsmith/Weaponsmith weight)
- Greedy villagers price their trade offers higher — other villagers may prefer trading with non-greedy alternatives if available
- Player reputation is tracked village-wide by the Librarian and affects trade prices across all villagers, not just a dedicated merchant

### 12. Village Memory
- The village tracks where threats have occurred and where villagers have died
- Toolsmith and Mason avoid flagged areas until a Weaponsmith clears them
- Cartographer maintains and shares this map data with Weaponsmiths, Toolsmith, and Mason

### 13. Iron Golem Defense System
Villages commission iron golems as permanent stockpile guardians. These are not naturally spawning vanilla golems — they are intentionally built by the village when conditions are met.

**Commissioning:**
- The Librarian triggers golem creation when two conditions are met simultaneously: village prosperity score exceeds a threshold (configurable, default ~250) AND the stockpile contains at least 36 iron ingots (equivalent to 4 iron blocks)
- The Armorer "builds" the golem by consuming the 36 ingots from the stockpile and calling `IronGolemSystem.spawnGolem()` — this spawns a vanilla `IronGolem` entity at the storehouse
- The spawned golem's UUID is recorded in `SmartVillage.golems`
- Village golem cap: one golem per 5 active villagers, maximum 3 — Librarian will not commission more than the cap

**Behavior:**
- Village-owned golems are permanently stationed at the stockpile chest cluster; they do not wander the village like vanilla golems
- During peaceful periods, golems idle at the storehouse entrance as a visible deterrent
- On `THREAT_ALERT`, golems activate — they pursue and engage any hostile mob in the village, prioritizing threats nearest the stockpile; they do not leave the village boundary
- After threat is resolved, golems return to their station at the storehouse
- Golems do not interact with the NeedQueue and are not villagers — they are village-owned entities managed by `IronGolemSystem`

**Replacement:**
- When a village-owned golem dies, `IronGolemSystem` detects the death (entity remove event), removes the UUID from `SmartVillage.golems`, and marks the slot as vacant
- The Librarian checks the vacant slot on its next monitoring tick — if prosperity and iron supply conditions are met again, it commissions a replacement via the Armorer
- Cooldown of at least one in-game day before replacement to prevent immediate respawning during an active raid

**Abstract simulation:**
- Golem health is tracked in `SmartVillage` as a simple integer
- Abstract batch updates apply threat-based damage to the golem; if health reaches zero the golem death is recorded and the slot is vacated
- On reconciliation, if the golem died during abstract simulation it is not spawned; the Librarian will commission a replacement through normal conditions

### 14. Emerald Economy
Emeralds are the village currency. Supply is finite and must be earned — there is no infinite money.

**Starting supply:**
- Each villager starts with 10 emeralds in their personal inventory as seed money for day-one trading
- The village stockpile (treasury) starts at 0 emeralds
- Villagers withdraw from the stockpile treasury before spending personal emeralds

**How emeralds enter the economy:**
- Toolsmith mines emerald ore during mining runs — the primary long-term growth source; emerald ore spawns in mountain biomes and as rare veins in other biomes
- Toolsmith loots chests in mapped structures (dungeons, mineshafts, desert temples) — Cartographer marks these locations so Toolsmith knows where to go; this provides a money source for villages in non-mountain biomes
- Player sells items to villagers — emeralds the player spends re-enter the village pool
- Player donates emeralds directly to the stockpile — raises prosperity and grows the money supply

**How emeralds leave the economy:**
- Player buys from villagers and does not spend emeralds back — the primary money sink
- This creates real scarcity pressure: a village that trades heavily with a player who never reinvests will eventually run low

**Villager-to-villager trades:**
- Villagers trade with each other using the same dynamic offer system as player trades
- Emeralds circulate internally — a Farmer selling food to a Weaponsmith who needs it moves money within the village without reducing total supply
- Internal trades do not add or remove emeralds from the village pool, they redistribute them

### 15. Two-Mode Village Simulation
Villages run in one of two modes depending on player proximity. This keeps the village economy running without the RAM cost of permanently force-loaded chunks.

**Full simulation** — player is within ~128 blocks
- Village chunks are loaded normally
- Villagers physically move, pathfind, and execute AI tasks in real time
- All systems run at full tick rate
- The "no free items" rule applies strictly — every item must come from a real in-game action

**Abstract simulation** — player is beyond ~128 blocks
- Village chunks are unloaded to free RAM
- Village state is stored as lightweight data: hunger levels, food supply, tool counts, NeedQueue entries, health values, build progress, emerald counts
- A batch update runs every few minutes and approximates what villagers would have done — simulating the actions (mine tick, craft tick, smelt tick) rather than directly granting items; the simulation models the action, not the result in isolation
- No pathfinding, no entity AI, no chunk overhead

**Reconciliation** — when player returns within range
- Chunks reload and full simulation resumes
- Abstract state is applied to the physical village — villagers spawn at correct health and hunger, inventory reflects what was produced, any deaths that occurred are applied
- The village should feel like it kept running the whole time

This approach allows multiple villages to exist and simulate simultaneously without stacking hundreds of MB of loaded chunks per village.

---

## Starting State
Villages spawn with little to nothing. This is intentional.

**Starting roster:** one Librarian, one or two Farmers, one Weaponsmith
**Starting inventory:** minimal food, no tools, no ore, minimal Cleric supplies, 0 emeralds in stockpile (each starting villager carries 10 emeralds personally)
**Starting buildings:** basic vanilla village structures only — must include at least one crafting table and one furnace detectable by `WorkstationTracker`

The village must earn everything else. The weighted profession system naturally expands the village toward what it needs — more Farmers until food is stable, then Toolsmiths for tools and ore, then Armorers and Fletchers for defense support, then Masons for expansion. A village that spawns with a Nitwit in its starting roster is immediately at a disadvantage.

---

## Implementation Order
Build in this order to avoid dependency issues:

1. Physical chest stockpile network (`StockpileChestTracker` + `VillageStockpile` reads/writes real chests) + Librarian as coordinator
2. `WorkstationTracker` — register crafting tables, furnaces, smokers, brewing stands at village init; villagers must path to these to craft/smelt/cook/brew
3. Hunger system — every villager needs this before anything else runs
4. Health system — damage, starvation passive damage, death and weighted role replacement
5. NeedQueue communication system — most important logic, build this carefully
6. Weaponsmith patrol + stockpile waypoints + threat alert + chest guardian assignment + civilian shelter behavior + Fletcher ranged defense + Armorer equipment logistics
7. Day/night cycle enforcement
8. Cleric healing (villagers + player) tied to supply levels
9. Iron golem defense system — Librarian commissions via Armorer when prosperity + iron thresholds met; golem stationed at storehouse; replacement logic on death
10. Toolsmith mining subrole (ore + emerald ore + structure loot) + Weaponsmith/Armorer crafting chain: Toolsmith deposits ore → Weaponsmith/Armorer process → equip themselves
11. Dynamic per-villager trading (VillagerTradeEvaluator) — player-facing and villager-to-villager; emerald economy tracking
12. Mason building subrole + Cartographer planning: Cartographer maps sites → Mason builds (storehouse + workstation expansion included)
13. Prosperity score + weighted population growth
14. Personality traits + inter-villager reputation + village memory

---

## Notes for the AI
- Always ask for the mod loader and Minecraft version before writing any code — APIs differ significantly between Fabric/Forge and MC versions
- Prefer extending vanilla systems (`Brain`, `BehaviorControl`, POI, workblock registration) over building from scratch
- All villager AI should use the goal/behavior task system, not tick-based overrides
- No custom professions exist — all roles are vanilla professions with behavior tasks registered into the Brain via `ProfessionBehaviorRegistry`; `ModProfessions.java` registers no custom professions and can be removed or left as an empty placeholder
- Workblocks are job site anchors only — profession is assigned at birth via weighted need calculation, not by villager claiming a block
- Subroles are drawn from a shared behavior pool — a villager checks the NeedQueue during idle time and picks up any task they are capable of based on tools in their personal inventory; do not hardcode subroles per profession
- The NeedQueue is the backbone of the mod — get it right before building anything on top of it
- Keep systems decoupled: economy, defense, and expansion should not call each other directly
- Hunger and health are survival layer systems — they must override job and social goals when triggered
- Trade offers must reflect actual villager inventory and stockpile levels — never hardcoded trade lists
- The Cleric healing the player requires no player input — proximity triggered, supply dependent, same supply pool as villager healing
- Nitwits have no goals beyond hunger and sleep — do not assign them any job behavior
- The Librarian coordinates the village — manages the NeedQueue log, tracks supply levels, drives build queue population; treat them as the administrative brain of the village
- The village should function and evolve whether or not the player is present
- Villages run in two modes: full simulation (chunks loaded, player within ~128 blocks) and abstract simulation (chunks unloaded, state tracked as data with batch updates). Never force-load village chunks permanently
- Abstract simulation approximates real actions — simulate the action tick, not a direct item grant; the rule is "no free items" in full sim, and "approximated real actions" in abstract sim
- Abstract simulation must track at minimum: per-villager hunger and health, shared inventory contents (chest snapshot), NeedQueue state, build queue progress, villager deaths, golem health, emerald counts — enough to reconcile correctly when full simulation resumes
- The stockpile is physical chests — `VillageStockpile` reads actual `ChestBlockEntity` tile entities; villagers must physically path to a chest to deposit or withdraw
- Villagers must physically path to a workstation (`WorkstationTracker`) to craft, smelt, cook, or brew — never grant crafted items without the villager being at the correct block
- Iron golems are village-commissioned entities, not naturally spawning ones — spawn via `IronGolemSystem.spawnGolem()` and track UUID in `SmartVillage`
- Weaponsmiths always include the stockpile chest cluster as a patrol waypoint — never generate a patrol route that skips the storehouse
- During `THREAT_ALERT`, one Weaponsmith is designated chest guardian — this assignment must be tracked explicitly

---

## Key Files

| File | Responsibility |
|---|---|
| `src/main/java/com/smartvillager/SmartVillager.java` | Main mod entry point that registers entity attachments and wires event handlers |
| `src/main/java/com/smartvillager/registration/ModAttachments.java` | Defines NeoForge entity attachments for villager backpack, hunger, health, personality, and emerald wallet data |
| `src/main/java/com/smartvillager/ai/ProfessionBehaviorRegistry.java` | Centralizes profession-specific Brain behavior injection hooked into villager brain refresh |
| `src/main/java/com/smartvillager/village/SimulationMode.java` | Enum for full vs. abstract village simulation modes based on player proximity |
| `src/main/java/com/smartvillager/village/VillageRegistry.java` | Persisted SavedData registry mapping villages by UUID and Bell anchor position |
| `src/main/java/com/smartvillager/village/VillageStockpile.java` | Reads and writes to physical chest TileEntities at registered positions; aggregates item counts across the full chest network; snapshots to/from abstract sim |
| `src/main/java/com/smartvillager/village/StockpileChestTracker.java` | Tracks the list of BlockPos for all registered stockpile chests; handles chest registration on village init and storehouse expansion by Mason |
| `src/main/java/com/smartvillager/village/WorkstationTracker.java` | Tracks BlockPos for all registered crafting tables, furnaces, smokers, and brewing stands; villagers must path to these to perform crafting/smelting/cooking/brewing |
| `src/main/java/com/smartvillager/village/LibrarianCoordinator.java` | Scans village stockpile against thresholds to detect and flag shortages for NeedQueue posting |
| `src/main/java/com/smartvillager/village/VillagerInteractionHandler.java` | Opens per-villager dynamic trade screen on player interaction; routes through VillagerTradeEvaluator |
| `src/main/java/com/smartvillager/village/SmartVillage.java` | Stores all persistent village data including roster, stockpile, threat state, emerald treasury, and abstract health tracking |
| `src/main/java/com/smartvillager/village/VillageDetector.java` | Detects vanilla villages, assigns professions at birth via weighted need calculation, and switches simulation modes based on player proximity |
| `src/main/java/com/smartvillager/village/ProsperitySystem.java` | Event-driven prosperity adjustments: villager death (−30), threat defeated (+20), structure built (+15), trade completed (+10), player donation (+5), villager starving (−5) |
| `src/main/java/com/smartvillager/trade/VillagerTradeEvaluator.java` | Generates dynamic buy/sell trade offers per villager based on personal inventory, stockpile levels, and player reputation; used for both player trades and villager-to-villager trades |
| `src/main/java/com/smartvillager/inventory/ArmorSlot.java` | Enum for the four armor equipment slots in villager backpack |
| `src/main/java/com/smartvillager/inventory/VillagerBackpack.java` | Per-villager personal inventory with 15 general slots and 4 armor slots, serializable and defensive |
| `src/main/java/com/smartvillager/hunger/VillagerHunger.java` | Per-villager hunger value attachment with depletion and restoration mechanics |
| `src/main/java/com/smartvillager/hunger/HungerDepletionRates.java` | Maps profession identifiers to hunger depletion rates (fast/normal/slow) |
| `src/main/java/com/smartvillager/hunger/HungerSystem.java` | Manages hunger depletion and stockpile-based feeding for both full and abstract simulations |
| `src/main/java/com/smartvillager/health/HealthSystem.java` | Applies starvation damage and manages health state in both full and abstract simulations |
| `src/main/java/com/smartvillager/health/VillagerHealth.java` | Per-villager health attachment that syncs with vanilla entity and tracks healing needs |
| `src/main/java/com/smartvillager/needqueue/NeedPriority.java` | Enum (low/normal/high/urgent) for prioritizing need requests |
| `src/main/java/com/smartvillager/needqueue/NeedTypes.java` | Defines all need request type identifiers for village communication (escort, healing, tools, trade, etc.) |
| `src/main/java/com/smartvillager/needqueue/NeedRequest.java` | Immutable request record with id, type, priority, poster, itemData, and acceptance status |
| `src/main/java/com/smartvillager/needqueue/VillageNeedQueue.java` | Per-village queue with open and in-progress request lists, supporting posting/acceptance/completion/expiry |
| `src/main/java/com/smartvillager/needqueue/NeedQueue.java` | System-level stateless logic syncing LibrarianCoordinator shortages to queue posts and expiring stale requests |
| `src/main/java/com/smartvillager/mixin/MixinVillager.java` | Mixin hook injecting profession-specific behavior into Villager.refreshBrain() call chain |
| `src/main/java/com/smartvillager/command/DebugCommands.java` | Thin `/sv` command orchestrator — builds the Brigadier tree by calling each submodule's `register()` and registering it; no command logic lives here |
| `src/main/java/com/smartvillager/command/DebugHelper.java` | Shared constants (`NO_VILLAGE_MSG`, `FMT_HP`, `CMD_CLEAR`, etc.) and utilities (`findNearestVillage`, `parseItemId`) used by all `/sv` command classes |
| `src/main/java/com/smartvillager/command/VillagerDebugCommands.java` | `/sv debug` thought broadcast (toggle + tick event), `/sv nearby`, `/sv villager heal\|feed` |
| `src/main/java/com/smartvillager/command/VillageInfoCommands.java` | `/sv status` (full village overview) and `/sv roster` (roster with live/abstract HP and hunger) |
| `src/main/java/com/smartvillager/command/StockpileCommands.java` | `/sv stockpile` list/add/set/clear — reads and mutates the village stockpile for testing |
| `src/main/java/com/smartvillager/command/NeedQueueCommands.java` | `/sv queue` list/clear — inspects and resets the VillageNeedQueue |
| `src/main/java/com/smartvillager/command/DefenseCommands.java` | `/sv threat` trigger/clear and `/sv golem` list/spawn — manual defense state control |
| `src/main/java/com/smartvillager/command/EconomyCommands.java` | `/sv prosperity` add/set and `/sv build` list/clear — prosperity score and build queue control |
| `DEV_COMMANDS.md` | Player-facing reference for all `/sv` in-game test commands with syntax, descriptions, and common testing workflows |
| `src/main/java/com/smartvillager/defense/WeaponsmithDefenseSystem.java` | Drives Weaponsmith melee combat, threat detection, chest-guardian assignment during THREAT_ALERT, civilian shelter orders, and perimeter patrol with mandatory stockpile waypoint |
| `src/main/java/com/smartvillager/defense/FletcherRangedDefenseBehavior.java` | Drives Fletcher stationary ranged fire during THREAT_ALERT; returns to crafting after threat resolves |
| `src/main/java/com/smartvillager/defense/ArmorerEquipmentLogisticsBehavior.java` | Drives Armorer equipment monitoring, delivery to defenders, and emergency rearm during and after fights; handles Iron Golem commission trigger |
| `src/main/java/com/smartvillager/defense/EscortSystem.java` | Assigns Weaponsmiths to NEED_ESCORT requests; tracks escort state (accept→accompany→return); skips escorting Weaponsmiths from patrol |
| `src/main/java/com/smartvillager/defense/IronGolemSystem.java` | Commissions, spawns, and stations village-owned iron golems at the storehouse; tracks golem UUIDs on SmartVillage; handles death detection and replacement cooldown |
| `src/main/java/com/smartvillager/daynight/DayNightCycle.java` | Utility for night detection and Weaponsmith night-rotation logic; gates resource gathering to daytime only |
| `src/main/java/com/smartvillager/cleric/ClericHealingSystem.java` | Drives Cleric healing of injured villagers and players, potion brewing subrole, and abstract-sim healing from stockpile supply |
| `src/main/java/com/smartvillager/food/FarmerSystem.java` | Drives Farmer crop harvesting (bread, carrot, potato, wheat) and wood-gathering subrole (logs, saplings) when food supply is adequate |
| `src/main/java/com/smartvillager/food/FishermanSystem.java` | Drives Fisherman fish production (cooked_cod, cooked_salmon) and water-edge resource subrole (sand, gravel, flint, clay) |
| `src/main/java/com/smartvillager/food/ShepherdSystem.java` | Drives Shepherd animal tending (wool, raw meat, feathers, leather, eggs) with doubled meat output when NEED_FOOD_BOOST is active |
| `src/main/java/com/smartvillager/food/ButcherSystem.java` | Converts raw meat from stockpile to cooked meat; posts NEED_FOOD_BOOST to NeedQueue when cooked meat supply drops critically low |
| `src/main/java/com/smartvillager/food/LeatherworkerSystem.java` | Converts leather from stockpile into leather armor pieces (helmet, chestplate, leggings, boots) for early Weaponsmith equipment |
| `src/main/java/com/smartvillager/supply/WeaponsmithSystem.java` | Crafts iron swords and axes from stockpile ingots; equips self first, deposits surplus; smelts raw ore as subrole when ingots are below threshold |
| `src/main/java/com/smartvillager/supply/ArmorerSystem.java` | Crafts iron armor pieces for defenders; smelt subrole activates only when ore is abundant so Weaponsmith has first access to scarce ore; commissions Iron Golem when triggered by Librarian |
| `src/main/java/com/smartvillager/supply/FletcherSystem.java` | Crafts arrows from oak logs, feathers, and flint up to a 64-arrow buffer; posts NEED_MATERIALS to NeedQueue when feather or flint supply drops below threshold |
| `src/main/java/com/smartvillager/build/BuildTaskType.java` | Enum of build task types (PLACE_CHEST, PLACE_WORKSTATION) describing what action fires on task completion |
| `src/main/java/com/smartvillager/build/BuildTask.java` | Immutable record for a single queued build task: type, target BlockPos, progress required/done; CODEC-serialized |
| `src/main/java/com/smartvillager/build/BuildQueue.java` | Ordered list of BuildTasks per village; head-first processing; CODEC-serialized through SmartVillage |
| `src/main/java/com/smartvillager/build/MasonSystem.java` | Drives Mason quarrying subrole (cobblestone, gravel, sand), build queue execution, and storehouse/workstation expansion; posts NEED_TOOLS when no pickaxe available |
| `src/main/java/com/smartvillager/cartographer/CartographerSystem.java` | Drives Cartographer survey waypoints and scouting subrole; queues PLACE_CHEST and PLACE_WORKSTATION build tasks when prosperity ≥ 150; records explored territory and loot structure locations in VillageMemory |
| `src/main/java/com/smartvillager/personality/PersonalityTrait.java` | Enum (BRAVE/CAUTIOUS/GREEDY/GENEROUS) with codec and behavior multiplier methods for profession weight, escort request chance, queue response, and trade pricing |
| `src/main/java/com/smartvillager/personality/VillagerPersonality.java` | Per-villager attachment wrapping PersonalityTrait; random assignment on creation; stored via ModAttachments.VILLAGER_PERSONALITY |
| `src/main/java/com/smartvillager/reputation/PlayerReputation.java` | Player reputation tier thresholds (HOSTILE/NEUTRAL/FRIENDLY/HONORED) and price multiplier lookup used by VillagerTradeEvaluator |
| `src/main/java/com/smartvillager/memory/ThreatRecord.java` | Immutable record (pos, gameTick, cleared) with CODEC; tracks a single threat location in VillageMemory |
| `src/main/java/com/smartvillager/memory/VillageMemory.java` | Persistent village memory: records threat locations (96k-tick expiry) and villager death sites; isFlagged() used by MasonSystem and CartographerSystem |
| `src/main/java/com/smartvillager/registration/ModCreativeTabs.java` | Registers the SmartVillager creative mode tab; currently empty (emerald icon placeholder); add items here as custom blocks/items are introduced |

---

## Patterns

Where to register or store new things:

- **New profession behavior** — register a handler in `ProfessionBehaviorRegistry` via `ProfessionBehaviorRegistry.register(professionKey, handler)` during `FMLCommonSetupEvent`
- **New need type** — add a constant to `NeedTypes`; post via `NeedQueue.postRequest()` and check via `VillageNeedQueue.hasOpenRequest()`
- **New attachment** — declare in `ModAttachments` and register on `ATTACHMENT_TYPES`; access via `entity.getData(ModAttachments.YOUR_ATTACHMENT)`
- **New game event handler** — add a `@SubscribeEvent` method to an `@EventBusSubscriber` class; wire mod-bus events in `SmartVillager` constructor
- **New per-villager data** — store as an attachment in `ModAttachments`; for village-scoped data, store on `SmartVillage` and serialize through `SmartVillage.CODEC`
- **New stockpile chest** — register via `village.getChestTracker().register(blockPos)`; the tracker persists positions through `SmartVillage.CODEC`; never write to or read from a chest that isn't in the tracker
- **New workstation** — register via `village.getWorkstationTracker().register(blockPos, type)`; villagers must path to the registered position to use it; never grant crafted/smelted items without the villager being at a registered workstation
- **New village-owned entity** — add its UUID list to `SmartVillage`; listen for entity remove events to detect death; never use vanilla natural-spawn mechanics for intentional village entities

---

## Rules

- **Check Key Files first** — before exploring the codebase for an unknown file or class, scan the Key Files table above; the responsible file is almost always listed there
- **Use existing registries** — never bypass `ModAttachments`, `ProfessionBehaviorRegistry`, or `VillageRegistry`; always extend them instead of creating parallel structures
- **Finish the task first** — complete the requested change before suggesting refactors, improvements, or follow-up work
- **Update Key Files before finishing** — every new Java file must have a row added to the Key Files table in the same task that created it
- **Update DEV_COMMANDS.md for every new `/sv` command** — whenever a command is added to or removed from `DebugCommands.java`, update `DEV_COMMANDS.md` in the same task: add a new entry under the correct section with the exact syntax, a one-line description, and any relevant testing notes; also add it to the Common Testing Workflows section if it enables a meaningful test scenario
- **Update TEST_CHECKLIST.md for every new testable behavior** — whenever a new system, role behavior, NeedQueue type, command, or in-game mechanic is implemented, add one or more unchecked `- [ ]` items to `TEST_CHECKLIST.md` under the appropriate section in the same task that created the feature; if no section fits, create one; never mark items as passing (`[x]`) yourself — that is the user's job after in-game verification

---

## Current State

| Property | Value |
|---|---|
| Mod version | 0.2.4 |
| Minecraft version | 26.1.2 |
| NeoForge version | 26.1.2.44-beta |

**Completed systems (pending rework per roadmap):** hunger, health, needqueue, village registry, guard defense (→ rework to Weaponsmith/Fletcher/Armorer), patrol (→ rework), day/night cycle, cleric healing, iron golem defense, food chain (farmer, fisherman, shepherd, butcher, leatherworker), defense supply (weaponsmith, armorer, fletcher), mason build (quarrying subrole, build queue, storehouse chest expansion)

**Pending (not yet implemented):** WorkstationTracker, VillagerTradeEvaluator, emerald economy, per-villager dynamic trading, weighted profession assignment, shared subrole pool, Weaponsmith patrol rework, Fletcher ranged defense behavior, Armorer equipment logistics behavior

**Branch pattern:** `feature/description` and `bugfix/description`

**Integration branch:** `dev` (not `main`) — all feature branches merge to `dev` first

**Commit format:** `feat:` or `fix:` prefix required

---

## Maintenance Rules

- Whenever a new Java file is created, add a row to the Key Files table above with its path and a one-line description of its responsibility before finishing the task.
