package com.valorantmc.mod.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a simple flat Valorant-style arena floating at Y=150
 * so it works above any terrain (ocean, mountains, etc.).
 *
 * Layout (top-down, Z axis = attacker ← → defender):
 *   Attacker spawn side (Z = -18)  ...cover boxes...  Defender spawn side (Z = +18)
 */
public class ArenaManager {

    public static final int FLOOR_Y   = 150;
    public static final int CENTER_X  = 0;
    public static final int CENTER_Z  = 0;

    private static final int W_HALF = 15;   // half-width along X  (30 blocks wide)
    private static final int D_HALF = 20;   // half-depth along Z  (40 blocks deep)
    private static final int CLEAR_H = 5;   // blocks of headroom cleared above floor

    public static void buildArena(MinecraftServer server) {
        ServerLevel world = server.overworld();

        // ── Floor ──────────────────────────────────────────────────────────────
        for (int x = CENTER_X - W_HALF; x <= CENTER_X + W_HALF; x++) {
            for (int z = CENTER_Z - D_HALF; z <= CENTER_Z + D_HALF; z++) {
                place(world, x, FLOOR_Y, z, Blocks.SMOOTH_STONE);
                // clear headroom
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + CLEAR_H; y++) {
                    place(world, x, y, z, Blocks.AIR);
                }
            }
        }

        // ── Outer barrier walls (invisible, unbreakable fence) ─────────────────
        for (int x = CENTER_X - W_HALF - 1; x <= CENTER_X + W_HALF + 1; x++) {
            for (int y = FLOOR_Y; y <= FLOOR_Y + CLEAR_H; y++) {
                place(world, x, y, CENTER_Z - D_HALF - 1, Blocks.BARRIER);
                place(world, x, y, CENTER_Z + D_HALF + 1, Blocks.BARRIER);
            }
        }
        for (int z = CENTER_Z - D_HALF - 1; z <= CENTER_Z + D_HALF + 1; z++) {
            for (int y = FLOOR_Y; y <= FLOOR_Y + CLEAR_H; y++) {
                place(world, CENTER_X - W_HALF - 1, y, z, Blocks.BARRIER);
                place(world, CENTER_X + W_HALF + 1, y, z, Blocks.BARRIER);
            }
        }

        // ── Cover boxes ────────────────────────────────────────────────────────
        // Mid-field crates
        box(world,  -4, CENTER_Z,      3, 2, 2);   // centre-left
        box(world,   2, CENTER_Z,      3, 2, 2);   // centre-right
        box(world,  -1, CENTER_Z - 6,  2, 2, 3);   // mid-left flank
        box(world,  -1, CENTER_Z + 4,  2, 2, 3);   // mid-right flank

        // Attacker-side cover (Z-negative)
        box(world,  -6, CENTER_Z - 12, 2, 2, 4);
        box(world,   4, CENTER_Z - 12, 2, 2, 4);
        box(world,  -1, CENTER_Z - 10, 4, 2, 2);

        // Defender-side cover (Z-positive)
        box(world,  -6, CENTER_Z + 10, 2, 2, 4);
        box(world,   4, CENTER_Z + 10, 2, 2, 4);
        box(world,  -1, CENTER_Z +  8, 4, 2, 2);

        // ── Spawn pads ─────────────────────────────────────────────────────────
        // Attacker side — Z negative, marked with cyan terracotta
        for (int x = CENTER_X - 4; x <= CENTER_X + 4; x++) {
            place(world, x, FLOOR_Y, CENTER_Z - 17, Blocks.CYAN_TERRACOTTA);
        }
        // Defender side — Z positive, marked with orange terracotta
        for (int x = CENTER_X - 4; x <= CENTER_X + 4; x++) {
            place(world, x, FLOOR_Y, CENTER_Z + 17, Blocks.ORANGE_TERRACOTTA);
        }
    }

    // ── Spawn lists ───────────────────────────────────────────────────────────

    public static List<Vec3> getAttackerSpawns() {
        List<Vec3> list = new ArrayList<>();
        for (int i = -3; i <= 3; i++) {
            list.add(new Vec3(CENTER_X + i * 2, FLOOR_Y + 1, CENTER_Z - 17));
        }
        return list;
    }

    public static List<Vec3> getDefenderSpawns() {
        List<Vec3> list = new ArrayList<>();
        for (int i = -3; i <= 3; i++) {
            list.add(new Vec3(CENTER_X + i * 2, FLOOR_Y + 1, CENTER_Z + 17));
        }
        return list;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void place(ServerLevel world, int x, int y, int z,
                               net.minecraft.world.level.block.Block block) {
        world.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    /** Solid box of SMOOTH_STONE, origin at (cx, FLOOR_Y+1, cz), size w×h×d. */
    private static void box(ServerLevel world, int cx, int cz, int w, int h, int d) {
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                for (int dz = 0; dz < d; dz++) {
                    place(world, CENTER_X + cx + dx, FLOOR_Y + 1 + dy,
                            CENTER_Z + cz + dz, Blocks.SMOOTH_STONE);
                }
            }
        }
    }
}
