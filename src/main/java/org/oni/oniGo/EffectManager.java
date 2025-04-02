package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

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

    // Kakure Dama (隠れ玉)
    private Map<UUID, Integer> kakureDamaRemaining = new HashMap<>();
    private Map<UUID, BukkitTask> kakureDamaTask = new HashMap<>();

    // 鬼用スロウタスク
    private BukkitTask oniSlownessTask;

    public EffectManager(OniGo plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    /**
     * 夜叉化
     */
    public void startYashaEffect(Player player) {
        activeYashaPlayer = player;

        // ゲーム中なら鬼チームへ移動
        if (plugin.isGameRunning() && teamManager.isPlayerInPlayerTeam(player)) {
            teamManager.movePlayerToOniTeam(player);
            player.sendMessage(ChatColor.RED + "夜叉化されたので鬼チームへ移動！");
        }

        // 全員に音を再生
        for (Player p : player.getWorld().getPlayers()) {
            p.playSound(p.getLocation(), ONISONG1_SOUND, 1.0f, 1.0f);
        }

        // 闇効果フェードイン
        currentDarkness = 0;
        fadeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentDarkness < 2) {
                    currentDarkness++;
                }
                for (Player p : player.getWorld().getPlayers()) {
                    // 鬼チームには暗闇を与えない
                    if (teamManager.isPlayerInOniTeam(p)) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, currentDarkness, false, false));
                    p.playSound(activeYashaPlayer.getLocation(), ONISONG2_SOUND, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 60L);
    }

    /**
     * 夜叉効果終了
     */
    public void stopYashaEffect() {
        if (fadeTask != null) {
            fadeTask.cancel();
            fadeTask = null;
        }
        if (reverseFadeTask != null) {
            reverseFadeTask.cancel();
            reverseFadeTask = null;
        }
        // 徐々に闇効果解除
        reverseFadeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentDarkness > 0) {
                    currentDarkness--;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (teamManager.isPlayerInOniTeam(p)) continue;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, currentDarkness, false, false));
                    }
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (teamManager.isPlayerInOniTeam(p)) continue;
                        p.removePotionEffect(PotionEffectType.DARKNESS);
                        p.stopSound(ONISONG1_SOUND);
                        p.stopSound(ONISONG2_SOUND);
                    }
                    cancel();
                    reverseFadeTask = null;
                }
            }
        }.runTaskTimer(plugin, 0L, 60L);
        activeYashaPlayer = null;
    }

    /**
     * 隠れ玉スタート
     */
    public void startKakureDamaEffect(Player player) {
        int remainingTime = kakureDamaRemaining.getOrDefault(player.getUniqueId(), 0);
        if (remainingTime <= 0) {
            player.sendMessage(ChatColor.RED + "隠れ玉の使用時間が残ってないよ！");
            return;
        }
        // 透明化＆スロー
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 999999, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 1, false, false));
        player.sendMessage(ChatColor.AQUA + "隠れ玉発動！残り" + remainingTime + "秒");

        BukkitTask task = new BukkitRunnable() {
            int timeLeft = remainingTime;
            @Override
            public void run() {
                timeLeft--;
                kakureDamaRemaining.put(player.getUniqueId(), timeLeft);
                plugin.getGameManager().updateScoreboard(); // スコアボード更新

                if (timeLeft <= 0) {
                    stopKakureDamaEffect(player);
                    player.sendMessage(ChatColor.RED + "隠れ玉が切れたよ！");
                    this.cancel();
                    kakureDamaTask.remove(player.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        kakureDamaTask.put(player.getUniqueId(), task);
    }

    /**
     * 隠れ玉停止
     */
    public void stopKakureDamaEffect(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        if (kakureDamaTask.containsKey(player.getUniqueId())) {
            kakureDamaTask.get(player.getUniqueId()).cancel();
            kakureDamaTask.remove(player.getUniqueId());
        }
    }

    /**
     * 鬼スロウ
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
                        if (!p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 2, false, false));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * 全エフェクト解除
     */
    public void clearAllEffects() {
        if (fadeTask != null) fadeTask.cancel();
        fadeTask = null;
        if (reverseFadeTask != null) reverseFadeTask.cancel();
        reverseFadeTask = null;
        if (oniSlownessTask != null) {
            oniSlownessTask.cancel();
            oniSlownessTask = null;
        }
        for (BukkitTask task : new ArrayList<>(kakureDamaTask.values())) {
            task.cancel();
        }
        kakureDamaTask.clear();

        for (Player p : Bukkit.getOnlinePlayers()) {
            clearAllPotionEffects(p);
            p.stopSound(ONISONG1_SOUND);
            p.stopSound(ONISONG2_SOUND);
            p.setFoodLevel(20);
        }
    }

    public void clearAllPotionEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    /**
     * 隠れ玉初期化
     */
    public void initializeKakureDama(int initialTime) {
        kakureDamaRemaining.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                kakureDamaRemaining.put(p.getUniqueId(), initialTime);
            }
        }
    }

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
