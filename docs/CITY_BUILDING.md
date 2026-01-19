# City Building System 🏰

Project Atlas introduces a **Minecraft Legends-style building system**, allowing mayors to construct extensive cities and fortifications in real-time.

## 🛠️ Builder Mode
Enter builder mode to view your city from a third-person perspective and place structures effortlessly.

**Command:** `/atlas build` (or `/b`)

### Controls
| Input | Action |
|-------|--------|
| **SCROLL WHEEL** | Cycle through available structures |
| **LEFT CLICK** | Place the selected structure |
| **RIGHT CLICK** | Rotate the structure preview (90°) |
| **DROP (Q)** | Exit Builder Mode |

### Features
*   **Visual Preview:** See a glowing hologram of the structure before placing it.
    *   **Green:** Valid placement.
    *   **Red:** Invalid (obstructed, out of bounds, or insufficient gold).
*   **City Borders:** Green glowing lines mark the exact edges of your territory.
*   **Extended Range:** Build up to **35 blocks away**.
*   **Construction:** "Builder Allays" appear to animate the construction process.

---

## 🏗️ Structures

Structures cost **Gold** from your city treasury.

### Economic & Support
| Structure | Cost | Utility |
|-----------|------|---------|
| **Quest Camp** | 150g | Spawns a Quest Giver NPC. |
| **Merchant Hut** | 200g | Spawns a Trader NPC. |
| **Generator** | 300g | Generates passive resource income. |
| **Barracks** | 500g | Spawns defenders (Iron Golems) during sieges. |
| **Nexus** | 1000g | Powerful city buff beacon. |

### Defensive Fortifications
Cheap, modular defenses designed for bulk placement to fortify your city against sieges.

| Structure | Cost | Description |
|-----------|------|-------------|
| **Wall** | 25g | 3x4 Stone Brick wall segment. |
| **Wall Corner**| 35g | L-shaped corner connector. |
| **Gate** | 75g | Functional gate with iron bars. |
| **Ramp** | 40g | Stairs for accessing wall battlements. |
| **Turret** | 150g | Auto-turret that shoots hostile mobs and intruders. |
| **Watchtower**| 200g | Tall tower for vision and archery. |

---

## ♻️ Demolish & Refund
Mistakes happen! You can remove misplaced structures.

**Command:** `/atlas build demolish`
*   **Effect:** Removes the structure you are looking at.
*   **Refund:** Returns **75%** of the Gold cost to the city treasury.
*   **Note:** Cannot demolish structures while they are under attack/ruined.

---

## ⚔️ Turret Logic
Turrets are your primary active defense.
*   **Targets:** Hostile Monsters and **Invaders** (Players from rival cities).
*   **Friendly:** Ignores members of your city and **Neutrals** (players with no city).
*   **Stats:** High range, fires critical arrows.
