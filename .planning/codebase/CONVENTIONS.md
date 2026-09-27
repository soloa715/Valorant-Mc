# Code Conventions & Style Guidelines

## Java Code Style
- Standard Java 21 / 25 syntax with strict encapsulation.
- Package naming convention: `com.valorantmc.<subsystem>`.
- Class naming: UpperCamelCase (e.g., `JettAgent`, `SpikeEngine`, `MapSetupCommand`).
- Constants: `UPPER_SNAKE_CASE` (e.g., `DEFAULT_BUY_TIME_SECONDS`).

## Minecraft Engine Conventions
- Event handlers annotated with Bukkit `@EventHandler`.
- Custom payload channels registered in `plugin.yml` / Fabric payload registry.
- Custom item visuals handled via `CustomModelData` integers mapped in resource pack JSONs.

## Error Handling & Logging
- Plugin logger accessed via Bukkit `getLogger()`.
- Command input validated with clear player feedback messages (e.g., color-formatted text using ChatColor / Component APIs).
