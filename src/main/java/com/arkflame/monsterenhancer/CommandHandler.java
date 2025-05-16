package com.arkflame.monsterenhancer;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandHandler implements CommandExecutor {
    
    private final MonsterEnhancer plugin;
    
    public CommandHandler(MonsterEnhancer plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("monsterenhancer")) {
            return false;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("monsterenhancer.reload")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to reload the configuration.");
                return true;
            }
            
            plugin.reloadPluginConfig();
            sender.sendMessage(ChatColor.GREEN + "MonsterEnhancer configuration reloaded successfully!");
            return true;
        }
        
        sendHelp(sender);
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "==== MonsterEnhancer Help ====");
        sender.sendMessage(ChatColor.YELLOW + "/me reload" + ChatColor.WHITE + " - Reload the configuration");
    }
}