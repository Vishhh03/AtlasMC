"""
Create clean pixel art Minecraft sword textures programmatically.
This ensures 100% proper transparency with no semi-transparent pixels.
"""
from PIL import Image
import os

TARGET_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"

def create_sword_texture(filename, blade_colors, handle_colors):
    """
    Create a 16x16 pixel art sword texture.
    Sword is diagonal from bottom-left to top-right (Minecraft style).
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))  # Fully transparent background
    pixels = img.load()
    
    # Sword pattern (1 = handle, 2 = blade dark, 3 = blade light, 4 = blade highlight)
    # Standard Minecraft sword layout:
    #     Row 0-2: Blade tip (top-right)
    #     Row 3-10: Blade body
    #     Row 11-13: Guard/crossguard
    #     Row 14-15: Handle (bottom-left)
    
    pattern = [
        # y, x, type (0=empty, 1=handle, 2=blade_dark, 3=blade_mid, 4=blade_light)
        # Blade tip (top right)
        (1, 14, 4), (1, 15, 3),
        (2, 13, 4), (2, 14, 3), (2, 15, 2),
        (3, 12, 4), (3, 13, 3), (3, 14, 2),
        (4, 11, 4), (4, 12, 3), (4, 13, 2),
        (5, 10, 4), (5, 11, 3), (5, 12, 2),
        (6, 9, 4), (6, 10, 3), (6, 11, 2),
        (7, 8, 4), (7, 9, 3), (7, 10, 2),
        (8, 7, 4), (8, 8, 3), (8, 9, 2),
        (9, 6, 4), (9, 7, 3), (9, 8, 2),
        (10, 5, 4), (10, 6, 3), (10, 7, 2),
        # Guard
        (11, 4, 1), (11, 5, 1), (11, 6, 1),
        (11, 3, 1), (11, 7, 1),
        # Handle
        (12, 3, 1), (12, 4, 1),
        (13, 2, 1), (13, 3, 1),
        (14, 1, 1), (14, 2, 1),
        (15, 0, 1), (15, 1, 1),
    ]
    
    color_map = {
        1: handle_colors[0],  # Handle
        2: blade_colors[0],   # Blade dark 
        3: blade_colors[1],   # Blade mid
        4: blade_colors[2],   # Blade light/highlight
    }
    
    for y, x, ptype in pattern:
        if ptype > 0 and 0 <= x < 16 and 0 <= y < 16:
            pixels[x, y] = color_map[ptype]
    
    filepath = os.path.join(TARGET_DIR, filename)
    img.save(filepath, "PNG")
    print(f"Created: {filename}")

def main():
    print("Creating clean pixel art textures...")
    
    # Hollow Knight Blade - Purple/Cyan colors
    create_sword_texture(
        "hollow_knight_blade.png",
        blade_colors=[
            (80, 40, 120, 255),    # Dark purple
            (140, 80, 180, 255),   # Mid purple
            (100, 200, 220, 255),  # Cyan highlight
        ],
        handle_colors=[
            (40, 40, 50, 255),     # Dark gray handle
        ]
    )
    
    # Warden Flame Sword - Orange/Red colors
    create_sword_texture(
        "warden_flame_sword.png",
        blade_colors=[
            (180, 60, 30, 255),    # Dark red
            (220, 120, 40, 255),   # Orange
            (255, 200, 80, 255),   # Yellow highlight
        ],
        handle_colors=[
            (80, 50, 30, 255),     # Brown handle
        ]
    )
    
    # Dragon Slayer - Gold/Red colors
    create_sword_texture(
        "dragon_slayer.png",
        blade_colors=[
            (150, 40, 40, 255),    # Dark red
            (200, 160, 50, 255),   # Gold
            (255, 220, 100, 255),  # Bright gold
        ],
        handle_colors=[
            (30, 30, 30, 255),     # Black handle
        ]
    )
    
    # Ender Sentinel Scythe - Purple/Black
    create_sword_texture(
        "ender_sentinel_scythe.png",
        blade_colors=[
            (20, 10, 30, 255),     # Very dark purple
            (80, 40, 120, 255),    # Purple
            (180, 100, 220, 255),  # Light purple
        ],
        handle_colors=[
            (40, 40, 50, 255),     # Dark handle
        ]
    )
    
    print("\nDone! All textures created with proper transparency.")

if __name__ == "__main__":
    main()
