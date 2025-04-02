package org.oni.oniGo;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    // 鬼叉用のクールダウン
    private Map<UUID, Long> kishaDashCooldowns = new HashMap<>();
    private Map<UUID, Long> kishaStopCooldowns = new HashMap<>();
    private Map<UUID, Long> kishaKanabooCooldowns = new HashMap<>();

    // 闇叉用のクールダウン
    private Map<UUID, Long> anshaDarkenCooldowns = new HashMap<>();
    private Map<UUID, Long> anshaLocationCooldowns = new HashMap<>();
    private Map<UUID, Boolean> anshaEscapeNotAllowedUsed = new HashMap<>();

    // 月牙用のクールダウン
    private Map<UUID, Long> getugaRandTeleportCooldowns = new HashMap<>();
    private Map<UUID, Long> getugaSlowCooldowns = new HashMap<>();
    private Map<UUID, Integer> getugaMoonCutUsages = new HashMap<>();

    // 攻撃回数管理
    private Map<UUID, Map<UUID, Integer>> attackCounts = new HashMap<>(); // 鬼UUID -> (プレイヤーUUID -> 攻撃回数)

    // 闇叉の転生ポイント
    private Map<UUID, Location> anshaLocationPoints = new HashMap<>();

    // 鬼叉の金棒使用状態
    private Map<UUID, Boolean> kishaKanabooActive = new HashMap<>();

    // 月牙の殺月使用状態
    private Map<UUID, Boolean> getugaSlowActive = new HashMap<>();

    // ドローン使用回数
    private Map<UUID, Integer> droneUsages = new HashMap<>();

    // クールダウン時間
    private static final long DETECTOR_COOLDOWN_MS = 60000;       // 60秒
    private static final long TELEPORTER_COOLDOWN_MS = 120000;    // 120秒
    private static final long PLAYER_ESCAPE_COOLDOWN_MS = 60000;  // 60秒

    // 鬼叉クールダウン
    private static final long KISHA_DASH_COOLDOWN_MS = 30000;     // 30秒
    private static final long KISHA_STOP_COOLDOWN_MS = 60000;     // 60秒
    private static final long KISHA_KANABOO_COOLDOWN_MS = 120000; // 120秒

    // 闇叉クールダウン
    private static final long ANSHA_DARKEN_COOLDOWN_MS = 100000;  // 100秒
    private static final long ANSHA_LOCATION_COOLDOWN_MS = 20000; // 20秒

    // 月牙クールダウン
    private static final long GETUGA_TELEPORT_COOLDOWN_MS = 150000; // 150秒
    private static final long GETUGA_SLOW_COOLDOWN_MS = 60000;     // 60秒

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

        // 鬼叉アイテム
        player.getInventory().addItem(createKishaDashItem());
        player.getInventory().addItem(createKishaStopItem());
        player.getInventory().addItem(createKishaKanabooItem());

        // 闇叉アイテム
        player.getInventory().addItem(createAnshaDarkenItem());
        player.getInventory().addItem(createAnshaLocationItem());
        player.getInventory().addItem(createAnshaEscapeNotAllowedItem());

        // 月牙アイテム
        player.getInventory().addItem(createGetugaMoonCutItem());
        player.getInventory().addItem(createGetugaRandTeleportItem());
        player.getInventory().addItem(createGetugaSlowItem());

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
                // 鬼タイプに応じて適切なアイテムを配布
                OniType oniType = teamManager.getPlayerOniType(p);
                switch (oniType) {
                    case YASHA:
                        giveYashaItems(p);
                        break;
                    case KISHA:
                        giveKishaItems(p);
                        break;
                    case ANSHA:
                        giveAnshaItems(p);
                        break;
                    case GETUGA:
                        giveGetugaItems(p);
                        break;
                }
                p.sendMessage(ChatColor.RED + oniType.getDisplayName() + "用アイテム配布！");
                p.sendMessage(ChatColor.YELLOW + "能力: " + oniType.getDescription());
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

    /**
     * 夜叉アイテム配布
     */
    private void giveYashaItems(Player player) {
        player.getInventory().addItem(createYashaItem());
        player.getInventory().addItem(createChestDetectorItem());
        player.getInventory().addItem(createChestTeleporterItem());
    }

    /**
     * 鬼叉アイテム配布
     */
    private void giveKishaItems(Player player) {
        player.getInventory().addItem(createKishaDashItem());
        player.getInventory().addItem(createKishaStopItem());
        player.getInventory().addItem(createKishaKanabooItem());
        // 攻撃カウントリセット
        attackCounts.put(player.getUniqueId(), new HashMap<>());
    }

    /**
     * 闇叉アイテム配布
     */
    private void giveAnshaItems(Player player) {
        player.getInventory().addItem(createAnshaDarkenItem());
        player.getInventory().addItem(createAnshaLocationItem());
        player.getInventory().addItem(createAnshaEscapeNotAllowedItem());
        // 暗闇効果付与（常時）
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.DARKNESS, 999999, 1, false, false));
        // 逃亡不可使用状態をリセット
        anshaEscapeNotAllowedUsed.put(player.getUniqueId(), false);
    }

    /**
     * 月牙アイテム配布
     */
    private void giveGetugaItems(Player player) {
        player.getInventory().addItem(createGetugaMoonCutItem());
        player.getInventory().addItem(createGetugaRandTeleportItem());
        player.getInventory().addItem(createGetugaSlowItem());
        // 攻撃カウントリセット
        attackCounts.put(player.getUniqueId(), new HashMap<>());
        // 月切り使用回数リセット
        getugaMoonCutUsages.put(player.getUniqueId(), 3);
    }

    public void clearPlayerInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
    }

    // 既存アイテム作成メソッド
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
        meta.setDisplayName(ChatColor.RED + "突進");
        meta.setLore(Arrays.asList("右クリック: 5秒間移動速度アップ", "クールダウン30秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKishaStopItem() {
        ItemStack item = new ItemStack(Material.COBWEB);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "停止");
        meta.setLore(Arrays.asList("右クリック: プレイヤー全員を2秒間停止", "クールダウン60秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createKishaKanabooItem() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "金棒");
        meta.setLore(Arrays.asList("右クリック: 30秒間プレイヤーを一撃で殺せる", "クールダウン120秒"));
        item.setItemMeta(meta);
        return item;
    }

    // 闇叉アイテム
    public ItemStack createAnshaDarkenItem() {
        ItemStack item = new ItemStack(Material.COAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "暗転");
        meta.setLore(Arrays.asList("右クリック: 10秒間全プレイヤーに暗闇3付与", "その間自分の暗闇は解除", "クールダウン100秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAnshaLocationItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "転生");
        meta.setLore(Arrays.asList("右クリック: 場所を記憶/記憶した場所にテレポート", "クールダウン20秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAnshaEscapeNotAllowedItem() {
        ItemStack item = new ItemStack(Material.WITHER_ROSE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "逃亡不可");
        meta.setLore(Arrays.asList("右クリック: プレイヤーの真後ろにワープ", "ゲーム開始120秒後に使用可能", "一度きり", "ワープ後2秒間動けない"));
        item.setItemMeta(meta);
        return item;
    }

    // 月牙アイテム
    public ItemStack createGetugaMoonCutItem() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "月切り");
        meta.setLore(Arrays.asList("プレイヤーを殴るとカウントチェストを1つ減らす", "ゲーム開始30秒後に使用可能", "使用回数: 3回"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGetugaRandTeleportItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "三日月");
        meta.setLore(Arrays.asList("右クリック: 全プレイヤーをランダムなチェスト近くにワープ", "クールダウン150秒"));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGetugaSlowItem() {
        ItemStack item = new ItemStack(Material.LEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "殺月");
        meta.setLore(Arrays.asList("右クリック: 30秒間全プレイヤーを鈍足にする", "誰かを殴ると解除", "クールダウン60秒"));
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

    // 新しい鬼タイプアイテム判別
    public boolean isKishaDashItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.FEATHER &&
                item.hasItemMeta() &&
                (ChatColor.RED + "突進").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isKishaStopItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.COBWEB &&
                item.hasItemMeta() &&
                (ChatColor.RED + "停止").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isKishaKanabooItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.BLAZE_ROD &&
                item.hasItemMeta() &&
                (ChatColor.RED + "金棒").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isAnshaDarkenItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.COAL &&
                item.hasItemMeta() &&
                (ChatColor.DARK_PURPLE + "暗転").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isAnshaLocationItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.ENDER_PEARL &&
                item.hasItemMeta() &&
                (ChatColor.DARK_PURPLE + "転生").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isAnshaEscapeNotAllowedItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.WITHER_ROSE &&
                item.hasItemMeta() &&
                (ChatColor.DARK_PURPLE + "逃亡不可").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isGetugaMoonCutItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.GOLDEN_SWORD &&
                item.hasItemMeta() &&
                (ChatColor.GOLD + "月切り").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isGetugaRandTeleportItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.CLOCK &&
                item.hasItemMeta() &&
                (ChatColor.GOLD + "三日月").equals(item.getItemMeta().getDisplayName());
    }

    public boolean isGetugaSlowItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.LEAD &&
                item.hasItemMeta() &&
                (ChatColor.GOLD + "殺月").equals(item.getItemMeta().getDisplayName());
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

    // 鬼叉クールダウン
    public boolean isKishaDashOnCooldown(UUID uuid) {
        if (!kishaDashCooldowns.containsKey(uuid)) return false;
        long lastUsed = kishaDashCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < KISHA_DASH_COOLDOWN_MS;
    }

    public void setKishaDashCooldown(UUID uuid) {
        kishaDashCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getKishaDashRemainingCooldown(UUID uuid) {
        if (!kishaDashCooldowns.containsKey(uuid)) return 0;
        long lastUsed = kishaDashCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= KISHA_DASH_COOLDOWN_MS) return 0;
        return (int)((KISHA_DASH_COOLDOWN_MS - elapsed) / 1000);
    }

    public boolean isKishaStopOnCooldown(UUID uuid) {
        if (!kishaStopCooldowns.containsKey(uuid)) return false;
        long lastUsed = kishaStopCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < KISHA_STOP_COOLDOWN_MS;
    }

    public void setKishaStopCooldown(UUID uuid) {
        kishaStopCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getKishaStopRemainingCooldown(UUID uuid) {
        if (!kishaStopCooldowns.containsKey(uuid)) return 0;
        long lastUsed = kishaStopCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= KISHA_STOP_COOLDOWN_MS) return 0;
        return (int)((KISHA_STOP_COOLDOWN_MS - elapsed) / 1000);
    }

    public boolean isKishaKanabooOnCooldown(UUID uuid) {
        if (!kishaKanabooCooldowns.containsKey(uuid)) return false;
        long lastUsed = kishaKanabooCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < KISHA_KANABOO_COOLDOWN_MS;
    }

    public void setKishaKanabooCooldown(UUID uuid) {
        kishaKanabooCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getKishaKanabooRemainingCooldown(UUID uuid) {
        if (!kishaKanabooCooldowns.containsKey(uuid)) return 0;
        long lastUsed = kishaKanabooCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= KISHA_KANABOO_COOLDOWN_MS) return 0;
        return (int)((KISHA_KANABOO_COOLDOWN_MS - elapsed) / 1000);
    }

    // 闇叉クールダウン
    public boolean isAnshaDarkenOnCooldown(UUID uuid) {
        if (!anshaDarkenCooldowns.containsKey(uuid)) return false;
        long lastUsed = anshaDarkenCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < ANSHA_DARKEN_COOLDOWN_MS;
    }

    public void setAnshaDarkenCooldown(UUID uuid) {
        anshaDarkenCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getAnshaDarkenRemainingCooldown(UUID uuid) {
        if (!anshaDarkenCooldowns.containsKey(uuid)) return 0;
        long lastUsed = anshaDarkenCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= ANSHA_DARKEN_COOLDOWN_MS) return 0;
        return (int)((ANSHA_DARKEN_COOLDOWN_MS - elapsed) / 1000);
    }

    public boolean isAnshaLocationOnCooldown(UUID uuid) {
        if (!anshaLocationCooldowns.containsKey(uuid)) return false;
        long lastUsed = anshaLocationCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < ANSHA_LOCATION_COOLDOWN_MS;
    }

    public void setAnshaLocationCooldown(UUID uuid) {
        anshaLocationCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getAnshaLocationRemainingCooldown(UUID uuid) {
        if (!anshaLocationCooldowns.containsKey(uuid)) return 0;
        long lastUsed = anshaLocationCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= ANSHA_LOCATION_COOLDOWN_MS) return 0;
        return (int)((ANSHA_LOCATION_COOLDOWN_MS - elapsed) / 1000);
    }

    // 月牙クールダウン
    public boolean isGetugaRandTeleportOnCooldown(UUID uuid) {
        if (!getugaRandTeleportCooldowns.containsKey(uuid)) return false;
        long lastUsed = getugaRandTeleportCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < GETUGA_TELEPORT_COOLDOWN_MS;
    }

    public void setGetugaRandTeleportCooldown(UUID uuid) {
        getugaRandTeleportCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getGetugaRandTeleportRemainingCooldown(UUID uuid) {
        if (!getugaRandTeleportCooldowns.containsKey(uuid)) return 0;
        long lastUsed = getugaRandTeleportCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= GETUGA_TELEPORT_COOLDOWN_MS) return 0;
        return (int)((GETUGA_TELEPORT_COOLDOWN_MS - elapsed) / 1000);
    }

    public boolean isGetugaSlowOnCooldown(UUID uuid) {
        if (!getugaSlowCooldowns.containsKey(uuid)) return false;
        long lastUsed = getugaSlowCooldowns.get(uuid);
        return (System.currentTimeMillis() - lastUsed) < GETUGA_SLOW_COOLDOWN_MS;
    }

    public void setGetugaSlowCooldown(UUID uuid) {
        getugaSlowCooldowns.put(uuid, System.currentTimeMillis());
    }

    public int getGetugaSlowRemainingCooldown(UUID uuid) {
        if (!getugaSlowCooldowns.containsKey(uuid)) return 0;
        long lastUsed = getugaSlowCooldowns.get(uuid);
        long elapsed = System.currentTimeMillis() - lastUsed;
        if (elapsed >= GETUGA_SLOW_COOLDOWN_MS) return 0;
        return (int)((GETUGA_SLOW_COOLDOWN_MS - elapsed) / 1000);
    }

    public void resetAllCooldowns() {
        chestDetectorCooldowns.clear();
        chestTeleporterCooldowns.clear();
        playerEscapeCooldowns.clear();

        // 新しい鬼タイプのクールダウンもリセット
        kishaDashCooldowns.clear();
        kishaStopCooldowns.clear();
        kishaKanabooCooldowns.clear();

        anshaDarkenCooldowns.clear();
        anshaLocationCooldowns.clear();
        anshaEscapeNotAllowedUsed.clear();

        getugaRandTeleportCooldowns.clear();
        getugaSlowCooldowns.clear();
        getugaMoonCutUsages.clear();

        // 攻撃回数もクリア
        attackCounts.clear();

        // 鬼叉の金棒使用状態クリア
        kishaKanabooActive.clear();

        // 月牙の殺月使用状態クリア
        getugaSlowActive.clear();

        // 闇叉の転生ポイントクリア
        anshaLocationPoints.clear();

        // ドローン使用回数はゲーム開始時に再配布されるので初期化しない
    }

    // 攻撃カウント管理
    public void addAttackCount(UUID oniUuid, UUID targetUuid) {
        if (!attackCounts.containsKey(oniUuid)) {
            attackCounts.put(oniUuid, new HashMap<>());
        }

        Map<UUID, Integer> counts = attackCounts.get(oniUuid);
        int currentCount = counts.getOrDefault(targetUuid, 0);
        counts.put(targetUuid, currentCount + 1);
    }

    public int getAttackCount(UUID oniUuid, UUID targetUuid) {
        if (!attackCounts.containsKey(oniUuid)) {
            return 0;
        }
        return attackCounts.get(oniUuid).getOrDefault(targetUuid, 0);
    }

    public void resetAttackCount(UUID oniUuid, UUID targetUuid) {
        if (attackCounts.containsKey(oniUuid)) {
            attackCounts.get(oniUuid).remove(targetUuid);
        }
    }

    // 鬼叉の金棒管理
    public void setKishaKanabooActive(UUID oniUuid, boolean active) {
        kishaKanabooActive.put(oniUuid, active);
    }

    public boolean isKishaKanabooActive(UUID oniUuid) {
        return kishaKanabooActive.getOrDefault(oniUuid, false);
    }

    // 闇叉の転生ポイント管理
    public void setAnshaLocationPoint(UUID oniUuid, Location location) {
        anshaLocationPoints.put(oniUuid, location);
    }

    public Location getAnshaLocationPoint(UUID oniUuid) {
        return anshaLocationPoints.get(oniUuid);
    }

    public boolean hasAnshaLocationPoint(UUID oniUuid) {
        return anshaLocationPoints.containsKey(oniUuid);
    }

    // 闇叉の逃亡不可管理
    public void setAnshaEscapeNotAllowedUsed(UUID oniUuid, boolean used) {
        anshaEscapeNotAllowedUsed.put(oniUuid, used);
    }

    public boolean isAnshaEscapeNotAllowedUsed(UUID oniUuid) {
        return anshaEscapeNotAllowedUsed.getOrDefault(oniUuid, false);
    }

    // 月牙の月切り使用回数管理
    public int getGetugaMoonCutUsages(UUID oniUuid) {
        return getugaMoonCutUsages.getOrDefault(oniUuid, 0);
    }

    public void decreaseGetugaMoonCutUsages(UUID oniUuid) {
        int usages = getugaMoonCutUsages.getOrDefault(oniUuid, 0);
        if (usages > 0) {
            getugaMoonCutUsages.put(oniUuid, usages - 1);
        }
    }

    // 月牙の殺月管理
    public void setGetugaSlowActive(UUID oniUuid, boolean active) {
        getugaSlowActive.put(oniUuid, active);
    }

    public boolean isGetugaSlowActive(UUID oniUuid) {
        return getugaSlowActive.getOrDefault(oniUuid, false);
    }
}