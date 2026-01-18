"""
Rebuild textures from original artifact images.
Resize to 16x16 and apply proper transparency.
"""
from PIL import Image
import os
import shutil

# Source: Original AI-generated images
ARTIFACT_DIR = r"C:\Users\visha\.gemini\antigravity\brain\9e4dc002-51f0-4922-8e52-8f3cd7637344"
# Target: Resource pack textures
TARGET_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"

# Mapping from artifact filename to target filename
FILE_MAPPING = {
    "hollow_knight_blade_16x16_1768645672253.png": "hollow_knight_blade.png",
    "warden_flame_sword_1768645833776.png": "warden_flame_sword.png",
    "dragon_slayer_sword_1768645762237.png": "dragon_slayer.png",
    "ender_sentinel_scythe_1768645802125.png": "ender_sentinel_scythe.png",
    "tax_collector_axe_1768645852091.png": "tax_collector_axe.png",
    "ascendant_crown_1768645967649.png": "ascendant_crown.png",
    "awakening_medal_1768645981734.png": "awakening_medal.png",
    "settler_badge_1768646157842.png": "settler_badge.png",
    "legend_crown_1768646424888.png": "legend_crown.png",
    "dungeon_key_1768646181992.png": "dungeon_key.png",
    "healing_salve_1768646307531.png": "healing_salve.png",
    "spirit_totem_1768646320858.png": "spirit_totem.png",
    "explorer_compass_1768646356010.png": "explorer_compass.png",
    "blueprint_generic_1768646373359.png": "blueprint_generic.png",
}

def make_background_transparent(img):
    """
    Remove white/light gray backgrounds typical of AI-generated images.
    Uses corner sampling to detect background color.
    """
    img = img.convert("RGBA")
    pixels = img.load()
    width, height = img.size
    
    # Sample corners to find background color
    corners = [
        pixels[0, 0],
        pixels[width-1, 0], 
        pixels[0, height-1],
        pixels[width-1, height-1]
    ]
    
    # Find most common corner color (likely background)
    from collections import Counter
    bg_color = Counter([c[:3] for c in corners]).most_common(1)[0][0]
    
    # Calculate if background is light (white/gray) or dark
    bg_brightness = sum(bg_color) / 3
    
    # Apply transparency based on similarity to background
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            
            # Calculate color distance from background
            distance = abs(r - bg_color[0]) + abs(g - bg_color[1]) + abs(b - bg_color[2])
            
            # If very close to background color, make transparent
            if distance < 60:  # Tolerance
                pixels[x, y] = (0, 0, 0, 0)
    
    return img

def process_texture(source_path, target_path):
    """Load, resize, apply transparency, and save."""
    try:
        img = Image.open(source_path)
        
        # Apply transparency first (at high res for better quality)
        img = make_background_transparent(img)
        
        # Resize to 16x16 using NEAREST for pixel art crispness
        img = img.resize((16, 16), Image.Resampling.NEAREST)
        
        # Save with full alpha channel
        img.save(target_path, "PNG")
        print(f"  OK: {os.path.basename(target_path)}")
        return True
    except Exception as e:
        print(f"  ERROR: {os.path.basename(target_path)}: {e}")
        return False

def main():
    print("Rebuilding textures from artifacts...")
    print(f"Source: {ARTIFACT_DIR}")
    print(f"Target: {TARGET_DIR}\n")
    
    success = 0
    failed = 0
    
    for source_name, target_name in FILE_MAPPING.items():
        source_path = os.path.join(ARTIFACT_DIR, source_name)
        target_path = os.path.join(TARGET_DIR, target_name)
        
        if os.path.exists(source_path):
            if process_texture(source_path, target_path):
                success += 1
            else:
                failed += 1
        else:
            print(f"  MISSING: {source_name}")
            failed += 1
    
    # Handle blueprint variants
    blueprint_source = os.path.join(ARTIFACT_DIR, "blueprint_generic_1768646373359.png")
    if os.path.exists(blueprint_source):
        for variant in ["blueprint_barracks.png", "blueprint_turret.png"]:
            target_path = os.path.join(TARGET_DIR, variant)
            if process_texture(blueprint_source, target_path):
                success += 1
            else:
                failed += 1
    
    print(f"\nDone! Success: {success}, Failed: {failed}")

if __name__ == "__main__":
    main()
