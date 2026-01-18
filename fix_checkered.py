"""
Fix the checkered transparency pattern that AI generators create.
The gray/white checkered pattern needs to be converted to actual alpha transparency.
"""
from PIL import Image
import os

TARGET_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"

def is_checkered_pixel(x, y, r, g, b):
    """
    Check if this pixel is part of the checkered transparency pattern.
    The pattern alternates between gray (~128) and lighter gray (~192) or white.
    """
    # Common checkered pattern colors
    is_gray = (120 <= r <= 140 and 120 <= g <= 140 and 120 <= b <= 140)
    is_light_gray = (180 <= r <= 210 and 180 <= g <= 210 and 180 <= b <= 210)
    is_white = (r >= 240 and g >= 240 and b >= 240)
    
    # Check if it matches the alternating pattern
    is_even_position = (x + y) % 2 == 0
    is_odd_position = not is_even_position
    
    # The checkered pattern typically has one color on even positions and another on odd
    if (is_gray and is_even_position) or (is_light_gray and is_odd_position):
        return True
    if (is_light_gray and is_even_position) or (is_gray and is_odd_position):
        return True
    if (is_gray and is_even_position) or (is_white and is_odd_position):
        return True
    if (is_white and is_even_position) or (is_gray and is_odd_position):
        return True
    
    return False

def fix_checkered_transparency(image_path):
    """Remove checkered pattern and make it truly transparent."""
    img = Image.open(image_path).convert("RGBA")
    pixels = img.load()
    width, height = img.size
    
    changed = 0
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            
            # Skip already transparent pixels
            if a == 0:
                continue
            
            # Check if this is a checkered pattern pixel
            if is_checkered_pixel(x, y, r, g, b):
                pixels[x, y] = (0, 0, 0, 0)  # Make transparent
                changed += 1
    
    img.save(image_path, "PNG")
    return changed

def main():
    print("Fixing checkered transparency pattern...")
    
    # Fix the two newly generated textures
    textures_to_fix = [
        "hollow_knight_blade.png",
        "warden_flame_sword.png"
    ]
    
    for filename in textures_to_fix:
        filepath = os.path.join(TARGET_DIR, filename)
        if os.path.exists(filepath):
            changed = fix_checkered_transparency(filepath)
            print(f"  {filename}: {changed} pixels made transparent")
        else:
            print(f"  {filename}: NOT FOUND")
    
    print("Done!")

if __name__ == "__main__":
    main()
