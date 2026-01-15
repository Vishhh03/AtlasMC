# Project Atlas - Progression System Design

## Philosophy
Like Elden Ring, progression is **gated by achievement**, not just time.
Like Minecraft, each tier unlocks new **dimensions and mechanics**.
Unlike both, we add **social/city requirements** for multiplayer focus.

---

## 🗺️ THE FIVE ERAS OF PROGRESSION

### ERA 0: AWAKENING (Hours 0-5)
**Theme**: Survival basics, tutorial phase

**Unlocked From Start**:
- Basic survival mechanics
- Food/healing system
- Party system (up to 3 players)
- Quest boards in wilderness
- Level 1-10 progression

**Gated Content** (Requires completion to progress):
- [ ] Reach Level 5
- [ ] Complete 3 wilderness quests
- [ ] Craft a full set of iron gear
- [ ] Sleep in a bed (establish respawn)

**Boss Gate**: **The Hollow Knight** (Spawns at night, Level 5+)
- Custom Wither Skeleton with 100 HP
- Drops: **Era Key Fragment (Settler)**
- Required to found/join a city

---

### ERA 1: SETTLEMENT (Hours 5-15)
**Theme**: City building, economy basics

**Unlocked After Era 0**:
- City creation/joining
- Player shops
- Basic city infrastructure (Clinic, Market)
- Skill Tree access (was locked!)
- Level 11-25 progression
- Party size increases to 5

**Gated Content**:
- [ ] Join or found a city
- [ ] Earn 1,000 gold through trading
- [ ] City must have 3+ members
- [ ] Build first infrastructure (any)
- [ ] Reach Level 15

**Boss Gate**: **The Tax Collector** (Raid event, Level 15+)
- Triggered when city treasury reaches 2,000g
- Wave of Illager-type enemies
- Drops: **Era Key Fragment (Explorer)**
- Required to access the Nether

---

### ERA 2: EXPEDITION (Hours 15-30)
**Theme**: Nether exploration, advanced combat

**Unlocked After Era 1**:
- Nether Portal activation (was blocked!)
- Advanced skill tree branches (Berserker, Scout)
- Dungeon system access
- City siege mechanics
- Level 26-40 progression
- Relic system

**Gated Content**:
- [ ] Collect 3 Blaze Rods
- [ ] Defeat a Dungeon (any tier)
- [ ] City must survive a siege
- [ ] Reach Level 30
- [ ] Obtain 1 Relic

**Boss Gate**: **The Warden of Flames** (Nether Fortress boss)
- Custom Blaze King with 300 HP, summons minions
- Drops: **Era Key Fragment (Ascendant)**
- Required to craft Eyes of Ender

---

### ERA 3: ASCENSION (Hours 30-50)
**Theme**: End preparation, political power

**Unlocked After Era 2**:
- End Portal activation (was blocked!)
- City alliances/wars
- Advanced infrastructure (Barracks, Turrets)
- Keystone skill nodes
- Level 41-60 progression

**Gated Content**:
- [ ] Craft 12 Eyes of Ender
- [ ] City must reach Tier 3 (5+ infrastructure)
- [ ] Win a city siege (as attacker OR defender)
- [ ] Reach Level 50
- [ ] Complete a Nightmare-tier dungeon

**Boss Gate**: **The Ender Sentinel** (End Gateway guardian)
- Spawns when entering End for first time
- Custom Enderman boss with 500 HP, teleport attacks
- Drops: **Era Key Fragment (Legendary)**
- Required to challenge the Dragon

---

### ERA 4: LEGEND (Hours 50+)
**Theme**: Endgame, legacy content

**Unlocked After Era 3**:
- Ender Dragon fight (enhanced!)
- World events (server-wide)
- Legacy titles and monuments
- Level 61+ (no cap)
- Full skill tree

**Gated Content** (Repeatable challenges):
- [ ] Defeat the Ender Dragon
- [ ] Establish a trade empire (10,000g treasury)
- [ ] Become Mayor of a capital city
- [ ] Complete all dungeon tiers
- [ ] Collect all Relics

**Final Boss**: **The Ender Dragon (Enhanced)**
- 600 HP (up from 200)
- New attack patterns
- Requires party coordination
- Drops: **Legendary Relic** + **World Monument**

---

## 🔐 GATE MECHANICS

### Era Keys
- Collected from Era Bosses
- Stored in player profile
- Cannot be traded
- Display in `/atlas progress`

### Level Requirements
| Era | Min Level | Max Level |
|-----|-----------|-----------|
| 0   | 1         | 10        |
| 1   | 5         | 25        |
| 2   | 15        | 40        |
| 3   | 30        | 60        |
| 4   | 50        | ∞         |

### Dimension Locks
- **Nether**: Blocked until Era 2 Key
- **End**: Blocked until Era 3 Key
- Portals show error: "You lack the power to traverse this gateway..."

### Feature Locks
| Feature | Unlocked Era | Requirement |
|---------|--------------|-------------|
| Skills  | 1            | Join a city |
| Dungeons| 2            | Nether access |
| Sieges  | 2            | City Tier 2 |
| Relics  | 2            | First dungeon|
| Alliances| 3           | Era 3 Key |
| Dragon  | 4            | Era 4 Key |

---

## 📊 DIFFICULTY SCALING

### Mob Scaling by Era
| Era | Mob HP | Mob Damage | Spawn Rate |
|-----|--------|------------|------------|
| 0   | 100%   | 100%       | Normal     |
| 1   | 120%   | 110%       | +10%       |
| 2   | 150%   | 130%       | +25%       |
| 3   | 200%   | 150%       | +40%       |
| 4   | 250%   | 175%       | +50%       |

### Boss HP Scaling (Per Player)
- Solo: 100% HP
- 2 Players: 150% HP
- 3 Players: 200% HP
- 4+ Players: +40% per additional

---

## 🎯 MILESTONE REWARDS

### Era 0 → Era 1
- 500 gold bonus
- "Settler" title
- Skill tree access
- 3 skill points bonus

### Era 1 → Era 2
- 1,000 gold bonus
- "Explorer" title
- Nether access
- Dungeon access
- 5 skill points bonus

### Era 2 → Era 3
- 2,000 gold bonus
- "Ascendant" title
- End access
- Full skill tree
- 10 skill points bonus

### Era 3 → Era 4
- 5,000 gold bonus
- "Legend" title
- Legacy monument
- 20 skill points bonus
- Server announcement

---

## 💀 DEATH PENALTIES BY ERA

| Era | Gold Loss | Item Drop | Respawn |
|-----|-----------|-----------|---------|
| 0   | 0%        | None      | Bed/Spawn |
| 1   | 5%        | None      | Bed/City |
| 2   | 10%       | Hotbar risk | City only |
| 3   | 15%       | 5 random  | City only |
| 4   | 20%       | 10 random | Capital only |

*Soul Binding skill reduces item drops*

---

## 🔄 REPLAYABILITY

### New Character Bonuses
- Alt characters start with 10% of main's gold
- Shared cosmetic unlocks
- Reduced Era requirements (75% after first clear)

### Seasonal Resets (Optional)
- Era progress resets
- Gold/items preserved
- New seasonal titles
- Leaderboards reset

---

## IMPLEMENTATION PRIORITY

1. **ProgressionManager** - Track era/milestones per player
2. **EraGate System** - Block portals/features
3. **Era Bosses** - Custom boss entities
4. **Milestone Tracker** - GUI showing progress
5. **Rewards System** - Bonus distribution
