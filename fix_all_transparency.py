from PIL import Image
import os

# Artifact directory
artifacts = "C:/Users/visha/.gemini/antigravity/brain/9e4dc002-51f0-4922-8e52-8f3cd7637344"
dest = "resource-pack/assets/minecraft/textures/item"

# Map artifact names to destination names
files = {
    "hollow_knight_blade_16x16_1768645672253.png": "hollow_knight_blade.png",
    "dragon_slayer_sword_1768645762237.png": "dragon_slayer.png",
    "ender_sentinel_scythe_1768645802125.png": "ender_sentinel_scythe.png",
    "warden_flame_sword_1768645833776.png": "warden_flame_sword.png",
    "tax_collector_axe_1768645852091.png": "tax_collector_axe.png",
    "ascendant_crown_1768645967649.png": "ascendant_crown.png",
    "awakening_medal_1768645981734.png": "awakening_medal.png",
    "settler_badge_1768646157842.png": "settler_badge.png",
    "dungeon_key_1768646181992.png": "dungeon_key.png",
    "healing_salve_1768646307531.png": "healing_salve.png",
    "spirit_totem_1768646320858.png": "spirit_totem.png",
    "explorer_compass_1768646356010.png": "explorer_compass.png",
    "blueprint_generic_1768646373359.png": "blueprint_generic.png",
    "legend_crown_1768646424888.png": "legend_crown.png"
}

for src_name, dest_name in files.items():
    src_path = os.path.join(artifacts, src_name)
    dest_path = os.path.join(dest, dest_name)
    
    if os.path.exists(src_path):
        # Load, convert to RGBA, resize to 16x16
        img = Image.open(src_path).convert("RGBA")
        img = img.resize((16, 16), Image.NEAREST)
        img.save(dest_path, "PNG")
        print(f"✓ {dest_name} (16x16, RGBA)")
    else:
        print(f"✗ Not found: {src_name}")

# Blueprint variants
import shutil
bp = os.path.join(dest, "blueprint_generic.png")
if os.path.exists(bp):
    shutil.copy(bp, os.path.join(dest, "blueprint_barracks.png"))
    shutil.copy(bp, os.path.join(dest, "blueprint_turret.png"))
    print("✓ Blueprint variants created")

print("\nDone! All textures now have RGBA mode with proper transparency.")
