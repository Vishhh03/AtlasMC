# Project Atlas - Admin Commands

This document lists all available administrative commands for the Project Atlas plugin.
**Required Permission**: `atlas.admin`

## 🌍 World & Game State

| Command | Usage | Description |
| :--- | :--- | :--- |
| **Start Event** | `/atlas event start` | Forces the start of a random Supply Drop event in the world. |
| **Spawn Boss** | `/atlas boss spawn <type>` | Spawns a World Boss at your location. Types: `hollow`, `tax`, `warden`, `ender`. |
| **Spawn Era Boss** | `/atlas boss era <type>` | Spawns a specific Era Boss (e.g., Hollow Knight, Tax Collector). |
| **Spawn Relic** | `/atlas relic spawn` | Spawns a Relic Container at your location. |
| **Give Relic** | `/atlas relic give` | Gives you a random Relic item. |
| **Start Siege** | `/atlas siege start <city_name>` | Forces a Siege event to start against the specified city immediately. |
| **Resource Pack** | `/atlas rp generate` | Generates the resource pack ZIP from current item models/textures (Server-side). |

## 🛠 Player Management

| Command | Usage | Description |
| :--- | :--- | :--- |
| **Give Resources** | `/atlas admin give <player> <amount>` | Gives Gold (`amount`) or a custom item (if specific name used) to a player. |
| **Give Item** | `/atlas admin give <player> <item_id>` | **Custom Items**: `healing_salve`, `spirit_totem`, `hollow_blade`, `warden_sword`, `ender_scythe`. |
| **Give XP** | `/atlas admin xp <player> <amount>` | Gives raw experience points to a player. |
| **Reset Player** | `/atlas admin reset <player>` | **DANGER**: Resets a player's Era progression and kicks them from their city. |

## 🏗 Building & Structures

| Command | Usage | Description |
| :--- | :--- | :--- |
| **Spawn Structure**| `/atlas spawn <type>` | Spawns a full structure at your location. Types: `MERCHANT_HUT`, `QUEST_CAMP`, `BARRACKS`, etc. |
| **Paste Schematic**| `/atlas schem paste <name>` | Pastes a saved schematic at your current location. |
| **Force Build** | `/atlas bp force` | Forces the placement of a blueprint you are currently previewing, ignoring costs/checks. |
| **Create Outpost** | `/atlas outpost create <name> <type>` | Creates a resource outpost at your location. Types: `IRON_MINE`, `COAL_PIT`, `GOLD_PAN`, `DIAMOND_DRILL`. |

## 🎬 Animation & Models (Debug)

| Command | Usage | Description |
| :--- | :--- | :--- |
| **Spawn Animated** | `/atlas anim spawn <model> [entity]` | Spawns an entity with a custom animated model attached. |
| **Attach Model** | `/atlas anim attach <model>` | Attaches a model to the entity you are looking at. |
| **Play Animation** | `/atlas anim play <anim_name>` | Plays an animation (e.g., `attack`, `walk`) on the target entity. |
| **Stop Animation** | `/atlas anim stop` | Stops the current animation on the target. |
| **List Models** | `/atlas anim list` | Lists all available models and animations. |
| **Procedural** | `/atlas anim proc <preset>` | Applies procedural animation settings (e.g., `HUMANOID`, `FLOATING`) to target. |

## 🐛 Troubleshooting

| Command | Usage | Description |
| :--- | :--- | :--- |
| **Reload** | (Use Server Reload) | The plugin supports hot-reloading for most configs, but a full server restart is recommended for code changes. |
