package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import org.bukkit.block.Chest;

import java.util.*;

public final class OniGo extends JavaPlugin implements CommandExecutor, Listener {

    private Player activeYashaPlayer;
    private BukkitTask fadeTask;
    private BukkitTask reverseFadeTask;
    private int currentDarkness = 0;

    private static final String ONISONG1_SOUND = "minecraft:onisong1";
    private static final String ONISONG2_SOUND = "minecraft:onisong2";

    private boolean gameRunning = false;
    private int remainingTime = 500;
    private Team playerTeam;
    private Team oniTeam;
    private BukkitTask gameTimerTask;
    private int initialPlayerCount = 0;
    private Scoreboard scoreboard;
    private Objective objective;
    private BossBar timerBar;

    // 脱出地点の座標
    private final Location escapeLocation = new Location(null, 104, -6, -36);
    // 脱出した人のリスト
    private Set<UUID> escapedPlayers = new HashSet<>();

    // ドアの位置
    private Location doorLocation;

    // 隠れ玉の使用管理
    private Map<UUID, Integer> kakureDamaRemaining = new HashMap<>();
    private Map<UUID, BukkitTask> kakureDamaTask = new HashMap<>();

    private Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    // 鬼に常にスロウ効果2を付与するためのタスク
    private BukkitTask oniSlownessTask;

    // チェスト開封カウント用
    private Map<String, Location> chestLocations = new HashMap<>();
    private Map<String, Boolean> chestOpened = new HashMap<>();

    @Override
    public void onEnable() {
        // plugin.yml のコマンドに合わせる
        if (getCommand("yasha") != null) {
            getCommand("yasha").setExecutor(this);
        }
        if (getCommand("end") != null) {
            getCommand("end").setExecutor(this);
        }
        if (getCommand("getcmditem") != null) {
            getCommand("getcmditem").setExecutor(this);
        }
        if (getCommand("re") != null) {
            getCommand("re").setExecutor(this);
        }
        if (getCommand("start") != null) {
            getCommand("start").setExecutor(this);
        }
        if (getCommand("stop") != null) {
            getCommand("stop").setExecutor(this);
        }
        if (getCommand("onistart") != null) {
            getCommand("onistart").setExecutor(this);
        }
        // /set コマンドを使ってチェストやドアを登録
        if (getCommand("set") != null) {
            getCommand("set").setExecutor(this);
        }
        // 新しく追加した /gamegive コマンド
        if (getCommand("gamegive") != null) {
            getCommand("gamegive").setExecutor(this);
        }

        // Configを読み込んでチェストやドア位置を復元
        loadChestAndDoorFromConfig();

        // 脱出地点のワールドを設定
        escapeLocation.setWorld(Bukkit.getWorlds().get(0));

        setupScoreboard();
        timerBar = Bukkit.createBossBar("残り時間: " + remainingTime + "秒", BarColor.GREEN, BarStyle.SOLID);

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("OniGo プラグイン有効化");
    }

