package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

public class TeamManager {
    private final OniGo plugin;
    private Team playerTeam;
    private Team oniTeam;
    private Scoreboard scoreboard;
    private Objective objective;

    private Set<UUID> escapedPlayers = new HashSet<>();
    private int initialPlayerCount = 0;

    // 出口ドア開けたプレイヤー（不要なら削除してOKだが、とりあえず保持）
    private Set<UUID> doorOpenedPlayers = new HashSet<>();

    // 鬼タイプ管理
    private Map<UUID, OniType> playerOniTypes = new HashMap<>();
    private Map<UUID, Integer> playerHitCounts = new HashMap<>(); // 攻撃カウント

    // プレイヤー準備状態追跡
    private Set<UUID> readyPlayers = new HashSet<>();

    public TeamManager(OniGo plugin) {
        this.plugin = plugin;
        setupScoreboard();
    }

    public void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        playerTeam = scoreboard.registerNewTeam("player");
        oniTeam = scoreboard.registerNewTeam("oni");

        playerTeam.setDisplayName("プレイヤー");
        oniTeam.setDisplayName("鬼");

        playerTeam.setColor(ChatColor.BLUE);
        oniTeam.setColor(ChatColor.RED);

        // ネームタグ非表示
        playerTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        oniTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        objective = scoreboard.registerNewObjective("gameStats", "dummy", ChatColor.GOLD + "鬼ごっこ");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void addPlayerToPlayerTeam(Player player) {
        oniTeam.removeEntry(player.getName());
        playerTeam.addEntry(player.getName());
        // プレイヤーチームは自動的に準備完了とする
        readyPlayers.add(player.getUniqueId());
    }

    public void addPlayerToOniTeam(Player player) {
        playerTeam.removeEntry(player.getName());
        oniTeam.addEntry(player.getName());
    }

    public void movePlayerToOniTeam(Player player) {
        if (playerTeam.hasEntry(player.getName())) {
            playerTeam.removeEntry(player.getName());
        }
        oniTeam.addEntry(player.getName());
    }

    public void setPlayerOniType(Player player, OniType type) {
        playerOniTypes.put(player.getUniqueId(), type);
        // 攻撃カウントをリセット
        playerHitCounts.put(player.getUniqueId(), 0);
        // 準備完了マーク
        readyPlayers.add(player.getUniqueId());
    }

    public OniType getPlayerOniType(Player player) {
        return playerOniTypes.getOrDefault(player.getUniqueId(), OniType.YASHA);
    }

    public int getPlayerHitCount(Player player) {
        return playerHitCounts.getOrDefault(player.getUniqueId(), 0);
    }

    public void incrementPlayerHitCount(Player player) {
        int count = getPlayerHitCount(player);
        playerHitCounts.put(player.getUniqueId(), count + 1);
    }

    public void resetPlayerHitCount(Player player) {
        playerHitCounts.put(player.getUniqueId(), 0);
    }

    public void resetTeams() {
        escapedPlayers.clear();
        doorOpenedPlayers.clear();
        playerOniTypes.clear();
        playerHitCounts.clear();
        readyPlayers.clear();

        if (playerTeam != null) playerTeam.unregister();
        if (oniTeam != null) oniTeam.unregister();
        if (objective != null) objective.unregister();
        setupScoreboard();
    }

