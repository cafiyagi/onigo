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
import org.bukkit.Location;
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
    private static final long DETECTOR_COOLDOWN_MS = 15000;       // 60秒
    private static final long TELEPORTER_COOLDOWN_MS = 30000;    // 120秒
    private static final long PLAYER_ESCAPE_COOLDOWN_MS = 90000;  // 60秒

    // 新しい鬼のアイテム用クールダウン管理
    private Map<UUID, Long> dashCooldowns = new HashMap<>(); // 鬼叉の突進
    private Map<UUID, Long> freezeCooldowns = new HashMap<>(); // 鬼叉の停止
    private Map<UUID, Long> goldClubCooldowns = new HashMap<>(); // 鬼叉の金棒
    private Map<UUID, Long> darknessCooldowns = new HashMap<>(); // 闇叉の暗転
    private Map<UUID, Long> teleportCooldowns = new HashMap<>(); // 闇叉の転生
    private Map<UUID, Boolean> escapeDisableCooldowns = new HashMap<>(); // 闇叉の逃亡不可
    private Map<UUID, Long> getsugaCooldowns = new HashMap<>(); // 月牙の月切り
    private Map<UUID, Long> crescentCooldowns = new HashMap<>(); // 月牙の三日月
    private Map<UUID, Long> killMoonCooldowns = new HashMap<>(); // 月牙の殺月

    // マーカー用
    private Map<UUID, Location> teleportMarkers = new HashMap<>(); // 闇叉の転生マーカー
    private Map<UUID, Integer> getsugaUsageCount = new HashMap<>(); // 月牙の月切り使用回数

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
                // 鬼タイプに基づいてアイテム配布
                OniType type = teamManager.getPlayerOniType(p);
                distributeOniItems(p, type);
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

    // 鬼叉アイテム
    public ItemStack createKishaDashItem() {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "突進");
        meta.setLore(Arrays.asList("右クリックで5秒間速度上昇", "クールダウン30秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKishaFreezeItem() {
        ItemStack item = new ItemStack(Material.ICE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "停止");
        meta.setLore(Arrays.asList("右クリックで全プレイヤーを2秒間停止", "クールダウン60秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKishaGoldClubItem() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "金棒");
        meta.setLore(Arrays.asList("右クリックで30秒間一撃必殺", "クールダウン120秒"));
        item.setItemMeta(meta);
        return item;
    }

    // 闇叉アイテム
    public ItemStack createAnshaDarknessItem() {
        ItemStack item = new ItemStack(Material.COAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "暗転");
        meta.setLore(Arrays.asList("右クリックで10秒間全プレイヤーに暗闇効果", "クールダウン100秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAnshaTeleportItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "転生");
        meta.setLore(Arrays.asList("右クリック：マーカー設置/マーカーにテレポート", "クールダウン20秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAnshaEscapeDisableItem() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "逃亡不可");
        meta.setLore(Arrays.asList("ゲーム開始120秒後に使用可能", "最も近いプレイヤーの背後にワープ", "一度きり使用可能"));
        item.setItemMeta(meta);
        return item;
    }

    // 月牙アイテム
    public ItemStack createGetsugaMoonSlashItem() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.BLUE + "月切り");
        meta.setLore(Arrays.asList("プレイヤーを殴るとカウントチェストを1つ減らす", "ゲーム開始30秒後に使用可能", "3回まで使用可能"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGetsugaCrescentItem() {
        ItemStack item = new ItemStack(Material.GHAST_TEAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "三日月");
        meta.setLore(Arrays.asList("右クリックで全プレイヤーをランダムなチェスト付近にワープ", "クールダウン150秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGetsugaKillMoonItem() {
        ItemStack item = new ItemStack(Material.DRAGON_BREATH);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "殺月");
        meta.setLore(Arrays.asList("右クリックで30秒間全プレイヤーを鈍足に", "プレイヤーを殴ると効果解除", "クールダウン60秒"));
        item.setItemMeta(meta);
        return item;
    }

    // 鬼タイプ別アイテム配布メソッド
    public void distributeOniItems(Player player, OniType type) {
        clearPlayerInventory(player);

        switch (type) {
            case YASHA:
                player.getInventory().addItem(createYashaItem());
                player.getInventory().addItem(createChestDetectorItem());
                player.getInventory().addItem(createChestTeleporterItem());
                player.sendMessage(ChatColor.RED + "夜叉用アイテム配布！");
                break;
            case KISHA:
                player.getInventory().addItem(createKishaDashItem());
                player.getInventory().addItem(createKishaFreezeItem());
                player.getInventory().addItem(createKishaGoldClubItem());
                player.sendMessage(ChatColor.GOLD + "鬼叉用アイテム配布！");
                break;
            case ANSHA:
                player.getInventory().addItem(createAnshaDarknessItem());
                player.getInventory().addItem(createAnshaTeleportItem());
                player.getInventory().addItem(createAnshaEscapeDisableItem());
                player.sendMessage(ChatColor.DARK_PURPLE + "闇叉用アイテム配布！");
                break;
            case GETSUGA:
                player.getInventory().addItem(createGetsugaMoonSlashItem());
                player.getInventory().addItem(createGetsugaCrescentItem());
                player.getInventory().addItem(createGetsugaKillMoonItem());
                player.sendMessage(ChatColor.BLUE + "月牙用アイテム配布！");
                break;
        }
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

    // 新しい鬼のアイテム判別メソッド
    public boolean isKishaDashItem(ItemStack item) {
        return item != null && item.getType() == Material.FEATHER && item.hasItemMeta() &&
                (ChatColor.GOLD + "突進").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isKishaFreezeItem(ItemStack item) {
        return item != null && item.getType() == Material.ICE && item.hasItemMeta() &&
                (ChatColor.AQUA + "停止").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isKishaGoldClubItem(ItemStack item) {
        return item != null && item.getType() == Material.GOLDEN_AXE && item.hasItemMeta() &&
                (ChatColor.YELLOW + "金棒").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isAnshaDarknessItem(ItemStack item) {
        return item != null && item.getType() == Material.COAL && item.hasItemMeta() &&
                (ChatColor.DARK_PURPLE + "暗転").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isAnshaTeleportItem(ItemStack item) {
        return item != null && item.getType() == Material.ENDER_PEARL && item.hasItemMeta() &&
                (ChatColor.LIGHT_PURPLE + "転生").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isAnshaEscapeDisableItem(ItemStack item) {
        return item != null && item.getType() == Material.ENDER_EYE && item.hasItemMeta() &&
                (ChatColor.RED + "逃亡不可").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isGetsugaMoonSlashItem(ItemStack item) {
        return item != null && item.getType() == Material.IRON_SWORD && item.hasItemMeta() &&
                (ChatColor.BLUE + "月切り").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isGetsugaCrescentItem(ItemStack item) {
        return item != null && item.getType() == Material.GHAST_TEAR && item.hasItemMeta() &&
                (ChatColor.AQUA + "三日月").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isGetsugaKillMoonItem(ItemStack item) {
        return item != null && item.getType() == Material.DRAGON_BREATH && item.hasItemMeta() &&
                (ChatColor.DARK_RED + "殺月").equals(item.getItemMeta().getDisplayName());
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

    // 新しい鬼のクールダウンメソッド
    public boolean isKishaDashOnCooldown(UUID uuid) {
        if (!dashCooldowns.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - dashCooldowns.get(uuid)) < 20000; // 30秒
    }

    public void setKishaDashCooldown(UUID uuid) {
        dashCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getKishaDashRemainingCooldown(UUID uuid) {
        if (!dashCooldowns.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - dashCooldowns.get(uuid);
        return elapsed >= 30000 ? 0 : (int)((30000 - elapsed) / 1000);
    }

    public boolean isKishaFreezeOnCooldown(UUID uuid) {
        if (!freezeCooldowns.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - freezeCooldowns.get(uuid)) < 60000; // 60秒
    }

    public void setKishaFreezeCooldown(UUID uuid) {
        freezeCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getKishaFreezeRemainingCooldown(UUID uuid) {
        if (!freezeCooldowns.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - freezeCooldowns.get(uuid);
        return elapsed >= 60000 ? 0 : (int)((60000 - elapsed) / 1000);
    }

    public boolean isKishaGoldClubOnCooldown(UUID uuid) {
        if (!goldClubCooldowns.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - goldClubCooldowns.get(uuid)) < 120000; // 120秒
    }

    public void setKishaGoldClubCooldown(UUID uuid) {
        goldClubCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getKishaGoldClubRemainingCooldown(UUID uuid) {
        if (!goldClubCooldowns.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - goldClubCooldowns.get(uuid);
        return elapsed >= 120000 ? 0 : (int)((120000 - elapsed) / 1000);
    }

    public boolean isAnshaDarknessOnCooldown(UUID uuid) {
        if (!darknessCooldowns.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - darknessCooldowns.get(uuid)) < 100000; // 100秒
    }

    public void setAnshaDarknessCooldown(UUID uuid) {
        darknessCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getAnshaDarknessRemainingCooldown(UUID uuid) {
        if (!darknessCooldowns.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - darknessCooldowns.get(uuid);
        return elapsed >= 100000 ? 0 : (int)((100000 - elapsed) / 1000);
    }

    public boolean isAnshaTeleportOnCooldown(UUID uuid) {
        if (!teleportCooldowns.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - teleportCooldowns.get(uuid)) < 20000; // 20秒
    }

    public void setAnshaTeleportCooldown(UUID uuid) {
        teleportCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getAnshaTeleportRemainingCooldown(UUID uuid) {
        if (!teleportCooldowns.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - teleportCooldowns.get(uuid);
        return elapsed >= 20000 ? 0 : (int)((20000 - elapsed) / 1000);
    }

    public boolean isAnshaEscapeDisableUsed(UUID uuid) {
        return escapeDisableCooldowns.getOrDefault(uuid, false);
    }

    public void setAnshaEscapeDisableUsed(UUID uuid) {
        escapeDisableCooldowns.put(uuid, true);
    }

    public void resetAnshaEscapeDisableUsed(UUID uuid) {
        escapeDisableCooldowns.put(uuid, false);
    }

    public int getGetsugaMoonSlashCount(UUID uuid) {
        return getsugaUsageCount.getOrDefault(uuid, 0);
    }

    public void incrementGetsugaMoonSlashCount(UUID uuid) {
        getsugaUsageCount.put(uuid, getGetsugaMoonSlashCount(uuid) + 1);
    }

    public void resetGetsugaMoonSlashCount(UUID uuid) {
        getsugaUsageCount.put(uuid, 0);
    }

    public boolean isGetsugaCrescentOnCooldown(UUID uuid) {
        if (!crescentCooldowns.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - crescentCooldowns.get(uuid)) < 150000; // 150秒
    }

    public void setGetsugaCrescentCooldown(UUID uuid) {
        crescentCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getGetsugaCrescentRemainingCooldown(UUID uuid) {
        if (!crescentCooldowns.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - crescentCooldowns.get(uuid);
        return elapsed >= 150000 ? 0 : (int)((150000 - elapsed) / 1000);
    }

    public boolean isGetsugaKillMoonOnCooldown(UUID uuid) {
        if (!killMoonCooldowns.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - killMoonCooldowns.get(uuid)) < 60000; // 60秒
    }

    public void setGetsugaKillMoonCooldown(UUID uuid) {
        killMoonCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getGetsugaKillMoonRemainingCooldown(UUID uuid) {
        if (!killMoonCooldowns.containsKey(uuid)) return 0;
        long elapsed = System.currentTimeMillis() - killMoonCooldowns.get(uuid);
        return elapsed >= 60000 ? 0 : (int)((60000 - elapsed) / 1000);
    }

    // 転生マーカー管理
    public void setTeleportMarker(UUID uuid, Location location) {
        teleportMarkers.put(uuid, location);
    }

    public Location getTeleportMarker(UUID uuid) {
        return teleportMarkers.get(uuid);
    }

    public boolean hasTeleportMarker(UUID uuid) {
        return teleportMarkers.containsKey(uuid);
    }

    public void clearTeleportMarker(UUID uuid) {
        teleportMarkers.remove(uuid);
    }

    public void resetAllCooldowns() {
        chestDetectorCooldowns.clear();
        chestTeleporterCooldowns.clear();
        playerEscapeCooldowns.clear();

        // 新しい鬼のクールダウンもリセット
        dashCooldowns.clear();
        freezeCooldowns.clear();
        goldClubCooldowns.clear();
        darknessCooldowns.clear();
        teleportCooldowns.clear();
        escapeDisableCooldowns.clear();
        crescentCooldowns.clear();
        killMoonCooldowns.clear();
        getsugaUsageCount.clear();
        teleportMarkers.clear();
    }
}