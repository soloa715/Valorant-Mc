package com.valorantmc.weapons;

import org.bukkit.Material;

/**
 * Every Valorant weapon with its full stat set.
 *
 * Fields:
 *   displayName   – friendly name
 *   category      – weapon class
 *   cost          – credits (0 = free)
 *   damage        – body-shot damage per bullet
 *   fireRate      – rounds per second
 *   magazineSize  – bullets per magazine (-1 = melee)
 *   reloadTime    – seconds to reload
 *   pellets       – number of pellets (shotguns), 1 for all others
 *   isSniper      – uses arrow projectile instead of raycast
 *   penetrates    – bullet penetrates walls
 *   material      – Minecraft item material used as base
 *   customModelId – CustomModelData for resource-pack skin
 */
public enum WeaponType {

    // ── Sidearms ─────────────────────────────────────────────────────────────
    CLASSIC  ("Classic",   WeaponCategory.SIDEARM,    0, 78,  6.75,  12,  1.75, 1, false, false, Material.WOODEN_SWORD,    1001, 0.040f, 0.015f),
    SHORTY   ("Shorty",    WeaponCategory.SIDEARM,  150, 24,  3.33,   2,  1.75, 2, false, false, Material.WOODEN_HOE,      1002, 0.080f, 0.020f),
    FRENZY   ("Frenzy",    WeaponCategory.SIDEARM,  450, 78, 10.00,  13,  1.50, 1, false, false, Material.STONE_SWORD,     1003, 0.060f, 0.018f),
    GHOST    ("Ghost",     WeaponCategory.SIDEARM,  500,105,  6.75,  15,  1.50, 1, false, false, Material.IRON_SWORD,      1004, 0.025f, 0.012f),
    SHERIFF  ("Sheriff",   WeaponCategory.SIDEARM,  800,160,  4.00,   6,  2.25, 1, false, false, Material.GOLDEN_SWORD,    1005, 0.020f, 0.025f),

    // ── SMGs ─────────────────────────────────────────────────────────────────
    STINGER  ("Stinger",   WeaponCategory.SMG,     1100, 67, 18.00,  20,  2.25, 1, false, false, Material.WOODEN_AXE,      2001, 0.070f, 0.020f),
    SPECTRE  ("Spectre",   WeaponCategory.SMG,     1600, 78, 13.33,  30,  2.25, 1, false, false, Material.STONE_AXE,       2002, 0.050f, 0.016f),

    // ── Shotguns ─────────────────────────────────────────────────────────────
    BUCKY    ("Bucky",     WeaponCategory.SHOTGUN,  900, 34,  1.10,   5,  2.50, 5, false, false, Material.WOODEN_PICKAXE,  3001, 0.150f, 0.030f),
    JUDGE    ("Judge",     WeaponCategory.SHOTGUN, 1850, 34,  3.50,   7,  2.25, 7, false, false, Material.STONE_PICKAXE,   3002, 0.180f, 0.025f),

    // ── Rifles ───────────────────────────────────────────────────────────────
    BULLDOG  ("Bulldog",   WeaponCategory.RIFLE,   2050,116,  9.15,  24,  2.50, 1, false, false, Material.IRON_AXE,        4001, 0.030f, 0.014f),
    GUARDIAN ("Guardian",  WeaponCategory.RIFLE,   2250,195,  5.25,  12,  3.00, 1, false, true,  Material.GOLDEN_AXE,      4002, 0.015f, 0.010f),
    PHANTOM  ("Phantom",   WeaponCategory.RIFLE,   2900,156, 11.00,  30,  2.50, 1, false, false, Material.DIAMOND_SWORD,   4003, 0.025f, 0.010f),
    VANDAL   ("Vandal",    WeaponCategory.RIFLE,   2900,156,  9.75,  25,  2.50, 1, false, false, Material.NETHERITE_SWORD,  4004, 0.030f, 0.012f),

