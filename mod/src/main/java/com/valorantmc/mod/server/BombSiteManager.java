package com.valorantmc.mod.server;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Tracks A and B bomb site center positions.
 * A player can only plant the spike when inside a site's radius.
 *
 * Configured via  /vsite add a|b   (stand in site, run command)
 *                 /vsite clear a|b
 *                 /vsite list
 */
public class BombSiteManager {

    private static final Path FILE   = Paths.get("valorantmc-sites.json");
    private static final double RADIUS = 8.0;
    private static BombSiteManager INSTANCE;

    private Vec3 siteA = null;
    private Vec3 siteB = null;

    private BombSiteManager() { load(); }

    public static BombSiteManager getInstance() {
        if (INSTANCE == null) INSTANCE = new BombSiteManager();
        return INSTANCE;
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void setSite(boolean isA, Vec3 pos) {
        if (isA) siteA = pos; else siteB = pos;
        save();
    }

    public void clearSite(boolean isA) {
        if (isA) siteA = null; else siteB = null;
        save();
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** Returns "A", "B", or null if the position is not within any bomb site. */
    public String getSiteAt(Vec3 pos) {
        if (siteA != null && pos.distanceTo(siteA) <= RADIUS) return "A";
        if (siteB != null && pos.distanceTo(siteB) <= RADIUS) return "B";
        return null;
    }

    public boolean hasSites() { return siteA != null || siteB != null; }
    public Vec3 getSiteA() { return siteA; }
    public Vec3 getSiteB() { return siteB; }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("siteA")) siteA = readVec(root.getAsJsonObject("siteA"));
            if (root.has("siteB")) siteB = readVec(root.getAsJsonObject("siteB"));
        } catch (Exception e) {
            System.err.println("[ValorantMC] Failed to read bomb site config: " + e.getMessage());
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        if (siteA != null) root.add("siteA", writeVec(siteA));
        if (siteB != null) root.add("siteB", writeVec(siteB));
        try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        } catch (Exception e) {
            System.err.println("[ValorantMC] Failed to save bomb site config: " + e.getMessage());
        }
    }

    private static Vec3 readVec(JsonObject o) {
        return new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
    }

    private static JsonObject writeVec(Vec3 v) {
        JsonObject o = new JsonObject();
        o.addProperty("x", (int) v.x);
        o.addProperty("y", (int) v.y);
        o.addProperty("z", (int) v.z);
        return o;
    }
}
