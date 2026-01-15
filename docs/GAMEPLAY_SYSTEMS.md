# Project Atlas - Gameplay Systems Documentation

This document tracks all custom gameplay systems implemented in Project Atlas.

---

## ⚔️ PROGRESSION SYSTEM (City-Based Competition)

### Philosophy
Like **Elden Ring**, progression is **gated by achievement**, not just time.
Like **Minecraft**, each tier unlocks new **dimensions and mechanics**.

**Key Design: Cities are Teams!**
- **Era is shared** by all members of a city
- Any member's achievement counts for the whole city
- Creates **competition between rival cities**
- Players without a city are **stuck at Era 0**

### The Five Eras

| Era | Name | Level | Key Unlocks | Gate Boss |
|-----|------|-------|-------------|-----------|
| 0 | **Awakening** | 1-10 | Basic survival, quests, parties | Hollow Knight |
| 1 | **Settlement** | 5-25 | Cities, Skill Tree, Economy | Tax Collector Raid |
| 2 | **Expedition** | 15-40 | Nether, Dungeons, Sieges | Warden of Flames |
| 3 | **Ascension** | 30-60 | End Portal, Alliances, Keystones | Ender Sentinel |
| 4 | **Legend** | 50+ | Dragon, World Events, Legacy | Enhanced Dragon |

### Dimension Locks
- **Nether**: Sealed until Era 2 (must complete Era 1 milestones)
- **End**: Sealed until Era 3 (must complete Era 2 milestones)

### Feature Locks
| Feature | Required Era |
|---------|--------------|
| Skill Tree | 1 (Settlement) |
| Dungeons | 2 (Expedition) |
| City Sieges | 2 (Expedition) |
| Relics | First dungeon complete |
| City Alliances | 3 (Ascension) |

### Commands
- `/atlas progress` - View current era and milestones
- `/atlas journey` - Same as above (alias)
- `/atlas boss era <type>` - (Admin) Spawn era boss

### Era Bosses

| Boss | Era | Health | Special Mechanics |
|------|-----|--------|-------------------|
| **Hollow Knight** | 0→1 | 100 HP | Netherite gear, Fire Resist |
| **Tax Collector** | 1→2 | 200 HP | Summons Vindicator minions |
| **Warden of Flames** | 2→3 | 350 HP | Fire Nova AoE attack |
| **Ender Sentinel** | 3→4 | 500 HP | Teleport behind players |

### Mob Scaling by Era

Hostile mobs scale based on the highest-era player nearby:

| Era | Mob HP | Mob Damage |
|-----|--------|------------|
| 0 (Awakening) | 100% | 100% |
| 1 (Settlement) | 120% | 110% |
| 2 (Expedition) | 150% | 130% |
| 3 (Ascension) | 200% | 150% |
| 4 (Legend) | 250% | 175% |

### Era Milestones (Examples)

**Era 0 (Awakening)**:
- Reach Level 5
- Complete 3 Quests
- Craft iron gear set
- Sleep in a bed
- Defeat Hollow Knight

**Era 1 (Settlement)**:
- Join/found a city
- Earn 1,000 gold
- City has 3+ members
- Build first infrastructure
- Defeat Tax Collector

---

## 🍖 Food & Saturation System

### Core Changes
| Feature | Vanilla | Atlas |
|---------|---------|-------|
| Saturation Healing | Enabled | **DISABLED** |
| Eating Healing | Enabled | **DISABLED** |
| Primary Healing | Food | Custom Items + Sleep |

### Food Quality Tiers
Food no longer heals, but provides **buffs** based on quality:

| Tier | Foods | Buff |
|------|-------|------|
| **Poor** | Raw meat, apples, berries, rotten flesh | No buff |
| **Common** | Cooked meats, potatoes, fish | +Speed I (30s) |
| **Quality** | Bread, stews, pies, honey | +Speed I, +Strength I (45s) |
| **Gourmet** | Golden apples, golden carrots | +Speed I, +Strength I, +Resistance I (60s) |

### Starvation Penalties
- **≤6 hunger**: Slowness I + Weakness I
- **≤3 hunger**: Warning message "You are starving!"

### Combat Hunger Drain
- Attacking drains **0.5 saturation per hit**
- When saturation is empty, **10% chance to lose 1 hunger point per attack**

---

## 💊 Healing Items System

