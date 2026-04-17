package com.valorantmc.managers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedurally generates a simple, symmetrical Valorant-style arena.
 *
 * Layout (top-down, +Z = defender side):
 *
 *      ┌─────────────────────────────────┐
 *      │   ATTACKER SPAWN (z = -30)      │
 *      │                                 │
 *      │   [ SITE A ]      [ SITE B ]    │
 *      │   (-15,?,0)        (15,?,0)     │
 *      │                                 │
 *      │   DEFENDER SPAWN (z = +30)      │
 *      └─────────────────────────────────┘
 *
 *  Arena is 60×60 blocks, 12 blocks tall, built from stone with glass walls.
 */
public class ArenaBuilder {

    public static class Built {
        public final List<Location> attackSpawns = new ArrayList<>();
        public final List<Location> defendSpawns = new ArrayList<>();
        public final List<Location> siteA        = new ArrayList<>();
        public final List<Location> siteB        = new ArrayList<>();
    }

    private static final int HALF = 30;  // arena is 60x60
    private static final int HEIGHT = 12;

    /** Build the arena at `origin` in `world` and return spawn/site locations. */
    public static Built build(World world, int ox, int oy, int oz) {
        Built b = new Built();

        // Floor
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                world.getBlockAt(ox + x, oy - 1, oz + z).setType(Material.SMOOTH_STONE);
            }
        }

        // Clear air above (in case of existing terrain)
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    world.getBlockAt(ox + x, oy + y, oz + z).setType(Material.AIR);
                }
            }
        }

        // Perimeter walls (glass so players can see out)
        for (int y = 0; y < HEIGHT; y++) {
            for (int i = -HALF; i <= HALF; i++) {
                world.getBlockAt(ox + i,    oy + y, oz - HALF).setType(Material.GLASS);
                world.getBlockAt(ox + i,    oy + y, oz + HALF).setType(Material.GLASS);
                world.getBlockAt(ox - HALF, oy + y, oz + i).setType(Material.GLASS);
                world.getBlockAt(ox + HALF, oy + y, oz + i).setType(Material.GLASS);
            }
        }

        // Ceiling (barrier so no escape but invisible)
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                world.getBlockAt(ox + x, oy + HEIGHT, oz + z).setType(Material.BARRIER);
            }
        }

        // Center mid-lane cover (cross of stone walls) to give cover
        buildCoverBox(world, ox - 3, oy, oz - 3, 3, 3, 3);
        buildCoverBox(world, ox + 3, oy, oz + 3, 3, 3, 3);

        // Site A (west) – 7x7 platform one block higher with cover
        buildSitePad(world, ox - 18, oy, oz - 2, 7, 7);
        buildCoverBox(world, ox - 20, oy + 1, oz + 2, 3, 2, 1);
        b.siteA.add(new Location(world, ox - 15 + 0.5, oy + 1, oz + 0.5));

        // Site B (east)
        buildSitePad(world, ox + 12, oy, oz - 2, 7, 7);
        buildCoverBox(world, ox + 20, oy + 1, oz - 2, 3, 2, 1);
        b.siteB.add(new Location(world, ox + 15 + 0.5, oy + 1, oz + 0.5));

        // Attacker spawn line (north edge, z = -HALF + 2)
        int atkZ = oz - HALF + 3;
        for (int i = -4; i <= 4; i += 2) {
            b.attackSpawns.add(new Location(world, ox + i + 0.5, oy, atkZ + 0.5, 0f, 0f));
        }

        // Defender spawn line (south edge, z = HALF - 2)
        int defZ = oz + HALF - 3;
        for (int i = -4; i <= 4; i += 2) {
            b.defendSpawns.add(new Location(world, ox + i + 0.5, oy, defZ + 0.5, 180f, 0f));
        }

        // Light the arena with glowstone pillars at corners
        for (int y = 2; y < HEIGHT; y += 4) {
            world.getBlockAt(ox - HALF + 1, oy + y, oz - HALF + 1).setType(Material.GLOWSTONE);
            world.getBlockAt(ox + HALF - 1, oy + y, oz - HALF + 1).setType(Material.GLOWSTONE);
            world.getBlockAt(ox - HALF + 1, oy + y, oz + HALF - 1).setType(Material.GLOWSTONE);
            world.getBlockAt(ox + HALF - 1, oy + y, oz + HALF - 1).setType(Material.GLOWSTONE);
        }

        // Mark A / B sites with wool for visibility
        markSite(world, ox - 15, oy - 1, oz, Material.YELLOW_WOOL);
        markSite(world, ox + 15, oy - 1, oz, Material.LIGHT_BLUE_WOOL);

        return b;
    }

    private static void buildSitePad(World w, int x0, int y0, int z0, int sx, int sz) {
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                w.getBlockAt(x0 + x, y0, z0 + z).setType(Material.POLISHED_ANDESITE);
            }
        }
    }

    private static void buildCoverBox(World w, int x0, int y0, int z0, int sx, int sy, int sz) {
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    Block b = w.getBlockAt(x0 + x, y0 + y, z0 + z);
                    if (b.getType() == Material.AIR) b.setType(Material.STONE_BRICKS);
                }
            }
        }
    }

    private static void markSite(World w, int cx, int cy, int cz, Material mat) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(cx + dx, cy, cz + dz).setType(mat);
            }
        }
    }
}
