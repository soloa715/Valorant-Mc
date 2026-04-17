package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.GameState;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponCategory;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.entity.Player;

public class ShopManager {

    private final ValorantMC plugin;

    public ShopManager(ValorantMC plugin) {
        this.plugin = plugin;
    }

    // ── Weapon purchases ──────────────────────────────────────────────────────

    public boolean buyWeapon(Player p, WeaponType type, ValorantGame game) {
        if (game == null || game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(plugin.msg("weapons.cannot-buy"));
            return false;
        }

        int cost = type.getCost();
        if (!plugin.getEconomyManager().canAfford(p, cost)) {
            p.sendMessage(plugin.msg("weapons.not-enough-credits")
                    .replace("{cost}", String.valueOf(cost)));
            return false;
        }

        plugin.getEconomyManager().spend(p, cost);

        // Place weapon in correct slot based on category
        Weapon weapon = new Weapon(type);
        int slot = getPreferredSlot(type);
        p.getInventory().setItem(slot, weapon.toItemStack(p.getUniqueId()));
        // Auto-equip the bought weapon so the player can use it immediately
        p.getInventory().setHeldItemSlot(slot);
        plugin.getWeaponManager().setHeldWeapon(p, weapon);

        String msg = plugin.msg("shop.bought")
                .replace("{item}", type.getDisplayName())
                .replace("{cost}", String.valueOf(cost));
        p.sendMessage(msg);
        p.sendMessage(ValorantMC.colorize("&7Credits remaining: &6"
                + plugin.getEconomyManager().getCredits(p)));
        return true;
    }

    // ── Armor purchases ────────────────────────────────────────────────────────

    public boolean buyLightArmor(Player p, ValorantGame game) {
        if (game == null || game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(plugin.msg("weapons.cannot-buy"));
            return false;
        }
        int cost = 400;
        if (!plugin.getEconomyManager().canAfford(p, cost)) {
            p.sendMessage(plugin.msg("weapons.not-enough-credits").replace("{cost}", String.valueOf(cost)));
            return false;
        }
        plugin.getEconomyManager().spend(p, cost);
        game.setShield(p, 25);
        p.sendMessage(ValorantMC.colorize("&aBought &fLight Shield &a(+25hp shield) for &6400c"));
        return true;
    }

    public boolean buyHeavyArmor(Player p, ValorantGame game) {
        if (game == null || game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(plugin.msg("weapons.cannot-buy"));
            return false;
        }
        int cost = 1000;
        if (!plugin.getEconomyManager().canAfford(p, cost)) {
            p.sendMessage(plugin.msg("weapons.not-enough-credits").replace("{cost}", String.valueOf(cost)));
            return false;
        }
        plugin.getEconomyManager().spend(p, cost);
        game.setHeavyShield(p);
        p.sendMessage(ValorantMC.colorize("&aBought &fHeavy Shield &a(+50hp shield) for &61000c"));
        return true;
    }

    // ── Ability purchases ──────────────────────────────────────────────────────

    public boolean buyAbility(Player p, char abilityKey, ValorantGame game) {
        if (game == null || game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(plugin.msg("weapons.cannot-buy"));
            return false;
        }

        com.valorantmc.agents.Agent agent = game.getAgent(p);
        if (agent == null) {
            p.sendMessage(ValorantMC.colorize("&cYou haven't selected an agent!"));
            return false;
        }

        com.valorantmc.agents.Agent.Ability ability = switch (Character.toUpperCase(abilityKey)) {
            case 'C' -> agent.getAbilityC();
            case 'Q' -> agent.getAbilityQ();
            case 'E' -> agent.getAbilityE();
            default  -> null;
        };

        if (ability == null) return false;
        if (ability.cost == 0) {
            p.sendMessage(ValorantMC.colorize("&7That ability is free!"));
            return true;
        }
        if (ability.getCurrentCharges() >= ability.charges) {
            p.sendMessage(ValorantMC.colorize("&cYou already have max charges for " + ability.name + "!"));
            return false;
        }
        if (!plugin.getEconomyManager().canAfford(p, ability.cost)) {
            p.sendMessage(plugin.msg("weapons.not-enough-credits").replace("{cost}", String.valueOf(ability.cost)));
            return false;
        }

        plugin.getEconomyManager().spend(p, ability.cost);
        ability.resetCharges();
        agent.giveAbilityItems(p);
        p.sendMessage(ValorantMC.colorize("&aBought &e" + ability.name + "&a for &6" + ability.cost + "c"));
        return true;
    }

    // ── Skin purchases ──────────────────────────────────────────────────────────

    public boolean buySkin(Player p, String skinId) {
        SkinManager.SkinData skin = plugin.getSkinManager().getSkin(skinId);
        if (skin == null) {
            p.sendMessage(ValorantMC.colorize("&cSkin not found: " + skinId));
            return false;
        }
        if (plugin.getSkinManager().hasSkin(p.getUniqueId(), skinId)) {
            p.sendMessage(ValorantMC.colorize("&cYou already own that skin!"));
            return false;
        }
        // Use VP currency unless server is in free-skins mode
        boolean freeSkins = plugin.getConfig().getBoolean("free-skins", true);
        int vpCost = skin.tier().vp;
        if (!freeSkins && vpCost > 0) {
            if (!plugin.getEconomyManager().canAffordVP(p, vpCost)) {
                p.sendMessage(ValorantMC.colorize("&cNot enough VP! Need &e" + vpCost + " VP&c, you have &e"
                        + plugin.getEconomyManager().getVP(p) + " VP&c."));
                return false;
            }
            plugin.getEconomyManager().spendVP(p, vpCost);
        }
        plugin.getSkinManager().grantSkin(p.getUniqueId(), skinId);
        p.sendMessage(ValorantMC.colorize("&aUnlocked skin: &e" + skin.displayName()
                + (freeSkins ? "" : " &7(-" + vpCost + " VP)")));
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getPreferredSlot(WeaponType type) {
        // Slot layout (must match Agent.giveAbilityItems and ValorantGame.giveStartingWeapons):
        //   0 = primary    1 = sidearm    2 = knife    3 = spike
        //   4 = C   5 = Q   6 = E   7 = X (ult)   8 = unused/buy hint
        return switch (type.getCategory()) {
            case SIDEARM                       -> 1;
            case MELEE                         -> 2;
            case RIFLE, SNIPER, HEAVY,
                 SMG, SHOTGUN                  -> 0; // primary slot
        };
    }
}
