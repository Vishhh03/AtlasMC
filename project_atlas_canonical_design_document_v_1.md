# Project Atlas
## Canonical Design Document (CDD) v1.0

> **A multiplayer-first RPG game built entirely inside Minecraft using server-side systems.**

---

## 0. DOCUMENT STATUS

- **Status:** Canonical, authoritative
- **Version:** v1.0
- **Scope:** Full vision (not MVP)
- **Persistence Rule:** This document is the *single source of truth*. Any future design, code, or discussion must reference this document.

---

## 1. HIGH-LEVEL VISION

### 1.1 What Project Atlas Is
Project Atlas is a **persistent multiplayer RPG world** hosted inside Minecraft. It is not a modpack, not survival+, and not a minigame network. It is a **systems-driven social RPG**, where progression emerges from cooperation, conflict, economy, politics, and history.

The world is meant to:
- Remember player actions
- Persist across months and years
- Be shaped primarily by players, not admins

### 1.2 What Project Atlas Is NOT
- Not a single-player power fantasy
- Not reset-based gameplay
- Not pay-to-win
- Not gear-score focused
- Not infinite grind

---

## 2. CORE DESIGN PILLARS

1. **Multiplayer-First Design** – No system should be optimal solo
2. **Systems Over Content** – Players create stories
3. **Scarcity Over Abundance** – Value comes from limits
4. **Consequences Over Power** – Every action has a cost
5. **Persistence Over Sessions** – The world remembers
6. **Horizontal Progression** – Power plateaus, relevance persists
7. **Social Capital Matters** – Reputation is gameplay

---

## 3. TECHNICAL CONSTRAINTS

### 3.1 Platform Assumptions
- Server software: Paper-based
- Client: Vanilla Minecraft (no required mods)
- All systems must be server-authoritative

### 3.2 Hard Constraints
- No client-side UI beyond inventories
- **Inventory GUIs (Chest Menus)** are the primary interface.
- No high-frequency combat math
- No true physics simulation
- All mechanics must be low-tick, state-driven

Design implication: systems must favor **deliberate decisions**, not twitch mechanics.

---

## 4. MACRO SYSTEM ARCHITECTURE

Project Atlas is composed of seven primary systems:

1. Identity & Reputation
2. Cities & Politics
3. Economy & Trade
4. Combat & Roles
5. Survival & World Pressure
6. Events & History
7. Endgame & Legacy

Each system is independent but interconnected.

---

## 5. PLAYER IDENTITY & REPUTATION

### 5.1 Persistent Player Identity
Each player has permanent attributes:
- Public Reputation Score
- Hidden Moral Alignment
- Criminal Record
- Social Trust Score
- Titles & Honors
- Player History Log

No global wipes.

### 5.2 Reputation Mechanics
Reputation is affected by:
- PvP behavior
- Theft & crime
- Contracts completed
- City service
- Betrayal or loyalty

Reputation influences:
- NPC interactions
- Market prices
- PvP legality
- City access
- Quest availability

### 5.3 Weakness & Mitigation
- **Alt abuse:** Mitigated via playtime-weighted gains and social validation
- **Griefing:** Mitigated via reputation decay and exile mechanics

---

## 6. CITIES, FACTIONS & POLITICS

### 6.1 Cities as Core Multiplayer Units
Cities are player-founded and player-run.

Each city has:
- Charter (laws)
- Treasury
- Leadership structure
- Controlled zones
- Resource rights

Cities unlock **mechanics**, not raw power.

### 6.2 Political Systems
- Elections
- Appointments
- Coups (high risk)
- Revolutions
- Public trials
- Impeachment

Leadership creates responsibility, not immunity.

### 6.3 Weakness & Mitigation
- **Tyranny:** Revolts, emigration, legitimacy decay
- **Inactivity:** Automatic decay & succession

### 6.4 City Infrastructure & Customization
Cities are not just claims; they are functional engines.
- **City Core**: A central nexus block that defines the city's heart. If destroyed during a siege, the city falls.
- **Infrastructure Modules** (Fallout-style base building):
  - **Defensive**: Arrow Towers (buffs damage), Shield Generators (temp resistance), Reinforced Walls.
  - **Industrial**: Auto-Smelteries (passive processing), Generators (upkeep reduction).
  - **Social**: Clinics (Regen II), Academies (XP boost), Markets (Trade tax).
- **Customization**:
  - Modular building nodes (buy a "Turret Node", place it to spawn a defensive mob).
  - Upgradable tiers for all structures.

---

## 7. ECONOMY & TRADE SYSTEM

### 7.1 Absolute Rules
1. No admin shops
2. No infinite money generation
3. Everything decays or has upkeep
4. Transport has cost and risk

### 7.2 Economic Flow
Resource → Processing → Transport → Market → Tax → Sink

### 7.3 Money Sinks
- Repairs
- Travel
- City upkeep
- Death recovery
- Power maintenance
- Bribes and fines

### 7.4 Weakness & Mitigation
- **Inflation:** Progressive taxation, exponential upkeep
- **Monopolies:** Anti-trust systems, smuggling

---

## 8. COMBAT & ROLES

