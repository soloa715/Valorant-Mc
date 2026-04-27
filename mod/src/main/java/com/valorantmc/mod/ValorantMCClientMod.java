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
 *  2. Receive HUD/buy-menu/radar packets and update local state.
 *  3. Render Valorant HUD overlay + crosshair.
 *  4. Open the buy screen when B is pressed during buy phase.
 *  5. Announce mod presence to the server on join.
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

            // Buy key: open GUI during buy phase, send command otherwise
            if (KEY_SHOP.wasPressed()) {
                if (ValorantHudState.roundPhase == 1) {
                    client.execute(() -> client.setScreen(
                            new BuyScreen(null, ValorantHudState.credits)));
                } else {
                    client.player.networkHandler.sendCommand("vshop");
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
        // HUD state
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
                    ValorantHudState.killFeed       = kf;
                    ValorantHudState.killFeedShownAt = System.currentTimeMillis();
                }
            })
        );

        // Buy menu state
        ClientPlayNetworking.registerGlobalReceiver(BuyMenuPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                ValorantHudState.credits    = payload.credits();
                ValorantHudState.inBuyPhase = payload.inBuyPhase();
                if (payload.inBuyPhase() && context.client().currentScreen == null) {
                    context.client().setScreen(
                            new BuyScreen(null, payload.credits()));
                }
            })
        );

        // Radar / minimap data
        ClientPlayNetworking.registerGlobalReceiver(RadarPayload.TYPE, (payload, context) ->
            context.client().execute(() ->
                ValorantHudState.radarData = payload.data()
            )
        );

        // Announce mod presence on join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            ClientPlayNetworking.send(new HelloPayload("1.0.0"))
        );
    }

    // ── HUD + Crosshair ───────────────────────────────────────────────────────

    private void registerHud() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden) return;
            if (client.currentScreen != null) return;

            int W = client.getWindow().getScaledWidth();
            int H = client.getWindow().getScaledHeight();

            // Crosshair (always shown while in-game, replaces vanilla)
            if (client.player != null) {
                CrosshairRenderer.render(drawContext, W, H);
            }

            // Valorant HUD overlay (only during active game)
            if (ValorantHudState.active) {
                ValorantHudRenderer.render(drawContext, W, H, tickDelta);
            }
        });
    }
}
