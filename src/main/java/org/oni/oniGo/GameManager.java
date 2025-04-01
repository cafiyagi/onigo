package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    // Door open states
    private boolean doorOpened = false;
    private boolean exitDoorOpened = false;

    // Exit door auto-close task
    private BukkitTask exitDoorCloseTask = null;

    // Player respawn tasks
    private Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    // Per-player chest tracking
    private Map<UUID, Integer> playerRequiredCountChests = new HashMap<>();
    private Map<UUID, Integer> playerOpenedCountChests = new HashMap<>();

    // 残りチェスト数
    private int remainingChests = 0;

    // 勝利座標
    private final Location victoryLocation = new Location(Bukkit.getWorlds().get(0), 0, 0, 7);

    public GameManager(OniGo plugin, ConfigManager configManager, EffectManager effectManager,
                       ItemManager itemManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.effectManager = effectManager;
        this.itemManager = itemManager;
        this.teamManager = teamManager;

        // Initialize boss bar
        timerBar = Bukkit.createBossBar("残り時間: " + remainingTime + "秒", BarColor.GREEN, BarStyle.SOLID);
    }

    /**
     * Start the game with normal mode
     */
    public boolean startGame(Player sender) {
        // Check if all players have selected a team
        if (teamManager.areAnyPlayersUnassigned()) {
            sendConfigMessage(sender, ChatColor.RED + "選択していない人がいるよ。");
            return false;
        }

        if (gameRunning) {
            sendConfigMessage(sender, ChatColor.RED + "ゲームはすでに進行中だよ！");
            return false;
        }

        // Clear inventories to be safe
        for (Player p : Bukkit.getOnlinePlayers()) {
            itemManager.clearPlayerInventory(p);
        }

        // 完全にリセット
        fullReset();

        // Initialize game state
        gameRunning = true;
        doorOpened = false;
        exitDoorOpened = false;
        teamManager.initializeGameState();

        // Reset all chest statuses
        configManager.resetChests();

        // Initialize per-player chest counts
        initializePlayerChestCounts();

        // Initialize kakure dama (hiding orb) timers
        effectManager.initializeKakureDama(15);

        // Reset all item cooldowns
        itemManager.resetAllCooldowns();

        // 残りチェスト数を設定
        updateRemainingChests();

        // Start countdown before game starts
        startGameCountdown(sender);

        return true;
    }

    /**
     * 完全にリセットする
     */
    private void fullReset() {
        gameRunning = false;
        doorOpened = false;
        exitDoorOpened = false;
        remainingTime = 500;

        // Cancel all tasks
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }

        if (exitDoorCloseTask != null) {
            exitDoorCloseTask.cancel();
            exitDoorCloseTask = null;
        }

        // Cancel all respawn tasks
        for (UUID uuid : new ArrayList<>(respawnTasks.keySet())) {
            BukkitTask task = respawnTasks.get(uuid);
            if (task != null) {
                task.cancel();
            }
        }
        respawnTasks.clear();

        // Reset player states
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setFoodLevel(20);
            effectManager.clearAllPotionEffects(player);
        }

        // Reset maps
        playerRequiredCountChests.clear();
        playerOpenedCountChests.clear();



        // Reset timerbar
        if (timerBar != null) {
            timerBar.removeAll();
            timerBar.setProgress(1.0);
            timerBar.setTitle("残り時間: " + remainingTime + "秒");
        }
    }

    /**
     * Start countdown before actual game start
     */
    private void startGameCountdown(Player sender) {
        new BukkitRunnable() {
            int count = 3;

            @Override
            public void run() {
                if (count > 0) {
                    // Display countdown to all players
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(
                                ChatColor.RED + "" + count,
                                ChatColor.GOLD + "ゲーム開始まで...",
                                0, 20, 10
                        );
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    count--;
                } else {
                    // エンダードラゴンの鳴き声を再生
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }

                    // Start the actual game when countdown ends
                    actualGameStart();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Actual game start after countdown
     */
    private void actualGameStart() {
        // Teleport players to their spawn locations
        teleportPlayersToSpawns();

        // Give team items
        itemManager.distributeTeamItems();

        // Start game timer
        startGameTimer();
        timerBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            timerBar.addPlayer(p);
        }

        // Start oni slowness effect
        effectManager.startOniSlownessTask();

        // Update scoreboard
        updateScoreboard();

        Bukkit.broadcastMessage(ChatColor.GREEN + "ゲームスタート！残り時間: " + remainingTime + "秒");
    }

    /**
     * Initialize per-player chest counts
     */
    private void initializePlayerChestCounts() {
        playerRequiredCountChests.clear();
        playerOpenedCountChests.clear();

        int requiredPerPlayer = configManager.getRequiredCountChests();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p)) {
                UUID playerId = p.getUniqueId();
                playerRequiredCountChests.put(playerId, requiredPerPlayer);
                playerOpenedCountChests.put(playerId, 0);
            }
        }
    }

    /**
     * Start the game with one player as oni
     */
    public void oniStartGame(Player oniPlayer) {
        // Clear player inventories
        for (Player p : Bukkit.getOnlinePlayers()) {
            itemManager.clearPlayerInventory(p);
        }

        // Setup teams with specified player as oni
        teamManager.setupOniStart(oniPlayer);

        // Start the game
        startGame(oniPlayer);
    }

    /**
     * Stop the game
     */
    public void stopGame(Player sender) {
        if (!gameRunning) {
            sendConfigMessage(sender, ChatColor.RED + "ゲームが開始されていないよ！");
            return;
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム中断 =========");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "管理者によりゲーム中断されたよ");
        Bukkit.broadcastMessage(ChatColor.GOLD + "============================");

        resetGame();
        sendConfigMessage(sender, ChatColor.GREEN + "ゲームを停止したよ！");
    }

    /**
     * End the game with win message
     */
    private void endGame() {
        gameRunning = false;

        String winMessage = teamManager.getWinMessage();

        Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
        Bukkit.broadcastMessage(winMessage);
        Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

        // Teleport all players back to spawn
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }

        resetGame();
    }

    /**
     * End game with player defeat (all players dead/captured)
     */
    public void endGameWithPlayerDefeat() {
        gameRunning = false;

        Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
        Bukkit.broadcastMessage(ChatColor.RED + "プレイヤー全滅！鬼陣営の勝利！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

        // Teleport all players back to spawn
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }

        resetGame();
    }

    /**
     * End game with one-hit kill message (when only one player exists)
     */
    public void endGameWithOneHitKill() {
        gameRunning = false;

        Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
        Bukkit.broadcastMessage(ChatColor.RED + "一撃必殺！プレイヤー全滅！鬼陣営の勝利！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

        // Teleport all players back to spawn
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(spawnLocation);
        }

        resetGame();
    }

    /**
     * End game with player victory (more than half escaped)
     */
    public void endGameWithPlayerVictory() {
        gameRunning = false;

        // 実績解除の音と勝利表示
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            p.sendTitle(
                    ChatColor.GOLD + "プレイヤー陣営勝利！",
                    ChatColor.AQUA + "過半数のプレイヤーが脱出に成功！",
                    10, 70, 20
            );
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
        Bukkit.broadcastMessage(ChatColor.BLUE + "過半数のプレイヤーが脱出に成功！プレイヤー陣営の勝利！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

        // Teleport all players back to spawn
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }

        resetGame();
    }

    /**
     * End game with direct victory (player reached victory location)
     */
    public void endGameWithDirectVictory(Player player) {
        gameRunning = false;

        // 実績解除の音と勝利表示
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            p.sendTitle(
                    ChatColor.GOLD + "プレイヤー陣営勝利！",
                    ChatColor.AQUA + player.getName() + "が勝利地点に到達！",
                    10, 70, 20
            );
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
        Bukkit.broadcastMessage(ChatColor.BLUE + player.getName() + "が勝利地点に到達！プレイヤー陣営の勝利！");
        Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

        // Teleport all players back to spawn
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(spawnLocation);
        }

        resetGame();
    }

    /**
     * Reset the game completely
     */
    public void resetGame() {
        // 完全リセット
        fullReset();

        // Clear effects from all players
        effectManager.clearAllEffects();

        // Reset player inventories
        for (Player player : Bukkit.getOnlinePlayers()) {
            itemManager.clearPlayerInventory(player);
        }

        // Teleport all players to spawn
        Location spawnLocation = configManager.getInitialSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }
    }

    /**
     * Start the game timer
     */
    private void startGameTimer() {
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
        }

        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                remainingTime--;
                timerBar.setTitle("残り時間: " + remainingTime + "秒");
                timerBar.setProgress(Math.max(0, remainingTime / 500.0));
                updateScoreboard();

                if (remainingTime <= 0) {
                    endGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Teleport players to their team spawn locations
     */
    private void teleportPlayersToSpawns() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInOniTeam(p)) {
                // Oni spawn points
                Location[] oniSpawns = new Location[]{
                        new Location(p.getWorld(), -2, -4, -24)
                };
                int randomIndex = (int) (Math.random() * oniSpawns.length);
                p.teleport(oniSpawns[randomIndex]);
                p.setFoodLevel(2);  // Oni players have low food level
                p.setGameMode(GameMode.ADVENTURE);
            } else if (teamManager.isPlayerInPlayerTeam(p)) {
                // Player spawn
                p.teleport(new Location(p.getWorld(), 0, 2, 0));
                p.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    /**
     * Update the scoreboard
     */
    public void updateScoreboard() {
        teamManager.updateScoreboard(remainingTime, effectManager.getKakureDamaRemaining(),
                playerOpenedCountChests, playerRequiredCountChests, remainingChests);
    }

    /**
     * Update the remaining chests count
     */
    public void updateRemainingChests() {
        int totalChests = configManager.getTotalCountChests();
        int openedChests = configManager.getOpenedCountChestsCount();
        remainingChests = totalChests - openedChests;
        updateScoreboard();
    }

    /**
     * Handle player death and respawn
     */
    public void handlePlayerDeath(Player player) {
        if (!gameRunning || !teamManager.isPlayerInPlayerTeam(player)) {
            return;
        }

        // Switch to spectator mode
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.RED + "死亡したよ。5秒後に復活する…");
        updateScoreboard();

        // Check if any survivors remain
        int survivors = teamManager.countSurvivingPlayers();
        if (survivors == 0) {
            // Check if there was just one player in the game
            if (teamManager.getInitialPlayerCount() == 1) {
                endGameWithOneHitKill();
            } else {
                endGameWithPlayerDefeat();
            }
            return;
        }

        // Create respawn task
        respawnTasks.put(player.getUniqueId(), new BukkitRunnable() {
            @Override
            public void run() {
                if (gameRunning) {
                    player.setGameMode(GameMode.ADVENTURE);
                    player.sendMessage(ChatColor.GREEN + "復活したよ！");
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                    updateScoreboard();
                }
            }
        }.runTaskLater(plugin, 20L * 5));
    }

    /**
     * Handle player escape detection and victory location check
     */
    public void checkPlayerEscape(Player player) {
        if (!gameRunning || !teamManager.isPlayerInPlayerTeam(player)) {
            return;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (teamManager.getEscapedPlayers().contains(player.getUniqueId())) {
            return;
        }

        // 勝利地点判定 (0,0,7)
        Location playerLoc = player.getLocation();
        if (isAtVictoryLocation(playerLoc)) {
            player.sendMessage(ChatColor.GREEN + "勝利地点に到達しました！");
            Bukkit.broadcastMessage(ChatColor.AQUA + player.getName() + "が勝利地点に到達しました！");

            // Play sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // 即座にプレイヤー陣営勝利
            endGameWithDirectVictory(player);
            return;
        }

        // 通常の脱出地点判定（以前の実装）
        Location escapeLoc = configManager.getEscapeLocation();
        if (playerLoc.getWorld().equals(escapeLoc.getWorld()) &&
                playerLoc.distance(escapeLoc) <= 3) {

            // Check if the exit door has been opened
            if (exitDoorOpened) {
                // Player has escaped
                teamManager.addEscapedPlayer(player);
                player.sendMessage(ChatColor.GREEN + "脱出地点に到達しました！");
                Bukkit.broadcastMessage(ChatColor.AQUA + player.getName() + "が脱出地点に到達しました！");

                // Play sound
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // Update scoreboard
                updateScoreboard();

                // Check if enough players escaped for victory
                if (teamManager.haveHalfPlayersEscaped()) {
                    endGameWithPlayerVictory();
                }
            } else {
                // Exit door is not open yet
                player.sendMessage(ChatColor.RED + "出口のドアが閉まっています。鍵を使ってドアを開けましょう！");
            }
        }
    }

    /**
     * 勝利地点（0,0,7）にピッタリいるかどうかを判定
     */
    private boolean isAtVictoryLocation(Location location) {
        // 座標を四捨五入して整数に丸める（サーバーの浮動小数点の誤差を考慮）
        int x = (int) Math.round(location.getX());
        int y = (int) Math.round(location.getY());
        int z = (int) Math.round(location.getZ());

        // 座標がピッタリ(0,0,7)かどうか確認
        return x == 0 && y == 0 && z == 7 && location.getWorld().equals(victoryLocation.getWorld());
    }

    /**
     * Handle regular chest opened event
     */
    public void handleChestOpened(String chestName, Player player) {
        if (!gameRunning || teamManager.isPlayerInOniTeam(player)) return;

        if (!configManager.isChestOpened(chestName)) {
            configManager.setChestOpened(chestName, true);
            player.sendMessage(ChatColor.AQUA + "チェスト「" + chestName + "」を開封したよ！");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        }
    }

    /**
     * Handle count chest opened event
     */
    public void handleCountChestOpened(String chestName, Player player) {
        if (!gameRunning || teamManager.isPlayerInOniTeam(player)) return;

        UUID playerId = player.getUniqueId();

        if (!configManager.isCountChestOpened(chestName)) {
            configManager.setCountChestOpened(chestName, true);

            // Increment player's opened chest count
            int currentOpened = playerOpenedCountChests.getOrDefault(playerId, 0) + 1;
            playerOpenedCountChests.put(playerId, currentOpened);

            int required = playerRequiredCountChests.getOrDefault(playerId, configManager.getRequiredCountChests());

            player.sendMessage(ChatColor.GOLD + "カウントチェスト「" + chestName + "」を開封したよ！残り" +
                    (required - currentOpened) + "個。");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

            // Check if this player has opened enough chests
            if (currentOpened >= required) {
                player.sendMessage(ChatColor.GOLD + "必要な数のカウントチェストを開けた！出口の鍵を入手した。");

                // Give this player an exit key
                ItemStack exitKey = itemManager.createExitKeyItem();
                player.getInventory().addItem(exitKey);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }

            // 残りチェスト数を更新
            updateRemainingChests();

            // Update scoreboard
            updateScoreboard();
        }
    }

    /**
     * プレイヤーの近くのチェストを探知する
     * ←【修正済み】画面中央に表示、かつプレイヤーが近ければ追加音を再生
     */
    public void detectNearbyChests(Player oniPlayer) {
        if (!gameRunning || !teamManager.isPlayerInOniTeam(oniPlayer)) return;

        UUID oniUuid = oniPlayer.getUniqueId();

        // クールダウンチェック（デフォルトを40秒に変更）
        if (itemManager.isChestDetectorOnCooldown(oniUuid)) {
            int remainingTime = itemManager.getChestDetectorRemainingCooldown(oniUuid);
            oniPlayer.sendTitle(ChatColor.RED + "クールダウン中", "残り" + remainingTime + "秒", 0, 60, 0);
            return;
        }

        // 最も近いプレイヤーを探す
        Player nearestPlayer = null;
        double minDistance = Double.MAX_VALUE;
        boolean playerNearby = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                double distance = oniPlayer.getLocation().distance(p.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPlayer = p;
                }

                // 10ブロック以内にプレイヤーがいるかチェック
                if (distance <= 10) {
                    playerNearby = true;
                }
            }
        }

        // プレイヤーが見つかったら、そのプレイヤーに最も近いチェストを探す
        if (nearestPlayer != null) {
            String nearestChestName = null;
            double nearestChestDistance = Double.MAX_VALUE;
            Location playerLoc = nearestPlayer.getLocation();

            // 通常チェストをチェック
            for (Map.Entry<String, Location> entry : configManager.getChestLocations().entrySet()) {
                double distance = playerLoc.distance(entry.getValue());
                if (distance < nearestChestDistance) {
                    nearestChestDistance = distance;
                    nearestChestName = "通常チェスト「" + entry.getKey() + "」";
                }
            }

            // カウントチェストをチェック
            for (Map.Entry<String, Location> entry : configManager.getCountChestLocations().entrySet()) {
                double distance = playerLoc.distance(entry.getValue());
                if (distance < nearestChestDistance) {
                    nearestChestDistance = distance;
                    nearestChestName = "カウントチェスト「" + entry.getKey() + "」";
                }
            }

            // 結果を表示
            if (nearestChestName != null) {
                // 画面中央にタイトルで表示
                oniPlayer.sendTitle(ChatColor.RED + nearestChestName,
                        ChatColor.YELLOW + "距離: 約 " + Math.round(nearestChestDistance) + "ブロック", 0, 60, 0);

                // プレイヤーが近い（10ブロック以内）なら特別な音を再生
                if (playerNearby) {
                    // 緊迫感のある音を追加
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.2f);
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                } else {
                    // 通常の通知音
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                }
            } else {
                oniPlayer.sendTitle(ChatColor.RED + "チェスト未検出", "", 0, 60, 0);
            }
        } else {
            oniPlayer.sendTitle(ChatColor.RED + "プレイヤー未検出", "", 0, 60, 0);
        }

        // クールダウン設定（デフォルトを40秒に変更）
        itemManager.setChestDetectorCooldown(oniUuid);
    }



    /**
     * プレイヤーの近くのチェストにワープする
     */
    public void teleportToNearbyChest(Player oniPlayer) {
        if (!gameRunning || !teamManager.isPlayerInOniTeam(oniPlayer)) return;
        UUID oniUuid = oniPlayer.getUniqueId();

        // クールダウンチェック
        if (itemManager.isChestTeleporterOnCooldown(oniUuid)) {
            int remainingTime = itemManager.getChestTeleporterRemainingCooldown(oniUuid);
            oniPlayer.sendMessage(ChatColor.RED + "このアイテムはクールダウン中だぜ。残り" + remainingTime + "秒");
            return;
        }

        // 最も近いプレイヤーを探す＆近くにいるかチェック（10ブロック以内ならフラグON）
        Player nearestPlayer = null;
        double minDistance = Double.MAX_VALUE;
        boolean playerNearby = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                double distance = oniPlayer.getLocation().distance(p.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPlayer = p;
                }
                if (distance <= 10) {
                    playerNearby = true;
                }
            }
        }

        if (nearestPlayer != null) {
            Location nearestChestLoc = null;
            double nearestChestDistance = Double.MAX_VALUE;
            String chestName = null;
            Location playerLoc = nearestPlayer.getLocation();

            // 通常チェストをチェック
            for (Map.Entry<String, Location> entry : configManager.getChestLocations().entrySet()) {
                double distance = playerLoc.distance(entry.getValue());
                if (distance < nearestChestDistance) {
                    nearestChestDistance = distance;
                    nearestChestLoc = entry.getValue().clone();
                    chestName = "通常チェスト「" + entry.getKey() + "」";
                }
            }

            // カウントチェストをチェック
            for (Map.Entry<String, Location> entry : configManager.getCountChestLocations().entrySet()) {
                double distance = playerLoc.distance(entry.getValue());
                if (distance < nearestChestDistance) {
                    nearestChestDistance = distance;
                    nearestChestLoc = entry.getValue().clone();
                    chestName = "カウントチェスト「" + entry.getKey() + "」";
                }
            }

            if (nearestChestLoc != null) {
                // 安全なワープのためチェストの1ブロック上に移動
                nearestChestLoc.add(0, 1, 0);

                // ワープ前のエフェクト
                oniPlayer.playSound(oniPlayer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                // テレポート実行
                oniPlayer.teleport(nearestChestLoc);
                // ワープ後のメッセージ
                oniPlayer.sendMessage(ChatColor.RED + chestName + " にワープしたぜ！プレイヤー「" +
                        nearestPlayer.getName() + "」から約 " + Math.round(nearestChestDistance) + "ブロック離れてるぜ。");

                // 近くにプレイヤーがいたら特別な音を再生
                if (playerNearby) {
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.2f);
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                } else {
                    oniPlayer.playSound(oniPlayer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
                }
            } else {
                oniPlayer.sendMessage(ChatColor.RED + "近くにチェストが見つからなかったぜ。");
            }
        } else {
            oniPlayer.sendMessage(ChatColor.RED + "対象のプレイヤーが見つからなかったぜ。");
        }

        // クールダウン設定
        itemManager.setChestTeleporterCooldown(oniUuid);
    }


    /**
     * Open the main door
     */
    public void openDoor() {
        Location doorLoc = configManager.getDoorLocation();
        if (doorLoc == null) {
            sendConfigMessage(null, ChatColor.RED + "ドアが登録されていないので開けられません！");
            return;
        }

        Block block = doorLoc.getBlock();

        // ドアかどうかを確認
        if (!block.getType().toString().contains("DOOR")) {
            sendConfigMessage(null, ChatColor.RED + "登録された場所にドアがありません！");
            return;
        }

        // ドアを開く
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Openable) {
            Openable door = (Openable) blockData;
            door.setOpen(true);
            block.setBlockData(door);
            doorOpened = true;

            // Play sound for all players
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(doorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 1.0f);
            }

            Bukkit.broadcastMessage(ChatColor.GREEN + "メインのドアが開いた！奥のエリアへ進もう！");
        } else {
            // 従来の方法でドアを「破壊」する（フォールバック）
            block.setType(Material.AIR);
            doorOpened = true;

            // Play sound for all players
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(doorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 1.0f);
            }

            Bukkit.broadcastMessage(ChatColor.GREEN + "メインのドアが開いた！奥のエリアへ進もう！");
        }
    }

    /**
     * Open the exit door (and close it after 3 seconds)
     */
    public void openExitDoor() {
        Location exitDoorLoc = configManager.getExitDoorLocation();
        if (exitDoorLoc == null) {
            sendConfigMessage(null, ChatColor.RED + "出口用ドアが登録されていないので開けられません！");
            return;
        }

        Block block = exitDoorLoc.getBlock();

        // ドアかどうかを確認
        if (!block.getType().toString().contains("DOOR")) {
            sendConfigMessage(null, ChatColor.RED + "登録された場所にドアがありません！");
            return;
        }

        // ドアを開く
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Openable) {
            Openable door = (Openable) blockData;
            door.setOpen(true);
            block.setBlockData(door);
            exitDoorOpened = true;

            // Play sound for all players
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(exitDoorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 1.0f);
            }

            Bukkit.broadcastMessage(ChatColor.GREEN + "出口のドアが開いた！");

            // 既存の閉じるタスクをキャンセル
            if (exitDoorCloseTask != null) {
                exitDoorCloseTask.cancel();
                exitDoorCloseTask = null;
            }

            // 3秒後にドアを閉じるタスクを設定
            exitDoorCloseTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // ドアを閉じる
                    if (exitDoorOpened && block.getBlockData() instanceof Openable) {
                        Openable door = (Openable) block.getBlockData();
                        door.setOpen(false);
                        block.setBlockData(door);
                        exitDoorOpened = false;

                        // Play door close sound
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(exitDoorLoc, Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
                        }

                        // No message when door closes
                        exitDoorCloseTask = null;
                    }
                }
            }.runTaskLater(plugin, 3 * 20L); // 3秒 = 3 * 20 ticks
        } else {
            // 従来の方法でドアを「破壊」する（フォールバック）
            block.setType(Material.AIR);
            exitDoorOpened = true;

            // Play sound for all players
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(exitDoorLoc, Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 1.0f);
            }

            Bukkit.broadcastMessage(ChatColor.GREEN + "出口のドアが開いた！");

            // 3秒後にドアを戻すタスク（フォールバック方式）
            exitDoorCloseTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // ドアを元の状態に戻す
                    Material originalType = Material.OAK_DOOR; // デフォルトのドアタイプ
                    if (block.getType() == Material.AIR) {
                        block.setType(originalType);
                        exitDoorOpened = false;

                        // Play door close sound
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(exitDoorLoc, Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
                        }

                        // No message when door closes
                        exitDoorCloseTask = null;
                    }
                }
            }.runTaskLater(plugin, 3 * 20L); // 3秒
        }
    }

    /**
     * Send config messages only to minamottooooooooo
     */
    private void sendConfigMessage(Player sender, String message) {
        Player target = Bukkit.getPlayerExact("minamottooooooooo");
        if (target != null && target.isOnline()) {
            target.sendMessage(message);
        } else if (sender != null && sender.getName().equals("minamottooooooooo")) {
            sender.sendMessage(message);
        }
    }

    /**
     * Set the game time
     */
    public void setGameTime(int seconds) {
        if (seconds < 60) {
            sendConfigMessage(null, ChatColor.RED + "最低60秒以上に設定してください。");
            return;
        }

        remainingTime = seconds;
        sendConfigMessage(null, ChatColor.GREEN + "ゲーム時間を" + seconds + "秒に設定しました。");
    }

    // Getters and setters
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
}
