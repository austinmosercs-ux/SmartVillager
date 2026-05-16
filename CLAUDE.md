# Villager Society Mod – Project Context

## What this mod does
This mod overhauls Minecraft villagers to function as a real society. Villagers have jobs with actual purpose, communicate with each other to solve problems, trade among themselves, defend their village, and expand it over time. The goal is emergent behavior — a village that feels alive and self-sustaining.

Vanilla villager trading with the player is completely removed. Villagers run their own internal economy. The player is an outsider who can observe, assist, donate, and interact — but the village operates with or without them.

---

## Villager Classes

### Design philosophy
Almost all roles use existing vanilla professions with new behaviors. No new professions are created except **Guard** (combat, no vanilla equivalent) and **Merchant** (player trade interface). Every vanilla villager keeps their workblock as a job site anchor — the block tells them where to go during work hours, but profession is assigned at birth based on village need, not by claiming a block.

Subroles are the second entry in each villager's goal stack. The primary role runs first. The subrole activates when primary work is idle, when a matching NeedQueue request exists, or when the villager's own supply chain is blocked. Every villager's subrole is designed to feed another villager's primary role — collaboration is structural, not optional.

---

### Custom classes

#### Guard
**Job site block:** Bell (vanilla) — Guard paths to the bell at the start and end of each shift; the bell is their patrol origin point and the village rally point during THREAT_ALERT. No custom block needed.

**Capacity:** Each Bell supports exactly 3 Guards. Guards claim a Bell as their assigned post at birth — once a Bell is at capacity, no new Guards are assigned to it until a slot opens (Guard dies or a new Bell is built). Multiple Bells in a village each have their own Guard unit. All Guards across all Bells respond to a village-wide THREAT_ALERT regardless of which Bell they are assigned to. Adding Bells is the only way to grow the Guard force — this is managed through the Mason build queue as the village prospers.

**Primary role:** Village defense and combat
- Runs perimeter patrol routes around the village boundary during the day; one or two Guards rotate on night duty
- On threat detection, broadcasts `THREAT_ALERT` — non-combat villagers enter `SHELTER` state, Cleric prepares to assist
- Engages hostile mobs in melee, prioritizing threats closest to non-combat villagers
- After combat, returns to patrol; Cleric paths to the Guard to heal

**Subrole:** Escort duty
- Monitors NeedQueue for `NEED_ESCORT` requests from Toolsmith, Mason, Fisherman, or Cartographer heading outside safe village boundaries
- Accompanies the requesting villager, fights any threats on the route, returns to patrol once the escorted villager is back
- Also escorts Merchant caravans on inter-village trade runs (stretch goal)

**Deposits:** Nothing — Guard is a consumer of equipment
**Withdraws:** Weapons, armor, and arrows when equipment is damaged or depleted
**Key collaborators:** Weaponsmith, Armorer, Fletcher (equipment supply), Cartographer (threat data and patrol routes), Cleric (post-combat healing)

---

#### Merchant
**Job site block:** None — Merchant has no assigned job site block. During work hours they wander within the village center and path toward the player when they enter the village. Requires a custom idle behavior to keep them from wandering outside the village boundary.

**Appearance:** Uses the Wandering Trader model as a base. Robe color is determined by the biome the village spawns in — assigned once at village creation and shared by all Merchants from that village. Color is purely cosmetic but acts as a visual identifier of which village a Merchant came from when they travel.

| Biome | Robe colors |
|---|---|
| Desert | Cyan, green, or lime |
| Plains | White or yellow |
| Savanna | Orange, red, or yellow |
| Taiga / Snowy Taiga | Blue or purple |
| Snowy Tundra | Blue, red, or white |

**Primary role:** Player trade interface
- Monitors village chest supply levels continuously
- When a player approaches, opens shop UI showing only items currently in stock — no hardcoded trades
- Prices adjust dynamically: high supply = lower prices, low supply = higher prices

**Subrole:** Village supply monitor
- During idle time, scans shared inventory for critically low supplies
- Posts `NEED_RESTOCK: [item]` to NeedQueue when levels fall below thresholds, prompting the responsible producer to act
- If the village consistently runs out of a particular item, flags it to the Librarian for long-term priority adjustment

**Deposits:** Nothing directly — stock comes from all producers
**Withdraws:** Items sold to the player; nothing for personal use
**Key collaborators:** Librarian (supply threshold coordination), all producers (everything the Merchant sells comes from them)

---

### Vanilla professions — detailed breakdown

#### Food Supply Group

