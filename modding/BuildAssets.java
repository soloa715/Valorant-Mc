import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/**
 * ValorantMC Asset Generator
 * Generates pixel-art PNG textures, Minecraft JSON models, and TaCZ gun data
 * for all 19 Valorant weapons, then packages them as deployable zip files.
 *
 * Usage: java BuildAssets.java   (Java 11+ single-file source launch)
 */
public class BuildAssets {

    static final String BASE  = new File("").getAbsolutePath(); // modding/
    static final String RP    = BASE + "/resource-pack";
    static final String TACZ  = BASE + "/tacz-extract";
    static final String DIST  = BASE + "/dist";

    // ─── PIXEL-ART WEAPON SPRITES ───────────────────────────────────────────

    // 16 rows x 16 cols, '1'=main colour, '2'=accent/shadow, '.'=transparent
    static final String[] PISTOL = {
        "................",
        "................",
        "....11111111....",
        "...111111111....",
        "..2211111111....",
        "..1111111111....",
        "...111111111....",
        ".....111111.....",
        ".....111111.....",
        ".....111111.....",
        ".....111122.....",
        ".....112222.....",
        "................",
        "................",
        "................",
        "................",
    };
    static final String[] SMG = {
        "................",
        "................",
        "...1111111111...",
        "..11111111111...",
        ".221111111111...",
        ".111111111111...",
        "..11111111111...",
        "....111111......",
        "....111111......",
        "....111112......",
        "....111222......",
        "................",
        "................",
        "................",
        "................",
        "................",
    };
    static final String[] SHOTGUN = {
        "................",
        "................",
        "..11111111111...",
        ".221111111111...",
        ".111111111111...",
        ".221111111111...",
        "..11111111111...",
        ".....1111222....",
        ".....1111222....",
        ".....1111222....",
        ".....111222.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    };
    static final String[] RIFLE = {
        "................",
        "................",
        "..111111111111..",
        ".2221111111111..",
        ".1111111111111..",
        ".2221111111111..",
        "..111111111111..",
        "....1111111.....",
        "....1111111.....",
        "....1111111.....",
        "....111222......",
        "................",
        "................",
        "................",
        "................",
        "................",
    };
    static final String[] SNIPER = {
        "................",
        ".......222......",
        ".....12222......",
        "..11111111111111",
        ".2111111111111..",
        "..11111111111111",
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
    };
    static final String[] HEAVY = {
        "................",
        "................",
        ".111111111111...",
        "2211111111111...",
        "2211111111111...",
        "2211111111111...",
        ".111111111111...",
        "....11111111....",
        "....11111111....",
        "....11111111....",
        "....111222......",
        "....122222......",
        "................",
        "................",
        "................",
        "................",
    };
    static final String[] KNIFE = {
        "................",
        "..............1.",
        ".............11.",
        "............211.",
        "...........2111.",
        "..........21111.",
        ".........211111.",
        "........2111111.",
        ".......21122211.",
        "......21112221..",
        ".....2111222....",
        "....211112......",
        "...21111222.....",
        "..2111122.......",
        "..11222.........",
        "................",
    };
    static final String[] SPIKE = {
        "................",
        ".......11.......",
        "......1221......",
        ".....122221.....",
        "....12222221....",
        "...1222112221...",
        "..12211111221...",
        ".12211111112211.",
        ".12211111112211.",
        "..12211111221...",
        "...1222112221...",
        "....12222221....",
        ".....122221.....",
        "......1221......",
        ".......11.......",
        "................",
    };

    // Weapon → (shape, R, G, B)
    static final Object[][] WEAPONS = {
        // name,        shape,   R,   G,   B
        {"classic",   PISTOL,  150, 155, 160},
        {"shorty",    PISTOL,   70,  70,  75},
        {"frenzy",    PISTOL,   90, 120, 200},
        {"ghost",     PISTOL,  200, 205, 215},
        {"sheriff",   PISTOL,  190, 140,  60},
        {"stinger",   SMG,     110, 185,  75},
        {"spectre",   SMG,     175, 160, 120},
        {"bucky",     SHOTGUN, 160, 120,  65},
        {"judge",     SHOTGUN,  55,  55,  60},
        {"bulldog",   RIFLE,    75, 105, 185},
        {"guardian",  RIFLE,   195, 170,  65},
        {"phantom",   RIFLE,    55,  85, 130},
        {"vandal",    RIFLE,   210,  65,  45},
        {"marshal",   SNIPER,  145, 110,  65},
        {"operator",  SNIPER,   45,  75,  55},
        {"ares",      HEAVY,   105, 130,  65},
        {"odin",      HEAVY,    75,  90, 115},
        {"knife",     KNIFE,   215, 215, 230},
        {"spike",     SPIKE,   225,  35,  35},
    };

    // Skin collections: name → {weapon,…}  +  (R,G,B) tint
    static final Object[][] SKIN_COLLECTIONS = {
        // name,           R,   G,   B,   weapons…
        {"prime",           0, 200, 255, "vandal","phantom","classic","guardian","knife"},
        {"glitchpop",     255,  45, 200, "vandal","phantom","sheriff","ghost","judge"},
        {"reaver",        185,  25,  25, "vandal","operator","sheriff","spectre"},
        {"elderflame",    255, 115,   0, "vandal","operator","knife"},
        {"champions2021", 255, 210,   0, "vandal"},
        {"ion",            70, 200, 255, "phantom","sheriff","spectre","bucky","stinger"},
    };

    // CMD assignments
    static int cmdFor(String name) {
        return switch (name) {
            case "classic"  -> 1001; case "shorty"   -> 1002; case "frenzy"   -> 1003;
            case "ghost"    -> 1004; case "sheriff"  -> 1005;
            case "stinger"  -> 2001; case "spectre"  -> 2002;
            case "bucky"    -> 3001; case "judge"    -> 3002;
            case "bulldog"  -> 4001; case "guardian" -> 4002;
            case "phantom"  -> 4003; case "vandal"   -> 4004;
            case "marshal"  -> 5001; case "operator" -> 5002;
            case "ares"     -> 6001; case "odin"     -> 6002;
            case "knife"    -> 7001; case "spike"    -> 8001;
            default         -> 9000;
        };
    }

    static int skinOffset(String collection) {
        return switch (collection) {
            case "prime"         -> 100;
            case "glitchpop"     -> 200;
            case "reaver"        -> 300;
            case "elderflame"    -> 400;
            case "champions2021" -> 500;
            case "ion"           -> 600;
            default              -> 700;
        };
    }

    static String categoryFor(String name) {
        return switch (name) {
            case "classic","shorty","frenzy","ghost","sheriff" -> "pistol";
            case "stinger","spectre"                           -> "smg";
            case "bucky","judge"                               -> "shotgun";
            case "bulldog","guardian","phantom","vandal"       -> "rifle";
            case "marshal","operator"                          -> "sniper";
            case "ares","odin"                                 -> "heavy";
            case "knife"                                       -> "knife";
            default                                            -> "other";
        };
    }

    // ─── PNG GENERATION ─────────────────────────────────────────────────────

    static BufferedImage makeSprite(String[] shape16, int r, int g, int b) {
        int ar = Math.max(0, r - 60);
        int ag = Math.max(0, g - 60);
        int ab = Math.max(0, b - 60);
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < 16; row++) {
            String s = shape16[row];
            for (int col = 0; col < 16; col++) {
                char c = col < s.length() ? s.charAt(col) : '.';
                int argb = switch (c) {
                    case '1' -> (255 << 24) | (r << 16) | (g << 8) | b;
                    case '2' -> (255 << 24) | (ar << 16) | (ag << 8) | ab;
                    default  -> 0x00000000;
                };
                img.setRGB(col, row, argb);
            }
        }
        return img;
    }

