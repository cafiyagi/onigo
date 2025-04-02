package org.oni.oniGo;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.GameMode;
import java.util.*;

public class ItemManager {
    private final OniGo plugin;
    private final TeamManager teamManager;

    // クールダウン管理
    private Map<UUID, Long> chestDetectorCooldowns = new HashMap<>();
    private Map<UUID, Long> chestTeleporterCooldowns = new HashMap<>();
    private Map<UUID, Long> playerEscapeCooldowns = new HashMap<>();

    // ドローン使用回数
    private Map<UUID, Integer> droneUsages = new HashMap<>();

    // クールダウン時間
    private static final long DETECTOR_COOLDOWN_MS = 60000;       // 60秒
    private static final long TELEPORTER_COOLDOWN_MS = 120000;    // 120秒
    private static final long PLAYER_ESCAPE_COOLDOWN_MS = 60000;  // 60秒

    public ItemManager(OniGo plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    /**
     * 全アイテム付与
     */
    public void giveAllGameItems(Player player) {
        // 夜叉アイテム
        player.getInventory().addItem(createYashaItem());
        // 隠れ玉
        player.getInventory().addItem(createKakureDamaItem());
        // 鬼アイテム
        player.getInventory().addItem(createChestDetectorItem());
        player.getInventory().addItem(createChestTeleporterItem());
        // プレイヤー緊急脱出
        player.getInventory().addItem(createPlayerEscapeItem());
        // チーム選択本
        player.getInventory().addItem(createTeamSelectBook());
        // ゲームスタート本
        player.getInventory().addItem(createGameStartBook());
        // チェスト設定本
        player.getInventory().addItem(createChestCountBook());
        // 時間設定本
        player.getInventory().addItem(createGameTimeBook());
        // 出口の鍵
        player.getInventory().addItem(createExitKeyItem());
        // ドローンコントローラー
        player.getInventory().addItem(createDroneControllerItem());

        player.sendMessage(ChatColor.GREEN + "ゲームアイテムが付与されたよ！");
    }

    /**
     * 陣営選択本を全員へ
     */
    public void distributeTeamSelectionBooks() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.getInventory().addItem(createTeamSelectBook());
        }
    }

    /**
     * スタート本を個人に
     */
    public void giveGameStartBook(String playerName) {
        Player p = plugin.getServer().getPlayer(playerName);
        if (p != null && p.isOnline()) {
            p.getInventory().addItem(createGameStartBook());
        }
    }

    /**
     * チェスト設定本を個人に
     */
    public void giveChestCountBook(String playerName) {
        Player p = plugin.getServer().getPlayer(playerName);
        if (p != null && p.isOnline()) {
            p.getInventory().addItem(createChestCountBook());
        }
    }

    /**
     * ゲーム時間設定本を個人に
     */
    public void giveGameTimeBook(String playerName) {
        Player p = plugin.getServer().getPlayer(playerName);
        if (p != null && p.isOnline()) {
            p.getInventory().addItem(createGameTimeBook());
        }
    }

    /**
     * チーム別アイテム配布
     */
    public void distributeTeamItems() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            clearPlayerInventory(p);
            if (teamManager.isPlayerInOniTeam(p)) {
                // 鬼
                p.getInventory().addItem(createYashaItem());
                p.getInventory().addItem(createChestDetectorItem());
                p.getInventory().addItem(createChestTeleporterItem());
                p.sendMessage(ChatColor.RED + "鬼用アイテム配布！");
            } else if (teamManager.isPlayerInPlayerTeam(p)) {
                // プレイヤー
                p.getInventory().addItem(createKakureDamaItem());
                p.getInventory().addItem(createPlayerEscapeItem());
                p.getInventory().addItem(createDroneControllerItem()); // ドローン
                droneUsages.put(p.getUniqueId(), 5); // ドローン使用回数=5回
                p.sendMessage(ChatColor.BLUE + "プレイヤー用アイテム配布！");
            }
        }
    }

    public void clearPlayerInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
    }

    public ItemStack createYashaItem() {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.setDisplayName("夜叉");
        star.setItemMeta(meta);
        return star;
    }

    public ItemStack createKakureDamaItem() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "隠れ玉");
        meta.setLore(Collections.singletonList("右クリックで透明化/再度右クリックで解除"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createChestDetectorItem() {
        ItemStack item = new ItemStack(Material.BLUE_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "プレイヤー探知コンパス");
        meta.setLore(Arrays.asList("右クリック: プレイヤー付近のチェストを探知", "クールダウン60秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createChestTeleporterItem() {
        ItemStack item = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "チェストワープの真珠");
        meta.setLore(Arrays.asList("右クリック: プレイヤー付近のチェストにワープ", "クールダウン120秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createPlayerEscapeItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.BLUE + "緊急脱出アイテム");
        meta.setLore(Arrays.asList("鬼が近くにいる時に右クリック", "チェスト付近へランダムワープ", "クールダウン60秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createExitKeyItem() {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "出口の鍵");
        meta.setLore(Arrays.asList("カウントチェスト開封で入手", "出口ドアを開けるために使用"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createTeamSelectBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("陣営選択本");
        meta.setAuthor("鬼ごっこプラグイン");
        meta.setPages(
                "右クリックでGUIを開いて陣営を選択してください。\n\n" +
                        "・左の紙: プレイヤー陣営\n" +
                        "・右の紙: 鬼陣営"
        );
        book.setItemMeta(meta);
        return book;
    }

    public ItemStack createGameStartBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("ゲームスタート本");
        meta.setAuthor("鬼ごっこプラグイン");
        meta.setPages(
                "「通常スタート」「鬼スタート」「ランダム鬼スタート」\n" +
                        "それぞれのモードを選択してゲームを開始できます。"
        );
        book.setItemMeta(meta);
        return book;
    }

    public ItemStack createChestCountBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("チェスト設定本");
        meta.setAuthor("鬼ごっこプラグイン");
        meta.setPages(
                "カウントチェスト数の設定を変更できます。\n" +
                        "「簡単(1個)」「普通(3個)」「難しい(5個)」「カスタム」"
        );
        book.setItemMeta(meta);
        return book;
    }

    public ItemStack createGameTimeBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("ゲーム時間設定本");
        meta.setAuthor("鬼ごっこプラグイン");
        meta.setPages(
                "「180秒」「300秒」「600秒」「カスタム」\n" +
                        "いずれかを選択してゲーム時間を設定します。"
        );
        book.setItemMeta(meta);
        return book;
    }

    // **追加** ドローンコントローラー
    public ItemStack createDroneControllerItem() {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "ドローンコントローラー");
        meta.setLore(Arrays.asList(
                "右クリックで5秒間ドローン視点に切り替え",
                "Oni（鬼）を見つけると5秒間ハイライト！",
                "使用回数: 最大5回"
        ));
        item.setItemMeta(meta);
        return item;
    }

    // 使用開始
    public void startDroneMode(Player player) {
        UUID pid = player.getUniqueId();
        int usageLeft = droneUsages.getOrDefault(pid, 0);
        if (usageLeft <= 0) {
            player.sendMessage(ChatColor.RED + "ドローンの使用回数が残っていないよ！");
            return;
        }
        usageLeft--;
        droneUsages.put(pid, usageLeft);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "ドローン起動！残り使用回数: " + usageLeft);

        // ドローン用のArmorStand生成（見た目は非表示＆重力なし）
        final ArmorStand drone = player.getWorld().spawn(player.getLocation().clone().add(0, 1, 0), ArmorStand.class);
        drone.setInvisible(true);
        drone.setGravity(false);
        drone.setInvulnerable(true);
        drone.setMarker(true);

        // プレイヤーが向いてる方向を取得（org.bukkit.util.Vectorを使用）
        final org.bukkit.util.Vector direction = player.getLocation().getDirection().normalize();

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 100) { // 5秒間（5*20ティック）
                    drone.remove();
                    player.sendMessage(ChatColor.GRAY + "ドローン操作が終了したよ");
                    cancel();
                    return;
                }
                // ドローンを向いてる方向に0.5ブロックずつ移動
                drone.teleport(drone.getLocation().add(direction.clone().multiply(0.5)));
                ticks++;

                // ドローン近くに鬼がいるかチェック（半径5ブロック以内）
                for (Player p : drone.getWorld().getPlayers()) {
                    if (teamManager.isPlayerInOniTeam(p)) {
                        if (p.getLocation().distance(drone.getLocation()) <= 5) {
                            player.sendMessage(ChatColor.RED + "鬼が近くにいる！");
                            break;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 判別系
     */
    public boolean isYashaItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.NETHER_STAR &&
                item.hasItemMeta() &&
                "夜叉".equals(item.getItemMeta().getDisplayName());
    }

    public boolean isKakureDamaItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.DIAMOND &&
                item.hasItemMeta() &&
                (ChatColor.AQUA + "隠れ玉").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isChestDetectorItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.BLUE_WOOL &&
                item.hasItemMeta() &&
                (ChatColor.RED + "プレイヤー探知コンパス").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isChestTeleporterItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.GREEN_WOOL &&
                item.hasItemMeta() &&
                (ChatColor.RED + "チェストワープの真珠").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isPlayerEscapeItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.NETHER_STAR &&
                item.hasItemMeta() &&
                (ChatColor.BLUE + "緊急脱出アイテム").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isExitKeyItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.TRIPWIRE_HOOK &&
                item.hasItemMeta() &&
                (ChatColor.GOLD + "出口の鍵").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isTeamSelectBook(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.WRITTEN_BOOK) return false;
        if (!item.hasItemMeta()) return false;
        if (!(item.getItemMeta() instanceof BookMeta)) return false;
        BookMeta bm = (BookMeta) item.getItemMeta();
        return "陣営選択本".equals(bm.getTitle());
    }

    public boolean isGameStartBook(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.WRITTEN_BOOK) return false;
        if (!item.hasItemMeta()) return false;
        if (!(item.getItemMeta() instanceof BookMeta)) return false;
        BookMeta bm = (BookMeta) item.getItemMeta();
        return "ゲームスタート本".equals(bm.getTitle());
    }

    public boolean isChestCountBook(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.WRITTEN_BOOK) return false;
        if (!item.hasItemMeta()) return false;
        if (!(item.getItemMeta() instanceof BookMeta)) return false;
        BookMeta bm = (BookMeta) item.getItemMeta();
        return "チェスト設定本".equals(bm.getTitle());
    }

    public boolean isGameTimeBook(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.WRITTEN_BOOK) return false;
        if (!item.hasItemMeta()) return false;
        if (!(item.getItemMeta() instanceof BookMeta)) return false;
        BookMeta bm = (BookMeta) item.getItemMeta();
        return "ゲーム時間設定本".equals(bm.getTitle());
    }

    // ドローンコントローラーかどうか
    public boolean isDroneControllerItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.HEART_OF_THE_SEA &&
                item.hasItemMeta() &&
                (ChatColor.LIGHT_PURPLE + "ドローンコントローラー").equals(item.getItemMeta().getDisplayName());
    }

    /**
     * クールダウン関連
     */
    public boolean isChestDetectorOnCooldown(UUID uuid) {
        if (!chestDetectorCooldowns.containsKey(uuid)) return false;
        long lastUsed = chestDetectorCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < DETECTOR_COOLDOWN_MS;
    }
    public void setChestDetectorCooldown(UUID uuid) {
        chestDetectorCooldowns.put(uuid, System.currentTimeMillis());
    }
    public int getChestDetectorRemainingCooldown(UUID uuid) {
        if (!chestDetectorCooldowns.containsKey(uuid)) return 0;
        long lastUsed = chestDetectorCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= DETECTOR_COOLDOWN_MS) return 0;
        return (int)((DETECTOR_COOLDOWN_MS - elapsed) / 1000);
    }

    public boolean isChestTeleporterOnCooldown(UUID uuid) {
        if (!chestTeleporterCooldowns.containsKey(uuid)) return false;
        long lastUsed = chestTeleporterCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < TELEPORTER_COOLDOWN_MS;
    }
    public void setChestTeleporterCooldown(UUID uuid) {
        chestTeleporterCooldowns.put(uuid, System.currentTimeMillis());
    }
    public int getChestTeleporterRemainingCooldown(UUID uuid) {
        if (!chestTeleporterCooldowns.containsKey(uuid)) return 0;
        long lastUsed = chestTeleporterCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= TELEPORTER_COOLDOWN_MS) return 0;
        return (int)((TELEPORTER_COOLDOWN_MS - elapsed) / 1000);
    }

    public boolean isPlayerEscapeOnCooldown(UUID uuid) {
        if (!playerEscapeCooldowns.containsKey(uuid)) return false;
        long lastUsed = playerEscapeCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < PLAYER_ESCAPE_COOLDOWN_MS;
    }
    public void setPlayerEscapeCooldown(UUID uuid) {
        playerEscapeCooldowns.put(uuid, System.currentTimeMillis());
    }
    public int getPlayerEscapeRemainingCooldown(UUID uuid) {
        if (!playerEscapeCooldowns.containsKey(uuid)) return 0;
        long lastUsed = playerEscapeCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= PLAYER_ESCAPE_COOLDOWN_MS) return 0;
        return (int)((PLAYER_ESCAPE_COOLDOWN_MS - elapsed) / 1000);
    }

    public void resetAllCooldowns() {
        chestDetectorCooldowns.clear();
        chestTeleporterCooldowns.clear();
        playerEscapeCooldowns.clear();
        // ドローン使用回数はゲーム開始時に再配布されるので初期化しない
    }
}
