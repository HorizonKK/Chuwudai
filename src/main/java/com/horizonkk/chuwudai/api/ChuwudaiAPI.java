package com.horizonkk.chuwudai.api;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.bag.BagManager;
import com.horizonkk.chuwudai.pill.PillData;
import com.horizonkk.chuwudai.pill.PillItem;
import com.horizonkk.chuwudai.pill.PillManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuwudai插件API接口
 * 提供储物袋、丹瓶、灵石管理等核心功能的编程接口
 */
public class ChuwudaiAPI {

    private static final Chuwudai plugin = Chuwudai.getInstance();
    private static final BagManager bagManager = plugin.getBagManager();
    private static final PillManager pillManager = plugin.getPillManager();

    // ==================== 储物袋API ====================

    /**
     * 创建储物袋物品
     * @param size 储物袋大小
     * @param style 储物袋样式
     * @return 储物袋物品
     */
    public static ItemStack createBag(int size, String style) {
        return bagManager.createBagItem(size, style);
    }

    /**
     * 给予玩家储物袋
     * @param player 玩家
     * @param size 储物袋大小
     * @param style 储物袋样式
     */
    public static void giveBagToPlayer(Player player, int size, String style) {
        bagManager.giveBagToPlayer(player, size, style);
    }

    /**
     * 打开储物袋界面
     * @param player 玩家
     * @param bagId 储物袋ID
     */
    public static void openBag(Player player, String bagId) {
        bagManager.openBag(player, bagId);
    }

    /**
     * 获取储物袋数据
     * @param bagId 储物袋ID
     * @return 储物袋数据
     */
    public static BagData getBagData(String bagId) {
        return bagManager.getBagData(bagId);
    }

    /**
     * 保存储物袋数据
     * @param bagId 储物袋ID
     */
    public static void saveBagData(String bagId) {
        bagManager.saveBagData(bagId);
    }

    // ==================== 丹瓶API ====================

    /**
     * 创建丹瓶物品
     * @param size 丹瓶大小
     * @return 丹瓶物品
     */
    public static ItemStack createPill(int size) {
        return pillManager.createPillItem(size);
    }

    /**
     * 给予玩家丹瓶
     * @param player 玩家
     * @param size 丹瓶大小
     */
    public static void givePillToPlayer(Player player, int size) {
        pillManager.givePillToPlayer(player, size);
    }

    /**
     * 打开丹瓶界面
     * @param player 玩家
     * @param pillId 丹瓶ID
     */
    public static void openPill(Player player, String pillId) {
        pillManager.openPill(player, pillId);
    }

    /**
     * 获取丹瓶数据
     * @param pillId 丹瓶ID
     * @return 丹瓶数据
     */
    public static PillData getPillData(String pillId) {
        return pillManager.getPillData(pillId);
    }

    /**
     * 保存丹瓶数据
     * @param pillId 丹瓶ID
     */
    public static void savePillData(String pillId) {
        pillManager.savePillData(pillId);
    }

    // ==================== 灵石管理API ====================

    /**
     * 从玩家背包中扣除指定数量的灵石
     * @param player 玩家
     * @param stoneType 灵石类型（&f下品灵石/&a中品灵石/&e上品灵石/&c极品灵石）
     * @param amount 要扣除的数量
     * @return 实际扣除的数量
     */
    public static int deductStoneFromPlayerInventory(Player player, String stoneType, int amount) {
        if (amount <= 0) return 0;

        int deducted = 0;
        PlayerInventory inventory = player.getInventory();

        // 从背包槽位中查找并扣除灵石（从索引9开始，跳过装备槽位）
        for (int slot = 9; slot < 36 && deducted < amount; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && isMatchingStone(item, stoneType)) {
                int toDeduct = Math.min(item.getAmount(), amount - deducted);
                if (toDeduct > 0) {
                    if (item.getAmount() > toDeduct) {
                        item.setAmount(item.getAmount() - toDeduct);
                        inventory.setItem(slot, item);
                    } else {
                        inventory.setItem(slot, null);
                    }
                    deducted += toDeduct;
                }
            }
        }

