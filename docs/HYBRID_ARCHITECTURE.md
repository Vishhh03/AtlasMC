# Project Atlas - Hybrid Architecture

## Philosophy: Plugin Logic + Resource Pack Visuals

We keep **Paper plugin** for all game logic (fast, stable, well-tested) but use **server-side resource packs** for premium visuals that feel like a modded experience.

**No client mods required - just vanilla Minecraft!**

---

## What Stays as Plugin (Game Logic)

| System | Why Plugin |
|--------|------------|
| **ProgressionManager** | Core data, era tracking, milestone completion |
| **CityManager** | City data, membership, treasury |
| **SiegeManager** | Wave spawning, damage calculation |
| **QuestManager** | Quest tracking, rewards |
| **EconomyManager** | Gold transactions |
| **SkillTreeManager** | Node unlocking, effect application |
| **DungeonManager** | Dungeon instances, mob spawning |
| **IdentityManager** | Player profiles, persistence |

---

## What Uses Resource Pack Magic (Visuals)

| Feature | Implementation | Visual Result |
|---------|----------------|---------------|
| **Custom Weapons** | `CustomModelData` on items | Unique 3D sword/axe models |
| **Relics** | `CustomModelData` 9000+ | Glowing artifacts, not generic items |
| **Era Boss Appearances** | Custom mob textures via resource pack | Hollow Knight looks different from skeleton |
| **GUI Backgrounds** | Negative-space font trick | Beautiful skill tree, not glass panes |
| **HUD Elements** | Action bar + custom font | Mana bar, stamina icons |
| **City Banners** | Custom banner patterns | Unique city flags |
| **Sound Effects** | Custom .ogg files | Era advancement fanfare |

---

## Resource Pack Structure

```
atlas-pack/
├── pack.mcmeta
├── pack.png
├── assets/
│   └── minecraft/
│       ├── font/
│       │   └── default.json          # Custom GUI characters
│       ├── models/
│       │   └── item/
│       │       ├── wooden_sword.json # Base + overrides
│       │       ├── iron_sword.json
│       │       └── ...
│       ├── textures/
│       │   ├── item/
│       │   │   └── custom/
│       │   │       ├── hollow_knight_blade.png
│       │   │       ├── phoenix_feather.png
│       │   │       └── ...
│       │   ├── gui/
│       │   │   ├── skill_tree_bg.png
│       │   │   ├── progress_gui_bg.png
│       │   │   └── ...
│       │   └── entity/
│       │       └── custom/
│       │           ├── hollow_knight.png
│       │           └── ...
│       └── sounds/
│           └── custom/
│               ├── era_advance.ogg
│               ├── milestone_complete.ogg
│               └── boss_spawn.ogg
```

---

## CustomModelData Registry

| ID Range | Category | Examples |
|----------|----------|----------|
| 1000-1999 | Era 0 Weapons | Hollow Knight's Blade (1001) |
| 2000-2999 | Era 1 Weapons | Tax Collector's Axe (2001) |
| 3000-3999 | Era 2 Weapons | Warden's Flame Sword (3001) |
| 4000-4999 | Era 3 Weapons | Ender Sentinel Scythe (4001) |
| 5000-5999 | Era 4 Weapons | Dragon Slayer (5001) |
| 9000-9099 | Relics | Phoenix Feather (9000), Void Shard (9001) |
| 10000-10999 | GUI Icons | Skill nodes, currency, etc. |
| 11000-11999 | City Items | Banners, decorations |

---

## Implementation Priority

### Phase 1: Resource Pack Infrastructure
- [ ] ResourcePackManager (auto-push on join)
- [ ] CustomItemManager (create items with CustomModelData)
- [ ] Pack hosting setup

### Phase 2: Custom Items
- [ ] Era Boss drops with unique models
- [ ] Relic visuals
- [ ] Legendary weapons

### Phase 3: Enhanced GUIs
- [ ] Skill Tree with custom background
- [ ] Progress GUI with era artwork
- [ ] City management panels

### Phase 4: Audio & Polish
- [ ] Custom sounds for milestones
- [ ] Boss spawn music
- [ ] Ambient city sounds
