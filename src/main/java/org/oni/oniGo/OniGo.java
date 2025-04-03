package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;




public final class OniGo extends JavaPlugin implements CommandExecutor, Listener {

    public static final String TUKI_SOUND     = "minecraft:tuki";
    public static final String MIKADUKI_SOUND = "minecraft:mikaduki";
    public static final String SATSUKI_SOUND  = "minecraft:satsuki";


    // Managers
    private ConfigManager configManager;
    private EffectManager effectManager;
    private GameManager gameManager;
    private ItemManager itemManager;
    private TeamManager teamManager;

    // カスタム入力モード用
    private Map<UUID, InputMode> playerInputModes = new HashMap<>();

    // 入力モード種類
    private enum InputMode {
        CHEST_COUNT,
        GAME_TIME,
        NONE
    }

    @Override
    public void onEnable() {
        // Initialize managers
        initializeManagers();

        // Register commands
        registerCommands();

        // Load config data
        configManager.loadConfig();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("OniGo プラグイン有効化");
    }

    @Override
    public void onDisable() {
        try {
            // Cancel all running tasks through the GameManager
            if (gameManager != null) {
                gameManager.resetGame();
            }
            // Clear effects through the EffectManager
            if (effectManager != null) {
                effectManager.clearAllEffects();
            }
            getLogger().info("OniGo プラグイン停止");
        } catch (Exception e) {
            getLogger().severe("プラグイン停止中にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * Initialize all manager classes
     */
    private void initializeManagers() {
        // Create managers in the right order to handle dependencies
        configManager = new ConfigManager(this);
        teamManager = new TeamManager(this);
        effectManager = new EffectManager(this, teamManager);
        itemManager = new ItemManager(this, teamManager);
        gameManager = new GameManager(this, configManager, effectManager, itemManager, teamManager);
    }

    /**
     * Register all commands
     */
    private void registerCommands() {
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
        if (getCommand("set") != null) {
            getCommand("set").setExecutor(this);
        }
        if (getCommand("gamegive") != null) {
            getCommand("gamegive").setExecutor(this);
        }
        if (getCommand("rtp") != null) {
            getCommand("rtp").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) && !command.getName().equalsIgnoreCase("rtp")) {
            return true;
        }

        Player player = (sender instanceof Player) ? (Player) sender : null;
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "yasha":
                if (!effectManager.isYashaActive()) {
                    effectManager.startYashaEffect(player);
                } else {
                    effectManager.stopYashaEffect();
                    if (player != null) {
                        player.sendMessage(ChatColor.GREEN + "夜叉効果を終了したよ！");
                    }
                }
                break;
            case "end":
                effectManager.stopYashaEffect();
                if (player != null) {
                    player.sendMessage(ChatColor.GREEN + "夜叉効果を終了したよ！");
                }
                break;
            case "getcmditem":
                if (player != null) {
                    itemManager.giveAllGameItems(player);
                }
                break;
            case "re":
                // Reset plugin
                if (effectManager.isYashaActive()) {
                    effectManager.stopYashaEffect();
                }
                if (gameManager != null) {
                    gameManager.resetGame();
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    effectManager.clearAllPotionEffects(p);
                    p.stopSound(EffectManager.ONISONG1_SOUND);
                    p.stopSound(EffectManager.ONISONG2_SOUND);
                }
                Bukkit.getPluginManager().disablePlugin(this);
                Bukkit.getPluginManager().enablePlugin(this);
                break;
            case "start":
                if (player != null) {
                    gameManager.startGame(player);
                }
                break;
            case "stop":
                if (player != null) {
                    gameManager.stopGame(player);
                }
                break;
            case "onistart":
                if (player != null) {
                    gameManager.oniStartGame(player);
                }
                break;
            case "set":
                if (player != null) {
                    handleSetCommand(player, args);
                }
                break;
            case "gamegive":
                itemManager.distributeTeamSelectionBooks();
                itemManager.giveGameStartBook("minamottooooooooo");
                itemManager.giveChestCountBook("minamottooooooooo");
                itemManager.giveGameTimeBook("minamottooooooooo");
                sendConfigMessage(player, ChatColor.GREEN + "必要なガイドブックをminamottoooooooooに配布しました！");
                break;
            case "rtp":
                handleRtpCommand(sender, args);
                break;
        }
        return true;
    }

    /**
     * Handle the /rtp command
     */
    private void handleRtpCommand(CommandSender sender, String[] args) {
        Location spawnLoc = configManager.getInitialSpawnLocation();
        if (spawnLoc == null) {
            sender.sendMessage(ChatColor.RED + "初期地点が設定されていません。");
            return;
        }

        List<Player> targets = new ArrayList<>();

        if (args.length == 0) {
            // If no args and sender is player, teleport the sender
            if (sender instanceof Player) {
                targets.add((Player) sender);
            } else {
                sender.sendMessage(ChatColor.RED + "使い方: /rtp <プレイヤー名> または /rtp @p または /rtp @a");
                return;
            }
        } else {
            String targetArg = args[0];

            if (targetArg.equals("@a")) {
                // Target all players
                targets.addAll(Bukkit.getOnlinePlayers());
            } else if (targetArg.equals("@p")) {
                // Target nearest player to sender
                if (sender instanceof Player) {
                    Player nearestPlayer = getNearestPlayer((Player) sender);
                    if (nearestPlayer != null) {
                        targets.add(nearestPlayer);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "コンソールからは@pを使用できません。");
                    return;
                }
            } else {
                // Target specific player
                Player targetPlayer = Bukkit.getPlayerExact(targetArg);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    targets.add(targetPlayer);
                } else {
                    sender.sendMessage(ChatColor.RED + "プレイヤー「" + targetArg + "」が見つかりません。");
                    return;
                }
            }
        }

        // Teleport all target players
        for (Player target : targets) {
            target.teleport(spawnLoc);
            target.sendMessage(ChatColor.GREEN + "初期地点にテレポートしました。");
        }

        // Confirmation message
        if (targets.size() > 0) {
            sender.sendMessage(ChatColor.GREEN + "" + targets.size() + "人のプレイヤーを初期地点にテレポートしました。");
        }
    }

