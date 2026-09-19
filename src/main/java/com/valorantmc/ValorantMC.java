package com.valorantmc;

import com.valorantmc.commands.ValorantCommand;
import com.valorantmc.listeners.*;
import com.valorantmc.managers.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public final class ValorantMC extends JavaPlugin {

    private static ValorantMC instance;

    private GameManager gameManager;
    private WeaponManager weaponManager;
    private ShopManager shopManager;
    private SkinManager skinManager;
    private AgentManager agentManager;
    private EconomyManager economyManager;
    private AbilityManager abilityManager;
    private MapManager mapManager;
    private StatsManager statsManager;
    private HudManager hudManager;
    private LobbyManager lobbyManager;
    private ResourcePackManager resourcePackManager;
    private com.valorantmc.network.FabricChannelListener fabricChannelListener;
    private final java.util.Map<java.util.UUID, com.valorantmc.game.CustomGameSettings> customSettings
            = new java.util.HashMap<>();
    private com.valorantmc.listeners.AbilityListener abilityListener;

    private com.valorantmc.web.WebGuiServer webGuiServer;
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        instance = this;

        // Save default configs
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // Load messages
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        // Initialize managers in dependency order
        lobbyManager   = new LobbyManager(this);
        weaponManager  = new WeaponManager(this);
        skinManager    = new SkinManager(this);
        economyManager = new EconomyManager(this);
        agentManager   = new AgentManager(this);
        abilityManager = new AbilityManager(this);
        shopManager    = new ShopManager(this);
        mapManager     = new MapManager(this);
        statsManager   = new StatsManager(this);
        gameManager    = new GameManager(this);
        hudManager          = new HudManager(this);
        hudManager.start();
        resourcePackManager = new ResourcePackManager(this);
        resourcePackManager.start();
        fabricChannelListener = new com.valorantmc.network.FabricChannelListener(this);
        fabricChannelListener.register();

        // Web GUI Admin Panel
        webGuiServer = new com.valorantmc.web.WebGuiServer(this, 8766);
        webGuiServer.start();

        // Register commands & tab completers
        ValorantCommand cmd = new ValorantCommand(this);
        com.valorantmc.commands.ValorantTabCompleter vTab = new com.valorantmc.commands.ValorantTabCompleter(this);
        com.valorantmc.commands.MapSetupTabCompleter mapTab = new com.valorantmc.commands.MapSetupTabCompleter(this);

        String[] playerCmds = {"valorant", "vshop", "vagent", "vstats", "vreload", "vdropspike", "vwalk", "vuse", "vskin", "vplay", "vcustom", "vscoreboard", "vspec", "vstart", "vjoin", "vleave", "vquick", "vpack"};
        for (String c : playerCmds) {
            if (getCommand(c) != null) {
                getCommand(c).setExecutor(cmd);
                getCommand(c).setTabCompleter(vTab);
            }
        }

        if (getCommand("vmapsetup") != null) {
            getCommand("vmapsetup").setExecutor(new com.valorantmc.commands.MapSetupCommand(this));
            getCommand("vmapsetup").setTabCompleter(mapTab);
        }

        if (getCommand("vadmin") != null) {
            getCommand("vadmin").setExecutor(
                (sender, cmd2, label, args) -> {
                    if (!(sender instanceof org.bukkit.entity.Player p)) { sender.sendMessage("Players only."); return true; }
                    if (!p.hasPermission("valorantmc.admin")) { p.sendMessage(colorize("&cNo permission.")); return true; }
                    if (args.length > 0 && args[0].equalsIgnoreCase("web")) {
                        p.sendMessage(colorize("&a&lValorantMC Web Admin GUI: &bhttp://localhost:8766"));
                        return true;
                    }
                    com.valorantmc.game.ValorantGame g = gameManager.getGame(p);
                    p.openInventory(com.valorantmc.gui.AdminGUI.buildMain(p, g));
                    return true;
                });
            getCommand("vadmin").setTabCompleter((sender, command, alias, args) -> args.length == 1 ? java.util.List.of("web") : java.util.Collections.emptyList());
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new com.valorantmc.listeners.AdminListener(this), this);
        abilityListener = new AbilityListener(this);
        getServer().getPluginManager().registerEvents(abilityListener, this);
        getServer().getPluginManager().registerEvents(new SpectatorListener(this), this);

        getLogger().info("=================================");
        getLogger().info("  ValorantMC v" + getDescription().getVersion() + " enabled!");
        getLogger().info("  Weapons: " + weaponManager.getWeaponCount());
        getLogger().info("  Agents:  " + agentManager.getAgentCount());
        getLogger().info("  Maps:    " + mapManager.getMapCount());
        getLogger().info("  Web GUI: http://localhost:8766");
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        if (webGuiServer   != null) webGuiServer.stop();
        if (economyManager != null) economyManager.saveAll();
        if (skinManager    != null) skinManager.saveAll();
        if (statsManager   != null) statsManager.saveAll();
        if (fabricChannelListener != null) fabricChannelListener.unregister();
        if (resourcePackManager != null) resourcePackManager.stop();
        if (hudManager     != null) hudManager.stop();
        if (gameManager    != null) gameManager.shutdown();
        getLogger().info("ValorantMC disabled. Good game!");
    }

    // ── Accessors ────────────────────────────────────────────────────────────
    public static ValorantMC getInstance() { return instance; }

    public GameManager    getGameManager()    { return gameManager;    }
    public WeaponManager  getWeaponManager()  { return weaponManager;  }
    public ShopManager    getShopManager()    { return shopManager;    }
    public SkinManager    getSkinManager()    { return skinManager;    }
    public AgentManager   getAgentManager()   { return agentManager;   }
    public EconomyManager getEconomyManager() { return economyManager; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public MapManager     getMapManager()     { return mapManager;     }
    public StatsManager   getStatsManager()   { return statsManager;   }
    public HudManager     getHudManager()     { return hudManager;     }
    public LobbyManager         getLobbyManager()         { return lobbyManager;         }
    public ResourcePackManager  getResourcePackManager()  { return resourcePackManager;  }
    public com.valorantmc.network.FabricChannelListener getFabricChannelListener() { return fabricChannelListener; }
    public com.valorantmc.game.CustomGameSettings getCustomSettings(java.util.UUID uuid) {
        return customSettings.computeIfAbsent(uuid, k -> new com.valorantmc.game.CustomGameSettings());
    }
    public com.valorantmc.listeners.AbilityListener getAbilityListener() { return abilityListener; }

    public FileConfiguration getMessages() { return messages; }

    public String msg(String path) {
        String prefix = messages.getString("prefix", "&b[VAL]&r ");
        String raw = messages.getString(path, "&cMissing message: " + path);
        return colorize(prefix + raw);
    }

    public String msgRaw(String path) {
        return colorize(messages.getString(path, "&cMissing message: " + path));
    }

    public static String colorize(String s) {
        return s.replace("&", "\u00a7");
    }
}
