package com.valorantmc.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ScoreboardScreen extends Screen {

    private record Row(String name, String agent, int kills, int deaths, int assists,
                       int credits, String team, boolean alive) {}

    private final List<Row> atkRows = new ArrayList<>();
    private final List<Row> defRows = new ArrayList<>();
    private final int atkScore, defScore, round;
    private final String gameState;

    private static final int BG      = 0xDD0A0A14;
    private static final int PANEL   = 0xCC161620;
    private static final int RED     = 0xFFFF4655;
    private static final int BLUE    = 0xFF5BC0DE;
    private static final int GOLD    = 0xFFFFEB3B;
    private static final int TEXT    = 0xFFDDDDDD;
    private static final int DIMTEXT = 0xFF888888;

    public ScoreboardScreen(ScoreboardPayload p) {
        super(Component.literal("Scoreboard"));
        this.atkScore  = p.atkScore();
        this.defScore  = p.defScore();
        this.round     = p.round();
        this.gameState = p.gameState();

        for (String s : p.rows()) {
            String[] parts = s.split(":");
            if (parts.length < 8) continue;
            try {
                Row row = new Row(parts[0], parts[1],
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
                        parts[6], parts[7].equals("1"));
                if (row.team().equalsIgnoreCase("ATTACKERS")) atkRows.add(row);
                else defRows.add(row);
            } catch (Exception ignored) {}
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        int w = width, h = height;

        // Full-screen dark background
        g.fill(0, 0, w, h, BG);

        // ── Header ─────────────────────────────────────────────────────────────
        g.fill(0, 0, w, 32, PANEL);
        String header = "§6§lSCOREBOARD §r§8| §cATK §f" + atkScore + " §8: §f" + defScore + " §bDEF  §8| §7Round §f" + round + "  §8[§7" + gameState + "§8]";
        g.drawCenteredString(font, header, w / 2, 12, TEXT);

        // ── Column headers ─────────────────────────────────────────────────────
        int headerY = 40;
        g.fill(0, headerY - 4, w, headerY + 12, 0x88000000);
        drawRow(g, "PLAYER", "AGENT", "K", "D", "A", "¢", headerY, TEXT);

        // ── ATK team ───────────────────────────────────────────────────────────
        int y = headerY + 18;
        g.fill(0, y - 2, w / 2 - 10, y + 8, 0x33FF4655);
        g.drawString(font, "§c§lATTACKERS", 20, y, RED);
        y += 14;

        for (Row r : atkRows) {
            String nameCol = (r.alive() ? "§f" : "§8") + r.name();
            drawRow(g, nameCol, "§b" + r.agent(), str(r.kills()), str(r.deaths()), str(r.assists()), r.credits() + "¢", y, TEXT);
            y += 14;
        }

        // ── DEF team ───────────────────────────────────────────────────────────
        y += 6;
        g.fill(0, y - 2, w / 2 - 10, y + 8, 0x335BC0DE);
        g.drawString(font, "§b§lDEFENDERS", 20, y, BLUE);
        y += 14;

        for (Row r : defRows) {
            String nameCol = (r.alive() ? "§f" : "§8") + r.name();
            drawRow(g, nameCol, "§b" + r.agent(), str(r.kills()), str(r.deaths()), str(r.assists()), r.credits() + "¢", y, TEXT);
            y += 14;
        }

        // ── Footer ─────────────────────────────────────────────────────────────
        g.fill(0, h - 20, w, h, PANEL);
        g.drawCenteredString(font, "§8Press §7Tab §8or §7Esc §8to close", w / 2, h - 14, DIMTEXT);

        super.render(g, mx, my, delta);
    }

    private void drawRow(GuiGraphics g, String name, String agent, String k, String d, String a, String credits, int y, int color) {
        int w = width;
        g.drawString(font, name,    20,          y, color);
        g.drawString(font, agent,   w / 3,       y, color);
        g.drawString(font, k,       w * 55 / 100, y, color);
        g.drawString(font, d,       w * 62 / 100, y, color);
        g.drawString(font, a,       w * 69 / 100, y, color);
        g.drawString(font, credits, w * 76 / 100, y, GOLD);
    }

    private static String str(int n) { return String.valueOf(n); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || keyCode == 258) { onClose(); return true; } // ESC or Tab
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
