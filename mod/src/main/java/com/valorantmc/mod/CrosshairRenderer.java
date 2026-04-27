package com.valorantmc.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;

/** Renders a Valorant-style crosshair: 4 lines + center dot, with a configurable gap. */
@Environment(EnvType.CLIENT)
public final class CrosshairRenderer {

    private static final int GAP    = 4;   // pixels from center before line starts
    private static final int LENGTH = 6;   // line length in pixels
    private static final int THICK  = 2;   // line thickness in pixels

    private static final int COLOR_OUTLINE = 0xAA000000;
    private static final int COLOR_MAIN    = 0xFFFFFFFF;

    private CrosshairRenderer() {}

    public static void render(DrawContext ctx, int W, int H) {
        int cx = W / 2;
        int cy = H / 2;

        // Draw outline pass first, then white on top
        drawLines(ctx, cx, cy, COLOR_OUTLINE, 1);
        drawLines(ctx, cx, cy, COLOR_MAIN, 0);
    }

    private static void drawLines(DrawContext ctx, int cx, int cy, int color, int expand) {
        int t = THICK + expand * 2;
        int half = t / 2;

        // Left
        ctx.fill(cx - GAP - LENGTH - expand, cy - half,
                 cx - GAP + expand,          cy - half + t, color);
        // Right
        ctx.fill(cx + GAP - expand,           cy - half,
                 cx + GAP + LENGTH + expand,  cy - half + t, color);
        // Up
        ctx.fill(cx - half, cy - GAP - LENGTH - expand,
                 cx - half + t, cy - GAP + expand, color);
        // Down
        ctx.fill(cx - half, cy + GAP - expand,
                 cx - half + t, cy + GAP + LENGTH + expand, color);

        // Center dot
        ctx.fill(cx - 1 - expand, cy - 1 - expand,
                 cx + 1 + expand, cy + 1 + expand, color);
    }
}
