# Minecraft Custom Texture Creation Guidelines

## Key Requirements for 1.21.11

### File Format
- **Size**: 16x16 pixels (standard Minecraft item size)
- **Format**: PNG with **alpha channel** (RGBA, not RGB)
- **Transparency**: Background must be fully transparent (alpha = 0)

### File Locations (projectatlas namespace)
```
assets/projectatlas/
├── items/           # Item definitions (links model to item)
│   └── item_name.json
├── models/item/     # Model files (points to texture)
│   └── item_name.json
└── textures/item/   # Actual PNG textures
    └── item_name.png
```

### Item Definition (`items/item_name.json`)
```json
{
  "model": {
    "type": "minecraft:model",
    "model": "projectatlas:item/item_name"
  }
}
```

### Model File (`models/item/item_name.json`)
```json
{
  "parent": "minecraft:item/handheld",  // or "generated" for non-held items
  "textures": {
    "layer0": "projectatlas:item/item_name"
  }
}
```

## Generating Textures with AI

### Prompt Template
```
A 16x16 pixel art Minecraft [item type] icon. [Color description] with [handle/details].
Pixel art style matching Minecraft aesthetic. Transparent background (PNG with alpha).
Simple, clean pixel art.
```

### Examples
- **Sword**: "diagonal from bottom-left to top-right"
- **Crown/Badge**: "centered, front-facing view"
- **Potion**: "simple bottle shape"

## Processing Generated Images

```python
from PIL import Image

def process_texture(source_path, target_path):
    img = Image.open(source_path).convert("RGBA")
    img = img.resize((16, 16), Image.Resampling.NEAREST)
    img.save(target_path, "PNG")
```

### Verification Checklist
1. [ ] Texture is exactly 16x16 pixels
2. [ ] File has alpha channel (RGBA mode)
3. [ ] Background is transparent (not white/gray)
4. [ ] **CRITICAL**: Ensure transparency is REAL alpha (0), not a gray/white checkered pattern drawn as pixels!
5. [ ] In-game reload: Press **F3 + T**
6. [ ] No purple/black checkered pattern (missing texture)

## Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| Purple/black checkerboard | Missing texture file | Check file path matches model reference |
| White/gray background | No alpha channel | Re-export with transparency |
| **Visible Gray Checkers** | **Fake transparency** | Use an editor (Aseprite/Photoshop) to delete background pixels |
| Blurry texture | Wrong resize filter | Use `NEAREST` resampling |

