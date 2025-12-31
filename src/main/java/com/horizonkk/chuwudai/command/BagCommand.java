package com.horizonkk.chuwudai.command;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class BagCommand implements CommandExecutor {
    
    private final BagManager bagManager;
    
    public BagCommand() {
        this.bagManager = Chuwudai.getInstance().getBagManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f");
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "give":
                if (!sender.hasPermission("chuwudai.give")) {
                    sender.sendMessage(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限！"));
                    return true;
                }
                
                if (args.length < 3) {
                    sender.sendMessage(prefix + Chuwudai.getInstance().getConfig().getString("commands.give.usage", "&c用法: /chuwudai give <玩家> <类型[27/54/81/108]>"));
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(prefix + "&c玩家不在线！");
                    return true;
                }
                
                try {
                    int size = Integer.parseInt(args[2]);
                    List<Integer> validSizes = Arrays.asList(27, 54, 81, 108);
                    
                    if (!validSizes.contains(size)) {
                        sender.sendMessage(prefix + "&c无效的储物袋大小！可选: 27, 54, 81, 108");
                        return true;
                    }
                    
                    bagManager.giveBagToPlayer(target, size);
                    sender.sendMessage(prefix + Chuwudai.getInstance().getConfig().getString("commands.give.success", "&a已给予 %player% 一个%type%储物袋")
                            .replace("%player%", target.getName())
                            .replace("%type%", getSizeName(size)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(prefix + "&c无效的数字！");
                }
                break;
                
            case "open":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(prefix + "&c只有玩家才能使用此命令！");
                    return true;
                }
                
                if (!sender.hasPermission("chuwudai.open")) {
                    sender.sendMessage(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限！"));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(prefix + Chuwudai.getInstance().getConfig().getString("commands.open.usage", "&c用法: /chuwudai open <储物袋ID>"));
                    return true;
                }
                
                String bagId = args[1];
                bagManager.openBag((Player) sender, bagId);
                break;
                
            case "reload":
                if (!sender.hasPermission("chuwudai.reload")) {
                    sender.sendMessage(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限！"));
                    return true;
                }
                
                Chuwudai.getInstance().reloadConfig();
                sender.sendMessage(prefix + "&a配置已重载！");
                break;
                
            case "help":
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        String prefix = Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f");
        
        sender.sendMessage(prefix + "&6=== 储物袋插件帮助 ===");
        if (sender.hasPermission("chuwudai.give")) {
            sender.sendMessage("&e/chuwudai give <玩家> <大小> &7- 给予储物袋");
        }
        if (sender.hasPermission("chuwudai.open")) {
            sender.sendMessage("&e/chuwudai open <ID> &7- 打开储物袋");
        }
        if (sender.hasPermission("chuwudai.reload")) {
            sender.sendMessage("&e/chuwudai reload &7- 重载配置");
        }
        sender.sendMessage("&e/chuwudai help &7- 显示帮助");
    }
    
    private String getSizeName(int size) {
        switch (size) {
            case 27: return "小型";
            case 54: return "中型";
            case 81: return "大型";
            case 108: return "巨型";
            default: return "";
        }
    }
}