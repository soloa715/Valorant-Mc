package com.valorantmc.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Renders the Valorant-style HUD overlay.
 *
 * Layout (bottom of screen):
 *   [HEALTH BAR]  [SHIELD BAR]          [AMMO: current / max  +reserve]
 *   [Ability C] [Q] [E]  [ULT x/n]
 *
 * All coordinates are relative to the scaled window size passed in.
 */
@Environment(EnvType.CLIENT)
public final class ValorantHudRenderer {

    // Color palette (ARGB)
    private static final int COLOR_HP      = 0xFF4CAF50;  // green
    private static final int COLOR_SHIELD  = 0xFF29B6F6;  // light blue
    private static final int COLOR_BG      = 0x88000000;  // semi-transparent black
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_AMMO    = 0xFFFFFFFF;
    private static final int COLOR_ULT_RDY = 0xFFFFD700;  // gold
    private static final int COLOR_ULT_PRG = 0xFF888888;  // grey progress
    private static final int COLOR_ABLE    = 0xFF4FC3F7;  // cyan for ready ability
    private static final int COLOR_EMPTY   = 0xFF444444;  // dark for depleted

    private ValorantHudRenderer() {}

    public static void render(DrawContext ctx, int W, int H, float tickDelta) {
        int health  = ValorantHudState.health;
        int shield  = ValorantHudState.shield;
        int ammo    = ValorantHudState.ammo;
        int maxAmmo = ValorantHudState.maxAmmo;
        int reserve = ValorantHudState.reserve;
        int chargesC= ValorantHudState.chargesC;
        int chargesQ= ValorantHudState.chargesQ;
        int chargesE= ValorantHudState.chargesE;
        int ultProg = ValorantHudState.ultProgress;
        int ultMax  = ValorantHudState.ultMax;
        String agent= ValorantHudState.agentName;

        MinecraftClient mc = MinecraftClient.getInstance();
        var textRenderer = mc.textRenderer;

        // ── Bottom bar baseline ───────────────────────────────────────────────
        int barY     = H - 28;  // top of the bottom bar area
        int barH     = 6;
        int hpBarW   = 100;
        int shBarW   = 50;
        int barGap   = 4;
        int leftX    = 8;

        // Background strip
        ctx.fill(leftX - 2, barY - 2, leftX + hpBarW + barGap + shBarW + 4, barY + barH + 14, COLOR_BG);

        // Health bar
        int hpFilled = (int)(hpBarW * Math.min(health, 100) / 100.0);
        ctx.fill(leftX, barY, leftX + hpBarW, barY + barH, 0x44FFFFFF);
        if (hpFilled > 0) ctx.fill(leftX, barY, leftX + hpFilled, barY + barH, COLOR_HP);
        ctx.drawText(textRenderer, Text.literal(health + "hp"), leftX, barY + barH + 2, COLOR_TEXT, false);

        // Shield bar
        int shX = leftX + hpBarW + barGap;
        int shFilled = (int)(shBarW * Math.min(shield, 50) / 50.0);
        ctx.fill(shX, barY, shX + shBarW, barY + barH, 0x44FFFFFF);
        if (shFilled > 0) ctx.fill(shX, barY, shX + shFilled, barY + barH, COLOR_SHIELD);
        ctx.drawText(textRenderer, Text.literal(shield + "sh"), shX, barY + barH + 2, COLOR_SHIELD, false);

        // ── Ability slots (below health bar, bottom-left) ─────────────────────
        int abilY = barY - 18;
        int slotW = 16;
        int slotH = 14;
        int slotGap = 3;
        String[] keys    = { "C", "Q", "E" };
        int[]    charges = { chargesC, chargesQ, chargesE };

        for (int i = 0; i < 3; i++) {
            int sx = leftX + i * (slotW + slotGap);
            boolean ready = charges[i] > 0;
            ctx.fill(sx, abilY, sx + slotW, abilY + slotH, 0xAA000000);
            border(ctx, sx, abilY, slotW, slotH, ready ? COLOR_ABLE : COLOR_EMPTY);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(keys[i]), sx + slotW / 2, abilY + 3, ready ? COLOR_ABLE : COLOR_EMPTY);
            if (charges[i] > 1) {
                ctx.drawText(textRenderer, Text.literal("" + charges[i]),
                        sx + slotW - 5, abilY + slotH - 7, COLOR_TEXT, false);
            }
        }

        // Ultimate slot
        int ultSlotX = leftX + 3 * (slotW + slotGap) + 6;
        boolean ultReady = ultMax > 0 && ultProg >= ultMax;
        ctx.fill(ultSlotX, abilY, ultSlotX + slotW + 6, abilY + slotH, 0xAA000000);
        border(ctx, ultSlotX, abilY, slotW + 6, slotH, ultReady ? COLOR_ULT_RDY : COLOR_ULT_PRG);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("X"), ultSlotX + (slotW + 6) / 2, abilY + 3,
                ultReady ? COLOR_ULT_RDY : COLOR_ULT_PRG);
        String ultLabel = ultMax > 0 ? (ultReady ? "RDY" : ultProg + "/" + ultMax) : "";
        ctx.drawText(textRenderer, Text.literal(ultLabel),
                ultSlotX, abilY + slotH + 2, ultReady ? COLOR_ULT_RDY : COLOR_ULT_PRG, false);

        // ── Ammo counter (bottom-right) ───────────────────────────────────────
        String ammoMain  = String.valueOf(ammo);
        String ammoSep   = " / ";
        String ammoMax   = String.valueOf(maxAmmo);
        String ammoRes   = "  +" + reserve;

        int ammoW = textRenderer.getWidth(ammoMain + ammoSep + ammoMax + ammoRes) + 4;
        int ammoX = W - ammoW - 8;
        int ammoY = barY;

        ctx.fill(ammoX - 2, ammoY - 2, W - 6, ammoY + 10, COLOR_BG);
        int cx = ammoX;
        ctx.drawText(textRenderer, Text.literal(ammoMain), cx, ammoY, COLOR_AMMO, true);
        cx += textRenderer.getWidth(ammoMain);
        ctx.drawText(textRenderer, Text.literal(ammoSep), cx, ammoY, 0xFFAAAAAA, false);
        cx += textRenderer.getWidth(ammoSep);
        ctx.drawText(textRenderer, Text.literal(ammoMax), cx, ammoY, 0xFFAAAAAA, false);
        cx += textRenderer.getWidth(ammoMax);
        ctx.drawText(textRenderer, Text.literal(ammoRes), cx, ammoY, 0xFF777777, false);

        // ── Agent name (small, above ability slots) ───────────────────────────
        if (!agent.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal(agent), leftX, abilY - 10, 0xFFCCCCCC, false);
        }
    }

    // Draw a 1-pixel border rectangle
    private static void border(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x,         y,         x + w,     y + 1,     color); // top
        ctx.fill(x,         y + h - 1, x + w,     y + h,     color); // bottom
        ctx.fill(x,         y,         x + 1,     y + h,     color); // left
        ctx.fill(x + w - 1, y,         x + w,     y + h,     color); // right
    }
}