    static void writePng(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
    }

    // ─── JSON HELPERS ────────────────────────────────────────────────────────

    static void writeJson(String path, String json) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), json);
    }

    static String displayJson(String category) {
        String scale = switch (category) {
            case "pistol"        -> "0.68";
            case "rifle","smg"   -> "0.55";
            case "sniper"        -> "0.45";
            case "heavy"         -> "0.50";
            case "shotgun"       -> "0.58";
            default              -> "0.60";
        };
        double guiScale = switch (category) {
            case "pistol"  -> 1.0;
            case "rifle"   -> 0.90;
            case "sniper"  -> 0.80;
            case "heavy"   -> 0.85;
            default        -> 0.95;
        };
        return """
        {
          "parent": "minecraft:item/generated",
          "textures": { "layer0": "minecraft:item/valorantmc/%WEAPON%" },
          "display": {
            "thirdperson_righthand": { "rotation":[0,-90,25], "translation":[1.13,3.2,1.13], "scale":[%S%,%S%,%S%] },
            "firstperson_righthand": { "rotation":[0,-90,25], "translation":[1.13,3.2,1.13], "scale":[%S%,%S%,%S%] },
            "gui":    { "rotation":[0,0,-30], "translation":[0,0,0], "scale":[%G%,%G%,%G%] },
            "ground": { "rotation":[0,0,0],   "translation":[0,2,0], "scale":[0.5,0.5,0.5] },
            "fixed":  { "rotation":[0,180,0], "translation":[0,0,0], "scale":[1.0,1.0,1.0] }
          }
        }
        """
        .replace("%S%", scale)
        .replace("%G%", String.valueOf(guiScale));
    }

    // ─── TACZ GUN DATA ───────────────────────────────────────────────────────

    record TaczGun(
        String type, int sort, int rpm, String ammo, int ammoCount,
        double damage, double hsMult, String[] fireModes,
        double reloadEmpty, double reloadTac, double drawTime, double weight,
        String baseGeo, String baseTex, String baseAnim, String baseSounds,
        double moveAim, int pellets
    ) {}

    static final Map<String, TaczGun> MISSING_GUNS = new LinkedHashMap<>();
    static {
        MISSING_GUNS.put("classic",  new TaczGun("pistol",1, 390,"tacz:45acp",  12,  7.8, 3.0, new String[]{"semi","burst"}, 1.75,1.10,0.75,2.1,"ghost","ghost","ghost","ghost",-0.15,1));
        MISSING_GUNS.put("shorty",   new TaczGun("pistol",2, 222,"tacz:12g",     2,  5.25,2.2, new String[]{"semi"},         1.75,1.50,0.75,1.5,"ghost","ghost","ghost","ghost",-0.10,7));
        MISSING_GUNS.put("frenzy",   new TaczGun("pistol",3, 600,"tacz:45acp",  13,  6.5, 2.5, new String[]{"auto"},         1.50,1.10,0.75,2.0,"ghost","ghost","ghost","ghost",-0.15,1));
        MISSING_GUNS.put("stinger",  new TaczGun("smg",   1,1200,"tacz:45acp",  20,  6.6, 2.5, new String[]{"auto","burst"}, 2.25,1.75,1.0, 2.7,"phantom","phantom","phantom","phantom",-0.20,1));
        MISSING_GUNS.put("spectre",  new TaczGun("smg",   2, 750,"tacz:45acp",  30,  7.8, 2.5, new String[]{"auto"},         2.25,1.75,1.0, 2.9,"phantom","phantom","phantom","phantom",-0.20,1));
        MISSING_GUNS.put("bucky",    new TaczGun("shotgun",1,78, "tacz:12g",     5,  6.0, 2.0, new String[]{"semi"},         2.50,2.50,1.0, 3.2,"phantom","phantom","phantom","phantom",-0.25,5));
        MISSING_GUNS.put("judge",    new TaczGun("shotgun",2,210,"tacz:12g",     7,  5.5, 2.0, new String[]{"auto"},         2.20,2.20,1.0, 3.4,"phantom","phantom","phantom","phantom",-0.25,7));
        MISSING_GUNS.put("bulldog",  new TaczGun("rifle", 1, 600,"tacz:556x45", 24,  9.15,4.0, new String[]{"auto","burst"}, 2.00,1.50,1.0, 3.0,"vandal","vandal","vandal","vandal",-0.20,1));
        MISSING_GUNS.put("guardian", new TaczGun("rifle", 2, 540,"tacz:762x39", 12, 13.0, 3.5, new String[]{"semi"},         2.50,1.80,1.0, 3.2,"vandal","vandal","vandal","vandal",-0.25,1));
        MISSING_GUNS.put("marshal",  new TaczGun("sniper",1, 150,"tacz:762x54",  5, 18.75,3.0, new String[]{"semi"},         2.50,2.00,1.0, 3.5,"vandal","vandal","vandal","vandal",-0.30,1));
        MISSING_GUNS.put("operator", new TaczGun("sniper",2,  45,"tacz:762x54",  5, 25.5, 3.4, new String[]{"semi"},         3.70,2.00,1.5, 4.5,"vandal","vandal","vandal","vandal",-0.35,1));
        MISSING_GUNS.put("ares",     new TaczGun("mg",    1, 800,"tacz:762x39", 50,  9.75,2.5, new String[]{"auto"},         4.00,4.00,1.5, 5.5,"odin","odin","odin","odin",-0.30,1));
        MISSING_GUNS.put("knife",    new TaczGun("pistol",9, 120,"tacz:45acp",   0,  7.5, 1.0, new String[]{"semi"},         0.00,0.00,0.5, 0.5,"ghost","ghost","ghost","ghost",0.0, 1));
    }

    static String gunDataJson(TaczGun g) {
        return String.format("""
        {
          "ammo": "%s",
          "ammo_amount": %d,
          "bolt": "open_bolt",
          "can_crawl": false,
          "can_slide": false,
          "rpm": %d,
          "bullet": {
            "life": 2,
            "bullet_amount": %d,
            "damage": %.2f,
            "tracer_count_interval": 0,
            "speed": 400,
            "gravity": 0,
            "knockback": 0,
            "friction": 0.02,
            "ignite_entity_time": 2,
            "pierce": 1,
            "extra_damage": {
              "armor_ignore": 0.5,
              "head_shot_multiplier": %.1f,
              "damage_adjust": [
                {"distance": 20,         "damage": %.2f},
                {"distance": 50,         "damage": %.2f},
                {"distance": "infinite", "damage": %.2f}
              ]
            }
          },
          "reload": {
            "type": "magazine",
            "infinite": false,
            "feed": { "empty": %.2f, "tactical": %.2f },
            "cooldown": { "empty": %.2f, "tactical": %.2f }
          },
          "draw_time": %.2f,
          "put_away_time": 0,
          "aim_time": 0.15,
          "sprint_time": 0.15,
          "weight": %.1f,
          "movement_speed": { "base": 0, "aim": %.2f, "reload": %.2f },
          "fire_mode": [%s]
        }
        """,
        g.ammo(), g.ammoCount(), g.rpm(), g.pellets(),
        g.damage(), g.hsMult(),
        g.damage(), g.damage() * 0.85, g.damage() * 0.75,
        g.reloadEmpty(), g.reloadTac(),
        g.reloadEmpty() + 0.25, g.reloadTac() + 0.25,
        g.drawTime(), g.weight(),
        g.moveAim(), g.moveAim() * 0.8,
        buildFireModes(g.fireModes())
        );
    }

    static String buildFireModes(String[] modes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(modes[i]).append("\"");
        }
        return sb.toString();
    }

    static String gunIndexJson(String name, TaczGun g) {
        return String.format("""
        {
          "name": "valorant.gun.%s.name",
          "display": "valorant:%s_display",
          "data": "valorant:%s_data",
          "tooltip": "valorant.gun.%s.desc",
          "type": "%s",
          "item_type": "modern_kinetic",
          "sort": %d
        }
        """, name, name, name, name, g.type(), g.sort());
    }

    static String gunDisplayJson(String name, TaczGun g) {
        String defAnim = switch (g.type()) { case "pistol" -> "pistol"; default -> "rifle"; };
        return String.format("""
        {
          "model": "valorant:%s_geo",
          "texture": "valorant:gun/uv/%s",
          "hud": "valorant:gun/hud/%s_hud",
          "slot": "valorant:gun/slot/%s_slot",
          "animation": "valorant:%s",
          "use_default_animation": "%s",
          "third_person_animation": "default",
          "show_crosshair": false,
          "iron_zoom": 1.25,
          "muzzle_flash": { "texture": "tacz:flash/common_muzzle_flash", "scale": 0.75 },
          "ammo": { "tracer_color": "#FF8888" },
          "sounds": {
            "shoot":           "valorant:%s/%s_shoot",
            "shoot_3p":        "valorant:%s/%s_shoot_3p",
            "reload_empty":    "valorant:%s/%s_reload",
            "reload_tactical": "valorant:%s/%s_reload",
            "draw":            "valorant:%s/%s_draw",
            "kill":            "valorant:kill"
          },
          "controllable": {
            "semi": { "low_frequency": 0.25, "high_frequency": 0.5, "time": 100 },
            "auto": { "low_frequency": 0.15, "high_frequency": 0.25, "time": 80 }
          }
        }
        """,
        g.baseGeo(), g.baseTex(),
        g.baseSounds(), g.baseSounds(),
        g.baseAnim(), defAnim,
        g.baseSounds(), g.baseSounds(),
        g.baseSounds(), g.baseSounds(),
        g.baseSounds(), g.baseSounds(),
        g.baseSounds(), g.baseSounds(),
        g.baseSounds(), g.baseSounds()
        );
    }

    // ─── ZIP HELPER ──────────────────────────────────────────────────────────

    static void zipDir(String dirPath, String zipPath) throws Exception {
        File dir = new File(dirPath);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath))) {
            zipRecurse(zos, dir, dir);
        }
    }

    static void zipRecurse(ZipOutputStream zos, File file, File base) throws Exception {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) zipRecurse(zos, child, base);
        } else {
            String entry = base.toURI().relativize(file.toURI()).getPath();
            zos.putNextEntry(new ZipEntry(entry));
            Files.copy(file.toPath(), zos);
            zos.closeEntry();
        }
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        new File(DIST).mkdirs();

        System.out.println("=== Generating textures ===");
        String texDir = RP + "/assets/minecraft/textures/item/valorantmc";
        new File(texDir).mkdirs();

        // Build lookup maps
        Map<String, String[]> shapeMap = new LinkedHashMap<>();
        Map<String, int[]> colorMap    = new LinkedHashMap<>();
        for (Object[] row : WEAPONS) {
            shapeMap.put((String)row[0], (String[])row[1]);
            colorMap.put((String)row[0], new int[]{(int)row[2],(int)row[3],(int)row[4]});
        }

        // Base weapon textures
        for (var e : shapeMap.entrySet()) {
            int[] c = colorMap.get(e.getKey());
            BufferedImage img = makeSprite(e.getValue(), c[0], c[1], c[2]);
            writePng(img, texDir + "/" + e.getKey() + ".png");
            System.out.println("  texture: " + e.getKey() + ".png");
        }

        // Skin variant textures
        for (Object[] coll : SKIN_COLLECTIONS) {
            String cName = (String) coll[0];
            int cr = (int)coll[1], cg = (int)coll[2], cb = (int)coll[3];
            for (int i = 4; i < coll.length; i++) {
                String wName = (String) coll[i];
                String[] shape = shapeMap.get(wName);
                if (shape == null) continue;
                BufferedImage img = makeSprite(shape, cr, cg, cb);
                writePng(img, texDir + "/" + wName + "_" + cName + ".png");
                System.out.println("  texture: " + wName + "_" + cName + ".png");
            }
        }

        System.out.println("\n=== Generating models ===");
        String mdlDir = RP + "/assets/minecraft/models/item/valorantmc";
        new File(mdlDir).mkdirs();

        for (var e : shapeMap.entrySet()) {
            String wName = e.getKey();
            String cat   = categoryFor(wName);
            String json  = displayJson(cat).replace("%WEAPON%", wName);
            writeJson(mdlDir + "/" + wName + ".json", json);
            System.out.println("  model: " + wName + ".json");
        }

        // Skin models
        for (Object[] coll : SKIN_COLLECTIONS) {
            String cName = (String) coll[0];
            for (int i = 4; i < coll.length; i++) {
                String wName = (String) coll[i];
                if (!shapeMap.containsKey(wName)) continue;
                String cat   = categoryFor(wName);
                String variant = wName + "_" + cName;
                String json  = displayJson(cat).replace("%WEAPON%", variant);
                writeJson(mdlDir + "/" + variant + ".json", json);
            }
        }
        System.out.println("  skin model variants done");

        System.out.println("\n=== Building crossbow.json ===");
        StringBuilder overrides = new StringBuilder();
        // Collect all CMD → model path pairs, sorted
        TreeMap<Integer, String> cmdMap = new TreeMap<>();
        for (Object[] row : WEAPONS) {
            String wName = (String) row[0];
            int cmd = cmdFor(wName);
            cmdMap.put(cmd, "minecraft:item/valorantmc/" + wName);
        }
        for (Object[] coll : SKIN_COLLECTIONS) {
            String cName = (String) coll[0];
            int offset = skinOffset(cName);
            for (int i = 4; i < coll.length; i++) {
                String wName = (String) coll[i];
                int base = cmdFor(wName);
                if (base == 9000) continue;
                cmdMap.put(base + offset, "minecraft:item/valorantmc/" + wName + "_" + cName);
            }
        }
        boolean first = true;
        for (var entry : cmdMap.entrySet()) {
            if (!first) overrides.append(",\n");
            first = false;
            overrides.append(String.format(
                "    { \"predicate\": { \"custom_model_data\": %d }, \"model\": \"%s\" }",
                entry.getKey(), entry.getValue()));
        }
        String crossbow = "{\n  \"parent\": \"minecraft:item/crossbow\",\n  \"overrides\": [\n"
                        + overrides + "\n  ]\n}";
        writeJson(RP + "/assets/minecraft/models/item/crossbow.json", crossbow);
        System.out.println("  crossbow.json: " + cmdMap.size() + " entries");

        System.out.println("\n=== Generating TaCZ gun data ===");
        for (var e : MISSING_GUNS.entrySet()) {
            String name = e.getKey();
            TaczGun g   = e.getValue();

            // data
            writeJson(TACZ + "/data/valorant/data/guns/" + name + "_data.json", gunDataJson(g));
            // index
            writeJson(TACZ + "/data/valorant/index/guns/" + name + ".json",      gunIndexJson(name, g));
            // display
            writeJson(TACZ + "/assets/valorant/display/guns/" + name + "_display.json", gunDisplayJson(name, g));
            // stub animation (reuses base gun's bones via use_default_animation)
            String animFile = TACZ + "/assets/valorant/animations/" + name + ".animation.json";
            if (!new File(animFile).exists()) {
                writeJson(animFile, """
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.placeholder.idle": {
                      "loop": true,
                      "animation_length": 0.25,
                      "bones": {}
                    }
                  }
                }
                """);
            }
            // allow_attachments tag
            writeJson(TACZ + "/data/valorant/tacz_tags/attachments/allow_attachments/" + name + ".json",
                      "{\"replace\":false,\"values\":[]}");
            // recipe stub
            writeJson(TACZ + "/data/valorant/recipes/gun/" + name + ".json",
                      "{\"type\":\"tacz:gun\",\"gun\":\"valorant:" + name + "\"}");

            System.out.println("  tacz: " + name);
        }

        // lang
        StringBuilder lang = new StringBuilder("{\n");
        java.util.List<String> names = new ArrayList<>(MISSING_GUNS.keySet());
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            String display = Character.toUpperCase(n.charAt(0)) + n.substring(1);
            lang.append(String.format("  \"valorant.gun.%s.name\": \"%s\",\n", n, display));
            lang.append(String.format("  \"valorant.gun.%s.desc\": \"Valorant %s\"%s\n",
                        n, display, i == names.size()-1 ? "" : ","));
        }
        lang.append("}");
        String langPath = TACZ + "/assets/valorant/lang/en_us.json";
        String existing = new File(langPath).exists()
                ? Files.readString(Path.of(langPath)).trim() : "{}";
        // Merge: strip outer braces, append
        existing = existing.substring(0, existing.lastIndexOf('}')).trim();
        if (!existing.endsWith("{")) existing += ",\n";
        String merged = existing + lang.toString().substring(1);
        writeJson(langPath, merged);
        System.out.println("  lang updated");

        System.out.println("\n=== Packaging zips ===");
        String taczZip = DIST + "/valorant-gunpack.zip";
        zipDir(TACZ, taczZip);
        System.out.println("  TaCZ gun pack  → " + taczZip);

        String rpZip = DIST + "/ValorantMC-ResourcePack.zip";
        zipDir(RP, rpZip);
        System.out.println("  Resource pack  → " + rpZip);

        // Show zip sizes
        System.out.printf("    TaCZ pack size  : %.1f KB%n", new File(taczZip).length() / 1024.0);
        System.out.printf("    Resource pack   : %.1f KB%n", new File(rpZip).length() / 1024.0);

        System.out.println("\n✓ Done!");
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Host dist/ValorantMC-ResourcePack.zip and set URL in run/plugins/ValorantMC/config.yml");
        System.out.println("  2. Place dist/valorant-gunpack.zip in your TaCZ gun packs folder");
        System.out.println("     (Forge: config/timelessandclassics/gun_packs/  |  check your TaCZ version's docs)");
    }
}
