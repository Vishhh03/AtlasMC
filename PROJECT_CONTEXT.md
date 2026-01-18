# Project Atlas Logic & Context

## Critical Configuration
- **Minecraft Version**: 1.21.11 (Java Edition, "Mounts of Mayhem" Update)
- **Platform**: Paper / Spigot
- **Resource Pack Format**: 70-75 (1.21.11 Standard)
- **API Standard**: 1.21.4+ (Uses `Attribute.MAX_HEALTH`, `Attribute.ARMOR`, etc. - NO `GENERIC_` prefix)

## Resource Pack System
We use the **1.21.2+ Item Model Component System** (Mandatory for 1.21.11):
1.  **Definitions**: `assets/projectatlas/items/<item_name>.json`
    - References the model: `projectatlas:item/<item_name>`
2.  **Models**: `assets/projectatlas/models/item/<item_name>.json`
    - Parent: `minecraft:item/handheld` or `generated`
    - Texture: `projectatlas:item/<item_name>`
3.  **Textures**: `assets/projectatlas/textures/item/<item_name>.png`
4.  **Application**: Items are given with the Data Component `minecraft:item_model="projectatlas:<item_name>"`

## Server Environment
- **Root**: `c:\Users\visha\Desktop\Mc_Plugin`
- **Test Server**: `test-server/`
- **Startup**: `.\test-server\start.bat` (Deploys plugin and starts server)
