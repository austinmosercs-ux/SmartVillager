# Villager Society Mod – Project Context

## What this mod does
This mod overhauls Minecraft villagers to function as a real society. Villagers have jobs with actual purpose, communicate with each other to solve problems, trade among themselves, defend their village, and expand it over time. The goal is emergent behavior — a village that feels alive and self-sustaining.

Vanilla villager trading with the player is completely removed. Villagers run their own internal economy. The player is an outsider who can observe, assist, donate, and interact — but the village operates with or without them.

---

## Villager Classes

### New classes
These are new professions tied to custom workblocks.

| Class | Role | Key behaviors |
|---|---|---|
| **Builder** | Expands the village | Fetches materials, pathfinds to build sites, places blocks |
| **Miner** | Gathers raw resources | Mines caves, posts needs to the NeedQueue when blocked by danger |
| **Hunter** | Combat + escort | Patrols, responds to escort requests, engages threats |
| **Guard** | Village defense | Perimeter patrol, broadcasts threat alerts, rallies Hunters |
| **Elder** | Village leadership | One per village, manages the shared inventory and build queue |

### Reframed vanilla classes
Vanilla professions are kept but their behavior is overhauled. They no longer offer vanilla trades. Instead they contribute to the village society.

| Class | Reframed role | Key behaviors |
|---|---|---|
| **Merchant** (replaces generic trader) | Internal + player economy | Only villager the player can buy from; only sells what the village has produced |
| **Cleric** | Village healer | Heals injured villagers after fights, brews potions for combat villagers, heals the player on entry if supplies allow — no menu, no trade, proximity triggered |
| **Toolsmith** | Tool production | Produces mining and building tools for the village |
| **Weaponsmith** | Weapons production | Equips Guards and Hunters, feeds the defense system |
| **Armorer** | Armor production | Handles village defense upgrades and Guard equipment |
| **Librarian** | Knowledge keeper | Tracks NeedQueue history, logs village events, unlocks enchanted tool recipes once village hits prosperity thresholds |
| **Cartographer** | Village mapping | Maps cave systems and surface threats, shares data with Miners and Guards, feeds the village memory system |
| **Fletcher** | Arrow supply | Supplies Hunters with arrows, gives them a resource dependency |
| **Butcher / Fisherman** | Food supply | Support the food side of the village inventory alongside Farmers |
| **Shepherd / Leatherworker** | Early supply roles | Low tier goods, relevant in early village growth before full economy is running |
| **Farmer** | Food production | Farms crops, primary food supplier to shared village inventory |
| **Nitwit** | Nothing | Consumes food and a bed, does no work, has no goals beyond survival — exists as a passive drain on the village economy |

---

## Player Interaction
Vanilla trading is fully removed. The player interacts with the village in these ways only:

- **Merchant** — the only trade interface. Opens a shop UI showing what the village currently has in stock. If the village has no iron, there is no iron for sale. Prices shift based on village supply levels
- **Cleric** — walks up to the player and heals them on village entry, no menu. Only works if the Cleric has enough supplies. No trade, no interaction required — proximity triggered
- **Elder** — can give the player quests (clear a dungeon, deliver materials, escort a Miner). Rewards come from village stock
- **Donating** — player can deposit materials into the village chest directly, boosting prosperity and restocking the Merchant over time
- **Village reputation** — player reputation with the village affects Merchant prices, whether the Elder offers quests, and whether Guards are friendly or hostile

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

- Depletion rate varies by role — physically demanding roles (Miner, Hunter, Guard, Builder) burn hunger faster than passive roles (Librarian, Merchant, Nitwit)
- When hunger drops below a threshold the villager enters a `HUNGRY` state, overrides their job goal, and paths to the village food supply to eat
- If the food supply is empty they cannot eat and begin losing health passively over time
- Nitwits consume food at a normal rate despite contributing nothing — this is intentional and creates real pressure on struggling villages
- A village with too many mouths and not enough Farmers will visibly deteriorate — villagers slow down, get hurt, stop doing their jobs, and the prosperity score drops

### 3. Health System
Every villager has a health value that can be reduced by combat, environment, and starvation.

- **Combat damage** — Hunters and Guards take damage fighting mobs
- **Environmental damage** — Miners can be hurt by cave-ins, lava, or mob encounters underground; Builders can take fall damage
- **Starvation damage** — any villager who cannot eat due to empty food supply loses health passively
- When health drops below a threshold the villager enters a `SEEK_HEALING` state, overrides their job goal, and paths to the Cleric
- If no Cleric is available or the Cleric has no supplies, the villager continues working but at reduced speed and effectiveness
- A villager that reaches zero health dies permanently, triggering the role replacement system
- The Cleric heals the player on proximity using the same supply pool — if the village is struggling the Cleric may have nothing left for the player

