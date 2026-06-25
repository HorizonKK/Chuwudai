package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.bag.BagManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import pers.neige.neigeitems.item.ItemInfo;
import pers.neige.neigeitems.item.ItemPack;
import pers.neige.neigeitems.manager.ItemManager;
import pers.neige.neigeitems.manager.ItemPackManager;
import pers.neige.neigeitems.utils.ItemUtils;

import java.util.HashMap;
import java.util.Map;

public class NeigeitemsHook {

    private static Boolean available;

    /**
     * 检查NeigeItems插件是否可用
     * @return 是否可用
     */
    public static boolean isNeigeItemsAvailable() {
        if (available != null) {
            return available;
        }

        try {
            Class.forName("pers.neige.neigeitems.manager.ItemManager");
            available = ItemManager.INSTANCE != null;
            return available;
        } catch (Exception e) {
            available = false;
            return false;
        }
    }

    /**
     * 获取物品
     * @param itemId 物品ID
     * @param player 玩家对象
     * @param data 数据参数
     * @return ItemStack 物品堆
     */
    public static ItemStack getItemStack(String itemId, Player player, Map<String, Object> data) {
        if (!isNeigeItemsAvailable()) {
            return null;
        }

        Map<String, String> stringData = new HashMap<>();
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                stringData.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return ItemManager.INSTANCE.getItemStack(itemId, player, stringData);
    }

    /**
     * 获取物品（简化版本）
     * @param itemId 物品ID
     * @param player 玩家对象
     * @return ItemStack 物品堆
     */
    public static ItemStack getItemStack(String itemId, Player player) {
        if (!isNeigeItemsAvailable()) {
            return null;
        }
        return ItemManager.INSTANCE.getItemStack(itemId, player, new HashMap<>());
    }

    /**
     * 获取物品包
     * @param packId 物品包ID
     * @return ItemPack 物品包对象
     */
    public static ItemPack getItemPack(String packId) {
        if (!isNeigeItemsAvailable()) {
            return null;
        }
        return ItemPackManager.INSTANCE.getItemPack(packId);
    }

    /**
     * 获取NI物品ID
     * @param itemStack 物品堆
     * @return String 物品ID
     */
    public static String getItemId(ItemStack itemStack) {
        if (!isNeigeItemsAvailable()) {
            return null;
        }
        return ItemUtils.getItemId(itemStack);
    }

    /**
     * 获取NI物品ID及物品节点信息
     * @param itemStack 物品堆
     * @return ItemInfo 物品信息对象
     */
    public static ItemInfo getItemInfo(ItemStack itemStack) {
        if (!isNeigeItemsAvailable()) {
            return null;
        }
        return ItemUtils.isNiItem(itemStack);
    }

    /**
     * 检查物品是否为NI物品
     * @param itemStack 物品堆
     * @return boolean 是否为NI物品
     */
    public static boolean isNiItem(ItemStack itemStack) {
        if (!isNeigeItemsAvailable()) {
            return false;
        }
        return ItemUtils.isNiItem(itemStack) != null;
    }

    /**
     * 安全获取物品ID（包含空值检查）
     * @param itemStack 物品堆
     * @return String 物品ID，如果不存在则返回null
     */
    public static String getSafeItemId(ItemStack itemStack) {
        if (itemStack == null) return null;
        return getItemId(itemStack);
    }

    /**
     * 安全获取物品信息（包含空值检查）
     * @param itemStack 物品堆
     * @return ItemInfo 物品信息对象，如果不存在则返回null
     */
    public static ItemInfo getSafeItemInfo(ItemStack itemStack) {
        if (itemStack == null) return null;
        return getItemInfo(itemStack);
    }

