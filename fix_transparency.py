"""
Fix transparency for all custom item textures.
Makes corner pixels transparent and uses flood fill approach.
"""
from PIL import Image
import os

TEXTURE_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"

def fix_transparency_floodfill(image_path):
    """Use flood fill from corners to make background transparent."""
    img = Image.open(image_path).convert("RGBA")
    pixels = img.load()
    width, height = img.size
    
    # Get corner pixel color as background reference
    corner_colors = [
        pixels[0, 0],
        pixels[width-1, 0],
        pixels[0, height-1],
        pixels[width-1, height-1]
    ]
    
    # Find most common corner color
    from collections import Counter
    bg_color = Counter(corner_colors).most_common(1)[0][0]
    
    # Make all pixels matching background color transparent
    changed = 0
    tolerance = 30  # Color tolerance for matching background
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            br, bg_c, bb, ba = bg_color
            
            # Check if pixel is similar to background
            if (abs(r - br) <= tolerance and 
                abs(g - bg_c) <= tolerance and 
                abs(b - bb) <= tolerance):
                pixels[x, y] = (0, 0, 0, 0)
                changed += 1
    
    img.save(image_path, "PNG")
    print(f"  Fixed {os.path.basename(image_path)}: bg={bg_color[:3]}, {changed} pixels transparent")
    return changed

def main():
    print(f"Processing textures in: {TEXTURE_DIR}")
    
    if not os.path.exists(TEXTURE_DIR):
        print(f"ERROR: Directory not found: {TEXTURE_DIR}")
        return
    
    png_files = [f for f in os.listdir(TEXTURE_DIR) if f.endswith('.png')]
    print(f"Found {len(png_files)} PNG files\n")
    
    total_fixed = 0
    for filename in png_files:
        filepath = os.path.join(TEXTURE_DIR, filename)
        try:
            changed = fix_transparency_floodfill(filepath)
            total_fixed += changed
        except Exception as e:
            print(f"  ERROR processing {filename}: {e}")
    
    print(f"\nDone! Total pixels made transparent: {total_fixed}")

if __name__ == "__main__":
    main()
