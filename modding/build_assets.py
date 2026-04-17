#!/usr/bin/env python3
"""
ValorantMC Asset Generator
- Generates 16x16 pixel-art PNG textures for all 19 weapons (no PIL needed)
- Generates Minecraft JSON item models for all 19 weapons
- Generates TaCZ gun pack data for the 14 missing weapons
- Packages both as .zip files ready to deploy
"""

import os, json, zlib, struct, zipfile, shutil

BASE       = os.path.dirname(os.path.abspath(__file__))
RP_BASE    = os.path.join(BASE, "resource-pack")
TACZ_BASE  = os.path.join(BASE, "tacz-extract")
OUT        = os.path.join(BASE, "dist")
os.makedirs(OUT, exist_ok=True)

# ─── RAW PNG GENERATOR ──────────────────────────────────────────────────────

def _chunk(ctype: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(ctype + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + ctype + data + struct.pack(">I", crc)

def make_png(pixels: list, w: int = 16, h: int = 16) -> bytes:
    """pixels = list of (r,g,b,a) tuples, row-major"""
    raw = b""
    for y in range(h):
        raw += b"\x00"                                    # filter = None
        for x in range(w):
            r, g, b, a = pixels[y * w + x]
            raw += bytes([r & 0xFF, g & 0xFF, b & 0xFF, a & 0xFF])
    sig  = b"\x89PNG\r\n\x1a\n"
    ihdr = _chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    idat = _chunk(b"IDAT", zlib.compress(raw, 9))
    iend = _chunk(b"IEND", b"")
    return sig + ihdr + idat + iend

T = (0, 0, 0, 0)          # transparent

def _row(s: str, fg, ac) -> list:
    return [fg if c == "1" else (ac if c == "2" else T) for c in s]

def sprite(rows16: list, fg, ac=None) -> bytes:
    ac = ac or tuple(max(0, v - 60) for v in fg[:3]) + (255,)
    pixels = []
    for row in rows16:
        pixels += _row(row, fg, ac)
    return make_png(pixels)

# ─── WEAPON SHAPES (16 rows × 16 cols, '1'=main '2'=accent '.'=transparent) ─

PISTOL = [
    "................",
    "................",
    "....11111111....",
    "...1111111122...",
    "..11111111122...",
    "..11111111122...",
    "...1111111122...",
    ".....11111......",
    ".....11111......",
    ".....11111......",
    ".....11112......",
    ".....11222......",
    "................",
    "................",
    "................",
    "................",
]
SMG = [
    "................",
    "................",
    "...111111111....",
    "..1111111111....",
    ".11111111111....",
    ".22111111111....",
    "..1111111111....",
    "....1111112.....",
    "....1111112.....",
    "....1111112.....",
    "....111222......",
    "................",
    "................",
    "................",
    "................",
    "................",
]
SHOTGUN = [
    "................",
    "................",
    "..1111111111....",
    ".11111111111....",
    ".22111111111....",
    ".11111111111....",
    "..1111111111....",
    ".....1111112....",
    ".....1111112....",
    ".....1111112....",
    ".....111222.....",
    "................",
    "................",
    "................",
    "................",
    "................",
]
RIFLE = [
    "................",
    "................",
    "..11111111111...",
    ".111111111111...",
    "1222111111111...",
    "1111111111111...",
    ".111111111112...",
    "....1111111.....",
    "....1111111.....",
    "....1111111.....",
    "....111222......",
    "................",
    "................",
    "................",
    "................",
    "................",
]
SNIPER = [
    "................",
    "......222.......",
    "....12212.......",
    "..1111111111111.",
    ".21111111111111.",
    "..1111111111111.",
    "....111111......",
    ".....111111.....",
    ".....111111.....",
    ".....111111.....",
    ".....111222.....",
    "................",
    "................",
    "................",
    "................",
    "................",
]
HEAVY = [
    "................",
    "................",
    ".11111111111....",
    "1211111111111...",
    "1211111111111...",
    "1211111111111...",
    ".21111111111....",
    ".....111111.....",
    ".....111111.....",
    "....1111111.....",
    "....1111222.....",
    "....1222........",
    "................",
    "................",
    "................",
    "................",
]
KNIFE = [
    "................",
    "..............1.",
    ".............11.",
    "............211.",
    "...........2111.",
    "..........21111.",
    ".........211111.",
    "........2111111.",
    ".......21111211.",
    "......211112211.",
    ".....2111122211.",
    "....211112222211",
    "...21111112222..",
    "..111111122.....",
    "...1111222......",
    "................",
]
SPIKE = [
    "................",
    ".......11.......",
    "......1111......",
    ".....111111.....",
    "....11111111....",
    "...1111221111...",
    "..111122221111..",
    ".11112222221111.",
    ".11112222221111.",
    "..111122221111..",
    "...1111221111...",
    "....11111111....",
    ".....111111.....",
    "......1111......",
    ".......11.......",
    "................",
]

# weapon name → (shape, (r,g,b,255))
WEAPONS = {
    "classic":  (PISTOL,  (160, 160, 160, 255)),
    "shorty":   (PISTOL,  ( 80,  80,  80, 255)),
    "frenzy":   (PISTOL,  (100, 130, 200, 255)),
    "ghost":    (PISTOL,  (200, 200, 210, 255)),
    "sheriff":  (PISTOL,  (180, 130,  60, 255)),
    "stinger":  (SMG,     (120, 180,  80, 255)),
    "spectre":  (SMG,     (180, 160, 120, 255)),
    "bucky":    (SHOTGUN, (160, 120,  70, 255)),
    "judge":    (SHOTGUN, ( 60,  60,  60, 255)),
    "bulldog":  (RIFLE,   ( 80, 110, 180, 255)),
    "guardian": (RIFLE,   (200, 170,  70, 255)),
    "phantom":  (RIFLE,   ( 60,  90, 130, 255)),
    "vandal":   (RIFLE,   (210,  70,  50, 255)),
    "marshal":  (SNIPER,  (140, 110,  70, 255)),
    "operator": (SNIPER,  ( 50,  80,  60, 255)),
    "ares":     (HEAVY,   (110, 130,  70, 255)),
    "odin":     (HEAVY,   ( 80,  90, 110, 255)),
    "knife":    (KNIFE,   (210, 210, 230, 255)),
    "spike":    (SPIKE,   (220,  40,  40, 255)),
}

# ─── RESOURCE PACK: TEXTURES ────────────────────────────────────────────────

TEX_DIR = os.path.join(RP_BASE, "assets", "minecraft", "textures", "item", "valorantmc")
os.makedirs(TEX_DIR, exist_ok=True)

for name, (shape, color) in WEAPONS.items():
    path = os.path.join(TEX_DIR, f"{name}.png")
    with open(path, "wb") as f:
        f.write(sprite(shape, color))
    print(f"  texture {name}.png")

# ─── RESOURCE PACK: MODELS ─────────────────────────────────────────────────

MDL_DIR = os.path.join(RP_BASE, "assets", "minecraft", "models", "item", "valorantmc")
os.makedirs(MDL_DIR, exist_ok=True)

# Display transforms: each weapon category has appropriate in-hand / GUI transforms
def display_for(category: str) -> dict:
    if category == "pistol":
        return {
            "thirdperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "gui":                   {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "ground":                {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed":                 {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        }
    if category == "rifle":
        return {
            "thirdperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.55, 0.55, 0.55]},
            "gui":                   {"rotation": [0, 0, -30], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "ground":                {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed":                 {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        }
    if category == "sniper":
        return {
            "thirdperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.45, 0.45, 0.45]},
            "gui":                   {"rotation": [0, 0, -20], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
            "ground":                {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed":                 {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
        }
    if category == "heavy":
        return {
            "thirdperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.5, 0.5, 0.5]},
            "gui":                   {"rotation": [0, 0, -30], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "ground":                {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed":                 {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
        }
    # default (smg, shotgun, knife, spike)
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
        "gui":                   {"rotation": [0, 0, -35], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
        "ground":                {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed":                 {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    }

WEAPON_CATEGORY = {
    "classic": "pistol", "shorty": "pistol", "frenzy": "pistol",
    "ghost": "pistol", "sheriff": "pistol",
    "stinger": "smg", "spectre": "smg",
    "bucky": "shotgun", "judge": "shotgun",
    "bulldog": "rifle", "guardian": "rifle", "phantom": "rifle", "vandal": "rifle",
    "marshal": "sniper", "operator": "sniper",
    "ares": "heavy", "odin": "heavy",
    "knife": "knife", "spike": "spike",
}

for name in WEAPONS:
    cat = WEAPON_CATEGORY[name]
    model = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"minecraft:item/valorantmc/{name}"
        },
        "display": display_for(cat)
    }
    path = os.path.join(MDL_DIR, f"{name}.json")
    with open(path, "w") as f:
        json.dump(model, f, indent=2)
    print(f"  model  {name}.json")

# ─── RESOURCE PACK: crossbow.json (complete with all 19 weapons + skins) ──

WEAPON_CMD = {
    "classic": 1001, "shorty": 1002, "frenzy": 1003, "ghost": 1004, "sheriff": 1005,
    "stinger": 2001, "spectre": 2002,
    "bucky": 3001, "judge": 3002,
    "bulldog": 4001, "guardian": 4002, "phantom": 4003, "vandal": 4004,
    "marshal": 5001, "operator": 5002,
    "ares": 6001, "odin": 6002,
    "knife": 7001, "spike": 8001,
}

# Skin CMD offsets: base + tier offset
SKIN_TIER_OFFSETS = {
    "prime":       100, "glitchpop":   200, "reaver":      300,
    "elderflame":  400, "champions2021": 500, "ion":        600,
}
SKIN_WEAPONS = {
    "prime":         ["vandal", "phantom", "classic", "guardian", "knife"],
    "glitchpop":     ["vandal", "phantom", "sheriff", "ghost", "judge"],
    "reaver":        ["vandal", "operator", "sheriff", "spectre"],
    "elderflame":    ["vandal", "operator", "knife"],
    "champions2021": ["vandal"],
    "ion":           ["phantom", "sheriff", "spectre", "bucky", "stinger"],
}

overrides = []
for wname, cmd in sorted(WEAPON_CMD.items(), key=lambda x: x[1]):
    overrides.append({
        "predicate": {"custom_model_data": cmd},
        "model": f"minecraft:item/valorantmc/{wname}"
    })
    # Add skin variants
    for cname, weapons in SKIN_WEAPONS.items():
        if wname in weapons:
            offset = SKIN_TIER_OFFSETS[cname]
            skin_cmd = cmd + offset
            # Create a skin model that tints the base weapon
            skin_model_name = f"{wname}_{cname}"
            overrides.append({
                "predicate": {"custom_model_data": skin_cmd},
                "model": f"minecraft:item/valorantmc/{skin_model_name}"
            })

crossbow_json = {
    "parent": "minecraft:item/crossbow",
    "overrides": sorted(overrides, key=lambda x: x["predicate"]["custom_model_data"])
}
cb_path = os.path.join(RP_BASE, "assets", "minecraft", "models", "item", "crossbow.json")
with open(cb_path, "w") as f:
    json.dump(crossbow_json, f, indent=2)
print("  crossbow.json updated with all weapons + skin variants")

# Create skin model variants (tinted versions)
SKIN_COLORS = {
    "prime":         (  0, 200, 255, 255),  # cyan
    "glitchpop":     (255,  50, 200, 255),  # neon pink
    "reaver":        (180,  30,  30, 255),  # deep red
    "elderflame":    (255, 120,   0, 255),  # flame orange
    "champions2021": (255, 210,   0, 255),  # gold
    "ion":           ( 80, 200, 255, 255),  # electric blue
}

for cname, weapons in SKIN_WEAPONS.items():
    scol = SKIN_COLORS[cname]
    for wname in weapons:
        shape, _ = WEAPONS[wname]
        # Write tinted PNG texture
        tex_path = os.path.join(TEX_DIR, f"{wname}_{cname}.png")
        with open(tex_path, "wb") as f:
            f.write(sprite(shape, scol))
        # Write model JSON
        cat = WEAPON_CATEGORY[wname]
        model = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"minecraft:item/valorantmc/{wname}_{cname}"},
            "display": display_for(cat)
        }
        mdl_path = os.path.join(MDL_DIR, f"{wname}_{cname}.json")
        with open(mdl_path, "w") as f:
            json.dump(model, f, indent=2)
print("  skin model variants created")

# ─── TACZ GUN PACK: MISSING 14 WEAPONS ────────────────────────────────────

# Each missing weapon: stats based on real Valorant data
# ammo types available in TaCZ: 45acp, 357mag, 556x45, 762x39, 762x54, 12g, 9mm
# We reuse closest existing geo/texture/animation from the 5 existing guns

TACZ_GUNS = {
    # EXISTING (skip, already have full data)
    # "ghost", "vandal", "phantom", "sheriff", "odin" → already done

    # PISTOLS (use ghost as base geo)
    "classic": {
        "type": "pistol", "sort": 2,
        "rpm": 390, "ammo": "tacz:45acp", "ammo_amount": 12, "damage": 7.8,
        "hs_mult": 3.0, "fire_modes": ["semi", "burst"],
        "reload_empty": 1.75, "reload_tac": 1.1,
        "draw_time": 0.75, "weight": 2.1,
        "base_geo": "ghost", "base_tex": "ghost", "base_anim": "ghost",
        "base_sounds": "ghost",
        "move_aim": -0.15,
    },
    "shorty": {
        "type": "pistol", "sort": 3,
        "rpm": 222, "ammo": "tacz:12g", "ammo_amount": 2, "damage": 5.25,
        "hs_mult": 2.2, "fire_modes": ["semi"],
        "reload_empty": 1.75, "reload_tac": 1.5,
        "draw_time": 0.75, "weight": 1.5,
        "base_geo": "ghost", "base_tex": "ghost", "base_anim": "ghost",
        "base_sounds": "ghost",
        "move_aim": -0.1,
        "pellets": 7,
    },
    "frenzy": {
        "type": "pistol", "sort": 4,
        "rpm": 600, "ammo": "tacz:45acp", "ammo_amount": 13, "damage": 6.5,
        "hs_mult": 2.5, "fire_modes": ["auto"],
        "reload_empty": 1.5, "reload_tac": 1.1,
        "draw_time": 0.75, "weight": 2.0,
        "base_geo": "ghost", "base_tex": "ghost", "base_anim": "ghost",
        "base_sounds": "ghost",
        "move_aim": -0.15,
    },
    # SMGs
    "stinger": {
        "type": "smg", "sort": 1,
        "rpm": 1200, "ammo": "tacz:45acp", "ammo_amount": 20, "damage": 6.6,
        "hs_mult": 2.5, "fire_modes": ["auto", "burst"],
        "reload_empty": 2.25, "reload_tac": 1.75,
        "draw_time": 1.0, "weight": 2.7,
        "base_geo": "phantom", "base_tex": "phantom", "base_anim": "phantom",
        "base_sounds": "phantom",
        "move_aim": -0.2,
    },
    "spectre": {
        "type": "smg", "sort": 2,
        "rpm": 750, "ammo": "tacz:45acp", "ammo_amount": 30, "damage": 7.8,
        "hs_mult": 2.5, "fire_modes": ["auto"],
        "reload_empty": 2.25, "reload_tac": 1.75,
        "draw_time": 1.0, "weight": 2.9,
        "base_geo": "phantom", "base_tex": "phantom", "base_anim": "phantom",
        "base_sounds": "phantom",
        "move_aim": -0.2,
    },
    # SHOTGUNS
    "bucky": {
        "type": "shotgun", "sort": 1,
        "rpm": 78, "ammo": "tacz:12g", "ammo_amount": 5, "damage": 6.0,
        "hs_mult": 2.0, "fire_modes": ["semi"],
        "reload_empty": 2.5, "reload_tac": 2.5,
        "draw_time": 1.0, "weight": 3.2,
        "base_geo": "phantom", "base_tex": "phantom", "base_anim": "phantom",
        "base_sounds": "phantom",
        "move_aim": -0.25,
        "pellets": 5,
    },
    "judge": {
        "type": "shotgun", "sort": 2,
        "rpm": 210, "ammo": "tacz:12g", "ammo_amount": 7, "damage": 5.5,
        "hs_mult": 2.0, "fire_modes": ["auto"],
        "reload_empty": 2.2, "reload_tac": 2.2,
        "draw_time": 1.0, "weight": 3.4,
        "base_geo": "phantom", "base_tex": "phantom", "base_anim": "phantom",
        "base_sounds": "phantom",
        "move_aim": -0.25,
        "pellets": 7,
    },
    # RIFLES
    "bulldog": {
        "type": "rifle", "sort": 1,
        "rpm": 600, "ammo": "tacz:556x45", "ammo_amount": 24, "damage": 9.15,
        "hs_mult": 4.0, "fire_modes": ["auto", "burst"],
        "reload_empty": 2.0, "reload_tac": 1.5,
        "draw_time": 1.0, "weight": 3.0,
        "base_geo": "vandal", "base_tex": "vandal", "base_anim": "vandal",
        "base_sounds": "vandal",
        "move_aim": -0.2,
    },
    "guardian": {
        "type": "rifle", "sort": 2,
        "rpm": 540, "ammo": "tacz:762x39", "ammo_amount": 12, "damage": 13.0,
        "hs_mult": 3.5, "fire_modes": ["semi"],
        "reload_empty": 2.5, "reload_tac": 1.8,
        "draw_time": 1.0, "weight": 3.2,
        "base_geo": "vandal", "base_tex": "vandal", "base_anim": "vandal",
        "base_sounds": "vandal",
        "move_aim": -0.25,
    },
    # SNIPERS
    "marshal": {
        "type": "sniper", "sort": 1,
        "rpm": 150, "ammo": "tacz:762x54", "ammo_amount": 5, "damage": 18.75,
        "hs_mult": 3.0, "fire_modes": ["semi"],
        "reload_empty": 2.5, "reload_tac": 2.0,
        "draw_time": 1.0, "weight": 3.5,
        "base_geo": "vandal", "base_tex": "vandal", "base_anim": "vandal",
        "base_sounds": "vandal",
        "move_aim": -0.3,
    },
    "operator": {
        "type": "sniper", "sort": 2,
        "rpm": 45, "ammo": "tacz:762x54", "ammo_amount": 5, "damage": 25.5,
        "hs_mult": 3.4, "fire_modes": ["semi"],
        "reload_empty": 3.7, "reload_tac": 2.0,
        "draw_time": 1.5, "weight": 4.5,
        "base_geo": "vandal", "base_tex": "vandal", "base_anim": "vandal",
        "base_sounds": "vandal",
        "move_aim": -0.35,
    },
    # HEAVY
    "ares": {
        "type": "mg", "sort": 1,
        "rpm": 800, "ammo": "tacz:762x39", "ammo_amount": 50, "damage": 9.75,
        "hs_mult": 2.5, "fire_modes": ["auto"],
        "reload_empty": 4.0, "reload_tac": 4.0,
        "draw_time": 1.5, "weight": 5.5,
        "base_geo": "odin", "base_tex": "odin", "base_anim": "odin",
        "base_sounds": "odin",
        "move_aim": -0.3,
    },
    # MELEE
    "knife": {
        "type": "pistol", "sort": 10,    # TaCZ has no melee type, use pistol
        "rpm": 120, "ammo": "tacz:45acp", "ammo_amount": 0, "damage": 7.5,
        "hs_mult": 1.0, "fire_modes": ["semi"],
        "reload_empty": 0.0, "reload_tac": 0.0,
        "draw_time": 0.5, "weight": 0.5,
        "base_geo": "ghost", "base_tex": "ghost", "base_anim": "ghost",
        "base_sounds": "ghost",
        "move_aim": 0.0,
    },
}

def write_json(path, obj, comments=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)

def make_gun_data(name, cfg):
    pellets = cfg.get("pellets", 1)
    data = {
        "ammo": cfg["ammo"],
        "ammo_amount": cfg["ammo_amount"],
        "bolt": "open_bolt",
        "can_crawl": False,
        "can_slide": False,
        "rpm": cfg["rpm"],
        "bullet": {
            "life": 2,
            "bullet_amount": pellets,
            "damage": cfg["damage"],
            "tracer_count_interval": 0,
            "speed": 400,
            "gravity": 0,
            "knockback": 0,
            "friction": 0.02,
            "ignite_entity_time": 2,
            "pierce": 1,
            "extra_damage": {
                "armor_ignore": 0.5,
                "head_shot_multiplier": cfg["hs_mult"],
                "damage_adjust": [
                    {"distance": 20, "damage": cfg["damage"]},
                    {"distance": 50, "damage": round(cfg["damage"] * 0.85, 2)},
                    {"distance": "infinite", "damage": round(cfg["damage"] * 0.75, 2)},
                ]
            }
        },
        "reload": {
            "type": "magazine",
            "infinite": False,
            "feed": {
                "empty": cfg["reload_empty"],
                "tactical": cfg["reload_tac"],
            },
            "cooldown": {
                "empty": cfg["reload_empty"] + 0.25,
                "tactical": cfg["reload_tac"] + 0.25,
            }
        },
        "draw_time": cfg["draw_time"],
        "put_away_time": 0,
        "aim_time": 0.15,
        "sprint_time": 0.15,
        "weight": cfg["weight"],
        "movement_speed": {
            "base": 0,
            "aim": cfg["move_aim"],
            "reload": cfg["move_aim"] * 0.8,
        },
        "fire_mode": cfg["fire_modes"],
    }
    if cfg.get("ammo_amount", 1) == 0:
        data["reload"]["type"] = "none"
        data["reload"]["feed"] = {"empty": 0, "tactical": 0}
        data["reload"]["cooldown"] = {"empty": 0, "tactical": 0}
    return data

def make_gun_index(name, cfg):
    return {
        "name": f"valorant.gun.{name}.name",
        "display": f"valorant:{name}_display",
        "data": f"valorant:{name}_data",
        "tooltip": f"valorant.gun.{name}.desc",
        "type": cfg["type"],
        "item_type": "modern_kinetic",
        "sort": cfg["sort"],
    }

def make_gun_display(name, cfg):
    bg = cfg["base_geo"]
    bt = cfg["base_tex"]
    ba = cfg["base_anim"]
    bs = cfg["base_sounds"]
    return {
        "model": f"valorant:{bg}_geo",
        "texture": f"valorant:gun/uv/{bt}",
        "hud": f"valorant:gun/hud/{bs}_hud",
        "slot": f"valorant:gun/slot/{bs}_slot",
        "animation": f"valorant:{ba}",
        "use_default_animation": "rifle" if cfg["type"] in ("rifle", "smg", "mg", "sniper") else "pistol",
        "third_person_animation": "default",
        "show_crosshair": False,
        "iron_zoom": 1.25,
        "muzzle_flash": {
            "texture": "tacz:flash/common_muzzle_flash",
            "scale": 0.75,
        },
        "ammo": {"tracer_color": "#FF8888"},
        "sounds": {
            "shoot":          f"valorant:{bs}/{bs}_shoot",
            "shoot_3p":       f"valorant:{bs}/{bs}_shoot_3p",
            "reload_empty":   f"valorant:{bs}/{bs}_reload",
            "reload_tactical":f"valorant:{bs}/{bs}_reload",
            "draw":           f"valorant:{bs}/{bs}_draw",
            "kill":           "valorant:kill",
        },
        "controllable": {
            "semi": {"low_frequency": 0.25, "high_frequency": 0.5, "time": 100},
            "auto": {"low_frequency": 0.15, "high_frequency": 0.25, "time": 80},
        }
    }

def make_lang_entries(name):
    disp = name.replace("_", " ").title()
    return {
        f"valorant.gun.{name}.name": disp,
        f"valorant.gun.{name}.desc": f"Valorant {disp}",
    }

# Write all missing gun files
lang_en = {}
for name, cfg in TACZ_GUNS.items():
    base = TACZ_BASE

    # data
    write_json(f"{base}/data/valorant/data/guns/{name}_data.json",   make_gun_data(name, cfg))
    # index
    write_json(f"{base}/data/valorant/index/guns/{name}.json",        make_gun_index(name, cfg))
    # display
    write_json(f"{base}/assets/valorant/display/guns/{name}_display.json", make_gun_display(name, cfg))
    # animation reference file (minimal, uses same animation as base gun)
    bg = cfg["base_anim"]
    anim_ref = {
        "format_version": "1.8.0",
        "animations": {
            f"animation.{name}.idle": {
                "loop": True,
                "animation_length": 0.25,
                "bones": {}
            }
        }
    }
    anim_path = f"{base}/assets/valorant/animations/{name}.animation.json"
    if not os.path.exists(anim_path):
        write_json(anim_path, anim_ref)

    lang_en.update(make_lang_entries(name))
    print(f"  tacz   {name}")

# Update lang file
lang_path = f"{TACZ_BASE}/assets/valorant/lang/en_us.json"
if os.path.exists(lang_path):
    with open(lang_path) as f:
        try:
            existing = json.load(f)
        except:
            existing = {}
    existing.update(lang_en)
    with open(lang_path, "w") as f:
        json.dump(existing, f, indent=2)
else:
    write_json(lang_path, lang_en)
print("  lang/en_us.json updated")

# Add recipe + tacz_tag stubs for new guns
for name, cfg in TACZ_GUNS.items():
    # recipe (give gun in creative/command, no real crafting recipe)
    recipe = {
        "type": "tacz:gun",
        "gun": f"valorant:{name}",
        "ammo": {"item": cfg["ammo"].replace("tacz:", "tacz_guns:")},
    }
    write_json(f"{TACZ_BASE}/data/valorant/recipes/gun/{name}.json", recipe)

    # allow_attachments tag (none for now)
    tag = {"replace": False, "values": []}
    write_json(f"{TACZ_BASE}/data/valorant/tacz_tags/attachments/allow_attachments/{name}.json", tag)

print("  recipes + tags written")

# Update recipe_filter to include all guns
filter_all = {
    "replace": False,
    "values": [f"valorant:{name}" for name in list(TACZ_GUNS.keys()) + ["ghost", "vandal", "phantom", "sheriff", "odin"]]
}
write_json(f"{TACZ_BASE}/data/valorant/recipe_filters/valorant_all.json", filter_all)

# ─── PACKAGE: TACZ GUN PACK ZIP ─────────────────────────────────────────────

print("\nPackaging TaCZ gun pack...")
tacz_zip_path = os.path.join(OUT, "valorant-gunpack.zip")
with zipfile.ZipFile(tacz_zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(TACZ_BASE):
        for file in files:
            abs_path = os.path.join(root, file)
            arc_path = os.path.relpath(abs_path, TACZ_BASE)
            zf.write(abs_path, arc_path)
print(f"  → {tacz_zip_path}")

# ─── PACKAGE: MINECRAFT RESOURCE PACK ZIP ────────────────────────────────────

print("Packaging Minecraft resource pack...")
rp_zip_path = os.path.join(OUT, "ValorantMC-ResourcePack.zip")
with zipfile.ZipFile(rp_zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(RP_BASE):
        for file in files:
            abs_path = os.path.join(root, file)
            arc_path = os.path.relpath(abs_path, RP_BASE)
            zf.write(abs_path, arc_path)
print(f"  → {rp_zip_path}")

# ─── DONE ─────────────────────────────────────────────────────────────────────

print("\n✓ All assets generated.")
print(f"  Resource pack : {rp_zip_path}")
print(f"  TaCZ gun pack : {tacz_zip_path}")
print("\nDeploy instructions:")
print("  1. Host ValorantMC-ResourcePack.zip and set URL in config.yml under resource-pack.url")
print("  2. Place valorant-gunpack.zip in your Forge/Fabric server's tacz gun pack folder")
print("     (typically: config/tacz/gun_packs/ or mods/tacz-default-gun-pack/)")
