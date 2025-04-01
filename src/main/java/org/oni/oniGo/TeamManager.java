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
     * Update scoreboard display with count chest info
     */
    public void updateScoreboard(int remainingTime, Map<UUID, Integer> kakureDamaRemaining,
                                 int openedCountChests, int requiredCountChests) {
        // Clear existing entries
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        // Display remaining time
        Score timeScore = objective.getScore(ChatColor.YELLOW + "残り時間: " + remainingTime + "秒");
        timeScore.setScore(9);

        // Count survivors (players not in spectator mode)
        int survivors = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName()) && p.getGameMode() != GameMode.SPECTATOR) {
                survivors++;
            }
        }
        Score survivorsScore = objective.getScore(ChatColor.GREEN + "生存者数: " + survivors + "人");
        survivorsScore.setScore(8);

        // Escaped players
        Score escapedScore = objective.getScore(ChatColor.AQUA + "脱出者数: " + escapedPlayers.size() + "人");
        escapedScore.setScore(7);

        // Count chest progress
        Score countChestScore = objective.getScore(ChatColor.GOLD + "カウントチェスト: " + openedCountChests + "/" + requiredCountChests);
        countChestScore.setScore(6);

        // Team counts
        Score oniScore = objective.getScore(ChatColor.RED + "鬼: " + oniTeam.getSize() + "人");
        oniScore.setScore(5);

        Score playerScore = objective.getScore(ChatColor.BLUE + "プレイヤー: " + playerTeam.getSize() + "人");
        playerScore.setScore(4);

        // Show individual hiding orb times
        int i = 3;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName())) {
                int rem = kakureDamaRemaining.getOrDefault(p.getUniqueId(), 0);
                Score kdScore = objective.getScore(ChatColor.AQUA + p.getName() + ": 隠れ玉 " + rem + "秒");
                kdScore.setScore(i);
                i--;
                if (i < 0) break; // 表示制限
            }
        }

        // Apply scoreboard to all players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(scoreboard);
        }
    }

    /**
     * 古いupdateScoreboardメソッド（互換性のために残す）
     */
    public void updateScoreboard(int remainingTime, Map<UUID, Integer> kakureDamaRemaining) {
        // デフォルトの値でカウントチェスト情報を追加
        updateScoreboard(remainingTime, kakureDamaRemaining, 0, 0);
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
}