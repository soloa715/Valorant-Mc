package com.valorantmc.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Full Valorant-style HUD overlay.
 *
 * Layout:
 *   Top-center  : score  "5 – 3"
 *   Top-right   : kill feed (4 s TTL per entry)
 *   Bottom-left : agent name / HP bar / shield bar / ability slots
 *   Bottom-right: ammo, credits, minimap
 *   Center      : spike countdown (when spike is planted/defusing)
 */
public final class ValorantHudRenderer {

    // ── Color palette (ARGB) ────────────────────────────────────────────────────
    private static final int C_HP       = 0xFF4CAF50;
    private static final int C_SHIELD   = 0xFF29B6F6;
    private static final int C_BG       = 0x99000000;
    private static final int C_WHITE    = 0xFFFFFFFF;
    private static final int C_GREY     = 0xFFAAAAAA;
    private static final int C_DARK     = 0xFF444444;
    private static final int C_ABLE     = 0xFF4FC3F7;
    private static final int C_ULT_RDY  = 0xFFFFD700;
    private static final int C_ULT_PRG  = 0xFF888888;
    private static final int C_ATK      = 0xFFFF4444;
    private static final int C_DEF      = 0xFF4488FF;
    private static final int C_SPIKE    = 0xFFFF6B35;
    private static final int C_CREDITS  = 0xFFFFEB3B;
    private static final int C_ALLY     = 0xFF66BB6A;
    private static final int C_ENEMY    = 0xFFEF5350;
    private static final int C_CD_OVER  = 0xBB000000;

    private ValorantHudRenderer() {}

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    // ── Entry point ─────────────────────────────────────────────────────────────

    public static void render(GuiGraphics ctx, int W, int H) {
        Minecraft mc = Minecraft.getInstance();
        Font tr = mc.font;

        int health        = ValorantHudState.health;
        int shield        = ValorantHudState.shield;
        int ammo          = ValorantHudState.ammo;
        int maxAmmo       = ValorantHudState.maxAmmo;
        int reserve       = ValorantHudState.reserve;
        int chargesC      = ValorantHudState.chargesC;
        int chargesQ      = ValorantHudState.chargesQ;
        int chargesE      = ValorantHudState.chargesE;
        int cooldownC     = ValorantHudState.cooldownC;
        int cooldownQ     = ValorantHudState.cooldownQ;
        int cooldownE     = ValorantHudState.cooldownE;
        int ultProg       = ValorantHudState.ultProgress;
        int ultMax        = ValorantHudState.ultMax;
        String agent      = ValorantHudState.agentName;
        int credits       = ValorantHudState.credits;
        int atkScore      = ValorantHudState.atkScore;
        int defScore      = ValorantHudState.defScore;
        int spikeState    = ValorantHudState.spikeState;
        int spikeTicks    = ValorantHudState.spikeTimerTicks;
        String roster     = ValorantHudState.teamRoster;
        String killFeed   = ValorantHudState.killFeed;
        long kfTime       = ValorantHudState.killFeedShownAt;
        String radarData  = ValorantHudState.radarData;

        renderScore(ctx, tr, W, atkScore, defScore);
        renderKillFeed(ctx, tr, W, killFeed, kfTime);
        renderBottomLeft(ctx, tr, H, health, shield, chargesC, chargesQ, chargesE,
                cooldownC, cooldownQ, cooldownE, ultProg, ultMax, agent);
        renderBottomRight(ctx, tr, W, H, ammo, maxAmmo, reserve, credits);
        renderMinimap(ctx, W, H, radarData);
        if (spikeState > 0) renderSpike(ctx, tr, W, H, spikeState, spikeTicks);
    }

    // ── Score (top-center) ──────────────────────────────────────────────────────

    private static void renderScore(GuiGraphics ctx, Font tr, int W, int atk, int def) {
        String text = atk + " – " + def;
        int tw = tr.width(text);
        int x  = (W - tw) / 2;
        int y  = 6;
        ctx.fill(x - 6, y - 2, x + tw + 6, y + 12, C_BG);
        String atkStr = String.valueOf(atk);
        String sep    = " – ";
        String defStr = String.valueOf(def);
        ctx.drawString(tr, atkStr, x, y, C_ATK, true);
        int ax = x + tr.width(atkStr);
        ctx.drawString(tr, sep, ax, y, C_WHITE, false);
        ax += tr.width(sep);
        ctx.drawString(tr, defStr, ax, y, C_DEF, true);
    }