    @Override
    public void onDisable() {
        try {
            if (fadeTask != null) {
                fadeTask.cancel();
                fadeTask = null;
            }

            if (reverseFadeTask != null) {
                reverseFadeTask.cancel();
                reverseFadeTask = null;
            }

            if (gameTimerTask != null) {
                gameTimerTask.cancel();
                gameTimerTask = null;
            }

            if (oniSlownessTask != null) {
                oniSlownessTask.cancel();
                oniSlownessTask = null;
            }

            // Clear kakureDamaTask safely
            for (UUID uuid : new ArrayList<>(kakureDamaTask.keySet())) {
                BukkitTask task = kakureDamaTask.get(uuid);
                if (task != null) {
                    task.cancel();
                }
            }
            kakureDamaTask.clear();

            // Clear respawnTasks safely
            for (UUID uuid : new ArrayList<>(respawnTasks.keySet())) {
                BukkitTask task = respawnTasks.get(uuid);
                if (task != null) {
                    task.cancel();
                }
            }
            respawnTasks.clear();

            if (timerBar != null) {
                timerBar.removeAll();
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != null && p.isOnline()) {
                    // すべてのポーション効果をクリア
                    clearAllPotionEffects(p);
                    p.stopSound(ONISONG1_SOUND);
                    p.stopSound(ONISONG2_SOUND);
                    // 食料レベルを20（満腹）に戻す
                    p.setFoodLevel(20);
                }
            }
            getLogger().info("OniGo プラグイン停止");
        } catch (Exception e) {
            getLogger().severe("プラグイン停止中にエラーが発生しました: " + e.getMessage());
        }
    }

    // =============================
    // Configに保存＆読み込み
    // =============================
    private void loadChestAndDoorFromConfig() {
        FileConfiguration config = getConfig();
        // chestLocations
        if (config.isConfigurationSection("chests")) {
            for (String chestName : config.getConfigurationSection("chests").getKeys(false)) {
                double x = config.getDouble("chests." + chestName + ".x");
                double y = config.getDouble("chests." + chestName + ".y");
                double z = config.getDouble("chests." + chestName + ".z");
                String worldName = config.getString("chests." + chestName + ".world");
                Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                chestLocations.put(chestName, loc);
                chestOpened.put(chestName, false); // 起動時は未開封にしておく
            }
        }
        // doorLocation
        if (config.isConfigurationSection("door")) {
            double x = config.getDouble("door.x");
            double y = config.getDouble("door.y");
            double z = config.getDouble("door.z");
            String worldName = config.getString("door.world");
            doorLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        }
    }

    private void saveChestAndDoorToConfig() {
        FileConfiguration config = getConfig();
        // 一旦削除して書き直す
        config.set("chests", null);
        for (Map.Entry<String, Location> entry : chestLocations.entrySet()) {
            String chestName = entry.getKey();
            Location loc = entry.getValue();
            config.set("chests." + chestName + ".x", loc.getX());
            config.set("chests." + chestName + ".y", loc.getY());
            config.set("chests." + chestName + ".z", loc.getZ());
            config.set("chests." + chestName + ".world", loc.getWorld().getName());
        }
        if (doorLocation != null) {
            config.set("door.x", doorLocation.getX());
            config.set("door.y", doorLocation.getY());
            config.set("door.z", doorLocation.getZ());
            config.set("door.world", doorLocation.getWorld().getName());
        }
        saveConfig();
    }

    // =============================
    // イベント: プレイヤーJoin -> スポーン位置
    // =============================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location initialSpawn = new Location(player.getWorld(), 6, 18, -28);
        player.teleport(initialSpawn);
    }

    // =============================
    // イベント: 鬼の食料値を2に固定
    // =============================
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if (oniTeam != null && oniTeam.hasEntry(p.getName())) {
            event.setFoodLevel(2);
        }
    }

    // =============================
    // スコアボードセットアップ
    // =============================
    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        playerTeam = scoreboard.registerNewTeam("player");
        oniTeam = scoreboard.registerNewTeam("oni");

        playerTeam.setDisplayName("プレイヤー");
        oniTeam.setDisplayName("鬼");

        playerTeam.setColor(ChatColor.BLUE);
        oniTeam.setColor(ChatColor.RED);

        // ネームタグをオフにする設定
        playerTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        oniTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        objective = scoreboard.registerNewObjective("gameStats", "dummy", ChatColor.GOLD + "鬼ごっこ");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    // =============================
    // コマンド
    // =============================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "yasha":
                if (fadeTask == null && reverseFadeTask == null) {
                    startYashaEffect(player);
                }
                break;
            case "end":
                // 修正: フェードタスクの有無にかかわらず常に効果を停止する
                stopYashaEffect(player);
                player.sendMessage(ChatColor.GREEN + "夜叉効果を終了したよ！");
                break;
            case "getcmditem":
                giveGameItems(player);
                break;
            case "re":
                // リセットしてプラグイン再起動
                if (fadeTask != null) fadeTask.cancel();
                if (reverseFadeTask != null) reverseFadeTask.cancel();
                resetGame();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.removePotionEffect(PotionEffectType.DARKNESS);
                    p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    p.removePotionEffect(PotionEffectType.SLOWNESS);
                    p.stopSound(ONISONG1_SOUND);
                    p.stopSound(ONISONG2_SOUND);
                }
                Bukkit.getPluginManager().disablePlugin(this);
                Bukkit.getPluginManager().enablePlugin(this);
                break;
            case "start":
                startCommand(player);
                break;
            case "stop":
                stopCommand(player);
                break;
            case "onistart":
                oniStartCommand(player);
                break;
            case "set":
                // /set chest <名前> or /set door
                setCommand(player, args);
                break;
            case "gamegive":
                // 新しいコマンド：陣営選択本は全員に、スタート本は特定プレイヤーのみに
                distributeTeamSelectionBooks();
                giveGameStartBookToSpecificPlayer("minamottooooooooo");
                player.sendMessage(ChatColor.GREEN + "陣営選択本を全員に配布し、ゲームスタート本をminamottoooooooooに配布しました！");
                break;
        }
        return true;
    }

    // =============================
    // 陣営選択本をすべてのプレイヤーに配布するメソッド
    // =============================
    private void distributeTeamSelectionBooks() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack selectBook = createTeamSelectBook();
            p.getInventory().addItem(selectBook);
        }
    }

    // =============================
    // ゲームスタート本を特定のプレイヤーのみに配布するメソッド
    // =============================
    private void giveGameStartBookToSpecificPlayer(String playerName) {
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            ItemStack startBook = createGameStartBook();
            targetPlayer.getInventory().addItem(startBook);
        }
    }

    private void setCommand(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "使い方: /set chest <名前>  または  /set door");
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("chest")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "チェストの名前を指定してね: /set chest <名前>");
                return;
            }
            // プレイヤーが見ているブロックをチェストとして登録
            Block block = player.getTargetBlockExact(5); // 5ブロック以内
            if (block == null || !(block.getState() instanceof Chest)) {
                player.sendMessage(ChatColor.RED + "近くにチェストがないか、ターゲットしていないよ！");
                return;
            }
            String chestName = args[1];
            Location loc = block.getLocation();
            chestLocations.put(chestName, loc);
            chestOpened.put(chestName, false);
            player.sendMessage(ChatColor.GREEN + "チェスト「" + chestName + "」を登録したよ！");
            // Configに保存
            saveChestAndDoorToConfig();
        }
        else if (sub.equals("door")) {
            // プレイヤーが見ているブロックをドアとして登録
            Block block = player.getTargetBlockExact(5);
            if (block == null || (!block.getType().toString().contains("DOOR"))) {
                player.sendMessage(ChatColor.RED + "近くにドアがないか、ターゲットしていないよ！");
                return;
            }
            doorLocation = block.getLocation();
            player.sendMessage(ChatColor.GREEN + "ドアを登録したよ！");
            // Configに保存
            saveChestAndDoorToConfig();
        }
        else {
            player.sendMessage(ChatColor.RED + "使い方: /set chest <名前>  または  /set door");
        }
    }

    // =============================
    // コマンド処理: getcmditem
    // =============================
    private void giveGameItems(Player player) {
        // 夜叉
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.setDisplayName("夜叉");
        star.setItemMeta(meta);
        player.getInventory().addItem(star);

        // 隠れ玉(ダイヤモンド)
        ItemStack kakureDama = createKakureDama();
        player.getInventory().addItem(kakureDama);

        // 陣営選択本
        ItemStack selectBook = createTeamSelectBook();
        player.getInventory().addItem(selectBook);

        // ゲームスタート本（新規追加）
        ItemStack startBook = createGameStartBook();
        player.getInventory().addItem(startBook);

        player.sendMessage(ChatColor.GREEN + "ゲームアイテムを取得したよ！");
    }

    // 陣営選択本を作成するメソッド
    private ItemStack createTeamSelectBook() {
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

    // ゲームスタート本を作成するメソッド
    private ItemStack createGameStartBook() {
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

    // =============================
    // チーム別のアイテム配布メソッド
    // =============================
    private void distributeTeamItems() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.getInventory().setHelmet(null);
            p.getInventory().setChestplate(null);
            p.getInventory().setLeggings(null);
            p.getInventory().setBoots(null);
            p.getInventory().setItemInOffHand(null);

            if (oniTeam.hasEntry(p.getName())) {
                // 鬼チームには夜叉のみ
                ItemStack star = new ItemStack(Material.NETHER_STAR);
                ItemMeta meta = star.getItemMeta();
                meta.setDisplayName("夜叉");
                star.setItemMeta(meta);
                p.getInventory().addItem(star);
                p.sendMessage(ChatColor.RED + "鬼チーム用アイテムを付与しました");
            } else if (playerTeam.hasEntry(p.getName())) {
                // プレイヤーチームには隠れ玉のみ
                ItemStack kakureDama = createKakureDama();
                p.getInventory().addItem(kakureDama);
                p.sendMessage(ChatColor.BLUE + "プレイヤーチーム用アイテムを付与しました");
            }
        }
    }

    // =============================
    // みんなにアイテム配布するメソッド
    // =============================
    private void distributeItems() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            giveGameItems(p);
        }
    }

    // =============================
    // コマンド処理: start
    // =============================
    private void startCommand(Player sender) {
        // インベントリをしっかりクリア（アーマースロットなど全てを含む）
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.getInventory().setHelmet(null);
            p.getInventory().setChestplate(null);
            p.getInventory().setLeggings(null);
            p.getInventory().setBoots(null);
            p.getInventory().setItemInOffHand(null);
        }

        List<UUID> notSelected = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!playerTeam.hasEntry(p.getName()) && !oniTeam.hasEntry(p.getName())) {
                notSelected.add(p.getUniqueId());
            }
        }
        if (!notSelected.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "選択していない人がいるよ。UUID:" + notSelected.toString());
            return;
        }
        if (!gameRunning) {
            startGame(sender);
        } else {
            sender.sendMessage(ChatColor.RED + "ゲームはすでに進行中だよ！");
        }
    }

    // =============================
    // コマンド処理: stop
    // =============================
    private void stopCommand(Player sender) {
        if (gameRunning) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム中断 =========");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "管理者によりゲーム中断されたよ");
            Bukkit.broadcastMessage(ChatColor.GOLD + "============================");
            resetGame();
            sender.sendMessage(ChatColor.GREEN + "ゲームを停止したよ！");
        } else {
            sender.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
        }
    }

    // =============================
    // コマンド処理: onistart
    // =============================
    private void oniStartCommand(Player player) {
        // インベントリをしっかりクリア（アーマースロットなど全てを含む）
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.getInventory().setHelmet(null);
            p.getInventory().setChestplate(null);
            p.getInventory().setLeggings(null);
            p.getInventory().setBoots(null);
            p.getInventory().setItemInOffHand(null);
        }

        playerTeam.removeEntry(player.getName());
        oniTeam.addEntry(player.getName());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getName().equals(player.getName())) {
                if (!playerTeam.hasEntry(p.getName()) && !oniTeam.hasEntry(p.getName())) {
                    playerTeam.addEntry(p.getName());
                }
            }
        }
        startGame(player);
    }

    // =============================
    // ゲーム開始処理
    // =============================
    private void startGame(Player sender) {
        gameRunning = true;
        remainingTime = 500;

        // ゲーム開始時のプレイヤー数をカウント
        initialPlayerCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName())) {
                initialPlayerCount++;
            }
        }

        // 脱出プレイヤーリストをクリア
        escapedPlayers.clear();

        // 全プレイヤーのインベントリを再度確実にクリア（念のため）
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();
            p.getInventory().setHelmet(null);
            p.getInventory().setChestplate(null);
            p.getInventory().setLeggings(null);
            p.getInventory().setBoots(null);
            p.getInventory().setItemInOffHand(null);
        }

        // プレイヤー陣営の隠れ玉初期化
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName())) {
                kakureDamaRemaining.put(p.getUniqueId(), 15);
            }
        }

        // 鬼＆プレイヤーのテレポート
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (oniTeam.hasEntry(p.getName())) {
                Location[] oniSpawns = new Location[]{
                        new Location(p.getWorld(), -2, -4, -24)
                };
                int randomIndex = (int) (Math.random() * oniSpawns.length);
                p.teleport(oniSpawns[randomIndex]);
                p.setFoodLevel(2);
                p.setGameMode(GameMode.ADVENTURE);
            } else if (playerTeam.hasEntry(p.getName())) {
                p.teleport(new Location(p.getWorld(), 0, 2, 0));
                p.setGameMode(GameMode.ADVENTURE);
            }
        }

        // チーム別アイテム支給
        distributeTeamItems();

        // タイマー開始
        startGameTimer();
        timerBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            timerBar.addPlayer(p);
        }

        // 全チェストを未開封にリセット
        for (String name : chestOpened.keySet()) {
            chestOpened.put(name, false);
        }

        // 鬼に常にスロウ効果2を付与するタスク
        startOniSlownessTask();

        updateScoreboard();
        Bukkit.broadcastMessage(ChatColor.GREEN + "ゲーム開始！残り時間: 500秒");
    }

    // =============================
    // 鬼に常にスロウ2を付与するタスク
    // =============================
    private void startOniSlownessTask() {
        if (oniSlownessTask != null) {
            oniSlownessTask.cancel();
        }
        oniSlownessTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) return;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (oniTeam.hasEntry(p.getName())) {
                        // スロウネスII(レベル1)を付与し直す
                        if (!p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                            p.addPotionEffect(new PotionEffect(
                                    PotionEffectType.SLOWNESS,
                                    999999,
                                    2, // スロウネスII
                                    false, false
                            ));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 1秒毎にチェック
    }

    // =============================
    // ゲームリセット
    // =============================
    private void resetGame() {
        gameRunning = false;
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
        if (oniSlownessTask != null) {
            oniSlownessTask.cancel();
            oniSlownessTask = null;
        }

        // カクレダマタスク停止
        for (UUID uuid : new ArrayList<>(kakureDamaTask.keySet())) {
            BukkitTask task = kakureDamaTask.get(uuid);
            if (task != null) {
                task.cancel();
            }
        }
        kakureDamaTask.clear();

        // リスポーンタスク停止
        for (UUID uuid : new ArrayList<>(respawnTasks.keySet())) {
            BukkitTask task = respawnTasks.get(uuid);
            if (task != null) {
                task.cancel();
            }
        }
        respawnTasks.clear();

        if (timerBar != null) {
            timerBar.removeAll();
        }

        // プレイヤーの状態をリセット
        for (Player player : Bukkit.getOnlinePlayers()) {
            // すべてのポーション効果をクリア
            clearAllPotionEffects(player);
            player.setGameMode(GameMode.SURVIVAL);
            // 食料レベルを20（満腹）に戻す
            player.setFoodLevel(20);

            // インベントリをクリア
            player.getInventory().clear();
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
            player.getInventory().setItemInOffHand(null);
        }

        // チームやスコアボードの初期化
        if (playerTeam != null) playerTeam.unregister();
        if (oniTeam != null) oniTeam.unregister();
        if (objective != null) objective.unregister();
        setupScoreboard();

        // 全員を(6,18,-28)へ
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(new Location(player.getWorld(), 6, 18, -28));
        }
    }

    // =============================
    // タイマー開始
    // =============================
    private void startGameTimer() {
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
        }.runTaskTimer(this, 20L, 20L);
    }

    // =============================
    // スコアボード更新
    // =============================
    private void updateScoreboard() {
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
        Score timeScore = objective.getScore(ChatColor.YELLOW + "残り時間: " + remainingTime + "秒");
        timeScore.setScore(7);

        int survivors = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName()) && p.getGameMode() != GameMode.SPECTATOR) {
                survivors++;
            }
        }
        Score survivorsScore = objective.getScore(ChatColor.GREEN + "生存者数: " + survivors + "人");
        survivorsScore.setScore(6);

        Score escapedScore = objective.getScore(ChatColor.AQUA + "脱出者数: " + escapedPlayers.size() + "人");
        escapedScore.setScore(5);

        Score oniScore = objective.getScore(ChatColor.RED + "鬼: " + oniTeam.getSize() + "人");
        oniScore.setScore(4);

        Score playerScore = objective.getScore(ChatColor.BLUE + "プレイヤー: " + playerTeam.getSize() + "人");
        playerScore.setScore(3);

        int i = 2;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerTeam.hasEntry(p.getName())) {
                int rem = kakureDamaRemaining.getOrDefault(p.getUniqueId(), 0);
                Score kdScore = objective.getScore(ChatColor.AQUA + p.getName() + ": 隠れ玉 " + rem + "秒");
                kdScore.setScore(i);
                i--;
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(scoreboard);
        }
    }

    // =============================
    // 隠れ玉作成
    // =============================
    private ItemStack createKakureDama() {
        ItemStack kakureDama = new ItemStack(Material.DIAMOND);
        ItemMeta meta = kakureDama.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "隠れ玉");
        List<String> lore = new ArrayList<>();
        lore.add("右クリックで透明化、再度右クリックで透明解除");
        meta.setLore(lore);
        kakureDama.setItemMeta(meta);
        return kakureDama;
    }

    // =============================
    // ゲーム終了
    // =============================
    private void endGame() {
        gameRunning = false;
        int halfInitial = initialPlayerCount / 2;
        int playerCount = playerTeam.getSize();
        String winMessage;
        if (playerCount > halfInitial) {
            winMessage = ChatColor.BLUE + "プレイヤー陣営の勝利！";
        } else if (playerCount == halfInitial) {
            winMessage = ChatColor.YELLOW + "引き分け！";
        } else {
            winMessage = ChatColor.RED + "鬼陣営の勝利！";
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
        Bukkit.broadcastMessage(winMessage);
        Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

        // 全員をテレポート
        Location spawnLocation = new Location(Bukkit.getWorlds().get(0), 6, 18, -28);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
        }

        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
        resetGame();
    }

    // =============================
    // すべてのポーション効果をクリアする
    // =============================
    private void clearAllPotionEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    // =============================
    // プレイヤー死亡時処理
    // =============================
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (gameRunning && playerTeam.hasEntry(player.getName())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatColor.RED + "死亡したよ。5秒後に復活する…");
            updateScoreboard();

            // 生存者数を確認
            int survivors = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (playerTeam.hasEntry(p.getName()) && p.getGameMode() != GameMode.SPECTATOR) {
                    survivors++;
                }
            }

            // 生存者がいなくなった、かつ初期プレイヤー数が1だった場合はゲーム終了
            if (survivors == 0) {
                if (initialPlayerCount == 1) {
                    Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
                    Bukkit.broadcastMessage(ChatColor.RED + "一撃必殺！プレイヤー全滅！鬼陣営の勝利！");
                    Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

                    // 全員をテレポート
                    Location spawnLocation = new Location(player.getWorld(), 6, 18, -28);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.teleport(spawnLocation);
                    }

                    resetGame();
                    return;
                } else {
                    Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
                    Bukkit.broadcastMessage(ChatColor.RED + "プレイヤー全滅！鬼陣営の勝利！");
                    Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

                    // 全員をテレポート
                    Location spawnLocation = new Location(player.getWorld(), 6, 18, -28);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.teleport(spawnLocation);
                    }

                    resetGame();
                    return;
                }
            }

            // 通常はリスポーンタスク
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
            }.runTaskLater(this, 20L * 5));
        }
    }

    // =============================
    // プレイヤーがチェストを開けたかどうか判定
    // =============================
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!gameRunning) return;
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // 開いたインベントリがチェストか判定
        if (event.getInventory().getLocation() == null) return; // たとえばエンダーチェストだとnullの場合あり
        Location loc = event.getInventory().getLocation();

        // chestLocationsに登録されているかチェック
        for (Map.Entry<String, Location> entry : chestLocations.entrySet()) {
            String chestName = entry.getKey();
            Location chestLoc = entry.getValue();
            if (chestLoc.equals(loc)) {
                // このチェストを開けた
                if (!chestOpened.get(chestName)) {
                    chestOpened.put(chestName, true);
                    player.sendMessage(ChatColor.AQUA + "チェスト「" + chestName + "」を開封したよ！");
                    // 全部開けたかチェック
                    checkAllChestOpened();
                }
            }
        }
    }

    // =============================
    // 全チェスト開封済みかどうかをチェック -> 全部開いてたらドアを開ける
    // =============================
    private void checkAllChestOpened() {
        for (boolean opened : chestOpened.values()) {
            if (!opened) {
                return; // 1つでも未開封があるので終了
            }
        }
        // 全部開封済み
        Bukkit.broadcastMessage(ChatColor.GOLD + "すべてのチェストが開けられた！玄関のドアが開くよ！");
        openDoor();
    }

    // =============================
    // ドアを開ける(=ブロックをエアにする)
    // =============================
    private void openDoor() {
        if (doorLocation == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "ドアが登録されていないので開けられません！");
            return;
        }
        Block block = doorLocation.getBlock();
        // シンプルにブロックを空気に置き換える
        block.setType(Material.AIR);
        // 必要に応じて上のブロックも消すなどアレンジしてOK
    }

    // =============================
    // 右クリックでのアイテム処理
    // =============================
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Player player = event.getPlayer();

        // 夜叉アイテム
        if (item.getType() == Material.NETHER_STAR && item.hasItemMeta() &&
                "夜叉".equals(item.getItemMeta().getDisplayName())) {
            event.setCancelled(true);
            if (fadeTask == null && reverseFadeTask == null) {
                startYashaEffect(player);
            } else {
                stopYashaEffect(player);
            }
        }
        // 隠れ玉(ダイヤモンド)
        else if (item.getType() == Material.DIAMOND && item.hasItemMeta() &&
                (ChatColor.AQUA + "隠れ玉").equals(item.getItemMeta().getDisplayName())) {
            event.setCancelled(true);
            if (!gameRunning) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!playerTeam.hasEntry(player.getName())) {
                player.sendMessage(ChatColor.RED + "プレイヤー陣営のみ使用可能だよ！");
                return;
            }
            if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                // 解除
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                if (kakureDamaTask.containsKey(player.getUniqueId())) {
                    kakureDamaTask.get(player.getUniqueId()).cancel();
                    kakureDamaTask.remove(player.getUniqueId());
                }
                player.sendMessage(ChatColor.GREEN + "透明解除したよ！");
            } else {
                int remain = kakureDamaRemaining.getOrDefault(player.getUniqueId(), 0);
                if (remain <= 0) {
                    player.sendMessage(ChatColor.RED + "隠れ玉の使用時間がなくなったよ！");
                    return;
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 999999, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 1, false, false));
                player.sendMessage(ChatColor.AQUA + "透明化したよ！残り " + remain + "秒");
                BukkitTask task = new BukkitRunnable() {
                    int timeLeft = remain;
                    @Override
                    public void run() {
                        timeLeft--;
                        kakureDamaRemaining.put(player.getUniqueId(), timeLeft);
                        updateScoreboard();
                        if (timeLeft <= 0) {
                            player.removePotionEffect(PotionEffectType.INVISIBILITY);
                            player.removePotionEffect(PotionEffectType.SLOWNESS);
                            player.sendMessage(ChatColor.RED + "隠れ玉の効果が切れたよ！");
                            this.cancel();
                            kakureDamaTask.remove(player.getUniqueId());
                        }
                    }
                }.runTaskTimer(this, 20L, 20L);
                kakureDamaTask.put(player.getUniqueId(), task);
            }
        }
        // 陣営選択本
        else if (item.getType() == Material.WRITTEN_BOOK && item.hasItemMeta()) {
            BookMeta bookMeta = (BookMeta) item.getItemMeta();
            if ("陣営選択本".equals(bookMeta.getTitle())) {
                event.setCancelled(true);
                openTeamSelectionGUI(player);
            }
            // ゲームスタート本
            else if ("ゲームスタート本".equals(bookMeta.getTitle())) {
                event.setCancelled(true);
                openGameStartGUI(player);
            }
        }
    }

    // =============================
    // ゲームスタートGUI
    // =============================
    private void openGameStartGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "ゲームスタート");

        ItemStack normalStart = new ItemStack(Material.GREEN_WOOL);
        ItemMeta normalMeta = normalStart.getItemMeta();
        normalMeta.setDisplayName("§2通常スタート");
        List<String> normalLore = new ArrayList<>();
        normalLore.add("§7すべてのプレイヤーが陣営選択済みの場合にゲームを開始します");
        normalMeta.setLore(normalLore);
        normalStart.setItemMeta(normalMeta);
        inv.setItem(2, normalStart);

        ItemStack oniStart = new ItemStack(Material.RED_WOOL);
        ItemMeta oniMeta = oniStart.getItemMeta();
        oniMeta.setDisplayName("§c鬼スタート");
        List<String> oniLore = new ArrayList<>();
        oniLore.add("§7あなたが鬼になります");
        oniLore.add("§7他のプレイヤーは自動的にプレイヤー陣営になります");
        oniMeta.setLore(oniLore);
        oniStart.setItemMeta(oniMeta);
        inv.setItem(6, oniStart);

        player.openInventory(inv);
    }

    // =============================
    // 夜叉効果開始 - 鬼チームにはダークネス効果を適用しない
    // =============================
    private void startYashaEffect(Player player) {
        activeYashaPlayer = player;
        if (gameRunning && playerTeam.hasEntry(player.getName())) {
            playerTeam.removeEntry(player.getName());
            oniTeam.addEntry(player.getName());
            player.sendMessage(ChatColor.RED + "夜叉になったから、鬼陣営に移ったよ！");
            updateScoreboard();
        }
        for (Player p : player.getWorld().getPlayers()) {
            p.playSound(p.getLocation(), ONISONG1_SOUND, 1.0f, 1.0f);
        }
        currentDarkness = 0;
        fadeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentDarkness < 2) {
                    currentDarkness++;
                }
                for (Player p : player.getWorld().getPlayers()) {
                    // 鬼チームにはダークネス効果を適用しない
                    if (oniTeam != null && oniTeam.hasEntry(p.getName())) {
                        continue;
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, currentDarkness, false, false));
                    p.playSound(activeYashaPlayer.getLocation(), ONISONG2_SOUND, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(this, 0L, 60L);
    }

    // =============================
    // 夜叉効果停止 - 鬼チームには影響しないように
    // =============================
    private void stopYashaEffect(Player player) {
        // まず古いタスクをキャンセル
        if (fadeTask != null) {
            fadeTask.cancel();
            fadeTask = null;
        }

        // 古いリバースフェードタスクがあれば先にキャンセル
        if (reverseFadeTask != null) {
            reverseFadeTask.cancel();
            reverseFadeTask = null;
        }

        // 新しいリバースフェードタスクを開始
        reverseFadeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentDarkness > 0) {
                    currentDarkness--;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p != null && p.isOnline()) {
                            // 鬼チームには影響しないように
                            if (oniTeam != null && oniTeam.hasEntry(p.getName())) {
                                continue;
                            }
                            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, currentDarkness, false, false));
                        }
                    }
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p != null && p.isOnline()) {
                            // 鬼チームには影響しないように
                            if (oniTeam != null && oniTeam.hasEntry(p.getName())) {
                                continue;
                            }
                            p.removePotionEffect(PotionEffectType.DARKNESS);
                            p.stopSound(ONISONG1_SOUND);
                            p.stopSound(ONISONG2_SOUND);
                        }
                    }
                    // 自分自身をnullにしてからcancelする
                    BukkitTask taskToCancel = reverseFadeTask;
                    reverseFadeTask = null;
                    if (taskToCancel != null) {
                        taskToCancel.cancel();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 60L);

        activeYashaPlayer = null;
    }

    // =============================
    // 走るのを禁止 (鬼は走れない)
    // =============================
    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!gameRunning) return;
        // 鬼は走れない
        if (oniTeam.hasEntry(player.getName())) {
            event.setCancelled(true);
        }
    }

    // =============================
    // 陣営選択GUI
    // =============================
    private void openTeamSelectionGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "陣営選択");

        ItemStack playerItem = new ItemStack(Material.PAPER);
        ItemMeta pMeta = playerItem.getItemMeta();
        pMeta.setDisplayName("プレイヤー陣営");
        playerItem.setItemMeta(pMeta);
        inv.setItem(3, playerItem);

        ItemStack oniItem = new ItemStack(Material.PAPER);
        ItemMeta oMeta = oniItem.getItemMeta();
        oMeta.setDisplayName("鬼陣営");
        oniItem.setItemMeta(oMeta);
        inv.setItem(5, oniItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 陣営選択GUI
        if (event.getView().getTitle().equals("陣営選択")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            String dispName = event.getCurrentItem().getItemMeta().getDisplayName();
            if ("プレイヤー陣営".equals(dispName)) {
                oniTeam.removeEntry(player.getName());
                playerTeam.addEntry(player.getName());
                player.sendMessage(ChatColor.BLUE + "プレイヤー陣営に選択されたよ！");
                player.closeInventory();
                updateScoreboard();
            } else if ("鬼陣営".equals(dispName)) {
                playerTeam.removeEntry(player.getName());
                oniTeam.addEntry(player.getName());
                player.sendMessage(ChatColor.RED + "鬼陣営に選択されたよ！");
                player.closeInventory();
                updateScoreboard();
            }
        }
        // ゲームスタートGUI
        else if (event.getView().getTitle().equals("ゲームスタート")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            String dispName = event.getCurrentItem().getItemMeta().getDisplayName();
            if ("§2通常スタート".equals(dispName)) {
                player.closeInventory();
                startCommand(player);
            } else if ("§c鬼スタート".equals(dispName)) {
                player.closeInventory();
                oniStartCommand(player);
            }
        }
    }

    // =============================
    // プレイヤー同士のPvP無効 & 鬼→プレイヤーは一撃必殺にする
    // =============================
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;
        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();

        // プレイヤー同士のPvPは無効
        if (playerTeam.hasEntry(damager.getName()) && playerTeam.hasEntry(target.getName())) {
            event.setCancelled(true);
            return;
        }

        // 鬼がプレイヤーを攻撃（一撃必殺）
        if (gameRunning && oniTeam.hasEntry(damager.getName()) && playerTeam.hasEntry(target.getName())) {
            event.setCancelled(true); // 通常のダメージ処理をキャンセル
            damager.sendMessage(ChatColor.RED + target.getName() + "を一撃で倒した！");
            target.sendMessage(ChatColor.RED + "鬼に襲われて死亡した！");
            target.damage(1000); // 実質的に必殺ダメージ
        }
    }

    // =============================
    // プレイヤー移動イベント - 脱出判定
    // =============================
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameRunning) return;

        Player player = event.getPlayer();
        if (!playerTeam.hasEntry(player.getName())) return; // プレイヤー陣営のみ判定
        if (player.getGameMode() == GameMode.SPECTATOR) return; // 観戦者は除外
        if (escapedPlayers.contains(player.getUniqueId())) return; // すでに脱出済みなら処理しない

        // 脱出地点の近くにいるか確認 (3ブロック以内)
        Location playerLoc = player.getLocation();
        if (playerLoc.getWorld().equals(escapeLocation.getWorld()) &&
                playerLoc.distance(escapeLocation) <= 3) {

            // 脱出成功
            escapedPlayers.add(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "脱出地点に到達しました！");
            Bukkit.broadcastMessage(ChatColor.AQUA + player.getName() + "が脱出地点に到達しました！");

            // 脱出効果音
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // スコアボード更新
            updateScoreboard();

            // 過半数が脱出したかチェック
            checkEscapeVictory();
        }
    }

    // =============================
    // 過半数脱出でのゲームクリア判定
    // =============================
    private void checkEscapeVictory() {
        if (!gameRunning) return;

        // 脱出した人数と全プレイヤー数
        int totalPlayers = initialPlayerCount;
        int escaped = escapedPlayers.size();

        // 過半数が脱出したらプレイヤー陣営の勝利
        if (escaped > totalPlayers / 2) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "========= ゲーム終了 =========");
            Bukkit.broadcastMessage(ChatColor.BLUE + "過半数のプレイヤーが脱出に成功！プレイヤー陣営の勝利！");
            Bukkit.broadcastMessage(ChatColor.GOLD + "===========================");

            // 全員をテレポート
            Location spawnLocation = new Location(Bukkit.getWorlds().get(0), 6, 18, -28);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.teleport(spawnLocation);
            }

            resetGame();
        }
    }
}