        // 如果还不够，检查快捷栏（索引0-8）
        if (deducted < amount) {
            for (int slot = 0; slot < 9 && deducted < amount; slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && isMatchingStone(item, stoneType)) {
                    int toDeduct = Math.min(item.getAmount(), amount - deducted);
                    if (toDeduct > 0) {
                        if (item.getAmount() > toDeduct) {
                            item.setAmount(item.getAmount() - toDeduct);
                            inventory.setItem(slot, item);
                        } else {
                            inventory.setItem(slot, null);
                        }
                        deducted += toDeduct;
                    }
                }
            }
        }

        return deducted;
    }

    /**
     * 查询玩家背包中指定品质的灵石数量（仅背包，不含储物袋）
     * @param player 玩家
     * @param stoneType 灵石类型（&f下品灵石/&a中品灵石/&e上品灵石/&c极品灵石）
     * @return 灵石数量
     */
    public static int countStoneInPlayerInventory(Player player, String stoneType) {
        int count = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && isMatchingStone(item, stoneType)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * 查询玩家背包+指定槽位储物袋中指定品质的灵石总量
     * @param player 玩家
     * @param stoneType 灵石类型（&f下品灵石/&a中品灵石/&e上品灵石/&c极品灵石）
     * @param bagSlot 储物袋所在槽位（0-35）
     * @return 灵石总量
     */
    public static int countStoneInPlayerAndBagSlot(Player player, String stoneType, int bagSlot) {
        int count = countStoneInPlayerInventory(player, stoneType);

        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        if (bagItem != null && BagItem.isBagItem(bagItem)) {
            String bagId = BagItem.getBagId(bagItem);
            BagData bagData = bagManager.getBagData(bagId);
            if (bagData != null) {
                count += bagData.getStoneCounts().getOrDefault(stoneType, 0);
            }
        }

        return count;
    }

    /**
     * 查询玩家背包+所有储物袋中指定品质的灵石总量
     * @param player 玩家
     * @param stoneType 灵石类型（&f下品灵石/&a中品灵石/&e上品灵石/&c极品灵石）
     * @return 灵石总量
     */
    public static int countStoneInPlayerAndAllBags(Player player, String stoneType) {
        int count = countStoneInPlayerInventory(player, stoneType);

        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && BagItem.isBagItem(item)) {
                String bagId = BagItem.getBagId(item);
                BagData bagData = bagManager.getBagData(bagId);
                if (bagData != null) {
                    count += bagData.getStoneCounts().getOrDefault(stoneType, 0);
                }
            }
        }

        return count;
    }

    /**
     * 优先从玩家背包扣除灵石，背包不足时自动从储物袋补充扣除
     * @param player 玩家
     * @param stoneType 灵石类型（&f下品灵石/&a中品灵石/&e上品灵石/&c极品灵石）
     * @param amount 要扣除的数量
     * @return 扣除结果
     */
    public static StoneDeductionResult deductStoneFromPlayerFirst(Player player, String stoneType, int amount) {
        if (amount <= 0) {
            return new StoneDeductionResult(0, "扣除数量必须大于0");
        }

        int fromInventory = deductStoneFromPlayerInventory(player, stoneType, amount);

        if (fromInventory >= amount) {
            return new StoneDeductionResult(fromInventory,
                    "成功从背包扣除" + fromInventory + "个" + getStoneDisplayName(stoneType));
        }

        int remaining = amount - fromInventory;
        StoneDeductionResult bagResult = deductStoneFromAnyBag(player, stoneType, remaining);
        int totalDeducted = fromInventory + bagResult.getDeductedAmount();

        if (totalDeducted >= amount) {
            return new StoneDeductionResult(totalDeducted,
                    "从背包扣除" + fromInventory + "个，从储物袋扣除" + bagResult.getDeductedAmount() + "个" + getStoneDisplayName(stoneType));
        } else if (totalDeducted > 0) {
            return new StoneDeductionResult(totalDeducted,
                    "部分扣除成功：从背包扣除" + fromInventory + "个，从储物袋扣除" + bagResult.getDeductedAmount() + "个" + getStoneDisplayName(stoneType) + "，还差" + (amount - totalDeducted) + "个");
        } else {
            return new StoneDeductionResult(0,
                    "背包和储物袋中都没有足够的" + getStoneDisplayName(stoneType));
        }
    }

    /**
     * 优先从玩家背包批量扣除多种灵石，背包不足时自动从储物袋补充扣除
     * @param player 玩家
     * @param requiredStones 需要扣除的灵石 Map<灵石类型, 数量>
     * @return 批量扣除结果
     */
    public static BatchStoneDeductionResult deductStonesFromPlayerFirst(Player player, Map<String, Integer> requiredStones) {
        if (requiredStones == null || requiredStones.isEmpty()) {
            return new BatchStoneDeductionResult(new HashMap<>(), new HashMap<>());
        }

        Map<String, Integer> remaining = new HashMap<>(requiredStones);
        Map<String, Integer> totalDeducted = new HashMap<>();

        // 阶段1: 从玩家背包扣除
        for (Map.Entry<String, Integer> entry : requiredStones.entrySet()) {
            String stoneType = entry.getKey();
            int needed = entry.getValue();
            int deducted = deductStoneFromPlayerInventory(player, stoneType, needed);
            if (deducted > 0) {
                totalDeducted.put(stoneType, deducted);
                int left = needed - deducted;
                if (left > 0) {
                    remaining.put(stoneType, left);
                } else {
                    remaining.remove(stoneType);
                }
            }
        }

        // 阶段2: 背包不足的部分从储物袋扣除
        if (!remaining.isEmpty()) {
            BatchStoneDeductionResult bagResult = deductStonesFromAnyBag(player, remaining);
            for (Map.Entry<String, Integer> entry : bagResult.getDeductedAmounts().entrySet()) {
                totalDeducted.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            remaining = bagResult.getRemainingAmounts();
        }

        remaining.values().removeIf(v -> v <= 0);
        return new BatchStoneDeductionResult(totalDeducted, remaining);
    }

    /**
     * 从指定槽位的储物袋中扣除灵石
     * @param player 玩家
     * @param bagSlot 储物袋在背包中的槽位（0-35）
     * @param stoneType 灵石类型
     * @param amount 要扣除的数量
     * @return 实际扣除的数量
     */
    public static int deductStoneFromBagSlot(Player player, int bagSlot, String stoneType, int amount) {
        if (amount <= 0) return 0;

        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        if (bagItem == null || !BagItem.isBagItem(bagItem)) return 0;

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = bagManager.getBagData(bagId);
        if (bagData == null) return 0;

        return deductStoneFromBagSlot(player, bagSlot, stoneType, amount, bagData, null);
    }

    /**
     * 从指定槽位的储物袋中扣除灵石（传入已获取的BagData，避免重复查询）
     * @param timings 可选的耗时记录Map，为null时不记录
     */
    private static int deductStoneFromBagSlot(Player player, int bagSlot, String stoneType, int amount, BagData bagData, Map<String, Long> timings) {
        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);

        if (bagItem == null || !BagItem.isBagItem(bagItem)) {
            return 0;
        }

        String bagId = BagItem.getBagId(bagItem);
        int deducted = 0;

        // 阶段3: 槽位扫描扣除
        long tScan = System.nanoTime();
        for (Map.Entry<Integer, ItemStack> entry : bagData.getAllItems().entrySet()) {
            if (deducted >= amount) break;
            int slot = entry.getKey();
            ItemStack item = entry.getValue();
            if (isMatchingStone(item, stoneType)) {
                int toDeduct = Math.min(item.getAmount(), amount - deducted);
                if (toDeduct > 0) {
                    if (item.getAmount() > toDeduct) {
                        item.setAmount(item.getAmount() - toDeduct);
                        bagData.setItemSilent(slot, item);
                    } else {
                        bagData.removeItemSilent(slot);
                    }
                    deducted += toDeduct;
                }
            }
        }
        if (timings != null) timings.put("scan", System.nanoTime() - tScan);

        if (deducted > 0) {
            bagData.decrementStoneCount(stoneType, deducted);

            // 阶段4: 数据保存（异步写盘，内存状态已就绪）
            long tSave = System.nanoTime();
            final String saveBagId = bagId;
            Chuwudai.getInstance().getServer().getScheduler().runTaskAsynchronously(
                Chuwudai.getInstance(), () -> bagManager.saveBagData(saveBagId));
            if (timings != null) timings.put("save", System.nanoTime() - tSave);

            // 阶段5: Lore更新
            long tLore = System.nanoTime();
            BagItem.updateBagLore(bagItem, bagData.getStoneCounts(), bagData);
            inventory.setItem(bagSlot, bagItem);
            if (timings != null) timings.put("lore", System.nanoTime() - tLore);
        }

        return deducted;
    }

    /**
     * 从玩家背包中指定槽位的储物袋扣除灵石（综合方法）
     * @param player 玩家
     * @param bagSlot 储物袋槽位（0-35）
     * @param stoneType 灵石类型
     * @param amount 要扣除的数量
     * @return 扣除结果信息
     */
    public static StoneDeductionResult deductStoneFromSpecificBag(Player player, int bagSlot, String stoneType, int amount) {
        if (amount <= 0) {
            return new StoneDeductionResult(0, "扣除数量必须大于0");
        }

        Map<String, Long> timings = new java.util.LinkedHashMap<>();
        long totalStart = System.nanoTime();

        // 阶段1: 加载储物袋数据
        long t0 = System.nanoTime();
        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);

        if (bagItem == null) {
            return new StoneDeductionResult(0, "指定槽位没有物品");
        }

        if (!BagItem.isBagItem(bagItem)) {
            return new StoneDeductionResult(0, "指定槽位的物品不是储物袋");
        }

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = bagManager.getBagData(bagId);
        timings.put("load", System.nanoTime() - t0);

        if (bagData == null) {
            return new StoneDeductionResult(0, "无法加载储物袋数据");
        }

        // 阶段2: 预检灵石数量
        long t1 = System.nanoTime();
        int available = bagData.getStoneCounts().getOrDefault(stoneType, 0);
        timings.put("precheck", System.nanoTime() - t1);

        if (available < amount) {
            timings.put("total", System.nanoTime() - totalStart);
            return new StoneDeductionResult(0, "储物袋中" + getStoneDisplayName(stoneType) + "不足，需要" + amount + "个，只有" + available + "个", timings);
        }

        // 阶段3-5: 执行扣除（内部计时 scan/save/lore）
        int deducted = deductStoneFromBagSlot(player, bagSlot, stoneType, amount, bagData, timings);
        timings.put("total", System.nanoTime() - totalStart);

        if (deducted == amount) {
            return new StoneDeductionResult(deducted, "成功从储物袋扣除" + deducted + "个" + getStoneDisplayName(stoneType), timings);
        } else {
            return new StoneDeductionResult(deducted, "扣除异常：期望扣除" + amount + "个，实际扣除" + deducted + "个", timings);
        }
    }

    /**
     * 从玩家背包中任意储物袋扣除灵石（自动查找）
     * @param player 玩家
     * @param stoneType 灵石类型
     * @param amount 要扣除的数量
     * @return 扣除结果信息
     */
    public static StoneDeductionResult deductStoneFromAnyBag(Player player, String stoneType, int amount) {
        if (amount <= 0) {
            return new StoneDeductionResult(0, "扣除数量必须大于0");
        }

        PlayerInventory inventory = player.getInventory();
        int totalDeducted = 0;

        // 遍历所有槽位查找储物袋
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && BagItem.isBagItem(item)) {
                String bagId = BagItem.getBagId(item);
                BagData bagData = bagManager.getBagData(bagId);

                if (bagData != null) {
                    int available = bagData.getStoneCounts().getOrDefault(stoneType, 0);

                    if (available > 0) {
                        int toDeduct = Math.min(available, amount - totalDeducted);
                        int actuallyDeducted = deductStoneFromBagSlot(player, slot, stoneType, toDeduct, bagData, null);
                        totalDeducted += actuallyDeducted;

                        if (totalDeducted >= amount) {
                            break;
                        }
                    }
                }
            }
        }

        if (totalDeducted == amount) {
            return new StoneDeductionResult(totalDeducted, "成功从储物袋扣除" + totalDeducted + "个" + getStoneDisplayName(stoneType));
        } else if (totalDeducted > 0) {
            return new StoneDeductionResult(totalDeducted, "部分扣除成功：扣除了" + totalDeducted + "个" + getStoneDisplayName(stoneType) + "，还需要" + (amount - totalDeducted) + "个");
        } else {
            return new StoneDeductionResult(0, "所有储物袋中都没有足够的" + getStoneDisplayName(stoneType));
        }
    }

    // ==================== 批量灵石扣除API ====================

    /**
     * 从玩家背包指定储物袋批量扣除多种灵石
     * 只扫描一次储物袋，保存一次，更新一次Lore
     * @param player 玩家
     * @param bagSlot 储物袋槽位（0-35）
     * @param requiredStones 需要扣除的灵石 Map<灵石类型(&f下品灵石), 数量>
     * @return 批量扣除结果
     */
    public static BatchStoneDeductionResult deductStonesFromSpecificBag(Player player, int bagSlot, Map<String, Integer> requiredStones) {
        if (requiredStones == null || requiredStones.isEmpty()) {
            return new BatchStoneDeductionResult(new HashMap<>(), new HashMap<>());
        }

        Map<String, Long> timings = new java.util.LinkedHashMap<>();
        long totalStart = System.nanoTime();

        // 阶段1: 加载储物袋数据
        long t0 = System.nanoTime();
        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);

        if (bagItem == null) {
            return new BatchStoneDeductionResult(new HashMap<>(), new HashMap<>(requiredStones));
        }
        if (!BagItem.isBagItem(bagItem)) {
            return new BatchStoneDeductionResult(new HashMap<>(), new HashMap<>(requiredStones));
        }

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = bagManager.getBagData(bagId);
        timings.put("load", System.nanoTime() - t0);

        if (bagData == null) {
            return new BatchStoneDeductionResult(new HashMap<>(), new HashMap<>(requiredStones));
        }

        // 阶段2: 预检可用量
        long t1 = System.nanoTime();
        Map<String, Integer> available = bagData.getStoneCounts();
        Map<String, Integer> needed = new HashMap<>();
        for (Map.Entry<String, Integer> entry : requiredStones.entrySet()) {
            int avail = available.getOrDefault(entry.getKey(), 0);
            needed.put(entry.getKey(), Math.min(entry.getValue(), avail));
        }
        timings.put("precheck", System.nanoTime() - t1);

        // 阶段3-5: 单次扫描扣除（内部计时 scan/save/lore）
        Map<String, Integer> deducted = deductStonesFromBag(player, bagSlot, needed, bagData, timings);
        timings.put("total", System.nanoTime() - totalStart);

        return new BatchStoneDeductionResult(deducted,
                computeRemaining(requiredStones, deducted), timings);
    }

    /**
     * 从玩家背包任意储物袋批量扣除多种灵石（自动查找）
     * @param player 玩家
     * @param requiredStones 需要扣除的灵石 Map<灵石类型, 数量>
     * @return 批量扣除结果
     */
    public static BatchStoneDeductionResult deductStonesFromAnyBag(Player player, Map<String, Integer> requiredStones) {
        if (requiredStones == null || requiredStones.isEmpty()) {
            return new BatchStoneDeductionResult(new HashMap<>(), new HashMap<>());
        }

        PlayerInventory inventory = player.getInventory();
        Map<String, Integer> remaining = new HashMap<>(requiredStones);
        Map<String, Integer> totalDeducted = new HashMap<>();

        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !BagItem.isBagItem(item)) continue;

            String bagId = BagItem.getBagId(item);
            BagData bagData = bagManager.getBagData(bagId);
            if (bagData == null) continue;

            // 计算当前储物袋能贡献多少
            Map<String, Integer> available = bagData.getStoneCounts();
            Map<String, Integer> neededFromBag = new HashMap<>();
            for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
                if (entry.getValue() > 0) {
                    int avail = available.getOrDefault(entry.getKey(), 0);
                    if (avail > 0) {
                        neededFromBag.put(entry.getKey(), Math.min(avail, entry.getValue()));
                    }
                }
            }

            if (neededFromBag.isEmpty()) continue;

            Map<String, Integer> deducted = deductStonesFromBag(player, slot, neededFromBag, bagData, null);
            for (Map.Entry<String, Integer> entry : deducted.entrySet()) {
                remaining.merge(entry.getKey(), -entry.getValue(), Integer::sum);
                totalDeducted.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }

            remaining.values().removeIf(v -> v <= 0);
            if (remaining.isEmpty()) break;
        }

        remaining.values().removeIf(v -> v <= 0);
        return new BatchStoneDeductionResult(totalDeducted, remaining);
    }

    /**
     * 核心扣除逻辑：单次扫描储物袋扣除多种灵石
     * @return 实际扣除量 Map<灵石类型, 扣除数量>
     */
    private static Map<String, Integer> deductStonesFromBag(Player player, int bagSlot, Map<String, Integer> needed, BagData bagData, Map<String, Long> timings) {
        Map<String, Integer> remaining = new HashMap<>(needed);
        Map<String, Integer> deducted = new HashMap<>();

        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        if (bagItem == null || !BagItem.isBagItem(bagItem)) return deducted;

        String bagId = BagItem.getBagId(bagItem);

        int remainingCount = needed.values().stream().mapToInt(Integer::intValue).sum();

        // 阶段3: 槽位扫描扣除
        long tScan = System.nanoTime();
        for (Map.Entry<Integer, ItemStack> entry : bagData.getAllItems().entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();

            java.util.Iterator<Map.Entry<String, Integer>> iter = remaining.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, Integer> stoneEntry = iter.next();
                String stoneType = stoneEntry.getKey();
                int need = stoneEntry.getValue();
                if (need <= 0) {
                    iter.remove();
                    continue;
                }
                if (isMatchingStone(item, stoneType)) {
                    int toDeduct = Math.min(item.getAmount(), need);
                    if (item.getAmount() > toDeduct) {
                        item.setAmount(item.getAmount() - toDeduct);
                        bagData.setItemSilent(slot, item);
                    } else {
                        bagData.removeItemSilent(slot);
                    }
                    if (toDeduct == need) {
                        iter.remove();
                    } else {
                        stoneEntry.setValue(need - toDeduct);
                    }
                    deducted.merge(stoneType, toDeduct, Integer::sum);
                    remainingCount -= toDeduct;
                    break;
                }
            }

            if (remainingCount <= 0) {
                break;
            }
        }
        if (timings != null) timings.put("scan", System.nanoTime() - tScan);

        remaining.values().removeIf(v -> v <= 0);

        int totalDeducted = deducted.values().stream().mapToInt(Integer::intValue).sum();
        if (totalDeducted > 0) {
            for (Map.Entry<String, Integer> entry : deducted.entrySet()) {
                bagData.decrementStoneCount(entry.getKey(), entry.getValue());
            }

            // 阶段4: 数据保存（异步写盘，内存状态已就绪）
            long tSave = System.nanoTime();
            final String saveBagId = bagId;
            Chuwudai.getInstance().getServer().getScheduler().runTaskAsynchronously(
                Chuwudai.getInstance(), () -> bagManager.saveBagData(saveBagId));
            if (timings != null) timings.put("save", System.nanoTime() - tSave);

            // 阶段5: Lore更新
            long tLore = System.nanoTime();
            BagItem.updateBagLore(bagItem, bagData.getStoneCounts(), bagData);
            inventory.setItem(bagSlot, bagItem);
            if (timings != null) timings.put("lore", System.nanoTime() - tLore);
        }

        return deducted;
    }

    private static Map<String, Integer> computeRemaining(Map<String, Integer> required, Map<String, Integer> deducted) {
        Map<String, Integer> remaining = new HashMap<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int d = deducted.getOrDefault(entry.getKey(), 0);
            int r = entry.getValue() - d;
            if (r > 0) {
                remaining.put(entry.getKey(), r);
            }
        }
        return remaining;
    }

    // ==================== 工具方法API ====================

    /**
     * 检查物品是否是储物袋
     * @param item 物品
     * @return 是否是储物袋
     */
    public static boolean isBagItem(ItemStack item) {
        return BagItem.isBagItem(item);
    }

    /**
     * 检查物品是否是丹瓶
     * @param item 物品
     * @return 是否是丹瓶
     */
    public static boolean isPillItem(ItemStack item) {
        return PillItem.isPillItem(item);
    }

    /**
     * 从储物袋物品获取ID
     * @param item 储物袋物品
     * @return 储物袋ID
     */
    public static String getBagId(ItemStack item) {
        return BagItem.getBagId(item);
    }

    /**
     * 从丹瓶物品获取ID
     * @param item 丹瓶物品
     * @return 丹瓶ID
     */
    public static String getPillId(ItemStack item) {
        return PillItem.getPillId(item);
    }

    /**
     * 获取储物袋中灵石统计信息
     * @param bagId 储物袋ID
     * @return 灵石统计Map
     */
    public static Map<String, Integer> getBagStoneCounts(String bagId) {
        BagData bagData = getBagData(bagId);
        return bagData != null ? bagData.getStoneCounts() : null;
    }

    /**
     * 获取储物袋已使用槽位数量
     * @param bagId 储物袋ID
     * @return 已使用槽位数量
     */
    public static int getBagUsedSlots(String bagId) {
        BagData bagData = getBagData(bagId);
        return bagData != null ? bagData.getUsedSlots() : 0;
    }

    /**
     * 获取储物袋总容量
     * @param bagId 储物袋ID
     * @return 总容量
     */
    public static int getBagTotalSize(String bagId) {
        BagData bagData = getBagData(bagId);
        return bagData != null ? bagData.getSize() : 0;
    }

    /**
     * 获取丹瓶中丹药名称列表
     * @param pillId 丹瓶ID
     * @return 丹药名称列表
     */
    public static java.util.List<String> getPillNames(String pillId) {
        PillData pillData = getPillData(pillId);
        return pillData != null ? pillData.getPillNames() : new java.util.ArrayList<>();
    }

    /**
     * 获取丹瓶已使用槽位数量
     * @param pillId 丹瓶ID
     * @return 已使用槽位数量
     */
    public static int getPillUsedSlots(String pillId) {
        PillData pillData = getPillData(pillId);
        return pillData != null ? pillData.getUsedSlots() : 0;
    }

    // ==================== 插件信息API ====================

    /**
     * 获取插件实例
     * @return Chuwudai插件实例
     */
    public static Chuwudai getPlugin() {
        return plugin;
    }

    /**
     * 获取插件版本
     * @return 插件版本号
     */
    public static String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * 检查插件是否启用
     * @return 插件启用状态
     */
    public static boolean isEnabled() {
        return plugin.isEnabled();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 检查物品是否匹配指定类型的灵石
     */
    private static boolean isMatchingStone(ItemStack item, String stoneType) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = item.getItemMeta().getDisplayName();
        String cleanName = displayName.replace("§", "&");
        return cleanName.equals(stoneType);
    }

    /**
     * 获取灵石显示名称
     */
    private static String getStoneDisplayName(String stoneType) {
        switch (stoneType) {
            case "&f下品灵石": return "下品灵石";
            case "&a中品灵石": return "中品灵石";
            case "&e上品灵石": return "上品灵石";
            case "&c极品灵石": return "极品灵石";
            default: return stoneType;
        }
    }

    /**
     * 灵石扣除结果类
     */
    public static class StoneDeductionResult {
        private final int deductedAmount;
        private final String message;
        private final boolean success;
        private final Map<String, Long> timings;

        public StoneDeductionResult(int deductedAmount, String message) {
            this(deductedAmount, message, new java.util.LinkedHashMap<>());
        }

        public StoneDeductionResult(int deductedAmount, String message, Map<String, Long> timings) {
            this.deductedAmount = deductedAmount;
            this.message = message;
            this.success = deductedAmount > 0;
            this.timings = new java.util.LinkedHashMap<>(timings);
        }

        public int getDeductedAmount() { return deductedAmount; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
        public Map<String, Long> getTimings() { return new java.util.LinkedHashMap<>(timings); }

        @Override
        public String toString() {
            return "StoneDeductionResult{" +
                    "deductedAmount=" + deductedAmount +
                    ", message='" + message + '\'' +
                    ", success=" + success +
                    '}';
        }
    }

    /**
     * 批量灵石扣除结果类
     */
    public static class BatchStoneDeductionResult {
        private final Map<String, Integer> deductedAmounts;
        private final Map<String, Integer> remainingAmounts;
        private final boolean allSuccess;
        private final String message;
        private final Map<String, Long> timings;

        public BatchStoneDeductionResult(Map<String, Integer> deductedAmounts, Map<String, Integer> remainingAmounts) {
            this(deductedAmounts, remainingAmounts, new java.util.LinkedHashMap<>());
        }

        public BatchStoneDeductionResult(Map<String, Integer> deductedAmounts, Map<String, Integer> remainingAmounts, Map<String, Long> timings) {
            this.deductedAmounts = new HashMap<>(deductedAmounts);
            this.remainingAmounts = new HashMap<>(remainingAmounts);
            this.allSuccess = remainingAmounts.isEmpty();
            this.timings = new java.util.LinkedHashMap<>(timings);

            StringBuilder sb = new StringBuilder();
            if (allSuccess) {
                sb.append("成功扣除: ");
            } else {
                sb.append("部分扣除: ");
            }
            boolean first = true;
            for (Map.Entry<String, Integer> entry : deductedAmounts.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(getStoneDisplayName(entry.getKey())).append("x").append(entry.getValue());
                first = false;
            }
            if (!allSuccess) {
                sb.append(" | 不足: ");
                first = true;
                for (Map.Entry<String, Integer> entry : remainingAmounts.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(getStoneDisplayName(entry.getKey())).append("差").append(entry.getValue());
                    first = false;
                }
            }
            this.message = sb.toString();
        }

        public Map<String, Integer> getDeductedAmounts() { return new HashMap<>(deductedAmounts); }
        public Map<String, Integer> getRemainingAmounts() { return new HashMap<>(remainingAmounts); }
        public boolean isAllSuccess() { return allSuccess; }
        public String getMessage() { return message; }
        public Map<String, Long> getTimings() { return new java.util.LinkedHashMap<>(timings); }

        @Override
        public String toString() {
            return "BatchStoneDeductionResult{" +
                    "deductedAmounts=" + deductedAmounts +
                    ", remainingAmounts=" + remainingAmounts +
                    ", allSuccess=" + allSuccess +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
