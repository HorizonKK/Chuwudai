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

        if (bagItem == null || !BagItem.isBagItem(bagItem)) {
            return 0;
        }

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = bagManager.getBagData(bagId);

        if (bagData == null) {
            return 0;
        }

        int deducted = 0;
        // 按顺序遍历储物袋槽位，扣除灵石（参照丹瓶扣除丹药的方式）
        for (int slot = 0; slot < bagData.getSize() && deducted < amount; slot++) {
            ItemStack item = bagData.getItem(slot);
            if (item != null && isMatchingStone(item, stoneType)) {
                int toDeduct = Math.min(item.getAmount(), amount - deducted);
                if (toDeduct > 0) {
                    if (item.getAmount() > toDeduct) {
                        // 减少数量
                        item.setAmount(item.getAmount() - toDeduct);
                        bagData.setItem(slot, item);
                    } else {
                        // 完全移除
                        bagData.removeItem(slot);
                    }
                    deducted += toDeduct;
                }
            }
        }

        // 保存储物袋数据
        if (deducted > 0) {
            bagManager.saveBagData(bagId);
            // 更新储物袋物品的Lore
            BagItem.updateBagLore(bagItem, bagData.getStoneCounts());
            inventory.setItem(bagSlot, bagItem);
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

        if (bagData == null) {
            return new StoneDeductionResult(0, "无法加载储物袋数据");
        }

        // 检查储物袋中是否有足够的灵石
        int available = bagData.getStoneCounts().getOrDefault(stoneType, 0);

        if (available < amount) {
            return new StoneDeductionResult(0, "储物袋中" + getStoneDisplayName(stoneType) + "不足，需要" + amount + "个，只有" + available + "个");
        }

        // 执行扣除
        int deducted = deductStoneFromBagSlot(player, bagSlot, stoneType, amount);

        if (deducted == amount) {
            return new StoneDeductionResult(deducted, "成功从储物袋扣除" + deducted + "个" + getStoneDisplayName(stoneType));
        } else {
            return new StoneDeductionResult(deducted, "扣除异常：期望扣除" + amount + "个，实际扣除" + deducted + "个");
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
                        int actuallyDeducted = deductStoneFromBagSlot(player, slot, stoneType, toDeduct);
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

        public StoneDeductionResult(int deductedAmount, String message) {
            this.deductedAmount = deductedAmount;
            this.message = message;
            this.success = deductedAmount > 0;
        }

        public int getDeductedAmount() {
            return deductedAmount;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return success;
        }

        @Override
        public String toString() {
            return "StoneDeductionResult{" +
                    "deductedAmount=" + deductedAmount +
                    ", message='" + message + '\'' +
                    ", success=" + success +
                    '}';
        }
    }
}
