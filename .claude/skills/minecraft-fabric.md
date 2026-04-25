# Skill: minecraft-fabric

You are an expert Minecraft mod developer. This project has a **Forge 1.20.1** submodule (`mod/`) alongside the Paper plugin.

## Project context (`mod/` subdirectory)
- **Build system**: Gradle with ForgeGradle 6
- **Java version**: 17 (toolchain)
- **Forge version**: `net.minecraftforge:forge:1.20.1-47.2.0`
- **MCP mappings**: `official` channel, version `1.20.1`
- **Group/artifact**: `com.valorantmc.mod:valorantmc-mod:1.0.0`
- **Main class**: `com.valorantmc.mod.ValorantMCMod`
- **Build command**: `cd mod && gradlew build` (output: `mod/build/libs/valorantmc-mod-1.0.0.jar`)

## ForgeGradle / Forge 1.20.1 patterns

### Mod entry point
```java
@Mod(ValorantMCMod.MODID)
public class ValorantMCMod {
    public static final String MODID = "valorantmcmod";
    public ValorantMCMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }
    private void commonSetup(final FMLCommonSetupEvent event) { ... }
}
```

### Event handling (Forge event bus)
```java
// On the MinecraftForge bus:
@SubscribeEvent
public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) { ... }

// On the mod bus (registration events):
@SubscribeEvent
public void onRegisterItems(RegisterEvent event) { ... }
```

### Registering objects (deferred register)
```java
public static final DeferredRegister<Item> ITEMS =
    DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
public static final RegistryObject<Item> MY_ITEM =
    ITEMS.register("my_item", () -> new Item(new Item.Properties()));
// In constructor: ITEMS.register(modEventBus);
```

### Client-only code
```java
// Guard with: if (event.getSide() == LogicalSide.CLIENT)
// Or use @OnlyIn(Dist.CLIENT)
```

### mods.toml
Located at `mod/src/main/resources/META-INF/mods.toml` — update when changing modid, version, or dependencies.

## Relationship to Paper plugin
The `mod/` Forge client mod can sync data / provide client-side rendering that complements the Paper server plugin. They communicate via:
- Custom payload packets (Forge `NetworkRegistry`)
- Shared constants kept in a common constants file

## Build & run
```bash
cd mod
./gradlew build           # produces build/libs/valorantmc-mod-1.0.0.jar
./gradlew runClient       # launches Minecraft client with mod loaded (dev env)
```

## Note on Fabric vs Forge
This project uses **Forge**, not Fabric. Do not suggest Fabric mixins or Fabric API calls. Use Forge's `@SubscribeEvent`, `DeferredRegister`, and `ForgeGradle` toolchain throughout.