### 8.1 Role-Based Identity
No hard classes. Roles emerge via usage:
- Frontliner
- Support
- Healer
- Scout
- Control
- Specialist

### 8.2 Multiplayer Combat Rules
- Friendly fire zones
- Morale system
- Injury persistence
- Revives cost resources
- Retreat is valid gameplay

### 8.3 Weakness & Mitigation
- **Zerging:** Stamina scaling, morale penalties
- **Ganking:** PvP legality & bounty systems

---

## 9. SURVIVAL & WORLD PRESSURE

### 9.1 Shared Survival
- Group food pools
- Medical roles
- Shelter ratings
- Sanity sharing
- Disease transmission

### 9.2 Environmental Pressure
- Seasons
- Weather
- Plagues
- Resource depletion
- Invasions

The world constantly pushes back.

---

## 10. EVENTS & WORLD HISTORY

### 10.1 Event Design
- Multi-day events
- Sieges
- Economic crashes
- Plagues
- World bosses

Failure has permanent consequences.

### 10.2 World Memory
- Player monuments
- Named eras
- Historical records
- Retired legends

The world ages.

### 10.3 Siege System (Enhanced Raids)
- **Targeted Raids**: Raids are not random; they target specific Cities based on Treasury size or Threat level.
- **Siege Mechanics**:
  - Waves of custom mobs (Breachers, Snipers, Tanks).
  - Goal: Destroy the **City Core** or kill the Mayor.
  - **Base Defense**: Players must build layouts that funnel enemies into killboxes (Tower Defense elements).
  - **Consequence**: Loss of Treasury, downgrading of Infrastructure levels.

### 10.4 NPCs & Quest System
- **NPC Types**:
  - **Merchants**: Sell rare items for gold. Spawn at structures.
  - **Quest Givers**: Offer combat and collection quests.
- **Dialogue System**: Sequential text with player choices. Opens inventory GUI.
- **Quest Mechanics**:
  - **Objectives**: Kill X mobs, Defend location, Collect items.
  - **Difficulty Tiers**: Easy (100g), Medium (300g), Hard (600g), Nightmare (1500g).
  - **Time Limits**: Optional countdown. Failure = no reward.
  - **Boss Bars**: Show progress for Hard+ quests.
- **Structures**: Random wilderness spawns containing NPCs (Merchant Huts, Quest Camps).

---

## 11. ENDGAME & LEGACY

### 11.1 Endgame Definition
Endgame is:
- Political dominance
- Economic control
- Cultural influence
- Knowledge monopoly

Not gear score.

### 11.2 World Endings
- Server-wide decisions
- Cataclysms
- Era transitions
- Legacy carryover

---

## 12. WEAKNESSES & RISKS

### 12.1 Complexity
Risk: Player overwhelm
Mitigation: Mentorship, diegetic tutorials

### 12.2 Toxicity
Risk: Political abuse
Mitigation: Transparency, exile, reputation

### 12.3 Development Burnout
Risk: Overengineering
Mitigation: Strict MVP gating

---

## 13. MVP PHASING (NON-NEGOTIABLE)

### Phase 1 – Core Loop
- Identity
- Reputation
- Economy
- Single city
- One event type

### Phase 2 – Expansion
- Multiple cities
- Trade routes
- Combat roles
- Survival pressure

### Phase 3 – Warfare & Industry
- **Infrastructure**: Buildable City Modules (Turrets, Generators).
- **Siege Engine**: Targeted PvE Raids on cities.
- **Politics**: Elections and Policies.
- **Legacy**: History recording.

---

## 14. FINAL RULE

If a feature does not:
- Encourage multiplayer interaction
- Create meaningful consequence
- Respect persistence

It does not belong in Project Atlas.

---

**End of Canonical Design Document v1.3**


## 15. RECENT IMPLEMENTATIONS (v1.3)
- **Skill Tree**: A massive passive progression system with PoE-style nodes (Life Leech, Double Jump, Vein Mining).
- **Dungeon System**: Instanced PvE arenas with unique themes (Shadow Cavern, Infernal Pit) and wave-based combat.
- **Relic System**: Rare artifacts spawning in the world with active abilities.
- **World-Based Quests**: Quest boards spawning in the wilderness for organic discovery.

## 16. FUTURE CONCEPTS (BRAINSTORMING)
### 16.1 Active Class Abilities
- **Concept**: Move beyond passive stats. Give classes active skills bound to items.
- **Examples**: 
  - *Vanguard*: Shield Bash (Right-click Shield) -> Stuns enemies.
  - *Scout*: Shadow Step (Right-click Feather) -> Teleport behind target.
  - *Medic*: Healing Circle (Right-click Dye) -> AoE regeneration.

### 16.2 Player Economy (Markets)
- **Concept**: Physical chests that function as shops.
- **Mechanics**:
  - Players rent stalls in Cities or spawn.
  - Set prices for items.
  - Taxes go to the City Treasury.

### 16.3 Visual Territory Control
- **Concept**: See exactly where City influence ends.
- **Mechanics**:
  - Particle borders when holding a specific item (Map/Compass).
  - "Contested Zones" between rival cities.

