package com.valorantmc.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side entrypoint.
 *
 * Responsibilities:
 *  1. Register Valorant keybindings and relay them as server commands.
 *  2. Receive HUD state packets and display a Valorant-style overlay.
 *  3. Announce mod presence to the server on join.
 */
@Environment(EnvType.CLIENT)
public class ValorantMCClientMod implements ClientModInitializer {

    private static final String CATEGORY = "key.categories.valorantmc";

    static KeyBinding KEY_SHOP;
    static KeyBinding KEY_RELOAD;
    static KeyBinding KEY_AGENT;
    static KeyBinding KEY_DROPSPIKE;
    static KeyBinding KEY_WALK;
    static KeyBinding KEY_ABILITY_C;
    static KeyBinding KEY_ABILITY_Q;
    static KeyBinding KEY_ABILITY_E;
    static KeyBinding KEY_ULT;
    static KeyBinding KEY_ADMIN;

    @Override
    public void onInitializeClient() {
        registerKeybindings();
        registerNetworking();
        registerHud();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.currentScreen != null) return;
            checkKey(client, KEY_SHOP,      "vshop");
            checkKey(client, KEY_RELOAD,    "vreload");
            checkKey(client, KEY_AGENT,     "vagent");
            checkKey(client, KEY_DROPSPIKE, "vdropspike");
            checkKey(client, KEY_WALK,      "vwalk");
            checkKey(client, KEY_ABILITY_C, "vuse C");
            checkKey(client, KEY_ABILITY_Q, "vuse Q");
            checkKey(client, KEY_ABILITY_E, "vuse E");
            checkKey(client, KEY_ULT,       "vuse X");
            checkKey(client, KEY_ADMIN,     "vadmin");
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
    }

    private static KeyBinding reg(String id, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(id, InputUtil.Type.KEYSYM, glfwKey, CATEGORY));
    }

    private static void checkKey(MinecraftClient client, KeyBinding kb, String command) {
        while (kb.wasPressed()) {
            assert client.player != null;
            client.player.networkHandler.sendCommand(command);
        }
    }

    // ── Networking ────────────────────────────────────────────────────────────

    private void registerNetworking() {
        // Receive HUD state from server
        ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                ValorantHudState.active      = payload.active();
                ValorantHudState.health      = payload.health();
                ValorantHudState.shield      = payload.shield();
                ValorantHudState.ammo        = payload.ammo();
                ValorantHudState.maxAmmo     = payload.maxAmmo();
                ValorantHudState.reserve     = payload.reserve();
                ValorantHudState.chargesC    = payload.chargesC();
                ValorantHudState.chargesQ    = payload.chargesQ();
                ValorantHudState.chargesE    = payload.chargesE();
                ValorantHudState.ultProgress = payload.ultProgress();
                ValorantHudState.ultMax      = payload.ultMax();
                ValorantHudState.agentName   = payload.agentName();
            })
        );

        // Announce mod presence to server on join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            ClientPlayNetworking.send(new HelloPayload("1.0.0"))
        );
    }

    // ── HUD Overlay ───────────────────────────────────────────────────────────

    private void registerHud() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!ValorantHudState.active) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden) return;
            int W = client.getWindow().getScaledWidth();
            int H = client.getWindow().getScaledHeight();
            ValorantHudRenderer.render(drawContext, W, H, tickDelta);
        });
    }
}