    /**
     * 查询指定槽位储物袋内指定NI物品数量
     * 仅读取储物袋内ItemStack的NI物品ID，并与传入itemId做字符串比较
     * @param player 玩家
     * @param bagSlot 储物袋在玩家背包中的槽位（0-35）
     * @param itemId NI物品ID
     * @return 指定NI物品数量
     */
    public static int countNiItemInBagSlot(Player player, int bagSlot, String itemId) {
        if (itemId == null || itemId.isEmpty()) return 0;
        if (!isNeigeItemsAvailable()) return 0;

        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        if (bagItem == null || !BagItem.isBagItem(bagItem)) return 0;

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = getBagManager().getBagData(bagId);
        if (bagData == null) return 0;

        return bagData.getNiItemCount(itemId);
    }

    /**
     * 批量查询指定槽位储物袋内多种NI物品数量
     * 只扫描一次该储物袋
     * @param player 玩家
     * @param bagSlot 储物袋在玩家背包中的槽位（0-35）
     * @param itemIds NI物品ID集合
     * @return Map<NI物品ID, 数量>
     */
    public static Map<String, Integer> countNiItemsInBagSlot(Player player, int bagSlot, java.util.Collection<String> itemIds) {
        Map<String, Integer> counts = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) return counts;
        if (!isNeigeItemsAvailable()) return counts;

        java.util.Set<String> targets = new java.util.HashSet<>();
        for (String itemId : itemIds) {
            if (itemId != null && !itemId.isEmpty()) {
                targets.add(itemId);
                counts.put(itemId, 0);
            }
        }
        if (targets.isEmpty()) return counts;

        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        if (bagItem == null || !BagItem.isBagItem(bagItem)) return counts;

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = getBagManager().getBagData(bagId);
        if (bagData == null) return counts;

