package org.oni.oniGo;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ItemManager {
    private final OniGo plugin;
    private final TeamManager teamManager;

    // クールダウン管理
    private Map<UUID, Long> chestDetectorCooldowns = new HashMap<>();
    private Map<UUID, Long> chestTeleporterCooldowns = new HashMap<>();
    private Map<UUID, Long> playerEscapeCooldowns = new HashMap<>();

    // クールダウン時間（ミリ秒）
    private static final long DETECTOR_COOLDOWN_MS = 60000; // 60秒
    private static final long TELEPORTER_COOLDOWN_MS = 120000; // 120秒
    private static final long PLAYER_ESCAPE_COOLDOWN_MS = 60000; // 60秒

    public ItemManager(OniGo plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    /**
     * Give all game items to a player
     */
    public void giveAllGameItems(Player player) {
        // Give Yasha item
        ItemStack yashaItem = createYashaItem();
        player.getInventory().addItem(yashaItem);

        // Give hiding orb
        ItemStack kakureDama = createKakureDamaItem();
        player.getInventory().addItem(kakureDama);

        // 新しい鬼アイテム
        ItemStack chestDetector = createChestDetectorItem();
        player.getInventory().addItem(chestDetector);

        ItemStack chestTeleporter = createChestTeleporterItem();
        player.getInventory().addItem(chestTeleporter);

        // 新しいプレイヤーアイテム
        ItemStack playerEscape = createPlayerEscapeItem();
        player.getInventory().addItem(playerEscape);

        // Give team selection book
        ItemStack teamBook = createTeamSelectBook();
        player.getInventory().addItem(teamBook);

        // Give game start book
        ItemStack startBook = createGameStartBook();
        player.getInventory().addItem(startBook);

        // Give chest count book
        ItemStack chestCountBook = createChestCountBook();
        player.getInventory().addItem(chestCountBook);

        // Give game time book
        ItemStack timeBook = createGameTimeBook();
        player.getInventory().addItem(timeBook);

        // Give exit key (for testing)
        ItemStack exitKey = createExitKeyItem();
        player.getInventory().addItem(exitKey);

        player.sendMessage(ChatColor.GREEN + "ゲームアイテムを取得したよ！");
    }

    /**
     * Distribute team selection books to all players
     */
    public void distributeTeamSelectionBooks() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ItemStack selectBook = createTeamSelectBook();
            p.getInventory().addItem(selectBook);
        }
    }

    /**
     * Give game start book to a specific player
     */
    public void giveGameStartBook(String playerName) {
        Player player = plugin.getServer().getPlayer(playerName);
        if (player != null && player.isOnline()) {
            ItemStack startBook = createGameStartBook();
            player.getInventory().addItem(startBook);
        }
    }

    /**
     * Give chest count book to a specific player
     */
    public void giveChestCountBook(String playerName) {
        Player player = plugin.getServer().getPlayer(playerName);
        if (player != null && player.isOnline()) {
            ItemStack chestCountBook = createChestCountBook();
            player.getInventory().addItem(chestCountBook);
        }
    }

    /**
     * Give game time book to a specific player
     */
    public void giveGameTimeBook(String playerName) {
        Player player = plugin.getServer().getPlayer(playerName);
        if (player != null && player.isOnline()) {
            ItemStack timeBook = createGameTimeBook();
            player.getInventory().addItem(timeBook);
        }
    }

    /**
     * Distribute team-specific items to all players based on their team
     */
    public void distributeTeamItems() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            // Clear inventory completely
            clearPlayerInventory(p);

            if (teamManager.isPlayerInOniTeam(p)) {
                // 鬼チーム用アイテム
                ItemStack yashaItem = createYashaItem();
                p.getInventory().addItem(yashaItem);

                // 新しい鬼アイテム
                ItemStack chestDetector = createChestDetectorItem();
                p.getInventory().addItem(chestDetector);

                ItemStack chestTeleporter = createChestTeleporterItem();
                p.getInventory().addItem(chestTeleporter);

                p.sendMessage(ChatColor.RED + "鬼チーム用アイテムを付与しました");
            } else if (teamManager.isPlayerInPlayerTeam(p)) {
                // Player team gets hiding orb
                ItemStack kakureDama = createKakureDamaItem();
                p.getInventory().addItem(kakureDama);

                // Add new player escape item
                ItemStack playerEscape = createPlayerEscapeItem();
                p.getInventory().addItem(playerEscape);

                p.sendMessage(ChatColor.BLUE + "プレイヤーチーム用アイテムを付与しました");
            }
        }
    }

    /**
     * Clear player inventory completely including armor
     */
    public void clearPlayerInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
    }

    /**
     * Create Yasha (demon) item
     */
    public ItemStack createYashaItem() {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.setDisplayName("夜叉");
        star.setItemMeta(meta);
        return star;
    }

    /**
     * Create hiding orb (kakure dama) item
     */
    public ItemStack createKakureDamaItem() {
        ItemStack kakureDama = new ItemStack(Material.DIAMOND);
        ItemMeta meta = kakureDama.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "隠れ玉");
        List<String> lore = new ArrayList<>();
        lore.add("右クリックで透明化、再度右クリックで透明解除");
        meta.setLore(lore);
        kakureDama.setItemMeta(meta);
        return kakureDama;
    }

    /**
     * Create chest detector item (for oni)
     */
    public ItemStack createChestDetectorItem() {
        ItemStack detector = new ItemStack(Material.BLUE_WOOL);
        ItemMeta meta = detector.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "プレイヤー探知コンパス");
        List<String> lore = new ArrayList<>();
        lore.add("右クリックでプレイヤーが近くにいるチェストの場所を探知");
        lore.add("クールダウン：60秒");
        meta.setLore(lore);
        detector.setItemMeta(meta);
        return detector;
    }

    /**
     * Create chest teleporter item (for oni)
     */
    public ItemStack createChestTeleporterItem() {
        ItemStack teleporter = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = teleporter.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "チェストワープの真珠");
        List<String> lore = new ArrayList<>();
        lore.add("右クリックでプレイヤーの近くのチェストにワープ");
        lore.add("クールダウン：120秒");
        meta.setLore(lore);
        teleporter.setItemMeta(meta);
        return teleporter;
    }

    /**
     * Create player escape item
     */
    public ItemStack createPlayerEscapeItem() {
        ItemStack escapeItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = escapeItem.getItemMeta();
        meta.setDisplayName(ChatColor.BLUE + "緊急脱出アイテム");
        List<String> lore = new ArrayList<>();
        lore.add("右クリックで鬼が近くにいる場合、ランダムなチェストの近くにテレポート");
        lore.add("クールダウン：60秒");
        meta.setLore(lore);
        escapeItem.setItemMeta(meta);
        return escapeItem;
    }

    /**
     * Create exit key item
     */
    public ItemStack createExitKeyItem() {
        ItemStack exitKey = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = exitKey.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "出口の鍵");
        List<String> lore = new ArrayList<>();
        lore.add("チェストを開けて入手したアイテム");
        lore.add("出口のドアで使用することで脱出が可能になる");
        meta.setLore(lore);
        exitKey.setItemMeta(meta);
        return exitKey;
    }

    /**
     * Create team selection book
     */
    public ItemStack createTeamSelectBook() {
        ItemStack selectBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) selectBook.getItemMeta();
        bookMeta.setTitle("陣営選択本");
        bookMeta.setAuthor("鬼ごっこプラグイン");
        List<String> pages = new ArrayList<>();
        pages.add("右クリックして陣営を選ぼう！\n\n・左クリック：プレイヤー陣営\n・右クリック：鬼陣営");
        bookMeta.setPages(pages);
        selectBook.setItemMeta(bookMeta);
        return selectBook;
    }

    /**
     * Create game start book
     */
    public ItemStack createGameStartBook() {
        ItemStack startBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta startBookMeta = (BookMeta) startBook.getItemMeta();
        startBookMeta.setTitle("ゲームスタート本");
        startBookMeta.setAuthor("鬼ごっこプラグイン");
        List<String> startPages = new ArrayList<>();
        startPages.add("§l§4★ ゲームスタート ★§r\n\n"+
                "§2[通常スタート]§r\n"+
                "すべてのプレイヤーが陣営を選択した状態で実行するとゲームが開始します。\n\n"+
                "§c[鬼スタート]§r\n"+
                "クリックした人が鬼になります。他のプレイヤーは自動的にプレイヤー陣営に割り当てられます。\n\n"+
                "§d[ランダム鬼スタート]§r\n"+
                "ランダムで1人を鬼に選びます。陣営選択せずにゲームを開始できます。");
        startBookMeta.setPages(startPages);
        startBook.setItemMeta(startBookMeta);
        return startBook;
    }

    /**
     * Create chest count book
     */
    public ItemStack createChestCountBook() {
        ItemStack chestCountBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta chestCountBookMeta = (BookMeta) chestCountBook.getItemMeta();
        chestCountBookMeta.setTitle("チェスト設定本");
        chestCountBookMeta.setAuthor("鬼ごっこプラグイン");
        List<String> chestCountPages = new ArrayList<>();

        // 登録済みチェスト数を取得
        int totalChests = plugin.getConfigManager().getTotalCountChests();
        int currentRequired = plugin.getConfigManager().getRequiredCountChests();

        chestCountPages.add("§l§6★ カウントチェスト設定 ★§r\n\n"+
                "現在の設定:\n" +
                "・必要数: §e" + currentRequired + "§r個/プレイヤー\n" +
                "・登録済み: §e" + totalChests + "§r個\n\n" +
                "§a[1個]§r - 簡単モード\n"+
                "§e[3個]§r - 標準モード\n"+
                "§c[5個]§r - 難しいモード\n"+
                "§d[カスタム]§r - 数値入力(最大" + totalChests + "個)");
        chestCountBookMeta.setPages(chestCountPages);
        chestCountBook.setItemMeta(chestCountBookMeta);
        return chestCountBook;
    }

    /**
     * Create game time book
     */
    public ItemStack createGameTimeBook() {
        ItemStack timeBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta timeBookMeta = (BookMeta) timeBook.getItemMeta();
        timeBookMeta.setTitle("ゲーム時間設定本");
        timeBookMeta.setAuthor("鬼ごっこプラグイン");
        List<String> timePages = new ArrayList<>();

        timePages.add("§l§6★ ゲーム時間設定 ★§r\n\n"+
                "現在の設定:\n" +
                "・ゲーム時間: §e" + plugin.getGameManager().getRemainingTime() + "§r秒\n\n" +
                "§a[180秒]§r - 短時間モード\n"+
                "§e[300秒]§r - 標準モード\n"+
                "§c[600秒]§r - 長時間モード\n"+
                "§d[カスタム]§r - 数値入力(最低60秒)");
        timeBookMeta.setPages(timePages);
        timeBook.setItemMeta(timeBookMeta);
        return timeBook;
    }

    /**
     * Check if an item is the Yasha item
     */
    public boolean isYashaItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.NETHER_STAR &&
                item.hasItemMeta() &&
                "夜叉".equals(item.getItemMeta().getDisplayName());
    }

    /**
     * Check if an item is the hiding orb (kakure dama)
     */
    public boolean isKakureDamaItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.DIAMOND &&
                item.hasItemMeta() &&
                (ChatColor.AQUA + "隠れ玉").equals(item.getItemMeta().getDisplayName());
    }

    /**
     * Check if an item is the chest detector
     */
    public boolean isChestDetectorItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.BLUE_WOOL &&
                item.hasItemMeta() &&
                (ChatColor.RED + "プレイヤー探知コンパス").equals(item.getItemMeta().getDisplayName());
    }

    /**
     * Check if an item is the chest teleporter
     */
    public boolean isChestTeleporterItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.GREEN_WOOL &&
                item.hasItemMeta() &&
                (ChatColor.RED + "チェストワープの真珠").equals(item.getItemMeta().getDisplayName());
    }

    /**
     * Check if an item is the player escape item
     */
    public boolean isPlayerEscapeItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.NETHER_STAR &&
                item.hasItemMeta() &&
                (ChatColor.BLUE + "緊急脱出アイテム").equals(item.getItemMeta().getDisplayName());
    }

    /**
     * Check if an item is the exit key
     */
    public boolean isExitKeyItem(ItemStack item) {
        return item != null &&
                item.getType() == Material.TRIPWIRE_HOOK &&
                item.hasItemMeta() &&
                (ChatColor.GOLD + "出口の鍵").equals(item.getItemMeta().getDisplayName());
    }

    /**
     * Check if an item is the team selection book
     */
    public boolean isTeamSelectBook(ItemStack item) {
        return item != null &&
                item.getType() == Material.WRITTEN_BOOK &&
                item.hasItemMeta() &&
                item.getItemMeta() instanceof BookMeta &&
                "陣営選択本".equals(((BookMeta) item.getItemMeta()).getTitle());
    }

    /**
     * Check if an item is the game start book
     */
    public boolean isGameStartBook(ItemStack item) {
        return item != null &&
                item.getType() == Material.WRITTEN_BOOK &&
                item.hasItemMeta() &&
                item.getItemMeta() instanceof BookMeta &&
                "ゲームスタート本".equals(((BookMeta) item.getItemMeta()).getTitle());
    }

    /**
     * Check if an item is the chest count book
     */
    public boolean isChestCountBook(ItemStack item) {
        return item != null &&
                item.getType() == Material.WRITTEN_BOOK &&
                item.hasItemMeta() &&
                item.getItemMeta() instanceof BookMeta &&
                "チェスト設定本".equals(((BookMeta) item.getItemMeta()).getTitle());
    }

    /**
     * Check if an item is the game time book
     */
    public boolean isGameTimeBook(ItemStack item) {
        return item != null &&
                item.getType() == Material.WRITTEN_BOOK &&
                item.hasItemMeta() &&
                item.getItemMeta() instanceof BookMeta &&
                "ゲーム時間設定本".equals(((BookMeta) item.getItemMeta()).getTitle());
    }

    /**
     * Check if chest detector is on cooldown
     */
    public boolean isChestDetectorOnCooldown(UUID playerUuid) {
        if (!chestDetectorCooldowns.containsKey(playerUuid)) {
            return false;
        }

        long lastUsed = chestDetectorCooldowns.get(playerUuid);
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastUsed) < DETECTOR_COOLDOWN_MS;
    }

    /**
     * Set chest detector on cooldown
     */
    public void setChestDetectorCooldown(UUID playerUuid) {
        chestDetectorCooldowns.put(playerUuid, System.currentTimeMillis());
    }

    /**
     * Get chest detector remaining cooldown in seconds
     */
    public int getChestDetectorRemainingCooldown(UUID playerUuid) {
        if (!chestDetectorCooldowns.containsKey(playerUuid)) {
            return 0;
        }

        long lastUsed = chestDetectorCooldowns.get(playerUuid);
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastUsed;

        if (elapsedTime >= DETECTOR_COOLDOWN_MS) {
            return 0;
        }

        return (int)((DETECTOR_COOLDOWN_MS - elapsedTime) / 1000);
    }

    /**
     * Check if chest teleporter is on cooldown
     */
    public boolean isChestTeleporterOnCooldown(UUID playerUuid) {
        if (!chestTeleporterCooldowns.containsKey(playerUuid)) {
            return false;
        }

        long lastUsed = chestTeleporterCooldowns.get(playerUuid);
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastUsed) < TELEPORTER_COOLDOWN_MS;
    }

    /**
     * Set chest teleporter on cooldown
     */
    public void setChestTeleporterCooldown(UUID playerUuid) {
        chestTeleporterCooldowns.put(playerUuid, System.currentTimeMillis());
    }

    /**
     * Get chest teleporter remaining cooldown in seconds
     */
    public int getChestTeleporterRemainingCooldown(UUID playerUuid) {
        if (!chestTeleporterCooldowns.containsKey(playerUuid)) {
            return 0;
        }

        long lastUsed = chestTeleporterCooldowns.get(playerUuid);
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastUsed;

        if (elapsedTime >= TELEPORTER_COOLDOWN_MS) {
            return 0;
        }

        return (int)((TELEPORTER_COOLDOWN_MS - elapsedTime) / 1000);
    }

    /**
     * Check if player escape item is on cooldown
     */
    public boolean isPlayerEscapeOnCooldown(UUID playerUuid) {
        if (!playerEscapeCooldowns.containsKey(playerUuid)) {
            return false;
        }

        long lastUsed = playerEscapeCooldowns.get(playerUuid);
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastUsed) < PLAYER_ESCAPE_COOLDOWN_MS;
    }

    /**
     * Set player escape item on cooldown
     */
    public void setPlayerEscapeCooldown(UUID playerUuid) {
        playerEscapeCooldowns.put(playerUuid, System.currentTimeMillis());
    }

    /**
     * Get player escape item remaining cooldown in seconds
     */
    public int getPlayerEscapeRemainingCooldown(UUID playerUuid) {
        if (!playerEscapeCooldowns.containsKey(playerUuid)) {
            return 0;
        }

        long lastUsed = playerEscapeCooldowns.get(playerUuid);
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastUsed;

        if (elapsedTime >= PLAYER_ESCAPE_COOLDOWN_MS) {
            return 0;
        }

        return (int)((PLAYER_ESCAPE_COOLDOWN_MS - elapsedTime) / 1000);
    }

    /**
     * Reset all cooldowns
     */
    public void resetAllCooldowns() {
        chestDetectorCooldowns.clear();
        chestTeleporterCooldowns.clear();
        playerEscapeCooldowns.clear();
    }
}