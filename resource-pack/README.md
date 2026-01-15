# Project Atlas Resource Pack

This resource pack provides **custom visuals** for the Project Atlas Minecraft server.

## Features

- **Custom Weapon Models** - Era boss drops have unique 3D appearances
- **Custom Relic Textures** - Relics look like actual artifacts, not generic items
- **GUI Backgrounds** - Enhanced skill tree and progress menus (Phase 3)
- **Custom Sounds** - Unique audio for milestones and boss encounters (Phase 4)

## How It Works

The server automatically pushes this pack to players on join.
**No client-side mods are required!**

## For Developers

### Adding New Custom Items

1. Create a texture in `assets/minecraft/textures/item/custom/`
2. Create a model in `assets/minecraft/models/item/custom/`
3. Add an override to the base item model (e.g., `netherite_sword.json`)
4. Register the `CustomModelData` ID in `CustomItemManager.kt`

### CustomModelData Registry

| ID Range | Category |
|----------|----------|
| 1000-1999 | Era 0 Items |
| 2000-2999 | Era 1 Items |
| 3000-3999 | Era 2 Items |
| 4000-4999 | Era 3 Items |
| 5000-5999 | Era 4 Items |
| 9000-9099 | Relics |
| 10000-10999 | GUI Icons |
| 11000-11999 | City Items |

## Deployment

1. Zip this folder (excluding this README):
   ```powershell
   Compress-Archive -Path "assets", "pack.mcmeta" -DestinationPath "atlas-pack.zip"
   ```

2. Host the zip file publicly (GitHub Releases, CDN, etc.)

3. Update `config.yml`:
   ```yaml
   resource-pack:
     url: "https://your-host.com/atlas-pack.zip"
     hash: "SHA1_HASH_OF_FILE"
     required: false
   ```

4. Restart the server or use `/atlas reloadpack`

## Creating Textures

Textures should be **16x16** or **32x32** pixels for items.
Use transparency for non-rectangular shapes.

Recommended tools:
- [Aseprite](https://www.aseprite.org/) - Pixel art editor
- [Blockbench](https://www.blockbench.net/) - 3D model editor
- [GIMP](https://www.gimp.org/) - Free image editor
