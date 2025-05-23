package org.oni.oniGo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final OniGo plugin;
    private Map<String, Location> chestLocations = new HashMap<>();
    private Map<String, Boolean> chestOpened = new HashMap<>();

    // カウントチェスト
    private Map<String, Location> countChestLocations = new HashMap<>();
    private Map<String, Boolean> countChestOpened = new HashMap<>();

    private Location doorLocation;       // メインドア
    private Location exitDoorLocation;   // 出口ドア
    private Location escapeLocation;
    private Location initialSpawnLocation;

    private int requiredCountChests = 3; // 1人あたり必要カウントチェスト数

    public ConfigManager(OniGo plugin) {
        this.plugin = plugin;
        // デフォルト設定
        escapeLocation = new Location(Bukkit.getWorlds().get(0), 104, -6, -36);
        initialSpawnLocation = new Location(Bukkit.getWorlds().get(0), 6, 18, -28);
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        // 通常チェスト
        if (config.isConfigurationSection("chests")) {
            for (String chestName : config.getConfigurationSection("chests").getKeys(false)) {
                double x = config.getDouble("chests." + chestName + ".x");
                double y = config.getDouble("chests." + chestName + ".y");
                double z = config.getDouble("chests." + chestName + ".z");
                String worldName = config.getString("chests." + chestName + ".world");
                Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                chestLocations.put(chestName, loc);
                chestOpened.put(chestName, false);
            }
        }

        // カウントチェスト
        if (config.isConfigurationSection("count_chests")) {
            for (String chestName : config.getConfigurationSection("count_chests").getKeys(false)) {
                double x = config.getDouble("count_chests." + chestName + ".x");
                double y = config.getDouble("count_chests." + chestName + ".y");
                double z = config.getDouble("count_chests." + chestName + ".z");
                String worldName = config.getString("count_chests." + chestName + ".world");
                Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                countChestLocations.put(chestName, loc);
                countChestOpened.put(chestName, false);
            }
        }

        // 必要カウントチェスト数
        if (config.contains("required_count_chests")) {
            requiredCountChests = config.getInt("required_count_chests", 3);
        }

        // door
        if (config.isConfigurationSection("door")) {
            double x = config.getDouble("door.x");
            double y = config.getDouble("door.y");
            double z = config.getDouble("door.z");
            String worldName = config.getString("door.world");
            doorLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        }

        // exit door
        if (config.isConfigurationSection("exit_door")) {
            double x = config.getDouble("exit_door.x");
            double y = config.getDouble("exit_door.y");
            double z = config.getDouble("exit_door.z");
            String worldName = config.getString("exit_door.world");
            exitDoorLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        }

        // escape
        if (config.isConfigurationSection("escape")) {
            double x = config.getDouble("escape.x");
            double y = config.getDouble("escape.y");
            double z = config.getDouble("escape.z");
            String worldName = config.getString("escape.world");
            escapeLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        }

        // initial spawn
        if (config.isConfigurationSection("initial_spawn")) {
            double x = config.getDouble("initial_spawn.x");
            double y = config.getDouble("initial_spawn.y");
            double z = config.getDouble("initial_spawn.z");
            String worldName = config.getString("initial_spawn.world");
            initialSpawnLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        }
    }

    public void saveConfig() {
        FileConfiguration config = plugin.getConfig();
        // clear and re-set
        config.set("chests", null);
        for (Map.Entry<String, Location> entry : chestLocations.entrySet()) {
            String chestName = entry.getKey();
            Location loc = entry.getValue();
            config.set("chests." + chestName + ".x", loc.getX());
            config.set("chests." + chestName + ".y", loc.getY());
            config.set("chests." + chestName + ".z", loc.getZ());
            config.set("chests." + chestName + ".world", loc.getWorld().getName());
        }

        config.set("count_chests", null);
        for (Map.Entry<String, Location> entry : countChestLocations.entrySet()) {
            String chestName = entry.getKey();
            Location loc = entry.getValue();
            config.set("count_chests." + chestName + ".x", loc.getX());
            config.set("count_chests." + chestName + ".y", loc.getY());
            config.set("count_chests." + chestName + ".z", loc.getZ());
            config.set("count_chests." + chestName + ".world", loc.getWorld().getName());
        }

        config.set("required_count_chests", requiredCountChests);

        if (doorLocation != null) {
            config.set("door.x", doorLocation.getX());
            config.set("door.y", doorLocation.getY());
            config.set("door.z", doorLocation.getZ());
            config.set("door.world", doorLocation.getWorld().getName());
        }

        if (exitDoorLocation != null) {
            config.set("exit_door.x", exitDoorLocation.getX());
            config.set("exit_door.y", exitDoorLocation.getY());
            config.set("exit_door.z", exitDoorLocation.getZ());
            config.set("exit_door.world", exitDoorLocation.getWorld().getName());
        }

        config.set("escape.x", escapeLocation.getX());
        config.set("escape.y", escapeLocation.getY());
        config.set("escape.z", escapeLocation.getZ());
        config.set("escape.world", escapeLocation.getWorld().getName());

        config.set("initial_spawn.x", initialSpawnLocation.getX());
        config.set("initial_spawn.y", initialSpawnLocation.getY());
        config.set("initial_spawn.z", initialSpawnLocation.getZ());
        config.set("initial_spawn.world", initialSpawnLocation.getWorld().getName());

        plugin.saveConfig();
    }

    public void registerChest(String name, Location location) {
        chestLocations.put(name, location);
        chestOpened.put(name, false);
        saveConfig();
    }

    public void registerCountChest(String name, Location location) {
        countChestLocations.put(name, location);
        countChestOpened.put(name, false);
        saveConfig();
    }

    public void registerDoor(Location location) {
        doorLocation = location;
        saveConfig();
    }

    public void registerExitDoor(Location location) {
        exitDoorLocation = location;
        saveConfig();
    }

    public void setChestOpened(String name, boolean opened) {
        chestOpened.put(name, opened);
    }

    public void setCountChestOpened(String name, boolean opened) {
        countChestOpened.put(name, opened);
    }

    public void resetChests() {
        for (String name : chestOpened.keySet()) {
            chestOpened.put(name, false);
        }
        for (String name : countChestOpened.keySet()) {
            countChestOpened.put(name, false);
        }
    }

    public boolean areAllChestsOpened() {
        for (boolean opened : chestOpened.values()) {
            if (!opened) {
                return false;
            }
        }
        return true;
    }

    public String getChestNameAtLocation(Location location) {
        for (Map.Entry<String, Location> entry : chestLocations.entrySet()) {
            if (entry.getValue().equals(location)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String getCountChestNameAtLocation(Location location) {
        for (Map.Entry<String, Location> entry : countChestLocations.entrySet()) {
            if (entry.getValue().equals(location)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean isChestOpened(String name) {
        return chestOpened.getOrDefault(name, false);
    }

    public boolean isCountChestOpened(String name) {
        return countChestOpened.getOrDefault(name, false);
    }

    public void setRequiredCountChests(int count) {
        requiredCountChests = count;
        saveConfig();
    }

    public int getTotalCountChests() {
        return countChestLocations.size();
    }

    public int getOpenedCountChestsCount() {
        int count = 0;
        for (boolean opened : countChestOpened.values()) {
            if (opened) {
                count++;
            }
        }
        return count;
    }

    public Map<String, Location> getChestLocations() {
        return chestLocations;
    }

    public Map<String, Location> getCountChestLocations() {
        return countChestLocations;
    }

    public Map<String, Boolean> getChestOpened() {
        return chestOpened;
    }

    public Map<String, Boolean> getCountChestOpened() {
        return countChestOpened;
    }

    public Location getDoorLocation() {
        return doorLocation;
    }

    public Location getExitDoorLocation() {
        return exitDoorLocation;
    }

    public Location getEscapeLocation() {
        return escapeLocation;
    }

    public Location getInitialSpawnLocation() {
        return initialSpawnLocation;
    }

    public int getRequiredCountChests() {
        return requiredCountChests;
    }
}
