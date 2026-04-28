package com.valorantmc.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class AdminScreen extends Screen {

    private enum Page { MAIN, GIVE, TROLL, MAP_SETUP, GAME_CONTROL }

    // payload data
    private final List<String> players;
    private final List<String> spawns;
    private final String gameState;
    private final int round;
    private final String mapName;

    // parsed player rows
    private record PlayerInfo(String name, String uuid, int hp, int shield, int credits, String agent, String team) {}
    private final List<PlayerInfo> playerInfos = new ArrayList<>();

    // navigation state
    private Page page = Page.MAIN;
    private String selectedUUID = "";
    private String selectedName = "";

    // labels collected during init(), drawn in render()
    private final List<int[]> labelCoords = new ArrayList<>();
    private final List<String> labelTexts  = new ArrayList<>();

    private static final int COLOR_BG    = 0xDD0A0A14;
    private static final int COLOR_PANEL = 0xCC161620;
    private static final int COLOR_TEXT  = 0xFFDDDDDD;

    public AdminScreen(AdminSyncPayload payload) {
        super(Component.literal("Admin Panel"));
        this.players   = payload.players();
        this.spawns    = payload.spawns();
        this.gameState = payload.gameState();
        this.round     = payload.round();
        this.mapName   = payload.mapName();

        for (String s : players) {
            String[] p = s.split(":");
            if (p.length >= 7) {
                try {
                    playerInfos.add(new PlayerInfo(p[0], p[1],
                            Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                            Integer.parseInt(p[4]), p[5], p[6]));
                } catch (Exception ignored) {}
            }
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        clearWidgets();
        labelCoords.clear();
        labelTexts.clear();
        switch (page) {
            case MAIN         -> buildMain();
            case GIVE         -> buildGive();
            case TROLL        -> buildTroll();
            case MAP_SETUP    -> buildMapSetup();
            case GAME_CONTROL -> buildGameControl();
        }
    }

    // ── MAIN ──────────────────────────────────────────────────────────────────

    private void buildMain() {
        int cx = width / 2, cy = height / 2;
        int bw = 155, bh = 24, gap = 8;

        btn("§e Give Items",  cx - bw - gap, cy - 60, bw, bh, () -> nav(Page.GIVE));
        btn("§c Troll",       cx + gap,      cy - 60, bw, bh, () -> nav(Page.TROLL));
        btn("§b Map Setup",   cx - bw - gap, cy - 28, bw, bh, () -> nav(Page.MAP_SETUP));
        btn("§a Game Control",cx + gap,      cy - 28, bw, bh, () -> nav(Page.GAME_CONTROL));
        btn("§7 Refresh",     cx - 75,       cy + 10, 150, bh, () -> {
            if (minecraft != null && minecraft.player != null)
                minecraft.player.connection.sendCommand("vadmin");
        });
        btn("§8 Close",       cx - 75,       cy + 44, 150, bh, this::onClose);

        // game info labels
        label(cx - 75, cy + 78, "§7Map: §e" + mapName + " §8| §7State: §f" + gameState
                + " §8| §7Round: §f" + round);
        label(cx - 75, cy + 90, "§7Players online: §f" + playerInfos.size());
    }

    // ── GIVE ITEMS ────────────────────────────────────────────────────────────

    private void buildGive() {
        buildBack();
        buildPlayerList();
        if (!selectedUUID.isEmpty()) buildGiveActions();
    }

    private void buildGiveActions() {
        int x = 155, y = 36;
        int bh = 17, gap = 3;

        label(x, y - 10, "§eCredits:");
        for (int[] v : new int[][]{{100},{500},{1000},{3000},{9000}}) {
            int amt = v[0]; int ax = x; int ay = y;
            btn("+" + amt, ax, ay, 52, bh, () -> send("give_credits_" + amt));
            x += 56;
        }
        btn("MAX", x, y, 38, bh, () -> send("give_credits_max")); x = 155; y += bh + gap + 12;

        String[][] cats = {
            {"Sidearms","Classic","Ghost","Sheriff","Frenzy","Shorty"},
            {"SMGs","Stinger","Spectre"},
            {"Shotguns","Bucky","Judge"},
            {"Rifles","Bulldog","Guardian","Phantom","Vandal"},
            {"Snipers","Marshal","Outlaw","Operator"},
            {"Heavy","Ares","Odin"}
        };
        for (String[] cat : cats) {
            label(x, y - 10, "§7" + cat[0] + ":");
            for (int i = 1; i < cat.length; i++) {
                String wep = cat[i]; int bx = x; int by = y;
                btn(wep, bx, by, 70, bh, () -> send("give_weapon_" + wep));
                x += 74;
            }
            x = 155; y += bh + gap + 12;
        }

        label(x, y - 10, "§dUlt:");
        btn("+1 Ult",  x,      y, 68, bh, () -> send("give_ult_1"));
        btn("Full Ult",x + 72, y, 68, bh, () -> send("give_ult_full")); x = 155; y += bh + gap + 12;

        label(x, y - 10, "§aAbility:");
        btn("+1 C", x,       y, 52, bh, () -> send("give_charge_C"));
        btn("+1 Q", x + 56,  y, 52, bh, () -> send("give_charge_Q"));
        btn("+1 E", x + 112, y, 52, bh, () -> send("give_charge_E")); x = 155; y += bh + gap + 12;

        label(x, y - 10, "§9Shield:");
        btn("Light (25)", x,      y, 78, bh, () -> send("give_shield_light"));
        btn("Heavy (50)", x + 82, y, 78, bh, () -> send("give_shield_heavy")); x = 155; y += bh + gap + 12;

        btn("Refill Ammo", x, y, 95, bh, () -> send("give_ammo"));
    }

    // ── TROLL ─────────────────────────────────────────────────────────────────

    private void buildTroll() {
        buildBack();
        buildPlayerList();
        if (!selectedUUID.isEmpty()) buildTrollActions();
    }

    private void buildTrollActions() {
        int x = 155, y = 36;
        int bw = 97, bh = 17, gap = 3, cols = 4;

        String[][] rows = {
            {"Kill","kill"}, {"Freeze","freeze"}, {"Unfreeze","unfreeze"}, {"Blind","blind"},
            {"Nausea","nausea"}, {"Noclip","noclip"}, {"Launch Up","launch"}, {"Strip Gear","strip"},
            {"Max Credits","credits_max"}, {"Zero Credits","credits_zero"}, {"Ignite","ignite"}, {"Random TP","tp_random"},
            {"TP to Me","tp_to_me"}, {"TP to Them","tp_to_target"}, {"Revive","revive"}, {"Clear Effects","clear_effects"},
            {"Speed Boost","speed"}, {"Slowness","slow"}
        };

        int col = 0;
        for (String[] r : rows) {
            String lbl = r[0]; String act = r[1];
            btn(lbl, x, y, bw, bh, () -> send(act));
            col++;
            x += bw + gap;
            if (col >= cols) { col = 0; x = 155; y += bh + gap; }
        }
    }

    // ── MAP SETUP ─────────────────────────────────────────────────────────────

    private void buildMapSetup() {
        buildBack();
        int x = 10, y = 36, bw = 135, bh = 17, gap = 3;

        label(x, y - 12, "§cATK Spawns:");
        btn("+ Add ATK (here)", x, y,      bw, bh, () -> send("spawn_add_atk", "_")); y += bh + gap;
        btn("✕ Clear All ATK",  x, y,      bw, bh, () -> send("spawn_clear_atk", "_")); y += bh + gap * 3;

        label(x, y - 12, "§bDEF Spawns:");
        btn("+ Add DEF (here)", x, y,      bw, bh, () -> send("spawn_add_def", "_")); y += bh + gap;
        btn("✕ Clear All DEF",  x, y,      bw, bh, () -> send("spawn_clear_def", "_")); y += bh + gap * 3;

        btn("§a Save Spawns",   x, y,      bw, bh, () -> send("spawn_save", "_")); y += bh + gap * 4;

        // spawn list with TP buttons
        label(155, 25, "§7Click to teleport:");
        int sx = 155, sy = 36;
        int atkIdx = 0, defIdx = 0;
        for (String spawn : spawns) {
            int colonIdx = spawn.indexOf(':');
            if (colonIdx < 0) continue;
            String type   = spawn.substring(0, colonIdx);
            String coords = spawn.substring(colonIdx + 1);
            String color  = type.equals("atk") ? "§c" : "§b";
            int idx = type.equals("atk") ? atkIdx++ : defIdx++;
            String actionKey = "tp_to_spawn_" + type + "_" + idx;
            btn(color + type.toUpperCase() + " §7" + coords, sx, sy, 220, bh,
                    () -> send(actionKey, "_"));
            sy += bh + gap;
            if (sy > height - 20) break;
        }
    }

    // ── GAME CONTROL ──────────────────────────────────────────────────────────

    private void buildGameControl() {
        buildBack();
        int x = width / 2 - 160, y = 50;
        int bw = 155, bh = 22, gap = 8;

        btn("§c End Round — ATK Wins",  x,        y, bw * 2 + gap, bh, () -> send("end_round_atk", "_")); y += bh + gap;
        btn("§b End Round — DEF Wins",  x,        y, bw * 2 + gap, bh, () -> send("end_round_def", "_")); y += bh + gap;
        btn("§e Skip Buy Phase",        x,        y, bw, bh, () -> send("skip_buy", "_"));
        btn("§7 Pause Game",            x + bw + gap, y, bw, bh, () -> send("pause", "_")); y += bh + gap;
        btn("§7 Resume Game",           x,        y, bw, bh, () -> send("resume", "_"));
        btn("§4 End Game (Force)",      x + bw + gap, y, bw, bh, () -> send("end_game", "_")); y += bh + gap;
        btn("§a Revive All Dead",       x,        y, bw, bh, () -> send("revive_all", "_"));
        btn("§6 Refill All Ammo",       x + bw + gap, y, bw, bh, () -> send("refill_all_ammo", "_")); y += bh + gap;
        btn("§d Start Next Round",      x,        y, bw, bh, () -> send("start_round", "_"));
        btn("§9 Balance Teams",         x + bw + gap, y, bw, bh, () -> send("balance_teams", "_"));
    }

    // ── Player list (left sidebar) ────────────────────────────────────────────

    private void buildPlayerList() {
        int x = 10, y = 36, bw = 135, bh = 15, gap = 2;
        label(x, y - 12, "§7Players:");
        for (PlayerInfo pi : playerInfos) {
            boolean sel = pi.uuid().equals(selectedUUID);
            String lbl = (sel ? "§a▶ " : "§7") + pi.name()
                    + " §8[§c" + pi.hp() + "§8/§9" + pi.shield() + "§8]";
            btn(lbl, x, y, bw, bh, () -> {
                selectedUUID = pi.uuid();
                selectedName = pi.name();
                clearWidgets(); labelCoords.clear(); labelTexts.clear(); init();
            });
            y += bh + gap;
        }
        if (playerInfos.isEmpty()) label(x, y, "§8(no players in game)");
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, COLOR_BG);
        g.fill(0, 0, width, 28, COLOR_PANEL);

        String title = switch (page) {
            case MAIN         -> "§6§lADMIN §r§8| §e" + mapName + " §8[§f" + gameState + "§8] §8Rd §f" + round;
            case GIVE         -> "§e§lGIVE ITEMS §r§8→ §f" + (selectedUUID.isEmpty() ? "select player" : selectedName);
            case TROLL        -> "§c§lTROLL §r§8→ §f" + (selectedUUID.isEmpty() ? "select player" : selectedName);
            case MAP_SETUP    -> "§b§lMAP SETUP §r§8| §e" + mapName;
            case GAME_CONTROL -> "§a§lGAME CONTROL §r§8| Rd §f" + round + " §8[§f" + gameState + "§8]";
        };
        g.drawString(font, title, 8, 10, COLOR_TEXT);

        // draw collected labels
        for (int i = 0; i < labelCoords.size(); i++) {
            int[] c = labelCoords.get(i);
            g.drawString(font, labelTexts.get(i), c[0], c[1], COLOR_TEXT);
        }

        super.render(g, mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void btn(String text, int x, int y, int w, int h, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> action.run())
                .bounds(x, y, w, h).build());
    }

    private void label(int x, int y, String text) {
        labelCoords.add(new int[]{x, y});
        labelTexts.add(text);
    }

    private void nav(Page p) {
        page = p;
        clearWidgets(); labelCoords.clear(); labelTexts.clear(); init();
    }

    private void buildBack() {
        btn("§7◀ Back", 10, 8, 70, 16, () -> {
            page = Page.MAIN;
            clearWidgets(); labelCoords.clear(); labelTexts.clear(); init();
        });
    }

    private void send(String action) {
        ClientPlayNetworking.send(new AdminActionPayload(action, selectedUUID));
    }

    private void send(String action, String uuid) {
        ClientPlayNetworking.send(new AdminActionPayload(action, uuid));
    }
}
