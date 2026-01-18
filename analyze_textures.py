"""
Debug script to analyze texture transparency.
"""
from PIL import Image
import os

TEXTURE_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"

def analyze_texture(image_path):
    """Analyze pixel transparency."""
    img = Image.open(image_path).convert("RGBA")
    pixels = img.load()
    
    width, height = img.size
    transparent_count = 0
    opaque_count = 0
    semi_count = 0
    dark_opaque = []
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                transparent_count += 1
            elif a == 255:
                opaque_count += 1
                # Log dark but opaque pixels
                if r <= 30 and g <= 30 and b <= 30:
                    dark_opaque.append((x, y, r, g, b, a))
            else:
                semi_count += 1
    
    return {
        'size': (width, height),
        'transparent': transparent_count,
        'opaque': opaque_count,
        'semi': semi_count,
        'dark_opaque_samples': dark_opaque[:5]
    }

def main():
    # Check first few textures
    test_files = ['hollow_knight_blade.png', 'warden_flame_sword.png', 'dragon_slayer.png']
    
    for filename in test_files:
        filepath = os.path.join(TEXTURE_DIR, filename)
        if os.path.exists(filepath):
            result = analyze_texture(filepath)
            print(f"\n=== {filename} ===")
            print(f"  Size: {result['size']}")
            print(f"  Transparent pixels: {result['transparent']}")
            print(f"  Opaque pixels: {result['opaque']}")
            print(f"  Semi-transparent: {result['semi']}")
            if result['dark_opaque_samples']:
                print(f"  Dark opaque samples (x,y,r,g,b,a): {result['dark_opaque_samples']}")
            else:
                print(f"  No dark opaque pixels found")

if __name__ == "__main__":
    main()
