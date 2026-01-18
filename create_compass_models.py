import os
import json

model_dir = "resource-pack/assets/minecraft/models/item"
os.makedirs(model_dir, exist_ok=True)

# Create 32 recovery compass model files
for i in range(32):
    model = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"minecraft:item/recovery_compass_{i:02d}"
        }
    }
    
    with open(os.path.join(model_dir, f"recovery_compass_{i:02d}.json"), 'w') as f:
        json.dump(model, f, indent=2)
    
    print(f"Created recovery_compass_{i:02d}.json")

print("All 32 model files created!")
