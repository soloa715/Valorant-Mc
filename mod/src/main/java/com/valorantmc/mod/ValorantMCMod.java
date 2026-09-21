package com.valorantmc.mod;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Mod(ValorantMCMod.MOD_ID)
public class ValorantMCMod {

    public static final String MOD_ID = "valorantmc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String PROTOCOL_VERSION = "1";

    private static SimpleChannel createChannel(String name) {
        return NetworkRegistry.newSimpleChannel(
                new ResourceLocation(MOD_ID, name),
                () -> PROTOCOL_VERSION,
                v -> true,
                v -> true
        );
    }

    public static final SimpleChannel CH_HELLO        = createChannel("hello");
    public static final SimpleChannel CH_BUY_ACTION   = createChannel("buyaction");
    public static final SimpleChannel CH_AGENT_CHOICE = createChannel("agentchoice");
    public static final SimpleChannel CH_MAP_VOTE     = createChannel("mapvote");
    public static final SimpleChannel CH_ADMIN_ACTION  = createChannel("adminaction");

    public static final SimpleChannel CH_HUD          = createChannel("hud");
    public static final SimpleChannel CH_BUY_MENU     = createChannel("buymenu");
    public static final SimpleChannel CH_RADAR        = createChannel("radar");
    public static final SimpleChannel CH_AGENT_SELECT = createChannel("agentselect");
    public static final SimpleChannel CH_MAP_SELECT   = createChannel("mapselect");
    public static final SimpleChannel CH_ADMIN_SYNC   = createChannel("adminsync");
    public static final SimpleChannel CH_SCOREBOARD   = createChannel("scoreboard");

