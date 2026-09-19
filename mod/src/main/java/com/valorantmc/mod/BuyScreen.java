package com.valorantmc.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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

    private static final String[] CAT_LABELS  = {"Sidearms","SMGs","Shotguns","Rifles","Snipers","Heavy"};
    private static final int[]    CAT_START   = {0, 5, 7, 9, 13, 16};
    private static final int[]    CAT_END     = {5, 7, 9, 13, 16, 18};

    private static final int COLOR_BG      = 0xDD0A0A14;
    private static final int COLOR_PANEL   = 0xCC161620;
    private static final int COLOR_HEADER  = 0xFFFF4655;
    private static final int COLOR_TEXT    = 0xFFDDDDDD;
    private static final int COLOR_COST    = 0xFFFFEB3B;
    private static final int COLOR_CREDITS = 0xFF66BB6A;

    private final Screen parent;
    private int credits;

    public BuyScreen(Screen parent, int credits) {
        super(Component.literal("Buy Menu"));
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

        int rowOffset = 0;

        for (int cat = 0; cat < CAT_LABELS.length; cat++) {
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
                Button btn = Button.builder(Component.literal(label), b -> purchase(name))
                        .bounds(bx, by, btnW, btnH)
                        .build();
                btn.active = can;
                addRenderableWidget(btn);
            }

            int rows = (int) Math.ceil((double)(CAT_END[cat] - CAT_START[cat]) / cols);
            rowOffset += rows;
        }

        // ── Shields ───────────────────────────────────────────────────────────
        int shieldY = startY + rowOffset * (btnH + 4) + CAT_LABELS.length * 14;
        int sx = startX;
        Button lightShield = Button.builder(Component.literal("Light Shield ¢400"), b -> purchase("light_shield"))
                .bounds(sx, shieldY, btnW, btnH).build();
        lightShield.active = credits >= 400;
        addRenderableWidget(lightShield);

        Button heavyShield = Button.builder(Component.literal("Heavy Shield ¢1000"), b -> purchase("heavy_shield"))
                .bounds(sx + btnW + 4, shieldY, btnW, btnH).build();
        heavyShield.active = credits >= 1000;
        addRenderableWidget(heavyShield);

        // ── Abilities ─────────────────────────────────────────────────────────
        int abilY = shieldY + btnH + 8;
        Button abilC = Button.builder(Component.literal("Ability C ¢200"), b -> purchase("ability_c"))
                .bounds(sx, abilY, btnW, btnH).build();
        abilC.active = credits >= 200;
        addRenderableWidget(abilC);

        Button abilQ = Button.builder(Component.literal("Ability Q ¢200"), b -> purchase("ability_q"))
                .bounds(sx + btnW + 4, abilY, btnW, btnH).build();
        abilQ.active = credits >= 200;
        addRenderableWidget(abilQ);

        addRenderableWidget(Button.builder(Component.literal("Close [ESC]"), b -> close())
                .bounds(width / 2 - 40, height - 28, 80, 20)
                .build());
    }

    private void purchase(String weaponName) {
        ClientPlayNetworking.send(new BuyActionPayload(weaponName.toLowerCase()));
        close();
    }

    private void close() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, COLOR_BG);

        ctx.drawCenteredString(font, Component.literal("BUY MENU"), width / 2, 12, COLOR_HEADER);

        String credText = "¢ " + ValorantHudState.credits;
        ctx.drawCenteredString(font, Component.literal(credText), width / 2, 24, COLOR_CREDITS);

        int cols   = 3;
        int btnW   = 120;
        int btnH   = 20;
        int startX = (width - cols * (btnW + 4)) / 2;
        int startY = 60;
        int rowOffset = 0;

        for (int cat = 0; cat < CAT_LABELS.length; cat++) {
            int catY = startY + rowOffset * (btnH + 4) + cat * 14;
            ctx.drawString(font, Component.literal("— " + CAT_LABELS[cat] + " —"),
                    startX, catY, COLOR_TEXT, false);
            rowOffset++;
            int rows = (int) Math.ceil((double)(CAT_END[cat] - CAT_START[cat]) / cols);
            rowOffset += rows;
        }

        // Shields and ability labels
        int finalY = startY + rowOffset * (btnH + 4) + CAT_LABELS.length * 14;
        ctx.drawString(font, Component.literal("— Shields —"), startX, finalY - 10, COLOR_TEXT, false);
        ctx.drawString(font, Component.literal("— Abilities —"), startX, finalY + btnH + 2, COLOR_TEXT, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
