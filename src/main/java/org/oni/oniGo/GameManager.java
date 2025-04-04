package org.oni.oniGo;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {
    private final OniGo plugin;
    private final ConfigManager configManager;
    private final EffectManager effectManager;
    private final ItemManager itemManager;
    private final TeamManager teamManager;

    private boolean gameRunning = false;
    private int remainingTime = 500;
    private BukkitTask gameTimerTask;
    private BossBar timerBar;

    private boolean doorOpened = false;
    private boolean exitDoorOpened = false;
    private BukkitTask exitDoorCloseTask = null;

    // リスポーンタスク
    private Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    // 各プレイヤーごとのカウントチェスト
    private Map<UUID, Integer> playerRequiredCountChests = new HashMap<>();
    private Map<UUID, Integer> playerOpenedCountChests = new HashMap<>();

    // 残りチェスト数
    private int remainingChests = 0;

    // 出口ドアを開けたプレイヤー
    private Set<UUID> exitDoorOpeners = new HashSet<>();

    // **追加**：プレイヤー残機
    private Map<UUID, Integer> playerLives = new HashMap<>();

    // 月牙用追加フィールド
    private long gameStartTime = 0;
    private Map<UUID, Boolean> nextAttackMoonSlash = new HashMap<>();

    public GameManager(OniGo plugin, ConfigManager configManager, EffectManager effectManager,
                       ItemManager itemManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.effectManager = effectManager;
        this.itemManager = itemManager;
        this.teamManager = teamManager;

        // BossBar
        timerBar = Bukkit.createBossBar("残り時間: " + remainingTime + "秒", BarColor.GREEN, BarStyle.SOLID);
    }

    /**
     * 通常スタート
     */
    public boolean startGame(Player sender) {
        if (teamManager.areAnyPlayersUnassigned()) {
            sendConfigMessage(sender, ChatColor.RED + "陣営を選択していない人がいるよ。");
            return false;
        }
        if (!teamManager.areAllPlayersReady()) {
            sendConfigMessage(sender, ChatColor.RED + "鬼タイプをまだ選択していないプレイヤーがいるよ。");
            return false;
        }
        if (gameRunning) {
            sendConfigMessage(sender, ChatColor.RED + "ゲームはすでに進行中だよ！");
            return false;
        }
        // 安全のため全員のインベントリクリア
        for (Player p : Bukkit.getOnlinePlayers()) {
            itemManager.clearPlayerInventory(p);
        }
        // 完全リセット
        fullReset();

        gameRunning = true;
        doorOpened = false;
        exitDoorOpened = false;

        teamManager.initializeGameState();

        // チェストリセット
        configManager.resetChests();

        // カウントチェスト状況リセット
        initializePlayerChestCounts();

        // 隠れ玉初期化（1人15秒）
        effectManager.initializeKakureDama(15);

        // クールダウンリセット
        itemManager.resetAllCooldowns();

        // 残りチェスト更新
        updateRemainingChests();

        // **残機(3)の割り当て**
        assignPlayerLives();

        // カウントダウン開始（3,2,1 -> START）
        startGameCountdown(sender);

        return true;
    }

    /**
     * 残機を全プレイヤー(プレイヤーチームのみ)に3付与
     */
    private void assignPlayerLives() {
        playerLives.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                playerLives.put(p.getUniqueId(), 3);
            }
        }
    }

    /**
     * 完全リセット
     */
    private void fullReset() {
        gameRunning = false;
        doorOpened = false;
        exitDoorOpened = false;
        remainingTime = 500;
        exitDoorOpeners.clear();
        nextAttackMoonSlash.clear();
        gameStartTime = 0;

        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
        if (exitDoorCloseTask != null) {
            exitDoorCloseTask.cancel();
            exitDoorCloseTask = null;
        }
        for (UUID uuid : new ArrayList<>(respawnTasks.keySet())) {
            BukkitTask task = respawnTasks.get(uuid);
            if (task != null) {
                task.cancel();
            }
        }
        respawnTasks.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setFoodLevel(20);
            effectManager.clearAllPotionEffects(player);
        }
        playerRequiredCountChests.clear();
        playerOpenedCountChests.clear();

        // ボスバー
        if (timerBar != null) {
            timerBar.removeAll();
            timerBar.setProgress(1.0);
            timerBar.setTitle("残り時間: " + remainingTime + "秒");
        }

        exitDoorOpened = false;
        resetExitDoor();
    }

    private void resetExitDoor() {
        Location exitDoorLoc = configManager.getExitDoorLocation();
        if (exitDoorLoc != null) {
            Block block = exitDoorLoc.getBlock();
            if (block.getType().toString().contains("DOOR")) {
                BlockData data = block.getBlockData();
                if (data instanceof Openable) {
                    Openable door = (Openable) data;
                    door.setOpen(false);
                    block.setBlockData(door);
                } else {
                    // フォールバック処理
                    block.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * カウントダウン -> 実際の開始
     */
    private void startGameCountdown(Player sender) {
        new BukkitRunnable() {
            int count = 3;
            @Override
            public void run() {
                if (count > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(ChatColor.RED + "" + count, ChatColor.GOLD + "ゲーム開始まで...",
                                0, 20, 10);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    }
                    count--;
                } else {
                    // カウントダウン完了
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                        // 画面中央に「鬼ごっこSTART!」
                        p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "鬼ごっこSTART", "",
                                10, 30, 10);
                    }
                    actualGameStart();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * 実際のゲーム開始
     */
    private void actualGameStart() {
        // スポーン振り分け
        teleportPlayersToSpawns();
        // チーム別アイテム
        itemManager.distributeTeamItems();
        // 鬼タイプ効果適用
        applyOniTypeEffects();
        // ゲームタイマー
        startGameTimer();
        timerBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            timerBar.addPlayer(p);
        }
        // 鬼スロウ
        effectManager.startOniSlownessTask();
        // スコアボード更新
        updateScoreboard();

        // ゲーム開始時間を記録
        gameStartTime = System.currentTimeMillis() / 1000;
        // 月牙のチェスト定期通知を開始
        effectManager.startGetsugaChestDetection();

        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                p.getInventory().addItem(new ItemStack(Material.COOKED_PORKCHOP, 64));
            }
        }
    }

    /**
     * 鬼タイプ効果適用
     */
    private void applyOniTypeEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInOniTeam(p)) {
                OniType type = teamManager.getPlayerOniType(p);

                // 闇叉なら暗闇効果適用
                if (type == OniType.ANSHA) {
                    effectManager.applyAnshaPermaDarkness(p);
                }
            }
        }
    }

    /**
     * 鬼タイプに合わせた速度適用
     */
    private void applyOniTypeSpeed(Player player, OniType type) {
        // 速度効果を一旦解除
        player.removePotionEffect(PotionEffectType.getByName("SPEED"));
        player.removePotionEffect(PotionEffectType.getByName("SLOW"));

        int speedLevel = 0;
        switch (type) {
            case KISHA:
                speedLevel = 2; // 最高速度
                break;
            case ANSHA:
                speedLevel = 1; // 1.2倍速
                break;
            case GETSUGA:
                speedLevel = 1; // 1.5倍速
                break;
            default:
                return; // YASHA は標準速度
        }

        if (speedLevel > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.getByName("SPEED"), 999999, speedLevel, false, false));
        }
    }

    /**
     * プレイヤー1人を鬼にしてゲーム開始
     */
    public void oniStartGame(Player oniPlayer) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            itemManager.clearPlayerInventory(p);
        }
        teamManager.setupOniStart(oniPlayer);
        startGame(oniPlayer);
    }

    /**
     * ゲーム停止
     */
    public void stopGame(Player sender) {
        if (!gameRunning) {
            sendConfigMessage(sender, ChatColor.RED + "ゲームは開始してないよ！");
            return;
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "======= ゲーム中断 =======");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "管理者が中断したよ");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=========================");
        resetGame();
        sendConfigMessage(sender, ChatColor.GREEN + "ゲームを停止したよ");
    }

    /**
     * ゲーム終了
     */
    private void endGame() {
        gameRunning = false;
        // 残り時間0になったときの勝敗判定
        boolean halfEscaped = teamManager.haveHalfPlayersEscaped();
        if (halfEscaped) {
            // プレイヤー勝利
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(ChatColor.GOLD + "プレイヤー勝利！", ChatColor.AQUA + "過半数が脱出！",
                        10, 70, 20);
            }
        } else {
            // 鬼勝利
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(ChatColor.RED + "鬼勝利！", ChatColor.AQUA + "",
                        10, 70, 20);
            }
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "========== ゲーム終了 ==========");
        if (halfEscaped) {
            Bukkit.broadcastMessage(ChatColor.BLUE + "プレイヤー陣営の勝利！");
        } else {
            Bukkit.broadcastMessage(ChatColor.RED + "鬼陣営の勝利！");
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "================================");

        // 全員を初期スポーンへ
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }

        resetGame();
    }

    /**
     * プレイヤー全滅
     */
    public void endGameWithPlayerDefeat() {
        gameRunning = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.RED + "鬼陣営の勝利！", ChatColor.GRAY + "プレイヤー全滅", 10, 70, 20);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "======= ゲーム終了 =======");
        Bukkit.broadcastMessage(ChatColor.RED + "プレイヤー全滅！鬼陣営の勝利！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=========================");
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }
        resetGame();
    }

    /**
     * 一撃必殺（1人しかプレイヤーいないとき）
     */
    public void endGameWithOneHitKill() {
        gameRunning = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.RED + "一撃必殺！", ChatColor.DARK_RED + "プレイヤー陣営全滅…鬼勝利", 10, 70, 20);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "======= ゲーム終了 =======");
        Bukkit.broadcastMessage(ChatColor.RED + "一撃必殺！鬼陣営の勝利！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=========================");
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(spawnLocation);
        }
        resetGame();
    }

    /**
     * 過半数脱出によるプレイヤー勝利
     */
    public void endGameWithPlayerVictory() {
        gameRunning = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.GOLD + "プレイヤー勝利！", ChatColor.AQUA + "過半数が脱出成功！",
                    10, 70, 20);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "======= ゲーム終了 =======");
        Bukkit.broadcastMessage(ChatColor.BLUE + "プレイヤー陣営の勝利！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=========================");
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }
        resetGame();
    }

    /**
     * 出口ドアを開けた数が過半数により勝利
     */
    public void endGameWithExitDoorVictory() {
        gameRunning = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.GOLD + "プレイヤー勝利！", ChatColor.AQUA + "",
                    10, 70, 20);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "======= ゲーム終了 =======");
        Bukkit.broadcastMessage(ChatColor.BLUE + "過半数のプレイヤーが出口ドアを開けました！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=========================");
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(spawnLocation);
        }
        resetGame();
    }

    /**
     * ゲーム完全リセット
     */
    public void resetGame() {
        fullReset();
        effectManager.clearAllEffects();
        for (Player player : Bukkit.getOnlinePlayers()) {
            itemManager.clearPlayerInventory(player);
        }
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }
    }

    /**
     * タイマー開始
     */
    private void startGameTimer() {
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
        }
        gameTimerTask = new BukkitRunnable() {
            int lastCountdown = 5; // 5秒カウントダウン用
            @Override
            public void run() {
                remainingTime--;
                timerBar.setTitle("残り時間: " + remainingTime + "秒");
                timerBar.setProgress(Math.max(0, remainingTime / 500.0));
                updateScoreboard();

                // 残り5秒からカウントダウン
                if (remainingTime <= 5 && remainingTime > 0) {
                    // 5,4,3,2,1と画面中央に表示
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(ChatColor.RED + "" + remainingTime, ChatColor.GRAY + "もうすぐ終了…",
                                0, 25, 0);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    }
                }
                if (remainingTime == 0) {
                    // 鬼ごっこ終了
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(ChatColor.GOLD + "鬼ごっこ終了！", "", 10, 40, 10);
                    }
                    // 過半数脱出チェック
                    endGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * スポーン振り分け
     */
    private void teleportPlayersToSpawns() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInOniTeam(p)) {
                Location[] oniSpawns = new Location[] {
                        new Location(p.getWorld(), -2, -4, -24)
                };
                int randomIndex = (int)(Math.random() * oniSpawns.length);
                p.teleport(oniSpawns[randomIndex]);
                p.setFoodLevel(2);
                p.setGameMode(GameMode.ADVENTURE);
            } else if (teamManager.isPlayerInPlayerTeam(p)) {
                p.teleport(new Location(p.getWorld(), 0, 2, 0));
                p.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    /**
     * スコアボード更新
     */
    public void updateScoreboard() {
        teamManager.updateScoreboard(
                remainingTime,
                effectManager.getKakureDamaRemaining(),
                playerOpenedCountChests,
                playerRequiredCountChests,
                remainingChests
        );
    }

    /**
     * 残りチェスト更新
     */
    public void updateRemainingChests() {
        int totalChests = configManager.getTotalCountChests();
        int openedChests = configManager.getOpenedCountChestsCount();
        remainingChests = totalChests - openedChests;
        updateScoreboard();
    }

    /**
     * プレイヤー死亡時の処理
     */
    public void handlePlayerDeath(Player player) {
        if (!gameRunning) return;
        if (!teamManager.isPlayerInPlayerTeam(player)) return;

        // 残機を減らす
        UUID pid = player.getUniqueId();
        int lives = playerLives.getOrDefault(pid, 0);
        lives--;
        playerLives.put(pid, lives);

        // インベントリをドロップさせず、出口の鍵は保持したままにしたい場合はKeepInventory
        // ただし他のアイテムは落としてもいいかも…という場合は細かく制御する必要あり
        // ここでは全部保持にしとく
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.RED + "やられた… 残機: " + lives);
        updateScoreboard();

        // 残機が0なら復活なし
        if (lives <= 0) {
            player.sendMessage(ChatColor.DARK_RED + "残機がなくなりました。");
            // まだ生存者がいるか確認
            int survivors = teamManager.countSurvivingPlayers();
            if (survivors == 0) {
                // 全滅
                if (teamManager.getInitialPlayerCount() == 1) {
                    endGameWithOneHitKill();
                } else {
                    endGameWithPlayerDefeat();
                }
            }
            return;
        }

        // まだ残機があるので5秒後に復活
        respawnTasks.put(pid, new BukkitRunnable() {
            @Override
            public void run() {
                if (gameRunning) {
                    // チェストのうち1つ「最初のカウントチェスト」を探す
                    Location respawnLoc = plugin.getConfigManager().getInitialSpawnLocation();
                    if (!plugin.getConfigManager().getCountChestLocations().isEmpty()) {
                        // 適当に最初のやつ
                        Location chestLoc = plugin.getConfigManager().getCountChestLocations().values().iterator().next();
                        respawnLoc = chestLoc.clone().add(0, 1, 0);
                    }
                    player.teleport(respawnLoc);
                    player.setGameMode(GameMode.ADVENTURE);
                    player.sendMessage(ChatColor.GREEN + "復活");
                    player.removePotionEffect(PotionEffectType.getByName("INVISIBILITY"));
                    player.removePotionEffect(PotionEffectType.getByName("SLOW"));
                    updateScoreboard();
                }
            }
        }.runTaskLater(plugin, 20L * 5));
    }

    /**
     * 脱出確認
     */
    public void checkPlayerEscape(Player player) {
        if (!gameRunning) return;
        if (!teamManager.isPlayerInPlayerTeam(player)) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (teamManager.getEscapedPlayers().contains(player.getUniqueId())) return;

        Location escapeLoc = configManager.getEscapeLocation();
        Location playerLoc = player.getLocation();
        if (playerLoc.getWorld().equals(escapeLoc.getWorld()) &&
                playerLoc.distance(escapeLoc) <= 3) {
            // 出口ドア開いてるかチェック
            if (exitDoorOpened) {
                teamManager.addEscapedPlayer(player);
                player.sendMessage(ChatColor.GREEN + "脱出成功！");
                Bukkit.broadcastMessage(ChatColor.AQUA + player.getName() + "が脱出した！");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

                updateScoreboard();

                if (teamManager.haveHalfPlayersEscaped()) {
                    endGameWithPlayerVictory();
                }
            } else {
                player.sendMessage(ChatColor.RED + "出口ドアが閉まっている…鍵で開けよう！");
            }
        }
    }

    /**
     * 通常チェスト開封
     */
    public void handleChestOpened(String chestName, Player player) {
        if (!gameRunning) return;
        if (teamManager.isPlayerInOniTeam(player)) return;
        if (!configManager.isChestOpened(chestName)) {
            configManager.setChestOpened(chestName, true);
            player.sendMessage(ChatColor.AQUA + "チェスト「" + chestName + "」開封");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        }
    }

    /**
     * カウントチェスト開封
     */
    public void handleCountChestOpened(String chestName, Player player) {
        if (!gameRunning) return;
        if (teamManager.isPlayerInOniTeam(player)) return;

        UUID pid = player.getUniqueId();
        if (!configManager.isCountChestOpened(chestName)) {
            configManager.setCountChestOpened(chestName, true);
            int currentOpened = playerOpenedCountChests.getOrDefault(pid, 0) + 1;
            playerOpenedCountChests.put(pid, currentOpened);

            int required = playerRequiredCountChests.getOrDefault(pid, configManager.getRequiredCountChests());
            player.sendMessage(ChatColor.GOLD + "カウントチェスト「" + chestName + "」を開封。 残り" + (required - currentOpened) + "個");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);

            if (currentOpened >= required) {
                player.sendMessage(ChatColor.GOLD + "必要数チェストすべて開けた。出口の鍵をゲット");
                // 鍵付与
                ItemStack exitKey = itemManager.createExitKeyItem();
                player.getInventory().addItem(exitKey);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
            updateRemainingChests();
            updateScoreboard();
        }
    }

    /**
     * カウントチェスト初期化
     */
    private void initializePlayerChestCounts() {
        playerRequiredCountChests.clear();
        playerOpenedCountChests.clear();
        int req = configManager.getRequiredCountChests();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                playerRequiredCountChests.put(p.getUniqueId(), req);
                playerOpenedCountChests.put(p.getUniqueId(), 0);
            }
        }
    }

    /**
     * プレイヤー周囲チェスト探知
     */
    public void detectNearbyChests(Player oniPlayer) {
        if (!gameRunning) return;
        if (!teamManager.isPlayerInOniTeam(oniPlayer)) return;

        UUID oniUuid = oniPlayer.getUniqueId();
        if (itemManager.isChestDetectorOnCooldown(oniUuid)) {
            int remain = itemManager.getChestDetectorRemainingCooldown(oniUuid);
            oniPlayer.sendTitle(ChatColor.RED + "クールダウン中", "残り" + remain + "秒", 0, 60, 0);
            return;
        }

        // 全プレイヤーに通知
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.RED + "チェスト探知！", ChatColor.GOLD + "プレイヤー付近のチェストを探知中...", 10, 30, 10);
        }

        // 最寄りプレイヤーを探す
        Player nearestPlayer = null;
        double minDist = Double.MAX_VALUE;
        boolean playerNearby = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                double dist = oniPlayer.getLocation().distance(p.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearestPlayer = p;
                }
                if (dist <= 10) {
                    playerNearby = true;
                }
            }
        }

        if (nearestPlayer != null) {
            // そのプレイヤーの最寄りチェスト（通常orカウント）
            String nearestChestName = null;
            double nearestChestDist = Double.MAX_VALUE;
            Location pLoc = nearestPlayer.getLocation();

            // 通常チェスト
            for (Map.Entry<String, Location> e : configManager.getChestLocations().entrySet()) {
                double d = pLoc.distance(e.getValue());
                if (d < nearestChestDist) {
                    nearestChestDist = d;
                    nearestChestName = "通常チェスト「" + e.getKey() + "」";
                }
            }
            // カウントチェスト
            for (Map.Entry<String, Location> e : configManager.getCountChestLocations().entrySet()) {
                double d = pLoc.distance(e.getValue());
                if (d < nearestChestDist) {
                    nearestChestDist = d;
                    nearestChestName = "カウントチェスト「" + e.getKey() + "」";
                }
            }
            if (nearestChestName != null) {
                oniPlayer.sendTitle(ChatColor.RED + nearestChestName,
                        ChatColor.YELLOW + "距離: 約" + Math.round(nearestChestDist) + "ブロック",
                        0, 60, 0);
                if (playerNearby) {
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1.2f);
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                } else {
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
                }
            } else {
                oniPlayer.sendTitle(ChatColor.RED + "チェスト未検出", "", 0, 60, 0);
            }
        } else {
            oniPlayer.sendTitle(ChatColor.RED + "プレイヤー未検出", "", 0, 60, 0);
        }
        itemManager.setChestDetectorCooldown(oniUuid);
    }

    /**
     * チェストにワープ
     */
    public void teleportToNearbyChest(Player oniPlayer) {
        if (!gameRunning) return;
        if (!teamManager.isPlayerInOniTeam(oniPlayer)) return;

        UUID oniUuid = oniPlayer.getUniqueId();
        if (itemManager.isChestTeleporterOnCooldown(oniUuid)) {
            int remain = itemManager.getChestTeleporterRemainingCooldown(oniUuid);
            oniPlayer.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
            return;
        }

        // 全プレイヤーに通知
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.RED + "チェストワープ！", ChatColor.GOLD + "鬼がチェスト付近にワープ...", 10, 30, 10);
        }

        Player nearestPlayer = null;
        double minDistance = Double.MAX_VALUE;
        boolean playerNearby = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                double dist = oniPlayer.getLocation().distance(p.getLocation());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestPlayer = p;
                }
                if (dist <= 10) {
                    playerNearby = true;
                }
            }
        }
        if (nearestPlayer != null) {
            Location nearestChestLoc = null;
            double nearestChestDist = Double.MAX_VALUE;
            String chestName = null;
            Location pLoc = nearestPlayer.getLocation();

            // カウントチェスト優先
            for (Map.Entry<String, Location> entry : configManager.getCountChestLocations().entrySet()) {
                double d = pLoc.distance(entry.getValue());
                if (d < nearestChestDist) {
                    nearestChestDist = d;
                    nearestChestLoc = entry.getValue().clone();
                    chestName = "カウントチェスト「" + entry.getKey() + "」";
                }
            }
            if (nearestChestLoc == null) {
                // 通常チェストに切り替え
                nearestChestDist = Double.MAX_VALUE;
                for (Map.Entry<String, Location> entry : configManager.getChestLocations().entrySet()) {
                    double d = pLoc.distance(entry.getValue());
                    if (d < nearestChestDist) {
                        nearestChestDist = d;
                        nearestChestLoc = entry.getValue().clone();
                        chestName = "通常チェスト「" + entry.getKey() + "」";
                    }
                }
            }
            if (nearestChestLoc != null) {
                nearestChestLoc.add(0, 1, 0);
                oniPlayer.playSound(oniPlayer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                oniPlayer.teleport(nearestChestLoc);
                oniPlayer.sendMessage(ChatColor.RED + chestName + " 付近にワープしたよ。" +
                        "約" + Math.round(nearestChestDist) + "ブロック先にプレイヤー「" + nearestPlayer.getName() + "」");
                if (playerNearby) {
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1.2f);
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                } else {
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
                }
            } else {
                oniPlayer.sendMessage(ChatColor.RED + "近くにチェストが見つからないよ！");
            }
        } else {
            oniPlayer.sendMessage(ChatColor.RED + "プレイヤーが見つからないよ！");
        }
        itemManager.setChestTeleporterCooldown(oniUuid);
    }

    /**
     * プレイヤー緊急脱出
     */
    public void handlePlayerEscape(Player player) {
        if (!gameRunning) return;
        if (!teamManager.isPlayerInPlayerTeam(player)) return;

        UUID pid = player.getUniqueId();
        if (itemManager.isPlayerEscapeOnCooldown(pid)) {
            int remain = itemManager.getPlayerEscapeRemainingCooldown(pid);
            player.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
            return;
        }

        // 鬼が10ブロック以内にいるか
        boolean oniNearby = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInOniTeam(p)) {
                double dist = p.getLocation().distance(player.getLocation());
                if (dist <= 10) {
                    oniNearby = true;
                    break;
                }
            }
        }
        if (!oniNearby) {
            player.sendMessage(ChatColor.RED + "近くに鬼がいないと使えない");
            return;
        }

        // ワープ先の候補(カウントチェスト or 通常チェスト)
        List<Location> chestLocs = new ArrayList<>(configManager.getCountChestLocations().values());
        if (chestLocs.isEmpty()) {
            chestLocs.addAll(configManager.getChestLocations().values());
        }
        if (chestLocs.isEmpty()) {
            player.sendMessage(ChatColor.RED + "チェストがどこにも登録されていないためワープできないよ！");
            return;
        }
        // ランダム選択
        Random random = new Random();
        Location targetLoc = chestLocs.get(random.nextInt(chestLocs.size())).clone().add(0,1,0);
        // 暗闇
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.getByName("BLINDNESS"), 20 * 3, 1, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        // テレポート
        player.teleport(targetLoc);
        player.sendMessage(ChatColor.BLUE + "緊急脱出");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);

        itemManager.setPlayerEscapeCooldown(pid);
    }

    /**
     * メインドア開ける
     */
    public void openDoor() {
        Location doorLoc = configManager.getDoorLocation();
        if (doorLoc == null) {
            sendConfigMessage(null, ChatColor.RED + "ドア未登録！");
            return;
        }
        Block block = doorLoc.getBlock();
        if (!block.getType().toString().contains("DOOR")) {
            sendConfigMessage(null, ChatColor.RED + "登録地点にドアがない！");
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Openable) {
            Openable door = (Openable) data;
            door.setOpen(true);
            block.setBlockData(door);
            doorOpened = true;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(doorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);
            }
            Bukkit.broadcastMessage(ChatColor.GREEN + "メインドアが開いた！");
        } else {
            block.setType(Material.AIR);
            doorOpened = true;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(doorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);
            }
            Bukkit.broadcastMessage(ChatColor.GREEN + "メインドアが開いた！（ドア破壊）");
        }
    }

    /**
     * 出口ドア開ける
     */
    public void openExitDoor(Player player) {
        Location exitDoorLoc = configManager.getExitDoorLocation();
        if (exitDoorLoc == null) {
            sendConfigMessage(null, ChatColor.RED + "出口ドア未登録！");
            return;
        }
        Block block = exitDoorLoc.getBlock();
        if (!block.getType().toString().contains("DOOR")) {
            sendConfigMessage(null, ChatColor.RED + "登録地点にドアがない！");
            return;
        }

        UUID pid = player.getUniqueId();
        if (exitDoorOpeners.contains(pid)) {
            player.sendMessage(ChatColor.YELLOW + "すでに出口ドアを開けています！");
            return;
        }
        exitDoorOpeners.add(pid);

        BlockData data = block.getBlockData();
        if (data instanceof Openable) {
            Openable door = (Openable) data;
            door.setOpen(true);
            block.setBlockData(door);
            exitDoorOpened = true;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(exitDoorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);
            }
            Bukkit.broadcastMessage(ChatColor.GREEN + player.getName() + "が出口ドアを開けた！");
            checkExitDoorVictory();
            scheduleExitDoorClose(block, exitDoorLoc);
        } else {
            block.setType(Material.AIR);
            exitDoorOpened = true;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(exitDoorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);
            }
            Bukkit.broadcastMessage(ChatColor.GREEN + player.getName() + "が出口ドアを開けた！（破壊）");
            checkExitDoorVictory();
            scheduleExitDoorClose(block, exitDoorLoc);
        }
    }

    private void checkExitDoorVictory() {
        int total = teamManager.getInitialPlayerCount();
        int openersCount = exitDoorOpeners.size();
        if (openersCount > total / 2) {
            endGameWithExitDoorVictory();
        } else {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "現在 " + openersCount + "/" + total +
                    " 人が出口ドアを開けた。あと " + (total/2 + 1 - openersCount) + "人でプレイヤー勝利！");
        }
    }

    private void scheduleExitDoorClose(Block block, final Location exitDoorLoc) {
        if (exitDoorCloseTask != null) {
            exitDoorCloseTask.cancel();
            exitDoorCloseTask = null;
        }
        exitDoorCloseTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) {
                    exitDoorCloseTask = null;
                    return;
                }
                if (exitDoorOpened && block.getBlockData() instanceof Openable) {
                    Openable door = (Openable) block.getBlockData();
                    door.setOpen(false);
                    block.setBlockData(door);
                    exitDoorOpened = false;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(exitDoorLoc, Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
                    }
                    exitDoorCloseTask = null;
                } else if (block.getType() == Material.AIR) {
                    block.setType(Material.OAK_DOOR);
                    exitDoorOpened = false;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(exitDoorLoc, Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
                    }
                    exitDoorCloseTask = null;
                }
            }
        }.runTaskLater(plugin, 20L); // 1秒後に実行
    }

    private void sendConfigMessage(Player sender, String message) {
        Player target = Bukkit.getPlayerExact("minamottooooooooo");
        if (target != null && target.isOnline()) {
            target.sendMessage(message);
        } else if (sender != null && "minamottooooooooo".equals(sender.getName())) {
            sender.sendMessage(message);
        }
    }

    public void setGameTime(int seconds) {
        if (seconds < 60) {
            sendConfigMessage(null, ChatColor.RED + "60秒以上にしてね！");
            return;
        }
        remainingTime = seconds;
        sendConfigMessage(null, ChatColor.GREEN + "ゲーム時間を" + seconds + "秒に設定したよ");
    }

    // 全プレイヤー最寄りチェスト検知（月牙用）
    public void detectAllPlayersNearestChests(Player oniPlayer) {
        if (!gameRunning) return;
        if (!teamManager.isPlayerInOniTeam(oniPlayer)) return;
        if (teamManager.getPlayerOniType(oniPlayer) != OniType.GETSUGA) return;

        StringBuilder message = new StringBuilder(ChatColor.BLUE + "チェスト周辺プレイヤー情報:\n");
        boolean playerFound = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                playerFound = true;
                // 最寄りチェスト探索
                String nearestChestName = null;
                double nearestChestDist = Double.MAX_VALUE;
                Location pLoc = p.getLocation();

                // 通常チェスト
                for (Map.Entry<String, Location> e : configManager.getChestLocations().entrySet()) {
                    double d = pLoc.distance(e.getValue());
                    if (d < nearestChestDist) {
                        nearestChestDist = d;
                        nearestChestName = "通常チェスト「" + e.getKey() + "」";
                    }
                }
                // カウントチェスト
                for (Map.Entry<String, Location> e : configManager.getCountChestLocations().entrySet()) {
                    double d = pLoc.distance(e.getValue());
                    if (d < nearestChestDist) {
                        nearestChestDist = d;
                        nearestChestName = "カウントチェスト「" + e.getKey() + "」";
                    }
                }

                message.append(ChatColor.AQUA).append(p.getName()).append(": ");
                if (nearestChestName != null) {
                    message.append(nearestChestName).append(" (約").append(Math.round(nearestChestDist)).append("ブロック)\n");
                } else {
                    message.append("チェスト周辺にいない\n");
                }
            }
        }

        if (!playerFound) {
            oniPlayer.sendMessage(ChatColor.RED + "プレイヤーが見つかりません");
        } else {
            oniPlayer.sendMessage(message.toString());
        }
    }

    // 全プレイヤーをランダムなチェストにテレポート（月牙の三日月用）
    public void teleportAllPlayersToRandomChests() {
        if (!gameRunning) return;

        List<Location> chestLocations = new ArrayList<>();
        chestLocations.addAll(configManager.getCountChestLocations().values());
        chestLocations.addAll(configManager.getChestLocations().values());

        if (chestLocations.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.RED + "テレポート先のチェストがありません");
            return;
        }

        // 全プレイヤーに通知
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.AQUA + "月牙の三日月！", ChatColor.BLUE + "全プレイヤーがランダムテレポート...", 10, 30, 10);
        }

        Random random = new Random();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                // ランダムなチェスト選択
                Location chestLoc = chestLocations.get(random.nextInt(chestLocations.size()));
                // 安全な位置に調整
                Location teleportLoc = chestLoc.clone().add(0, 1, 0);

                p.teleport(teleportLoc);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                p.sendMessage(ChatColor.AQUA + "月牙の能力で転送された！");
            }
        }

        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "月牙の三日月により全プレイヤーが転送された！");
    }

    // 次の攻撃で月切り発動フラグ
    public void setNextAttackMoonSlash(Player player) {
        nextAttackMoonSlash.put(player.getUniqueId(), true);
    }

    // 月切り待機中かどうか
    public boolean isNextAttackMoonSlash(Player player) {
        return nextAttackMoonSlash.getOrDefault(player.getUniqueId(), false);
    }

    // 月切り発動（カウントチェスト減少）
    public void executeGetsugaMoonSlash(Player player, Player target) {
        if (!isNextAttackMoonSlash(player)) return;

        // 月切りフラグ解除
        nextAttackMoonSlash.put(player.getUniqueId(), false);

        // ターゲットのカウントチェスト数を減らす
        UUID targetId = target.getUniqueId();
        int opened = playerOpenedCountChests.getOrDefault(targetId, 0);

        if (opened > 0) {
            // 既に開けたチェストをカウントダウン
            playerOpenedCountChests.put(targetId, opened - 1);

            // 全プレイヤーに通知
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(ChatColor.BLUE + "月牙の月切り！", ChatColor.AQUA + target.getName() + "のチェスト進行度が減少", 10, 30, 10);
            }

            target.sendMessage(ChatColor.RED + "月牙の月切りによりカウントチェスト進行度が1つ減った！");
            player.sendMessage(ChatColor.BLUE + target.getName() + "のカウントチェスト進行度を1つ減らした！");

            // 使用回数カウント
            itemManager.incrementGetsugaMoonSlashCount(player.getUniqueId());
            int used = itemManager.getGetsugaMoonSlashCount(player.getUniqueId());
            player.sendMessage(ChatColor.GRAY + "月切り: " + used + "/3回使用");

            updateScoreboard();
        } else {
            player.sendMessage(ChatColor.RED + target.getName() + "はまだカウントチェストを開けていません");
        }
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public Map<UUID, BukkitTask> getRespawnTasks() {
        return respawnTasks;
    }

    public boolean isDoorOpened() {
        return doorOpened;
    }

    public boolean isExitDoorOpened() {
        return exitDoorOpened;
    }

    public Map<UUID, Integer> getPlayerOpenedCountChests() {
        return playerOpenedCountChests;
    }

    public Map<UUID, Integer> getPlayerRequiredCountChests() {
        return playerRequiredCountChests;
    }

    public int getRemainingChests() {
        return remainingChests;
    }

    public long getGameStartTime() {
        return gameStartTime;
    }
}