from PIL import Image
import os

texture_dir = "resource-pack/assets/minecraft/textures/item"

# List of textures to resize
textures = [
    "hollow_knight_blade.png",
    "dragon_slayer.png", 
    "warden_flame_sword.png",
    "ender_sentinel_scythe.png",
    "tax_collector_axe.png",
    "ascendant_crown.png",
    "awakening_medal.png",
    "settler_badge.png",
    "legend_crown.png",
    "dungeon_key.png",
    "healing_salve.png",
    "spirit_totem.png",
    "explorer_compass.png",
    "blueprint_generic.png",
    "blueprint_barracks.png",
    "blueprint_turret.png"
]

for tex in textures:
    path = os.path.join(texture_dir, tex)
    if os.path.exists(path):
        img = Image.open(path)
        # Resize to 16x16 using nearest neighbor (preserves pixel art look)
        img = img.resize((16, 16), Image.NEAREST)
        img.save(path)
        print(f"Resized {tex} to 16x16")
    else:
        print(f"Not found: {tex}")

print("Done!")
