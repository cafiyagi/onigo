package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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

    // 新しい鬼タイプ用のタスク
    private Map<UUID, BukkitTask> kishaDashTasks = new HashMap<>();  // 鬼叉：突進
    private Map<UUID, BukkitTask> kishaKanabooTasks = new HashMap<>(); // 鬼叉：金棒
    private Map<UUID, BukkitTask> anshaDarkenTasks = new HashMap<>(); // 闇叉：暗転
    private Map<UUID, BukkitTask> getugaSlowTasks = new HashMap<>(); // 月牙：殺月

    // 月牙の自動検知タスク
    private BukkitTask getugaAutoDetectTask;

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
                        OniType oniType = teamManager.getPlayerOniType(p);
                        // 各鬼タイプに応じた速度効果
                        if (oniType == OniType.KISHA) {
                            // 鬼叉は最高速度（通常の鬼より速い）
                            if (!p.hasPotionEffect(PotionEffectType.SPEED)) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false));
                            }
                        } else if (oniType == OniType.ANSHA) {
                            // 闇叉はプレイヤーの1.2倍速
                            if (!p.hasPotionEffect(PotionEffectType.SPEED)) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, false, false));
                            }
                            // 常に暗闇2（特殊スキル使用中は除く）
                            if (!isAnshaDarkenActive(p.getUniqueId()) && !p.hasPotionEffect(PotionEffectType.DARKNESS)) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 999999, 1, false, false));
                            }
                        } else if (oniType == OniType.GETUGA) {
                            // 月牙はプレイヤーの1.5倍速
                            if (!p.hasPotionEffect(PotionEffectType.SPEED)) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 2, false, false));
                            }
                        } else {
                            // 他の鬼（夜叉など）はスロウ
                            if (!p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 2, false, false));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        // 月牙の自動検知も開始
        startGetugaAutoDetectTask();
    }

    /**
     * 鬼叉：突進スキル
     */
    public void startKishaDashEffect(Player player) {
        UUID pid = player.getUniqueId();

        // 既存の効果を解除
        if (kishaDashTasks.containsKey(pid)) {
            kishaDashTasks.get(pid).cancel();
        }

        // 5秒間速度増加
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 15, false, false));
        player.sendMessage(ChatColor.RED + "突進！5秒間速度大幅アップ");

        // 効果終了タイマー
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.RED + "突進効果が切れた");
                if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                    player.removePotionEffect(PotionEffectType.SPEED);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false));
                }
                kishaDashTasks.remove(pid);
            }
        }.runTaskLater(plugin, 100L); // 5秒後

        kishaDashTasks.put(pid, task);
    }

    /**
     * 鬼叉：停止スキル
     */
    public void executeKishaStopEffect() {
        // 全プレイヤーを2秒間停止
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 100, false, false));
                player.sendMessage(ChatColor.RED + "鬼叉の「停止」により動けなくなった！");
            }
        }

        // 2秒後に解除するタイマーは必要ない（効果時間が自動的に切れる）
    }

    /**
     * 鬼叉：金棒スキル
     */
    public void startKishaKanabooEffect(Player player) {
        UUID pid = player.getUniqueId();
        plugin.getItemManager().setKishaKanabooActive(pid, true);

        player.sendMessage(ChatColor.RED + "金棒発動！30秒間一撃必殺！");

        // 既存のタスクを解除
        if (kishaKanabooTasks.containsKey(pid)) {
            kishaKanabooTasks.get(pid).cancel();
        }

        // 30秒後に解除
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getItemManager().setKishaKanabooActive(pid, false);
                player.sendMessage(ChatColor.RED + "金棒効果が切れた");
                kishaKanabooTasks.remove(pid);
            }
        }.runTaskLater(plugin, 600L); // 30秒後

        kishaKanabooTasks.put(pid, task);
    }

    /**
     * 闇叉：暗転スキル
     */
    public void startAnshaDarkenEffect(Player player) {
        UUID pid = player.getUniqueId();

        // プレイヤー全員に暗闇3を付与（10秒間）
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 2, false, false));
                p.sendMessage(ChatColor.DARK_PURPLE + "闇叉の「暗転」により視界がほぼ見えなくなった！");
            }
        }

        // 闇叉自身の暗闇解除
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.sendMessage(ChatColor.DARK_PURPLE + "暗転発動！10秒間自分の暗闇が解除され、プレイヤーに暗闇3付与");

        // 既存のタスクを解除
        if (anshaDarkenTasks.containsKey(pid)) {
            anshaDarkenTasks.get(pid).cancel();
        }

        // 10秒後に元に戻す
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.DARK_PURPLE + "暗転効果が切れた");
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 999999, 1, false, false));
                anshaDarkenTasks.remove(pid);
            }
        }.runTaskLater(plugin, 200L); // 10秒後

        anshaDarkenTasks.put(pid, task);
    }

    /**
     * 闇叉の暗転が有効かどうか
     */
    public boolean isAnshaDarkenActive(UUID playerUuid) {
        return anshaDarkenTasks.containsKey(playerUuid);
    }

    /**
     * 月牙：自動検知タスク
     */
    private void startGetugaAutoDetectTask() {
        if (getugaAutoDetectTask != null) {
            getugaAutoDetectTask.cancel();
        }

        getugaAutoDetectTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isGameRunning()) {
                    cancel();
                    return;
                }

                // 月牙タイプの鬼を全て探す
                for (Player oni : Bukkit.getOnlinePlayers()) {
                    if (teamManager.isPlayerInOniTeam(oni) &&
                            teamManager.getPlayerOniType(oni) == OniType.GETUGA) {

                        // 最寄りのプレイヤーとそのチェストを検知
                        Player nearestPlayer = findNearestPlayer(oni);
                        if (nearestPlayer != null) {
                            String chestInfo = getPlayerNearestChestInfo(nearestPlayer);
                            oni.sendMessage(ChatColor.GOLD + "月牙の能力: " + nearestPlayer.getName() +
                                    " は " + chestInfo + " の近くにいる");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // 30秒ごと (30 * 20 = 600 ticks)
    }

    /**
     * 最寄りのプレイヤーを探す
     */
    private Player findNearestPlayer(Player source) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                double dist = p.getLocation().distance(source.getLocation());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = p;
                }
            }
        }

        return nearest;
    }

    /**
     * プレイヤーに最も近いチェスト情報を取得
     */
    private String getPlayerNearestChestInfo(Player player) {
        Location playerLoc = player.getLocation();
        double minDistance = Double.MAX_VALUE;
        String chestInfo = "不明な場所";

        // カウントチェストを優先的に確認
        for (Map.Entry<String, Location> entry : plugin.getConfigManager().getCountChestLocations().entrySet()) {
            double dist = playerLoc.distance(entry.getValue());
            if (dist < minDistance) {
                minDistance = dist;
                chestInfo = "カウントチェスト「" + entry.getKey() + "」";
            }
        }

        // 通常チェストも確認
        for (Map.Entry<String, Location> entry : plugin.getConfigManager().getChestLocations().entrySet()) {
            double dist = playerLoc.distance(entry.getValue());
            if (dist < minDistance) {
                minDistance = dist;
                chestInfo = "チェスト「" + entry.getKey() + "」";
            }
        }

        return chestInfo;
    }

    /**
     * 月牙：殺月スキル
     */
    public void startGetugaSlowEffect(Player player) {
        UUID pid = player.getUniqueId();
        plugin.getItemManager().setGetugaSlowActive(pid, true);

        // 全プレイヤーに鈍足効果
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 2, false, false));
                p.sendMessage(ChatColor.GOLD + "月牙の「殺月」により30秒間動きが遅くなった！");
            }
        }

        player.sendMessage(ChatColor.GOLD + "殺月発動！30秒間プレイヤーを鈍足に。誰かを殴ると解除される。");

        // 既存のタスクを解除
        if (getugaSlowTasks.containsKey(pid)) {
            getugaSlowTasks.get(pid).cancel();
        }

        // 30秒後に自動で解除
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getItemManager().setGetugaSlowActive(pid, false);
                player.sendMessage(ChatColor.GOLD + "殺月効果が切れた");
                removeAllPlayerSlowness();
                getugaSlowTasks.remove(pid);
            }
        }.runTaskLater(plugin, 600L); // 30秒後

        getugaSlowTasks.put(pid, task);
    }

    /**
     * 月牙：殺月効果解除
     */
    public void stopGetugaSlowEffect(Player player) {
        UUID pid = player.getUniqueId();

        if (getugaSlowTasks.containsKey(pid)) {
            getugaSlowTasks.get(pid).cancel();
            getugaSlowTasks.remove(pid);
        }

        plugin.getItemManager().setGetugaSlowActive(pid, false);
        removeAllPlayerSlowness();
        player.sendMessage(ChatColor.GOLD + "殺月効果が解除された");
    }

    /**
     * 全プレイヤーの鈍足解除
     */
    private void removeAllPlayerSlowness() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.sendMessage(ChatColor.GREEN + "鈍足効果が解除された");
            }
        }
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

        // 新しい鬼タイプ用のタスクも停止
        for (BukkitTask task : new ArrayList<>(kishaDashTasks.values())) {
            task.cancel();
        }
        kishaDashTasks.clear();

        for (BukkitTask task : new ArrayList<>(kishaKanabooTasks.values())) {
            task.cancel();
        }
        kishaKanabooTasks.clear();

        for (BukkitTask task : new ArrayList<>(anshaDarkenTasks.values())) {
            task.cancel();
        }
        anshaDarkenTasks.clear();

        for (BukkitTask task : new ArrayList<>(getugaSlowTasks.values())) {
            task.cancel();
        }
        getugaSlowTasks.clear();

        if (getugaAutoDetectTask != null) {
            getugaAutoDetectTask.cancel();
            getugaAutoDetectTask = null;
        }

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