# Project Atlas
## Canonical Design Document (CDD) v2.0

> **A multiplayer-first RPG game built entirely inside Minecraft using server-side systems.**

---

## 0. DOCUMENT STATUS

- **Status:** Canonical, authoritative
- **Version:** v2.0
- **Last Updated:** January 2026
- **Scope:** Full vision and current implementations
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

## 3. TECHNICAL ARCHITECTURE

### 3.1 Platform
- Server software: Paper 1.21.x
- Client: Vanilla Minecraft (no required mods)
- All systems are server-authoritative
- Custom resource pack for visual enhancements

### 3.2 Core Systems (46 Managers)
```
Identity & Social          Economy & Trade           Combat & Progression
├── IdentityManager        ├── EconomyManager        ├── ProgressionManager
├── ChatManager            ├── MarketManager         ├── EraBossManager
├── PartyManager           ├── BountyManager         ├── SkillTreeManager
├── AchievementManager     └── VillageTradeManager   ├── SurvivalManager
└── HistoryManager                                   └── MobCustomizer

City & Territory           World & Events            Dungeons & Content
├── CityManager            ├── EventManager          ├── DungeonManager
├── SiegeManager           ├── GlobalThreatManager   ├── QuestManager
├── WonderManager          ├── WorldBossManager      ├── QuestBoardManager
├── OutpostManager         ├── AtmosphereManager     ├── RelicManager
└── PoliticsManager        └── SupplyDropListener    └── NPCManager

Visual & Animation         Structures                Utilities
├── AnimationSystem        ├── StructureManager      ├── ConfigManager
├── VisualManager          ├── StructureHealthManager├── GuiManager
├── PacketManager          ├── BlueprintMarketplace  ├── MapManager
├── AtmosphereManager      ├── SchematicManager      └── QoLManager
└── ResourcePackManager    └── SummoningAltarManager
```

### 3.3 Technical Constraints
- Inventory GUIs (Chest Menus) are the primary interface
- All mechanics are low-tick, state-driven
- Design favors **deliberate decisions**, not twitch mechanics

---

## 4. PLAYER PROGRESSION SYSTEM

### 4.1 Era-Based Progression
Players progress through distinct eras, each with milestones and a boss:

| Era | Name | Requirements | Boss |
|-----|------|--------------|------|
| 0 | Awakening | Basic survival milestones | Hollow Knight |
| 1 | Settlement | Join/create city, infrastructure | Tax Collector |
| 2 | Dominion | Dungeons, advanced structures | Warden of Flames |
| 3 | Ascension | Wonders, full specialization | Ender Sentinel |

### 4.2 Skill Tree System
Five skill branches with 25+ nodes:
- **Combat**: Damage, armor, critical hits
- **Survival**: Health, resistance, swimming
- **Rest**: Sleep bonuses, regeneration
- **Trade**: Discounts, reputation bonuses
- **Siege**: Defense bonuses during city sieges

### 4.3 Class System
Soft classes emerge through skill investment:
- Vanguard (Tank)
- Scout (DPS)
- Medic (Support)
- Diplomat (Trade)

---

## 5. CITY & POLITICAL SYSTEMS

### 5.1 City Features
- **Treasury**: Gold-based funding from taxes and deposits
- **Energy**: Redstone-based power for structures
- **Mana**: Lapis-based resource for special abilities
- **Territory**: Chunk-based claims with scaling costs

### 5.2 Infrastructure Modules
| Category | Module | Effect |
|----------|--------|--------|
| Defense | Walls | Reduce siege damage |
| Defense | Turrets | Auto-attack during sieges |
| Defense | Barracks | Spawn Iron Golem defenders |
| Economy | Generator | Passive resource generation |
| Economy | Market | Tax efficiency bonus |
| Support | Clinic | Regeneration buff to members |
| Support | Beacon | Various buffs |

### 5.3 City Specializations
- **Arcane Sanctum**: Mana generation, threat mitigation
- **Industrial Forge**: Production bonuses
- **Military Bastion**: Defense bonuses

### 5.4 Wonders
Unique city-wide projects with powerful effects (only one per server).

### 5.5 Outposts
Resource-generating territory expansions:
- Iron Mine, Coal Pit, Gold Pan, Diamond Drill

### 5.6 Political Systems
- Democratic elections
- Tax rate control (0-100%)
- Member invites and kicks
- City history logging

---

## 6. ECONOMY SYSTEM

### 6.1 Currency
- **Gold Nuggets**: Base unit (1.0 economy value)
- **Gold Ingots**: 9x value
- **Gold Blocks**: 81x value

