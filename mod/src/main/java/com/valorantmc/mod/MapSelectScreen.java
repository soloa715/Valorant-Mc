package com.valorantmc.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

@Environment(EnvType.CLIENT)
public class MapSelectScreen extends Screen {

    private static final int COLOR_BG     = 0xDD0A0A14;
    private static final int COLOR_HEADER = 0xFFFF4655;
    private static final int COLOR_TEXT   = 0xFFDDDDDD;
    private static final int COLOR_ACTIVE = 0xFFFFEB3B;

    private final List<String> maps;
    private String current;

    public MapSelectScreen(List<String> maps, String current) {
        super(Component.literal("Map Select"));
        this.maps    = maps;
        this.current = current;
    }

    @Override
    protected void init() {
        int btnW  = 160;
        int btnH  = 24;
        int gap   = 6;
        int startX = (width - btnW) / 2;
        int startY = 50;

        for (int i = 0; i < maps.size(); i++) {
            String map = maps.get(i);
            boolean active = map.equals(current);
            String label = (active ? "§e✔ " : "") + map;
            int by = startY + i * (btnH + gap);
            addRenderableWidget(Button.builder(Component.literal(label), b -> voteMap(map))
                    .bounds(startX, by, btnW, btnH)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 30, height - 28, 60, 20)
                .build());
    }

    private void voteMap(String map) {
        current = map;
        ClientPlayNetworking.send(new MapVotePayload(map));
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, COLOR_BG);
        ctx.drawCenteredString(font, Component.literal("§c§lMAP SELECT"), width / 2, 12, COLOR_HEADER);
        ctx.drawCenteredString(font, Component.literal("§7Vote for the next map"), width / 2, 24, COLOR_TEXT);
        if (!current.isEmpty()) {
            ctx.drawCenteredString(font, Component.literal("§aCurrent: §e" + current), width / 2, 36, COLOR_ACTIVE);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
