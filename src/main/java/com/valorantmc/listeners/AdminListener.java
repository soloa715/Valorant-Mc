package com.valorantmc.listeners;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.game.ValorantTeam;
import com.valorantmc.gui.AdminGUI;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all click events for the admin panel GUI screens.
 */
public class AdminListener implements Listener {

    private final ValorantMC plugin;

    /** Stores each admin's previous GameMode before entering map-setup mode. */
    private final Map<UUID, GameMode> mapSetupModes = new ConcurrentHashMap<>();

    /** Glowing ArmorStand markers spawned while an admin is in map-setup mode. */
    private final Map<UUID, List<ArmorStand>> mapMarkers = new ConcurrentHashMap<>();

    public AdminListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    // ── Inventory close: restore gamemode if admin was in map setup ───────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player admin)) return;
        if (!admin.hasPermission("valorantmc.admin")) return;
        String title = e.getView().getTitle();
        if (!title.equals(AdminGUI.TITLE_MAP)) return;

        // Restore gamemode when map-setup GUI is closed
        GameMode prev = mapSetupModes.remove(admin.getUniqueId());
        if (prev != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!admin.isOnline()) return;
                admin.setGameMode(prev);
                if (prev != GameMode.CREATIVE && prev != GameMode.SPECTATOR) {
                    admin.setAllowFlight(false);
                    admin.setFlying(false);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (!admin.hasPermission("valorantmc.admin")) return;

        String title = e.getView().getTitle();
        boolean isAdminGUI =
                title.equals(AdminGUI.TITLE_MAIN)          ||
                title.equals(AdminGUI.TITLE_PLAYERS_GIVE)  ||
                title.equals(AdminGUI.TITLE_PLAYERS_TROLL) ||
                title.equals(AdminGUI.TITLE_GIVE)          ||
                title.equals(AdminGUI.TITLE_TROLL)         ||
                title.equals(AdminGUI.TITLE_MAP)           ||
                title.equals(AdminGUI.TITLE_GAME);

        if (!isAdminGUI) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(AdminGUI.NSK_ACTION, PersistentDataType.STRING);
        String targetStr = clicked.getItemMeta().getPersistentDataContainer()
                .get(AdminGUI.NSK_TARGET, PersistentDataType.STRING);
        if (action == null) return;

        // Resolve target player
        Player target = null;
        if (targetStr != null) {
            try {
                target = Bukkit.getPlayer(UUID.fromString(targetStr));
            } catch (Exception ignored) {}
        }

        ValorantGame game = plugin.getGameManager().getGame(admin);

        handleAction(admin, target, game, action, e.isShiftClick(), title);
    }