    public void setupOniStart(Player oniPlayer) {
        oniTeam.addEntry(oniPlayer.getName());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getName().equals(oniPlayer.getName())) {
                if (!playerTeam.hasEntry(p.getName()) && !oniTeam.hasEntry(p.getName())) {
                    playerTeam.addEntry(p.getName());
                }
            }
        }
    }

    public boolean areAnyPlayersUnassigned() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!playerTeam.hasEntry(p.getName()) && !oniTeam.hasEntry(p.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean areAllPlayersReady() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName()) || oniTeam.hasEntry(p.getName())) {
                if (!readyPlayers.contains(p.getUniqueId())) {
                    return false;
                }
            }
        }
        return true;
    }

    public void initializeGameState() {
        escapedPlayers.clear();
        initialPlayerCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName())) {
                initialPlayerCount++;
            }
        }
    }

    public void addEscapedPlayer(Player player) {
        escapedPlayers.add(player.getUniqueId());
    }

    public boolean haveHalfPlayersEscaped() {
        return escapedPlayers.size() > initialPlayerCount / 2;
    }

    public boolean isPlayerInPlayerTeam(Player p) {
        return playerTeam.hasEntry(p.getName());
    }

    public boolean isPlayerInOniTeam(Player p) {
        return oniTeam.hasEntry(p.getName());
    }

    public int countSurvivingPlayers() {
        int survivors = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName()) && p.getGameMode() != GameMode.SPECTATOR) {
                survivors++;
            }
        }
        return survivors;
    }

    public void updateScoreboard(int remainingTime,
                                 Map<UUID, Integer> kakureDamaRemaining,
                                 Map<UUID, Integer> playerOpenedCountChests,
                                 Map<UUID, Integer> playerRequiredCountChests,
                                 int remainingChests) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = scoreboard;
            Objective obj = objective;

            // いったん既存スコアをリセット
            for (String e : sb.getEntries()) {
                sb.resetScores(e);
            }

            // 残り時間
            Score timeScore = obj.getScore(ChatColor.YELLOW + "残り時間: " + remainingTime + "秒");
            timeScore.setScore(15);

            // 生存者数
            int surv = countSurvivingPlayers();
            Score survScore = obj.getScore(ChatColor.GREEN + "生存者: " + surv + "人");
            survScore.setScore(14);

            // 脱出者数
            Score escapedScore = obj.getScore(ChatColor.AQUA + "脱出者: " + escapedPlayers.size() + "人");
            escapedScore.setScore(13);

            // **両陣営とも「残りチェスト数」を表示**
            Score chestRemainScore = obj.getScore(ChatColor.GOLD + "残りチェスト: " + remainingChests + "個");
            chestRemainScore.setScore(12);

            if (isPlayerInPlayerTeam(p)) {
                // 個別：カウントチェスト進捗
                UUID pid = p.getUniqueId();
                int opened = playerOpenedCountChests.getOrDefault(pid, 0);
                int req = playerRequiredCountChests.getOrDefault(pid, 0);

                Score countChestScore = obj.getScore(ChatColor.GOLD + "チェスト進捗: " + opened + "/" + req);
                countChestScore.setScore(11);

                // 隠れ玉残秒
                int kdRemain = kakureDamaRemaining.getOrDefault(pid, 0);
                Score kdScore = obj.getScore(ChatColor.AQUA + "隠れ玉: " + kdRemain + "秒");
                kdScore.setScore(10);
            }

            // チーム人数
            Score oniScore = obj.getScore(ChatColor.RED + "鬼チーム: " + oniTeam.getSize() + "人");
            oniScore.setScore(6);
            Score plScore = obj.getScore(ChatColor.BLUE + "プレイヤー: " + playerTeam.getSize() + "人");
            plScore.setScore(5);

            // 勝利条件
            Score line = obj.getScore(ChatColor.LIGHT_PURPLE + "-------------");
            line.setScore(4);
            Score line2 = obj.getScore(ChatColor.WHITE + "過半数脱出 or 時間切れ");
            line2.setScore(3);

            p.setScoreboard(sb);
        }
    }

    // オーバーロード（古い呼び出し用）
    public void updateScoreboard(int remainingTime,
                                 Map<UUID, Integer> kakureDamaRemaining,
                                 Map<UUID, Integer> playerOpenedCountChests,
                                 Map<UUID, Integer> playerRequiredCountChests) {
        updateScoreboard(remainingTime, kakureDamaRemaining, playerOpenedCountChests, playerRequiredCountChests, 0);
    }

    public String getWinMessage() {
        int halfInitial = initialPlayerCount / 2;
        int plCount = playerTeam.getSize();
        if (plCount > halfInitial) {
            return ChatColor.BLUE + "プレイヤーの勝利！";
        } else if (plCount == halfInitial) {
            return ChatColor.YELLOW + "引き分け？！";
        } else {
            return ChatColor.RED + "鬼の勝利！";
        }
    }

    public int getInitialPlayerCount() {
        return initialPlayerCount;
    }

    public Set<UUID> getEscapedPlayers() {
        return escapedPlayers;
    }

    public Set<UUID> getDoorOpenedPlayers() {
        return doorOpenedPlayers;
    }
}