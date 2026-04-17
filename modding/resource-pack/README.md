# ValorantMC Resource Pack

## How to build

1. Put weapon model `.json` files in `assets/minecraft/models/item/valorantmc/`
2. Zip: select `pack.mcmeta` + `assets/` → compress → `ValorantMC-pack.zip`
3. Host online (GitHub Releases works well)
4. SHA-1: `certutil -hashfile ValorantMC-pack.zip SHA1` (Windows)
5. Update `plugins/ValorantMC/config.yml` with URL and hash

## CustomModelData IDs

Each weapon uses a unique ID from `WeaponType.java` (`customModelId` field).
Place model files at matching paths in `assets/minecraft/models/item/valorantmc/`.

## Tools

- [Blockbench](https://blockbench.net) — free 3D model editor for Minecraft
- Base item: `minecraft:crossbow` (overridden via CustomModelData)
