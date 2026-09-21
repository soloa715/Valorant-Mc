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

    public record PurchaseRecord(String itemId, String displayName, int cost, int slot) {}

    private final java.util.Map<java.util.UUID, java.util.List<PurchaseRecord>> roundPurchases = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, WeaponType> pendingRequests = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Weapon purchases ──────────────────────────────────────────────────────

    public boolean buyWeapon(Player p, WeaponType type, ValorantGame game) {
        if (game != null && game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(plugin.msg("weapons.cannot-buy"));
            return false;
        }

        int cost = type.getCost();
        if (game != null && !plugin.getEconomyManager().canAfford(p, cost)) {
            p.sendMessage(plugin.msg("weapons.not-enough-credits")
                    .replace("{cost}", String.valueOf(cost)));
            return false;
        }

        if (game != null) {
            plugin.getEconomyManager().spend(p, cost);
        }

        // Place weapon in correct slot based on category
        Weapon weapon = new Weapon(type);
        int slot = getPreferredSlot(type);
        plugin.getWeaponManager().giveTaCZWeapon(p, type, slot);
        // Auto-equip the bought weapon so the player can use it immediately
        p.getInventory().setHeldItemSlot(slot);
        plugin.getWeaponManager().setHeldWeapon(p, weapon);

        // Record purchase for refund
        if (game != null && cost > 0) {
            roundPurchases.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>())
                    .add(new PurchaseRecord(type.name(), type.getDisplayName(), cost, slot));
        }

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
        if (game != null && game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(plugin.msg("weapons.cannot-buy"));
            return false;
        }
        int cost = 400;
        if (game != null && !plugin.getEconomyManager().canAfford(p, cost)) {
            p.sendMessage(plugin.msg("weapons.not-enough-credits").replace("{cost}", String.valueOf(cost)));
            return false;
        }
        if (game != null) {
            plugin.getEconomyManager().spend(p, cost);
            game.setShield(p, 25);
            roundPurchases.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>())
                    .add(new PurchaseRecord("light_shield", "Light Shield", cost, -1));
        }
        p.sendMessage(ValorantMC.colorize("&aBought &fLight Shield &a(+25hp shield) for &6400c"));
        return true;
    }

    public boolean buyHeavyArmor(Player p, ValorantGame game) {
        if (game != null && game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(plugin.msg("weapons.cannot-buy"));
            return false;
        }
        int cost = 1000;
        if (game != null && !plugin.getEconomyManager().canAfford(p, cost)) {
            p.sendMessage(plugin.msg("weapons.not-enough-credits").replace("{cost}", String.valueOf(cost)));
            return false;
        }
        if (game != null) {
            plugin.getEconomyManager().spend(p, cost);
            game.setHeavyShield(p);
            roundPurchases.computeIfAbsent(p.getUniqueId(), k -> new java.util.ArrayList<>())
                    .add(new PurchaseRecord("heavy_shield", "Heavy Shield", cost, -1));
        }
        p.sendMessage(ValorantMC.colorize("&aBought &fHeavy Shield &a(+50hp shield) for &61000c"));
        return true;
    }

    // ── Refund System ──────────────────────────────────────────────────────────

    public void resetRoundPurchases() {
        roundPurchases.clear();
        pendingRequests.clear();
    }

    public java.util.List<PurchaseRecord> getRoundPurchases(Player p) {
        return roundPurchases.getOrDefault(p.getUniqueId(), java.util.Collections.emptyList());
    }

    public boolean refundLatest(Player p, ValorantGame game) {
        if (game != null && game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(ValorantMC.colorize("&cYou can only refund items during the Buy Phase!"));
            return false;
        }
        java.util.List<PurchaseRecord> list = roundPurchases.get(p.getUniqueId());
        if (list == null || list.isEmpty()) {
            p.sendMessage(ValorantMC.colorize("&cNo recent purchases to refund this round."));
            return false;
        }
        PurchaseRecord last = list.remove(list.size() - 1);
        return processRefund(p, game, last);
    }

    public boolean refundItem(Player p, String itemId, ValorantGame game) {
        if (game != null && game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(ValorantMC.colorize("&cYou can only refund items during the Buy Phase!"));
            return false;
        }
        java.util.List<PurchaseRecord> list = roundPurchases.get(p.getUniqueId());
        if (list == null || list.isEmpty()) return false;
        PurchaseRecord target = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).itemId().equalsIgnoreCase(itemId)) {
                target = list.remove(i);
                break;
            }
        }
        if (target == null) return false;
        return processRefund(p, game, target);
    }

    private boolean processRefund(Player p, ValorantGame game, PurchaseRecord rec) {
        plugin.getEconomyManager().addCredits(p, rec.cost());
        if (rec.itemId().equals("light_shield") || rec.itemId().equals("heavy_shield")) {
            if (game != null) game.setShield(p, 0);
        } else {
            // Weapon refund
            if (rec.slot() == 0) {
                p.getInventory().setItem(0, null);
                plugin.getWeaponManager().setHeldWeapon(p, null);
            } else if (rec.slot() == 1) {
                // Reset sidearm to classic
                plugin.getWeaponManager().giveTaCZWeapon(p, WeaponType.CLASSIC, 1);
            }
        }
        p.sendMessage(ValorantMC.colorize("&aRefunded &f" + rec.displayName() + " &afor &6+" + rec.cost() + " credits!"));
        return true;
    }

    // ── Teammate Weapon Requests ───────────────────────────────────────────────

    public boolean requestWeapon(Player p, WeaponType type, ValorantGame game) {
        if (game != null && game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(ValorantMC.colorize("&cYou can only request weapons during the Buy Phase!"));
            return false;
        }
        pendingRequests.put(p.getUniqueId(), type);
        p.sendMessage(ValorantMC.colorize("&eRequested &b" + type.getDisplayName() + " &efrom your team."));
        if (game != null) {
            com.valorantmc.game.ValorantTeam team = game.getTeam(p);
            if (team != null) {
                for (Player teammate : team.getOnlinePlayers()) {
                    if (teammate.equals(p)) continue;
                    teammate.sendMessage(ValorantMC.colorize(
                            "&e[BUY REQUEST] &b" + p.getName() + " &7requested &f" + type.getDisplayName()
                                    + " &6(" + type.getCost() + "c)! &a[Type /vshop buyfor " + p.getName() + " " + type.name() + "]"));
                    teammate.playSound(teammate.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                }
            }
        }
        return true;
    }

    public boolean buyForTeammate(Player buyer, Player recipient, WeaponType type, ValorantGame game) {
        if (game != null && game.getState() != GameState.BUY_PHASE) {
            buyer.sendMessage(ValorantMC.colorize("&cYou can only buy for teammates during the Buy Phase!"));
            return false;
        }
        if (buyer.equals(recipient)) return false;
        int cost = type.getCost();
        if (game != null && !plugin.getEconomyManager().canAfford(buyer, cost)) {
            buyer.sendMessage(ValorantMC.colorize("&cNot enough credits to buy " + type.getDisplayName() + " for " + recipient.getName() + "!"));
            return false;
        }

        if (game != null) {
            plugin.getEconomyManager().spend(buyer, cost);
        }

        // Deliver weapon directly to recipient
        Weapon weapon = new Weapon(type);
        int slot = getPreferredSlot(type);
        plugin.getWeaponManager().giveTaCZWeapon(recipient, type, slot);
        recipient.getInventory().setHeldItemSlot(slot);
        plugin.getWeaponManager().setHeldWeapon(recipient, weapon);
        pendingRequests.remove(recipient.getUniqueId());

        buyer.sendMessage(ValorantMC.colorize("&aYou bought &f" + type.getDisplayName() + " &afor &b" + recipient.getName() + "&a! &6(-" + cost + "c)"));
        recipient.sendMessage(ValorantMC.colorize("&a&l" + buyer.getName() + " &abought you a &f" + type.getDisplayName() + "&a!"));
        recipient.playSound(recipient.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        return true;
    }

    public WeaponType getPendingRequest(Player p) {
        return pendingRequests.get(p.getUniqueId());
    }

    // ── Ability purchases ──────────────────────────────────────────────────────

    public boolean buyAbility(Player p, char abilityKey, ValorantGame game) {
        if (game != null && game.getState() != GameState.BUY_PHASE) {
            p.sendMessage(plugin.msg("weapons.cannot-buy"));
            return false;
        }

        com.valorantmc.agents.Agent agent = (game != null) ? game.getAgent(p) : plugin.getAgentManager().getAgent(p);
        if (agent == null) {
            p.sendMessage(ValorantMC.colorize("&cYou haven't selected an agent! Use /vagent"));
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
        boolean freeSkins = plugin.getConfig().getBoolean("economy.free-skins", true);
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

    public int getPreferredSlot(WeaponType type) {
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
