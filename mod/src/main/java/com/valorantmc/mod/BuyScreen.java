package com.valorantmc.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * In-game weapon buy menu. Opens during buy phase (roundPhase == 1).
 * Sends BuyActionPayload to the server when a weapon is selected.
 */
@Environment(EnvType.CLIENT)
public class BuyScreen extends Screen {

    // "name", cost
    private static final String[][] WEAPONS = {
            {"Classic",  "0"},
            {"Shorty",   "150"},
            {"Frenzy",   "450"},
            {"Ghost",    "500"},
            {"Sheriff",  "800"},
            {"Stinger",  "1100"},
            {"Spectre",  "1600"},
            {"Bucky",    "900"},
            {"Judge",    "1850"},
            {"Bulldog",  "2050"},
            {"Guardian", "2250"},
            {"Phantom",  "2900"},
            {"Vandal",   "2900"},
            {"Marshal",  "950"},
            {"Outlaw",   "2400"},
            {"Operator", "4700"},
            {"Ares",     "1600"},
            {"Odin",     "3200"},
    };

    // Category labels and the weapon index ranges they span in WEAPONS[]
    private static final String[] CAT_LABELS  = {"Sidearms","SMGs","Shotguns","Rifles","Snipers","Heavy"};
    private static final int[]    CAT_START   = {0, 5, 7, 9, 13, 16};
    private static final int[]    CAT_END     = {5, 7, 9, 13, 16, 18}; // exclusive

    private static final int COLOR_BG      = 0xDD0A0A14;
    private static final int COLOR_PANEL   = 0xCC161620;
    private static final int COLOR_HEADER  = 0xFFFF4655;   // Valorant red
    private static final int COLOR_TEXT    = 0xFFDDDDDD;
    private static final int COLOR_COST    = 0xFFFFEB3B;
    private static final int COLOR_CREDITS = 0xFF66BB6A;
    private static final int COLOR_AFFORD  = 0xFF4CAF50;
    private static final int COLOR_CANT    = 0xFF777777;

    private final Screen parent;
    private int credits;

    public BuyScreen(Screen parent, int credits) {
        super(Text.literal("Buy Menu"));
        this.parent  = parent;
        this.credits = credits;
    }

    @Override
    protected void init() {
        int cols   = 3;
        int btnW   = 120;
        int btnH   = 20;
        int startX = (width - cols * (btnW + 4)) / 2;
        int startY = 60;

        int activeCat = -1;
        int rowOffset = 0;

        for (int cat = 0; cat < CAT_LABELS.length; cat++) {
            // Category header — not a button, just rendered via drawBackground
            int catY = startY + rowOffset * (btnH + 4) + cat * 14;
            rowOffset++;

            for (int wi = CAT_START[cat]; wi < CAT_END[cat]; wi++) {
                String name = WEAPONS[wi][0];
                int cost    = Integer.parseInt(WEAPONS[wi][1]);
                boolean can = credits >= cost;

                int col = (wi - CAT_START[cat]) % cols;
                int row = (wi - CAT_START[cat]) / cols;
                int bx  = startX + col * (btnW + 4);
                int by  = catY + row * (btnH + 4);

                String label = name + " ¢" + cost;
                ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> purchase(name))
                        .dimensions(bx, by, btnW, btnH)
                        .build();
                btn.active = can;
                addDrawableChild(btn);
            }

            int rows = (int) Math.ceil((double)(CAT_END[cat] - CAT_START[cat]) / cols);
            rowOffset += rows;
        }

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("Close [ESC]"), b -> close())
                .dimensions(width / 2 - 40, height - 28, 80, 20)
                .build());
    }

    private void purchase(String weaponName) {
        ClientPlayNetworking.send(new BuyActionPayload(weaponName.toLowerCase()));
        // Refresh credits from state after server confirms
        close();
    }

    private void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dark overlay
        ctx.fill(0, 0, width, height, COLOR_BG);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("BUY MENU"), width / 2, 12, COLOR_HEADER);

        // Credits
        String credText = "¢ " + ValorantHudState.credits;
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(credText), width / 2, 24, COLOR_CREDITS);

        // Category headers (drawn before buttons so buttons render on top)
        int cols   = 3;
        int btnW   = 120;
        int btnH   = 20;
        int startX = (width - cols * (btnW + 4)) / 2;
        int startY = 60;
        int rowOffset = 0;

        for (int cat = 0; cat < CAT_LABELS.length; cat++) {
            int catY = startY + rowOffset * (btnH + 4) + cat * 14;
            ctx.drawText(textRenderer, Text.literal("— " + CAT_LABELS[cat] + " —"),
                    startX, catY, COLOR_TEXT, false);
            rowOffset++;
            int rows = (int) Math.ceil((double)(CAT_END[cat] - CAT_START[cat]) / cols);
            rowOffset += rows;
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC closes the screen
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
