package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import com.valorantmc.weapons.WeaponType;

import java.io.File;
import java.util.*;

/**
 * Manages weapon skin collections.
 *
 * Each skin overrides the item's CustomModelData so the resource pack
 * can supply a different 3D model/texture for that ID.
 *
 * Skin ID ranges (per weapon base ID + offset):
 *   +0    = default
 *   +100  = skin tier 1 (select edition)
 *   +200  = skin tier 2 (deluxe)
 *   +300  = skin tier 3 (premium)
 *   +400  = skin tier 4 (ultra)
 *   +500  = skin tier 5 (exclusive)
 */
public class SkinManager {

    private final ValorantMC plugin;
    private final File skinsFile;

    /** skinId → SkinData */
    private final Map<String, SkinData> skinRegistry = new LinkedHashMap<>();

    // Per-player owned skins
    private final Map<UUID, Set<String>> playerSkins = new HashMap<>();

    public record SkinData(
            String id,
            String displayName,
            String collection,
            WeaponType weaponType,
            int customModelId,
            int cost,            // Valorant Points (cosmetic currency)
            SkinTier tier
    ) {}

    public enum SkinTier {
        SELECT("Select", 875),
        DELUXE("Deluxe", 1275),
        PREMIUM("Premium", 1775),
        ULTRA("Ultra", 2175),
        EXCLUSIVE("Exclusive", 0);  // Event / battle pass

        public final String displayName;
        public final int    vp;
        SkinTier(String name, int vp) { this.displayName = name; this.vp = vp; }
    }

    public SkinManager(ValorantMC plugin) {
        this.plugin     = plugin;
        this.skinsFile  = new File(plugin.getDataFolder(), "skins.yml");
        registerDefaultSkins();
        loadAll();
    }

    private void registerDefaultSkins() {

        // ── Prime Collection ─────────────────────────────────────────────────
        register("prime_vandal",   "Prime Vandal",   "Prime",  WeaponType.VANDAL,   WeaponType.VANDAL.getCustomModelId()   + 100, SkinTier.PREMIUM);
        register("prime_phantom",  "Prime Phantom",  "Prime",  WeaponType.PHANTOM,  WeaponType.PHANTOM.getCustomModelId()  + 100, SkinTier.PREMIUM);
        register("prime_classic",  "Prime Classic",  "Prime",  WeaponType.CLASSIC,  WeaponType.CLASSIC.getCustomModelId()  + 100, SkinTier.PREMIUM);
        register("prime_guardian", "Prime Guardian", "Prime",  WeaponType.GUARDIAN, WeaponType.GUARDIAN.getCustomModelId() + 100, SkinTier.PREMIUM);
        register("prime_knife",    "Prime//2.0 Blade","Prime", WeaponType.KNIFE,    WeaponType.KNIFE.getCustomModelId()    + 100, SkinTier.PREMIUM);

        // ── Glitchpop Collection ──────────────────────────────────────────────
        register("glitchpop_vandal",   "Glitchpop Vandal",   "Glitchpop", WeaponType.VANDAL,   WeaponType.VANDAL.getCustomModelId()   + 200, SkinTier.ULTRA);
        register("glitchpop_phantom",  "Glitchpop Phantom",  "Glitchpop", WeaponType.PHANTOM,  WeaponType.PHANTOM.getCustomModelId()  + 200, SkinTier.ULTRA);
        register("glitchpop_sheriff",  "Glitchpop Sheriff",  "Glitchpop", WeaponType.SHERIFF,  WeaponType.SHERIFF.getCustomModelId()  + 200, SkinTier.ULTRA);
        register("glitchpop_ghost",    "Glitchpop Ghost",    "Glitchpop", WeaponType.GHOST,    WeaponType.GHOST.getCustomModelId()    + 200, SkinTier.ULTRA);
        register("glitchpop_judge",    "Glitchpop Judge",    "Glitchpop", WeaponType.JUDGE,    WeaponType.JUDGE.getCustomModelId()    + 200, SkinTier.ULTRA);

        // ── Reaver Collection ──────────────────────────────────────────────────
        register("reaver_vandal",  "Reaver Vandal",  "Reaver", WeaponType.VANDAL,   WeaponType.VANDAL.getCustomModelId()   + 300, SkinTier.PREMIUM);
        register("reaver_operator","Reaver Operator","Reaver", WeaponType.OPERATOR, WeaponType.OPERATOR.getCustomModelId() + 300, SkinTier.PREMIUM);
        register("reaver_sheriff", "Reaver Sheriff", "Reaver", WeaponType.SHERIFF,  WeaponType.SHERIFF.getCustomModelId()  + 300, SkinTier.PREMIUM);
        register("reaver_spectre", "Reaver Spectre", "Reaver", WeaponType.SPECTRE,  WeaponType.SPECTRE.getCustomModelId()  + 300, SkinTier.PREMIUM);

        // ── Elderflame Collection ──────────────────────────────────────────────
        register("elderflame_vandal",  "Elderflame Vandal",  "Elderflame", WeaponType.VANDAL,   WeaponType.VANDAL.getCustomModelId()   + 400, SkinTier.ULTRA);
        register("elderflame_operator","Elderflame Operator","Elderflame", WeaponType.OPERATOR, WeaponType.OPERATOR.getCustomModelId() + 400, SkinTier.ULTRA);
        register("elderflame_knife",   "Elderflame Dagger",  "Elderflame", WeaponType.KNIFE,    WeaponType.KNIFE.getCustomModelId()    + 400, SkinTier.ULTRA);

        // ── Champions 2021 ─────────────────────────────────────────────────────
        register("champions2021_vandal", "Champions 2021 Vandal", "Champions", WeaponType.VANDAL, WeaponType.VANDAL.getCustomModelId() + 500, SkinTier.EXCLUSIVE);

        // ── Ion Collection ─────────────────────────────────────────────────────
        register("ion_phantom",  "Ion Phantom",  "Ion", WeaponType.PHANTOM,  WeaponType.PHANTOM.getCustomModelId()  + 600, SkinTier.PREMIUM);
        register("ion_sheriff",  "Ion Sheriff",  "Ion", WeaponType.SHERIFF,  WeaponType.SHERIFF.getCustomModelId()  + 600, SkinTier.PREMIUM);
        register("ion_spectre",  "Ion Spectre",  "Ion", WeaponType.SPECTRE,  WeaponType.SPECTRE.getCustomModelId()  + 600, SkinTier.PREMIUM);
        register("ion_bucky",    "Ion Bucky",    "Ion", WeaponType.BUCKY,    WeaponType.BUCKY.getCustomModelId()    + 600, SkinTier.PREMIUM);
        register("ion_stinger",  "Ion Stinger",  "Ion", WeaponType.STINGER,  WeaponType.STINGER.getCustomModelId()  + 600, SkinTier.PREMIUM);
    }