### 4. Village Inventory (Shared Economy)
- A shared chest network or data structure attached to the village
- Managed by the Elder
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
  - Miner detects hostile mobs blocking a cave → posts `NEED_ESCORT` with emerald reward → Hunter accepts → escorts Miner to cave and back
  - Miner skips the cave → posts `WANT_TO_BUY: iron` → Merchant fills order from village stock
  - Builder needs wood → posts `NEED_MATERIALS: wood` → Farmer or returning Miner drops it off
  - Injured Hunter has no Cleric available → posts `NEED_HEALING` → Cleric prioritizes them on return
- Keep this system decoupled — economy, defense, and expansion route through the NeedQueue, not through direct calls to each other

### 6. Defense System
- Guards run continuous perimeter patrol routes around the village boundary
- On threat detection, Guard broadcasts a `THREAT_ALERT` to the village
- Hunters receive the alert and rally to engage or escort civilians to shelter
- Non-combat villagers enter a `SHELTER` goal state and pathfind to the nearest building
- Cleric moves to assist injured combat villagers after a fight
- Alert clears after a cooldown with no threats detected
- Village memory logs where threats originated — Miners avoid flagged areas until Guards clear them

### 7. Village Expansion
- Builder villagers check a build queue managed by the Elder
- Build queue populates based on village needs (more villagers = more houses, more Miners = mine entrance structure, etc.)
- Builder state machine: `IDLE → CHECK_QUEUE → FETCH_MATERIALS → PATHFIND_TO_SITE → BUILD → IDLE`
- Builders require tools from the village inventory — they wait or post a NeedRequest if none available
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
- Death consequences ripple through the economy — losing a Miner slows ore supply, losing a Guard increases threat frequency, losing the only Farmer starts a starvation cascade

### 10. Day/Night Cycle Behavior
- Villagers go home and sleep at night — enforced, not optional
- Miners do not enter caves at night
- Guards and Hunters remain active at night on rotation
- Night raids are more dangerous — the village is at reduced capacity
- Cleric stays available at night for emergencies
- A hungry villager will wake and eat before sleeping if food is available

### 11. Villager Personality & Reputation
- Villagers have simple personality traits: brave, cautious, greedy, generous
- Traits bias their NeedQueue decisions — a brave Miner may attempt the cave solo, a cautious one always waits for an escort
- Traits also affect hunger and health behavior — a stubborn villager might ignore the `SEEK_HEALING` goal longer than they should
- Inter-villager reputation: Hunters who consistently complete escort jobs earn trust and better pay over time
- Greedy Merchants charge more and lose customers to other Merchants if prices are too high

### 12. Village Memory
- The village tracks where threats have occurred and where villagers have died
- Miners avoid flagged cave areas until a Guard or Hunter clears them
- Cartographer maintains and shares this map data with relevant villagers

### 13. Inter-Village Trade (stretch goal)
- Two nearby villages can send a Merchant caravan to trade surplus goods
- Caravan needs Hunter escort, creating natural job demand
- Creates a regional economy across multiple villages

---

## Starting State
Villages spawn with little to nothing. This is intentional.

**Starting roster:** one Elder, one or two Farmers, one Guard
**Starting inventory:** minimal food, no tools, no ore, minimal Cleric supplies
**Starting buildings:** basic vanilla village structures only

The village must earn everything else. New classes only appear once prosperity thresholds are met — a Toolsmith won't spawn until there is enough raw ore being produced to justify the role, a Hunter won't spawn until food surplus can support a non-food-producing member. A village that spawns with a Nitwit in its starting roster is immediately at a disadvantage.

---

## Implementation Order
Build in this order to avoid dependency issues:

1. Register new professions and workblocks — get all classes spawning correctly
2. Shared village inventory system + Elder
3. Hunger system — every villager needs this before anything else runs
4. Health system — damage, starvation passive damage, death and role replacement
5. NeedQueue communication system — most important logic, build this carefully
6. Guard patrol + threat alert + Hunter rally + civilian shelter behavior
7. Day/night cycle enforcement
8. Cleric healing (villagers + player) tied to supply levels
9. Toolsmith / Weaponsmith / Armorer chain: Miner deposits ore → processes → Merchant distributes
10. Prosperity score + population growth gating
11. Personality traits + inter-villager reputation + village memory
12. Builder expansion system (requires economy + tools stable first)
13. Inter-village trade caravans (stretch goal)

---

## Notes for the AI
- Always ask for the mod loader and Minecraft version before writing any code — APIs differ significantly between Fabric/Forge and MC versions
- Prefer extending vanilla systems (`Brain`, `BehaviorControl`, POI, workblock registration) over building from scratch
- All villager AI should use the goal/behavior task system, not tick-based overrides
- Vanilla player trading is completely removed — do not implement it or reference it
- The NeedQueue is the backbone of the mod — get it right before building anything on top of it
- Keep systems decoupled: economy, defense, and expansion should not call each other directly
- Hunger and health are survival layer systems — they must override job and social goals when triggered
- The Merchant shop UI should only reflect actual village inventory — never hardcoded trades
- The Cleric healing the player requires no player input — proximity triggered, supply dependent, same supply pool as villager healing
- Nitwits have no goals beyond hunger and sleep — do not assign them any job behavior
- The village should function and evolve whether or not the player is present