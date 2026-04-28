package com.valorantmc.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
@Environment(EnvType.CLIENT)
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

    private static final long KILLFEED_TTL_MS = 4000L;

    private static void renderKillFeed(GuiGraphics ctx, Font tr, int W,
                                       String entry, long shownAt) {
        if (entry == null || entry.isEmpty()) return;
        if (System.currentTimeMillis() - shownAt > KILLFEED_TTL_MS) return;

        int tw = tr.width(entry) + 4;
        int x  = W - tw - 6;
        int y  = 6;
        ctx.fill(x - 2, y - 1, x + tw, y + 9, C_BG);
        ctx.drawString(tr, entry, x, y, C_WHITE, true);
    }

    // ── Bottom-left: agent / HP / shield / abilities ────────────────────────────

    private static void renderBottomLeft(GuiGraphics ctx, Font tr, int H,
            int health, int shield,
            int chargesC, int chargesQ, int chargesE,
            int cooldownC, int cooldownQ, int cooldownE,
            int ultProg, int ultMax, String agent) {

        int leftX = 8;
        int barH  = 6;
        int hpW   = 100;
        int shW   = 50;
        int gap   = 4;
        int barY  = H - 28;

        ctx.fill(leftX - 3, barY - 30, leftX + hpW + gap + shW + 5, barY + barH + 10, C_BG);

        if (!agent.isEmpty()) {
            ctx.drawString(tr, agent, leftX, barY - 26, C_GREY, false);
        }

        int hpFill = (int)(hpW * Math.min(Math.max(health, 0), 100) / 100.0);
        int hpColor = health > 50 ? C_HP : (health > 25 ? 0xFFFFEB3B : C_ATK);
        ctx.fill(leftX, barY, leftX + hpW, barY + barH, 0x44FFFFFF);
        if (hpFill > 0) ctx.fill(leftX, barY, leftX + hpFill, barY + barH, hpColor);
        ctx.drawString(tr, health + " HP", leftX, barY + barH + 2, C_WHITE, false);

        int shX    = leftX + hpW + gap;
        int shFill = (int)(shW * Math.min(shield, 50) / 50.0);
        ctx.fill(shX, barY, shX + shW, barY + barH, 0x44FFFFFF);
        if (shFill > 0) ctx.fill(shX, barY, shX + shFill, barY + barH, C_SHIELD);
        ctx.drawString(tr, shield + " SH", shX, barY + barH + 2, C_SHIELD, false);

        int abilY  = barY - 18;
        int slotW  = 18;
        int slotH  = 14;
        int slotGap= 3;
        String[] keys     = {"C", "Q", "E"};
        int[]    charges  = {chargesC, chargesQ, chargesE};
        int[]    cooldowns= {cooldownC, cooldownQ, cooldownE};

        for (int i = 0; i < 3; i++) {
            int sx = leftX + i * (slotW + slotGap);
            boolean ready = charges[i] > 0 && cooldowns[i] == 0;
            boolean onCd  = cooldowns[i] > 0;
            int borderCol = ready ? C_ABLE : C_DARK;

            ctx.fill(sx, abilY, sx + slotW, abilY + slotH, 0xAA000000);
            border(ctx, sx, abilY, slotW, slotH, borderCol);
            ctx.drawCenteredString(tr, keys[i], sx + slotW / 2, abilY + 3,
                    ready ? C_ABLE : C_DARK);
            if (onCd) {
                ctx.fill(sx + 1, abilY + 1, sx + slotW - 1, abilY + slotH - 1, C_CD_OVER);
                String cdText = String.format("%.1f", cooldowns[i] / 10.0f);
                ctx.drawCenteredString(tr, cdText, sx + slotW / 2, abilY + 4, C_WHITE);
            } else if (charges[i] > 1) {
                ctx.drawString(tr, String.valueOf(charges[i]),
                        sx + slotW - 6, abilY + slotH - 7, C_WHITE, false);
            }
        }

        int ultX    = leftX + 3 * (slotW + slotGap) + 6;
        int ultSlotW= slotW + 6;
        boolean ultReady = ultMax > 0 && ultProg >= ultMax;
        int ultBorder = ultReady ? C_ULT_RDY : C_ULT_PRG;
        ctx.fill(ultX, abilY, ultX + ultSlotW, abilY + slotH, 0xAA000000);
        border(ctx, ultX, abilY, ultSlotW, slotH, ultBorder);
        ctx.drawCenteredString(tr, "X", ultX + ultSlotW / 2, abilY + 3, ultBorder);
        if (ultMax > 0) {
            String label = ultReady ? "RDY" : ultProg + "/" + ultMax;
            ctx.drawCenteredString(tr, label, ultX + ultSlotW / 2, abilY + slotH + 2, ultBorder);
        }
    }

    // ── Bottom-right: ammo + credits ────────────────────────────────────────────

    private static void renderBottomRight(GuiGraphics ctx, Font tr,
                                          int W, int H, int ammo, int maxAmmo,
                                          int reserve, int credits) {
        int rightEdge = W - 8;
        int ammoY     = H - 28;

        String cur  = String.valueOf(ammo);
        String sep  = " / ";
        String max  = String.valueOf(maxAmmo);
        String res  = "  +" + reserve;
        int totalW  = tr.width(cur + sep + max + res) + 4;
        int ammoX   = rightEdge - totalW;

        ctx.fill(ammoX - 3, ammoY - 2, rightEdge + 1, ammoY + 20, C_BG);
        int cx = ammoX;
        ctx.drawString(tr, cur, cx, ammoY, C_WHITE, true);
        cx += tr.width(cur);
        ctx.drawString(tr, sep, cx, ammoY, C_GREY, false);
        cx += tr.width(sep);
        ctx.drawString(tr, max, cx, ammoY, C_GREY, false);
        cx += tr.width(max);
        ctx.drawString(tr, res, cx, ammoY, C_DARK, false);

        String credStr = "¢ " + credits;
        int credW = tr.width(credStr);
        ctx.drawString(tr, credStr, rightEdge - credW, ammoY + 12, C_CREDITS, false);
    }

    // ── Minimap / radar (above ammo, bottom-right) ──────────────────────────────

    private static final int MAP_SIZE  = 80;
    private static final float SCALE   = 2.0f;

    private static void renderMinimap(GuiGraphics ctx, int W, int H, String radarData) {
        if (radarData == null || radarData.isEmpty()) return;

        int mapX = W - MAP_SIZE - 8;
        int mapY = H - 28 - MAP_SIZE - 4;

        ctx.fill(mapX - 1, mapY - 1, mapX + MAP_SIZE + 1, mapY + MAP_SIZE + 1, 0xFF222222);
        ctx.fill(mapX, mapY, mapX + MAP_SIZE, mapY + MAP_SIZE, 0x88000000);

        String[] parts = radarData.split("\\|", 2);
        if (parts.length < 1) return;

        String[] self = parts[0].split(":");
        if (self.length < 3) return;
        float selfX, selfZ, selfYaw;
        try {
            selfX   = Float.parseFloat(self[0]);
            selfZ   = Float.parseFloat(self[1]);
            selfYaw = Float.parseFloat(self[2]);
        } catch (NumberFormatException e) {
            return;
        }

        int midX = mapX + MAP_SIZE / 2;
        int midY = mapY + MAP_SIZE / 2;
        ctx.fill(midX - 2, midY - 2, midX + 2, midY + 2, C_WHITE);

        if (parts.length < 2 || parts[1].isEmpty()) return;

        for (String entry : parts[1].split(",")) {
            String[] ef = entry.split(":");
            if (ef.length < 4) continue;
            try {
                float ex = Float.parseFloat(ef[1]);
                float ez = Float.parseFloat(ef[2]);
                boolean ally = "A".equals(ef[3]);

                float dx = ex - selfX;
                float dz = ez - selfZ;
                double rad = Math.toRadians(-selfYaw);
                float rotX = (float)(dx * Math.cos(rad) - dz * Math.sin(rad));
                float rotZ = (float)(dx * Math.sin(rad) + dz * Math.cos(rad));

                int px = midX + Math.round(rotX / SCALE);
                int py = midY + Math.round(rotZ / SCALE);

                px = Math.max(mapX + 2, Math.min(mapX + MAP_SIZE - 3, px));
                py = Math.max(mapY + 2, Math.min(mapY + MAP_SIZE - 3, py));

                int dotColor = ally ? C_ALLY : C_ENEMY;
                ctx.fill(px - 2, py - 2, px + 2, py + 2, dotColor);
            } catch (NumberFormatException ignored) {}
        }
    }

    // ── Spike indicator (screen center) ────────────────────────────────────────

    private static void renderSpike(GuiGraphics ctx, Font tr,
                                    int W, int H, int spikeState, int spikeTicks) {
        int totalTicks = spikeState == 2 ? 120 : 900;
        float progress = Math.max(0f, Math.min(1f, (float) spikeTicks / totalTicks));

        String label = spikeState == 2 ? "DEFUSING" : "SPIKE PLANTED";
        int seconds = spikeTicks / 20;
        String timer = seconds + "s";

        int bw = 140;
        int bh = 10;
        int bx = (W - bw) / 2;
        int by = H / 2 + 20;

        ctx.fill(bx - 4, by - 20, bx + bw + 4, by + bh + 4, C_BG);
        ctx.drawCenteredString(tr, label, W / 2, by - 16, C_SPIKE);
        ctx.drawCenteredString(tr, timer, W / 2, by - 6, C_WHITE);

        ctx.fill(bx, by, bx + bw, by + bh, 0x44FFFFFF);
        int filled = (int)(bw * progress);
        if (filled > 0) ctx.fill(bx, by, bx + filled, by + bh,
                spikeState == 2 ? C_SHIELD : C_SPIKE);
    }

    // ── Utilities ────────────────────────────────────────────────────────────────

    private static void border(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x,         y,         x + w,     y + 1,     color);
        ctx.fill(x,         y + h - 1, x + w,     y + h,     color);
        ctx.fill(x,         y,         x + 1,     y + h,     color);
        ctx.fill(x + w - 1, y,         x + w,     y + h,     color);
    }
}
