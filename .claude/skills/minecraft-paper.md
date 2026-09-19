# Skill: minecraft-paper

You are an expert Paper/Spigot Minecraft plugin developer. This project is **ValorantMC** — a full Valorant experience inside Minecraft, built as a Paper plugin.

## Project context
- **Build system**: Maven (`pom.xml` at root)
- **Java version**: 25
- **Paper API**: `io.papermc.paper:paper-api:26.1.2.build.7-alpha`
- **Group/artifact**: `com.valorantmc:ValorantMC:1.0.0`
- **Source root**: `src/main/java/com/valorantmc/`
- **Build command**: `mvn clean package` (output: `target/ValorantMC-1.0.0.jar`)

## Package layout
```
com.valorantmc
├── ValorantMC.java          — main plugin class (extends JavaPlugin)
├── agents/                  — Agent interface, AgentRole enum, impl/* (12 agents)
├── commands/                — ValorantCommand, MapSetupCommand
├── game/                    — GameState, Spike, ValorantGame, ValorantTeam
├── gui/                     — AdminGUI
├── listeners/               — Ability/Admin/Game/Shop/WeaponListener
├── managers/                — Ability/Agent/Arena/Economy/Game/HUD/Map/Shop/Skin/Stats/WeaponManager
├── shop/                    — AgentSelectGUI, LobbyGUI, ShopGUI, SkinGUI
├── utils/                   — ItemBuilder
└── weapons/                 — Weapon, WeaponCategory, WeaponType
```

## Paper-specific patterns to follow

### Event listeners
```java
public class MyListener implements Listener {
    @EventHandler
    public void onSomething(SomeEvent event) { ... }
}
// Register in onEnable: getServer().getPluginManager().registerEvents(new MyListener(), this);
```

### Scheduling (Folia-safe style preferred)
```java
// Sync task
Bukkit.getScheduler().runTask(plugin, () -> { ... });
// Async task
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { ... });
// Repeating
Bukkit.getScheduler().runTaskTimer(plugin, task -> { ... }, delayTicks, periodTicks);
```

### NBT / persistent data (Paper API)
```java
NamespacedKey key = new NamespacedKey(plugin, "my_key");
item.getItemMeta().getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
```

### Component text (Adventure API — preferred over legacy)
```java
player.sendMessage(Component.text("Hello").color(NamedTextColor.RED));
player.sendActionBar(Component.text("3...2...1..."));
```

### plugin.yml / paper-plugin.yml
Always update `src/main/resources/plugin.yml` when adding new commands or permissions.

## ValorantMC game rules
- Two teams: Attackers (plant spike) vs Defenders (defuse spike)
- 12 rounds; first to 7 wins
- Economy: earn credits per kill/round-win, spend in shop
- Spike: custom NBT item, plant/defuse via right-click interaction
- Abilities tied to agent selection; cooldowns managed by AbilityManager

## Common tasks
- **Add a new agent**: extend `Agent`, implement ability methods, register in `AgentManager`
- **Add a weapon**: add to `WeaponType` enum, configure stats in `WeaponManager`
- **Add a command**: implement `CommandExecutor`, register in `plugin.yml` and `ValorantCommand`
- **Fix item NBT**: use `NamespacedKey` + `PersistentDataContainer`; never rely on item display names for identification
- **HUD updates**: always call `HudManager.updateHUD(player)` after state changes

## Build & deploy
```bash
mvn clean package          # build jar
# Copy target/ValorantMC-1.0.0.jar to server/plugins/ and reload
```
