package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.game.ValorantGame;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class AbilityManager {

    private final ValorantMC plugin;

    public AbilityManager(ValorantMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Activate an ability by hotbar key character.
     * Called from AbilityListener when a player right-clicks an ability item.
     */
    public void activateAbility(Player player, char key, ValorantGame game) {
        Agent agent = game.getAgent(player);
        if (agent == null) {
            player.sendMessage(plugin.msg("agents.no-agent"));
            return;
        }

        switch (Character.toUpperCase(key)) {
            case 'C' -> agent.useC(player, game);
            case 'Q' -> agent.useQ(player, game);
            case 'E' -> agent.useE(player, game);
            case 'X' -> agent.useX(player, game);
        }

        // Refresh ability items after use
        agent.giveAbilityItems(player);
    }

    /** Check held item NBT and return ability key ('C','Q','E','X') or '\0' */
    public char getAbilityKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return '\0';
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "ability_key"), PersistentDataType.STRING);
        if (val == null || val.isEmpty()) return '\0';
        return val.charAt(0);
    }

    /** Check if item is an ability item */
    public boolean isAbilityItem(ItemStack item) {
        return getAbilityKey(item) != '\0';
    }

    /** Check if item is the Spike */
    public boolean isSpikeItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "spike"), PersistentDataType.BOOLEAN);
    }
}
