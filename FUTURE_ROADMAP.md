# SmartVillager – Future Development Roadmap

Future features and systems beyond the current implementation scope.

---

## Planned Roles

### Engineer
**Job site block:** Smithing Table — Engineers claim a Smithing Table as their workstation at birth; the block is their anchor during work hours.

**Capacity:** One Engineer per village until prosperity reaches the mid-tier threshold; a second slot unlocks when the first storehouse expansion is complete.

**Primary role:** Automate village systems with redstone infrastructure
- Designs and builds redstone-based automation for the village — automatic doors, pressure-plate entry gates, lighting networks, and alarm tripwires around the perimeter
- Builds and maintains a minecart chest rail between the storehouse and key production sites (Shepherd pen, quarry entrance, Farmer plots) so materials travel automatically without villagers walking the full route
- When the Mason completes a new structure, the Engineer follows up to wire it — lights, doors, and any mechanical fittings that structure needs
- Monitors existing redstone infrastructure for failures (burnt-out components, blocked rails, broken wiring) and repairs them as their primary maintenance loop

**Subrole 1:** Craft redstone components for the stockpile
- Continuously crafts redstone devices from stockpile materials and deposits them as a general village supply — pistons, sticky pistons, observers, comparators, droppers, dispensers, hoppers, rails, powered rails, and detector rails
- These items flow into shared inventory like any other produced good — the Merchant can sell them to the player, other villagers can withdraw them for build tasks, and the Engineer draws from this buffer for their own automation projects instead of crafting on demand
- Maintains a target quantity for each component type; once a type is at stock level, switches to the next item in the queue
- If redstone dust, iron, or gold supply is low, posts `NEED_MATERIALS: [item]` to NeedQueue — Toolsmith prioritizes those ores on the next mining run

**Subrole 2:** Pre-craft components for queued build projects
- When an automation project is next in the build queue, reviews the required materials and crafts any missing components before fetching materials for the site
- Prevents mid-build stalls where the Engineer reaches a site and discovers a component is not in stock
- Surplus components from project prep are deposited back to shared inventory rather than held personally

**Deposits:** Redstone components (pistons, sticky pistons, observers, comparators, droppers, dispensers, hoppers, rails, powered rails, detector rails), lighting (lanterns, torches)
**Withdraws:** Redstone dust, iron ingots, gold ingots, stone, wood from shared inventory; tools from Toolsmith
**Key collaborators:** Mason (builds structures the Engineer then wires), Toolsmith (ore supply for components), Cartographer (structure locations and rail route planning), Librarian (build queue entries for automation projects)

**Design notes:**
- The minecart chest network is the signature output — a village with a working rail line between storehouse and production sites moves goods significantly faster, visible to the player as a sign of prosperity
- Engineer does not overlap with Mason — Mason places structural blocks, Engineer places functional and mechanical blocks on top of what Mason builds
- Automation projects are fed through the Librarian's build queue like Mason tasks; Cartographer plans the routes and posts them to the queue
- An Engineer is a mid-game unlock — the village needs a stable ore supply (Toolsmith), a built storehouse (Mason), and mapped infrastructure (Cartographer) before an Engineer can do useful work

---

## Status Types

| Status | Description |
|---|---|
| _(empty)_ | Not started |
| In Progress | Currently in development |
| In Review | Final checks before merge |
| Done | Merged and released |
