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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class OniGo extends JavaPlugin implements CommandExecutor, Listener {

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
                    player.sendMessage(ChatColor.GREEN + "夜叉効果を終了したよ！");
                }
                break;
            case "end":
                effectManager.stopYashaEffect();
                player.sendMessage(ChatColor.GREEN + "夜叉効果を終了したよ！");
                break;
            case "getcmditem":
                itemManager.giveAllGameItems(player);
                break;
            case "re":
                // Reset plugin
                if (effectManager.isYashaActive()) {
                    effectManager.stopYashaEffect();
                }
                gameManager.resetGame();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    effectManager.clearAllPotionEffects(p);
                    p.stopSound(EffectManager.ONISONG1_SOUND);
                    p.stopSound(EffectManager.ONISONG2_SOUND);
                }
                Bukkit.getPluginManager().disablePlugin(this);
                Bukkit.getPluginManager().enablePlugin(this);
                break;
            case "start":
                gameManager.startGame(player);
                break;
            case "stop":
                gameManager.stopGame(player);
                break;
            case "onistart":
                gameManager.oniStartGame(player);
                break;
            case "set":
                handleSetCommand(player, args);
                break;
            case "gamegive":
                itemManager.distributeTeamSelectionBooks();
                itemManager.giveGameStartBook("minamottooooooooo");
                itemManager.giveChestCountBook("minamottooooooooo");
                itemManager.giveGameTimeBook("minamottooooooooo");
                sendConfigMessage(player, ChatColor.GREEN + "陣営選択本、ゲームスタート本、チェスト設定本、時間設定本をminamottoooooooooに配布しました！");
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
            sendConfigMessage(player, ChatColor.RED + "使い方: /set chest <名前>  または  /set countchest <名前>  または  /set door  または  /set exitdoor");
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("chest")) {
            if (args.length < 2) {
                sendConfigMessage(player, ChatColor.RED + "チェストの名前を指定してね: /set chest <名前>");
                return;
            }

            // Check if player is looking at a chest
            org.bukkit.block.Block block = player.getTargetBlockExact(5);
            if (block == null || !(block.getState() instanceof org.bukkit.block.Chest)) {
                sendConfigMessage(player, ChatColor.RED + "近くにチェストがないか、ターゲットしていないよ！");
                return;
            }

            String chestName = args[1];
            configManager.registerChest(chestName, block.getLocation());
            sendConfigMessage(player, ChatColor.GREEN + "チェスト「" + chestName + "」を登録したよ！");
        }
        else if (sub.equals("countchest")) {
            if (args.length < 2) {
                sendConfigMessage(player, ChatColor.RED + "カウントチェストの名前を指定してね: /set countchest <名前>");
                return;
            }

            // Check if player is looking at a chest
            org.bukkit.block.Block block = player.getTargetBlockExact(5);
            if (block == null || !(block.getState() instanceof org.bukkit.block.Chest)) {
                sendConfigMessage(player, ChatColor.RED + "近くにチェストがないか、ターゲットしていないよ！");
                return;
            }

            String chestName = args[1];
            configManager.registerCountChest(chestName, block.getLocation());
            sendConfigMessage(player, ChatColor.GOLD + "カウントチェスト「" + chestName + "」を登録したよ！");
        }
        else if (sub.equals("door")) {
            // Check if player is looking at a door
            org.bukkit.block.Block block = player.getTargetBlockExact(5);
            if (block == null || (!block.getType().toString().contains("DOOR"))) {
                sendConfigMessage(player, ChatColor.RED + "近くにドアがないか、ターゲットしていないよ！");
                return;
            }

            configManager.registerDoor(block.getLocation());
            sendConfigMessage(player, ChatColor.GREEN + "メインドアを登録したよ！");
        }
        else if (sub.equals("exitdoor")) {
            // Check if player is looking at a door
            org.bukkit.block.Block block = player.getTargetBlockExact(5);
            if (block == null || (!block.getType().toString().contains("DOOR"))) {
                sendConfigMessage(player, ChatColor.RED + "近くにドアがないか、ターゲットしていないよ！");
                return;
            }

            configManager.registerExitDoor(block.getLocation());
            sendConfigMessage(player, ChatColor.GREEN + "出口ドアを登録したよ！");
        }
        else if (sub.equals("setreq")) {
            if (args.length < 2) {
                sendConfigMessage(player, ChatColor.RED + "必要なカウントチェスト数を指定してね: /set setreq <数>");
                return;
            }

            try {
                int requiredChests = Integer.parseInt(args[1]);
                if (requiredChests < 1) {
                    sendConfigMessage(player, ChatColor.RED + "1以上の値を指定してね！");
                    return;
                }

                configManager.setRequiredCountChests(requiredChests);
                sendConfigMessage(player, ChatColor.GREEN + "鍵入手に必要なカウントチェスト数を" + requiredChests + "に設定したよ！");
            } catch (NumberFormatException e) {
                sendConfigMessage(player, ChatColor.RED + "数値を指定してね: /set setreq <数>");
            }
        }
        else {
            sendConfigMessage(player, ChatColor.RED + "使い方: /set chest <名前>  または  /set countchest <名前>  または  /set door  または  /set exitdoor");
        }
    }

    /**
     * Send config messages only to minamottooooooooo
     */
    private void sendConfigMessage(Player sender, String message) {
        Player target = Bukkit.getPlayerExact("minamottooooooooo");
        if (target != null && target.isOnline()) {
            target.sendMessage(message);
        } else if (sender != null && sender.getName().equals("minamottooooooooo")) {
            sender.sendMessage(message);
        }
    }

    // =============================
    // EVENT HANDLERS
    // =============================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.teleport(configManager.getInitialSpawnLocation());
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if (teamManager.isPlayerInOniTeam(p)) {
            event.setFoodLevel(2);  // Keep oni team members hungry
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

        // Prevent oni from opening chests
        if (teamManager.isPlayerInOniTeam(player)) {
            if (event.getInventory().getLocation() != null &&
                    event.getInventory().getLocation().getBlock().getState() instanceof org.bukkit.block.Chest) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "鬼はチェストを開けられません！");
                return;
            }
        }

        // Check if opened inventory is a chest
        if (event.getInventory().getLocation() == null) return;
        Location loc = event.getInventory().getLocation();

        // Check if the chest is a regular chest
        String chestName = configManager.getChestNameAtLocation(loc);
        if (chestName != null) {
            gameManager.handleChestOpened(chestName, player);
        }

        // Check if the chest is a count chest
        String countChestName = configManager.getCountChestNameAtLocation(loc);
        if (countChestName != null) {
            gameManager.handleCountChestOpened(countChestName, player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Player player = event.getPlayer();

        // Handle Yasha item (Night Demon)
        if (itemManager.isYashaItem(item)) {
            event.setCancelled(true);
            if (!effectManager.isYashaActive()) {
                effectManager.startYashaEffect(player);
            } else {
                effectManager.stopYashaEffect();
                player.sendMessage(ChatColor.GREEN + "夜叉効果を終了したよ！");
            }
        }
        // Handle hiding orb (Kakure Dama)
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
                // Cancel invisibility
                effectManager.stopKakureDamaEffect(player);
                player.sendMessage(ChatColor.GREEN + "透明解除したよ！");
            } else {
                // Start invisibility
                effectManager.startKakureDamaEffect(player);
            }
        }
        // Handle chest detector (compass)
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
        // Handle chest teleporter (pearl)
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
        // Handle player escape item
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
        // Handle exit key item
        else if (itemManager.isExitKeyItem(item) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (!gameManager.isGameRunning()) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていないよ！");
                return;
            }

            // Check if the clicked block is a door
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null) return;

            // Check if it's the main door
            Location doorLoc = configManager.getDoorLocation();
            if (doorLoc != null && clickedBlock.getLocation().equals(doorLoc)) {
                // Check if door is already open
                if (gameManager.isDoorOpened()) {
                    player.sendMessage(ChatColor.YELLOW + "メインドアはすでに開いているよ！");
                    return;
                }

                // Remove one key from inventory
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().removeItem(item);
                }

                // Open the main door
                gameManager.openDoor();
                player.sendMessage(ChatColor.GREEN + "鍵を使ってメインドアを開いたよ！");
                return;
            }

            // Check if it's the exit door
            Location exitDoorLoc = configManager.getExitDoorLocation();
            if (exitDoorLoc != null && clickedBlock.getLocation().equals(exitDoorLoc)) {
                // Check if door is already open
                if (gameManager.isExitDoorOpened()) {
                    player.sendMessage(ChatColor.YELLOW + "出口ドアはすでに開いているよ！");
                    return;
                }

                // Remove one key from inventory
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().removeItem(item);
                }

                // Open the exit door with the player reference
                gameManager.openExitDoor(player);
                return;
            }

            player.sendMessage(ChatColor.RED + "この場所では鍵を使えないよ。ドアのところで使おう！");
        }
        // Handle team selection book
        else if (itemManager.isTeamSelectBook(item)) {
            event.setCancelled(true);
            openTeamSelectionGUI(player);
        }
        // Handle game start book
        else if (itemManager.isGameStartBook(item)) {
            event.setCancelled(true);
            openGameStartGUI(player);
        }
        // Handle chest count book
        else if (itemManager.isChestCountBook(item)) {
            event.setCancelled(true);
            openChestCountGUI(player);
        }
        // Handle game time book
        else if (itemManager.isGameTimeBook(item)) {
            event.setCancelled(true);
            openGameTimeGUI(player);
        }
    }

    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isGameRunning()) return;

        // Oni team members can't sprint
        if (teamManager.isPlayerInOniTeam(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Team selection GUI
        if (event.getView().getTitle().equals("陣営選択")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            String dispName = event.getCurrentItem().getItemMeta().getDisplayName();

            if ("プレイヤー陣営".equals(dispName)) {
                teamManager.addPlayerToPlayerTeam(player);
                player.sendMessage(ChatColor.BLUE + "プレイヤー陣営に選択されたよ！");
                player.closeInventory();
                gameManager.updateScoreboard();
            } else if ("鬼陣営".equals(dispName)) {
                teamManager.addPlayerToOniTeam(player);
                player.sendMessage(ChatColor.RED + "鬼陣営に選択されたよ！");
                player.closeInventory();
                gameManager.updateScoreboard();
            }
        }
        // Game start GUI
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
        // Chest count GUI
        else if (event.getView().getTitle().equals("カウントチェスト設定")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            String dispName = event.getCurrentItem().getItemMeta().getDisplayName();

            if ("§a1個（簡単モード）".equals(dispName)) {
                configManager.setRequiredCountChests(1);
                sendConfigMessage(player, ChatColor.GREEN + "必要なカウントチェスト数を1個に設定したよ！");
                player.closeInventory();
            } else if ("§e3個（標準モード）".equals(dispName)) {
                configManager.setRequiredCountChests(3);
                sendConfigMessage(player, ChatColor.YELLOW + "必要なカウントチェスト数を3個に設定したよ！");
                player.closeInventory();
            } else if ("§c5個（難しいモード）".equals(dispName)) {
                configManager.setRequiredCountChests(5);
                sendConfigMessage(player, ChatColor.RED + "必要なカウントチェスト数を5個に設定したよ！");
                player.closeInventory();
            } else if ("§dカスタム設定".equals(dispName)) {
                player.closeInventory();
                sendConfigMessage(player, ChatColor.LIGHT_PURPLE + "チャットで必要なチェスト数を入力してください。(1-" +
                        Math.max(1, configManager.getTotalCountChests()) + ")");
                playerInputModes.put(player.getUniqueId(), InputMode.CHEST_COUNT);
            }
        }
        // Game time GUI
        else if (event.getView().getTitle().equals("ゲーム時間設定")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            String dispName = event.getCurrentItem().getItemMeta().getDisplayName();

            if ("§a180秒（短時間モード）".equals(dispName)) {
                gameManager.setGameTime(180);
                sendConfigMessage(player, ChatColor.GREEN + "ゲーム時間を180秒に設定したよ！");
                player.closeInventory();
            } else if ("§e300秒（標準モード）".equals(dispName)) {
                gameManager.setGameTime(300);
                sendConfigMessage(player, ChatColor.YELLOW + "ゲーム時間を300秒に設定したよ！");
                player.closeInventory();
            } else if ("§c600秒（長時間モード）".equals(dispName)) {
                gameManager.setGameTime(600);
                sendConfigMessage(player, ChatColor.RED + "ゲーム時間を600秒に設定したよ！");
                player.closeInventory();
            } else if ("§dカスタム設定".equals(dispName)) {
                player.closeInventory();
                sendConfigMessage(player, ChatColor.LIGHT_PURPLE + "チャットでゲーム時間（秒）を入力してください。(最低60秒)");
                playerInputModes.put(player.getUniqueId(), InputMode.GAME_TIME);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;
        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();

        // Cancel PvP between players on the same team
        if (teamManager.isPlayerInPlayerTeam(damager) && teamManager.isPlayerInPlayerTeam(target)) {
            event.setCancelled(true);
            return;
        }

        // Oni attacking player (one-hit kill)
        if (gameManager.isGameRunning() && teamManager.isPlayerInOniTeam(damager) && teamManager.isPlayerInPlayerTeam(target)) {
            event.setCancelled(true);  // Cancel normal damage
            damager.sendMessage(ChatColor.RED + target.getName() + "を一撃で倒した！");
            target.sendMessage(ChatColor.RED + "鬼に襲われて死亡した！");
            target.damage(1000);  // Essentially fatal damage
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

        // チャット入力モードチェック
        if (playerInputModes.containsKey(uuid)) {
            InputMode mode = playerInputModes.get(uuid);
            String message = event.getMessage();

            // カウントチェスト数設定モード
            if (mode == InputMode.CHEST_COUNT) {
                event.setCancelled(true);
                try {
                    int totalChests = configManager.getTotalCountChests();
                    int chestCount = Integer.parseInt(message);

                    // 最大値をチェスト総数に制限
                    int maxChests = Math.max(1, totalChests);

                    if (chestCount < 1) {
                        sendConfigMessage(player, ChatColor.RED + "最低1個のチェストが必要です。");
                        return;
                    }

                    if (chestCount > maxChests) {
                        sendConfigMessage(player, ChatColor.RED + "設定できる最大数は" + maxChests + "個です（登録チェスト数が上限）。");
                        return;
                    }

                    // メインスレッドで実行
                    Bukkit.getScheduler().runTask(this, () -> {
                        configManager.setRequiredCountChests(chestCount);
                        sendConfigMessage(player, ChatColor.GREEN + "必要なカウントチェスト数を" + chestCount + "個に設定したよ！");

                        // チェスト数が足りない場合の警告
                        if (chestCount > totalChests) {
                            sendConfigMessage(player, ChatColor.RED + "警告: 必要数(" + chestCount + "個)が登録済みチェスト数(" +
                                    totalChests + "個)より多くなっています。先に追加のチェストを登録してください！");
                        }

                        playerInputModes.remove(uuid);
                    });
                } catch (NumberFormatException e) {
                    sendConfigMessage(player, ChatColor.RED + "数字を入力してください。");
                }
            }
            // ゲーム時間設定モード
            else if (mode == InputMode.GAME_TIME) {
                event.setCancelled(true);
                try {
                    int gameTime = Integer.parseInt(message);

                    if (gameTime < 60) {
                        sendConfigMessage(player, ChatColor.RED + "最低60秒以上のゲーム時間が必要です。");
                        return;
                    }

                    // メインスレッドで実行
                    Bukkit.getScheduler().runTask(this, () -> {
                        gameManager.setGameTime(gameTime);
                        sendConfigMessage(player, ChatColor.GREEN + "ゲーム時間を" + gameTime + "秒に設定したよ！");
                        playerInputModes.remove(uuid);
                    });
                } catch (NumberFormatException e) {
                    sendConfigMessage(player, ChatColor.RED + "数字を入力してください。");
                }
            }
        }
    }

    // =============================
    // GUI HELPER METHODS
    // =============================

    /**
     * Open team selection GUI
     */
    private void openTeamSelectionGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "陣営選択");

        ItemStack playerItem = new ItemStack(Material.PAPER);
        org.bukkit.inventory.meta.ItemMeta pMeta = playerItem.getItemMeta();
        pMeta.setDisplayName("プレイヤー陣営");
        playerItem.setItemMeta(pMeta);
        inv.setItem(3, playerItem);

        ItemStack oniItem = new ItemStack(Material.PAPER);
        org.bukkit.inventory.meta.ItemMeta oMeta = oniItem.getItemMeta();
        oMeta.setDisplayName("鬼陣営");
        oniItem.setItemMeta(oMeta);
        inv.setItem(5, oniItem);

        player.openInventory(inv);
    }

    /**
     * Open game start GUI with random oni option
     */
    private void openGameStartGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "ゲームスタート");

        // 通常スタート
        ItemStack normalStart = new ItemStack(Material.GREEN_WOOL);
        ItemMeta normalMeta = normalStart.getItemMeta();
        normalMeta.setDisplayName("§2通常スタート");
        List<String> normalLore = new ArrayList<>();
        normalLore.add("§7すべてのプレイヤーが陣営選択済みの場合にゲームを開始します");
        normalMeta.setLore(normalLore);
        normalStart.setItemMeta(normalMeta);
        inv.setItem(2, normalStart);

        // 鬼スタート
        ItemStack oniStart = new ItemStack(Material.RED_WOOL);
        ItemMeta oniMeta = oniStart.getItemMeta();
        oniMeta.setDisplayName("§c鬼スタート");
        List<String> oniLore = new ArrayList<>();
        oniLore.add("§7あなたが鬼になります");
        oniLore.add("§7他のプレイヤーは自動的にプレイヤー陣営になります");
        oniMeta.setLore(oniLore);
        oniStart.setItemMeta(oniMeta);
        inv.setItem(4, oniStart);

        // ランダム鬼スタート
        ItemStack randomOniStart = new ItemStack(Material.PURPLE_WOOL);
        ItemMeta randomOniMeta = randomOniStart.getItemMeta();
        randomOniMeta.setDisplayName("§dランダム鬼スタート");
        List<String> randomOniLore = new ArrayList<>();
        randomOniLore.add("§7ランダムで1人を鬼に選びます");
        randomOniLore.add("§7陣営選択せずにゲームを開始できます");
        randomOniMeta.setLore(randomOniLore);
        randomOniStart.setItemMeta(randomOniMeta);
        inv.setItem(6, randomOniStart);

        player.openInventory(inv);
    }

    /**
     * Open chest count setting GUI
     */
    private void openChestCountGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "カウントチェスト設定");

        // 登録済みのチェスト総数
        int totalChests = configManager.getTotalCountChests();
        int currentRequired = configManager.getRequiredCountChests();

        // 簡単モード (1個)
        ItemStack easyMode = new ItemStack(Material.LIME_WOOL);
        ItemMeta easyMeta = easyMode.getItemMeta();
        easyMeta.setDisplayName("§a1個（簡単モード）");
        List<String> easyLore = new ArrayList<>();
        easyLore.add("§7必要なカウントチェスト数を1個に設定します");
        easyMeta.setLore(easyLore);
        easyMode.setItemMeta(easyMeta);
        inv.setItem(1, easyMode);

        // 標準モード (3個)
        ItemStack normalMode = new ItemStack(Material.YELLOW_WOOL);
        ItemMeta normalMeta = normalMode.getItemMeta();
        normalMeta.setDisplayName("§e3個（標準モード）");
        List<String> normalLore = new ArrayList<>();
        normalLore.add("§7必要なカウントチェスト数を3個に設定します");
        if (totalChests < 3) {
            normalLore.add("§c警告: 登録チェストが不足しています！");
        }
        normalMeta.setLore(normalLore);
        normalMode.setItemMeta(normalMeta);
        inv.setItem(3, normalMode);

        // 難しいモード (5個)
        ItemStack hardMode = new ItemStack(Material.RED_WOOL);
        ItemMeta hardMeta = hardMode.getItemMeta();
        hardMeta.setDisplayName("§c5個（難しいモード）");
        List<String> hardLore = new ArrayList<>();
        hardLore.add("§7必要なカウントチェスト数を5個に設定します");
        if (totalChests < 5) {
            hardLore.add("§c警告: 登録チェストが不足しています！");
        }
        hardMeta.setLore(hardLore);
        hardMode.setItemMeta(hardMeta);
        inv.setItem(5, hardMode);

        // カスタム設定
        ItemStack customMode = new ItemStack(Material.PURPLE_WOOL);
        ItemMeta customMeta = customMode.getItemMeta();
        customMeta.setDisplayName("§dカスタム設定");
        List<String> customLore = new ArrayList<>();
        customLore.add("§7チャットで必要なチェスト数を入力できます");
        customLore.add("§7設定可能範囲: 1～" + totalChests + "個");
        customMeta.setLore(customLore);
        customMode.setItemMeta(customMeta);
        inv.setItem(7, customMode);

        // 現在の設定表示
        ItemStack currentSetting = new ItemStack(Material.PAPER);
        ItemMeta currentMeta = currentSetting.getItemMeta();
        currentMeta.setDisplayName("§f現在の設定");
        List<String> currentLore = new ArrayList<>();
        currentLore.add("§7必要数: §e" + currentRequired + "§7個/プレイヤー");
        currentLore.add("§7登録済みチェスト: §e" + totalChests + "§7個");
        currentMeta.setLore(currentLore);
        currentSetting.setItemMeta(currentMeta);
        inv.setItem(4, currentSetting);

        player.openInventory(inv);
    }

    /**
     * Open game time setting GUI
     */
    private void openGameTimeGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "ゲーム時間設定");

        // 現在の設定を取得
        int currentTime = gameManager.getRemainingTime();

        // 短時間モード (180秒)
        ItemStack shortMode = new ItemStack(Material.LIME_WOOL);
        ItemMeta shortMeta = shortMode.getItemMeta();
        shortMeta.setDisplayName("§a180秒（短時間モード）");
        List<String> shortLore = new ArrayList<>();
        shortLore.add("§7ゲーム時間を180秒に設定します");
        shortMeta.setLore(shortLore);
        shortMode.setItemMeta(shortMeta);
        inv.setItem(1, shortMode);

        // 標準モード (300秒)
        ItemStack normalMode = new ItemStack(Material.YELLOW_WOOL);
        ItemMeta normalMeta = normalMode.getItemMeta();
        normalMeta.setDisplayName("§e300秒（標準モード）");
        List<String> normalLore = new ArrayList<>();
        normalLore.add("§7ゲーム時間を300秒に設定します");
        normalMeta.setLore(normalLore);
        normalMode.setItemMeta(normalMeta);
        inv.setItem(3, normalMode);

        // 長時間モード (600秒)
        ItemStack longMode = new ItemStack(Material.RED_WOOL);
        ItemMeta longMeta = longMode.getItemMeta();
        longMeta.setDisplayName("§c600秒（長時間モード）");
        List<String> longLore = new ArrayList<>();
        longLore.add("§7ゲーム時間を600秒に設定します");
        longMeta.setLore(longLore);
        longMode.setItemMeta(longMeta);
        inv.setItem(5, longMode);

        // カスタム設定
        ItemStack customMode = new ItemStack(Material.PURPLE_WOOL);
        ItemMeta customMeta = customMode.getItemMeta();
        customMeta.setDisplayName("§dカスタム設定");
        List<String> customLore = new ArrayList<>();
        customLore.add("§7チャットでゲーム時間を入力できます");
        customLore.add("§7最低60秒から設定可能");
        customMeta.setLore(customLore);
        customMode.setItemMeta(customMeta);
        inv.setItem(7, customMode);

        // 現在の設定表示
        ItemStack currentSetting = new ItemStack(Material.PAPER);
        ItemMeta currentMeta = currentSetting.getItemMeta();
        currentMeta.setDisplayName("§f現在の設定");
        List<String> currentLore = new ArrayList<>();
        currentLore.add("§7ゲーム時間: §e" + currentTime + "§7秒");
        currentMeta.setLore(currentLore);
        currentSetting.setItemMeta(currentMeta);
        inv.setItem(4, currentSetting);

        player.openInventory(inv);
    }

    /**
     * ランダムで鬼を1人選んで開始
     */
    private void randomOniStart(Player player) {
        // オンラインプレイヤー一覧取得
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        // プレイヤーが2人以上いるか確認
        if (allPlayers.size() < 2) {
            sendConfigMessage(player, ChatColor.RED + "ランダム鬼スタートには、少なくとも2人のプレイヤーが必要です！");
            return;
        }

        // ランダムな鬼を選択
        Random random = new Random();
        Player oniPlayer = allPlayers.get(random.nextInt(allPlayers.size()));

        // 鬼プレイヤーを通知
        Bukkit.broadcastMessage(ChatColor.GOLD + "ランダム抽選の結果、" + oniPlayer.getName() + "が鬼に選ばれました！");

        // ゲーム開始
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

    /**
     * ConfigManagerを取得するためのアクセサメソッド
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * GameManagerを取得するためのアクセサメソッド
     */
    public GameManager getGameManager() {
        return gameManager;
    }

    /**
     * ItemManagerを取得するためのアクセサメソッド
     */
    public ItemManager getItemManager() {
        return itemManager;
    }
}