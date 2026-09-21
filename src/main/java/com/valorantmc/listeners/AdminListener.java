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
import org.bukkit.event.player.PlayerQuitEvent;
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
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        NamespacedKey nskWand = new NamespacedKey(plugin, "map_wand");
        boolean isWand = meta.getPersistentDataContainer().has(nskWand, PersistentDataType.BOOLEAN)
                || (meta.hasDisplayName() && meta.getDisplayName().contains("Map Wand"));
        if (!isWand) return;

        org.bukkit.block.Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        com.valorantmc.commands.MapSetupCommand setupCmd =
            (com.valorantmc.commands.MapSetupCommand) Objects.requireNonNull(plugin.getCommand("vmapsetup")).getExecutor();
        com.valorantmc.commands.MapSetupCommand.SetupSession s = setupCmd.getActiveSession(p);
        if (s == null) {
            p.sendMessage(ValorantMC.colorize("&c[Map Wand] No active map setup session! Use /vmapsetup edit <map> first."));
            return;
        }

        e.setCancelled(true);
        Location loc = clicked.getLocation();
        String coordStr = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

        if (e.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            s.pos1Str = coordStr;
            spawnMarker(p, loc.clone().add(0.5, 1.0, 0.5), "§a§l[POS 1]");
            p.sendMessage(ValorantMC.colorize("&a&l[Map Wand] Pos1 set to: &e" + coordStr));
            p.sendMessage(ValorantMC.colorize("&7Right-click another block for Pos2, then run &b/vmapsetup setsite <a|b>&7."));
        } else if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            s.pos2Str = coordStr;
            spawnMarker(p, loc.clone().add(0.5, 1.0, 0.5), "§b§l[POS 2]");
            p.sendMessage(ValorantMC.colorize("&b&l[Map Wand] Pos2 set to: &e" + coordStr));
            p.sendMessage(ValorantMC.colorize("&7Run &b/vmapsetup setsite a &7or &b/vmapsetup setsite b &7to save 3D region!"));
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
            // Check if player has an active setup session; if not, try auto-editing current world's map
            try {
                com.valorantmc.commands.MapSetupCommand setupCmd =
                    (com.valorantmc.commands.MapSetupCommand) Objects.requireNonNull(plugin.getCommand("vmapsetup")).getExecutor();
                com.valorantmc.commands.MapSetupCommand.SetupSession s = setupCmd.getActiveSession(admin);
                if (s == null && (game == null || game.getMapName() == null)) {
                    String wName = admin.getWorld().getName();
                    for (String name : plugin.getMapManager().getMapNames()) {
                        com.valorantmc.managers.MapManager.ValorantMap vm = plugin.getMapManager().getMap(name);
                        if (vm != null && ((vm.getOriginWorld() != null && vm.getOriginWorld().equalsIgnoreCase(wName)) || name.equalsIgnoreCase(wName) || wName.toLowerCase().endsWith("_" + name.toLowerCase()))) {
                            admin.performCommand("vmapsetup edit " + vm.getName());
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

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
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 254));
                target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 200, 128));
                admin.sendMessage(ok(target, "freeze (10s)"));
                refreshTroll(admin, target);
            }
            return;
        }
        if (action.equals("troll_unfreeze")) {
            if (ensureTarget(admin, target)) {
                target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
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
                target.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 160, 0));
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
                target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
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
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 2));
                admin.sendMessage(ok(target, "slowness III (10s)"));
                refreshTroll(admin, target);
            }
            return;
        }

        // ── Map setup actions ────────────────────────────────────────────────
        com.valorantmc.commands.MapSetupCommand setupCmd = null;
        com.valorantmc.commands.MapSetupCommand.SetupSession s = null;
        try {
            setupCmd = (com.valorantmc.commands.MapSetupCommand) Objects.requireNonNull(plugin.getCommand("vmapsetup")).getExecutor();
            s = setupCmd.getActiveSession(admin);
        } catch (Exception ignored) {}

        if (action.startsWith("map_tp_atk:")) {
            int idx = Integer.parseInt(action.substring("map_tp_atk:".length()));
            if (s != null && idx < s.attackSpawns.size()) {
                if (shift) {
                    s.attackSpawns.remove(idx);
                    admin.sendMessage(ValorantMC.colorize("&cRemoved ATK spawn #" + (idx+1) + "."));
                } else {
                    World w = Bukkit.getWorld(s.worldName); if (w == null) w = admin.getWorld();
                    Location loc = AdminGUI.parseSpawnLoc(w, s.attackSpawns.get(idx));
                    if (loc != null) admin.teleport(loc);
                }
            } else if (game != null && idx < game.getAttackSpawnsPublic().size()) {
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
            if (s != null && idx < s.defendSpawns.size()) {
                if (shift) {
                    s.defendSpawns.remove(idx);
                    admin.sendMessage(ValorantMC.colorize("&cRemoved DEF spawn #" + (idx+1) + "."));
                } else {
                    World w = Bukkit.getWorld(s.worldName); if (w == null) w = admin.getWorld();
                    Location loc = AdminGUI.parseSpawnLoc(w, s.defendSpawns.get(idx));
                    if (loc != null) admin.teleport(loc);
                }
            } else if (game != null && idx < game.getDefendSpawnsPublic().size()) {
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
            if (s != null && idx < s.siteA.size()) {
                if (shift) {
                    s.siteA.remove(idx);
                    admin.sendMessage(ValorantMC.colorize("&cRemoved Site A #" + (idx+1) + "."));
                } else {
                    World w = Bukkit.getWorld(s.worldName); if (w == null) w = admin.getWorld();
                    Location loc = AdminGUI.parseBlockLoc(w, s.siteA.get(idx));
                    if (loc != null) admin.teleport(loc);
                }
            } else if (game != null && idx < game.getSiteALocations().size()) {
                if (shift) {
                    game.getSiteALocations().remove(idx);
                    admin.sendMessage(ValorantMC.colorize("&cRemoved Site A #" + (idx+1) + "."));
                } else {
                    admin.teleport(game.getSiteALocations().get(idx));
                }
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.startsWith("map_tp_siteB:")) {
            int idx = Integer.parseInt(action.substring("map_tp_siteB:".length()));
            if (s != null && idx < s.siteB.size()) {
                if (shift) {
                    s.siteB.remove(idx);
                    admin.sendMessage(ValorantMC.colorize("&cRemoved Site B #" + (idx+1) + "."));
                } else {
                    World w = Bukkit.getWorld(s.worldName); if (w == null) w = admin.getWorld();
                    Location loc = AdminGUI.parseBlockLoc(w, s.siteB.get(idx));
                    if (loc != null) admin.teleport(loc);
                }
            } else if (game != null && idx < game.getSiteBLocations().size()) {
                if (shift) {
                    game.getSiteBLocations().remove(idx);
                    admin.sendMessage(ValorantMC.colorize("&cRemoved Site B #" + (idx+1) + "."));
                } else {
                    admin.teleport(game.getSiteBLocations().get(idx));
                }
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_get_wand")) {
            admin.performCommand("vmapsetup wand");
            admin.closeInventory();
            return;
        }

        if (action.equals("map_set_pos1")) {
            if (s != null) {
                org.bukkit.block.Block tb = admin.getTargetBlockExact(50);
                Location loc = tb != null ? tb.getLocation() : admin.getLocation();
                s.pos1Str = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                spawnMarker(admin, loc.clone().add(0.5, 1.0, 0.5), "§a§l[POS 1]");
                admin.sendMessage(ValorantMC.colorize("&aPos1 set to: &e" + s.pos1Str));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active setup session. Use /vmapsetup edit <map> first!"));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_set_pos2")) {
            if (s != null) {
                org.bukkit.block.Block tb = admin.getTargetBlockExact(50);
                Location loc = tb != null ? tb.getLocation() : admin.getLocation();
                s.pos2Str = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                spawnMarker(admin, loc.clone().add(0.5, 1.0, 0.5), "§b§l[POS 2]");
                admin.sendMessage(ValorantMC.colorize("&bPos2 set to: &e" + s.pos2Str));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active setup session. Use /vmapsetup edit <map> first!"));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_setsite_a")) {
            if (s != null) {
                if (s.pos1Str == null || s.pos2Str == null) {
                    admin.sendMessage(ValorantMC.colorize("&cSet both Pos1 and Pos2 first! (Left & Right click blocks with Map Wand)"));
                } else {
                    s.siteA.clear();
                    s.siteA.add(s.pos1Str);
                    s.siteA.add(s.pos2Str);
                    admin.sendMessage(ValorantMC.colorize("&a&lSet Site A region from &e" + s.pos1Str + " &ato &e" + s.pos2Str + "&a!"));
                }
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_setsite_b")) {
            if (s != null) {
                if (s.pos1Str == null || s.pos2Str == null) {
                    admin.sendMessage(ValorantMC.colorize("&cSet both Pos1 and Pos2 first! (Left & Right click blocks with Map Wand)"));
                } else {
                    s.siteB.clear();
                    s.siteB.add(s.pos1Str);
                    s.siteB.add(s.pos2Str);
                    admin.sendMessage(ValorantMC.colorize("&a&lSet Site B region from &e" + s.pos1Str + " &ato &e" + s.pos2Str + "&a!"));
                }
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_radius_inc") || action.equals("map_radius_dec")) {
            if (s == null && game != null && game.getMapName() != null && setupCmd != null) {
                s = setupCmd.getOrCreateSession(admin, game.getMapName());
            }
            if (s != null) {
                if (action.equals("map_radius_inc")) s.siteRadius = Math.min(30.0, s.siteRadius + 1.0);
                else s.siteRadius = Math.max(1.0, s.siteRadius - 1.0);
                admin.sendMessage(ValorantMC.colorize("&aSite zone radius set to: &b" + s.siteRadius + " meters&a!"));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active setup session. Use /vmapsetup edit <map> first!"));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_add_atk")) {
            Location loc = admin.getLocation().clone();
            org.bukkit.block.Block tb = admin.getTargetBlockExact(50);
            if (tb != null) {
                loc = tb.getLocation().add(0.5, 1.0, 0.5);
                loc.setYaw(admin.getLocation().getYaw());
                loc.setPitch(admin.getLocation().getPitch());
            }
            String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                    + "," + Math.round(loc.getYaw()) + "," + Math.round(loc.getPitch());

            if (s != null) {
                s.attackSpawns.add(entry);
                spawnMarker(admin, loc, "§c§l[ATK] Spawn #" + s.attackSpawns.size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added ATK spawn #" + s.attackSpawns.size() + " to map session '" + s.mapName + "'."));
            } else if (game != null) {
                game.addAttackSpawn(loc);
                spawnMarker(admin, loc, "§c§l[ATK] Spawn #" + game.getAttackSpawnsPublic().size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added ATK spawn to current game."));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active setup session. Use /vmapsetup edit <map> first!"));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_add_def")) {
            Location loc = admin.getLocation().clone();
            org.bukkit.block.Block tb = admin.getTargetBlockExact(50);
            if (tb != null) {
                loc = tb.getLocation().add(0.5, 1.0, 0.5);
                loc.setYaw(admin.getLocation().getYaw());
                loc.setPitch(admin.getLocation().getPitch());
            }
            String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                    + "," + Math.round(loc.getYaw()) + "," + Math.round(loc.getPitch());

            if (s != null) {
                s.defendSpawns.add(entry);
                spawnMarker(admin, loc, "§b§l[DEF] Spawn #" + s.defendSpawns.size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added DEF spawn #" + s.defendSpawns.size() + " to map session '" + s.mapName + "'."));
            } else if (game != null) {
                game.addDefendSpawn(loc);
                spawnMarker(admin, loc, "§b§l[DEF] Spawn #" + game.getDefendSpawnsPublic().size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added DEF spawn to current game."));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active setup session. Use /vmapsetup edit <map> first!"));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_add_siteA")) {
            Location loc = admin.getLocation().clone();
            org.bukkit.block.Block tb = admin.getTargetBlockExact(50);
            if (tb != null) {
                loc = tb.getLocation().add(0.5, 1.0, 0.5);
            }
            String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

            if (s != null) {
                s.siteA.add(entry);
                spawnMarker(admin, loc, "§6§l[SITE A] #" + s.siteA.size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added Site A #" + s.siteA.size() + " to map session '" + s.mapName + "'."));
            } else if (game != null) {
                game.addSiteA(loc);
                spawnMarker(admin, loc, "§6§l[SITE A] #" + game.getSiteALocations().size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added Site A to current game."));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active setup session. Use /vmapsetup edit <map> first!"));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_add_siteB")) {
            Location loc = admin.getLocation().clone();
            org.bukkit.block.Block tb = admin.getTargetBlockExact(50);
            if (tb != null) {
                loc = tb.getLocation().add(0.5, 1.0, 0.5);
            }
            String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

            if (s != null) {
                s.siteB.add(entry);
                spawnMarker(admin, loc, "§a§l[SITE B] #" + s.siteB.size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added Site B #" + s.siteB.size() + " to map session '" + s.mapName + "'."));
            } else if (game != null) {
                game.addSiteB(loc);
                spawnMarker(admin, loc, "§a§l[SITE B] #" + game.getSiteBLocations().size());
                admin.sendMessage(ValorantMC.colorize("&a+ Added Site B to current game."));
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active setup session. Use /vmapsetup edit <map> first!"));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_save")) {
            if (s != null) {
                if (s.attackSpawns.size() < 2 || s.defendSpawns.size() < 2 || s.siteA.isEmpty() || s.siteB.isEmpty()) {
                    admin.sendMessage(ValorantMC.colorize("&cCannot save — map requires at least 2 ATK, 2 DEF, 1 Site A, and 1 Site B!"));
                } else {
                    plugin.getMapManager().saveSessionToFile(s.mapName, s.worldName,
                            s.attackSpawns, s.defendSpawns, s.siteA, s.siteB, s.siteRadius);
                    plugin.getMapManager().reloadMaps();
                    if (setupCmd != null) setupCmd.removeSession(admin);
                    removeMapMarkers(admin);
                    admin.sendMessage(ValorantMC.colorize("&a&lMap '" + s.mapName + "' saved and loaded successfully!"));
                }
            } else if (game != null && game.getMapName() != null) {
                saveGameMapToFile(admin, game);
                removeMapMarkers(admin);
            } else {
                admin.sendMessage(ValorantMC.colorize("&cNo active setup session to save. Use /vmapsetup edit <map> first."));
            }
            admin.openInventory(AdminGUI.buildMapSetup(admin, game));
            return;
        }

        if (action.equals("map_clear")) {
            if (s != null) {
                s.attackSpawns.clear();
                s.defendSpawns.clear();
                s.siteA.clear();
                s.siteB.clear();
                removeMapMarkers(admin);
                admin.sendMessage(ValorantMC.colorize("&cCleared all spawns and sites for setup session '" + s.mapName + "'."));
            } else if (game != null) {
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
        plugin.getWeaponManager().giveTaCZWeapon(target, wt, slot);
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

    /** Remove marker ArmorStands and restore gamemode when an admin disconnects mid-setup. */
    @EventHandler
    public void onAdminQuit(PlayerQuitEvent e) {
        Player admin = e.getPlayer();
        removeMapMarkers(admin);
        mapSetupModes.remove(admin.getUniqueId());
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