    private void register(String id, String displayName, String collection,
                           WeaponType type, int modelId, SkinTier tier) {
        skinRegistry.put(id, new SkinData(id, displayName, collection, type, modelId, tier.vp, tier));
    }

    // ── Player skin management ────────────────────────────────────────────────

    public void grantSkin(UUID uuid, String skinId) {
        playerSkins.computeIfAbsent(uuid, k -> new HashSet<>()).add(skinId);
    }

    public boolean hasSkin(UUID uuid, String skinId) {
        Set<String> owned = playerSkins.get(uuid);
        return owned != null && owned.contains(skinId);
    }

    public Set<String> getOwnedSkins(UUID uuid) {
        return playerSkins.getOrDefault(uuid, Collections.emptySet());
    }

    public List<SkinData> getSkinsForWeapon(WeaponType type) {
        List<SkinData> result = new ArrayList<>();
        for (SkinData skin : skinRegistry.values()) {
            if (skin.weaponType() == type) result.add(skin);
        }
        return result;
    }

    public SkinData getSkin(String id) {
        return skinRegistry.get(id);
    }

    public Collection<SkinData> getAllSkins() {
        return skinRegistry.values();
    }

    public int getSkinCount() { return skinRegistry.size(); }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveAll() {
        org.bukkit.configuration.file.YamlConfiguration cfg =
                new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, java.util.Set<String>> e : playerSkins.entrySet()) {
            cfg.set("owned." + e.getKey().toString(), new java.util.ArrayList<>(e.getValue()));
        }
        try { cfg.save(skinsFile); }
        catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to save skins.yml: " + ex.getMessage());
        }
    }

    public void loadAll() {
        if (!skinsFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(skinsFile);
        org.bukkit.configuration.ConfigurationSection sec = cfg.getConfigurationSection("owned");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                java.util.List<String> list = sec.getStringList(key);
                playerSkins.computeIfAbsent(uuid, k -> new java.util.HashSet<>()).addAll(list);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
