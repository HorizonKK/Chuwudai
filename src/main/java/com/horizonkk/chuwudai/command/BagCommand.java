package com.horizonkk.chuwudai.command;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.api.ChuwudaiAPI;
import com.horizonkk.chuwudai.bag.BagManager;
import com.horizonkk.chuwudai.Temporary.TempBoxManager;
import com.horizonkk.chuwudai.data.BagDataManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BagCommand implements CommandExecutor {

    private final BagManager bagManager;
    private final getSize getSize;
    private volatile boolean migrationRunning;

    public BagCommand() {
        this.bagManager = Chuwudai.getInstance().getBagManager();
        this.getSize = new getSize();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try { return executeCommand(sender,command,label,args); }
        catch(com.horizonkk.chuwudai.storage.StorageAccessException e) {
            sender.sendMessage("§c容器暂不可用："+e.getMessage());
            if(sender instanceof Player player && Chuwudai.getInstance().getSharedStorage()!=null) Chuwudai.getInstance().getSharedStorage().preparePlayer(player).exceptionally(error->null);
            return true;
        }
    }
    private boolean executeCommand(CommandSender sender, Command command, String label, String[] args) {
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
                    sender.sendMessage(ColorUtils.translateColorCodes("&7注意: 样式为可选参数，不指定时随机选择颜色"));
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

                    if (type.equals("bag") || type.equals("储物袋")) {
                        if (size <= 0 || size % 9 != 0) {
                            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c储物袋大小必须是9的倍数且大于0！"));
                            sender.sendMessage(ColorUtils.translateColorCodes("&7有效大小: &f9&7, &f18&7, &f27&7, &f36&7, &f45&7, &f54&7, &f63&7, &f72&7..."));
                            sender.sendMessage(ColorUtils.translateColorCodes("&7分页说明: 每页最多45格存储，超出部分自动分页"));
                            return true;
                        }
                        int maxBagSize = Math.max(9, Chuwudai.getInstance().getConfig().getInt("bag.max-size", 540));
                        if (size > maxBagSize) {
                            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c储物袋大小不能超过" + maxBagSize + "格！"));
                            return true;
                        }

                        String styleName;
                        if (args.length >= 5) {
                            String style = args[4].toLowerCase();
                            if (!isValidStyle(style)) {
                                sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c无效的样式！&7可用样式: &fblue&7, &fbrown&7, &fcyan&7, &fgray&7, &fgreen&7, &flight_blue&7, &flight_gray&7, &flime&7, &fmagenta&7, &forange&7, &fpink&7, &fpurple&7, &fred&7, &fwhite&7, &fyellow"));
                                return true;
                            }
                            styleName = getStyleChineseName(style);
                            bagManager.giveBagToPlayer(target, size, style);
                        } else {
                            styleName = "随机";
                            bagManager.giveBagToPlayer(target, size);
                        }

                        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a已提交发放请求，目标玩家 &e" + target.getName() + " &a一个 &b" + getSize.getBagSizeName(size) + "储物袋&a(&e" + size + "格&a, &e" + styleName + "&a样式)"));
                    } else if (type.equals("pill") || type.equals("丹瓶")) {
                        List<Integer> validPillSizes = Arrays.asList(9, 18, 27);
                        if (!validPillSizes.contains(size)) {
                            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c无效的丹瓶大小！&7可选: &f9&7, &f18&7, &f27"));
                            return true;
                        }
                        Chuwudai.getInstance().getPillManager().givePillToPlayer(target, size);
                        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a已提交发放请求，目标玩家 &e" + target.getName() + " &a一个 &d" + getSize.getPillSizeName(size) + "丹瓶"));
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

            case "getbag":
                if (!sender.hasPermission("chuwudai.getbag") && !sender.hasPermission("chuwudai.give")) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限执行此命令！")));
                    return true;
                }
                handleGetBagCommand(sender, args, prefix);
                break;

            case "tempbox":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c只有玩家才能使用此命令！"));
                    return true;
                }
                if (!sender.hasPermission("chuwudai.tempbox")) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限执行此命令！")));
                    return true;
                }

                if (args.length >= 2 && sender.hasPermission("chuwudai.tempbox.others")) {
                    Player targetPlayer = Bukkit.getPlayer(args[1]);
                    if (targetPlayer == null) {
                        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c玩家 &e" + args[1] + " &c不在线！"));
                        return true;
                    }
                    Chuwudai.getInstance().getTempBoxManager().openTempBox(targetPlayer);
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a已打开玩家 &e" + targetPlayer.getName() + " &a的暂存箱"));
                } else {
                    Chuwudai.getInstance().getTempBoxManager().openTempBox((Player) sender);
                }
                break;

            case "migratepages":
                handleMigratePagesCommand(sender, args, prefix);
                break;

            case "migratesqlite":
                handleMigrateSqliteCommand(sender, args, prefix);
                break;

            case "reload":
                if (!sender.hasPermission("chuwudai.reload")) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限执行此命令！")));
                    return true;
                }
                Chuwudai.getInstance().reloadConfig();
                sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a配置文件已成功重载！存储后端、连接和服务器标识变更需要重启。"));
                break;

            case "batchdeduct":
                if (!sender.hasPermission("chuwudai.test")) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限！")));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c只有玩家才能使用此命令！"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c用法: &e/chuwudai batchdeduct <槽位> <下品数量> <中品数量> <上品数量> <极品数量>"));
                    sender.sendMessage(ColorUtils.translateColorCodes("&7示例: &f/chuwudai batchdeduct 9 100 50 0 0"));
                    return true;
                }
                try {
                    int bagSlot = Integer.parseInt(args[1]);
                    if (bagSlot < 0 || bagSlot > 35) {
                        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c槽位必须在0-35之间！"));
                        return true;
                    }
                    Map<String, Integer> stones = new HashMap<>();
                    if (args.length >= 3) {
                        int xp = Integer.parseInt(args[2]);
                        if (xp > 0) stones.put("&f下品灵石", xp);
                    }
                    if (args.length >= 4) {
                        int zp = Integer.parseInt(args[3]);
                        if (zp > 0) stones.put("&a中品灵石", zp);
                    }
                    if (args.length >= 5) {
                        int sp = Integer.parseInt(args[4]);
                        if (sp > 0) stones.put("&e上品灵石", sp);
                    }
                    if (args.length >= 6) {
                        int jp = Integer.parseInt(args[5]);
                        if (jp > 0) stones.put("&c极品灵石", jp);
                    }
                    if (stones.isEmpty()) {
                        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c至少需要指定一种灵石数量！"));
                        return true;
                    }
                    testBatchDeduct((Player) sender, bagSlot, stones);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c槽位和数量必须是数字！"));
                }
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
                testStoneMatch(player2, item);
                break;
            case "deductstone":
                if (!sender.hasPermission("chuwudai.test")) {
                    sender.sendMessage(prefix + Chuwudai.getInstance().getConfig().getString("messages.no-permission", "&c你没有权限！"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(prefix + "&c只有玩家才能使用此命令！");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(prefix + "&c用法: &e/chuwudai deductstone <槽位> <灵石类型> <数量>");
                    sender.sendMessage(prefix + "&7示例: &f/chuwudai deductstone 9 下品灵石 5");
                    sender.sendMessage(prefix + "&7槽位: 储物袋在背包中的槽位(0-35)");
                    sender.sendMessage(prefix + "&7灵石类型: 下品灵石, 中品灵石, 上品灵石, 极品灵石");
                    return true;
                }
                Player player3 = (Player) sender;
                try {
                    int bagSlot = Integer.parseInt(args[1]);
                    String stoneType = args[2];
                    int amount = Integer.parseInt(args[3]);

                    if (bagSlot < 0 || bagSlot > 35) {
                        player3.sendMessage(prefix + "&c槽位必须在0-35之间！");
                        return true;
                    }

                    if (!isValidStoneType(stoneType)) {
                        player3.sendMessage(prefix + "&c无效的灵石类型！");
                        player3.sendMessage(prefix + "&7可用类型: 下品灵石, 中品灵石, 上品灵石, 极品灵石");
                        return true;
                    }

                    testDeductStone(player3, bagSlot, stoneType, amount);
                } catch (NumberFormatException e) {
                    player3.sendMessage(prefix + "&c槽位和数量必须是数字！");
                }
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handleMigrateSqliteCommand(CommandSender sender, String[] args, String prefix) {
        if(!sender.hasPermission("chuwudai.migrate")) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c没有权限: chuwudai.migrate"));
            return;
        }
        if(!(sender instanceof org.bukkit.command.ConsoleCommandSender)
                && !(sender instanceof org.bukkit.command.RemoteConsoleCommandSender)) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c请在服务端控制台执行 SQLite 迁移。"));
            return;
        }
        if(args.length!=2 || !("check".equalsIgnoreCase(args[1]) || "start".equalsIgnoreCase(args[1]))) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&e用法: chuwudai migratesqlite <check|start>"));
            sender.sendMessage(ColorUtils.translateColorCodes("&7check 检查数据和冲突；start 导入，跳过 MySQL 已有 ID，保留 SQLite 原文件。"));
            return;
        }
        Chuwudai plugin=Chuwudai.getInstance();
        var shared=plugin.getSharedStorage();
        if(shared==null) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c请先正常停服保存 SQLite，配置 MySQL 后以 storage.type: mysql 重启，再执行迁移。"));
            return;
        }
        java.nio.file.Path source=plugin.getDataFolder().toPath().resolve(plugin.getConfig().getString("storage.sqlite.file","bags.db"));
        boolean dryRun="check".equalsIgnoreCase(args[1]);
        var job=shared.migrateSqlite(source,dryRun);
        if(!job.isCompletedExceptionally()) sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a已开始 SQLite " + (dryRun ? "检查" : "导入") + "，源文件: " + source));
        job.whenComplete((result,error)->{
            if(!plugin.isEnabled()) return;
            shared.onMain(()->{
                if(error!=null) {
                    Throwable cause=error;
                    while(cause.getCause()!=null && (cause instanceof java.util.concurrent.CompletionException || cause instanceof java.util.concurrent.ExecutionException)) cause=cause.getCause();
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c迁移未完成: " + cause.getMessage()));
                    return null;
                }
                String verb=dryRun ? "可导入" : "已导入";
                sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&aSQLite " + (dryRun ? "检查" : "导入") + "结束。&7扫描: &f" + result.scanned()));
                sender.sendMessage(ColorUtils.translateColorCodes("&7" + verb + "储物袋: &f" + result.imported().getOrDefault(com.horizonkk.chuwudai.storage.MySqlRepository.Kind.BAG,0L)
                        + " &7丹瓶: &f" + result.imported().getOrDefault(com.horizonkk.chuwudai.storage.MySqlRepository.Kind.PILL,0L)
                        + " &7暂存箱: &f" + result.imported().getOrDefault(com.horizonkk.chuwudai.storage.MySqlRepository.Kind.TEMP,0L)));
                sender.sendMessage(ColorUtils.translateColorCodes("&7已有 ID 跳过: &e" + result.existing() + " &7正在占用跳过: &e" + result.busy() + " &7数据异常: &c" + result.failed()));
                sender.sendMessage(ColorUtils.translateColorCodes("&7明细报告: &f" + result.reportFile()));
                if(result.existing()+result.busy()+result.failed()>0) sender.sendMessage(ColorUtils.translateColorCodes("&e仍有未迁移的容器，请检查报告；已有 ID 不会自动覆盖，包括空暂存箱。"));
                return null;
            });
        });
    }

    private void handleMigratePagesCommand(CommandSender sender, String[] args, String prefix) {
        if (!sender.hasPermission("chuwudai.migrate")) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&cNo permission: chuwudai.migrate"));
            return;
        }

        if (args.length < 2 || !"confirm".equalsIgnoreCase(args[1])) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&eUsage: /chuwudai migratepages confirm"));
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&7This converts legacy bags.data blobs into bag_pages rows. Back up bags.db first."));
            return;
        }

        if (!Chuwudai.getInstance().getDataManager().isSQLiteStorage()) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&cPaged migration only supports sqlite storage."));
            return;
        }

        if (migrationRunning) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&cA page migration is already running."));
            return;
        }

        migrationRunning = true;
        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&aStarted legacy bag page migration asynchronously."));

        Chuwudai plugin = Chuwudai.getInstance();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BagDataManager.MigrationResult result = null;
            Throwable error = null;
            long start = System.nanoTime();

            try {
                result = plugin.getDataManager().migrateLegacyBagsToPagedStorage();
            } catch (Throwable t) {
                error = t;
            }

            BagDataManager.MigrationResult finalResult = result;
            Throwable finalError = error;
            long elapsedNanos = System.nanoTime() - start;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                migrationRunning = false;
                if (finalError != null) {
                    sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&cPage migration failed: " + finalError.getMessage()));
                    return;
                }

                sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&aPage migration complete."));
                sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&7Scanned bags: &f" + finalResult.getScannedBags()
                        + " &7Migrated: &f" + finalResult.getMigratedBags()
                        + " &7Failed: &f" + finalResult.getFailedBags()));
                sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&7Written pages: &f" + finalResult.getWrittenPages()
                        + " &7Items: &f" + finalResult.getMigratedItems()
                        + " &7Time: &e" + String.format("%.3f", elapsedNanos / 1_000_000.0) + " ms"));
            });
        });
    }

    private void handleGetBagCommand(CommandSender sender, String[] args, String prefix) {
        Player target;
        String bagId;
        String style = BundleStyle.getRandomStyleName();

        if (args.length == 2 && sender instanceof Player) {
            target = (Player) sender;
            bagId = args[1];
        } else if (args.length == 3 && sender instanceof Player && Bukkit.getPlayer(args[1]) == null) {
            target = (Player) sender;
            bagId = args[1];
            style = args[2].toLowerCase();
        } else if (args.length >= 3) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c玩家 &e" + args[1] + " &c不在线！"));
                return;
            }
            bagId = args[2];
            if (args.length >= 4) {
                style = args[3].toLowerCase();
            }
        } else {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c用法: &e/chuwudai getbag <储物袋ID> [样式]"));
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c用法: &e/chuwudai getbag <玩家名> <储物袋ID> [样式]"));
            return;
        }

        if (!isValidStyle(style)) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c无效的样式！&7可用样式: &fblue&7, &fbrown&7, &fcyan&7, &fgray&7, &fgreen&7, &flight_blue&7, &flight_gray&7, &flime&7, &fmagenta&7, &forange&7, &fpink&7, &fpurple&7, &fred&7, &fwhite&7, &fyellow"));
            return;
        }

        var shared=Chuwudai.getInstance().getSharedStorage();
        if(shared!=null) {
            var key=new com.horizonkk.chuwudai.storage.MySqlRepository.Key(com.horizonkk.chuwudai.storage.MySqlRepository.Kind.BAG,bagId);
            if(!shared.isReady(key)) { shared.prepare(key).whenComplete((v,e)->shared.onMain(()->{ if(e==null) handleGetBagCommand(sender,args,prefix); else sender.sendMessage("§c读取失败："+e.getMessage()); return null; })); return; }
        }
        com.horizonkk.chuwudai.bag.BagData bagData = bagManager.getBagData(bagId);
        if (bagData == null) {
            sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&c找不到ID为 &e" + bagId + " &c的储物袋数据！"));
            return;
        }

        ItemStack bagItem = com.horizonkk.chuwudai.bag.BagItem.createBag(bagData.getSize(), bagId, style);
        com.horizonkk.chuwudai.bag.BagItem.updateBagLore(bagItem, bagData.getStoneCounts(), bagData);

        Map<Integer, ItemStack> leftover = target.getInventory().addItem(bagItem);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItem(target.getLocation(), bagItem);
        }

        String styleName = getStyleChineseName(style);
        sender.sendMessage(ColorUtils.translateColorCodes(prefix + "&a已给予玩家 &e" + target.getName() + " &a储物袋 &f" + bagId + " &7(" + bagData.getSize() + "格, " + styleName + "样式)"));
        if (!sender.equals(target)) {
            target.sendMessage(ColorUtils.translateColorCodes(prefix + "&a你获得了储物袋 &f" + bagId + " &7(" + bagData.getSize() + "格, " + styleName + "样式)"));
        }
    }

    private boolean isValidStoneType(String stoneType) {
        return stoneType.equals("下品灵石") ||
                stoneType.equals("中品灵石") ||
                stoneType.equals("上品灵石") ||
                stoneType.equals("极品灵石");
    }

    private void testDeductStone(Player player, int bagSlot, String stoneType, int amount) {
        String prefix = ColorUtils.translateColorCodes(
                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f"));

        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&6=== 灵石扣除测试 ==="));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7槽位: &f" + bagSlot));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7灵石类型: &f" + stoneType));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7扣除数量: &f" + amount));

        String apiStoneType = convertStoneTypeToAPIFormat(stoneType);

        if (apiStoneType == null) {
            player.sendMessage(ColorUtils.translateColorCodes(prefix + "&c无效的灵石类型！"));
            return;
        }

        long start = System.nanoTime();
        ChuwudaiAPI.StoneDeductionResult result = ChuwudaiAPI.deductStoneFromSpecificBag(player, bagSlot, apiStoneType, amount);
        long elapsed = System.nanoTime() - start;

        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&6--- 测试结果 ---"));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7扣除结果: " + (result.isSuccess() ? "&a成功" : "&c失败")));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7实际扣除数量: &f" + result.getDeductedAmount()));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7消息: &f" + result.getMessage()));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7总耗时: &e" + String.format("%.3f", elapsed / 1_000_000.0) + " ms"));
        // 分阶段耗时
        Map<String, Long> timings = result.getTimings();
        if (!timings.isEmpty()) {
            player.sendMessage(ColorUtils.translateColorCodes(prefix + "&6--- 分阶段耗时 ---"));
            printTimingLine(player, prefix, timings, "load", "加载数据");
            printTimingLine(player, prefix, timings, "precheck", "预检数量");
            printTimingLine(player, prefix, timings, "scan", "槽位扫描");
            printTimingLine(player, prefix, timings, "save", "数据保存");
            printTimingLine(player, prefix, timings, "lore", "Lore更新");
            printTimingLine(player, prefix, timings, "total", "合计(API)");
        }

        if (result.isSuccess()) {
            player.sendMessage(ColorUtils.translateColorCodes(prefix + "&a✓ 灵石扣除测试通过！"));
        } else {
            player.sendMessage(ColorUtils.translateColorCodes(prefix + "&c✗ 灵石扣除测试失败！"));
        }
    }

    private void testBatchDeduct(Player player, int bagSlot, Map<String, Integer> stones) {
        String prefix = ColorUtils.translateColorCodes(
                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f"));

        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&6=== 批量灵石扣除测试 ==="));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7槽位: &f" + bagSlot));
        StringBuilder typesStr = new StringBuilder();
        for (Map.Entry<String, Integer> entry : stones.entrySet()) {
            if (typesStr.length() > 0) typesStr.append("&7, ");
            typesStr.append("&e").append(getChineseName(entry.getKey())).append("&7x&f").append(entry.getValue());
        }
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7扣除: " + typesStr));

        long start = System.nanoTime();
        ChuwudaiAPI.BatchStoneDeductionResult result = ChuwudaiAPI.deductStonesFromSpecificBag(player, bagSlot, stones);
        long elapsed = System.nanoTime() - start;

        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&6--- 批量扣除结果 ---"));
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7结果: " + (result.isAllSuccess() ? "&a全部成功" : "&e部分成功")));
        StringBuilder deductedStr = new StringBuilder();
        for (Map.Entry<String, Integer> entry : result.getDeductedAmounts().entrySet()) {
            if (deductedStr.length() > 0) deductedStr.append("&7, ");
            deductedStr.append("&e").append(getChineseName(entry.getKey())).append("&7x&f").append(entry.getValue());
        }
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7已扣除: " + deductedStr));
        if (!result.isAllSuccess()) {
            StringBuilder remainingStr = new StringBuilder();
            for (Map.Entry<String, Integer> entry : result.getRemainingAmounts().entrySet()) {
                if (remainingStr.length() > 0) remainingStr.append("&7, ");
                remainingStr.append("&e").append(getChineseName(entry.getKey())).append("&7差&f").append(entry.getValue());
            }
            player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7不足: " + remainingStr));
        }
        player.sendMessage(ColorUtils.translateColorCodes(prefix + "&7总耗时: &e" + String.format("%.3f", elapsed / 1_000_000.0) + " ms"));
        // 分阶段耗时
        Map<String, Long> timings = result.getTimings();
        if (!timings.isEmpty()) {
            player.sendMessage(ColorUtils.translateColorCodes(prefix + "&6--- 分阶段耗时 ---"));
            printTimingLine(player, prefix, timings, "load", "加载数据");
            printTimingLine(player, prefix, timings, "precheck", "预检数量");
            printTimingLine(player, prefix, timings, "scan", "槽位扫描");
            printTimingLine(player, prefix, timings, "save", "数据保存");
            printTimingLine(player, prefix, timings, "lore", "Lore更新");
            printTimingLine(player, prefix, timings, "total", "合计(API)");
        }
    }

    private String getChineseName(String apiStoneType) {
        switch (apiStoneType) {
            case "&f下品灵石": return "下品灵石";
            case "&a中品灵石": return "中品灵石";
            case "&e上品灵石": return "上品灵石";
            case "&c极品灵石": return "极品灵石";
            default: return apiStoneType;
        }
    }

    private String convertStoneTypeToAPIFormat(String chineseType) {
        switch (chineseType) {
            case "下品灵石": return "&f下品灵石";
            case "中品灵石": return "&a中品灵石";
            case "上品灵石": return "&e上品灵石";
            case "极品灵石": return "&c极品灵石";
            default: return null;
        }
    }

    private boolean isValidStyle(String style) {
        return BundleStyle.isValidStyle(style);
    }

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
            sender.sendMessage(ColorUtils.translateColorCodes("&7默认: &f不指定样式时随机选择颜色"));
            sender.sendMessage(ColorUtils.translateColorCodes("&7UI说明: 左侧←上一页 | 中间📖信息 | 右侧→下一页"));
        }
        if (sender.hasPermission("chuwudai.getbag") || sender.hasPermission("chuwudai.give")) {
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai getbag <玩家> <ID> [样式] &7- 按ID补发已有储物袋"));
            sender.sendMessage(ColorUtils.translateColorCodes("&7玩家自用: &f/chuwudai getbag <ID> [样式]"));
        }
        if (sender.hasPermission("chuwudai.open")) {
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai open <ID> &7- 打开背包中携带的指定ID储物袋"));
        }
        if (sender.hasPermission("chuwudai.tempbox")) {
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai tempbox [玩家] &7- 打开暂存箱(54格，重启后保留)"));
        }
        if (sender.hasPermission("chuwudai.migrate")) {
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai migratepages confirm &7- migrate legacy bag blobs into paged storage"));
            sender.sendMessage(ColorUtils.translateColorCodes("&e/chuwudai migratesqlite <check|start> &7- 控制台检查或导入 SQLite 到 MySQL"));
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

    private void printTimingLine(Player player, String prefix, Map<String, Long> timings, String key, String label) {
        Long ns = timings.get(key);
        if (ns != null) {
            double ms = ns / 1_000_000.0;
            player.sendMessage(ColorUtils.translateColorCodes(
                    prefix + "  &7" + label + ": &e" + String.format("%.3f", ms) + " ms &7(&e" + ns + " ns&7)"));
        }
    }
}
