package com.valorantmc.mod.server;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Persists attacker and defender spawn points to valorantmc-spawns.json
 * in the server's working directory. Survives restarts.
 *
 * Usage (in-game with op):
 *   /vspawn add atk      — save your current standing position as an attacker spawn
 *   /vspawn add def      — save your current standing position as a defender spawn
 *   /vspawn clear atk    — wipe all attacker spawns
 *   /vspawn clear def    — wipe all defender spawns
 *   /vspawn list         — print all saved spawns
 */
public class SpawnConfigManager {

    private static final Path FILE = Paths.get("valorantmc-spawns.json");
    private static SpawnConfigManager INSTANCE;

    private final List<Vec3> attackerSpawns = new ArrayList<>();
    private final List<Vec3> defenderSpawns = new ArrayList<>();

    private SpawnConfigManager() { load(); }

    public static SpawnConfigManager getInstance() {
        if (INSTANCE == null) INSTANCE = new SpawnConfigManager();
        return INSTANCE;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void load() {
        attackerSpawns.clear();
        defenderSpawns.clear();
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            readList(root, "attackerSpawns", attackerSpawns);
            readList(root, "defenderSpawns", defenderSpawns);
        } catch (Exception e) {
            System.err.println("[ValorantMC] Failed to read spawn config: " + e.getMessage());
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.add("attackerSpawns", toArray(attackerSpawns));
        root.add("defenderSpawns", toArray(defenderSpawns));
        try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        } catch (Exception e) {
            System.err.println("[ValorantMC] Failed to save spawn config: " + e.getMessage());
        }
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void addAttacker(Vec3 v) { attackerSpawns.add(v); save(); }
    public void addDefender(Vec3 v) { defenderSpawns.add(v); save(); }

    public void clearAttacker() { attackerSpawns.clear(); save(); }
    public void clearDefender() { defenderSpawns.clear(); save(); }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Vec3> getAttackerSpawns() { return Collections.unmodifiableList(attackerSpawns); }
    public List<Vec3> getDefenderSpawns() { return Collections.unmodifiableList(defenderSpawns); }
    public boolean    hasSpawns()         { return !attackerSpawns.isEmpty() && !defenderSpawns.isEmpty(); }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static void readList(JsonObject root, String key, List<Vec3> out) {
        if (!root.has(key)) return;
        for (JsonElement el : root.getAsJsonArray(key)) {
            JsonObject o = el.getAsJsonObject();
            out.add(new Vec3(o.get("x").getAsDouble(),
                             o.get("y").getAsDouble(),
                             o.get("z").getAsDouble()));
        }
    }

    private static JsonArray toArray(List<Vec3> list) {
        JsonArray arr = new JsonArray();
        for (Vec3 v : list) {
            JsonObject o = new JsonObject();
            o.addProperty("x", Math.round(v.x * 10.0) / 10.0);
            o.addProperty("y", Math.round(v.y * 10.0) / 10.0);
            o.addProperty("z", Math.round(v.z * 10.0) / 10.0);
            arr.add(o);
        }
        return arr;
    }
}
