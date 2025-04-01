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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

public final class OniGo extends JavaPlugin implements CommandExecutor, Listener {

    // Managers
    private ConfigManager configManager;
    private EffectManager effectManager;
    private GameManager gameManager;
    private ItemManager itemManager;
    private TeamManager teamManager;

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
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
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
                player.sendMessage(ChatColor.GREEN + "陣営選択本を全員に配布し、ゲームスタート本をminamottoooooooooに配布しました！");
                break;
        }
        return true;
    }

    /**
     * Handle the /set command to register chests and doors
     */
    private void handleSetCommand(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "使い方: /set chest <名前>  または  /set countchest <名前>  または  /set door  または  /set exitdoor");
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("chest")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "チェストの名前を指定してね: /set chest <名前>");
                return;
            }

            // Check if player is looking at a chest
            org.bukkit.block.Block block = player.getTargetBlockExact(5);
            if (block == null || !(block.getState() instanceof org.bukkit.block.Chest)) {
                player.sendMessage(ChatColor.RED + "近くにチェストがないか、ターゲットしていないよ！");
                return;
            }

            String chestName = args[1];
            configManager.registerChest(chestName, block.getLocation());
            player.sendMessage(ChatColor.GREEN + "チェスト「" + chestName + "」を登録したよ！");
        }
        else if (sub.equals("countchest")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "カウントチェストの名前を指定してね: /set countchest <名前>");
                return;
            }

            // Check if player is looking at a chest
            org.bukkit.block.Block block = player.getTargetBlockExact(5);
            if (block == null || !(block.getState() instanceof org.bukkit.block.Chest)) {
                player.sendMessage(ChatColor.RED + "近くにチェストがないか、ターゲットしていないよ！");
                return;
            }

            String chestName = args[1];
            configManager.registerCountChest(chestName, block.getLocation());
            player.sendMessage(ChatColor.GOLD + "カウントチェスト「" + chestName + "」を登録したよ！");
        }
        else if (sub.equals("door")) {
            // Check if player is looking at a door
            org.bukkit.block.Block block = player.getTargetBlockExact(5);
            if (block == null || (!block.getType().toString().contains("DOOR"))) {
                player.sendMessage(ChatColor.RED + "近くにドアがないか、ターゲットしていないよ！");
                return;
            }

            configManager.registerDoor(block.getLocation());
            player.sendMessage(ChatColor.GREEN + "メインドアを登録したよ！");
        }
        else if (sub.equals("exitdoor")) {
            // Check if player is looking at a door
            org.bukkit.block.Block block = player.getTargetBlockExact(5);
            if (block == null || (!block.getType().toString().contains("DOOR"))) {
                player.sendMessage(ChatColor.RED + "近くにドアがないか、ターゲットしていないよ！");
                return;
            }

            configManager.registerExitDoor(block.getLocation());
            player.sendMessage(ChatColor.GREEN + "出口ドアを登録したよ！");
        }
        else if (sub.equals("setreq")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "必要なカウントチェスト数を指定してね: /set setreq <数>");
                return;
            }

            try {
                int requiredChests = Integer.parseInt(args[1]);
                if (requiredChests < 1) {
                    player.sendMessage(ChatColor.RED + "1以上の値を指定してね！");
                    return;
                }

                configManager.setRequiredCountChests(requiredChests);
                player.sendMessage(ChatColor.GREEN + "鍵入手に必要なカウントチェスト数を" + requiredChests + "に設定したよ！");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "数値を指定してね: /set setreq <数>");
            }
        }
        else {
            player.sendMessage(ChatColor.RED + "使い方: /set chest <名前>  または  /set countchest <名前>  または  /set door  または  /set exitdoor");
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

                // Open the exit door
                gameManager.openExitDoor();
                player.sendMessage(ChatColor.GREEN + "鍵を使って出口ドアを開いたよ！");
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

    // GUI Helpers
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

    private void openGameStartGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "ゲームスタート");

        ItemStack normalStart = new ItemStack(Material.GREEN_WOOL);
        org.bukkit.inventory.meta.ItemMeta normalMeta = normalStart.getItemMeta();
        normalMeta.setDisplayName("§2通常スタート");
        java.util.List<String> normalLore = new java.util.ArrayList<>();
        normalLore.add("§7すべてのプレイヤーが陣営選択済みの場合にゲームを開始します");
        normalMeta.setLore(normalLore);
        normalStart.setItemMeta(normalMeta);
        inv.setItem(2, normalStart);

        ItemStack oniStart = new ItemStack(Material.RED_WOOL);
        org.bukkit.inventory.meta.ItemMeta oniMeta = oniStart.getItemMeta();
        oniMeta.setDisplayName("§c鬼スタート");
        java.util.List<String> oniLore = new java.util.ArrayList<>();
        oniLore.add("§7あなたが鬼になります");
        oniLore.add("§7他のプレイヤーは自動的にプレイヤー陣営になります");
        oniMeta.setLore(oniLore);
        oniStart.setItemMeta(oniMeta);
        inv.setItem(6, oniStart);

        player.openInventory(inv);
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
}