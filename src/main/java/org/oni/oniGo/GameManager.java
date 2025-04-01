package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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

    // Player respawn tasks
    private Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

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
            sender.sendMessage(ChatColor.RED + "選択していない人がいるよ。");
            return false;
        }

        if (gameRunning) {
            sender.sendMessage(ChatColor.RED + "ゲームはすでに進行中だよ！");
            return false;
        }

        // Clear inventories to be safe
        for (Player p : Bukkit.getOnlinePlayers()) {
            itemManager.clearPlayerInventory(p);
        }

        // Initialize game state
        gameRunning = true;
        remainingTime = 500;
        teamManager.initializeGameState();

        // Reset all chest statuses
        configManager.resetChests();

        // Initialize kakure dama (hiding orb) timers
        effectManager.initializeKakureDama(15);

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

        Bukkit.broadcastMessage(ChatColor.GREEN + "ゲーム開始！残り時間: " + remainingTime + "秒");
        return true;
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
            sender.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
            return;
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム中断 =========");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "管理者によりゲーム中断されたよ");
        Bukkit.broadcastMessage(ChatColor.GOLD + "============================");

        resetGame();
        sender.sendMessage(ChatColor.GREEN + "ゲームを停止したよ！");
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }

        resetGame();
    }

    /**
     * End game with player victory (more than half escaped)
     */
    public void endGameWithPlayerVictory() {
        gameRunning = false;

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
     * Reset the game completely
     */
    public void resetGame() {
        gameRunning = false;

        // Cancel timer task
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }

        // Cancel all respawn tasks
        for (UUID uuid : new ArrayList<>(respawnTasks.keySet())) {
            BukkitTask task = respawnTasks.get(uuid);
            if (task != null) {
                task.cancel();
            }
        }
        respawnTasks.clear();

        // Remove timer bar from all players
        if (timerBar != null) {
            timerBar.removeAll();
        }

        // Clear effects from all players
        effectManager.clearAllEffects();

        // Reset player states
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setFoodLevel(20);
            itemManager.clearPlayerInventory(player);
        }

        // Reset teams
        teamManager.resetTeams();

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
        teamManager.updateScoreboard(remainingTime, effectManager.getKakureDamaRemaining());
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
     * Handle player escape detection
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

        // Check if player is near escape location
        Location playerLoc = player.getLocation();
        Location escapeLoc = configManager.getEscapeLocation();

        if (playerLoc.getWorld().equals(escapeLoc.getWorld()) &&
                playerLoc.distance(escapeLoc) <= 3) {

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
        }
    }

    /**
     * Handle chest opened event
     */
    public void handleChestOpened(String chestName, Player player) {
        if (!gameRunning) return;

        if (!configManager.isChestOpened(chestName)) {
            configManager.setChestOpened(chestName, true);
            player.sendMessage(ChatColor.AQUA + "チェスト「" + chestName + "」を開封したよ！");

            // Check if all chests opened
            checkAllChestsOpened();
        }
    }

    /**
     * Check if all chests opened and open door if so
     */
    private void checkAllChestsOpened() {
        if (configManager.areAllChestsOpened()) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "すべてのチェストが開けられた！玄関のドアが開くよ！");
            openDoor();
        }
    }

    /**
     * Open the exit door
     */
    private void openDoor() {
        Location doorLoc = configManager.getDoorLocation();
        if (doorLoc == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "ドアが登録されていないので開けられません！");
            return;
        }

        Block block = doorLoc.getBlock();
        block.setType(Material.AIR);
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
}