    private void handleAction(Player admin, Player target, ValorantGame game,
                               String action, boolean shift, String title) {

        // ── Navigation ───────────────────────────────────────────────────────
        if (action.equals("close")) {
            admin.closeInventory(); return;
        }
        if (action.equals("back_main")) {
            // If leaving map setup, restore previous gamemode and remove markers
            if (title.equals(AdminGUI.TITLE_MAP)) {
                restoreGameMode(admin);
                removeMapMarkers(admin);
            }
            admin.openInventory(AdminGUI.buildMain(admin, game)); return;
        }
        if (action.equals("give_players")) {
            admin.openInventory(AdminGUI.buildPlayerSelect(admin, game, "give")); return;
        }
        if (action.equals("troll_players")) {
            admin.openInventory(AdminGUI.buildPlayerSelect(admin, game, "troll")); return;
        }
        if (action.equals("map_setup")) {
            // Save current gamemode and enter Creative + fly for map setup
            if (!mapSetupModes.containsKey(admin.getUniqueId())) {
                mapSetupModes.put(admin.getUniqueId(), admin.getGameMode());
            }
            admin.setGameMode(GameMode.CREATIVE);
            admin.setAllowFlight(true);
            admin.setFlying(true);
            admin.sendMessage(ValorantMC.colorize("&b[Map Setup] &fEntered Creative mode. Use the GUI to add spawns/sites."));
            admin.sendMessage(ValorantMC.colorize("&7Your previous gamemode will be restored when you click Back or close this menu."));
            admin.openInventory(AdminGUI.buildMapSetup(admin, game)); return;
        }
        if (action.equals("game_control")) {
            admin.openInventory(AdminGUI.buildGameControl(admin, game)); return;
        }
        if (action.equals("back_give_players")) {
            admin.openInventory(AdminGUI.buildPlayerSelect(admin, game, "give")); return;
        }
        if (action.equals("back_troll_players")) {
            admin.openInventory(AdminGUI.buildPlayerSelect(admin, game, "troll")); return;
        }

        // ── Player select ────────────────────────────────────────────────────
        if (action.startsWith("player_select:")) {
            UUID tid = UUID.fromString(action.substring("player_select:".length()));
            Player t = Bukkit.getPlayer(tid);
            if (t == null || !t.isOnline()) {
                admin.sendMessage(ValorantMC.colorize("&cPlayer not found."));
                return;
            }
            // Determine context from current title
            if (title.equals(AdminGUI.TITLE_PLAYERS_GIVE)) {
                admin.openInventory(AdminGUI.buildGive(admin, t));
            } else {
                admin.openInventory(AdminGUI.buildTroll(admin, t));
            }
            return;
        }

        // ── Give actions ─────────────────────────────────────────────────────
        if (action.startsWith("give_credits:")) {
            int amount = Integer.parseInt(action.substring("give_credits:".length()));
            doGiveCredits(admin, target, game, amount);
            return;
        }
        if (action.equals("give_credits_max")) {
            doGiveCredits(admin, target, game, 9000);
            return;
        }
        if (action.startsWith("give_weapon:")) {
            String typeName = action.substring("give_weapon:".length());
            doGiveWeapon(admin, target, game, typeName);
            return;
        }
        if (action.equals("give_ult_1")) {
            doGiveUlt(admin, target, game, 1);
            return;
        }
        if (action.equals("give_ult_full")) {
            doGiveUlt(admin, target, game, Integer.MAX_VALUE);
            return;
        }
        if (action.startsWith("give_ability_")) {
            char key = action.charAt(action.length() - 1);
            doGiveAbility(admin, target, game, key);
            return;
        }
        if (action.equals("give_light_shield")) {
            if (ensureTarget(admin, target) && game != null) {
                game.setShield(target, 25);
                admin.sendMessage(ok(target, "light shield"));
                refreshGive(admin, target);
            }
            return;
        }
        if (action.equals("give_heavy_shield")) {
            if (ensureTarget(admin, target) && game != null) {
                game.setHeavyShield(target);
                admin.sendMessage(ok(target, "heavy shield"));
                refreshGive(admin, target);
            }
            return;
        }
        if (action.equals("give_refill_ammo")) {
            if (ensureTarget(admin, target)) {
                plugin.getWeaponManager().refillAmmo(target);
                admin.sendMessage(ok(target, "ammo refill"));
                refreshGive(admin, target);
            }
            return;
        }

        // ── Troll actions ────────────────────────────────────────────────────
        if (action.equals("troll_kill")) {
            if (ensureTarget(admin, target) && game != null) {
                game.applyDamage(null, target, 9999, false, false);
                admin.sendMessage(ok(target, "kill"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_freeze")) {
            if (ensureTarget(admin, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 254));
                target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 128));
                admin.sendMessage(ok(target, "freeze (10s)"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_unfreeze")) {
            if (ensureTarget(admin, target)) {
                target.clearActivePotionEffects();
                admin.sendMessage(ok(target, "unfreeze/clear effects"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_blind")) {
            if (ensureTarget(admin, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 160, 0));
                admin.sendMessage(ok(target, "blindness (8s)"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_nausea")) {
            if (ensureTarget(admin, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0));
                admin.sendMessage(ok(target, "nausea (8s)"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_noclip")) {
            if (ensureTarget(admin, target)) {
                boolean flying = target.getAllowFlight();
                target.setAllowFlight(!flying);
                target.setFlying(!flying);
                admin.sendMessage(ok(target, "noclip " + (!flying ? "ON" : "OFF")));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_launch")) {
            if (ensureTarget(admin, target)) {
                target.setVelocity(new org.bukkit.util.Vector(0, 3.5, 0));
                admin.sendMessage(ok(target, "launched"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_strip")) {
            if (ensureTarget(admin, target)) {
                // Remove only valorant weapon items
                for (int i = 0; i < target.getInventory().getSize(); i++) {
                    ItemStack it = target.getInventory().getItem(i);
                    if (it != null && Weapon.getWeaponType(it) != null) {
                        target.getInventory().setItem(i, null);
                    }
                }
                plugin.getWeaponManager().setHeldWeapon(target, null);
                admin.sendMessage(ok(target, "stripped weapons"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_maxcredits")) {
            if (ensureTarget(admin, target) && game != null) {
                plugin.getEconomyManager().setCredits(target.getUniqueId(), 9000);
                admin.sendMessage(ok(target, "max credits (9000)"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_zerocredits")) {
            if (ensureTarget(admin, target) && game != null) {
                plugin.getEconomyManager().setCredits(target.getUniqueId(), 0);
                admin.sendMessage(ok(target, "zero credits"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_ignite")) {
            if (ensureTarget(admin, target)) {
                target.setFireTicks(100);
                admin.sendMessage(ok(target, "ignited (5s)"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_randtp")) {
            if (ensureTarget(admin, target) && game != null) {
                java.util.List<Location> allSpawns = new java.util.ArrayList<>();
                allSpawns.addAll(game.getAttackSpawnsPublic());
                allSpawns.addAll(game.getDefendSpawnsPublic());
                if (!allSpawns.isEmpty()) {
                    Location dest = allSpawns.get(new java.util.Random().nextInt(allSpawns.size()));
                    target.teleport(dest);
                    admin.sendMessage(ok(target, "random teleport"));
                }
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_tptome")) {
            if (ensureTarget(admin, target)) {
                target.teleport(admin.getLocation());
                admin.sendMessage(ok(target, "teleported to you"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_tototarget")) {
            if (ensureTarget(admin, target)) {
                admin.teleport(target.getLocation());
                admin.sendMessage(ValorantMC.colorize("&aTeleported to §f" + target.getName()));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_revive")) {
            if (ensureTarget(admin, target) && game != null) {
                if (target.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    target.setGameMode(org.bukkit.GameMode.ADVENTURE);
                    target.setHealth(20);
                    admin.sendMessage(ok(target, "revived"));
                }
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_cleareffects")) {
            if (ensureTarget(admin, target)) {
                target.clearActivePotionEffects();
                admin.sendMessage(ok(target, "cleared effects"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_speed")) {
            if (ensureTarget(admin, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                admin.sendMessage(ok(target, "speed II (10s)"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_slow")) {
            if (ensureTarget(admin, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 2));
                admin.sendMessage(ok(target, "slowness III (10s)"));
                refreshTroll(admin, target);
            }
            return;
        }

        // ── Map setup actions ────────────────────────────────────────────────
        if (action.startsWith("map_tp_atk:")) {
            int idx = Integer.parseInt(action.substring("map_tp_atk:".length()));
            if (game != null && idx < game.getAttackSpawnsPublic().size()) {
                Location dest = game.getAttackSpawnsPublic().get(idx);
                if (shift) {
                    game.getAttackSpawnsPublic().remove(idx);
                    admin.sendMessage(ValorantMC.colorize("&cRemoved ATK spawn #" + (idx+1) + "."));
                } else {
                    admin.teleport(dest);
                }
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.startsWith("map_tp_def:")) {
            int idx = Integer.parseInt(action.substring("map_tp_def:".length()));
            if (game != null && idx < game.getDefendSpawnsPublic().size()) {
                if (shift) {
                    game.getDefendSpawnsPublic().remove(idx);
                    admin.sendMessage(ValorantMC.colorize("&cRemoved DEF spawn #" + (idx+1) + "."));
                } else {
                    admin.teleport(game.getDefendSpawnsPublic().get(idx));
                }
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.startsWith("map_tp_siteA:")) {
            int idx = Integer.parseInt(action.substring("map_tp_siteA:".length()));
            if (game != null && idx < game.getSiteALocations().size()) {
                admin.teleport(game.getSiteALocations().get(idx));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.startsWith("map_tp_siteB:")) {
            int idx = Integer.parseInt(action.substring("map_tp_siteB:".length()));
            if (game != null && idx < game.getSiteBLocations().size()) {
                admin.teleport(game.getSiteBLocations().get(idx));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.equals("map_add_atk")) {
            if (game != null) {
                Location loc = admin.getLocation().clone();
                game.addAttackSpawn(loc);
                spawnMarker(admin, loc, "§c§l[ATK] Spawn #" + game.getAttackSpawnsPublic().size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added attacker spawn at your location."));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNot in a game. Use /vmapsetup for offline editing."));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.equals("map_add_def")) {
            if (game != null) {
                Location loc = admin.getLocation().clone();
                game.addDefendSpawn(loc);
                spawnMarker(admin, loc, "§b§l[DEF] Spawn #" + game.getDefendSpawnsPublic().size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added defender spawn at your location."));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNot in a game. Use /vmapsetup for offline editing."));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.equals("map_add_siteA")) {
            if (game != null) {
                Location loc = admin.getLocation().clone();
                game.addSiteA(loc);
                spawnMarker(admin, loc, "§6§l[SITE A] #" + game.getSiteALocations().size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added Site A at your location."));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.equals("map_add_siteB")) {
            if (game != null) {
                Location loc = admin.getLocation().clone();
                game.addSiteB(loc);
                spawnMarker(admin, loc, "§a§l[SITE B] #" + game.getSiteBLocations().size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added Site B at your location."));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.equals("map_save")) {
            if (game != null && game.getMapName() != null) {
                saveGameMapToFile(admin, game);
                // Remove glowing markers — the map is saved, setup is complete
                removeMapMarkers(admin);
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active map to save. Use /vmapsetup for offline config."));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }
        if (action.equals("map_clear")) {
            if (game != null) {
                game.clearMapPoints();
                removeMapMarkers(admin);
                admin.sendMessage(ValorantMC.colorize("&cCleared all spawns and sites for the current game."));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        // ── Game control actions ─────────────────────────────────────────────
        if (action.equals("game_end_round_atk")) {
            if (game != null) {
                game.endRound(ValorantTeam.Side.ATTACKERS, "Admin forced");
                admin.closeInventory();
            }
            return;
        }
        if (action.equals("game_end_round_def")) {
            if (game != null) {
                game.endRound(ValorantTeam.Side.DEFENDERS, "Admin forced");
                admin.closeInventory();
            }
            return;
        }
        if (action.equals("game_skip_buy")) {
            if (game != null && game.getState() == com.valorantmc.game.GameState.BUY_PHASE) {
                game.adminSkipBuyPhase();
                admin.sendMessage(ValorantMC.colorize("&aBuy phase skipped."));
                admin.closeInventory();
            }
            return;
        }
        if (action.equals("game_toggle_pause")) {
            if (game != null) {
                if (game.isPaused()) game.resume(); else game.pause();
                admin.openInventory(AdminGUI.buildGameControl(admin, game));
            }
            return;
        }
        if (action.equals("game_end")) {
            if (game != null) {
                game.shutdown();
                plugin.getGameManager().removeGame(game.getId());
                admin.sendMessage(ValorantMC.colorize("&c&lGame ended."));
                admin.closeInventory();
            }
            return;
        }
        if (action.equals("game_refill_all")) {
            if (game != null) {
                game.getAllPlayers().forEach(p -> plugin.getWeaponManager().refillAmmo(p));
                admin.sendMessage(ValorantMC.colorize("&aRefilled ammo for all players."));
                admin.openInventory(AdminGUI.buildGameControl(admin, game));
            }
            return;
        }
        if (action.equals("game_revive_all")) {
            if (game != null) {
                game.getAllPlayers().forEach(p -> {
                    if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                        p.setHealth(20);
                    }
                });
                admin.sendMessage(ValorantMC.colorize("&aRevived all dead players."));
                admin.openInventory(AdminGUI.buildGameControl(admin, game));
            }
            return;
        }
        if (action.equals("game_status")) {
            // Just show info — no action needed, GUI already shows it
            return;
        }
    }

    // ── Give helpers ──────────────────────────────────────────────────────────

    private void doGiveCredits(Player admin, Player target, ValorantGame game, int amount) {
        if (!ensureTarget(admin, target) || game == null) return;
        plugin.getEconomyManager().addCredits(target.getUniqueId(), amount);
        target.sendMessage(ValorantMC.colorize("&6[Admin] §fYou received §6" + amount + " credits§f."));
        admin.sendMessage(ok(target, "+" + amount + " credits"));
        refreshGive(admin, target);
    }

    private void doGiveWeapon(Player admin, Player target, ValorantGame game, String typeName) {
        if (!ensureTarget(admin, target) || game == null) return;
        WeaponType wt;
        try { wt = WeaponType.valueOf(typeName); }
        catch (Exception e) { admin.sendMessage(ValorantMC.colorize("&cUnknown weapon: " + typeName)); return; }

        Weapon w = new Weapon(wt);
        int slot = plugin.getShopManager().getPreferredSlot(wt);
        target.getInventory().setItem(slot, w.toItemStack(target.getUniqueId()));
        plugin.getWeaponManager().setHeldWeapon(target, w);
        target.getInventory().setHeldItemSlot(slot);
        target.sendMessage(ValorantMC.colorize("&6[Admin] §fYou received §b" + wt.getDisplayName() + "§f."));
        admin.sendMessage(ok(target, wt.getDisplayName()));
        refreshGive(admin, target);
    }

    private void doGiveUlt(Player admin, Player target, ValorantGame game, int points) {
        if (!ensureTarget(admin, target) || game == null) return;
        com.valorantmc.agents.Agent agent = game.getAgent(target);
        if (agent == null) { admin.sendMessage(ValorantMC.colorize("&cTarget has no agent.")); return; }
        if (points == Integer.MAX_VALUE) {
            agent.fillUlt();
        } else {
            for (int i = 0; i < points; i++) agent.getAbilityX().addUltPoint();
        }
        agent.giveAbilityItems(target);
        target.sendMessage(ValorantMC.colorize("&6[Admin] §fYou received ult points."));
        admin.sendMessage(ok(target, "ult points"));
        refreshGive(admin, target);
    }

    private void doGiveAbility(Player admin, Player target, ValorantGame game, char key) {
        if (!ensureTarget(admin, target) || game == null) return;
        com.valorantmc.agents.Agent agent = game.getAgent(target);
        if (agent == null) { admin.sendMessage(ValorantMC.colorize("&cTarget has no agent.")); return; }
        com.valorantmc.agents.Agent.Ability ability = switch (key) {
            case 'C' -> agent.getAbilityC();
            case 'Q' -> agent.getAbilityQ();
            case 'E' -> agent.getAbilityE();
            default  -> null;
        };
        if (ability == null) { admin.sendMessage(ValorantMC.colorize("&cNo ability " + key + ".")); return; }
        ability.addCharge();
        agent.giveAbilityItems(target);
        target.sendMessage(ValorantMC.colorize("&6[Admin] §fYou received 1 charge of ability §b" + key + "§f."));
        admin.sendMessage(ok(target, "ability " + key));
        refreshGive(admin, target);
    }

    // ── Map save helper ───────────────────────────────────────────────────────

    private void saveGameMapToFile(Player admin, ValorantGame game) {
        String mapName = game.getMapName();
        java.util.List<String> atkList = new java.util.ArrayList<>();
        java.util.List<String> defList = new java.util.ArrayList<>();
        java.util.List<String> siteAList = new java.util.ArrayList<>();
        java.util.List<String> siteBList = new java.util.ArrayList<>();

        for (Location l : game.getAttackSpawnsPublic())
            atkList.add(l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ",0,0");
        for (Location l : game.getDefendSpawnsPublic())
            defList.add(l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ",0,0");
        for (Location l : game.getSiteALocations())
            siteAList.add(l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        for (Location l : game.getSiteBLocations())
            siteBList.add(l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());

        String worldName = game.getAttackSpawnsPublic().isEmpty()
                ? Bukkit.getWorlds().get(0).getName()
                : game.getAttackSpawnsPublic().get(0).getWorld().getName();

        plugin.getMapManager().saveSessionToFile(mapName, worldName,
                atkList, defList, siteAList, siteBList);
        plugin.getMapManager().reloadMaps();
        admin.sendMessage(ValorantMC.colorize("&a&lMap §f" + mapName + " §a§lsaved and reloaded!"));
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private boolean ensureTarget(Player admin, Player target) {
        if (target == null || !target.isOnline()) {
            admin.sendMessage(ValorantMC.colorize("&cPlayer not found or offline."));
            return false;
        }
        return true;
    }

    private String ok(Player target, String action) {
        return ValorantMC.colorize("&a✔ §f" + action + " §7→ §e" + target.getName());
    }

    private void refreshGive(Player admin, Player target) {
        if (target != null && target.isOnline())
            admin.openInventory(AdminGUI.buildGive(admin, target));
    }

    private void refreshTroll(Player admin, Player target) {
        if (target != null && target.isOnline())
            admin.openInventory(AdminGUI.buildTroll(admin, target));
    }

    // ── Map setup helpers ─────────────────────────────────────────────────────

    /**
     * Spawn a glowing, invisible ArmorStand at the given location as a visual
     * marker for the admin during map setup.
     */
    private void spawnMarker(Player admin, Location loc, String label) {
        Location markerLoc = loc.clone().add(0, 0, 0);
        markerLoc.getWorld().spawn(markerLoc, ArmorStand.class, stand -> {
            stand.setGravity(false);
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setCustomName(label);
            stand.setCustomNameVisible(true);
            stand.setGlowing(true);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            mapMarkers.computeIfAbsent(admin.getUniqueId(), k -> new ArrayList<>()).add(stand);
        });
    }

    /** Remove all glowing marker ArmorStands for the given admin. */
    private void removeMapMarkers(Player admin) {
        List<ArmorStand> markers = mapMarkers.remove(admin.getUniqueId());
        if (markers != null) markers.forEach(org.bukkit.entity.Entity::remove);
    }

    /** Restore the admin's previous gamemode after leaving map setup. */
    private void restoreGameMode(Player admin) {
        GameMode prev = mapSetupModes.remove(admin.getUniqueId());
        if (prev != null) {
            admin.setGameMode(prev);
            if (prev != GameMode.CREATIVE && prev != GameMode.SPECTATOR) {
                admin.setAllowFlight(false);
                admin.setFlying(false);
            }
            admin.sendMessage(ValorantMC.colorize("&b[Map Setup] &fRestored gamemode: &e" + prev.name()));
        }
    }
}