##### Farmer
**Primary role:** Grow and harvest crops — primary food source for the village
- Tills soil, plants seeds, harvests mature crops (wheat, carrots, potatoes, beetroot)
- Bakes bread when wheat supply allows; deposits all food to shared inventory
- Maintains farmland — replants after every harvest, expands plots when Mason builds new farmland

**Subrole:** Gather wood and plant materials
- When crops are not yet mature and primary work is idle, chops nearby trees for logs and saplings
- Deposits logs and saplings to shared inventory — Fletcher uses wood for arrows, Mason uses it for early construction
- Replants saplings after chopping to keep the wood supply sustainable

**Deposits:** Food (crops, bread), logs, saplings
**Withdraws:** Hoe, axe (from Toolsmith)
**Key collaborators:** Butcher (food chain), Toolsmith (tool supply), Fletcher (wood supply), Mason (early wood for building)

---

##### Fisherman
**Primary role:** Fish for food — supplements village food supply independently of farmland
- Paths to nearest water source, fishes until inventory is full, returns and deposits to shared inventory
- Critical in early village when farmland is limited, or as a food buffer when crops fail

**Subrole:** Gather water-adjacent resources
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

**Subrole:** Assist Shepherd with animal husbandry
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

**Subrole:** Supply the production chain with animal byproducts
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
- Crafts leather armor pieces — provides early protection for Guards before Armorer is producing iron armor
- Deposits leather goods to shared inventory for Guard use and Merchant sale

**Subrole:** Flex worker in the animal supply chain
- When leather supply is adequate and primary work is idle, assists Butcher with smoker operations or Shepherd with pen maintenance
- Acts as a buffer in the food/animal group when one member is injured or overloaded

**Deposits:** Leather armor pieces, leather goods
**Withdraws:** Leather from Shepherd via shared inventory
**Key collaborators:** Shepherd (leather source), Armorer (hands off leather armor to Guard before iron is available), Guard (early armor recipient)

---

#### Build and Map Group

##### Mason
**Primary role:** Build and expand village structures
- Checks the build queue (managed by Librarian, planned by Cartographer) for pending construction tasks
- Fetches required materials from shared inventory; if stock is low, triggers own quarrying subrole first
- Pathfinds to the build site and places blocks to complete the structure
- State machine: `IDLE → CHECK_QUEUE → FETCH_MATERIALS → PATHFIND_TO_SITE → BUILD → IDLE`
- Requires tools from Toolsmith — waits or posts `NEED_TOOLS` to NeedQueue if none available

**Subrole:** Quarry stone and raw building materials
- When the build queue is empty or materials are low, switches to quarrying
- Mines stone, cobblestone, gravel, and sand at designated quarry zones; takes what the current build task requires and deposits all surplus to shared inventory
- Posts `NEED_ESCORT` if the quarry area is flagged as dangerous by Cartographer before heading out

**Deposits:** Stone, cobblestone, gravel, surplus building materials
**Withdraws:** Tools (from Toolsmith), building materials from shared inventory
**Key collaborators:** Cartographer (build plans and site locations), Toolsmith (tool supply), Guard (escort when quarrying in flagged areas), Librarian (build queue management)

---

##### Cartographer
**Primary role:** Map the village, plan build sites, and track all territory
- Surveys the village boundary, records all existing structure locations, identifies where new structures are needed
- Plans new build sites based on population and prosperity (more villagers → more houses; more Toolsmiths → mine entrance; etc.)
- Shares build site plans with Mason via the Librarian's build queue
- Maps cave systems and underground ore areas, sharing data with Toolsmith so mining runs are efficient

**Subrole:** Scout new areas and flag threats
- Ventures outside the village boundary to explore terrain (posts `NEED_ESCORT` first if the area is unknown)
- Marks hostile mob spawn points, cave entrances, and resource-rich zones
- Feeds threat data to Guards so they can extend patrol routes to cover newly identified danger areas
- Updates village memory with explored territory, cleared threats, and newly discovered resources

**Deposits:** Data — location records, threat flags, build plans (no physical items)
**Withdraws:** Paper, maps (tools of the trade)
**Key collaborators:** Mason (build site plans), Guard (threat map and patrol route data), Toolsmith (cave and ore location data), Librarian (all map data feeds into village memory and build queue)

---

#### Defense Supply Group