        for (String target : targets) {
            counts.put(target, bagData.getNiItemCount(target));
        }
        return counts;
    }

    /**
     * 从指定槽位的储物袋中扣除指定NI物品
     * 仅读取储物袋内ItemStack的NI物品ID，并与传入itemId做字符串比较
     * @param player 玩家
     * @param bagSlot 储物袋在玩家背包中的槽位（0-35）
     * @param itemId NI物品ID
     * @param amount 要扣除的数量
     * @return 实际扣除数量
     */
    public static int deductNiItemFromBagSlot(Player player, int bagSlot, String itemId, int amount) {
        if (amount <= 0 || itemId == null || itemId.isEmpty()) return 0;
        if (!isNeigeItemsAvailable()) return 0;

        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        if (bagItem == null || !BagItem.isBagItem(bagItem)) return 0;

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = getBagManager().getBagData(bagId);
        if (bagData == null) return 0;

        Map<String, Integer> needed = new HashMap<>();
        needed.put(itemId, amount);
        return deductNiItemsFromBag(player, bagSlot, needed, bagData, null).getOrDefault(itemId, 0);
    }

    /**
     * 从指定槽位的储物袋中扣除指定NI物品，并返回结果信息
     * @param player 玩家
     * @param bagSlot 储物袋在玩家背包中的槽位（0-35）
     * @param itemId NI物品ID
     * @param amount 要扣除的数量
     * @return 扣除结果
     */
    public static NiItemDeductionResult deductNiItemFromSpecificBag(Player player, int bagSlot, String itemId, int amount) {
        if (amount <= 0) {
            return new NiItemDeductionResult(itemId, 0, amount, "扣除数量必须大于0");
        }
        if (itemId == null || itemId.isEmpty()) {
            return new NiItemDeductionResult(itemId, 0, amount, "NI物品ID不能为空");
        }
        if (!isNeigeItemsAvailable()) {
            return new NiItemDeductionResult(itemId, 0, amount, "NeigeItems不可用");
        }

        Map<String, Long> timings = new java.util.LinkedHashMap<>();
        long totalStart = System.nanoTime();

        long t0 = System.nanoTime();
        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        timings.put("load", System.nanoTime() - t0);

        if (bagItem == null) {
            timings.put("total", System.nanoTime() - totalStart);
            return new NiItemDeductionResult(itemId, 0, amount, "指定槽位没有物品", timings);
        }
        if (!BagItem.isBagItem(bagItem)) {
            timings.put("total", System.nanoTime() - totalStart);
            return new NiItemDeductionResult(itemId, 0, amount, "指定槽位的物品不是储物袋", timings);
        }

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = getBagManager().getBagData(bagId);
        if (bagData == null) {
            timings.put("total", System.nanoTime() - totalStart);
            return new NiItemDeductionResult(itemId, 0, amount, "无法加载储物袋数据", timings);
        }

        Map<String, Integer> needed = new HashMap<>();
        needed.put(itemId, amount);
        int deducted = deductNiItemsFromBag(player, bagSlot, needed, bagData, timings).getOrDefault(itemId, 0);
        timings.put("total", System.nanoTime() - totalStart);

        int remaining = Math.max(0, amount - deducted);
        if (remaining == 0) {
            return new NiItemDeductionResult(itemId, deducted, 0, "成功从储物袋扣除" + deducted + "个NI物品 " + itemId, timings);
        }
        if (deducted > 0) {
            return new NiItemDeductionResult(itemId, deducted, remaining, "部分扣除成功：扣除了" + deducted + "个NI物品 " + itemId + "，还差" + remaining + "个", timings);
        }
        return new NiItemDeductionResult(itemId, 0, amount, "储物袋中没有足够的NI物品 " + itemId, timings);
    }

    /**
     * 从玩家背包指定储物袋批量扣除多种NI物品
     * 只扫描一次该储物袋；每个物品只读取一次NI物品ID
     * @param player 玩家
     * @param bagSlot 储物袋在玩家背包中的槽位（0-35）
     * @param requiredItems 需要扣除的NI物品 Map<NI物品ID, 数量>
     * @return 批量扣除结果
     */
    public static BatchNiItemDeductionResult deductNiItemsFromSpecificBag(Player player, int bagSlot, Map<String, Integer> requiredItems) {
        Map<String, Integer> needed = sanitizeRequiredAmounts(requiredItems);
        if (needed.isEmpty()) {
            return new BatchNiItemDeductionResult(new HashMap<>(), new HashMap<>());
        }
        if (!isNeigeItemsAvailable()) {
            return new BatchNiItemDeductionResult(new HashMap<>(), needed, "NeigeItems不可用");
        }

        Map<String, Long> timings = new java.util.LinkedHashMap<>();
        long totalStart = System.nanoTime();

        long t0 = System.nanoTime();
        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        timings.put("load", System.nanoTime() - t0);

        if (bagItem == null || !BagItem.isBagItem(bagItem)) {
            timings.put("total", System.nanoTime() - totalStart);
            return new BatchNiItemDeductionResult(new HashMap<>(), needed, "指定槽位不是储物袋", timings);
        }

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = getBagManager().getBagData(bagId);
        if (bagData == null) {
            timings.put("total", System.nanoTime() - totalStart);
            return new BatchNiItemDeductionResult(new HashMap<>(), needed, "无法加载储物袋数据", timings);
        }

        Map<String, Integer> deducted = deductNiItemsFromBag(player, bagSlot, needed, bagData, timings);
        timings.put("total", System.nanoTime() - totalStart);
        return new BatchNiItemDeductionResult(deducted, computeRemaining(needed, deducted), timings);
    }

    private static Map<String, Integer> deductNiItemsFromBag(Player player, int bagSlot, Map<String, Integer> needed, BagData bagData, Map<String, Long> timings) {
        Map<String, Integer> remaining = new HashMap<>(needed);
        Map<String, Integer> deducted = new HashMap<>();
        java.util.List<DeductionPlanEntry> plan = new java.util.ArrayList<>();

        PlayerInventory inventory = player.getInventory();
        ItemStack bagItem = inventory.getItem(bagSlot);
        if (bagItem == null || !BagItem.isBagItem(bagItem)) return deducted;

        String bagId = BagItem.getBagId(bagItem);
        int remainingCount = remaining.values().stream().mapToInt(Integer::intValue).sum();

        long tPrecheck = System.nanoTime();
        for (Map.Entry<String, Integer> entry : needed.entrySet()) {
            if (bagData.getNiItemCount(entry.getKey()) < entry.getValue()) {
                if (timings != null) timings.put("precheck", System.nanoTime() - tPrecheck);
                return new HashMap<>();
            }
        }
        if (timings != null) timings.put("precheck", System.nanoTime() - tPrecheck);

        long tScan = System.nanoTime();
        for (Map.Entry<Integer, ItemStack> entry : bagData.getAllItems().entrySet()) {
            if (remainingCount <= 0) break;

            int slot = entry.getKey();
            ItemStack item = entry.getValue();
            String currentItemId = getSafeItemId(item);
            if (currentItemId == null) continue;

            Integer need = remaining.get(currentItemId);
            if (need == null || need <= 0) continue;

            int toDeduct = Math.min(item.getAmount(), need);
            if (toDeduct <= 0) continue;

            plan.add(new DeductionPlanEntry(slot, item, toDeduct));
            deducted.merge(currentItemId, toDeduct, Integer::sum);

            int left = need - toDeduct;
            if (left > 0) {
                remaining.put(currentItemId, left);
            } else {
                remaining.remove(currentItemId);
            }
            remainingCount -= toDeduct;
        }
        if (timings != null) timings.put("scan", System.nanoTime() - tScan);

        if (!remaining.isEmpty()) {
            return new HashMap<>();
        }

        int totalDeducted = deducted.values().stream().mapToInt(Integer::intValue).sum();
        if (totalDeducted > 0) {
            long tApply = System.nanoTime();
            for (DeductionPlanEntry entry : plan) {
                ItemStack item = entry.item.clone();
                if (item.getAmount() > entry.amount) {
                    item.setAmount(item.getAmount() - entry.amount);
                    bagData.setItem(entry.slot, item);
                } else {
                    bagData.removeItem(entry.slot);
                }
            }
            if (timings != null) timings.put("apply", System.nanoTime() - tApply);

            long tSave = System.nanoTime();
            final String saveBagId = bagId;
            getBagManager().queueBagSave(saveBagId);
            if (timings != null) timings.put("save", System.nanoTime() - tSave);

            long tLore = System.nanoTime();
            BagItem.updateBagLore(bagItem, bagData.getStoneCounts(), bagData);
            inventory.setItem(bagSlot, bagItem);
            if (timings != null) timings.put("lore", System.nanoTime() - tLore);
        }

        return deducted;
    }

    private static class DeductionPlanEntry {
        private final int slot;
        private final ItemStack item;
        private final int amount;

        private DeductionPlanEntry(int slot, ItemStack item, int amount) {
            this.slot = slot;
            this.item = item.clone();
            this.amount = amount;
        }
    }

    private static BagManager getBagManager() {
        return Chuwudai.getInstance().getBagManager();
    }

    private static Map<String, Integer> sanitizeRequiredAmounts(Map<String, Integer> requiredItems) {
        Map<String, Integer> needed = new HashMap<>();
        if (requiredItems == null || requiredItems.isEmpty()) {
            return needed;
        }

        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            String itemId = entry.getKey();
            Integer amount = entry.getValue();
            if (itemId == null || itemId.isEmpty() || amount == null || amount <= 0) {
                continue;
            }
            needed.merge(itemId, amount, Integer::sum);
        }
        return needed;
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

    /**
     * NI物品扣除结果类
     */
    public static class NiItemDeductionResult {
        private final String itemId;
        private final int deductedAmount;
        private final int remainingAmount;
        private final String message;
        private final boolean success;
        private final boolean allSuccess;
        private final Map<String, Long> timings;

        public NiItemDeductionResult(String itemId, int deductedAmount, int remainingAmount, String message) {
            this(itemId, deductedAmount, remainingAmount, message, new java.util.LinkedHashMap<>());
        }

        public NiItemDeductionResult(String itemId, int deductedAmount, int remainingAmount, String message, Map<String, Long> timings) {
            this.itemId = itemId;
            this.deductedAmount = deductedAmount;
            this.remainingAmount = Math.max(0, remainingAmount);
            this.message = message;
            this.success = deductedAmount > 0;
            this.allSuccess = this.remainingAmount == 0;
            this.timings = new java.util.LinkedHashMap<>(timings);
        }

        public String getItemId() { return itemId; }
        public int getDeductedAmount() { return deductedAmount; }
        public int getRemainingAmount() { return remainingAmount; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
        public boolean isAllSuccess() { return allSuccess; }
        public Map<String, Long> getTimings() { return new java.util.LinkedHashMap<>(timings); }

        @Override
        public String toString() {
            return "NiItemDeductionResult{" +
                    "itemId='" + itemId + '\'' +
                    ", deductedAmount=" + deductedAmount +
                    ", remainingAmount=" + remainingAmount +
                    ", success=" + success +
                    ", allSuccess=" + allSuccess +
                    ", message='" + message + '\'' +
                    '}';
        }
    }

    /**
     * 批量NI物品扣除结果类
     */
    public static class BatchNiItemDeductionResult {
        private final Map<String, Integer> deductedAmounts;
        private final Map<String, Integer> remainingAmounts;
        private final boolean allSuccess;
        private final String message;
        private final Map<String, Long> timings;

        public BatchNiItemDeductionResult(Map<String, Integer> deductedAmounts, Map<String, Integer> remainingAmounts) {
            this(deductedAmounts, remainingAmounts, null, new java.util.LinkedHashMap<>());
        }

        public BatchNiItemDeductionResult(Map<String, Integer> deductedAmounts, Map<String, Integer> remainingAmounts, Map<String, Long> timings) {
            this(deductedAmounts, remainingAmounts, null, timings);
        }

        public BatchNiItemDeductionResult(Map<String, Integer> deductedAmounts, Map<String, Integer> remainingAmounts, String message) {
            this(deductedAmounts, remainingAmounts, message, new java.util.LinkedHashMap<>());
        }

        public BatchNiItemDeductionResult(Map<String, Integer> deductedAmounts, Map<String, Integer> remainingAmounts, String message, Map<String, Long> timings) {
            this.deductedAmounts = new HashMap<>(deductedAmounts);
            this.remainingAmounts = new HashMap<>(remainingAmounts);
            this.allSuccess = this.remainingAmounts.isEmpty();
            this.timings = new java.util.LinkedHashMap<>(timings);
            this.message = message != null ? message : buildMessage(this.deductedAmounts, this.remainingAmounts, this.allSuccess);
        }

        private static String buildMessage(Map<String, Integer> deductedAmounts, Map<String, Integer> remainingAmounts, boolean allSuccess) {
            StringBuilder sb = new StringBuilder();
            sb.append(allSuccess ? "成功扣除NI物品: " : "部分扣除NI物品: ");

            boolean first = true;
            for (Map.Entry<String, Integer> entry : deductedAmounts.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append("x").append(entry.getValue());
                first = false;
            }
            if (deductedAmounts.isEmpty()) {
                sb.append("无");
            }

            if (!allSuccess) {
                sb.append(" | 不足: ");
                first = true;
                for (Map.Entry<String, Integer> entry : remainingAmounts.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(entry.getKey()).append("差").append(entry.getValue());
                    first = false;
                }
            }
            return sb.toString();
        }

        public Map<String, Integer> getDeductedAmounts() { return new HashMap<>(deductedAmounts); }
        public Map<String, Integer> getRemainingAmounts() { return new HashMap<>(remainingAmounts); }
        public boolean isAllSuccess() { return allSuccess; }
        public String getMessage() { return message; }
        public Map<String, Long> getTimings() { return new java.util.LinkedHashMap<>(timings); }

        @Override
        public String toString() {
            return "BatchNiItemDeductionResult{" +
                    "deductedAmounts=" + deductedAmounts +
                    ", remainingAmounts=" + remainingAmounts +
                    ", allSuccess=" + allSuccess +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