    public ValorantMCMod() {
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> IExtensionPoint.DisplayTest.IGNORESERVERONLY,
                        (remoteVersion, isServer) -> true
                )
        );

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // C2S Packets
        CH_HELLO.registerMessage(0, HelloPayload.class, HelloPayload::encode, HelloPayload::decode, HelloPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH_BUY_ACTION.registerMessage(0, BuyActionPayload.class, BuyActionPayload::encode, BuyActionPayload::decode, BuyActionPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH_AGENT_CHOICE.registerMessage(0, AgentChoicePayload.class, AgentChoicePayload::encode, AgentChoicePayload::decode, AgentChoicePayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH_MAP_VOTE.registerMessage(0, MapVotePayload.class, MapVotePayload::encode, MapVotePayload::decode, MapVotePayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CH_ADMIN_ACTION.registerMessage(0, AdminActionPayload.class, AdminActionPayload::encode, AdminActionPayload::decode, AdminActionPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // S2C Packets
        CH_HUD.registerMessage(0, HudPayload.class, HudPayload::encode, HudPayload::decode, HudPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH_BUY_MENU.registerMessage(0, BuyMenuPayload.class, BuyMenuPayload::encode, BuyMenuPayload::decode, BuyMenuPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH_RADAR.registerMessage(0, RadarPayload.class, RadarPayload::encode, RadarPayload::decode, RadarPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH_AGENT_SELECT.registerMessage(0, AgentSelectPayload.class, AgentSelectPayload::encode, AgentSelectPayload::decode, AgentSelectPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH_MAP_SELECT.registerMessage(0, MapSelectPayload.class, MapSelectPayload::encode, MapSelectPayload::decode, MapSelectPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH_ADMIN_SYNC.registerMessage(0, AdminSyncPayload.class, AdminSyncPayload::encode, AdminSyncPayload::decode, AdminSyncPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH_SCOREBOARD.registerMessage(0, ScoreboardPayload.class, ScoreboardPayload::encode, ScoreboardPayload::decode, ScoreboardPayload::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(this::clientSetup);
            modEventBus.addListener(this::registerKeyMappings);
            modEventBus.addListener(this::registerGuiOverlays);
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    public static void sendToServer(Object message) {
        if (message instanceof HelloPayload msg) CH_HELLO.sendToServer(msg);
        else if (message instanceof BuyActionPayload msg) CH_BUY_ACTION.sendToServer(msg);
        else if (message instanceof AgentChoicePayload msg) CH_AGENT_CHOICE.sendToServer(msg);
        else if (message instanceof MapVotePayload msg) CH_MAP_VOTE.sendToServer(msg);
        else if (message instanceof AdminActionPayload msg) CH_ADMIN_ACTION.sendToServer(msg);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("ValorantMC Forge Client initialized!");
    }

    private static final String CATEGORY = "key.categories.valorantmc";
    public static KeyMapping KEY_SHOP;
    public static KeyMapping KEY_RELOAD;
    public static KeyMapping KEY_AGENT;
    public static KeyMapping KEY_DROPSPIKE;
    public static KeyMapping KEY_WALK;
    public static KeyMapping KEY_ABILITY_C;
    public static KeyMapping KEY_ABILITY_Q;
    public static KeyMapping KEY_ABILITY_E;
    public static KeyMapping KEY_ULT;
    public static KeyMapping KEY_ADMIN;
    public static KeyMapping KEY_MAP;
    public static KeyMapping KEY_SCOREBOARD;

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        KEY_SHOP      = new KeyMapping("key.valorantmc.shop",      InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
        KEY_RELOAD    = new KeyMapping("key.valorantmc.reload",    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
        KEY_AGENT     = new KeyMapping("key.valorantmc.agent",     InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
        KEY_DROPSPIKE = new KeyMapping("key.valorantmc.dropspike", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
        KEY_WALK      = new KeyMapping("key.valorantmc.walk",      InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
        KEY_ABILITY_C = new KeyMapping("key.valorantmc.ability_c", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
        KEY_ABILITY_Q = new KeyMapping("key.valorantmc.ability_q", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
        KEY_ABILITY_E = new KeyMapping("key.valorantmc.ability_e", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
        KEY_ULT       = new KeyMapping("key.valorantmc.ult",       InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
        KEY_ADMIN     = new KeyMapping("key.valorantmc.admin",     InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_0, CATEGORY);
        KEY_MAP       = new KeyMapping("key.valorantmc.map",       InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
        KEY_SCOREBOARD= new KeyMapping("key.valorantmc.scoreboard",InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, CATEGORY);

        event.register(KEY_SHOP);
        event.register(KEY_RELOAD);
        event.register(KEY_AGENT);
        event.register(KEY_DROPSPIKE);
        event.register(KEY_WALK);
        event.register(KEY_ABILITY_C);
        event.register(KEY_ABILITY_Q);
        event.register(KEY_ABILITY_E);
        event.register(KEY_ULT);
        event.register(KEY_ADMIN);
        event.register(KEY_MAP);
        event.register(KEY_SCOREBOARD);
    }

    private void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("valorant_hud", (gui, guiGraphics, partialTick, width, height) -> {
            if (ValorantHudState.active) {
                ValorantHudRenderer.render(guiGraphics, width, height);
                CrosshairRenderer.render(guiGraphics, width, height);
            }
        });
    }

    private int helloTick = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        helloTick++;
        // Periodic heartbeat to ensure server always knows mod is present
        if (helloTick % 40 == 0) {
            sendToServer(new HelloPayload("1.0.0"));
        }

        if (client.screen != null) return;

        if (KEY_SHOP != null && KEY_SHOP.consumeClick()) {
            client.execute(() -> client.setScreen(new BuyScreen(null, ValorantHudState.credits)));
        }

        if (KEY_AGENT != null && KEY_AGENT.consumeClick()) {
            List<String> list = List.of(
                "Jett", "Reyna", "Raze", "Phoenix", "Neon",
                "Sova", "Skye", "Breach", "Fade", "Gekko",
                "Omen", "Viper", "Brimstone",
                "Sage", "Cypher", "Killjoy", "Chamber"
            );
            client.execute(() -> client.setScreen(new AgentSelectScreen(list, ValorantHudState.agentName)));
        }

        checkKey(client, KEY_RELOAD,    "vreload");
        checkKey(client, KEY_DROPSPIKE, "vdropspike");
        checkKey(client, KEY_WALK,      "vwalk");
        checkKey(client, KEY_ABILITY_C, "vuse C");
        checkKey(client, KEY_ABILITY_Q, "vuse Q");
        checkKey(client, KEY_ABILITY_E, "vuse E");
        checkKey(client, KEY_ULT,       "vuse X");
        checkKey(client, KEY_ADMIN,     "vadmin");

        if (KEY_SCOREBOARD != null && KEY_SCOREBOARD.consumeClick()) {
            boolean isSpectator = client.player.isSpectator();
            client.player.connection.sendCommand(isSpectator ? "vspec" : "vscoreboard");
        }

        if (KEY_MAP != null && KEY_MAP.consumeClick()) {
            if (ValorantHudState.mapList != null && !ValorantHudState.mapList.isEmpty()) {
                client.execute(() -> client.setScreen(
                        new MapSelectScreen(ValorantHudState.mapList, ValorantHudState.currentMap)));
            } else {
                client.player.connection.sendCommand("vmap list");
            }
        }
    }

    private static void checkKey(Minecraft client, KeyMapping kb, String command) {
        if (kb == null) return;
        while (kb.consumeClick()) {
            assert client.player != null;
            client.player.connection.sendCommand(command);
        }
    }

    @SubscribeEvent
    public void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        sendToServer(new HelloPayload("1.0.0"));
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ValorantHudState.clear();
    }
}
