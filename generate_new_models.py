import os
import json

base_path = "resource-pack/assets/projectatlas"
items_path = os.path.join(base_path, "items")
models_path = os.path.join(base_path, "models/item")

# Ensure directories exist
os.makedirs(items_path, exist_ok=True)
os.makedirs(models_path, exist_ok=True)

# List of simple items (Handheld or Generated)
# (name, parent_model)
items = [
    ("hollow_knight_blade", "minecraft:item/handheld"),
    ("warden_flame_sword", "minecraft:item/handheld"),
    ("ender_sentinel_scythe", "minecraft:item/handheld"),
    ("dragon_slayer", "minecraft:item/handheld"),
    ("tax_collector_axe", "minecraft:item/handheld"),
    ("ascendant_crown", "minecraft:item/generated"),
    ("awakening_medal", "minecraft:item/generated"),
    ("settler_badge", "minecraft:item/generated"),
    ("legend_crown", "minecraft:item/generated"),
    ("healing_salve", "minecraft:item/generated"),
    ("spirit_totem", "minecraft:item/generated"),
    ("explorer_compass", "minecraft:item/generated"),
    ("dungeon_key", "minecraft:item/generated"),
    ("blueprint_generic", "minecraft:item/generated"),
    ("blueprint_barracks", "minecraft:item/generated"),
    ("blueprint_turret", "minecraft:item/generated"),
]

for name, parent in items:
    # 1. Create Model File (assets/projectatlas/models/item/NAME.json)
    model_data = {
        "parent": parent,
        "textures": {
            "layer0": f"projectatlas:item/{name}"
        }
    }
    with open(os.path.join(models_path, f"{name}.json"), "w") as f:
        json.dump(model_data, f, indent=2)

    # 2. Create Item Definition (assets/projectatlas/items/NAME.json)
    item_def_data = {
        "model": {
            "type": "minecraft:model",
            "model": f"projectatlas:item/{name}"
        }
    }
    with open(os.path.join(items_path, f"{name}.json"), "w") as f:
        json.dump(item_def_data, f, indent=2)

    print(f"Generated {name}")

# Special Case: Recovery Compass (Animated / Directional)
# For the new system, we can use the "minecraft:compass" model type in the definition
# calling a model that has overrides? NO, the new system works differently.
# But for now, let's replicate the old behavior: 32 models, one definition file that logic?
# Actually, 1.21.2 item definitions support "minecraft:compass" model type!
# BUT simpler: just create the 32 models and let usage decide?
# The video says "middleman" file points to model.
# For a compass, we typically want the ANGLE to drive the model choice.
# In the new system, we can use "minecraft:ranges" in the item definition!

compass_def = {
    "model": {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:compass",
        "scale": 32.0,  # 32 frames
        "entries": []
    }
}

# Generate 32 entries
for i in range(32):
    # Model: projectatlas:item/recovery_compass_XX
    model_name = f"recovery_compass_{i:02d}"
    
    # 1. Create Model File
    model_data = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"projectatlas:item/{model_name}"
        }
    }
    with open(os.path.join(models_path, f"{model_name}.json"), "w") as f:
        json.dump(model_data, f, indent=2)
        
    # 2. Add entry to Definition
    # Compass range: 0.0 to 1.0 (wrapped) -> scaled by 32
    # So entry 0 is for range [0, 1) etc?
    # Actually range_dispatch entries have "threshold".
    # For a circle logic, it's simpler if we treat it as 0..32
    compass_def["model"]["entries"].append({
        "threshold": float(i),
        "model": {
             "type": "minecraft:model",
             "model": f"projectatlas:item/{model_name}"
        }
    })

# Write the compass definition
# NOTE: Need to verify if "minecraft:compass" property works on recovery compass items.
# It usually does if the item component "minecraft:lodestone_tracker" is present.
# Vanilla recovery compass uses "angle" predicate in old system.
# New system property: "minecraft:compass" (based on lodestone/spawn/death).

with open(os.path.join(items_path, "recovery_compass.json"), "w") as f:
    json.dump(compass_def, f, indent=2)

print("Generated recovery_compass (animated)")
