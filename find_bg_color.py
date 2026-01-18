"""
Find the actual background color of textures.
"""
from PIL import Image
import os
from collections import Counter

TEXTURE_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"

def find_background_color(image_path):
    """Find most common colors (likely background)."""
    img = Image.open(image_path).convert("RGBA")
    pixels = list(img.getdata())
    
    # Count color occurrences
    color_counts = Counter(pixels)
    
    # Get top 5 most common
    return color_counts.most_common(5)

def main():
    test_files = ['hollow_knight_blade.png', 'warden_flame_sword.png', 'dragon_slayer.png']
    
    for filename in test_files:
        filepath = os.path.join(TEXTURE_DIR, filename)
        if os.path.exists(filepath):
            colors = find_background_color(filepath)
            print(f"\n=== {filename} ===")
            print(f"Top 5 colors (r,g,b,a): count")
            for color, count in colors:
                print(f"  {color}: {count} pixels")

if __name__ == "__main__":
    main()
