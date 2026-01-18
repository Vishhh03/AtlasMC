"""
Analyze and fix texture alpha channel.
Ensure background pixels are FULLY transparent (alpha=0).
"""
from PIL import Image
import os

TARGET_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"

def analyze_and_fix(image_path):
    """Analyze alpha values and ensure full transparency."""
    img = Image.open(image_path).convert("RGBA")
    pixels = img.load()
    width, height = img.size
    
    # Count pixels with different alpha states
    stats = {
        'fully_transparent': 0,  # alpha = 0
        'semi_transparent': 0,   # 0 < alpha < 255
        'fully_opaque': 0        # alpha = 255
    }
    
    # Also collect unique alpha values for analysis
    alpha_values = set()
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            alpha_values.add(a)
            
            if a == 0:
                stats['fully_transparent'] += 1
            elif a == 255:
                stats['fully_opaque'] += 1
            else:
                stats['semi_transparent'] += 1
                # Make semi-transparent FULLY transparent
                pixels[x, y] = (0, 0, 0, 0)
    
    print(f"\n=== {os.path.basename(image_path)} ===")
    print(f"  Unique alpha values: {sorted(alpha_values)}")
    print(f"  Fully transparent (a=0): {stats['fully_transparent']}")
    print(f"  Semi-transparent: {stats['semi_transparent']}")
    print(f"  Fully opaque (a=255): {stats['fully_opaque']}")
    
    if stats['semi_transparent'] > 0:
        img.save(image_path, "PNG")
        print(f"  FIXED: Converted {stats['semi_transparent']} semi-transparent to fully transparent")
    
    return stats

def main():
    print("Analyzing texture alpha channels...")
    
    textures = ["hollow_knight_blade.png", "warden_flame_sword.png"]
    
    for filename in textures:
        filepath = os.path.join(TARGET_DIR, filename)
        if os.path.exists(filepath):
            analyze_and_fix(filepath)
        else:
            print(f"\nNOT FOUND: {filename}")
    
    print("\nDone!")

if __name__ == "__main__":
    main()
