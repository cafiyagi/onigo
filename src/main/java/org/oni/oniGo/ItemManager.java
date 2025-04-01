package org.oni.oniGo;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {
    private final OniGo plugin;
    private final TeamManager teamManager;

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

        // Give team selection book
        ItemStack teamBook = createTeamSelectBook();
        player.getInventory().addItem(teamBook);

        // Give game start book
        ItemStack startBook = createGameStartBook();
        player.getInventory().addItem(startBook);

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
     * Distribute team-specific items to all players based on their team
     */
    public void distributeTeamItems() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            // Clear inventory completely
            clearPlayerInventory(p);

            if (teamManager.isPlayerInOniTeam(p)) {
                // Oni team gets Yasha item
                ItemStack yashaItem = createYashaItem();
                p.getInventory().addItem(yashaItem);
                p.sendMessage(ChatColor.RED + "鬼チーム用アイテムを付与しました");
            } else if (teamManager.isPlayerInPlayerTeam(p)) {
                // Player team gets hiding orb
                ItemStack kakureDama = createKakureDamaItem();
                p.getInventory().addItem(kakureDama);
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
                "クリックした人が鬼になります。他のプレイヤーは自動的にプレイヤー陣営に割り当てられます。");
        startBookMeta.setPages(startPages);
        startBook.setItemMeta(startBookMeta);
        return startBook;
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
}