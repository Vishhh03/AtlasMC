"""
Fix recovery compass textures - these need special handling.
Uses the death_compass artifact and creates all 32 animation frames.
"""
from PIL import Image
import os
import math

ARTIFACT_DIR = r"C:\Users\visha\.gemini\antigravity\brain\9e4dc002-51f0-4922-8e52-8f3cd7637344"
TARGET_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"

def make_transparent(img):
    """Remove white/light backgrounds."""
    img = img.convert("RGBA")
    pixels = img.load()
    width, height = img.size
    
    # Get corner color as background
    corners = [pixels[0, 0][:3], pixels[width-1, 0][:3], pixels[0, height-1][:3], pixels[width-1, height-1][:3]]
    from collections import Counter
    bg_color = Counter(corners).most_common(1)[0][0]
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            distance = abs(r - bg_color[0]) + abs(g - bg_color[1]) + abs(b - bg_color[2])
            if distance < 80:  # More aggressive for compass
                pixels[x, y] = (0, 0, 0, 0)
    return img

def main():
    # Use the death compass texture artifact
    source = os.path.join(ARTIFACT_DIR, "death_compass_texture_1768649239326.png")
    
    if not os.path.exists(source):
        print(f"Source not found: {source}")
        return
    
    # Load and process
    img = Image.open(source)
    img = make_transparent(img)
    img = img.resize((16, 16), Image.Resampling.NEAREST)
    
    # Save as base and all 32 animation frames
    # (For a static compass, we can use the same texture for all frames)
    base_path = os.path.join(TARGET_DIR, "recovery_compass.png")
    img.save(base_path, "PNG")
    print(f"Saved: recovery_compass.png")
    
    for i in range(32):
        frame_path = os.path.join(TARGET_DIR, f"recovery_compass_{i:02d}.png")
        img.save(frame_path, "PNG")
        print(f"Saved: recovery_compass_{i:02d}.png")
    
    print("\nDone!")

if __name__ == "__main__":
    main()
