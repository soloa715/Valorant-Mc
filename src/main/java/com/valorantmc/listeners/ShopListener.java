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
                case "agent" -> player.openInventory(AgentSelectGUI.build(player));
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
            if (game == null) return;

            // Weapon purchase
            WeaponType type = ShopGUI.getWeaponFromShopItem(clicked);
            if (type != null) {
                plugin.getShopManager().buyWeapon(player, type, game);
                // Refresh shop
                player.openInventory(ShopGUI.build(player));
                return;
            }

            // Armor purchase
            String armorId = ShopGUI.getArmorFromShopItem(clicked);
            if (armorId != null) {
                if (armorId.equals("light_shield")) plugin.getShopManager().buyLightArmor(player, game);
                else if (armorId.equals("heavy_shield")) plugin.getShopManager().buyHeavyArmor(player, game);
                player.openInventory(ShopGUI.build(player));
                return;
            }

            // Ability purchase
            Character abilityKey = ShopGUI.getAbilityKeyFromShopItem(clicked);
            if (abilityKey != null) {
                // Double-buy prevention: check before delegating to ShopManager
                com.valorantmc.agents.Agent agent = game.getAgent(player);
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

            if (!clicked.hasItemMeta()) return;
            String name = clicked.getItemMeta().getDisplayName();
            String stripped = org.bukkit.ChatColor.stripColor(name);
            // Find skin by display name
            for (com.valorantmc.managers.SkinManager.SkinData skin :
                    plugin.getSkinManager().getAllSkins()) {
                if (skin.displayName().equalsIgnoreCase(stripped)) {
                    if (!plugin.getSkinManager().hasSkin(player.getUniqueId(), skin.id())) {
                        player.sendMessage(ValorantMC.colorize("&cYou don't own this skin."));
                        return;
                    }
                    // Apply to the held weapon if it matches this skin's weapon type
                    ItemStack held = player.getInventory().getItemInMainHand();
                    WeaponType heldType = com.valorantmc.weapons.Weapon.getWeaponType(held);
                    if (heldType != skin.weaponType()) {
                        player.sendMessage(ValorantMC.colorize(
                                "&eHold a " + skin.weaponType().getDisplayName() + " to apply this skin."));
                        return;
                    }
                    org.bukkit.inventory.meta.ItemMeta meta = held.getItemMeta();
                    if (meta == null) return;
                    meta.setCustomModelData(skin.customModelId());
                    held.setItemMeta(meta);
                    player.sendMessage(ValorantMC.colorize("&aEquipped &f" + skin.displayName()));
                    player.closeInventory();
                    return;
                }
            }
            return;
        }

        // ── Agent Select GUI ──────────────────────────────────────────────────
        if (title.equals(AgentSelectGUI.TITLE)) {
            e.setCancelled(true);
            if (clicked == null) return;

            String agentName = AgentSelectGUI.getAgentFromItem(clicked);
            if (agentName == null) return;

            ValorantGame game = plugin.getGameManager().getGame(player);
            if (game == null) return;

            com.valorantmc.agents.Agent agent =
                    plugin.getAgentManager().createInstance(agentName);
            if (agent == null) {
                player.sendMessage(ValorantMC.colorize("&cUnknown agent: " + agentName));
                return;
            }

            game.setAgent(player, agent);
            agent.giveAbilityItems(player);
            player.sendMessage(plugin.msg("agents.selected").replace("{agent}", agent.getDisplayName()));
            player.closeInventory();
            // Shop will be opened automatically when buy phase starts — no premature open here
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
