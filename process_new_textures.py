"""
Process newly generated textures and copy to resource pack.
Also copy reference textures from working Apex pack if available.
"""
from PIL import Image
import os
import shutil

ARTIFACT_DIR = r"C:\Users\visha\.gemini\antigravity\brain\9e4dc002-51f0-4922-8e52-8f3cd7637344"
TARGET_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\resource-pack\assets\projectatlas\textures\item"
APEX_DIR = r"c:\Users\visha\Desktop\Mc_Plugin\temp_apex_pack"

# New generated textures
NEW_TEXTURES = {
    "hollow_knight_blade_1768753323107.png": "hollow_knight_blade.png",
    "warden_flame_sword_1768753341627.png": "warden_flame_sword.png",
}

def resize_and_save(source_path, target_path):
    """Resize to 16x16 and save."""
    img = Image.open(source_path).convert("RGBA")
    img = img.resize((16, 16), Image.Resampling.NEAREST)
    img.save(target_path, "PNG")
    print(f"  Saved: {os.path.basename(target_path)}")

def main():
    print("Processing new textures...")
    
    # Process newly generated textures
    for source_name, target_name in NEW_TEXTURES.items():
        source_path = os.path.join(ARTIFACT_DIR, source_name)
        target_path = os.path.join(TARGET_DIR, target_name)
        
        if os.path.exists(source_path):
            resize_and_save(source_path, target_path)
        else:
            print(f"  MISSING: {source_name}")
    
    # Check if Apex pack has textures we can reference
    apex_textures = os.path.join(APEX_DIR, "assets", "tutorial", "textures", "item")
    if os.path.exists(apex_textures):
        print(f"\nFound Apex reference textures at: {apex_textures}")
        for f in os.listdir(apex_textures):
            print(f"  - {f}")
    else:
        print(f"\nNo Apex reference found at: {apex_textures}")
    
    print("\nDone!")

if __name__ == "__main__":
    main()