##### Weaponsmith
**Primary role:** Craft weapons for Guards
- Monitors Guard equipment condition — checks whether Guards have functional swords and axes
- Draws iron ore and ingots from shared inventory (deposited by Toolsmith's mining subrole); smelts if raw ore is available
- Crafts swords and axes, deposits to shared inventory for Guard withdrawal or delivers directly
- Upgrades weapon tier as ore supply grows (stone → iron → diamond)

**Subrole:** Smelt ore surplus into ingots
- When Guards are fully equipped, smelts raw ore from shared inventory into ingots as a buffer
- Pre-smelted ingots allow both Weaponsmith and Armorer to respond faster when equipment demand spikes after a fight
- Coordinates with Armorer to avoid both smelting simultaneously when ore supply is limited

**Deposits:** Swords, axes, smelted ingots (surplus)
**Withdraws:** Iron ore, coal, ingots from shared inventory
**Key collaborators:** Toolsmith (ore supply), Armorer (full Guard kit coordination — neither duplicates the other's work), Guard (weapon recipient), Fletcher (coordinates ranged vs melee supply)

---

##### Armorer
**Primary role:** Craft armor for Guards
- Monitors Guard armor condition — checks if Guards have complete armor sets
- Draws ingots from shared inventory (smelted by Weaponsmith or self-smelted); crafts helmet, chestplate, leggings, and boots
- Deposits armor to shared inventory or delivers directly to Guard
- Upgrades armor tier progressively — starts with leather (from Leatherworker) then iron, then diamond as prosperity grows

**Subrole:** Manage Guard equipment upgrades and emergency rearming
- When Guards are fully armored, smelts ingots as a buffer reserve for rapid rearming after fights when multiple Guards take damage
- Tracks Guard equipment state — if a Guard dies and their armor is lost, flags it as `URGENT` in the NeedQueue
- Coordinates with Leatherworker on early game to supply leather armor before iron is available

**Deposits:** Armor pieces (helmet, chestplate, leggings, boots)
**Withdraws:** Ingots, leather (early tier), coal from shared inventory
**Key collaborators:** Weaponsmith (full Guard kit coordination and shared smelting), Leatherworker (early armor supply), Toolsmith (ore deposits), Guard (armor recipient)

---

##### Fletcher
**Primary role:** Craft arrows for Guards
- Draws feathers (from Shepherd), sticks and wood (from Farmer), and flint (from Fisherman/Mason) from shared inventory
- Crafts arrows in bulk; deposits to shared inventory for Guard withdrawal
- Monitors Guard arrow count — during active threat periods, prioritizes arrow production above all else

**Subrole:** Gather wood and feathers directly when shared inventory supply is low
- If feathers or sticks are low in the chest, paths to Shepherd's pen to collect directly or helps Farmer chop wood
- Posts `NEED_MATERIALS: feathers` to NeedQueue if Shepherd is not producing enough to sustain arrow supply
- Crafts bows when material supply allows — provides Guards with ranged capability

**Deposits:** Arrows, bows
**Withdraws:** Feathers (Shepherd), sticks/logs (Farmer), flint (Fisherman/Mason) from shared inventory
**Key collaborators:** Shepherd (feather supply), Farmer (wood supply), Fisherman and Mason (flint supply), Guard (arrow and bow recipient)

---

#### Support and Production Group

##### Toolsmith
**Primary role:** Craft tools for the village workforce
- Monitors NeedQueue for tool requests (Mason needs pickaxes, Farmer needs hoes, Fisherman needs rods, Guard needs repairing)
- Draws ingots from shared inventory; crafts the appropriate tool; deposits or delivers directly to the requesting villager
- Tracks which villagers have tools — a Mason without a pickaxe cannot quarry, a Farmer without a hoe cannot farm; these are treated as high-urgency requests

**Subrole:** Mine ore and coal
- When tool demand is met and shared inventory ore supply is low, switches to mining
- Uses Cartographer's cave map data to identify ore-rich areas before heading out
- Posts `NEED_ESCORT` if the target area is flagged as dangerous
- Mines iron ore, coal, and other ores; takes enough for immediate tool production and deposits all surplus — Weaponsmith and Armorer draw from this surplus to equip Guards

**Deposits:** Pickaxes, axes, hoes, fishing rods, shovels, iron ore, coal, surplus ore
**Withdraws:** Ingots (smelted), coal (for own smelting) from shared inventory
**Key collaborators:** Mason (primary tool consumer), Farmer (hoe/axe supply), Fisherman (fishing rod), Weaponsmith and Armorer (ore deposits feed their crafting), Cartographer (cave maps for mining routes), Guard (escort during dangerous mining runs)

---

##### Cleric
**Primary role:** Heal injured villagers and manage the medical supply
- Monitors all villagers' health — when a villager posts `NEED_HEALING` or drops below a health threshold, Cleric paths to them and applies healing
- After a Guard fight, moves to injured Guards and heals using the medical supply pool
- Heals the player on proximity when they enter the village — uses the same supply pool, so a depleted Cleric has nothing left for the player
- Stays available at night for combat emergencies

**Subrole:** Brew potions and maintain supply stockpile
- When no active healing is needed, brews healing potions from ingredients in shared inventory
- Maintains a medical supply reserve large enough to handle a full Guard fight without running dry
- If ingredients are unavailable, posts `NEED_MATERIALS: [ingredient]` to NeedQueue — the Librarian may convert this into a player quest

**Deposits:** Healing potions and supplies (to shared inventory medical reserve)
**Withdraws:** Brewing ingredients, glass bottles, fuel from shared inventory
**Key collaborators:** Guard (primary healing recipient after combat), all villagers (general health maintenance), Librarian (supply shortages flagged for quest generation or NeedQueue escalation)

---

##### Librarian
**Primary role:** Village administrator — coordinate priorities, manage NeedQueue, log all events
- Monitors shared inventory supply levels continuously; posts NeedQueue requests when supplies drop below threshold — acts as the trigger for the whole village economy
- Logs all village events: deaths, completed builds, threats defeated, food shortages, Guard fights
- Manages the build queue: populates it based on village population and prosperity needs; feeds it to Cartographer and Mason
- At prosperity thresholds, unlocks advanced recipes: enchanted tools for Toolsmith, better weapon tiers for Weaponsmith

**Subrole:** Give player quests and manage village reputation
- When a player with sufficient reputation is present, offers quests sourced from active NeedQueue items (clear a dungeon, deliver materials, escort a Toolsmith on a mining run)
- Rewards come from village stock — Librarian controls what the village can afford to give
- Tracks player reputation score — donations and completed quests raise it, attacking villagers drops it

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
Toolsmith mines ore ──────────────────────────────────────► deposits ore surplus ──► Shared Inventory
Toolsmith crafts tools ──────────────────────────────────► Mason, Farmer, Fisherman withdraw
Weaponsmith draws ore ──► smelts ──► crafts weapons ──────► Guard equips
Armorer draws ingots ────────────► crafts armor ──────────► Guard equips
Leatherworker ───────────────────► crafts leather armor ──► Guard (early tier)
Fletcher draws feathers/wood/flint ──► crafts arrows ─────► Guard equips

BUILD CHAIN
Cartographer surveys ──────────► plans build sites ──► feeds Librarian build queue
Mason checks queue ────────────► fetches materials ──► builds structures ──► village grows
Mason quarrying subrole ───────► deposits stone surplus ──► Shared Inventory

DEFENSE CHAIN
Cartographer flags threats ────────────────────────────── Guard extends patrol to flagged area
Guard detects threat ──► THREAT_ALERT ──────────────────► all non-combat villagers SHELTER
Guard engages mob ──► fight resolves ───────────────────► Cleric heals Guard
Weaponsmith / Armorer / Fletcher restock Guard ─────────► Guard ready for next fight
Cartographer marks area cleared ───────────────────────► Toolsmith / Mason resume work in area

ESCORT CHAIN
Toolsmith or Mason posts NEED_ESCORT ──► Guard accepts ──► escorts out and back to village
Cartographer posts NEED_ESCORT for scouting runs ───────► Guard escorts

ADMIN CHAIN
Librarian monitors inventory ──► posts NeedQueue requests ──► producers respond
Librarian manages build queue ─────────────────────────► Mason and Cartographer pull tasks
Librarian logs events ─────────────────────────────────► village memory ──► prosperity score
Cleric flags low medical supply ───────────────────────► Librarian escalates to player quest
```

Each producer takes what their primary role needs first. Surplus flows to shared inventory and becomes available to other roles via direct withdrawal or NeedQueue request. No role calls another directly — everything routes through the shared inventory and the NeedQueue.

---

## Player Interaction
Vanilla trading is fully removed. The player interacts with the village in these ways only:

- **Merchant** — the only trade interface. Opens a shop UI showing what the village currently has in stock. If the village has no iron, there is no iron for sale. Prices shift based on village supply levels
- **Cleric** — walks up to the player and heals them on village entry, no menu. Only works if the Cleric has enough supplies. No trade, no interaction required — proximity triggered
- **Librarian** — can give the player quests (clear a dungeon, deliver materials, escort a Toolsmith on a mining run). Rewards come from village stock
- **Donating** — player can deposit materials into the village chest directly, boosting prosperity and restocking the Merchant over time
- **Village reputation** — player reputation with the village affects Merchant prices, whether the Librarian offers quests, and whether Guards are friendly or hostile

The village does not need the player to survive. The player is an optional participant.

---

## Core Systems

### 1. Villager AI Goal Stack
Each villager evaluates goals in priority order:
1. **Survival** — eating when hungry, seeking the Cleric when injured, sheltering from threats
2. **Job task** — class-specific behavior (mine, build, craft, guard, etc.)
3. **Social** — post needs, respond to requests, negotiate, hire

A starving or badly injured villager will not perform their job until their survival need is met. Use the vanilla `Brain` / `BehaviorControl` system. Each class registers its own set of behavior tasks into the brain. Extend `VillagerEntity` or the loader equivalent — do not build new entities from scratch.

### 2. Hunger System
Every villager has a hunger value that depletes over time.

- Depletion rate varies by role — physically demanding roles (Toolsmith mining, Mason quarrying, Guard patrolling) burn hunger faster than passive roles (Librarian, Merchant, Nitwit)
- When hunger drops below a threshold the villager enters a `HUNGRY` state, overrides their job goal, and paths to the village food supply to eat
- If the food supply is empty they cannot eat and begin losing health passively over time
- Nitwits consume food at a normal rate despite contributing nothing — this is intentional and creates real pressure on struggling villages
- A village with too many mouths and not enough Farmers will visibly deteriorate — villagers slow down, get hurt, stop doing their jobs, and the prosperity score drops

### 3. Health System
Every villager has a health value that can be reduced by combat, environment, and starvation.

- **Combat damage** — Guards take damage fighting mobs
- **Environmental damage** — Toolsmith and Mason can be hurt by cave-ins, lava, or mob encounters while gathering; Mason can take fall damage during construction
- **Starvation damage** — any villager who cannot eat due to empty food supply loses health passively
- When health drops below a threshold the villager enters a `SEEK_HEALING` state, overrides their job goal, and paths to the Cleric
- If no Cleric is available or the Cleric has no supplies, the villager continues working but at reduced speed and effectiveness
- A villager that reaches zero health dies permanently, triggering the role replacement system
- The Cleric heals the player on proximity using the same supply pool — if the village is struggling the Cleric may have nothing left for the player

### 4. Village Inventory (Shared Economy)
- A shared chest network or data structure attached to the village
- Coordinated by the Librarian — tracks supply levels, flags shortages, and drives NeedQueue requests
- All villagers deposit and withdraw based on role and need
- Tracks supply levels — low supply triggers job requests via the NeedQueue
- Player can deposit directly to boost village stock
- Merchant pulls from this inventory to determine what is available for sale
- Cleric pulls healing supplies from this inventory

### 5. NeedQueue (Communication System)
The backbone of the mod. A server-side queue scoped to each village.

- Villagers post a `NeedRequest` containing: `requestType`, `reward`, `urgency`, `poster`
- Other villagers check the queue during idle state and accept matching jobs
- Example flows:
  - Toolsmith detects hostile mobs while mining → posts `NEED_ESCORT` → Guard accepts → escorts Toolsmith to mine and back
  - Weaponsmith needs ore and chest is empty → posts `WANT_TO_BUY: iron` → Toolsmith prioritizes a mining run
  - Mason needs stone and has none → posts `NEED_MATERIALS: stone` → Mason's own subrole triggers a quarry run, or another Mason drops off surplus
  - Injured Guard has no Cleric available → posts `NEED_HEALING` → Cleric prioritizes them on return
- Keep this system decoupled — economy, defense, and expansion route through the NeedQueue, not through direct calls to each other

### 6. Defense System
- Guards run continuous perimeter patrol routes around the village boundary
- On threat detection, Guard broadcasts a `THREAT_ALERT` to the village
- Non-combat villagers enter a `SHELTER` goal state and pathfind to the nearest building
- Cleric moves to assist injured Guards after a fight
- Alert clears after a cooldown with no threats detected
- Village memory logs where threats originated — Toolsmith and Mason avoid flagged areas until Guards clear them
- Guards also respond to `NEED_ESCORT` from Toolsmith or Mason heading out to gather materials

### 7. Village Expansion
- Mason villagers check a build queue coordinated by the Librarian
- Build queue populates based on village needs (more villagers = more houses, more Toolsmiths = mine entrance structure, etc.)
- Cartographer plans build sites and shares locations with Mason
- Mason state machine: `IDLE → CHECK_QUEUE → FETCH_MATERIALS → PATHFIND_TO_SITE → BUILD → IDLE`
- Mason requires tools from the village inventory (produced by Toolsmith) — waits or posts a NeedRequest if none available
- Build this system last — it depends on the economy and NeedQueue being stable

### 8. Village Prosperity Score
A hidden score tracking overall village health. Drives growth and unlocks.

Increases with: successful trades, buildings completed, threats defeated, food surplus, player donations, villagers at full health and hunger
Decreases with: villager deaths, unmet needs, food shortage, villagers starving or injured with no Cleric, failed build attempts

Drives:
- New villager spawns (only when food, beds, and jobs exist to support them)
- Building tier unlocks
- Merchant stock volume and variety
- Librarian unlocking enchanted tool recipes for Toolsmiths and Weaponsmiths

### 9. Population & Role Control
- Villages start small and grow only when they can support new members (food + beds + available job)
- New villager class is assigned based on what the village needs most at time of birth
- If the only critical role villager dies, the village flags it and prioritizes replacing it at next birth
- Death consequences ripple through the economy — losing a Toolsmith slows ore and tool supply, losing a Guard increases threat frequency, losing the only Farmer starts a starvation cascade, losing the Mason halts village expansion

### 10. Day/Night Cycle Behavior
- Villagers go home and sleep at night — enforced, not optional
- Toolsmith and Mason do not go out to gather materials at night
- Guards remain active at night on rotation — the only villagers with a night work goal
- Night raids are more dangerous — the village is at reduced capacity
- Cleric stays available at night for emergencies
- A hungry villager will wake and eat before sleeping if food is available

### 11. Villager Personality & Reputation
- Villagers have simple personality traits: brave, cautious, greedy, generous
- Traits bias their NeedQueue decisions — a brave Toolsmith may attempt a mining run solo, a cautious one always posts `NEED_ESCORT` first
- Traits also affect hunger and health behavior — a stubborn villager might ignore the `SEEK_HEALING` goal longer than they should
- Inter-villager reputation: Guards who consistently complete escort jobs earn trust and better pay over time
- Greedy Merchants charge more and lose village favor if prices are too high relative to supply

### 12. Village Memory
- The village tracks where threats have occurred and where villagers have died
- Toolsmith and Mason avoid flagged areas until a Guard clears them
- Cartographer maintains and shares this map data with Guards, Toolsmith, and Mason

### 13. Two-Mode Village Simulation
Villages run in one of two modes depending on player proximity. This keeps the village economy running without the RAM cost of permanently force-loaded chunks.

**Full simulation** — player is within ~128 blocks
- Village chunks are loaded normally
- Villagers physically move, pathfind, and execute AI tasks in real time
- All systems run at full tick rate

**Abstract simulation** — player is beyond ~128 blocks
- Village chunks are unloaded to free RAM
- Village state is stored as lightweight data: hunger levels, food supply, tool counts, NeedQueue entries, health values, build progress
- A batch update runs every few minutes and calculates what would have happened — food consumed, tools produced, ore mined, structures advanced, threats resolved — and applies it to the stored state
- No pathfinding, no entity AI, no chunk overhead

**Reconciliation** — when player returns within range
- Chunks reload and full simulation resumes
- Abstract state is applied to the physical village — villagers spawn at correct health and hunger, inventory reflects what was produced, any deaths that occurred are applied
- The village should feel like it kept running the whole time

This approach allows multiple villages to exist and simulate simultaneously without stacking hundreds of MB of loaded chunks per village.

### 14. Inter-Village Trade (stretch goal)
- When a village has surplus goods and the Cartographer has mapped a nearby village, the Librarian flags a trade run
- Merchant posts `NEED_ESCORT` → Guard accompanies them to the destination village and back
- Merchant carries surplus goods from their home village's inventory and returns with goods the home village lacks
- A visiting Merchant's robe color identifies which village they came from — a cyan-robed Merchant in a Plains village is visibly a Desert village traveler
- Creates a regional economy across multiple villages where biome resources flow between settlements

---

## Starting State
Villages spawn with little to nothing. This is intentional.

**Starting roster:** one Librarian, one or two Farmers, one Guard
**Starting inventory:** minimal food, no tools, no ore, minimal Cleric supplies
**Starting buildings:** basic vanilla village structures only

The village must earn everything else. New professions only appear once prosperity thresholds are met — a Toolsmith won't appear until the village can support a non-food role, a Weaponsmith won't appear until the Toolsmith is producing enough ore surplus to feed a crafting chain. A village that spawns with a Nitwit in its starting roster is immediately at a disadvantage.

---

## Implementation Order
Build in this order to avoid dependency issues:

1. Register Guard and Merchant as custom professions — all other roles use vanilla professions with overridden behavior
2. Shared village inventory system + Librarian as coordinator
3. Hunger system — every villager needs this before anything else runs
4. Health system — damage, starvation passive damage, death and role replacement
5. NeedQueue communication system — most important logic, build this carefully
6. Guard patrol + threat alert + civilian shelter behavior
7. Day/night cycle enforcement
8. Cleric healing (villagers + player) tied to supply levels
9. Toolsmith mining subrole + Weaponsmith/Armorer crafting chain: Toolsmith deposits ore → Weaponsmith/Armorer process → Guard equips
10. Mason building subrole + Cartographer planning: Cartographer maps sites → Mason builds
11. Prosperity score + population growth gating
12. Personality traits + inter-villager reputation + village memory
13. Inter-village trade caravans (stretch goal)

---

## Notes for the AI
- Always ask for the mod loader and Minecraft version before writing any code — APIs differ significantly between Fabric/Forge and MC versions
- Prefer extending vanilla systems (`Brain`, `BehaviorControl`, POI, workblock registration) over building from scratch
- All villager AI should use the goal/behavior task system, not tick-based overrides
- Vanilla player trading is completely removed — do not implement it or reference it
- Only two custom professions exist: Guard and Merchant. Every other role is a vanilla profession with new behavior tasks registered into the Brain
- Guard uses the vanilla Bell as their job site block — do not register a custom workblock for Guard
- Workblocks are job site anchors only — profession is assigned at birth based on village need, not by villager claiming a block
- Subroles are secondary goal stack entries — a Toolsmith's primary goal is crafting tools, the mining subrole activates when primary work is idle or when ore is needed
- The NeedQueue is the backbone of the mod — get it right before building anything on top of it
- Keep systems decoupled: economy, defense, and expansion should not call each other directly
- Hunger and health are survival layer systems — they must override job and social goals when triggered
- The Merchant shop UI should only reflect actual village inventory — never hardcoded trades
- The Cleric healing the player requires no player input — proximity triggered, supply dependent, same supply pool as villager healing
- Nitwits have no goals beyond hunger and sleep — do not assign them any job behavior
- The Librarian coordinates the village — manages the NeedQueue log, tracks supply levels, drives build queue population; treat them as the administrative brain of the village
- The village should function and evolve whether or not the player is present
- Villages run in two modes: full simulation (chunks loaded, player within ~128 blocks) and abstract simulation (chunks unloaded, state tracked as data with batch updates). Never force-load village chunks permanently — this would stack hundreds of MB of RAM per village
- Abstract simulation must track at minimum: per-villager hunger and health, shared inventory contents, NeedQueue state, build queue progress, and any villager deaths — enough to reconcile correctly when full simulation resumes

---

## Key Files

| File | Responsibility |
|---|---|
| `src/main/java/com/smartvillager/SmartVillager.java` | Main mod entry point that registers professions and entity attachments |
| `src/main/java/com/smartvillager/registration/ModProfessions.java` | Registers custom Guard and Merchant villager professions with their POI associations |
| `src/main/java/com/smartvillager/registration/ModAttachments.java` | Defines NeoForge entity attachments for villager backpack, hunger, and health data |
| `src/main/java/com/smartvillager/ai/ProfessionBehaviorRegistry.java` | Centralizes profession-specific Brain behavior injection hooked into villager brain refresh |
| `src/main/java/com/smartvillager/village/SimulationMode.java` | Enum for full vs. abstract village simulation modes based on player proximity |
| `src/main/java/com/smartvillager/village/MerchantColor.java` | Maps villager biome types to merchant robe color palettes with random selection per village |
| `src/main/java/com/smartvillager/village/VillageRegistry.java` | Persisted SavedData registry mapping villages by UUID and Bell anchor position |
| `src/main/java/com/smartvillager/village/VillageStockpile.java` | Shared village inventory for depositing and withdrawing items tracked by ItemStack counts |
| `src/main/java/com/smartvillager/village/LibrarianCoordinator.java` | Scans village stockpile against thresholds to detect and flag shortages for NeedQueue posting |
| `src/main/java/com/smartvillager/village/VillagerInteractionHandler.java` | Blocks vanilla villager trading entirely to route interactions through mod systems |
| `src/main/java/com/smartvillager/village/SmartVillage.java` | Stores all persistent village data including roster, stockpile, threat state, and abstract health tracking |
| `src/main/java/com/smartvillager/village/VillageDetector.java` | Detects vanilla villages, assigns professions at birth, and switches simulation modes based on player proximity |
| `src/main/java/com/smartvillager/inventory/ArmorSlot.java` | Enum for the four armor equipment slots in villager backpack |
| `src/main/java/com/smartvillager/inventory/VillagerBackpack.java` | Per-villager personal inventory with 15 general slots and 4 armor slots, serializable and defensive |
| `src/main/java/com/smartvillager/hunger/VillagerHunger.java` | Per-villager hunger value attachment with depletion and restoration mechanics |
| `src/main/java/com/smartvillager/hunger/HungerDepletionRates.java` | Maps profession identifiers to hunger depletion rates (fast/normal/slow) |
| `src/main/java/com/smartvillager/hunger/HungerSystem.java` | Manages hunger depletion and stockpile-based feeding for both full and abstract simulations |
| `src/main/java/com/smartvillager/health/HealthSystem.java` | Applies starvation damage and manages health state in both full and abstract simulations |
| `src/main/java/com/smartvillager/health/VillagerHealth.java` | Per-villager health attachment that syncs with vanilla entity and tracks healing needs |
| `src/main/java/com/smartvillager/needqueue/NeedPriority.java` | Enum (low/normal/high/urgent) for prioritizing need requests |
| `src/main/java/com/smartvillager/needqueue/NeedTypes.java` | Defines all need request type identifiers for village communication (escort, healing, tools, etc.) |
| `src/main/java/com/smartvillager/needqueue/NeedRequest.java` | Immutable request record with id, type, priority, poster, itemData, and acceptance status |
| `src/main/java/com/smartvillager/needqueue/VillageNeedQueue.java` | Per-village queue with open and in-progress request lists, supporting posting/acceptance/completion/expiry |
| `src/main/java/com/smartvillager/needqueue/NeedQueue.java` | System-level stateless logic syncing LibrarianCoordinator shortages to queue posts and expiring stale requests |
| `src/main/java/com/smartvillager/mixin/MixinVillager.java` | Mixin hook injecting profession-specific behavior into Villager.refreshBrain() call chain |
| `src/main/java/com/smartvillager/command/DebugCommands.java` | Debug commands for listing nearby villagers, viewing/adding stockpile items, and broadcasting villager thoughts |
| `src/main/java/com/smartvillager/defense/GuardDefenseSystem.java` | Drives guard combat, threat detection, and civilian shelter orders during threat alerts |
| `src/main/java/com/smartvillager/defense/PatrolSystem.java` | Generates circular waypoint patrols around the Bell anchor for Guards during peaceful periods |

---

## Patterns

Where to register or store new things:

- **New profession behavior** — register a handler in `ProfessionBehaviorRegistry` via `ProfessionBehaviorRegistry.register(professionKey, handler)` during `FMLCommonSetupEvent`
- **New profession** — declare in `ModProfessions` and register its POI association; add to `VillageDetector.chooseProfession()` if it should be auto-assigned at birth
- **New need type** — add a constant to `NeedTypes`; post via `NeedQueue.postRequest()` and check via `VillageNeedQueue.hasOpenRequest()`
- **New attachment** — declare in `ModAttachments` and register on `ATTACHMENT_TYPES`; access via `entity.getData(ModAttachments.YOUR_ATTACHMENT)`
- **New game event handler** — add a `@SubscribeEvent` method to an `@EventBusSubscriber` class; wire mod-bus events in `SmartVillager` constructor
- **New per-villager data** — store as an attachment in `ModAttachments`; for village-scoped data, store on `SmartVillage` and serialize through `SmartVillage.CODEC`

---

## Rules

- **Check Key Files first** — before exploring the codebase for an unknown file or class, scan the Key Files table above; the responsible file is almost always listed there
- **Use existing registries** — never bypass `ModProfessions`, `ModAttachments`, `ProfessionBehaviorRegistry`, or `VillageRegistry`; always extend them instead of creating parallel structures
- **Finish the task first** — complete the requested change before suggesting refactors, improvements, or follow-up work
- **Update Key Files before finishing** — every new Java file must have a row added to the Key Files table in the same task that created it

---

## Current State

| Property | Value |
|---|---|
| Mod version | 0.2.4 |
| Minecraft version | 26.1.2 |
| NeoForge version | 26.1.2.44-beta |

**Completed systems:** hunger, health, needqueue, village registry, guard defense, patrol, day/night cycle

**Branch pattern:** `feature/description` and `bugfix/description`

**Integration branch:** `dev` (not `main`) — all feature branches merge to `dev` first

**Commit format:** `feat:` or `fix:` prefix required

---

## Maintenance Rules

- Whenever a new Java file is created, add a row to the Key Files table above with its path and a one-line description of its responsibility before finishing the task.