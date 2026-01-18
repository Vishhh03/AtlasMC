from PIL import Image
import os

# Load the north-pointing compass
north_img = Image.open("C:/Users/visha/.gemini/antigravity/brain/9e4dc002-51f0-4922-8e52-8f3cd7637344/death_compass_north_1768649480291.png")

# Resize to 16x16
north_img = north_img.resize((16, 16), Image.NEAREST)

dest_dir = "resource-pack/assets/minecraft/textures/item"
os.makedirs(dest_dir, exist_ok=True)

# Create 32 rotated frames (32 positions = 360/32 = 11.25 degrees per frame)
for i in range(32):
    angle = i * 11.25  # Rotate clockwise (death compass points toward death location)
    rotated = north_img.rotate(-angle, resample=Image.NEAREST, expand=False)
    rotated.save(os.path.join(dest_dir, f"recovery_compass_{i:02d}.png"))
    print(f"Created recovery_compass_{i:02d}.png (rotated {angle}°)")

print("All 32 compass frames created!")
