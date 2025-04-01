package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EffectManager {
    private final OniGo plugin;
    private final TeamManager teamManager;

    private Player activeYashaPlayer;
    private BukkitTask fadeTask;
    private BukkitTask reverseFadeTask;
    private int currentDarkness = 0;

    // Sound constants
    public static final String ONISONG1_SOUND = "minecraft:onisong1";
    public static final String ONISONG2_SOUND = "minecraft:onisong2";

    // Kakure Dama (hiding orb) effect tracking
    private Map<UUID, Integer> kakureDamaRemaining = new HashMap<>();
    private Map<UUID, BukkitTask> kakureDamaTask = new HashMap<>();

    // Oni slowness effect task
    private BukkitTask oniSlownessTask;

    public EffectManager(OniGo plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    /**
     * Start the Yasha (demon) effect
     */
    public void startYashaEffect(Player player) {
        activeYashaPlayer = player;

        // If this is during a game, move player to oni team
        if (plugin.isGameRunning() && teamManager.isPlayerInPlayerTeam(player)) {
            teamManager.movePlayerToOniTeam(player);
            player.sendMessage(ChatColor.RED + "夜叉になったから、鬼陣営に移ったよ！");
        }

        // Play sound to all players
        for (Player p : player.getWorld().getPlayers()) {
            p.playSound(p.getLocation(), ONISONG1_SOUND, 1.0f, 1.0f);
        }

        // Start darkness effect
        currentDarkness = 0;
        fadeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentDarkness < 2) {
                    currentDarkness++;
                }
                for (Player p : player.getWorld().getPlayers()) {
                    // Don't apply darkness to oni team
                    if (teamManager.isPlayerInOniTeam(p)) {
                        continue;
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, currentDarkness, false, false));
                    p.playSound(activeYashaPlayer.getLocation(), ONISONG2_SOUND, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 60L);
    }

    /**
     * Stop the Yasha (demon) effect
     */
    public void stopYashaEffect() {
        // Cancel old tasks
        if (fadeTask != null) {
            fadeTask.cancel();
            fadeTask = null;
        }

        if (reverseFadeTask != null) {
            reverseFadeTask.cancel();
            reverseFadeTask = null;
        }

        // Start reverse fade (removing darkness gradually)
        reverseFadeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentDarkness > 0) {
                    currentDarkness--;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p != null && p.isOnline()) {
                            // Don't affect oni team
                            if (teamManager.isPlayerInOniTeam(p)) {
                                continue;
                            }
                            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, currentDarkness, false, false));
                        }
                    }
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p != null && p.isOnline()) {
                            // Don't affect oni team
                            if (teamManager.isPlayerInOniTeam(p)) {
                                continue;
                            }
                            p.removePotionEffect(PotionEffectType.DARKNESS);
                            p.stopSound(ONISONG1_SOUND);
                            p.stopSound(ONISONG2_SOUND);
                        }
                    }
                    // Cancel task and clean up
                    BukkitTask taskToCancel = reverseFadeTask;
                    reverseFadeTask = null;
                    if (taskToCancel != null) {
                        taskToCancel.cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 60L);

        activeYashaPlayer = null;
    }

    /**
     * Start the kakure dama (hiding orb) effect - invisibility
     */
    public void startKakureDamaEffect(Player player) {
        int remainingTime = kakureDamaRemaining.getOrDefault(player.getUniqueId(), 0);
        if (remainingTime <= 0) {
            player.sendMessage(ChatColor.RED + "隠れ玉の使用時間がなくなったよ！");
            return;
        }

        // Apply invisibility and slowness
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 999999, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 1, false, false));
        player.sendMessage(ChatColor.AQUA + "透明化したよ！残り " + remainingTime + "秒");

        // Start countdown timer
        BukkitTask task = new BukkitRunnable() {
            int timeLeft = remainingTime;
            @Override
            public void run() {
                timeLeft--;
                kakureDamaRemaining.put(player.getUniqueId(), timeLeft);
                plugin.updateScoreboard(); // Update timer on scoreboard

                if (timeLeft <= 0) {
                    stopKakureDamaEffect(player);
                    player.sendMessage(ChatColor.RED + "隠れ玉の効果が切れたよ！");
                    this.cancel();
                    kakureDamaTask.remove(player.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        kakureDamaTask.put(player.getUniqueId(), task);
    }

    /**
     * Stop the kakure dama (hiding orb) effect
     */
    public void stopKakureDamaEffect(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        // Cancel task if exists
        if (kakureDamaTask.containsKey(player.getUniqueId())) {
            kakureDamaTask.get(player.getUniqueId()).cancel();
            kakureDamaTask.remove(player.getUniqueId());
        }
    }

    /**
     * Start slowness effect for oni team members
     */
    public void startOniSlownessTask() {
        if (oniSlownessTask != null) {
            oniSlownessTask.cancel();
        }

        oniSlownessTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isGameRunning()) return;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (teamManager.isPlayerInOniTeam(p)) {
                        // Apply slowness II (level 2) effect
                        if (!p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                            p.addPotionEffect(new PotionEffect(
                                    PotionEffectType.SLOWNESS,
                                    999999,
                                    2, // Level 2 slowness
                                    false, false
                            ));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Check every second
    }

    /**
     * Clear all effects for all players
     */
    public void clearAllEffects() {
        // Cancel all tasks
        if (fadeTask != null) {
            fadeTask.cancel();
            fadeTask = null;
        }

        if (reverseFadeTask != null) {
            reverseFadeTask.cancel();
            reverseFadeTask = null;
        }

        if (oniSlownessTask != null) {
            oniSlownessTask.cancel();
            oniSlownessTask = null;
        }

        // Cancel kakure dama tasks
        for (UUID uuid : new ArrayList<>(kakureDamaTask.keySet())) {
            BukkitTask task = kakureDamaTask.get(uuid);
            if (task != null) {
                task.cancel();
            }
        }
        kakureDamaTask.clear();

        // Remove effects from all players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null && p.isOnline()) {
                clearAllPotionEffects(p);
                p.stopSound(ONISONG1_SOUND);
                p.stopSound(ONISONG2_SOUND);
                p.setFoodLevel(20); // Reset food level
            }
        }
    }

    /**
     * Clear all potion effects from a player
     */
    public void clearAllPotionEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    /**
     * Initialize kakure dama timers for all players
     */
    public void initializeKakureDama(int initialTime) {
        kakureDamaRemaining.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                kakureDamaRemaining.put(p.getUniqueId(), initialTime);
            }
        }
    }

    // Getters and setters
    public Map<UUID, Integer> getKakureDamaRemaining() {
        return kakureDamaRemaining;
    }

    public Player getActiveYashaPlayer() {
        return activeYashaPlayer;
    }

    public boolean isYashaActive() {
        return fadeTask != null || reverseFadeTask != null;
    }
}