### 6.2 Economic Mechanics
- Player-to-player payments
- City treasury deposits
- Villager gold-based trades (emeralds converted)
- Chest shop system with signs
- Bounty system for PvP rewards

### 6.3 Money Sinks
- City creation cost
- Chunk claims (scaling costs)
- Infrastructure upgrades
- Wonder construction
- Death taxes

---

## 7. COMBAT & DUNGEONS

### 7.1 Dungeon System
Instanced PvE content with party support:

| Dungeon | Theme | Rooms | Difficulty |
|---------|-------|-------|------------|
| Shadow Cavern | Dark caves | 5 | Medium |
| Infernal Pit | Nether-themed | 6 | Hard |
| Crystal Sanctum | Ice/crystal | 5 | Medium |

Features:
- Persistent mobs (don't despawn)
- Trap rooms with ambush mechanics
- Boss encounters
- Loot scaling with modifiers

### 7.2 World Bosses
Four era bosses with unique mechanics:
- **Hollow Knight** (Era 0): Melee, summons shadows
- **Tax Collector** (Era 1): Economic debuffs
- **Warden of Flames** (Era 2): Fire-based attacks
- **Ender Sentinel** (Era 3): Teleportation, void damage

### 7.3 Sieges
City defense events triggered by threat level:
- Wave-based mob spawning
- Smart AI targeting infrastructure
- Dynamic difficulty scaling based on city strength
- Defensive structure activation

---

## 8. SURVIVAL MECHANICS

### 8.1 Healing System
Tiered healing items replacing instant food healing:

| Tier | Items | Heal Amount |
|------|-------|-------------|
| 1 | Bandage, Poultice | 3-4 HP |
| 2 | Salve, Remedy | 5-6 HP + effects |
| 3 | Medkit, Draught | 8-10 HP + regen |
| 4 | Surgeon, Phoenix | 12-16 HP + buffs |
| 5 | Divine Restoration | 20 HP + absorption |

### 8.2 Environmental Pressure
- Hypothermia in cold oceans
- Weather effects
- Disease transmission (planned)

---

## 9. QUEST & NPC SYSTEMS

### 9.1 Quest Types
- Kill objectives
- Collection objectives
- Escort missions
- Defense missions
- Boss encounters

### 9.2 Quest Board
Physical spawn points for procedural quests with difficulty tiers.

### 9.3 NPC System
- Cinematic dialogue with typewriter effect
- Head tracking after dialogue
- Fade-out animations
- Custom merchants at structures

---

## 10. GLOBAL SYSTEMS

### 10.1 Threat System
- Passive threat increase over time
- Arcane Sanctum cities mitigate threat
- Dungeon completion reduces threat
- At 100% threat: **Blood Moon** (all-city siege)

### 10.2 Supply Drops
Random world events with loot containers.

### 10.3 Relic System
Rare artifacts with active abilities:
- Combat relics
- Utility relics
- Defense relics

### 10.4 History Recording
Persistent logging of city events:
- Foundings, sieges, politics
- Member changes, upgrades

---

## 11. VISUAL & ANIMATION SYSTEMS

### 11.1 Custom Models
- Resource pack with custom item textures
- Model animations via Display Entities
- Procedural animation presets (humanoid, floating, beast)

### 11.2 Atmosphere System
- Dynamic fog and sky effects
- Threat-based visual changes
- Weather integration

### 11.3 Blueprint Preview
- Real-time placement visualization
- Border outlines and corner pillars
- Color-coded validity (green/red)
- Error indication with problem markers

---

## 12. ADMIN COMMANDS

Full admin command suite for testing and management:

| Category | Commands |
|----------|----------|
| Player | give, reset, xp |
| Systems | threat, villager, questboard |
| World | event, boss, relic, siege |
| Building | spawn, schem, blueprint |
| Animation | anim spawn/attach/play |
| City | history view |

---

## 13. QOL FEATURES

- Inventory sorting
- Quick stack to nearby chests
- Damage numbers toggle
- Scoreboard toggle
- Death compass
- Party chat (/pc)
- Map system

---

## 14. FUTURE ROADMAP

### Phase 4 Planned Features
- Naval combat system
- Extended dungeon types
- Cross-server city alliances
- Seasonal events
- Achievement rewards
- Extended relic system

---

## 15. FINAL RULE

If a feature does not:
- Encourage multiplayer interaction
- Create meaningful consequence
- Respect persistence

It does not belong in Project Atlas.

---

**End of Canonical Design Document v2.0**
