package com.example.dutyplugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DutyPlugin extends JavaPlugin {
    
    private File dataFile;
    private FileConfiguration dataConfig;
    private Map<UUID, DutySession> activeSessions;
    private String webhookUrl;
    private Map<String, DutyType> dutyTypes; // duty name -> DutyType object
    private LuckPerms luckPerms;
    private boolean luckPermsEnabled = false;
    
    @Override
    public void onEnable() {
        activeSessions = new HashMap<>();
        dutyTypes = new HashMap<>();
        
        // Create config
        saveDefaultConfig();
        loadConfig();
        
        // Hook into LuckPerms
        setupLuckPerms();
        
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
        if (luckPermsEnabled) {
            getLogger().info("LuckPerms integration enabled!");
        } else {
            getLogger().warning("LuckPerms not found! Group management will be disabled.");
        }
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
    
    private void setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            luckPermsEnabled = true;
        }
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
                String group = dutiesSection.getString(dutyName + ".group", "");
                
                if (permission != null && !permission.isEmpty()) {
                    dutyTypes.put(dutyName, new DutyType(dutyName, permission, group));
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
            return handleCheckTimeCommand(sender, args);
            
        } else if (command.getName().equalsIgnoreCase("dutytimes")) {
            return handleDutyTimesCommand(sender, args);
            
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
            
            // Remove LuckPerms group
            DutyType dutyType = dutyTypes.get(session.getDutyName());
            if (dutyType != null && !dutyType.getGroup().isEmpty()) {
                removeGroup(player, dutyType.getGroup());
            }
            
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
        
        DutyType dutyType = dutyTypes.get(dutyName);
        
        // Check permission
        if (!player.hasPermission(dutyType.getPermission())) {
            player.sendMessage(ChatColor.RED + "You don't have permission to go on duty for " + dutyName + "!");
            player.sendMessage(ChatColor.GRAY + "Required permission: " + dutyType.getPermission());
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
        
        // Add LuckPerms group
        if (!dutyType.getGroup().isEmpty()) {
            addGroup(player, dutyType.getGroup());
        }
        
        player.sendMessage(ChatColor.GREEN + "You are now on duty for " + 
                         ChatColor.YELLOW + dutyName + ChatColor.GREEN + "!");
        
        // Send to Discord
        sendDiscordMessage(player.getName() + " went ON duty for **" + dutyName + "**", 3066993);
        
        return true;
    }
    
    private void addGroup(Player player, String groupName) {
        if (!luckPermsEnabled) return;
        
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;
        
        Node node = Node.builder("group." + groupName).build();
        user.data().add(node);
        luckPerms.getUserManager().saveUser(user);
        
        player.sendMessage(ChatColor.GRAY + "Added to group: " + ChatColor.YELLOW + groupName);
    }
    
    private void removeGroup(Player player, String groupName) {
        if (!luckPermsEnabled) return;
        
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;
        
        Node node = Node.builder("group." + groupName).build();
        user.data().remove(node);
        luckPerms.getUserManager().saveUser(user);
        
        player.sendMessage(ChatColor.GRAY + "Removed from group: " + ChatColor.YELLOW + groupName);
    }
    
    private boolean handleCheckTimeCommand(CommandSender sender, String[] args) {
        // /checktime <duty_name> - check your own time
        // /checktime <player> <duty_name> - check another player's time (requires permission)
        
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /checktime <duty_name> or /checktime <player> <duty_name>");
            sender.sendMessage(ChatColor.YELLOW + "Available duties: " + String.join(", ", dutyTypes.keySet()));
            return true;
        }
        
        if (args.length == 1) {
            // Check own time
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /checktime <player> <duty_name>");
                return true;
            }
            
            Player player = (Player) sender;
            String dutyName = args[0];
            UUID uuid = player.getUniqueId();
            
            long totalTime = getTotalTime(uuid, dutyName);
            String formattedTime = formatDuration(totalTime);
            
            player.sendMessage(ChatColor.GREEN + "Your total time for " + 
                             ChatColor.YELLOW + dutyName + 
                             ChatColor.GREEN + ": " + ChatColor.AQUA + formattedTime);
            return true;
        }
        
        // Check another player's time
        if (!sender.hasPermission("duty.checkothers")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to check other players' times!");
            return true;
        }
        
        String targetName = args[0];
        String dutyName = args[1];
        
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }
        
        UUID targetUuid = target.getUniqueId();
        long totalTime = getTotalTime(targetUuid, dutyName);
        String formattedTime = formatDuration(totalTime);
        
        sender.sendMessage(ChatColor.GREEN + target.getName() + "'s total time for " + 
                         ChatColor.YELLOW + dutyName + 
                         ChatColor.GREEN + ": " + ChatColor.AQUA + formattedTime);
        
        return true;
    }
    
    private boolean handleDutyTimesCommand(CommandSender sender, String[] args) {
        // /dutytimes <duty_name> [page]
        
        if (!sender.hasPermission("duty.viewall")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view all duty times!");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /dutytimes <duty_name> [page]");
            sender.sendMessage(ChatColor.YELLOW + "Available duties: " + String.join(", ", dutyTypes.keySet()));
            return true;
        }
        
        String dutyName = args[0];
        int page = 1;
        
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid page number!");
                return true;
            }
        }
        
        // Collect all players and their times
        List<PlayerTimeEntry> entries = new ArrayList<>();
        ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
        
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long time = getTotalTime(uuid, dutyName);
                    
                    if (time > 0) {
                        @SuppressWarnings("deprecation")
                        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                        String playerName = player.getName() != null ? player.getName() : "Unknown";
                        entries.add(new PlayerTimeEntry(playerName, time));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        
        // Sort by time (descending)
        entries.sort((a, b) -> Long.compare(b.time, a.time));
        
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No recorded times for " + dutyName);
            return true;
        }
        
        // Pagination
        int entriesPerPage = 10;
        int totalPages = (int) Math.ceil((double) entries.size() / entriesPerPage);
        
        if (page > totalPages) page = totalPages;
        
        int startIndex = (page - 1) * entriesPerPage;
        int endIndex = Math.min(startIndex + entriesPerPage, entries.size());
        
        // Display header
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "Duty Times for " + ChatColor.AQUA + dutyName + 
                         ChatColor.GRAY + " (Page " + page + "/" + totalPages + ")");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        
        // Display entries
        for (int i = startIndex; i < endIndex; i++) {
            PlayerTimeEntry entry = entries.get(i);
            int rank = i + 1;
            String rankColor = getRankColor(rank);
            
            sender.sendMessage(rankColor + "#" + rank + ". " + 
                             ChatColor.WHITE + entry.playerName + 
                             ChatColor.GRAY + " - " + 
                             ChatColor.AQUA + formatDuration(entry.time));
        }
        
        // Display footer
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        if (page < totalPages) {
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/dutytimes " + dutyName + " " + (page + 1) + 
                             ChatColor.GRAY + " for next page");
        }
        
        return true;
    }
    
    private String getRankColor(int rank) {
        switch (rank) {
            case 1: return ChatColor.GOLD.toString();
            case 2: return ChatColor.GRAY.toString();
            case 3: return ChatColor.YELLOW.toString();
            default: return ChatColor.WHITE.toString();
        }
    }
    
    private long getTotalTime(UUID uuid, String dutyName) {
        String path = "players." + uuid.toString() + "." + dutyName;
        long totalTime = dataConfig.getLong(path, 0);
        
        // Add current session time if on duty for this
        if (activeSessions.containsKey(uuid)) {
            DutySession session = activeSessions.get(uuid);
            if (session.getDutyName().equals(dutyName)) {
                totalTime += System.currentTimeMillis() - session.getStartTime();
            }
        }
        
        return totalTime;
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
    
    private static class PlayerTimeEntry {
        String playerName;
        long time;
        
        public PlayerTimeEntry(String playerName, long time) {
            this.playerName = playerName;
            this.time = time;
        }
    }
    
    private static class DutyType {
        private final String name;
        private final String permission;
        private final String group;
        
        public DutyType(String name, String permission, String group) {
            this.name = name;
            this.permission = permission;
            this.group = group;
        }
        
        public String getName() {
            return name;
        }
        
        public String getPermission() {
            return permission;
        }
        
        public String getGroup() {
            return group;
        }
    }
}
