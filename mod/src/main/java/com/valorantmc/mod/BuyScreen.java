package com.valorantmc.mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Authentic 6-column Valorant buy menu screen.
 * Fits properly on all GUI scales and resolutions.
 */
public class BuyScreen extends Screen {

    private static record ShopItem(String displayName, String buyId, int cost) {}
    private static record ShopColumn(String title, List<ShopItem> items) {}

    private static final List<ShopColumn> COLUMNS = List.of(
            new ShopColumn("SIDEARMS", List.of(
                    new ShopItem("Classic",  "Classic", 0),
                    new ShopItem("Shorty",   "Shorty", 150),
                    new ShopItem("Frenzy",   "Frenzy", 450),
                    new ShopItem("Ghost",    "Ghost", 500),
                    new ShopItem("Sheriff",  "Sheriff", 800)
            )),
            new ShopColumn("SMG / SHOT", List.of(
                    new ShopItem("Stinger",  "Stinger", 1100),
                    new ShopItem("Spectre",  "Spectre", 1600),
                    new ShopItem("Bucky",    "Bucky", 900),
                    new ShopItem("Judge",    "Judge", 1850)
            )),
            new ShopColumn("RIFLES", List.of(
                    new ShopItem("Bulldog",  "Bulldog", 2050),
                    new ShopItem("Guardian", "Guardian", 2250),
                    new ShopItem("Phantom",  "Phantom", 2900),
                    new ShopItem("Vandal",   "Vandal", 2900)
            )),
            new ShopColumn("SNIPERS", List.of(
                    new ShopItem("Marshal",  "Marshal", 950),
                    new ShopItem("Outlaw",   "Outlaw", 2400),
                    new ShopItem("Operator", "Operator", 4700)
            )),
            new ShopColumn("HEAVY", List.of(
                    new ShopItem("Ares",     "Ares", 1600),
                    new ShopItem("Odin",     "Odin", 3200)
            )),
            new ShopColumn("ARMOR / ABIL", List.of(
                    new ShopItem("Light (+25)",  "light_shield", 400),
                    new ShopItem("Heavy (+50)",  "heavy_shield", 1000),
                    new ShopItem("Ability C",    "ability_c", 200),
                    new ShopItem("Ability Q",    "ability_q", 200)
            ))
    );

    private static final int COLOR_BG      = 0xEE080B12;
    private static final int COLOR_HEADER  = 0xFFFF4655;
    private static final int COLOR_TITLE   = 0xFFB0B0B0;
    private static final int COLOR_CREDITS = 0xFF50E3C2;

    private final Screen parent;
    private final int initialCredits;

    private final java.util.List<java.util.Map.Entry<Button, Integer>> buyButtons = new java.util.ArrayList<>();

    public BuyScreen(Screen parent, int credits) {
        super(Component.literal("Buy Menu"));
        this.parent  = parent;
        this.initialCredits = credits;
    }

    @Override
    protected void init() {
        buyButtons.clear();
        int cols = COLUMNS.size();
        int btnW = Math.max(64, Math.min(88, (width - 40) / cols - 6));
        int gap = 6;
        int totalW = cols * btnW + (cols - 1) * gap;
        int startX = (width - totalW) / 2;
        int startY = 48;
        int btnH = 22;

        int curCred = ValorantHudState.credits > 0 ? ValorantHudState.credits : initialCredits;

        for (int c = 0; c < cols; c++) {
            ShopColumn col = COLUMNS.get(c);
            int colX = startX + c * (btnW + gap);

            for (int r = 0; r < col.items().size(); r++) {
                ShopItem item = col.items().get(r);
                int btnY = startY + r * (btnH + 4);

                String label = item.cost() > 0 ? (item.displayName() + " \u00a7e" + item.cost()) : (item.displayName() + " \u00a7aFREE");
                Button btn = Button.builder(Component.literal(label), b -> purchase(item.buyId()))
                        .bounds(colX, btnY, btnW, btnH)
                        .build();
                btn.active = curCred >= item.cost();
                buyButtons.add(java.util.Map.entry(btn, item.cost()));
                addRenderableWidget(btn);
            }
        }

        addRenderableWidget(Button.builder(Component.literal("\u00a7cRefund"), b -> refund())
                .bounds(width / 2 - 110, height - 26, 60, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Close [ESC/B]"), b -> close())
                .bounds(width / 2 - 45, height - 26, 90, 20)
                .build());
    }

    private void purchase(String buyId) {
        ValorantMCMod.sendToServer(new BuyActionPayload(buyId.toLowerCase()));
        // Keep buy screen open so players can buy abilities, shields, and guns without re-opening!
    }

    private void refund() {
        ValorantMCMod.sendToServer(new BuyActionPayload("sell:latest"));
    }

    private void close() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, COLOR_BG);

        ctx.drawCenteredString(font, Component.literal("\u00a7c\u00a7lVALORANT \u00a7fBUY MENU"), width / 2, 8, COLOR_HEADER);

        int curCred = ValorantHudState.credits > 0 ? ValorantHudState.credits : initialCredits;
        for (java.util.Map.Entry<Button, Integer> entry : buyButtons) {
            entry.getKey().active = curCred >= entry.getValue();
        }
        String credText = "\u00a76\u00a2 \u00a7a\u00a7l" + curCred + " \u00a77CREDITS";
        ctx.drawCenteredString(font, Component.literal(credText), width / 2, 22, COLOR_CREDITS);

        int cols = COLUMNS.size();
        int btnW = Math.max(64, Math.min(88, (width - 40) / cols - 6));
        int gap = 6;
        int totalW = cols * btnW + (cols - 1) * gap;
        int startX = (width - totalW) / 2;

        for (int c = 0; c < cols; c++) {
            ShopColumn col = COLUMNS.get(c);
            int colX = startX + c * (btnW + gap);
            ctx.drawString(font, Component.literal("\u00a77" + col.title()), colX + 2, 36, COLOR_TITLE, false);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) { // Right Click = Request weapon
            int cols = COLUMNS.size();
            int btnW = Math.max(64, Math.min(88, (width - 40) / cols - 6));
            int gap = 6;
            int totalW = cols * btnW + (cols - 1) * gap;
            int startX = (width - totalW) / 2;
            int startY = 48;
            int btnH = 22;

            for (int c = 0; c < cols; c++) {
                ShopColumn col = COLUMNS.get(c);
                int colX = startX + c * (btnW + gap);
                for (int r = 0; r < col.items().size(); r++) {
                    ShopItem item = col.items().get(r);
                    int btnY = startY + r * (btnH + 4);
                    if (mouseX >= colX && mouseX <= colX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                        ValorantMCMod.sendToServer(new BuyActionPayload("request:" + item.buyId().toLowerCase()));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_B) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
