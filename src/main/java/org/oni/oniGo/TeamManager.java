package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamManager {
    private final OniGo plugin;

    private Team playerTeam;
    private Team oniTeam;
    private Scoreboard scoreboard;
    private Objective objective;

    // Track escaped players
    private Set<UUID> escapedPlayers = new HashSet<>();
    private int initialPlayerCount = 0;

    public TeamManager(OniGo plugin) {
        this.plugin = plugin;
        setupScoreboard();
    }

    /**
     * Create teams and setup scoreboard
     */
    public void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        playerTeam = scoreboard.registerNewTeam("player");
        oniTeam = scoreboard.registerNewTeam("oni");

        playerTeam.setDisplayName("プレイヤー");
        oniTeam.setDisplayName("鬼");

        playerTeam.setColor(ChatColor.BLUE);
        oniTeam.setColor(ChatColor.RED);

        // Hide nametags
        playerTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        oniTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        objective = scoreboard.registerNewObjective("gameStats", "dummy", ChatColor.GOLD + "鬼ごっこ");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    /**
     * Add player to player team
     */
    public void addPlayerToPlayerTeam(Player player) {
        oniTeam.removeEntry(player.getName());
        playerTeam.addEntry(player.getName());
    }

    /**
     * Add player to oni team
     */
    public void addPlayerToOniTeam(Player player) {
        playerTeam.removeEntry(player.getName());
        oniTeam.addEntry(player.getName());
    }

    /**
     * Move player from player team to oni team
     */
    public void movePlayerToOniTeam(Player player) {
        playerTeam.removeEntry(player.getName());
        oniTeam.addEntry(player.getName());
    }

    /**
     * Reset teams (unregister and setup again)
     */
    public void resetTeams() {
        escapedPlayers.clear();

        if (playerTeam != null) playerTeam.unregister();
        if (oniTeam != null) oniTeam.unregister();
        if (objective != null) objective.unregister();

        setupScoreboard();
    }

    /**
     * Add all players except one to player team, and that one to oni team
     */
    public void setupOniStart(Player oniPlayer) {
        playerTeam.removeEntry(oniPlayer.getName());
        oniTeam.addEntry(oniPlayer.getName());

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getName().equals(oniPlayer.getName())) {
                if (!playerTeam.hasEntry(p.getName()) && !oniTeam.hasEntry(p.getName())) {
                    playerTeam.addEntry(p.getName());
                }
            }
        }
    }

    /**
     * Check if any players haven't selected a team
     */
    public boolean areAnyPlayersUnassigned() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!playerTeam.hasEntry(p.getName()) && !oniTeam.hasEntry(p.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Initialize the escape tracking and count initial players
     */
    public void initializeGameState() {
        escapedPlayers.clear();

        // Count initial players in player team
        initialPlayerCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName())) {
                initialPlayerCount++;
            }
        }
    }

    /**
     * Add player to escaped list
     */
    public void addEscapedPlayer(Player player) {
        escapedPlayers.add(player.getUniqueId());
    }

    /**
     * Check if more than half of initial players have escaped
     */
    public boolean haveHalfPlayersEscaped() {
        return escapedPlayers.size() > initialPlayerCount / 2;
    }

    /**
     * Check if a player is in player team
     */
    public boolean isPlayerInPlayerTeam(Player player) {
        return playerTeam.hasEntry(player.getName());
    }

    /**
     * Check if a player is in oni team
     */
    public boolean isPlayerInOniTeam(Player player) {
        return oniTeam.hasEntry(player.getName());
    }

    /**
     * Update the scoreboard
     */
    public void updateScoreboard(int remainingTime, Map<UUID, Integer> kakureDamaRemaining,
                                 Map<UUID, Integer> playerOpenedCountChests,
                                 Map<UUID, Integer> playerRequiredCountChests,
                                 int remainingChests) {
        // Apply different scoreboards for different teams
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard playerScoreboard = scoreboard; // shared scoreboard reference
            Objective playerObjective = objective;    // shared objective reference

            // Clear existing entries for this player
            for (String entry : playerScoreboard.getEntries()) {
                playerScoreboard.resetScores(entry);
            }

            // Display remaining time
            Score timeScore = playerObjective.getScore(ChatColor.YELLOW + "残り時間: " + remainingTime + "秒");
            timeScore.setScore(14);

            // Count survivors (players not in spectator mode)
            int survivors = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (playerTeam.hasEntry(target.getName()) && target.getGameMode() != GameMode.SPECTATOR) {
                    survivors++;
                }
            }
            Score survivorsScore = playerObjective.getScore(ChatColor.GREEN + "生存者数: " + survivors + "人");
            survivorsScore.setScore(13);

            // Escaped players
            Score escapedScore = playerObjective.getScore(ChatColor.AQUA + "脱出者数: " + escapedPlayers.size() + "人");
            escapedScore.setScore(12);

            // 残りチェスト数（プレイヤーチームのみ）
            if (isPlayerInPlayerTeam(p) && !isPlayerInOniTeam(p)) {
                Score remainingChestsScore = playerObjective.getScore(ChatColor.GOLD + "残りチェスト: " + remainingChests + "個");
                remainingChestsScore.setScore(11);
            }

            // Special information only for player team
            if (isPlayerInPlayerTeam(p) && !isPlayerInOniTeam(p)) {
                // Individual player chest progress
                UUID playerId = p.getUniqueId();
                int opened = playerOpenedCountChests.getOrDefault(playerId, 0);
                int required = playerRequiredCountChests.getOrDefault(playerId, 0);

                Score countChestScore = playerObjective.getScore(ChatColor.GOLD + "カウントチェスト: " + opened + "/" + required);
                countChestScore.setScore(10);

                // Show individual hiding orb time
                int rem = kakureDamaRemaining.getOrDefault(playerId, 0);
                Score kdScore = playerObjective.getScore(ChatColor.AQUA + "隠れ玉: " + rem + "秒");
                kdScore.setScore(9);

                // プレイヤー緊急脱出アイテムのクールダウン
                ItemManager itemManager = plugin.getItemManager();
                int playerEscapeCooldown = itemManager.getPlayerEscapeRemainingCooldown(playerId);
                Score escapeScore = playerObjective.getScore(ChatColor.BLUE + "緊急脱出: " +
                        (playerEscapeCooldown > 0 ? playerEscapeCooldown + "秒" : "準備完了"));
                escapeScore.setScore(8);
            }

            // 鬼チーム用のクールダウン表示
            if (isPlayerInOniTeam(p)) {
                UUID playerId = p.getUniqueId();
                ItemManager itemManager = plugin.getItemManager();

                // チェスト探知アイテムのクールダウン
                int detectorCooldown = itemManager.getChestDetectorRemainingCooldown(playerId);
                Score detectorScore = playerObjective.getScore(ChatColor.RED + "探知コンパス: " +
                        (detectorCooldown > 0 ? detectorCooldown + "秒" : "準備完了"));
                detectorScore.setScore(10);

                // チェストワープアイテムのクールダウン
                int teleporterCooldown = itemManager.getChestTeleporterRemainingCooldown(playerId);
                Score teleporterScore = playerObjective.getScore(ChatColor.RED + "チェストワープ: " +
                        (teleporterCooldown > 0 ? teleporterCooldown + "秒" : "準備完了"));
                teleporterScore.setScore(9);
            }

            // Team counts for everyone
            Score oniScore = playerObjective.getScore(ChatColor.RED + "鬼: " + oniTeam.getSize() + "人");
            oniScore.setScore(6);

            Score playerScore = playerObjective.getScore(ChatColor.BLUE + "プレイヤー: " + playerTeam.getSize() + "人");
            playerScore.setScore(5);

            // 勝利条件の説明
            Score winConditionScore = playerObjective.getScore(ChatColor.LIGHT_PURPLE + "----------");
            winConditionScore.setScore(4);

            Score winCondition1 = playerObjective.getScore(ChatColor.WHITE + "・過半数脱出で勝利");
            winCondition1.setScore(3);

            Score winCondition3 = playerObjective.getScore(ChatColor.LIGHT_PURPLE + "----------");
            winCondition3.setScore(1);

            // Apply updated scoreboard to this player
            p.setScoreboard(playerScoreboard);
        }
    }

    /**
     * 互換性のためのオーバーロードメソッド
     */
    public void updateScoreboard(int remainingTime, Map<UUID, Integer> kakureDamaRemaining,
                                 Map<UUID, Integer> playerOpenedCountChests,
                                 Map<UUID, Integer> playerRequiredCountChests) {
        updateScoreboard(remainingTime, kakureDamaRemaining, playerOpenedCountChests, playerRequiredCountChests, 0);
    }

    /**
     * Get the winning team message based on player count
     */
    public String getWinMessage() {
        int halfInitial = initialPlayerCount / 2;
        int playerCount = playerTeam.getSize();

        if (playerCount > halfInitial) {
            return ChatColor.BLUE + "プレイヤー陣営の勝利！";
        } else if (playerCount == halfInitial) {
            return ChatColor.YELLOW + "引き分け！";
        } else {
            return ChatColor.RED + "鬼陣営の勝利！";
        }
    }

    /**
     * Count surviving players (not in spectator mode)
     */
    public int countSurvivingPlayers() {
        int survivors = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName()) && p.getGameMode() != GameMode.SPECTATOR) {
                survivors++;
            }
        }
        return survivors;
    }

    // Getters
    public Team getPlayerTeam() {
        return playerTeam;
    }

    public Team getOniTeam() {
        return oniTeam;
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    public int getInitialPlayerCount() {
        return initialPlayerCount;
    }

    public Set<UUID> getEscapedPlayers() {
        return escapedPlayers;
    }
    // 出口ドアを開けたプレイヤーの追跡用セット
    private Set<UUID> doorOpenedPlayers = new HashSet<>();

    /**
     * 出口ドアを開けたプレイヤーを追加
     */
    public void addDoorOpenedPlayer(Player player) {
        doorOpenedPlayers.add(player.getUniqueId());
    }

    /**
     * 過半数のプレイヤーが出口ドアを開けたかチェック
     */
    public boolean haveHalfPlayersDoorOpened() {
        return doorOpenedPlayers.size() > initialPlayerCount / 2;
    }

    /**
     * 出口ドアを開けたプレイヤーリストをリセット
     */
    public void resetDoorOpenedPlayers() {
        doorOpenedPlayers.clear();
    }

    /**
     * 出口ドアを開けたプレイヤー数を取得
     */
    public int getDoorOpenedPlayerCount() {
        return doorOpenedPlayers.size();
    }
}

