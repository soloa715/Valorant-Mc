package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * REYNA — Duelist
 *
 * C – Leer:    Shoot an eye that nearsights enemies (250c)
 * Q – Devour:  Consume a soul orb to overheal (free after kill)
 * E – Dismiss: Consume a soul orb to briefly go invulnerable (free after kill)
 * X – Empress: Enhanced frenzy mode (6 ult points)
 */
public class Reyna extends Agent {

    private int soulOrbs = 0;

    public Reyna() {
        super("reyna", "Reyna", AgentRole.DUELIST);
        abilityC = new Ability("Leer",     250, 2, 0);
        abilityQ = new Ability("Devour",     0, 0, 0);  // charges granted by kills
        abilityE = new Ability("Dismiss",    0, 0, 0);
        abilityX = new Ability("Empress",    0, 1, 6);
    }

    /** C – Leer: flash nearby enemies (blind + nausea briefly) */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&cLeer has no charges!")); return; }
        abilityC.consume();

        Location eye = player.getTargetBlock(null, 15).getLocation().add(0.5, 1, 0.5);
        player.getWorld().spawnParticle(Particle.CRIT_MAGIC, eye, 30, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().playSound(eye, Sound.ENTITY_ENDERMAN_STARE, 0.7f, 1.5f);

        // Blind enemies in range
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player)) continue;
            if (game.getTeam(nearby) == null) continue;
            if (game.getTeam(nearby).getSide().equals(game.getTeam(player).getSide())) continue;
            if (nearby.getLocation().distance(eye) <= 8) {
                nearby.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,  30, 0, false, false));
                nearby.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0, false, false));
                nearby.sendActionBar(ValorantMC.colorize("&c[Leer] &fYou've been near-sighted!"));
            }
        }
        player.sendMessage(ValorantMC.colorize("&c[Reyna] &fLeer cast!"));
    }

    /** Q – Devour: consume soul orb to overheal */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (soulOrbs <= 0) { player.sendMessage(ValorantMC.colorize("&cNo soul orbs! Get a kill first.")); return; }
        soulOrbs--;

        // Heal + overheal up to 150hp
        int current = game.getHealth(player);
        int healed  = Math.min(100, 150 - current);
        // We apply healing via negative damage (hack in ValorantGame.applyDamage)
        // but Devour over-heals above 100, so do it manually:
        player.setHealth(Math.min(20, player.getHealth() + healed / 5.0));
        player.sendMessage(ValorantMC.colorize("&c[Reyna] &fDevoured! +" + healed + "hp"));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
    }

    /** E – Dismiss: consume soul orb to become briefly invulnerable */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (soulOrbs <= 0) { player.sendMessage(ValorantMC.colorize("&cNo soul orbs! Get a kill first.")); return; }
        soulOrbs--;

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 255, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 50, 0.5, 1, 0.5, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.sendMessage(ValorantMC.colorize("&c[Reyna] &fDismissed!"));
    }

    /** X – Empress: enhanced frenzy — speed, fire-rate boost, auto-soul on kill */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cEmpress not ready!")); return; }
        abilityX.activateUlt();

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,       200, 2, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,       200, 2, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1, false, false));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f);
        game.broadcast(ValorantMC.colorize("&c[Reyna] &f" + player.getName() + " activated &cEmpress&f!"));

        // Mark empress active (AbilityListener uses this to auto-grant soul orbs on kill)
        player.getPersistentDataContainer().set(
                new NamespacedKey(ValorantMC.getInstance(), "empress"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);

        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            player.getPersistentDataContainer().remove(new NamespacedKey(ValorantMC.getInstance(), "empress"));
            player.sendMessage(ValorantMC.colorize("&cEmpress faded."));
        }, 200L);
    }

    @Override
    public void onKill(Player player, Player victim, ValorantGame game) {
        super.onKill(player, victim, game);
        soulOrbs = Math.min(soulOrbs + 1, 4);
        abilityQ.resetCharges();
        abilityE.resetCharges();
        player.sendMessage(ValorantMC.colorize("&c[Reyna] &fSoul Orb gained! (" + soulOrbs + " total)"));
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
    }

    public int getSoulOrbs() { return soulOrbs; }
}
