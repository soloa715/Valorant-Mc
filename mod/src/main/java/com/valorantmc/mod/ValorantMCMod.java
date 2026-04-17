package com.valorantmc.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * ValorantMC Companion Mod
 *
 * Client-side keybinds that relay commands to the server-side plugin.
 * Instead of a custom packet protocol, we use chat commands (prefixed /)
 * which the plugin registers — this keeps the mod forward-compatible.
 *
 *   B – Open buy menu    → /vshop
 *   R – Reload           → /vreload
 *   N – Pick agent       → /vagent
 *   G – Drop spike       → /vdropspike
 *   Y – Toggle walk/run  → /vwalk
 *   F – Use ability C    → /vuse C
 *   C – Use ability Q    → /vuse Q
 *   V – Use ability E    → /vuse E
 *   X – Ult              → /vuse X
 */
@Mod(ValorantMCMod.MODID)
public class ValorantMCMod {

    public static final String MODID = "valorantmcmod";

    public static KeyMapping KEY_SHOP;
    public static KeyMapping KEY_RELOAD;
    public static KeyMapping KEY_AGENT;
    public static KeyMapping KEY_DROPSPIKE;
    public static KeyMapping KEY_WALK;
    public static KeyMapping KEY_ABILITY_C;
    public static KeyMapping KEY_ABILITY_Q;
    public static KeyMapping KEY_ABILITY_E;
    public static KeyMapping KEY_ULT;

    public ValorantMCMod() {
        MinecraftForge.EVENT_BUS.register(this);
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get()
                .getModEventBus().addListener(this::onClientSetup);
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get()
                .getModEventBus().addListener(this::onRegisterKeybinds);
    }

    private void onClientSetup(FMLClientSetupEvent e) { }

    private void onRegisterKeybinds(RegisterKeyMappingsEvent e) {
        KEY_SHOP       = register(e, "key.valorantmc.shop",       GLFW.GLFW_KEY_B);
        KEY_RELOAD     = register(e, "key.valorantmc.reload",     GLFW.GLFW_KEY_R);
        KEY_AGENT      = register(e, "key.valorantmc.agent",      GLFW.GLFW_KEY_N);
        KEY_DROPSPIKE  = register(e, "key.valorantmc.dropspike",  GLFW.GLFW_KEY_G);
        KEY_WALK       = register(e, "key.valorantmc.walk",       GLFW.GLFW_KEY_Y);
        KEY_ABILITY_C  = register(e, "key.valorantmc.ability_c",  GLFW.GLFW_KEY_F);
        KEY_ABILITY_Q  = register(e, "key.valorantmc.ability_q",  GLFW.GLFW_KEY_C);
        KEY_ABILITY_E  = register(e, "key.valorantmc.ability_e",  GLFW.GLFW_KEY_V);
        KEY_ULT        = register(e, "key.valorantmc.ult",        GLFW.GLFW_KEY_X);
    }

    private KeyMapping register(RegisterKeyMappingsEvent e, String name, int key) {
        KeyMapping m = new KeyMapping(name, InputConstants.Type.KEYSYM, key, "key.categories.valorantmc");
        e.register(m);
        return m;
    }

    @SubscribeEvent
    public void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return;
        if (KEY_SHOP       != null && KEY_SHOP.consumeClick())       send(mc, "/vshop");
        if (KEY_RELOAD     != null && KEY_RELOAD.consumeClick())     send(mc, "/vreload");
        if (KEY_AGENT      != null && KEY_AGENT.consumeClick())      send(mc, "/vagent");
        if (KEY_DROPSPIKE  != null && KEY_DROPSPIKE.consumeClick())  send(mc, "/vdropspike");
        if (KEY_WALK       != null && KEY_WALK.consumeClick())       send(mc, "/vwalk");
        if (KEY_ABILITY_C  != null && KEY_ABILITY_C.consumeClick())  send(mc, "/vuse C");
        if (KEY_ABILITY_Q  != null && KEY_ABILITY_Q.consumeClick())  send(mc, "/vuse Q");
        if (KEY_ABILITY_E  != null && KEY_ABILITY_E.consumeClick())  send(mc, "/vuse E");
        if (KEY_ULT        != null && KEY_ULT.consumeClick())        send(mc, "/vuse X");
    }

    private void send(Minecraft mc, String cmd) {
        if (mc.player == null) return;
        // Strip leading / — ClientPacketListener.sendCommand does not expect it
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        mc.player.connection.sendCommand(c);
    }
}