    // ── Snipers ──────────────────────────────────────────────────────────────
    MARSHAL  ("Marshal",   WeaponCategory.SNIPER,   950,202,  1.50,   5,  2.50, 1, true,  true,  Material.BOW,             5001, 0.002f, 0.000f),
    OUTLAW   ("Outlaw",    WeaponCategory.SNIPER,  2400,238,  2.50,   2,  2.50, 2, true,  true,  Material.CROSSBOW,        5002, 0.003f, 0.000f),
    OPERATOR ("Operator",  WeaponCategory.SNIPER,  4700,255,  0.75,   5,  3.70, 1, true,  true,  Material.GOLDEN_HOE,      5003, 0.001f, 0.000f),

    // ── Heavy ─────────────────────────────────────────────────────────────────
    ARES     ("Ares",      WeaponCategory.HEAVY,   1600, 72, 10.00,  50,  3.25, 1, false, false, Material.IRON_PICKAXE,    6001, 0.060f, 0.018f),
    ODIN     ("Odin",      WeaponCategory.HEAVY,   3200, 62, 12.00, 100,  5.00, 1, false, false, Material.DIAMOND_PICKAXE, 6002, 0.070f, 0.016f),

    // ── Melee ────────────────────────────────────────────────────────────────
    KNIFE    ("Knife",     WeaponCategory.MELEE,      0, 50,  2.00,  -1,  0.00, 1, false, false, Material.WOODEN_SWORD,    7001, 0.000f, 0.000f);

    // ── Fields ───────────────────────────────────────────────────────────────
    private final String        displayName;
    private final WeaponCategory category;
    private final int           cost;
    private final int           damage;
    private final double        fireRate;
    private final int           magazineSize;
    private final double        reloadTime;
    private final int           pellets;
    private final boolean       isSniper;
    private final boolean       penetrates;
    private final Material      material;
    private final int           customModelId;
    private final float         baseSpread;
    private final float         recoilPerShot;

    WeaponType(String displayName, WeaponCategory category, int cost,
               int damage, double fireRate, int magazineSize, double reloadTime,
               int pellets, boolean isSniper, boolean penetrates,
               Material material, int customModelId,
               float baseSpread, float recoilPerShot) {
        this.displayName   = displayName;
        this.category      = category;
        this.cost          = cost;
        this.damage        = damage;
        this.fireRate      = fireRate;
        this.magazineSize  = magazineSize;
        this.reloadTime    = reloadTime;
        this.pellets       = pellets;
        this.isSniper      = isSniper;
        this.penetrates    = penetrates;
        this.material      = material;
        this.customModelId = customModelId;
        this.baseSpread    = baseSpread;
        this.recoilPerShot = recoilPerShot;
    }

    // ── Derived stat helpers ─────────────────────────────────────────────────

    /** Headshot multiplier: leg=0.85, body=1.0, head=varies by gun */
    public double getHeadshotMultiplier() {
        return switch (this) {
            case SHERIFF, GUARDIAN, OPERATOR -> 3.4;
            case MARSHAL, OUTLAW             -> 3.0;
            case GHOST, PHANTOM, VANDAL      -> 2.5;
            default                          -> 2.5;
        };
    }

    /** Leg-shot multiplier */
    public double getLegMultiplier() { return 0.85; }

    /** Fire-rate expressed as ticks between shots (20 ticks = 1 second). */
    public int getTicksBetweenShots() {
        return Math.max(1, (int) (20.0 / fireRate));
    }

    /** Reload time in ticks */
    public long getReloadTicks() {
        return (long) (reloadTime * 20);
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public String         getDisplayName()  { return displayName;   }
    public WeaponCategory getCategory()     { return category;      }
    public int            getCost()         { return cost;          }
    public int            getDamage()       { return damage;        }
    public double         getFireRate()     { return fireRate;      }
    public int            getMagazineSize() { return magazineSize;  }
    public double         getReloadTime()   { return reloadTime;    }
    public int            getPellets()      { return pellets;       }
    public boolean        isSniper()        { return isSniper;      }
    public boolean        penetrates()      { return penetrates;    }
    public Material       getMaterial()     { return material;      }
    public int            getCustomModelId(){ return customModelId; }
    public float          getBaseSpread()   { return baseSpread;    }
    public float          getRecoilPerShot(){ return recoilPerShot; }
    public boolean        isFree()          { return cost == 0;     }
    public boolean        isMelee()         { return category == WeaponCategory.MELEE; }
}
