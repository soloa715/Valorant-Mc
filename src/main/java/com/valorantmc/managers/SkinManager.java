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
            String taczGunId,
            String taczSkinId,
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

        // ── 3D Melee Knives (Valorant Melee Pack) ────────────────────────────
        registerKnife("prime_karambit",    "Prime Karambit",     "Prime",      "wtyj:eternal_karambit", WeaponType.KNIFE.getCustomModelId() + 100, SkinTier.PREMIUM);
        registerKnife("prime_axe",         "Prime//2.0 Axe",     "Prime",      "wtyj:eternal_axe",      WeaponType.KNIFE.getCustomModelId() + 101, SkinTier.PREMIUM);
        registerKnife("rgx_butterfly",     "RGX 11z Firefly",    "RGX 11z",    "wtyj:future_butterfly", WeaponType.KNIFE.getCustomModelId() + 102, SkinTier.ULTRA);
        registerKnife("rgx_dagger",        "RGX 11z Dagger",     "RGX 11z",    "wtyj:future_dagger",    WeaponType.KNIFE.getCustomModelId() + 103, SkinTier.ULTRA);
        registerKnife("misericordio",      "Misericordio Blade", "VCT",        "wtyj:desire_blade",     WeaponType.KNIFE.getCustomModelId() + 104, SkinTier.EXCLUSIVE);
        registerKnife("smite_hammer",      "Smite Hammer",       "Smite",      "wtyj:thunder_hammer",   WeaponType.KNIFE.getCustomModelId() + 105, SkinTier.SELECT);

        // ── Vandal Collection ───────────────────────────────────────────────
        registerGunSkin("prime_vandal",           "Prime Vandal",           "Prime",      WeaponType.VANDAL, "valorant:vandal", "valorant:vandal_prime",        WeaponType.VANDAL.getCustomModelId() + 100, SkinTier.PREMIUM);
        registerGunSkin("glitchpop_vandal",       "Glitchpop Vandal",       "Glitchpop",  WeaponType.VANDAL, "valorant:vandal", "valorant:vandal_glitchpop",    WeaponType.VANDAL.getCustomModelId() + 200, SkinTier.ULTRA);
        registerGunSkin("reaver_vandal",          "Reaver Vandal",          "Reaver",     WeaponType.VANDAL, "valorant:vandal", "valorant:vandal_reaver",       WeaponType.VANDAL.getCustomModelId() + 300, SkinTier.PREMIUM);
        registerGunSkin("elderflame_vandal",      "Elderflame Vandal",      "Elderflame", WeaponType.VANDAL, "valorant:vandal", "valorant:vandal_elderflame",   WeaponType.VANDAL.getCustomModelId() + 400, SkinTier.ULTRA);
        registerGunSkin("champions2021_vandal",   "Champions 2021 Vandal",  "Champions",  WeaponType.VANDAL, "valorant:vandal", "valorant:vandal_champions2021",WeaponType.VANDAL.getCustomModelId() + 500, SkinTier.EXCLUSIVE);

        // ── Phantom Collection ──────────────────────────────────────────────
        registerGunSkin("prime_phantom",     "Prime Phantom",     "Prime",     WeaponType.PHANTOM, "valorant:phantom", "valorant:phantom_prime",     WeaponType.PHANTOM.getCustomModelId() + 100, SkinTier.PREMIUM);
        registerGunSkin("glitchpop_phantom",    "Glitchpop Phantom", "Glitchpop", WeaponType.PHANTOM, "valorant:phantom", "valorant:phantom_glitchpop", WeaponType.PHANTOM.getCustomModelId() + 200, SkinTier.ULTRA);
        registerGunSkin("ion_phantom",          "Ion Phantom",       "Ion",       WeaponType.PHANTOM, "valorant:phantom", "valorant:phantom_ion",       WeaponType.PHANTOM.getCustomModelId() + 600, SkinTier.PREMIUM);

        // ── Operator Collection ─────────────────────────────────────────────
        registerGunSkin("reaver_operator",      "Reaver Operator",     "Reaver",     WeaponType.OPERATOR, "valorant:operator", "valorant:operator_reaver",    WeaponType.OPERATOR.getCustomModelId() + 300, SkinTier.PREMIUM);
        registerGunSkin("elderflame_operator",  "Elderflame Operator", "Elderflame", WeaponType.OPERATOR, "valorant:operator", "valorant:operator_elderflame", WeaponType.OPERATOR.getCustomModelId() + 400, SkinTier.ULTRA);

        // ── Sheriff Collection ──────────────────────────────────────────────
        registerGunSkin("glitchpop_sheriff",    "Glitchpop Sheriff", "Glitchpop", WeaponType.SHERIFF, "valorant:sheriff", "valorant:sheriff_glitchpop", WeaponType.SHERIFF.getCustomModelId() + 200, SkinTier.ULTRA);
        registerGunSkin("reaver_sheriff",       "Reaver Sheriff",    "Reaver",    WeaponType.SHERIFF, "valorant:sheriff", "valorant:sheriff_reaver",    WeaponType.SHERIFF.getCustomModelId() + 300, SkinTier.PREMIUM);
        registerGunSkin("ion_sheriff",          "Ion Sheriff",       "Ion",       WeaponType.SHERIFF, "valorant:sheriff", "valorant:sheriff_ion",       WeaponType.SHERIFF.getCustomModelId() + 600, SkinTier.PREMIUM);
    }

    private void registerKnife(String id, String displayName, String collection, String taczKnifeId, int modelId, SkinTier tier) {
        skinRegistry.put(id, new SkinData(id, displayName, collection, WeaponType.KNIFE, modelId, taczKnifeId, null, tier.vp, tier));
    }

    private void registerGunSkin(String id, String displayName, String collection, WeaponType type, String gunId, String skinId, int modelId, SkinTier tier) {
        skinRegistry.put(id, new SkinData(id, displayName, collection, type, modelId, gunId, skinId, tier.vp, tier));
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

    // Per-player equipped skins (player -> (weaponType -> skinId))
    private final Map<UUID, Map<WeaponType, String>> equippedSkins = new HashMap<>();

    public void equipSkin(UUID uuid, String skinId) {
        SkinData skin = getSkin(skinId);
        if (skin == null) return;
        equippedSkins.computeIfAbsent(uuid, k -> new EnumMap<>(WeaponType.class)).put(skin.weaponType(), skinId);
        saveAll();
    }

    public void unequipSkin(UUID uuid, WeaponType type) {
        Map<WeaponType, String> map = equippedSkins.get(uuid);
        if (map != null) {
            map.remove(type);
            saveAll();
        }
    }

    public void resetAllEquipped(UUID uuid) {
        equippedSkins.remove(uuid);
        saveAll();
    }

    public String getEquippedSkin(UUID uuid, WeaponType type) {
        Map<WeaponType, String> map = equippedSkins.get(uuid);
        return (map != null) ? map.get(type) : null;
    }

    public boolean isEquipped(UUID uuid, String skinId) {
        SkinData skin = getSkin(skinId);
        if (skin == null) return false;
        String eq = getEquippedSkin(uuid, skin.weaponType());
        return skinId.equals(eq);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveAll() {
        org.bukkit.configuration.file.YamlConfiguration cfg =
                new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, java.util.Set<String>> e : playerSkins.entrySet()) {
            cfg.set("owned." + e.getKey().toString(), new java.util.ArrayList<>(e.getValue()));
        }
        for (Map.Entry<UUID, Map<WeaponType, String>> e : equippedSkins.entrySet()) {
            for (Map.Entry<WeaponType, String> eq : e.getValue().entrySet()) {
                cfg.set("equipped." + e.getKey().toString() + "." + eq.getKey().name(), eq.getValue());
            }
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
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    java.util.List<String> list = sec.getStringList(key);
                    playerSkins.computeIfAbsent(uuid, k -> new java.util.HashSet<>()).addAll(list);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection eqSec = cfg.getConfigurationSection("equipped");
        if (eqSec != null) {
            for (String uuidStr : eqSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    org.bukkit.configuration.ConfigurationSection userEq = eqSec.getConfigurationSection(uuidStr);
                    if (userEq != null) {
                        for (String wtStr : userEq.getKeys(false)) {
                            try {
                                WeaponType wt = WeaponType.valueOf(wtStr);
                                String skinId = userEq.getString(wtStr);
                                if (skinId != null) {
                                    equippedSkins.computeIfAbsent(uuid, k -> new EnumMap<>(WeaponType.class)).put(wt, skinId);
                                }
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
