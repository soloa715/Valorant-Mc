import os
import json
from pathlib import Path

models_dir = Path("src/main/resources/pack/assets/minecraft/models/item")
items_dir = Path("src/main/resources/pack/assets/minecraft/items")
items_dir.mkdir(parents=True, exist_ok=True)

for model_file in models_dir.glob("*.json"):
    item_name = model_file.stem
    with open(model_file, "r", encoding="utf-8-sig") as f:
        data = json.load(f)
    
    overrides = data.get("overrides", [])
    cases = []
    for ov in overrides:
        cmd = ov.get("predicate", {}).get("custom_model_data")
        target_model = ov.get("model")
        if cmd is not None and target_model:
            cases.append({
                "when": cmd,
                "model": {
                    "type": "minecraft:model",
                    "model": target_model
                }
            })
    
    if cases:
        item_data = {
            "model": {
                "type": "minecraft:select",
                "property": "minecraft:custom_model_data",
                "fallback": {
                    "type": "minecraft:model",
                    "model": f"minecraft:item/{item_name}"
                },
                "cases": cases
            }
        }
    else:
        item_data = {
            "model": {
                "type": "minecraft:model",
                "model": f"minecraft:item/{item_name}"
            }
        }
    
    out_file = items_dir / f"{item_name}.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(item_data, f, indent=2)
    print(f"Generated 1.21.4 item model: {out_file} ({len(cases)} custom model cases)")

print("All 1.21.4 item model files generated successfully!")