    /**
     * Get the nearest player to a source player
     */
    private Player getNearestPlayer(Player source) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(source)) continue; // Skip self
            double distance = p.getLocation().distance(source.getLocation());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = p;
            }
        }
        return nearest != null ? nearest : source; // If no other players, return self
    }

    /**
     * Handle the /set command to register chests and doors
     */
    private void handleSetCommand(Player player, String[] args) {
        if (args.length == 0) {
            sendConfigMessage(player, ChatColor.RED + "使い方: /set chest <名前>, /set countchest <名前>, /set door, /set exitdoor, /set setreq <数>");
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("chest")) {
            if (args.length < 2) {
                sendConfigMessage(player, ChatColor.RED + "チェストの名前: /set chest <名前>");
                return;
            }
            Block block = player.getTargetBlockExact(5);
            if (block == null || !(block.getState() instanceof org.bukkit.block.Chest)) {
                sendConfigMessage(player, ChatColor.RED + "近くのブロックがチェストじゃないか、届いてないよ！");
                return;
            }
            String chestName = args[1];
            configManager.registerChest(chestName, block.getLocation());
            sendConfigMessage(player, ChatColor.GREEN + "チェスト「" + chestName + "」を登録したよ！");
        }
        else if (sub.equals("countchest")) {
            if (args.length < 2) {
                sendConfigMessage(player, ChatColor.RED + "カウントチェストの名前: /set countchest <名前>");
                return;
            }
            Block block = player.getTargetBlockExact(5);
            if (block == null || !(block.getState() instanceof org.bukkit.block.Chest)) {
                sendConfigMessage(player, ChatColor.RED + "近くのブロックがチェストじゃないか、届いてないよ！");
                return;
            }
            String chestName = args[1];
            configManager.registerCountChest(chestName, block.getLocation());
            sendConfigMessage(player, ChatColor.GOLD + "カウントチェスト「" + chestName + "」を登録したよ！");
        }
        else if (sub.equals("door")) {
            Block block = player.getTargetBlockExact(5);
            if (block == null || (!block.getType().toString().contains("DOOR"))) {
                sendConfigMessage(player, ChatColor.RED + "近くのブロックがドアじゃないか、届いてないよ！");
                return;
            }
            configManager.registerDoor(block.getLocation());
            sendConfigMessage(player, ChatColor.GREEN + "メインドアを登録したよ！");
        }
        else if (sub.equals("exitdoor")) {
            Block block = player.getTargetBlockExact(5);
            if (block == null || (!block.getType().toString().contains("DOOR"))) {
                sendConfigMessage(player, ChatColor.RED + "近くのブロックがドアじゃないか、届いてないよ！");
                return;
            }
            configManager.registerExitDoor(block.getLocation());
            sendConfigMessage(player, ChatColor.GREEN + "出口ドアを登録したよ！");
        }
        else if (sub.equals("setreq")) {
            if (args.length < 2) {
                sendConfigMessage(player, ChatColor.RED + "必要カウントチェスト数: /set setreq <数>");
                return;
            }
            try {
                int requiredChests = Integer.parseInt(args[1]);
                if (requiredChests < 1) {
                    sendConfigMessage(player, ChatColor.RED + "1以上の値を指定してね！");
                    return;
                }
                configManager.setRequiredCountChests(requiredChests);
                sendConfigMessage(player, ChatColor.GREEN + "必要なカウントチェスト数を" + requiredChests + "に設定したよ！");
            } catch (NumberFormatException e) {
                sendConfigMessage(player, ChatColor.RED + "数値を指定してね: /set setreq <数>");
            }
        }
        else {
            sendConfigMessage(player, ChatColor.RED + "使い方: /set chest <名前>, /set countchest <名前>, /set door, /set exitdoor, /set setreq <数>");
        }
    }

    /**
     * Send config messages only to a specific admin or sender
     */
    void sendConfigMessage(Player sender, String message) {
        Player target = Bukkit.getPlayerExact("minamottooooooooo");
        if (target != null && target.isOnline()) {
            target.sendMessage(message);
        } else if (sender != null && "minamottooooooooo".equals(sender.getName())) {
            sender.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // テレポ先が設定されていればそちらに飛ばす
        if (configManager.getInitialSpawnLocation() != null) {
            player.teleport(configManager.getInitialSpawnLocation());
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if (teamManager.isPlayerInOniTeam(p)) {
            event.setFoodLevel(2);  // 鬼陣営は空腹2で固定
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        gameManager.handlePlayerDeath(player);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!gameManager.isGameRunning()) return;
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // 鬼はチェストを開けられない（月牙を除く）
        if (teamManager.isPlayerInOniTeam(player) &&
                teamManager.getPlayerOniType(player) != OniType.GETSUGA) {
            if (event.getInventory().getLocation() != null &&
                    event.getInventory().getLocation().getBlock().getState() instanceof org.bukkit.block.Chest) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "鬼はチェストを開けられません！");
                return;
            }
        }

        // 通常/カウントチェストの判定
        if (event.getInventory().getLocation() == null) return;
        Location loc = event.getInventory().getLocation();

        String chestName = configManager.getChestNameAtLocation(loc);
        if (chestName != null) {
            gameManager.handleChestOpened(chestName, player);
        }
        String countChestName = configManager.getCountChestNameAtLocation(loc);
        if (countChestName != null) {
            gameManager.handleCountChestOpened(countChestName, player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Player player = event.getPlayer();

        // 夜叉アイテム
        if (itemManager.isYashaItem(item)) {
            event.setCancelled(true);
            if (!effectManager.isYashaActive()) {
                effectManager.startYashaEffect(player);
            } else {
                effectManager.stopYashaEffect();
                player.sendMessage(ChatColor.GREEN + "夜叉効果を終了したよ！");
            }
        }
        // 隠れ玉
        else if (itemManager.isKakureDamaItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInPlayerTeam(player)) {
                player.sendMessage(ChatColor.RED + "プレイヤー陣営のみ使用可能だよ！");
                return;
            }
            if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                // 透明化解除
                effectManager.stopKakureDamaEffect(player);
                player.sendMessage(ChatColor.GREEN + "透明解除したよ！");
            } else {
                // 透明化
                effectManager.startKakureDamaEffect(player);
            }
        }
        // 鬼用「チェスト探知コンパス」
        else if (itemManager.isChestDetectorItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player)) {
                player.sendMessage(ChatColor.RED + "鬼陣営のみ使用可能だよ！");
                return;
            }
            gameManager.detectNearbyChests(player);
        }
        // 鬼用「チェストワープの真珠」
        else if (itemManager.isChestTeleporterItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player)) {
                player.sendMessage(ChatColor.RED + "鬼陣営のみ使用可能だよ！");
                return;
            }
            gameManager.teleportToNearbyChest(player);
        }
        // プレイヤー用「緊急脱出アイテム」
        else if (itemManager.isPlayerEscapeItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInPlayerTeam(player)) {
                player.sendMessage(ChatColor.RED + "プレイヤー陣営のみ使用可能だよ！");
                return;
            }
            gameManager.handlePlayerEscape(player);
        }
        // 出口の鍵
        else if (itemManager.isExitKeyItem(item) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null) return;

            // メインドアチェック
            Location doorLoc = configManager.getDoorLocation();
            if (doorLoc != null && clickedBlock.getLocation().equals(doorLoc)) {
                if (gameManager.isDoorOpened()) {
                    player.sendMessage(ChatColor.YELLOW + "メインドアはすでに開いてるよ！");
                    return;
                }
                // アイテム1個消費
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().removeItem(item);
                }
                gameManager.openDoor();
                player.sendMessage(ChatColor.GREEN + "メインドアを開けたよ！");
                return;
            }

            // 出口ドアチェック
            Location exitDoorLoc = configManager.getExitDoorLocation();
            if (exitDoorLoc != null && clickedBlock.getLocation().equals(exitDoorLoc)) {
                if (gameManager.isExitDoorOpened()) {
                    player.sendMessage(ChatColor.YELLOW + "出口ドアはすでに開いてるよ！");
                    return;
                }
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().removeItem(item);
                }
                gameManager.openExitDoor(player);
                return;
            }
            player.sendMessage(ChatColor.RED + "ここでは鍵を使えないよ。ドアに向かって使ってね！");
        }
        // ドローンアイテム
        else if (itemManager.isDroneControllerItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInPlayerTeam(player)) {
                player.sendMessage(ChatColor.RED + "プレイヤー陣営のみ使用可能だよ！");
                return;
            }
            // ドローン起動
            itemManager.startDroneMode(player);
        }
        // 陣営選択本
        else if (itemManager.isTeamSelectBook(item)) {
            event.setCancelled(true);
            openTeamSelectionGUI(player);
        }
        // ゲームスタート本
        else if (itemManager.isGameStartBook(item)) {
            event.setCancelled(true);
            openGameStartGUI(player);
        }
        // チェスト設定本
        else if (itemManager.isChestCountBook(item)) {
            event.setCancelled(true);
            openChestCountGUI(player);
        }
        // ゲーム時間設定本
        else if (itemManager.isGameTimeBook(item)) {
            event.setCancelled(true);
            openGameTimeGUI(player);
        }
        // 鬼叉「突進」
        else if (itemManager.isKishaDashItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.KISHA) {
                player.sendMessage(ChatColor.RED + "鬼叉のみ使用可能だよ！");
                return;
            }

            UUID pid = player.getUniqueId();
            if (itemManager.isKishaDashOnCooldown(pid)) {
                int remain = itemManager.getKishaDashRemainingCooldown(pid);
                player.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
                return;
            }

            effectManager.startKishaDashEffect(player);
            itemManager.setKishaDashCooldown(pid);
        }
        // 鬼叉「停止」
        else if (itemManager.isKishaFreezeItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.KISHA) {
                player.sendMessage(ChatColor.RED + "鬼叉のみ使用可能だよ！");
                return;
            }

            UUID pid = player.getUniqueId();
            if (itemManager.isKishaFreezeOnCooldown(pid)) {
                int remain = itemManager.getKishaFreezeRemainingCooldown(pid);
                player.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
                return;
            }

            effectManager.startKishaFreezeEffect(player);
            itemManager.setKishaFreezeCooldown(pid);
        }
        // 鬼叉「金棒」
        else if (itemManager.isKishaGoldClubItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.KISHA) {
                player.sendMessage(ChatColor.RED + "鬼叉のみ使用可能だよ！");
                return;
            }

            UUID pid = player.getUniqueId();
            if (itemManager.isKishaGoldClubOnCooldown(pid)) {
                int remain = itemManager.getKishaGoldClubRemainingCooldown(pid);
                player.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
                return;
            }

            effectManager.startKishaGoldClubEffect(player);
            itemManager.setKishaGoldClubCooldown(pid);
        }
        // 闇叉「暗転」
        else if (itemManager.isAnshaDarknessItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.ANSHA) {
                player.sendMessage(ChatColor.RED + "闇叉のみ使用可能だよ！");
                return;
            }

            UUID pid = player.getUniqueId();
            if (itemManager.isAnshaDarknessOnCooldown(pid)) {
                int remain = itemManager.getAnshaDarknessRemainingCooldown(pid);
                player.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
                return;
            }

            effectManager.startAnshaDarknessEffect(player);
            itemManager.setAnshaDarknessCooldown(pid);
        }
        // 闇叉「転生」
        else if (itemManager.isAnshaTeleportItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.ANSHA) {
                player.sendMessage(ChatColor.RED + "闇叉のみ使用可能だよ！");
                return;
            }

            UUID pid = player.getUniqueId();
            if (itemManager.isAnshaTeleportOnCooldown(pid)) {
                int remain = itemManager.getAnshaTeleportRemainingCooldown(pid);
                player.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
                return;
            }

            // 闇叉「転生」使用時
            if (itemManager.hasTeleportMarker(pid)) {
                // マーカーに転送
                Location marker = itemManager.getTeleportMarker(pid);

                // 全プレイヤーに通知
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(ChatColor.LIGHT_PURPLE + "闇叉の転生！", ChatColor.DARK_PURPLE + "マーカーへテレポート...", 10, 30, 10);
                }

                player.teleport(marker);
                itemManager.clearTeleportMarker(pid);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "マーカーにテレポート！");
            } else {
                // マーカーセット
                itemManager.setTeleportMarker(pid, player.getLocation());
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "テレポートマーカーを設置した！");

                // 全プレイヤーに通知
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(ChatColor.LIGHT_PURPLE + "闇叉の転生！", ChatColor.DARK_PURPLE + "マーカー設置...", 10, 30, 10);
                }
            }

            itemManager.setAnshaTeleportCooldown(pid);
        }
        // 闇叉「逃亡不可」
        else if (itemManager.isAnshaEscapeDisableItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.ANSHA) {
                player.sendMessage(ChatColor.RED + "闇叉のみ使用可能だよ！");
                return;
            }

            UUID pid = player.getUniqueId();
            if (itemManager.isAnshaEscapeDisableUsed(pid)) {
                player.sendMessage(ChatColor.RED + "このアイテムは既に使用済みです");
                return;
            }

            // 120秒経過しているか確認
            if (gameManager.getGameStartTime() + 120 > (System.currentTimeMillis() / 1000)) {
                int remaining = (int)(gameManager.getGameStartTime() + 120 - (System.currentTimeMillis() / 1000));
                player.sendMessage(ChatColor.RED + "ゲーム開始から120秒後に使用可能です。残り" + remaining + "秒");
                return;
            }

            // 最も近いプレイヤーを探す
            Player nearestPlayer = null;
            double minDist = Double.MAX_VALUE;

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (teamManager.isPlayerInPlayerTeam(p) && p.getGameMode() != GameMode.SPECTATOR) {
                    double dist = p.getLocation().distance(player.getLocation());
                    if (dist < minDist) {
                        minDist = dist;
                        nearestPlayer = p;
                    }
                }
            }

            if (nearestPlayer == null) {
                player.sendMessage(ChatColor.RED + "近くにプレイヤーがいません");
                return;
            }

            // 闇叉「逃亡不可」使用時
            // プレイヤーの後ろにワープ
            Location playerLoc = nearestPlayer.getLocation();
            Vector direction = playerLoc.getDirection().multiply(-1).normalize();
            Location teleportLoc = playerLoc.clone().add(direction.multiply(1.5));

            // 全プレイヤーに通知
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(ChatColor.RED + "闇叉の逃亡不可！", ChatColor.DARK_RED + nearestPlayer.getName() + "の背後に出現...", 10, 30, 10);
            }

            player.teleport(teleportLoc);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            player.sendMessage(ChatColor.RED + nearestPlayer.getName() + "の背後に現れた！");
            nearestPlayer.sendMessage(ChatColor.RED + "背後に闇叉が現れた！");

            // 2秒間の行動不能
            player.addPotionEffect(new PotionEffect(PotionEffectType.getByName("SLOW"), 40, 255, false, false));

            // 使用済みフラグを立てる
            itemManager.setAnshaEscapeDisableUsed(pid);
        }
        // 月牙「月切り」
        else if (itemManager.isGetsugaMoonSlashItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.GETSUGA) {
                player.sendMessage(ChatColor.RED + "月牙のみ使用可能だよ！");
                return;
            }


            UUID pid = player.getUniqueId();
            // 使用回数チェック
            int usedCount = itemManager.getGetsugaMoonSlashCount(pid);
            if (usedCount >= 3) {
                player.sendMessage(ChatColor.RED + "月切りは既に3回使用済みです");
                return;
            }

            // 30秒経過しているか確認
            if (gameManager.getGameStartTime() + 30 > (System.currentTimeMillis() / 1000)) {
                int remaining = (int)(gameManager.getGameStartTime() + 30 - (System.currentTimeMillis() / 1000));
                player.sendMessage(ChatColor.RED + "ゲーム開始から30秒後に使用可能です。残り" + remaining + "秒");
                return;
            }

            // 次に殴った時に発動する効果を設定
            player.sendMessage(ChatColor.BLUE + "月切りが準備完了！次にプレイヤーを殴るとカウントチェストを1つ減らします");
            gameManager.setNextAttackMoonSlash(player);
        }
        // 月牙「三日月」
        else if (itemManager.isGetsugaCrescentItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.GETSUGA) {
                player.sendMessage(ChatColor.RED + "月牙のみ使用可能だよ！");
                return;
            }
            // プレイヤーのいるワールド内の全プレイヤーに再生
            for (Player p : player.getWorld().getPlayers()) {
                p.playSound(p.getLocation(), MIKADUKI_SOUND, 1.0f, 1.0f);
            }

            UUID pid = player.getUniqueId();
            if (itemManager.isGetsugaCrescentOnCooldown(pid)) {
                int remain = itemManager.getGetsugaCrescentRemainingCooldown(pid);
                player.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
                return;
            }

            // 全プレイヤーをランダムなチェスト付近にワープ
            gameManager.teleportAllPlayersToRandomChests();
            player.sendMessage(ChatColor.AQUA + "三日月発動！全プレイヤーをランダムなチェスト付近にテレポートさせた");

            itemManager.setGetsugaCrescentCooldown(pid);
        }
        // 月牙「殺月」
        else if (itemManager.isGetsugaKillMoonItem(item)) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }
            if (!teamManager.isPlayerInOniTeam(player) ||
                    teamManager.getPlayerOniType(player) != OniType.GETSUGA) {
                player.sendMessage(ChatColor.RED + "月牙のみ使用可能だよ！");
                return;
            }

            UUID pid = player.getUniqueId();
            if (itemManager.isGetsugaKillMoonOnCooldown(pid)) {
                int remain = itemManager.getGetsugaKillMoonRemainingCooldown(pid);
                player.sendMessage(ChatColor.RED + "クールダウン中: 残り" + remain + "秒");
                return;
            }
            for (Player p : player.getWorld().getPlayers()) {
                p.playSound(p.getLocation(), SATSUKI_SOUND, 1.0f, 1.0f);
            }

            effectManager.startGetsugaKillMoonEffect(player);
            itemManager.setGetsugaKillMoonCooldown(pid);
        }
    }

    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isGameRunning()) return;
        // 鬼は常時スプリント不可
        if (teamManager.isPlayerInOniTeam(player)) {
            event.setCancelled(true);
        }
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
                teamManager.addPlayerToPlayerTeam(player);
                player.sendMessage(ChatColor.BLUE + "プレイヤー陣営に配属されたよ！");
                player.closeInventory();
                gameManager.updateScoreboard();
            } else             if ("鬼陣営".equals(dispName)) {
                teamManager.addPlayerToOniTeam(player);
                player.sendMessage(ChatColor.RED + "鬼陣営に配属されたよ！鬼タイプを選択してください");
                player.closeInventory();
                openOniTypeSelectionGUI(player);
                gameManager.updateScoreboard();
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
                gameManager.startGame(player);
            } else if ("§c鬼スタート".equals(dispName)) {
                player.closeInventory();
                gameManager.oniStartGame(player);
            } else if ("§dランダム鬼スタート".equals(dispName)) {
                player.closeInventory();
                randomOniStart(player);
            }
        }
        // チェスト数設定GUI
        else if (event.getView().getTitle().equals("カウントチェスト設定")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            String dispName = event.getCurrentItem().getItemMeta().getDisplayName();
            if ("§a1個（簡単モード）".equals(dispName)) {
                configManager.setRequiredCountChests(1);
                sendConfigMessage(player, ChatColor.GREEN + "必要カウントチェスト数を1個にしたよ！");
                player.closeInventory();
            } else if ("§e3個（標準モード）".equals(dispName)) {
                configManager.setRequiredCountChests(3);
                sendConfigMessage(player, ChatColor.YELLOW + "必要カウントチェスト数を3個にしたよ！");
                player.closeInventory();
            } else if ("§c5個（難しいモード）".equals(dispName)) {
                configManager.setRequiredCountChests(5);
                sendConfigMessage(player, ChatColor.RED + "必要カウントチェスト数を5個にしたよ！");
                player.closeInventory();
            } else if ("§dカスタム設定".equals(dispName)) {
                player.closeInventory();
                sendConfigMessage(player, ChatColor.LIGHT_PURPLE + "チャットで数値を入力してね（1～" +
                        Math.max(1, configManager.getTotalCountChests()) + "）");
                playerInputModes.put(player.getUniqueId(), InputMode.CHEST_COUNT);
            }
        }
        // ゲーム時間設定GUI
        else if (event.getView().getTitle().equals("ゲーム時間設定")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            String dispName = event.getCurrentItem().getItemMeta().getDisplayName();
            if ("§a180秒（短時間モード）".equals(dispName)) {
                gameManager.setGameTime(180);
                sendConfigMessage(player, ChatColor.GREEN + "ゲーム時間を180秒にしたよ！");
                player.closeInventory();
            } else if ("§e300秒（標準モード）".equals(dispName)) {
                gameManager.setGameTime(300);
                sendConfigMessage(player, ChatColor.YELLOW + "ゲーム時間を300秒にしたよ！");
                player.closeInventory();
            } else if ("§c600秒（長時間モード）".equals(dispName)) {
                gameManager.setGameTime(600);
                sendConfigMessage(player, ChatColor.RED + "ゲーム時間を600秒にしたよ！");
                player.closeInventory();
            } else if ("§dカスタム設定".equals(dispName)) {
                player.closeInventory();
                sendConfigMessage(player, ChatColor.LIGHT_PURPLE + "チャットでゲーム時間（秒）を入力してね（最低60秒）");
                playerInputModes.put(player.getUniqueId(), InputMode.GAME_TIME);
            }
        }
        // 鬼タイプ選択GUI
        else if (event.getView().getTitle().equals("鬼タイプ選択")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            if (!teamManager.isPlayerInOniTeam(player)) {
                player.sendMessage(ChatColor.RED + "鬼チームのみ選択できます");
                player.closeInventory();
                return;
            }

            OniType selectedType = null;
            String typeName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());

            try {
                selectedType = OniType.valueOf(typeName.toUpperCase()); // 日本語名を英語名に変換
            } catch (Exception e) {
                // 名前変換のフォールバック
                if (typeName.equals("夜叉")) selectedType = OniType.YASHA;
                else if (typeName.equals("鬼叉")) selectedType = OniType.KISHA;
                else if (typeName.equals("闇叉")) selectedType = OniType.ANSHA;
                else if (typeName.equals("月牙")) selectedType = OniType.GETSUGA;
            }

            if (selectedType != null) {
                teamManager.setPlayerOniType(player, selectedType);
                player.sendMessage(ChatColor.GREEN + selectedType.getDisplayName() + "を選択しました！ゲーム開始時にアイテムが配布されます。");
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;
        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();

        // 同じプレイヤーチーム間のPvPは無効
        if (teamManager.isPlayerInPlayerTeam(damager) && teamManager.isPlayerInPlayerTeam(target)) {
            event.setCancelled(true);
            return;
        }

        // 鬼 -> プレイヤーの攻撃処理
        if (gameManager.isGameRunning() &&
                teamManager.isPlayerInOniTeam(damager) &&
                teamManager.isPlayerInPlayerTeam(target)) {

            OniType oniType = teamManager.getPlayerOniType(damager);

            event.setCancelled(true);

            // 月牙の月切り発動チェック
            if (oniType == OniType.GETSUGA && gameManager.isNextAttackMoonSlash(damager)) {
                gameManager.executeGetsugaMoonSlash(damager, target);
                return;
            }

            // 金棒効果中なら一撃必殺
            if (effectManager.isKishaGoldClubActive(damager)) {
                damager.sendMessage(ChatColor.YELLOW + "金棒の力で" + target.getName() + "を一撃で倒した！");
                target.sendMessage(ChatColor.RED + "鬼叉の金棒にやられた…");
                target.damage(1000);
                return;
            }

            // 月牙の殺月効果中で誰かを殴ったら効果解除
            if (effectManager.isKillMoonActive() && oniType == OniType.GETSUGA) {
                effectManager.stopGetsugaKillMoonEffect();
                damager.sendMessage(ChatColor.RED + "殺月の効果が解除された！");
            }

            switch (oniType) {
                case YASHA:
                    // 夜叉は一撃で倒せる
                    damager.sendMessage(ChatColor.RED + target.getName() + "を一撃で倒した！");
                    target.sendMessage(ChatColor.RED + "夜叉にやられた…");
                    target.damage(1000);
                    break;

                case KISHA:
                    // 鬼叉は2回攻撃で倒せる
                    int hits = teamManager.getPlayerHitCount(target);
                    hits++;
                    teamManager.incrementPlayerHitCount(target);

                    if (hits >= 2) {
                        damager.sendMessage(ChatColor.GOLD + target.getName() + "を倒した！(" + hits + "ヒット)");
                        target.sendMessage(ChatColor.RED + "鬼叉にやられた…");
                        target.damage(1000);
                        teamManager.resetPlayerHitCount(target);
                    } else {
                        damager.sendMessage(ChatColor.GOLD + target.getName() + "に攻撃！(" + hits + "ヒット)");
                        target.sendMessage(ChatColor.RED + "鬼叉に攻撃された！残りHP:" + (2-hits));
                        // ノックバック効果
                        org.bukkit.util.Vector knockback = damager.getLocation().getDirection().multiply(1.5);
                        target.setVelocity(knockback);
                    }
                    break;

                case ANSHA:
                    // 闇叉は一撃で倒せる
                    damager.sendMessage(ChatColor.DARK_PURPLE + target.getName() + "を一撃で倒した！");
                    target.sendMessage(ChatColor.RED + "闇叉にやられた…");
                    target.damage(1000);
                    break;

                case GETSUGA:
                    // 月牙は5回攻撃で倒せる
                    int gHits = teamManager.getPlayerHitCount(target);
                    gHits++;
                    teamManager.incrementPlayerHitCount(target);

                    if (gHits >= 5) {
                        damager.sendMessage(ChatColor.BLUE + target.getName() + "を倒した！(" + gHits + "ヒット)");
                        target.sendMessage(ChatColor.RED + "月牙にやられた…");
                        target.damage(1000);
                        teamManager.resetPlayerHitCount(target);
                    } else {
                        damager.sendMessage(ChatColor.BLUE + target.getName() + "に攻撃！(" + gHits + "ヒット)");
                        target.sendMessage(ChatColor.RED + "月牙に攻撃された！残りHP:" + (5-gHits));
                        // ノックバック効果
                        org.bukkit.util.Vector kb = damager.getLocation().getDirection().multiply(0.8);
                        target.setVelocity(kb);
                    }
                    break;
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        gameManager.checkPlayerEscape(player);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (playerInputModes.containsKey(uuid)) {
            InputMode mode = playerInputModes.get(uuid);
            String message = event.getMessage();

            if (mode == InputMode.CHEST_COUNT) {
                event.setCancelled(true);
                try {
                    int totalChests = configManager.getTotalCountChests();
                    int chestCount = Integer.parseInt(message);
                    int maxChests = Math.max(1, totalChests);

                    if (chestCount < 1) {
                        sendConfigMessage(player, ChatColor.RED + "最低1個以上が必要だよ！");
                        return;
                    }
                    if (chestCount > maxChests) {
                        sendConfigMessage(player, ChatColor.RED + "登録チェスト数(" + totalChests + ")より多い値は設定できないよ！");
                        return;
                    }
                    Bukkit.getScheduler().runTask(this, () -> {
                        configManager.setRequiredCountChests(chestCount);
                        sendConfigMessage(player, ChatColor.GREEN + "必要なカウントチェスト数を" + chestCount + "個にしたよ！");
                        playerInputModes.remove(uuid);
                    });
                } catch (NumberFormatException e) {
                    sendConfigMessage(player, ChatColor.RED + "数字を入力してね。");
                }
            }
            else if (mode == InputMode.GAME_TIME) {
                event.setCancelled(true);
                try {
                    int gameTime = Integer.parseInt(message);
                    if (gameTime < 60) {
                        sendConfigMessage(player, ChatColor.RED + "最低60秒以上にしてね！");
                        return;
                    }
                    Bukkit.getScheduler().runTask(this, () -> {
                        gameManager.setGameTime(gameTime);
                        sendConfigMessage(player, ChatColor.GREEN + "ゲーム時間を" + gameTime + "秒にしたよ！");
                        playerInputModes.remove(uuid);
                    });
                } catch (NumberFormatException e) {
                    sendConfigMessage(player, ChatColor.RED + "数字を入力してね。");
                }
            }
        }
    }

    // GUI呼び出し系
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

    private void openGameStartGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "ゲームスタート");

        // 通常スタート
        ItemStack normalStart = new ItemStack(Material.GREEN_WOOL);
        ItemMeta normalMeta = normalStart.getItemMeta();
        normalMeta.setDisplayName("§2通常スタート");
        normalMeta.setLore(Collections.singletonList("全員が陣営選択した状態で開始"));
        normalStart.setItemMeta(normalMeta);
        inv.setItem(2, normalStart);

        // 鬼スタート
        ItemStack oniStart = new ItemStack(Material.RED_WOOL);
        ItemMeta oniMeta = oniStart.getItemMeta();
        oniMeta.setDisplayName("§c鬼スタート");
        oniMeta.setLore(Arrays.asList("クリックした人が鬼になる", "他は自動的にプレイヤー陣営"));
        oniStart.setItemMeta(oniMeta);
        inv.setItem(4, oniStart);

        // ランダム鬼スタート
        ItemStack randomOniStart = new ItemStack(Material.PURPLE_WOOL);
        ItemMeta randomOniMeta = randomOniStart.getItemMeta();
        randomOniMeta.setDisplayName("§dランダム鬼スタート");
        randomOniMeta.setLore(Arrays.asList("ランダムで1人を鬼に選ぶ", "陣営選択なしで即開始"));
        randomOniStart.setItemMeta(randomOniMeta);
        inv.setItem(6, randomOniStart);

        player.openInventory(inv);
    }

    private void openChestCountGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "カウントチェスト設定");

        // 現在の設定
        int totalChests = configManager.getTotalCountChests();
        int currentRequired = configManager.getRequiredCountChests();

        // 1個
        ItemStack easyMode = new ItemStack(Material.LIME_WOOL);
        ItemMeta easyMeta = easyMode.getItemMeta();
        easyMeta.setDisplayName("§a1個（簡単モード）");
        easyMeta.setLore(Collections.singletonList("必要なカウントチェスト数を1個に"));
        easyMode.setItemMeta(easyMeta);
        inv.setItem(1, easyMode);

        // 3個
        ItemStack normalMode = new ItemStack(Material.YELLOW_WOOL);
        ItemMeta normalMeta = normalMode.getItemMeta();
        normalMeta.setDisplayName("§e3個（標準モード）");
        normalMeta.setLore(Collections.singletonList("必要なカウントチェスト数を3個に"));
        normalMode.setItemMeta(normalMeta);
        inv.setItem(3, normalMode);

        // 5個
        ItemStack hardMode = new ItemStack(Material.RED_WOOL);
        ItemMeta hardMeta = hardMode.getItemMeta();
        hardMeta.setDisplayName("§c5個（難しいモード）");
        hardMeta.setLore(Collections.singletonList("必要なカウントチェスト数を5個に"));
        hardMode.setItemMeta(hardMeta);
        inv.setItem(5, hardMode);

        // カスタム
        ItemStack customMode = new ItemStack(Material.PURPLE_WOOL);
        ItemMeta customMeta = customMode.getItemMeta();
        customMeta.setDisplayName("§dカスタム設定");
        customMeta.setLore(Arrays.asList("チャットで数値入力", "1～" + totalChests + "まで設定可"));
        customMode.setItemMeta(customMeta);
        inv.setItem(7, customMode);

        // 現在表示
        ItemStack currentSetting = new ItemStack(Material.PAPER);
        ItemMeta currentMeta = currentSetting.getItemMeta();
        currentMeta.setDisplayName("§f現在の設定");
        currentMeta.setLore(Arrays.asList(
                "必要: " + currentRequired + "個/人",
                "登録: " + totalChests + "個"
        ));
        currentSetting.setItemMeta(currentMeta);
        inv.setItem(4, currentSetting);

        player.openInventory(inv);
    }

    private void openGameTimeGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "ゲーム時間設定");

        int currentTime = gameManager.getRemainingTime();

        // 180秒
        ItemStack shortMode = new ItemStack(Material.LIME_WOOL);
        ItemMeta shortMeta = shortMode.getItemMeta();
        shortMeta.setDisplayName("§a180秒（短時間モード）");
        shortMeta.setLore(Collections.singletonList("ゲーム時間を180秒に"));
        shortMode.setItemMeta(shortMeta);
        inv.setItem(1, shortMode);

        // 300秒
        ItemStack normalMode = new ItemStack(Material.YELLOW_WOOL);
        ItemMeta normalMeta = normalMode.getItemMeta();
        normalMeta.setDisplayName("§e300秒（標準モード）");
        normalMeta.setLore(Collections.singletonList("ゲーム時間を300秒に"));
        normalMode.setItemMeta(normalMeta);
        inv.setItem(3, normalMode);

        // 600秒
        ItemStack longMode = new ItemStack(Material.RED_WOOL);
        ItemMeta longMeta = longMode.getItemMeta();
        longMeta.setDisplayName("§c600秒（長時間モード）");
        longMeta.setLore(Collections.singletonList("ゲーム時間を600秒に"));
        longMode.setItemMeta(longMeta);
        inv.setItem(5, longMode);

        // カスタム
        ItemStack customMode = new ItemStack(Material.PURPLE_WOOL);
        ItemMeta customMeta = customMode.getItemMeta();
        customMeta.setDisplayName("§dカスタム設定");
        customMeta.setLore(Arrays.asList("チャットで入力", "最低60秒"));
        customMode.setItemMeta(customMeta);
        inv.setItem(7, customMode);

        // 現在の設定
        ItemStack currentSetting = new ItemStack(Material.PAPER);
        ItemMeta currentMeta = currentSetting.getItemMeta();
        currentMeta.setDisplayName("§f現在の設定");
        currentMeta.setLore(Collections.singletonList("ゲーム時間: " + currentTime + "秒"));
        currentSetting.setItemMeta(currentMeta);
        inv.setItem(4, currentSetting);

        player.openInventory(inv);
    }

    // 鬼タイプ選択GUI
    public void openOniTypeSelectionGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "鬼タイプ選択");

        // 夜叉
        ItemStack yashaItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta yashaMeta = yashaItem.getItemMeta();
        yashaMeta.setDisplayName(ChatColor.RED + "夜叉");
        yashaMeta.setLore(Arrays.asList(
                "通常の鬼。プレイヤーに暗闇効果を与える。",
                "一撃で殺せる。攻撃アイテム：夜叉、チェスト探知、ワープ"
        ));
        yashaItem.setItemMeta(yashaMeta);
        inv.setItem(1, yashaItem);

        // 鬼叉
        ItemStack kishaItem = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta kishaMeta = kishaItem.getItemMeta();
        kishaMeta.setDisplayName(ChatColor.GOLD + "鬼叉");
        kishaMeta.setLore(Arrays.asList(
                "最高速度で歩く鬼。2回攻撃で1キル。",
                "攻撃アイテム：突進、停止、金棒"
        ));
        kishaItem.setItemMeta(kishaMeta);
        inv.setItem(3, kishaItem);

        // 闇叉
        ItemStack anshaItem = new ItemStack(Material.COAL);
        ItemMeta anshaMeta = anshaItem.getItemMeta();
        anshaMeta.setDisplayName(ChatColor.DARK_PURPLE + "闇叉");
        anshaMeta.setLore(Arrays.asList(
                "常に暗闇効果を持つが高速な鬼。",
                "一撃で殺せる。攻撃アイテム：暗転、転生、逃亡不可"
        ));
        anshaItem.setItemMeta(anshaMeta);
        inv.setItem(5, anshaItem);

        // 月牙
        ItemStack getsugaItem = new ItemStack(Material.IRON_SWORD);
        ItemMeta getsugaMeta = getsugaItem.getItemMeta();
        getsugaMeta.setDisplayName(ChatColor.BLUE + "月牙");
        getsugaMeta.setLore(Arrays.asList(
                "チェスト探知能力を持つ鬼。5回攻撃で1キル。",
                "高速で移動できる。攻撃アイテム：月切り、三日月、殺月"
        ));
        getsugaItem.setItemMeta(getsugaMeta);
        inv.setItem(7, getsugaItem);

        player.openInventory(inv);
    }

    // 鬼タイプに合わせた速度適用
    private void applyOniTypeSpeed(Player player, OniType type) {
        // 速度効果を一旦解除
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.getByName("SLOW"));

        int speedLevel = 0;
        switch (type) {
            case KISHA:
                speedLevel = 2; // 最高速度
                break;
            case ANSHA:
                speedLevel = 1; // 1.2倍速
                break;
            case GETSUGA:
                speedLevel = 3; // 1.5倍速
                break;
            default:
                return; // YASHA は標準速度
        }

        if (speedLevel > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, speedLevel, false, false));
        }
    }

    /**
     * ランダム鬼スタート
     */
    private void randomOniStart(Player player) {
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (allPlayers.size() < 2) {
            sendConfigMessage(player, ChatColor.RED + "2人以上が必要だよ！");
            return;
        }
        Random random = new Random();
        Player oniPlayer = allPlayers.get(random.nextInt(allPlayers.size()));
        Bukkit.broadcastMessage(ChatColor.GOLD + "ランダムで " + oniPlayer.getName() + " が鬼に選ばれた！");
        gameManager.oniStartGame(oniPlayer);
    }

    // Helper method for GameManager to update scoreboard
    public void updateScoreboard() {
        if (gameManager != null) {
            gameManager.updateScoreboard();
        }
    }

    // Helper method to check if game is running
    public boolean isGameRunning() {
        return gameManager != null && gameManager.isGameRunning();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }
}