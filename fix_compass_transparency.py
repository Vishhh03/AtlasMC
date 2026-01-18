from PIL import Image
import os

# Source - the north-pointing compass with transparency
src = "C:/Users/visha/.gemini/antigravity/brain/9e4dc002-51f0-4922-8e52-8f3cd7637344/death_compass_north_1768649480291.png"
dest_dir = "resource-pack/assets/minecraft/textures/item"

# Load and ensure RGBA mode
north_img = Image.open(src).convert("RGBA")

# Resize to 16x16 with proper transparency handling
north_img = north_img.resize((16, 16), Image.NEAREST)

# Create 32 rotated frames
for i in range(32):
    angle = i * 11.25  # 360 / 32 = 11.25 degrees per frame
    # Rotate with expand=False to keep 16x16, fill with transparent
    rotated = north_img.rotate(-angle, resample=Image.NEAREST, expand=False, fillcolor=(0, 0, 0, 0))
    rotated.save(os.path.join(dest_dir, f"recovery_compass_{i:02d}.png"), "PNG")
    print(f"Created recovery_compass_{i:02d}.png (RGBA, {angle}°)")

# Verify
check = Image.open(os.path.join(dest_dir, "recovery_compass_00.png"))
print(f"\nVerification: Mode = {check.mode}, Size = {check.size}")
