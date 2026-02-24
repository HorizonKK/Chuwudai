package com.horizonkk.chuwudai.command;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagManager;
import com.horizonkk.chuwudai.utlis.BundleStyle;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import com.horizonkk.chuwudai.utlis.getSize;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class BagCommand implements CommandExecutor {
    
    private final BagManager bagManager;
    private final getSize getSize;
    
    public BagCommand() {
        this.bagManager = Chuwudai.getInstance().getBagManager();
        this.getSize = new getSize();
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
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限执行此命令！")));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c用法: &e/chuwudai give <玩家名> <类型> <大小> [样式]"));
                    sender.sendMessage(ColorUtils.translateColorCodes("&7示例: &f/chuwudai give Steve bag 27 blue"));
                    sender.sendMessage(ColorUtils.translateColorCodes("&7类型: &fbag&7(储物袋) 或 &fpill&7(丹瓶)"));
                    sender.sendMessage(ColorUtils.translateColorCodes("&7样式: &fblue&7, &fbrown&7, &fcyan&7, &fgray&7, &fgreen&7, &flight_blue&7, &flight_gray&7, &flime&7, &fmagenta&7, &forange&7, &fpink&7, &fpurple&7, &fred&7, &fwhite&7, &fyellow"));
                    sender.sendMessage(ColorUtils.translateColorCodes("&7注意: 样式为可选参数，默认为 &fbrown"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c玩家 &e" + args[1] + " &c不在线！"));
                    return true;
                }

                String type = args[2].toLowerCase();
                try {
                    int size = Integer.parseInt(args[3]);
                    String style = "brown"; // 默认样式

                    // 如果提供了样式参数
                    if (args.length >= 5) {
                        style = args[4].toLowerCase();
                        if (!isValidStyle(style)) {
                            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c无效的样式！&7可用样式: &fblue&7, &fbrown&7, &fcyan&7, &fgray&7, &fgreen&7, &flight_blue&7, &flight_gray&7, &flime&7, &fmagenta&7, &forange&7, &fpink&7, &fpurple&7, &fred&7, &fwhite&7, &fyellow"));
                            return true;
                        }
                    }

                    if (type.equals("bag") || type.equals("储物袋")) {
                        // 验证储物袋大小：必须是9的倍数且大于0
                        if (size <= 0 || size % 9 != 0) {
                            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c储物袋大小必须是9的倍数且大于0！"));
                            sender.sendMessage(ColorUtils.translateColorCodes("&7有效大小: &f9&7, &f18&7, &f27&7, &f36&7, &f45&7, &f54&7, &f63&7, &f72&7..."));
                            sender.sendMessage(ColorUtils.translateColorCodes("&7分页说明: 每页最多45格存储，超出部分自动分页"));
                            return true;
                        }
                        // 可以添加最大限制检查
                        if (size > 540) { // 限制最大60排
                            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c储物袋大小不能超过540格！&7(最大60排)"));
                            return true;
                        }
                        bagManager.giveBagToPlayer(target, size, style);
                        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a已成功给予玩家 &e" + target.getName() + " &a一个 &b" + getSize.getBagSizeName(size) + "储物袋&a(&e" + size + "格&a, &e" + getStyleChineseName(style) + "&a样式)"));
                    } else if (type.equals("pill") || type.equals("丹瓶")) {
                        // 验证丹瓶大小
                        List<Integer> validPillSizes = Arrays.asList(9, 18, 27);
                        if (!validPillSizes.contains(size)) {
                            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c无效的丹瓶大小！&7可选: &f9&7, &f18&7, &f27"));
                            return true;
                        }
                        Chuwudai.getInstance().getPillManager().givePillToPlayer(target, size);
                        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a已成功给予玩家 &e" + target.getName() + " &a一个 &d" + getSize.getPillSizeName(size) + "丹瓶"));
                    } else {
                        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c无效的类型！&7请输入 &fbag&7(储物袋) 或 &fpill&7(丹瓶)"));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c无效的数字！&7请输入有效的数字大小"));
                }
                break;
            case "open":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c只有玩家才能使用此命令！"));
                    return true;
                }

                if (!sender.hasPermission("chuwudai.open")) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限执行此命令！")));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c用法: &e/chuwudai open <储物袋ID>"));
                    sender.sendMessage(ColorUtils.translateColorCodes("&7示例: &f/chuwudai open 12345678"));
                    return true;
                }
                
                String bagId = args[1];
                bagManager.openBag((Player) sender, bagId);
                break;

            case "reload":
                if (!sender.hasPermission("chuwudai.reload")) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限执行此命令！")));
                    return true;
                }
                Chuwudai.getInstance().reloadConfig();
                sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a配置文件已成功重载！"));
                break;
                
            case "help":
            case "givestone":
                if (!sender.hasPermission("chuwudai.give")) {
                    sender.sendMessage(prefix + "&c你没有权限！");
                    return true;
                }

                if (!(sender instanceof Player)) {
                    sender.sendMessage(prefix + "&c只有玩家才能使用此命令！");
                    return true;
                }

                Player player1 = (Player) sender;
                ItemStack stone = createTestStone();
                player1.getInventory().addItem(stone);
                player1.sendMessage(prefix + "&a已获得测试灵石");
                break;
            case "teststone":
                if (!sender.hasPermission("chuwudai.test")) {
                    sender.sendMessage(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限！"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(prefix + "&c只有玩家才能使用此命令！");
                    return true;
                }
                Player player2 = (Player) sender;
                ItemStack item = player2.getInventory().getItemInMainHand();
                if (item == null || item.getType() == org.bukkit.Material.AIR) {
                    player2.sendMessage(prefix + "&c你手中没有物品！");
                    return true;
                }
                // 测试物品匹配
                testStoneMatch(player2, item);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private boolean isValidStyle(String style) {
        return BundleStyle.isValidStyle(style);
    }

    // 获取样式的中文名称
    private String getStyleChineseName(String style) {
        return BundleStyle.fromStyleName(style).getChineseName();
    }
    private void sendHelp(CommandSender sender) {
        String prefix = ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f"));
        sender.sendMessage(prefix + ColorUtils.translateColorCodes("&6=== &e储物袋插件帮助 &6==="));
        if (sender.hasPermission("chuwudai.give")) {
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai give <玩家> <类型> <大小> [样式] &7- 给予储物袋或丹瓶"));
            sender.sendMessage(ColorUtils.translateColorCodes("&7示例: &f/chuwudai give Steve bag 27 blue"));
            sender.sendMessage(ColorUtils.translateColorCodes("&7样式可选: &fblue&7, &fbrown&7, &fcyan&7, &fgray&7, &fgreen&7, &flight_blue&7, &flight_gray&7, &flime&7, &fmagenta&7, &forange&7, &fpink&7, &fpurple&7, &fred&7, &fwhite&7, &fyellow"));
            sender.sendMessage(ColorUtils.translateColorCodes("&7默认样式: &fbrown"));
            sender.sendMessage(ColorUtils.translateColorCodes("&7UI说明: 左侧←上一页 | 中间📖信息 | 右侧→下一页"));
        }
        if (sender.hasPermission("chuwudai.open")) {
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai open <ID> &7- 打开指定ID的储物袋"));
        }
        if (sender.hasPermission("chuwudai.reload")) {
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai reload &7- 重载插件配置文件"));
        }
        if (sender.hasPermission("chuwudai.test")) {
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai teststone &7- 测试手中物品是否为灵石"));
        }
        sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai help &7- 显示此帮助信息"));
    }

    private ItemStack createTestStone() {
        ItemStack stone = new ItemStack(org.bukkit.Material.PAPER, 1);
        org.bukkit.inventory.meta.ItemMeta meta = stone.getItemMeta();
        meta.setDisplayName("§f下品灵石");
        meta.setCustomModelData(10016);
        stone.setItemMeta(meta);
        return stone;
    }

    private void testStoneMatch(Player player, ItemStack item) {
        String prefix = Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f");

        // 显示物品基本信息
        player.sendMessage(prefix + "&6=== 物品匹配测试 ===");
        player.sendMessage("&7物品类型: &f" + item.getType().name());
        player.sendMessage("&7物品数量: &f" + item.getAmount());

        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                String displayName = meta.getDisplayName();
                player.sendMessage("&7显示名称: &f'" + displayName + "'");
            } else {
                player.sendMessage("&7显示名称: &f无");
            }

            if (meta.hasLore()) {
                java.util.List<String> lore = meta.getLore();
                player.sendMessage("&7Lore行数: &f" + (lore != null ? lore.size() : 0));
            }
        } else {
            player.sendMessage("&7物品Meta: &f无");
        }

        // 测试配置中的所有灵石类型
        org.bukkit.configuration.ConfigurationSection stonesSection = Chuwudai.getInstance().getConfig().getConfigurationSection("spirit-stones");
        if (stonesSection != null) {
            player.sendMessage("&6--- 配置匹配测试 ---");
            boolean matched = false;

            for (String key : stonesSection.getKeys(false)) {
                player.sendMessage("&7尝试匹配 '&e" + key + "&7':");

                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    String displayName = item.getItemMeta().getDisplayName();
                    boolean isMatch = displayName.equals(key);
                    player.sendMessage("  &f" + (isMatch ? "✓" : "✗") + " &7'" + displayName + "' == '" + key + "'");

                    if (isMatch) {
                        matched = true;
                        player.sendMessage("&a匹配成功！这是 " + key);
                    }
                } else {
                    player.sendMessage("  &c物品无显示名称，无法匹配");
                }
            }

            if (!matched) {
                player.sendMessage("&c未找到匹配的灵石类型");
            }
        } else {
            player.sendMessage("&c配置文件中未找到 spirit-stones 配置");
        }
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