### Tier Overview
| Tier | Items | Heal | Cooldown | Special |
|------|-------|------|----------|---------|
| **1** ⭐ | Bandage, Herbal Poultice | 3-4 HP | 5-6s | - |
| **2** ⭐⭐ | Healing Salve, Herbal Remedy | 5-6 HP | 7-8s | Clears debuffs |
| **3** ⭐⭐⭐ | Medical Kit, Regen Draught | 8-10 HP | 10-12s | Clears debuffs, +Regen |
| **4** ⭐⭐⭐⭐ | Surgeon's Kit, Phoenix Elixir | 12-16 HP | 15-20s | Clears debuffs, +Fire Resist |
| **5** ⭐⭐⭐⭐⭐ | Divine Restoration | 20 HP | 30s | Clears ALL, +Absorption II |

### Crafting Recipes

**Tier 1 (Early Game):**
- **Bandage**: 2 Paper + 1 String
- **Herbal Poultice**: 2 Fern + 1 Clay Ball

**Tier 2 (Mid-Early):**
- **Healing Salve**: 1 Honeycomb + 1 Bowl + 1 Sugar
- **Herbal Remedy**: 2 Sweet Berries + 1 Red Mushroom + 1 Dandelion

**Tier 3 (Mid Game):**
- **Medical Kit** (shaped): Paper/String/Paper, Bottle/Honey/Bottle, Iron/Iron/Iron
- **Regen Draught**: 1 Ghast Tear + 1 Glistering Melon + 1 Glass Bottle

**Tier 4 (Late Game):**
- **Surgeon's Kit** (shaped): Gold/Diamond/Gold, Shears/Chest/Shears, Gold/Emerald/Gold
- **Phoenix Elixir**: 1 Dragon's Breath + 2 Blaze Powder + 1 Golden Apple

**Tier 5 (Legendary):**
- **Divine Restoration** (shaped): Emerald Block border + Nether Star center

### Commands
- `/atlas heal` - Show all items
- `/atlas heal items` - Get a bundle of ALL tiers
- `/atlas heal <item> [amount]` - Get specific item

---

## 😴 Sleep/Rest Healing System

### Bed Quality Tiers
| Bed Type | Quality | Full Sleep Heal | Rest Multiplier |
|----------|---------|-----------------|-----------------|
| White Bed | Basic | 4 HP | 1.0x |
| Colored Beds | Comfort | 6 HP | 1.5x |
| Black Bed | Luxury | 10 HP | 2.0x |

### Time-Based Resting (NEW!)
Even if the night doesn't skip (multiplayer), you still heal by just laying in bed:
- **Base Rate**: 0.5 HP per second
- **Minimum Rest Time**: 3 seconds
- **Quality Scaling**: Better beds heal faster (1x → 1.5x → 2x)
- **Max Heal**: Capped at bed quality's full sleep amount

### Full Sleep Benefits (Night Skipped)
- Heals full amount based on bed quality
- Clears: Poison, Hunger, Weakness, Slowness
- Grants: Regeneration I (10s)
- Special message: "☽ [Quality] ☽"

### Partial Rest Benefits (No Skip)
- Shows time spent resting
- Heals proportional amount
- Message: "💤 Rested for Xs - Restored X.X health"

---

## ✦ Skill Tree System

### Overview
A **Path of Exile-style** passive skill tree with 5 role-based branches. Players earn 1 skill point per level and can navigate a grid-based interface to unlock nodes.

### Command
- `/atlas skills` - Opens the skill tree GUI

### Role Branches

| Branch | Color | Focus | Key Skills |
|--------|-------|-------|------------|
| **Vanguard** | 🔵 Blue | Tank/Defense | Health, Armor, Regen, Thorns, Poise |
| **Berserker** | 🔴 Red | DPS/Combat | Melee Damage, Crits, Lifesteal, Execute |
| **Scout** | 🔷 Cyan | Mobility/Stealth | Speed, Double Jump, Sneak Damage, Dodge |
| **Artisan** | 🟠 Orange | Mining/Gathering | Haste, Vein Miner, Auto-Smelt, Lumberjack |
| **Settler** | 🟢 Green | Economy/City | Trade Discount, Quest Gold, Siege Defense |

### Node Tiers
| Tier | Cost | Description |
|------|------|-------------|
| **Minor** | 1 pt | Small stat bonuses |
| **Notable** | 2 pts | Medium bonuses, unlocks mechanics |
| **Keystone** | 3 pts | Powerful effects, some are **exclusive** |

