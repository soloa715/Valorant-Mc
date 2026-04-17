package com.valorantmc.shop;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AgentSelectGUI {

    public static final String TITLE = ValorantMC.colorize("&c&lSELECT YOUR AGENT");

    // Material map for each agent (representing their 'color')
    private static Material agentMaterial(String agentName) {
        return switch (agentName.toLowerCase()) {
            case "jett"      -> Material.LIGHT_BLUE_CONCRETE;
            case "sage"      -> Material.CYAN_CONCRETE;
            case "reyna"     -> Material.PURPLE_CONCRETE;
            case "phoenix"   -> Material.ORANGE_CONCRETE;
            case "sova"      -> Material.BLUE_CONCRETE;
            case "raze"      -> Material.YELLOW_CONCRETE;
            case "killjoy"   -> Material.YELLOW_CONCRETE;
            case "cypher"    -> Material.GRAY_CONCRETE;
            case "omen"      -> Material.PURPLE_CONCRETE;
            case "viper"     -> Material.GREEN_CONCRETE;
            case "brimstone" -> Material.RED_CONCRETE;
            case "breach"    -> Material.ORANGE_CONCRETE;
            default          -> Material.WHITE_CONCRETE;
        };
    }

    public static Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        List<Agent> agents = new ArrayList<>(ValorantMC.getInstance().getAgentManager().getAllAgents());

        // Arrange by role: Duelists, Initiators, Controllers, Sentinels
        int slot = 0;
        for (AgentRole role : AgentRole.values()) {
            for (Agent agent : agents) {
                if (agent.getRole() != role) continue;
                if (slot >= 45) break;
                inv.setItem(slot, buildAgentItem(agent, player));
                slot++;
            }
            slot = nextRowStart(slot);
        }

        // Bottom row info
        ItemStack info = buildItem(Material.BOOK,
                ValorantMC.colorize("&e&lAgent Select"),
                List.of(
                        ValorantMC.colorize("&7Click an agent to select them."),
                        ValorantMC.colorize("&7Each agent has unique abilities.")
                ));
        inv.setItem(49, info);

        ItemStack filler = buildItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        return inv;
    }

    private static ItemStack buildAgentItem(Agent agent, Player player) {
        com.valorantmc.game.ValorantGame game =
                ValorantMC.getInstance().getGameManager().getGame(player);
        boolean isSelected = game != null && game.getAgent(player) != null
                && game.getAgent(player).getName().equals(agent.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ValorantMC.colorize(agent.getRole().getColor() + agent.getRole().getDisplayName()));
        lore.add("");
        lore.add(ValorantMC.colorize("&7C: &f" + agent.getAbilityC().name));
        lore.add(ValorantMC.colorize("&7Q: &f" + agent.getAbilityQ().name));
        lore.add(ValorantMC.colorize("&7E: &f" + agent.getAbilityE().name));
        lore.add(ValorantMC.colorize("&7X: &f" + agent.getAbilityX().name));
        lore.add("");
        lore.add(ValorantMC.colorize(isSelected ? "&a✔ Selected!" : "&eClick to select!"));

        String nameColor = isSelected ? "&a" : "&f";
        ItemStack item = buildItem(agentMaterial(agent.getName()),
                ValorantMC.colorize(nameColor + "&l" + agent.getDisplayName()), lore);

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "select_agent"),
                org.bukkit.persistence.PersistentDataType.STRING, agent.getName());
        item.setItemMeta(meta);
        return item;
    }

    private static int nextRowStart(int current) {
        // Snap to next multiple-of-9 boundary if not already on one
        return current % 9 == 0 ? current : ((current / 9) + 1) * 9;
    }

    private static ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }

    /** Null if item is not an agent-select item */
    public static String getAgentFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "select_agent"),
                        org.bukkit.persistence.PersistentDataType.STRING);
    }
}
