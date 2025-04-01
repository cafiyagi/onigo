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
    private Location doorLocation;
    private Location escapeLocation;
    private Location initialSpawnLocation;

    public ConfigManager(OniGo plugin) {
        this.plugin = plugin;
        // Initialize default locations
        escapeLocation = new Location(Bukkit.getWorlds().get(0), 104, -6, -36);
        initialSpawnLocation = new Location(Bukkit.getWorlds().get(0), 6, 18, -28);
    }

    /**
     * Load chest and door locations from config
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        // Load chest locations
        if (config.isConfigurationSection("chests")) {
            for (String chestName : config.getConfigurationSection("chests").getKeys(false)) {
                double x = config.getDouble("chests." + chestName + ".x");
                double y = config.getDouble("chests." + chestName + ".y");
                double z = config.getDouble("chests." + chestName + ".z");
                String worldName = config.getString("chests." + chestName + ".world");
                Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                chestLocations.put(chestName, loc);
                chestOpened.put(chestName, false); // Initialize as unopened
            }
        }

        // Load door location
        if (config.isConfigurationSection("door")) {
            double x = config.getDouble("door.x");
            double y = config.getDouble("door.y");
            double z = config.getDouble("door.z");
            String worldName = config.getString("door.world");
            doorLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        }

        // Load escape location if exists
        if (config.isConfigurationSection("escape")) {
            double x = config.getDouble("escape.x");
            double y = config.getDouble("escape.y");
            double z = config.getDouble("escape.z");
            String worldName = config.getString("escape.world");
            escapeLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        }

        // Load initial spawn if exists
        if (config.isConfigurationSection("initial_spawn")) {
            double x = config.getDouble("initial_spawn.x");
            double y = config.getDouble("initial_spawn.y");
            double z = config.getDouble("initial_spawn.z");
            String worldName = config.getString("initial_spawn.world");
            initialSpawnLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        }
    }

    /**
     * Save chest and door locations to config
     */
    public void saveConfig() {
        FileConfiguration config = plugin.getConfig();

        // Clear and save chest locations
        config.set("chests", null);
        for (Map.Entry<String, Location> entry : chestLocations.entrySet()) {
            String chestName = entry.getKey();
            Location loc = entry.getValue();
            config.set("chests." + chestName + ".x", loc.getX());
            config.set("chests." + chestName + ".y", loc.getY());
            config.set("chests." + chestName + ".z", loc.getZ());
            config.set("chests." + chestName + ".world", loc.getWorld().getName());
        }

        // Save door location
        if (doorLocation != null) {
            config.set("door.x", doorLocation.getX());
            config.set("door.y", doorLocation.getY());
            config.set("door.z", doorLocation.getZ());
            config.set("door.world", doorLocation.getWorld().getName());
        }

        // Save escape location
        config.set("escape.x", escapeLocation.getX());
        config.set("escape.y", escapeLocation.getY());
        config.set("escape.z", escapeLocation.getZ());
        config.set("escape.world", escapeLocation.getWorld().getName());

        // Save initial spawn location
        config.set("initial_spawn.x", initialSpawnLocation.getX());
        config.set("initial_spawn.y", initialSpawnLocation.getY());
        config.set("initial_spawn.z", initialSpawnLocation.getZ());
        config.set("initial_spawn.world", initialSpawnLocation.getWorld().getName());

        plugin.saveConfig();
    }

    /**
     * Register a chest location
     */
    public void registerChest(String name, Location location) {
        chestLocations.put(name, location);
        chestOpened.put(name, false);
        saveConfig();
    }

    /**
     * Register door location
     */
    public void registerDoor(Location location) {
        doorLocation = location;
        saveConfig();
    }

    /**
     * Mark a chest as opened
     */
    public void setChestOpened(String name, boolean opened) {
        chestOpened.put(name, opened);
    }

    /**
     * Reset all chests to unopened
     */
    public void resetChests() {
        for (String name : chestOpened.keySet()) {
            chestOpened.put(name, false);
        }
    }

    /**
     * Check if all chests are opened
     */
    public boolean areAllChestsOpened() {
        for (boolean opened : chestOpened.values()) {
            if (!opened) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if chest exists at location
     */
    public String getChestNameAtLocation(Location location) {
        for (Map.Entry<String, Location> entry : chestLocations.entrySet()) {
            if (entry.getValue().equals(location)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Check if chest is opened
     */
    public boolean isChestOpened(String name) {
        return chestOpened.getOrDefault(name, false);
    }

    // Getters
    public Map<String, Location> getChestLocations() {
        return chestLocations;
    }

    public Map<String, Boolean> getChestOpened() {
        return chestOpened;
    }

    public Location getDoorLocation() {
        return doorLocation;
    }

    public Location getEscapeLocation() {
        return escapeLocation;
    }

    public Location getInitialSpawnLocation() {
        return initialSpawnLocation;
    }
}