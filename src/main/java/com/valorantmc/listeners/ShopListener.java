package com.valorantmc.listeners;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.shop.AgentSelectGUI;
import com.valorantmc.shop.ShopGUI;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public class ShopListener implements Listener {

    private final ValorantMC plugin;

    public ShopListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String title = e.getView().getTitle();
        ItemStack clicked = e.getCurrentItem();

        // ── Main Menu GUI ─────────────────────────────────────────────────────
        if (title.equals(com.valorantmc.shop.MainMenuGUI.TITLE)) {
            e.setCancelled(true);
            if (clicked == null || !clicked.hasItemMeta()) return;
            String action = clicked.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "menu_action"), PersistentDataType.STRING);
            if (action == null) return;

            if (action.startsWith("join:")) {
                String gameId = action.substring(5);
                player.closeInventory();
                plugin.getLobbyManager().exitLobby(player);
                plugin.getGameManager().joinGame(player, gameId);
                return;
            }

            switch (action) {
                case "quickplay" -> {
                    player.closeInventory();
                    plugin.getLobbyManager().exitLobby(player);
                    plugin.getGameManager().quickPlay(player);
                }
                case "custom_game" -> {
                    player.closeInventory();
                    player.sendMessage(ValorantMC.colorize(
                            "&e[ValorantMC] &7Custom game creator coming soon! Use &e/vcustom&7."));
                }
                case "skins" -> player.openInventory(
                        com.valorantmc.shop.SkinGUI.build(player, null));
                case "agent" -> {
                    ValorantGame agentGame = plugin.getGameManager().getGame(player);
                    if (agentGame != null) {
                        if (plugin.getFabricChannelListener() != null && plugin.getFabricChannelListener().hasMod(player)) {
                            player.closeInventory();
                            plugin.getFabricChannelListener().sendAgentSelect(player);
                        } else {
                            player.openInventory(AgentSelectGUI.build(player));
                        }
                    } else {
                        player.sendMessage(ValorantMC.colorize(
                                "&7You can pick your agent once you join a game."));
                    }
                }
                case "stats" -> {
                    player.closeInventory();
                    player.performCommand("vstats");
                }
                case "settings" -> {
                    player.closeInventory();
                    player.sendMessage(ValorantMC.colorize(
                            "&7Commands: &e/valorant setlobby &7· &e/valorant reload"));
                }
                case "close" -> player.closeInventory();
            }
            return;
        }

        // ── Custom Game GUI ───────────────────────────────────────────────────
        if (title.equals(com.valorantmc.shop.CustomGameGUI.TITLE)) {
            e.setCancelled(true);
            if (clicked == null || !clicked.hasItemMeta()) return;
            String cgAction = clicked.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "cg_action"), PersistentDataType.STRING);
            if (cgAction == null) return;

            com.valorantmc.game.CustomGameSettings s = plugin.getCustomSettings(player.getUniqueId());
            boolean rebuilt = true;

            switch (cgAction) {
                case "toggle_unlimited_abilities" -> s.unlimitedAbilities = !s.unlimitedAbilities;
                case "toggle_infinite_credits"    -> s.infiniteCredits    = !s.infiniteCredits;
                case "toggle_wallhack"            -> s.wallhack           = !s.wallhack;
                case "toggle_one_shot"            -> s.oneShot            = !s.oneShot;
                case "toggle_infinite_ammo"       -> s.infiniteAmmo       = !s.infiniteAmmo;
                case "toggle_no_cooldowns"        -> s.noCooldowns        = !s.noCooldowns;
                case "toggle_show_hp"             -> s.showEnemyHP        = !s.showEnemyHP;
                case "toggle_ff"                  -> s.allowTeamDamage    = !s.allowTeamDamage;
                case "dmg_mult" -> {
                    boolean right = e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT;
                    s.abilityDmgMult = Math.max(0.25f, Math.min(3.0f,
                            s.abilityDmgMult + (right ? 0.25f : -0.25f)));
                }
                case "start_credits" -> {
                    boolean right = e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT;
                    s.startingCredits = Math.max(0, Math.min(9000,
                            s.startingCredits + (right ? 200 : -200)));
                }
                case "max_rounds" -> {
                    boolean right = e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT;
                    s.maxRounds = Math.max(0, s.maxRounds + (right ? 1 : -1));
                }
                case "start_custom" -> {
                    player.closeInventory();
                    rebuilt = false;
                    s.hostUUID = player.getUniqueId().toString();
                    String gameId = "custom-" + player.getName() + "-" + System.currentTimeMillis() % 10000;
                    com.valorantmc.game.ValorantGame cg = plugin.getGameManager().createGame(gameId);
                    cg.setCustomSettings(s);
                    cg.addPlayer(player);
                    plugin.getGameManager().registerPlayerGame(player, gameId);
                    plugin.getLobbyManager().exitLobby(player);
                    player.sendMessage(ValorantMC.colorize(
                            "&e[Custom] &aGame &f" + gameId + " &acreated! Use &e/valorant start "
                            + gameId + " <map>&a when ready."));
                }
                case "back" -> {
                    player.closeInventory();
                    rebuilt = false;
                    player.openInventory(com.valorantmc.shop.MainMenuGUI.build(player, plugin));
                }
                default -> rebuilt = false;
            }

            if (rebuilt) {
                // Refresh the GUI to reflect the new setting value
                final com.valorantmc.game.CustomGameSettings finalS = s;
                plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        player.openInventory(com.valorantmc.shop.CustomGameGUI.build(player, finalS)), 1L);
            }
            return;
        }

        // ── Lobby GUI ─────────────────────────────────────────────────────────
        if (title.equals(com.valorantmc.shop.LobbyGUI.TITLE)) {
            e.setCancelled(true);
            if (clicked == null || !clicked.hasItemMeta()) return;
            String action = clicked.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "lobby_action"), PersistentDataType.STRING);
            if (action == null) return;
            switch (action) {
                case "quickplay" -> {
                    player.closeInventory();
                    plugin.getGameManager().quickPlay(player);
                }
                case "agent" -> {
                    if (plugin.getFabricChannelListener() != null && plugin.getFabricChannelListener().hasMod(player)) {
                        player.closeInventory();
                        plugin.getFabricChannelListener().sendAgentSelect(player);
                    } else {
                        player.openInventory(AgentSelectGUI.build(player));
                    }
                }
                case "skins" -> player.openInventory(com.valorantmc.shop.SkinGUI.build(player, null));
                case "stats" -> {
                    player.closeInventory();
                    player.performCommand("vstats");
                }
            }
            return;
        }

        // ── Shop GUI ──────────────────────────────────────────────────────────
        if (title.equals(ShopGUI.TITLE)) {
            e.setCancelled(true);
            if (clicked == null) return;

            ValorantGame game = plugin.getGameManager().getGame(player);
            if (game != null && game.getState() != com.valorantmc.game.GameState.BUY_PHASE) {
                player.sendMessage(ValorantMC.colorize("&cThe shop is only available during the Buy Phase!"));
                player.closeInventory();
                return;
            }

            boolean isRightClick = e.isRightClick();

            // Refund button
            if (clicked.hasItemMeta() && Boolean.TRUE.equals(clicked.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "shop_refund"), PersistentDataType.BOOLEAN))) {
                plugin.getShopManager().refundLatest(player, game);
                player.openInventory(ShopGUI.build(player));
                return;
            }

            // Buy for teammate button
            if (clicked.hasItemMeta() && clicked.getItemMeta().getPersistentDataContainer()
                    .has(new NamespacedKey(plugin, "shop_buyfor_player"), PersistentDataType.STRING)) {
                String targetName = clicked.getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "shop_buyfor_player"), PersistentDataType.STRING);
                String weaponStr = clicked.getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "shop_buyfor_weapon"), PersistentDataType.STRING);
                org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(targetName);
                if (target != null && weaponStr != null) {
                    try {
                        WeaponType wt = WeaponType.valueOf(weaponStr);
                        plugin.getShopManager().buyForTeammate(player, target, wt, game);
                    } catch (Exception ignored) {}
                }
                player.openInventory(ShopGUI.build(player));
                return;
            }

            // Weapon purchase or request
            WeaponType type = ShopGUI.getWeaponFromShopItem(clicked);
            if (type != null) {
                if (isRightClick) {
                    plugin.getShopManager().requestWeapon(player, type, game);
                } else {
                    plugin.getShopManager().buyWeapon(player, type, game);
                }
                player.openInventory(ShopGUI.build(player));
                return;
            }

            // Armor purchase
            String armorId = ShopGUI.getArmorFromShopItem(clicked);
            if (armorId != null) {
                if (isRightClick) {
                    plugin.getShopManager().refundItem(player, armorId, game);
                } else {
                    if (armorId.equals("light_shield")) plugin.getShopManager().buyLightArmor(player, game);
                    else if (armorId.equals("heavy_shield")) plugin.getShopManager().buyHeavyArmor(player, game);
                }
                player.openInventory(ShopGUI.build(player));
                return;
            }

            // Ability purchase
            Character abilityKey = ShopGUI.getAbilityKeyFromShopItem(clicked);
            if (abilityKey != null) {
                com.valorantmc.agents.Agent agent = (game != null) ? game.getAgent(player) : plugin.getAgentManager().getAgent(player);
                if (agent != null) {
                    com.valorantmc.agents.Agent.Ability ability = switch (abilityKey) {
                        case 'C' -> agent.getAbilityC();
                        case 'Q' -> agent.getAbilityQ();
                        case 'E' -> agent.getAbilityE();
                        default  -> null;
                    };
                    if (ability != null && ability.cost > 0
                            && ability.getCurrentCharges() >= ability.charges) {
                        player.sendMessage(ValorantMC.colorize(
                                "&c&lShop &r&cYou already have max charges for this ability!"));
                        player.openInventory(ShopGUI.build(player));
                        return;
                    }
                }
                plugin.getShopManager().buyAbility(player, abilityKey, game);
                player.openInventory(ShopGUI.build(player));
            }
            return;
        }

        // ── Skin GUI ──────────────────────────────────────────────────────────
        if (title.equals(com.valorantmc.shop.SkinGUI.TITLE)) {
            e.setCancelled(true);
            if (clicked == null || !clicked.hasItemMeta()) return;

            // Weapon selector row (top row)
            if (e.getRawSlot() < 9) {
                WeaponType wt = WeaponType.values().length > e.getRawSlot() ?
                        WeaponType.values()[e.getRawSlot()] : null;
                if (wt != null) player.openInventory(com.valorantmc.shop.SkinGUI.build(player, wt));
                return;
            }

            // Reset to default button
            if (clicked.getType() == org.bukkit.Material.BARRIER) {
                plugin.getSkinManager().resetAllEquipped(player.getUniqueId());
                player.sendMessage(ValorantMC.colorize("&aReset all weapon skins to default."));
                player.openInventory(com.valorantmc.shop.SkinGUI.build(player, null));
                return;
            }

            String name = clicked.getItemMeta().getDisplayName();
            String stripped = org.bukkit.ChatColor.stripColor(name);
            // Find skin by display name
            for (com.valorantmc.managers.SkinManager.SkinData skin :
                    plugin.getSkinManager().getAllSkins()) {
                if (skin.displayName().equalsIgnoreCase(stripped)) {
                    if (!plugin.getSkinManager().hasSkin(player.getUniqueId(), skin.id())) {
                        // Attempt to buy skin
                        plugin.getShopManager().buySkin(player, skin.id());
                    } else {
                        // Equip skin
                        plugin.getSkinManager().equipSkin(player.getUniqueId(), skin.id());
                        player.sendMessage(ValorantMC.colorize("&aEquipped &f" + skin.displayName()));

                        if (skin.weaponType() == WeaponType.KNIFE) {
                            plugin.getWeaponManager().giveTaCZWeapon(player, WeaponType.KNIFE, 2);
                        } else {
                            int slot = (skin.weaponType().getCategory() == com.valorantmc.weapons.WeaponCategory.SIDEARM) ? 1 : 0;
                            plugin.getWeaponManager().giveTaCZWeapon(player, skin.weaponType(), slot);
                        }
                    }
                    player.openInventory(com.valorantmc.shop.SkinGUI.build(player, skin.weaponType()));
                    return;
                }
            }
            return;
        }

        // ── Agent Select GUI ──────────────────────────────────────────────────
        if (title.equals(AgentSelectGUI.TITLE)) {
            e.setCancelled(true);
            if (clicked == null) return;

            ValorantGame game = plugin.getGameManager().getGame(player);
            if (game != null && game.getState() != com.valorantmc.game.GameState.AGENT_SELECT) {
                player.sendMessage(ValorantMC.colorize("&cYou cannot change agents mid-game!"));
                player.closeInventory();
                return;
            }

            String agentName = AgentSelectGUI.getAgentFromItem(clicked);
            if (agentName == null) return;

            com.valorantmc.agents.Agent agent =
                    plugin.getAgentManager().createInstance(agentName);
            if (agent == null) {
                player.sendMessage(ValorantMC.colorize("&cUnknown agent: " + agentName));
                return;
            }

            if (game != null) {
                game.setAgent(player, agent);
            }
            plugin.getAgentManager().setPlayerAgent(player, agent);
            agent.giveAbilityItems(player);
            player.sendMessage(plugin.msg("agents.selected").replace("{agent}", agent.getDisplayName()));
            player.closeInventory();
        }
    }

    /**
     * If a player closes the agent-select GUI without picking, reopen it —
     * picking is required before the round starts.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        String title = e.getView().getTitle();
        if (!title.equals(AgentSelectGUI.TITLE)) return;

        ValorantGame game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        if (game.getState() != com.valorantmc.game.GameState.AGENT_SELECT) return;
        if (game.getAgent(player) != null) return; // already picked

        // If player has the companion mod, do not force the vanilla chest GUI back open!
        if (plugin.getFabricChannelListener() != null && plugin.getFabricChannelListener().hasMod(player)) return;

        // Reopen next tick — Bukkit doesn't allow opening during the close event
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ValorantGame g = plugin.getGameManager().getGame(player);
            if (g == null) return;
            if (g.getState() != com.valorantmc.game.GameState.AGENT_SELECT) return;
            if (g.getAgent(player) != null) return;
            player.openInventory(AgentSelectGUI.build(player));
            player.sendMessage(ValorantMC.colorize("&c&lYou must pick an agent to continue!"));
        }, 2L);
    }
}
