package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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

    // 追加フィールド
    private Map<UUID, BukkitTask> kishaGoldClubTasks = new HashMap<>(); // 鬼叉の金棒効果
    private Map<UUID, BukkitTask> anshaDarknessTasks = new HashMap<>(); // 闇叉の暗闇状態
    private Map<UUID, BukkitTask> playerDarknessTasks = new HashMap<>(); // 闇叉の暗転効果
    private Map<UUID, BukkitTask> getsugaKillMoonTasks = new HashMap<>(); // 月牙の殺月効果
    private boolean killMoonActive = false;

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

        // 追加エフェクト解除
        for (BukkitTask task : kishaGoldClubTasks.values()) task.cancel();
        kishaGoldClubTasks.clear();
        for (BukkitTask task : anshaDarknessTasks.values()) task.cancel();
        anshaDarknessTasks.clear();
        for (BukkitTask task : playerDarknessTasks.values()) task.cancel();
        playerDarknessTasks.clear();
        for (BukkitTask task : getsugaKillMoonTasks.values()) task.cancel();
        getsugaKillMoonTasks.clear();
        killMoonActive = false;

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

    // 鬼叉の突進効果
    public void startKishaDashEffect(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 2, false, true));
        player.sendMessage(ChatColor.GOLD + "突進発動！5秒間速度上昇");
    }

    // 鬼叉の停止効果
    public void startKishaFreezeEffect(Player player) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.getByName("SLOW"), 20 * 2, 255, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.getByName("JUMP"), 20 * 2, 128, false, true));
                p.sendMessage(ChatColor.AQUA + "鬼叉の能力で2秒間動けなくなった！");
            }
            // 全プレイヤーに通知
            p.sendTitle(ChatColor.AQUA + "鬼叉の停止！", ChatColor.RED + "プレイヤーが一時停止...", 10, 30, 10);
        }
        player.sendMessage(ChatColor.AQUA + "停止発動！全プレイヤーを2秒間停止させた");
    }

    // 鬼叉の金棒効果
    public void startKishaGoldClubEffect(Player player) {
        UUID pid = player.getUniqueId();
        if (kishaGoldClubTasks.containsKey(pid)) {
            kishaGoldClubTasks.get(pid).cancel();
        }

        player.sendMessage(ChatColor.YELLOW + "金棒発動！30秒間一撃必殺");

        // 全プレイヤーに通知
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.YELLOW + "鬼叉の金棒！", ChatColor.RED + "30秒間一撃必殺状態...", 10, 30, 10);
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.RED + "金棒の効果が切れた");
                kishaGoldClubTasks.remove(pid);
            }
        }.runTaskLater(plugin, 20 * 30); // 30秒後

        kishaGoldClubTasks.put(pid, task);
    }

    // 金棒効果中かどうか
    public boolean isKishaGoldClubActive(Player player) {
        return kishaGoldClubTasks.containsKey(player.getUniqueId());
    }

    // 闇叉の常時暗闇効果
    public void applyAnshaPermaDarkness(Player player) {
        UUID pid = player.getUniqueId();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isGameRunning()) {
                    this.cancel();
                    anshaDarknessTasks.remove(pid);
                    return;
                }
                // 闇叉の暗転効果中でなければ暗闇付与
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 2, false, false));
            }
        }.runTaskTimer(plugin, 0L, 20L);

        anshaDarknessTasks.put(pid, task);
    }

    // 闇叉の暗転効果
    public void startAnshaDarknessEffect(Player player) {
        // 闇叉は暗闇から解放される
        if (anshaDarknessTasks.containsKey(player.getUniqueId())) {
            BukkitTask task = anshaDarknessTasks.get(player.getUniqueId());
            task.cancel();
            anshaDarknessTasks.remove(player.getUniqueId());
        }
        player.removePotionEffect(PotionEffectType.DARKNESS);

        // プレイヤーに強い暗闇をかける
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 10, 3, false, false));
                p.sendMessage(ChatColor.DARK_PURPLE + "闇叉の能力で強い暗闇に包まれた！");

                // 10秒後に解除
                UUID pid = p.getUniqueId();
                if (playerDarknessTasks.containsKey(pid)) {
                    playerDarknessTasks.get(pid).cancel();
                }

                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        p.removePotionEffect(PotionEffectType.DARKNESS);
                        playerDarknessTasks.remove(pid);
                    }
                }.runTaskLater(plugin, 20 * 10);

                playerDarknessTasks.put(pid, task);
            }
        }

        player.sendMessage(ChatColor.DARK_PURPLE + "暗転発動！10秒間プレイヤーに強い暗闇効果");

        // 10秒後に闇叉の暗闇復活
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.isGameRunning() && teamManager.isPlayerInOniTeam(player)) {
                    applyAnshaPermaDarkness(player);
                }
            }
        }.runTaskLater(plugin, 20 * 10);
    }

    // 月牙のチェスト通知（GameManagerからの呼び出し用）
    public void startGetsugaChestDetection() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isGameRunning()) {
                    this.cancel();
                    return;
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (teamManager.isPlayerInOniTeam(p) &&
                            teamManager.getPlayerOniType(p) == OniType.GETSUGA) {
                        // 全プレイヤーの最寄りのチェストを通知
                        plugin.getGameManager().detectAllPlayersNearestChests(p);
                        break; // 月牙は1人だけなので最初に見つかったらbreak
                    }
                }
            }
        }.runTaskTimer(plugin, 20 * 30, 20 * 30); // 30秒ごとに実行
    }

    // 月牙の殺月効果
    public void startGetsugaKillMoonEffect(Player player) {
        killMoonActive = true;

        // 全プレイヤーに通知
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.DARK_RED + "月牙の殺月！", ChatColor.RED + "30秒間鈍足効果...", 10, 30, 10);
        }

        // 全プレイヤーに鈍足効果
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.getByName("SLOW"), 20 * 30, 1, false, true));
                p.sendMessage(ChatColor.DARK_RED + "月牙の殺月！30秒間鈍足になった");
            }
        }

        player.sendMessage(ChatColor.DARK_RED + "殺月発動！30秒間プレイヤーを鈍足化（誰かを殴ると解除）");

        // 30秒後に解除
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                stopGetsugaKillMoonEffect();
            }
        }.runTaskLater(plugin, 20 * 30);

        UUID pid = player.getUniqueId();
        getsugaKillMoonTasks.put(pid, task);
    }

    // 殺月効果解除
    public void stopGetsugaKillMoonEffect() {
        killMoonActive = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                p.removePotionEffect(PotionEffectType.getByName("SLOW"));
            }
        }

        for (BukkitTask task : getsugaKillMoonTasks.values()) {
            task.cancel();
        }
        getsugaKillMoonTasks.clear();
    }

    // 殺月効果中かどうか
    public boolean isKillMoonActive() {
        return killMoonActive;
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