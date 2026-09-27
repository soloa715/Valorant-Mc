# Integrations & Communications

## External & Protocol Interfaces
- **Minecraft Network Protocol**:
  - Custom payload packets for Client-Server communication (`valorantmc:ability_cast`, `valorantmc:buy_menu`, `valorantmc:sync_hud`).
- **Resource Pack Delivery**:
  - HTTP hosting for `ValorantMC-ResourcePack.zip`. Configured via `plugins/ValorantMC/config.yml` (`url`, `hash`, `required`).
- **LAN Sync & Local Deployment**:
  - `tools/lan_sync_server.py` exposes a local HTTP server on port 9090 for live test deployments across local instances.
- **Paper / Bukkit Event Hooks**:
  - Entity damage events, player movement hooks, flight anti-cheat overrides, custom inventory GUI listeners for `/vshop`, `/vagent`, `/vskin`.