    // ── Kill feed (top-right, 4 s TTL) ─────────────────────────────────────────

    private static void renderKillFeed(GuiGraphics ctx, Font tr, int W, String killFeed, long kfTime) {
        if (killFeed == null || killFeed.isEmpty()) return;
        if (System.currentTimeMillis() - kfTime > 4000) return;

        int tw = tr.width(killFeed);
        int x  = W - tw - 10;
        int y  = 10;
        ctx.fill(x - 4, y - 2, x + tw + 4, y + 11, C_BG);
        ctx.drawString(tr, killFeed, x, y, C_WHITE, false);
    }

    // ── Bottom-left HUD: Agent / HP / Shield / Abilities / Ult ────────────────

    private static void renderBottomLeft(GuiGraphics ctx, Font tr, int H,
                                         int hp, int shield,
                                         int chargesC, int chargesQ, int chargesE,
                                         int cdC, int cdQ, int cdE,
                                         int ultProg, int ultMax,
                                         String agent) {
        int x = 10;
        int y = H - 55;

        ctx.fill(x - 4, y - 4, x + 190, H - 6, C_BG);

        // Agent name
        if (agent != null && !agent.isEmpty()) {
            ctx.drawString(tr, agent.toUpperCase(), x, y - 12, C_WHITE, true);
        }

        // Health bar (100 px wide)
        int hpWidth = clamp(hp, 0, 100);
        ctx.fill(x, y, x + 100, y + 8, C_DARK);
        ctx.fill(x, y, x + hpWidth, y + 8, C_HP);
        ctx.drawString(tr, String.valueOf(hp), x + 104, y, C_WHITE, false);

        // Shield bar (50 px max shield, 100 px width scale = 2 px per shield point)
        int shY = y + 11;
        int shWidth = clamp(shield * 2, 0, 100);
        ctx.fill(x, shY, x + 100, shY + 6, C_DARK);
        ctx.fill(x, shY, x + shWidth, shY + 6, C_SHIELD);
        ctx.drawString(tr, String.valueOf(shield), x + 104, shY - 1, C_SHIELD, false);

        // Ability & Ult row
        int slotY  = shY + 10;
        int slotW  = 22;
        int slotH  = 20;
        int gap    = 4;

        renderAbilitySlot(ctx, tr, x,                       slotY, slotW, slotH, "C", chargesC, cdC);
        renderAbilitySlot(ctx, tr, x + slotW + gap,         slotY, slotW, slotH, "Q", chargesQ, cdQ);
        renderAbilitySlot(ctx, tr, x + (slotW + gap) * 2,   slotY, slotW, slotH, "E", chargesE, cdE);
        renderUltSlot    (ctx, tr, x + (slotW + gap) * 3,   slotY, slotW + 8, slotH, ultProg, ultMax);
    }