### Exclusive Keystones
Some powerful nodes lock out others, forcing meaningful build choices:
- **Colossus** (Max Health) ⚔ **Executioner** (Execute Damage)
- **Vein Miner** ⚔ **Auto-Smelt**

### GUI Features
- 🗺️ **Pannable Grid**: Navigate with arrow buttons
- 📊 **Progress Tracker**: Shows % of tree unlocked
- 📖 **Legend**: Color-coded branch reference
- ✨ **Visual Feedback**: Glowing unlocked nodes, connection indicators

---

## 🏰 Dungeon System

### Dungeon Types
Located in `world_the_end`

### Mob Scaling (Per Tier)
| Stat | Formula | Tier 1 | Tier 5 |
|------|---------|--------|--------|
| **Health** | Base × (1.0 + Tier × 0.4) | +40% HP | +200% HP |
| **Damage** | Base × (1.0 + Tier × 0.2) | +20% DMG | +100% DMG |
| **Armor** | Gear scaling | Chain/Leather | Full Diamond |

### Boss Stats
- Base Health: 150 HP
- Tier 1 Boss: ~210 HP
- Tier 5 Boss: ~450 HP
- Always wears Diamond armor with enchants

### Death in Dungeon
- **KeepInventory**: Enabled in dungeons
- **No Death Compass**: Dungeon deaths don't trigger death compass
- **Expulsion**: Player is teleported out of dungeon
- **Dungeon Fails**: All progress is lost

### Enderman Prevention
- Natural Enderman spawns are blocked in dungeon world

---

## 💀 Death Compass System

### When Active
- Player dies with items to drop
- NOT in a dungeon (world_the_end)
- NOT with KeepInventory enabled

### Features
- **Death Compass Item**: Given on respawn after delay
- **Points to Death Location**: Right-click to update compass target
- **10-Minute Expiration**: Compass tracking expires after 10 min
- **Lore Display**: Shows death coordinates and distance

### On Map
- Death location appears as skull icon on local map

---

## 🗺️ Local Map System

### Opening
| Method | Action |
|--------|--------|
| **Hotkey** | Shift + Q |
| **Command** | `/atlas map` |

### Display (9x6 Chunk Grid)
| Icon | Meaning |
|------|---------|
| Player Head | Your position (center) |
| Skeleton Skull | Death location |
| Lime Concrete | Other players |
| Sign | Quest board |
| Villager Egg | NPCs nearby |
| Green Glass | Your city territory |
| Red Glass | Foreign city territory |
| Colored Glass | Biome type |

---

## 🏕️ Natural Structures

### Structure Types

**Merchant Hut** (Weathered Cabin)
- Irregular cobblestone foundation
- Mixed wood plank floors
- Varied wall materials (70/30 random)
- Overhanging roof with slabs/stairs
- Interior: Chest, crafting table, barrel, lantern
- Exterior: Random potted plants, hay bales

