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
public class AgentSelectScreen extends Screen {

    // Agent role colours (hex ARGB)
    private static final int COLOR_BG       = 0xDD0A0A14;
    private static final int COLOR_HEADER   = 0xFFFF4655;
    private static final int COLOR_DUELIST  = 0xFFFF4655;
    private static final int COLOR_INITIATOR= 0xFF4CAF50;
    private static final int COLOR_CONTROLLER=0xFF9C27B0;
    private static final int COLOR_SENTINEL = 0xFF2196F3;
    private static final int COLOR_TEXT     = 0xFFDDDDDD;
    private static final int COLOR_SELECTED = 0xFFFFEB3B;
    private static final int COLOR_LOCKED   = 0xFF555555;

    // Role per agent
    private static final java.util.Map<String,String> ROLES = new java.util.HashMap<>();
    static {
        for (String a : new String[]{"Jett","Reyna","Raze","Phoenix","Neon","Yoru","Iso","Clove"})
            ROLES.put(a, "DUELIST");
        for (String a : new String[]{"Sova","Skye","Breach","KAY/O","Fade","Gekko","Tejo"})
            ROLES.put(a, "INITIATOR");
        for (String a : new String[]{"Omen","Viper","Brimstone","Astra","Harbor","Deadlock"})
            ROLES.put(a, "CONTROLLER");
        for (String a : new String[]{"Sage","Cypher","Killjoy","Chamber","Vyse"})
            ROLES.put(a, "SENTINEL");
    }

    private final List<String> agents;
    private String myAgent;
    private int timeLeft = 20;

    public AgentSelectScreen(List<String> agents, String myAgent) {
        super(Component.literal("Agent Select"));
        this.agents  = agents;
        this.myAgent = myAgent;
    }

    @Override
    protected void init() {
        buildButtons();
    }

    private void buildButtons() {
        clearWidgets();
        int cols  = 5;
        int btnW  = 100;
        int btnH  = 40;
        int gap   = 6;
        int totalW = cols * btnW + (cols - 1) * gap;
        int startX = (width - totalW) / 2;
        int startY = 55;

        for (int i = 0; i < agents.size(); i++) {
            String agent = agents.get(i);
            boolean selected = agent.equals(myAgent);
            int col = i % cols;
            int row = i / cols;
            int bx  = startX + col * (btnW + gap);
            int by  = startY + row * (btnH + gap);

            String label = (selected ? "§e✔ " : "") + agent;
            Button btn = Button.builder(Component.literal(label), b -> selectAgent(agent))
                    .bounds(bx, by, btnW, btnH)
                    .build();
            addRenderableWidget(btn);
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 30, height - 28, 60, 20)
                .build());
    }

    private void selectAgent(String agent) {
        myAgent = agent;
        ClientPlayNetworking.send(new AgentChoicePayload(agent));
        buildButtons();
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, COLOR_BG);
        ctx.drawCenteredString(font, Component.literal("§c§lAGENT SELECT"), width / 2, 12, COLOR_HEADER);
        ctx.drawCenteredString(font, Component.literal("§7Choose your agent — §e" + timeLeft + "s"), width / 2, 24, COLOR_TEXT);

        if (!myAgent.isEmpty()) {
            ctx.drawCenteredString(font, Component.literal("§aSelected: §b" + myAgent), width / 2, 36, COLOR_SELECTED);
        }

        // Draw role labels above each agent button
        int cols  = 5;
        int btnW  = 100;
        int btnH  = 40;
        int gap   = 6;
        int totalW = cols * btnW + (cols - 1) * gap;
        int startX = (width - totalW) / 2;
        int startY = 55;

        for (int i = 0; i < agents.size(); i++) {
            String agent = agents.get(i);
            String role  = ROLES.getOrDefault(agent, "");
            int col = i % cols;
            int row = i / cols;
            int bx  = startX + col * (btnW + gap);
            int by  = startY + row * (btnH + gap);

            int roleColor = switch (role) {
                case "DUELIST"    -> COLOR_DUELIST;
                case "INITIATOR"  -> COLOR_INITIATOR;
                case "CONTROLLER" -> COLOR_CONTROLLER;
                case "SENTINEL"   -> COLOR_SENTINEL;
                default           -> COLOR_TEXT;
            };
            ctx.drawCenteredString(font, Component.literal("§7" + role), bx + btnW / 2, by + btnH - 10, roleColor);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    public void updateTimer(int seconds) {
        this.timeLeft = seconds;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