    private static void renderAbilitySlot(GuiGraphics ctx, Font tr,
                                           int x, int y, int w, int h,
                                           String key, int charges, int cdSecs) {
        boolean ready = charges > 0 && cdSecs <= 0;
        int borderCol = ready ? C_ABLE : C_DARK;

        ctx.fill(x, y, x + w, y + h, C_BG);
        ctx.fill(x, y, x + w, y + 1, borderCol);
        ctx.fill(x, y + h - 1, x + w, y + h, borderCol);
        ctx.fill(x, y, x + 1, y + h, borderCol);
        ctx.fill(x + w - 1, y, x + w, y + h, borderCol);

        ctx.drawString(tr, key, x + (w - tr.width(key)) / 2, y + 2, ready ? C_WHITE : C_GREY, false);

        if (cdSecs > 0) {
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_CD_OVER);
            String cdStr = String.valueOf(cdSecs);
            ctx.drawString(tr, cdStr, x + (w - tr.width(cdStr)) / 2, y + 6, C_WHITE, false);
        } else {
            String chStr = charges > 0 ? "•".repeat(charges) : "—";
            ctx.drawString(tr, chStr, x + (w - tr.width(chStr)) / 2, y + 10, ready ? C_ABLE : C_GREY, false);
        }
    }

    private static void renderUltSlot(GuiGraphics ctx, Font tr,
                                       int x, int y, int w, int h,
                                       int ultProg, int ultMax) {
        boolean ready = ultMax > 0 && ultProg >= ultMax;
        int color = ready ? C_ULT_RDY : C_ULT_PRG;

        ctx.fill(x, y, x + w, y + h, C_BG);
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);

        if (ready) {
            ctx.drawString(tr, "X", x + (w - tr.width("X")) / 2, y + 2, C_ULT_RDY, true);
            ctx.drawString(tr, "READY", x + (w - tr.width("READY")) / 2, y + 10, C_ULT_RDY, false);
        } else {
            ctx.drawString(tr, "X", x + (w - tr.width("X")) / 2, y + 2, C_GREY, false);
            String pStr = ultProg + "/" + ultMax;
            ctx.drawString(tr, pStr, x + (w - tr.width(pStr)) / 2, y + 10, C_GREY, false);
        }
    }

    // ── Bottom-right HUD: Ammo & Credits ───────────────────────────────────────

    private static void renderBottomRight(GuiGraphics ctx, Font tr, int W, int H,
                                          int ammo, int maxAmmo, int reserve, int credits) {
        int w = 110;
        int h = 40;
        int x = W - w - 10;
        int y = H - h - 10;

        ctx.fill(x, y, x + w, y + h, C_BG);

        // Ammo string
        String ammoStr = (maxAmmo > 0) ? (ammo + " / " + reserve) : "—";
        int ammoColor = (ammo == 0 && maxAmmo > 0) ? C_ATK : C_WHITE;
        ctx.drawString(tr, ammoStr, x + 8, y + 6, ammoColor, false);

        // Credits string
        String credStr = "¢ " + credits;
        ctx.drawString(tr, credStr, x + 8, y + 22, C_CREDITS, false);
    }

    // ── Minimap (radar) ─────────────────────────────────────────────────────────

    private static void renderMinimap(GuiGraphics ctx, int W, int H, String radarData) {
        if (radarData == null || radarData.isEmpty()) return;

        int size = 64;
        int x    = W - size - 10;
        int y    = 10;

        ctx.fill(x, y, x + size, y + size, 0xBB000000);
        ctx.fill(x, y, x + size, y + 1, C_GREY);
        ctx.fill(x, y + size - 1, x + size, y + size, C_GREY);
        ctx.fill(x, y, x + 1, y + size, C_GREY);
        ctx.fill(x + size - 1, y, x + size, y + size, C_GREY);

        int cx = x + size / 2;
        int cy = y + size / 2;

        ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, C_WHITE);

        String[] dots = radarData.split(";");
        for (String dot : dots) {
            String[] p = dot.split(",");
            if (p.length < 3) continue;
            try {
                int dx       = Integer.parseInt(p[0]);
                int dy       = Integer.parseInt(p[1]);
                boolean enemy = p[2].equals("1");

                int px = clamp(cx + dx, x + 2, x + size - 3);
                int py = clamp(cy + dy, y + 2, y + size - 3);

                int color = enemy ? C_ENEMY : C_ALLY;
                ctx.fill(px - 1, py - 1, px + 2, py + 2, color);
            } catch (Exception ignored) {}
        }
    }

    // ── Spike indicator (center) ────────────────────────────────────────────────

    private static void renderSpike(GuiGraphics ctx, Font tr, int W, int H,
                                    int spikeState, int spikeTicks) {
        int cx = W / 2;
        int y  = 26;

        float secs = spikeTicks / 20.0f;
        String label = (spikeState == 1)
                ? String.format("SPIKE PLANTED  %.1fs", secs)
                : String.format("DEFUSING  %.1fs", secs);

        int tw = tr.width(label);
        int color = (spikeState == 1) ? C_SPIKE : C_SHIELD;

        ctx.fill(cx - tw / 2 - 6, y - 2, cx + tw / 2 + 6, y + 12, C_BG);
        ctx.drawString(tr, label, cx - tw / 2, y, color, true);
    }
}
