package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages the in-game HUD for every active player:
 *
 *   XP bar    — current ammo as a fill % (level = current ammo count)
 *   Action bar— ❤ HP  🛡 SHIELD  |  [C ●●] [Q ●] [E ●] [X 3/7]
 *
 * Refreshes every 4 ticks (200 ms) so it stays snappy without eating CPU.
 * Plant/defuse progress bars override the action bar per-tick — that's fine
 * because those runnables fire every 4 ticks too and send their own
 * sendActionBar calls which take priority (last write wins, same tick).
 */
public class HudManager {

    private final ValorantMC plugin;
    private BukkitTask task;

    public HudManager(ValorantMC plugin) {
        this.plugin = plugin;
    }

    /** Start the repeating HUD update task. Call once from ValorantMC#onEnable. */
    public void start() {
        if (task != null) task.cancel();
        task = new BukkitRunnable() {
            @Override public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    ValorantGame game = plugin.getGameManager().getGame(player);
                    if (game == null) continue;

                    updateAmmoBar(player);
                    updateActionBar(player, game);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /** Stop the task (call on plugin disable or server shutdown). */
    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    // ── XP bar = ammo ────────────────────────────────────────────────────────

    private void updateAmmoBar(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        WeaponType type = Weapon.getWeaponType(held);

        if (type == null || type.isMelee()) {
            // No gun held — reset bar to full so it doesn't linger
            player.setExp(1f);
            player.setLevel(0);
            return;
        }

        Weapon weapon = plugin.getWeaponManager().getHeldWeapon(player);
        int current = weapon != null ? weapon.getCurrentAmmo()  : Weapon.getStoredAmmo(held);
        int reserve = weapon != null ? weapon.getReserveAmmo()  : Weapon.getStoredReserve(held);
        int mag     = type.getMagazineSize();

        if (current < 0) current = mag;  // fallback for fresh NBT-less items
        if (reserve < 0) reserve = 0;

        float fill  = mag > 0 ? (float) current / mag : 1f;
        player.setExp(Math.max(0f, Math.min(1f, fill)));
        player.setLevel(current);  // shows current ammo count as the level number
    }

    // ── Action bar = HP | Shield | Abilities ────────────────────────────────

    private void updateActionBar(Player player, ValorantGame game) {
        int hp     = game.getHealth(player);
        int shield = game.getShield(player);

        // HP colour: green >50, yellow 25-50, red <25
        String hpColor  = hp > 50 ? "&a" : hp > 25 ? "&e" : "&c";
        String shColor  = shield > 0 ? "&b" : "&8";

        StringBuilder sb = new StringBuilder();
        sb.append(hpColor).append("❤ ").append(hp);
        sb.append("  ").append(shColor).append("🛡 ").append(shield);

        // Ability charges
        Agent agent = game.getAgent(player);
        if (agent != null) {
            sb.append("  &7|");
            sb.append(abilityCharge(agent.getAbilityC(), "C", "&6"));
            sb.append(abilityCharge(agent.getAbilityQ(), "Q", "&e"));
            sb.append(abilityCharge(agent.getAbilityE(), "E", "&a"));
            sb.append(ultCharge(agent.getAbilityX()));
        }

        // Also show reloading indicator
        if (plugin.getWeaponManager().isReloading(player)) {
            sb.append("  &7RELOADING&f...");
        }

        player.sendActionBar(ValorantMC.colorize(sb.toString()));
    }

    /** Renders a single ability slot, e.g.  [C ●●]  or  [C ○] */
    private String abilityCharge(Agent.Ability a, String key, String color) {
        if (a == null) return "";
        StringBuilder sb = new StringBuilder("  ").append(color).append("[").append(key).append(" ");
        if (a.isOnCooldown()) {
            // Show remaining cooldown instead of dots
            sb.append(String.format("&f%.1fs", a.getCooldownSeconds()));
        } else {
            int total   = Math.max(1, a.charges);
            int current = a.getCurrentCharges();
            for (int i = 0; i < total; i++) {
                sb.append(i < current ? "●" : "&8○").append(color);
            }
        }
        sb.append("&r").append(color).append("]");
        return sb.toString();
    }

    /** Renders the ultimate slot, e.g.  [X ●●●○○○○] or [X READY!] */
    private String ultCharge(Agent.Ability ult) {
        if (ult == null) return "";
        if (ult.ultimatePoints <= 0) return "";

        StringBuilder sb = new StringBuilder("  &6[X ");
        if (ult.isUltReady()) {
            sb.append("&l&6READY!");
        } else {
            int prog  = ult.getUltimateProgress();
            int total = ult.ultimatePoints;
            for (int i = 0; i < total; i++) {
                sb.append(i < prog ? "&6●" : "&8○");
            }
        }
        sb.append("&r&6]");
        return sb.toString();
    }
}