**Quest Camp** (Adventurer's Campsite)
- Central campfire with stone ring
- Two wool tents (white & brown)
- Log seating scattered around
- Random barrels, chests, fletching table
- Lantern on fence post

**Barracks** (Ruined Outpost)
- Cracked/mossy stone brick floor with holes
- Variable height walls (1-3 blocks, partial ruins)
- Mixed wall materials
- Partial corner towers
- Interior: Chest, barrel, smithing table, armor stand
- Central campfire

---

## ⚔️ Quest System

### Death Penalty
- Dying with an active quest **fails the quest**
- Boss bar is hidden
- Player is notified of quest failure

### Quest Boards
- Display: Quest name, short objective, reward, difficulty
- Signs with clickable interaction
- Barrel for item turn-in

### Quest Types
- **Kill Mobs**: Defeat specific enemies.
- **Fetch Item**: Gather and turn in resources.
- **Escort Villager**: Lead a vulnerable NPC to a destination while protecting them from zombies.
- **Defend Villager**: Protect an NPC from waves of enemies for a set duration.
- **Explore**: Visit biomes or travel long distances.
- **Dungeon**: Complete a difficulty-rated dungeon.

---

## 🏰 City System

### Creating a City
- `/atlas city create <name>` - Found a new city (costs currency)
- First member becomes **Mayor**

### City Roles
| Role | Permissions |
|------|-------------|
| **Mayor** | Full control, disband, promote/demote, manage territory |
| **Officer** | Invite, kick members, manage claims |
| **Member** | Build in territory, access city features |

### Territory Claims
- `/atlas city claim` - Claim current chunk for your city
- `/atlas city unclaim` - Remove claim from current chunk
- Territory provides **protection** from outsiders
- Natural structures won't spawn in claimed territory

### City Buffs
Cities provide passive buffs to members in territory:
- Regeneration (based on city level)
- Speed boost near city center
- Protection from some environmental hazards

### City Management
- `/atlas city invite <player>` - Invite player to city
- `/atlas city kick <player>` - Remove member from city
- `/atlas city promote <player>` - Promote to Officer
- `/atlas city demote <player>` - Demote to Member
- `/atlas city leave` - Leave your city
- `/atlas city disband` - Destroy the city (Mayor only)

### City Info
- `/atlas city` - Show city info
- `/atlas city list` - List all cities
- `/atlas city members` - Show city roster

### City Defense & Siege System

**Triggering a Siege:**
- Certain actions or events can trigger a siege on your city
- Boss bar appears showing siege progress

**Siege Waves:**
| Wave | Enemy Types |
|------|-------------|
| Early Waves | Grunts (zombies) |
| Mid Waves | Breachers (with armor) |
| Late Waves | Snipers (skeletons with bows) |

**Siege Victory:**
- Kill all enemies in each wave
- Survive all waves to win
- Victory grants rewards and reputation

**Siege Defeat:**
- If defenders fail, city takes damage
- Resources may be lost
- Cooldown before next siege

**Defense Tips:**
- Build walls around territory
- Set up chokepoints
- Stock healing items before defense events
- Coordinate with party/city members

---

## 👥 Party System

### Creating a Party
- `/atlas party create` - Create a new party
- `/atlas party invite <player>` - Invite player to party
- `/atlas party accept` - Accept pending invitation

### Party Benefits
| Feature | Benefit |
|---------|---------|
| **Shared Quest Progress** | Some quests progress for entire party |
| **Buff Sharing** | Certain buffs affect all nearby members |
| **Communication** | Party chat channel |
| **Map Visibility** | See party members on local map |

### Party Management
- `/atlas party kick <player>` - Kick member (leader only)
- `/atlas party leave` - Leave the party
- `/atlas party disband` - Disband party (leader only)
- `/atlas party promote <player>` - Transfer leadership

### Party Chat
- `/atlas party chat <message>` - Send message to party
- Or use `/pc <message>` shortcut

### Party Info
- `/atlas party` - Show party info and members
- `/atlas party list` - List online party members

---

---

## 🌳 Skill Tree / Progression

Players earn skill points to unlock passive bonuses and special abilities.

### New Skills (v1.4 Expansion)
| Skill | Category | Tier | Effect |
|-------|----------|------|--------|
| **Deep Sleep** | Survival | Notable | +50% Healing from Sleep/Rest |
| **Bounty Hunter** | Combat/Util | Notable | +25% Gold from Quests |
| **Siege Defender** | Defense | Notable | +25% Damage vs Siege Raiders |
| **Silver Tongue** | Utility | Notable | 10% Discount on City Territory Claims |
| **Mariner** | Mobility | Minor | Move faster in water (Dolphins Grace) |
| **Polar Fur** | Survival | Minor | Immunity to Hypothermia (Freezing water) |
| **Iron Lungs** | Utility | Keystone | Breathe underwater indefinitely |

---

## 📋 Commands Reference

### Healing
- `/atlas heal` - Show healing items
- `/atlas heal items` - Get all healing items
- `/atlas heal <type> [amount]` - Get specific item

### Map
- `/atlas map` - Open local area map

### City
- `/atlas city create <name>` - Found a city
- `/atlas city claim` - Claim territory
- `/atlas city invite/kick/promote/demote <player>` - Manage members

### Party
- `/atlas party create` - Create party
- `/atlas party invite <player>` - Invite player
- `/atlas party leave` - Leave party

### Other Systems
- `/atlas dungeon` - List/enter dungeons
- `/atlas quest` - Quest information
- `/atlas quest abandon` - Abandon current quest

---

## 🔧 Configuration

Most settings are hardcoded for balance. Future versions may add config options for:
- Healing item values and cooldowns
- Food quality buff durations
- Dungeon mob scaling factors
- Death compass duration
- City claim costs
- Party size limits

---

*Last Updated: January 15, 2026*
