package com.example.dutyplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DutyPlugin extends JavaPlugin {
    
    private File dataFile;
    private FileConfiguration dataConfig;
    private Map<UUID, DutySession> activeSessions;
    private String webhookUrl;
    private Map<String, String> dutyTypes; // duty name -> permission
    
    @Override
    public void onEnable() {
        activeSessions = new HashMap<>();
        dutyTypes = new HashMap<>();
        
        // Create config
        saveDefaultConfig();
        loadConfig();
        
        // Load data file
        dataFile = new File(getDataFolder(), "dutydata.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        getLogger().info("DutyPlugin has been enabled!");
        getLogger().info("Loaded " + dutyTypes.size() + " duty types from config.");
    }
    
    @Override
    public void onDisable() {
        // End all active sessions
        for (UUID uuid : activeSessions.keySet()) {
            DutySession session = activeSessions.get(uuid);
            endDutySession(uuid, session);
        }
        saveData();
        getLogger().info("DutyPlugin has been disabled!");
    }
    
    private void loadConfig() {
        reloadConfig();
        webhookUrl = getConfig().getString("discord-webhook-url", "");
        
        // Load duty types from config
        dutyTypes.clear();
        ConfigurationSection dutiesSection = getConfig().getConfigurationSection("duties");
        if (dutiesSection != null) {
            Set<String> dutyNames = dutiesSection.getKeys(false);
            for (String dutyName : dutyNames) {
                String permission = dutiesSection.getString(dutyName + ".permission");
                if (permission != null && !permission.isEmpty()) {
                    dutyTypes.put(dutyName, permission);
                }
            }
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("duty")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                return handleReloadCommand(sender);
            }
            
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }
            
            Player player = (Player) sender;
            return handleDutyCommand(player, args);
            
        } else if (command.getName().equalsIgnoreCase("checktime")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }
            Player player = (Player) sender;
            return handleCheckTimeCommand(player, args);
            
        } else if (command.getName().equalsIgnoreCase("resettime")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }
            Player player = (Player) sender;
            return handleResetTimeCommand(player, args);
        }
        
        return false;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("duty.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to reload the config!");
            return true;
        }
        
        loadConfig();
        sender.sendMessage(ChatColor.GREEN + "DutyPlugin config reloaded! Loaded " + 
                         ChatColor.YELLOW + dutyTypes.size() + 
                         ChatColor.GREEN + " duty types.");
        return true;
    }
    
    private boolean handleDutyCommand(Player player, String[] args) {
        UUID uuid = player.getUniqueId();
        
        // Check if going off duty
        if (args.length == 0) {
            if (!activeSessions.containsKey(uuid)) {
                player.sendMessage(ChatColor.RED + "You are not currently on duty!");
                return true;
            }
            
            DutySession session = activeSessions.get(uuid);
            endDutySession(uuid, session);
            
            long duration = System.currentTimeMillis() - session.getStartTime();
            String formattedTime = formatDuration(duration);
            
            player.sendMessage(ChatColor.GREEN + "You have gone off duty for " + 
                             ChatColor.YELLOW + session.getDutyName() + 
                             ChatColor.GREEN + "! Duration: " + ChatColor.AQUA + formattedTime);
            
            // Send to Discord
            sendDiscordMessage(player.getName() + " went OFF duty for **" + session.getDutyName() + 
                             "** (Duration: " + formattedTime + ")", 15158332);
            
            activeSessions.remove(uuid);
            saveData();
            return true;
        }
        
        // Going on duty
        String dutyName = args[0];
        
        // Check if duty type exists in config
        if (!dutyTypes.containsKey(dutyName)) {
            player.sendMessage(ChatColor.RED + "Unknown duty type: " + dutyName);
            player.sendMessage(ChatColor.YELLOW + "Available duties: " + String.join(", ", dutyTypes.keySet()));
            return true;
        }
        
        // Check permission
        String requiredPermission = dutyTypes.get(dutyName);
        if (!player.hasPermission(requiredPermission)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to go on duty for " + dutyName + "!");
            player.sendMessage(ChatColor.GRAY + "Required permission: " + requiredPermission);
            return true;
        }
        
        // Check if already on duty
        if (activeSessions.containsKey(uuid)) {
            DutySession currentSession = activeSessions.get(uuid);
            player.sendMessage(ChatColor.RED + "You are already on duty for " + 
                             ChatColor.YELLOW + currentSession.getDutyName() + 
                             ChatColor.RED + "! Use /duty to go off duty first.");
            return true;
        }
        
        // Start duty session
        DutySession session = new DutySession(dutyName, System.currentTimeMillis());
        activeSessions.put(uuid, session);
        
        player.sendMessage(ChatColor.GREEN + "You are now on duty for " + 
                         ChatColor.YELLOW + dutyName + ChatColor.GREEN + "!");
        
        // Send to Discord
        sendDiscordMessage(player.getName() + " went ON duty for **" + dutyName + "**", 3066993);
        
        return true;
    }
    
    private boolean handleCheckTimeCommand(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /checktime <duty_name>");
            player.sendMessage(ChatColor.YELLOW + "Available duties: " + String.join(", ", dutyTypes.keySet()));
            return true;
        }
        
        String dutyName = args[0];
        UUID uuid = player.getUniqueId();
        
        String path = "players." + uuid.toString() + "." + dutyName;
        long totalTime = dataConfig.getLong(path, 0);
        
        // Add current session time if on duty for this
        if (activeSessions.containsKey(uuid)) {
            DutySession session = activeSessions.get(uuid);
            if (session.getDutyName().equals(dutyName)) {
                totalTime += System.currentTimeMillis() - session.getStartTime();
            }
        }
        
        String formattedTime = formatDuration(totalTime);
        player.sendMessage(ChatColor.GREEN + "Total time for " + 
                         ChatColor.YELLOW + dutyName + 
                         ChatColor.GREEN + ": " + ChatColor.AQUA + formattedTime);
        
        return true;
    }
    
    private boolean handleResetTimeCommand(Player player, String[] args) {
        if (!player.hasPermission("duty.reset")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to reset duty time!");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /resettime <duty_name|ALL>");
            return true;
        }
        
        String target = args[0];
        UUID uuid = player.getUniqueId();
        
        if (target.equalsIgnoreCase("ALL")) {
            dataConfig.set("players." + uuid.toString(), null);
            saveData();
            player.sendMessage(ChatColor.GREEN + "All duty times have been reset!");
        } else {
            String path = "players." + uuid.toString() + "." + target;
            dataConfig.set(path, 0);
            saveData();
            player.sendMessage(ChatColor.GREEN + "Duty time for " + 
                             ChatColor.YELLOW + target + 
                             ChatColor.GREEN + " has been reset!");
        }
        
        return true;
    }
    
    private void endDutySession(UUID uuid, DutySession session) {
        long duration = System.currentTimeMillis() - session.getStartTime();
        String path = "players." + uuid.toString() + "." + session.getDutyName();
        long currentTotal = dataConfig.getLong(path, 0);
        dataConfig.set(path, currentTotal + duration);
    }
    
    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
    
    private void sendDiscordMessage(String message, int color) {
        if (webhookUrl.isEmpty()) {
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                String json = String.format(
                    "{\"embeds\":[{\"description\":\"%s\",\"color\":%d,\"timestamp\":\"%s\"}]}",
                    message, color, java.time.Instant.now().toString()
                );
                
                conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private static class DutySession {
        private final String dutyName;
        private final long startTime;
        
        public DutySession(String dutyName, long startTime) {
            this.dutyName = dutyName;
            this.startTime = startTime;
        }
        
        public String getDutyName() {
            return dutyName;
        }
        
        public long getStartTime() {
            return startTime;
        }
    }
}
