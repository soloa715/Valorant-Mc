package com.valorantmc.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ValorantMCClientMod implements ClientModInitializer {

    private static final String CATEGORY = "key.categories.valorantmc";

    static KeyMapping KEY_SHOP;
    static KeyMapping KEY_RELOAD;
    static KeyMapping KEY_AGENT;
    static KeyMapping KEY_DROPSPIKE;
    static KeyMapping KEY_WALK;
    static KeyMapping KEY_ABILITY_C;
    static KeyMapping KEY_ABILITY_Q;
    static KeyMapping KEY_ABILITY_E;
    static KeyMapping KEY_ULT;
    static KeyMapping KEY_ADMIN;
    static KeyMapping KEY_MAP;

    @Override
    public void onInitializeClient() {
        registerKeybindings();
        registerNetworking();
        registerHud();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.screen != null) return;

            if (KEY_SHOP.consumeClick()) {
                if (ValorantHudState.roundPhase == 1) {
                    client.execute(() -> client.setScreen(
                            new BuyScreen(null, ValorantHudState.credits)));
                } else {
                    client.player.connection.sendCommand("vshop");
                }
            }

            checkKey(client, KEY_RELOAD,    "vreload");
            checkKey(client, KEY_AGENT,     "vagent");
            checkKey(client, KEY_DROPSPIKE, "vdropspike");
            checkKey(client, KEY_WALK,      "vwalk");
            checkKey(client, KEY_ABILITY_C, "vuse C");
            checkKey(client, KEY_ABILITY_Q, "vuse Q");
            checkKey(client, KEY_ABILITY_E, "vuse E");
            checkKey(client, KEY_ULT,       "vuse X");
            checkKey(client, KEY_ADMIN,     "vadmin");

            if (KEY_MAP.consumeClick()) {
                if (ValorantHudState.mapList != null && !ValorantHudState.mapList.isEmpty()) {
                    client.execute(() -> client.setScreen(
                            new MapSelectScreen(ValorantHudState.mapList, ValorantHudState.currentMap)));
                } else {
                    client.player.connection.sendCommand("vmap list");
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ValorantHudState.clear());
    }

    // ── Keybindings ───────────────────────────────────────────────────────────

    private void registerKeybindings() {
        KEY_SHOP      = reg("key.valorantmc.shop",      GLFW.GLFW_KEY_B);
        KEY_RELOAD    = reg("key.valorantmc.reload",    GLFW.GLFW_KEY_R);
        KEY_AGENT     = reg("key.valorantmc.agent",     GLFW.GLFW_KEY_N);
        KEY_DROPSPIKE = reg("key.valorantmc.dropspike", GLFW.GLFW_KEY_G);
        KEY_WALK      = reg("key.valorantmc.walk",      GLFW.GLFW_KEY_Y);
        KEY_ABILITY_C = reg("key.valorantmc.ability_c", GLFW.GLFW_KEY_F);
        KEY_ABILITY_Q = reg("key.valorantmc.ability_q", GLFW.GLFW_KEY_C);
        KEY_ABILITY_E = reg("key.valorantmc.ability_e", GLFW.GLFW_KEY_V);
        KEY_ULT       = reg("key.valorantmc.ult",       GLFW.GLFW_KEY_X);
        KEY_ADMIN     = reg("key.valorantmc.admin",     GLFW.GLFW_KEY_KP_0);
        KEY_MAP       = reg("key.valorantmc.map",       GLFW.GLFW_KEY_M);
    }

    private static KeyMapping reg(String id, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyMapping(id, InputConstants.Type.KEYSYM, glfwKey, CATEGORY));
    }

    private static void checkKey(Minecraft client, KeyMapping kb, String command) {
        while (kb.consumeClick()) {
            assert client.player != null;
            client.player.connection.sendCommand(command);
        }
    }

    // ── Networking ────────────────────────────────────────────────────────────

    private void registerNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                ValorantHudState.active          = payload.active();
                ValorantHudState.health          = payload.health();
                ValorantHudState.shield          = payload.shield();
                ValorantHudState.ammo            = payload.ammo();
                ValorantHudState.maxAmmo         = payload.maxAmmo();
                ValorantHudState.reserve         = payload.reserve();
                ValorantHudState.chargesC        = payload.chargesC();
                ValorantHudState.chargesQ        = payload.chargesQ();
                ValorantHudState.chargesE        = payload.chargesE();
                ValorantHudState.cooldownC       = payload.cooldownC();
                ValorantHudState.cooldownQ       = payload.cooldownQ();
                ValorantHudState.cooldownE       = payload.cooldownE();
                ValorantHudState.ultProgress     = payload.ultProgress();
                ValorantHudState.ultMax          = payload.ultMax();
                ValorantHudState.agentName       = payload.agentName();
                ValorantHudState.credits         = payload.credits();
                ValorantHudState.atkScore        = payload.atkScore();
                ValorantHudState.defScore        = payload.defScore();
                ValorantHudState.spikeState      = payload.spikeState();
                ValorantHudState.spikeTimerTicks = payload.spikeTimerTicks();
                ValorantHudState.roundPhase      = payload.roundPhase();
                ValorantHudState.teamRoster      = payload.teamRoster();
                String kf = payload.killFeed();
                if (!kf.isEmpty()) {
                    ValorantHudState.killFeed        = kf;
                    ValorantHudState.killFeedShownAt = System.currentTimeMillis();
                }
            })
        );

        ClientPlayNetworking.registerGlobalReceiver(BuyMenuPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                ValorantHudState.credits    = payload.credits();
                ValorantHudState.inBuyPhase = payload.inBuyPhase();
                if (payload.inBuyPhase() && context.client().screen == null) {
                    context.client().setScreen(
                            new BuyScreen(null, payload.credits()));
                }
            })
        );

        ClientPlayNetworking.registerGlobalReceiver(RadarPayload.TYPE, (payload, context) ->
            context.client().execute(() ->
                ValorantHudState.radarData = payload.data()
            )
        );

        ClientPlayNetworking.registerGlobalReceiver(AgentSelectPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                ValorantHudState.agentSelectList = payload.availableAgents();
                ValorantHudState.mySelectedAgent  = payload.myAgent();
                if (context.client().screen == null || !(context.client().screen instanceof AgentSelectScreen)) {
                    context.client().setScreen(new AgentSelectScreen(
                            payload.availableAgents(), payload.myAgent()));
                }
            })
        );

        ClientPlayNetworking.registerGlobalReceiver(MapSelectPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                ValorantHudState.mapList    = payload.maps();
                ValorantHudState.currentMap = payload.currentMap();
            })
        );

        ClientPlayNetworking.registerGlobalReceiver(AdminSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (!(context.client().screen instanceof AdminScreen)) {
                    context.client().setScreen(new AdminScreen(payload));
                }
            })
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            ClientPlayNetworking.send(new HelloPayload("1.0.0"))
        );
    }

    // ── HUD + Crosshair ───────────────────────────────────────────────────────

    private void registerHud() {
        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.options.hideGui) return;
            if (client.screen != null) return;

            int W = client.getWindow().getGuiScaledWidth();
            int H = client.getWindow().getGuiScaledHeight();

            if (client.player != null) {
                CrosshairRenderer.render(guiGraphics, W, H);
            }

            if (ValorantHudState.active) {
                ValorantHudRenderer.render(guiGraphics, W, H);
            }
        });
    }
}
