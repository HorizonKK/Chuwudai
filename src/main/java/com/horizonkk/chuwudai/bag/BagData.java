package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.hook.NeigeitemsHook;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class BagData {
    
    private final String id;
    private int size;
    private final Map<Integer, ItemStack> items;
    private final Map<String, Integer> stoneCounts;
    private final Map<String, Integer> niItemCounts;
    
    public BagData(String id, int size) {
        this.id = id;
        this.size = size;
        this.items = new HashMap<>();
        this.stoneCounts = new HashMap<>();
        this.niItemCounts = new HashMap<>();
        initializeStoneCounts();
    }
    
    private void initializeStoneCounts() {
        stoneCounts.put("&f下品灵石", 0);
        stoneCounts.put("&a中品灵石", 0);
        stoneCounts.put("&e上品灵石", 0);
        stoneCounts.put("&c极品灵石", 0);
    }
    
    // 获取物品
    public ItemStack getItem(int slot) {
        ItemStack item = items.get(slot);
        return item == null ? null : item.clone();
    }
    
    // 设置物品
    public void setItem(int slot, ItemStack item) {
        ItemStack normalized = normalizeItem(item);
        ItemStack oldItem = items.get(slot);

        if (areSameStack(oldItem, normalized)) {
            return;
        }

        adjustCachedCounts(oldItem, -1);
        if (normalized == null) {
            items.remove(slot);
        } else {
            items.put(slot, normalized.clone());
            adjustCachedCounts(normalized, 1);
        }
    }

    /**
     * 设置物品但不触发灵石计数更新（批量操作时使用，完后手动调 updateStoneCounts）
     */
    public void setItemSilent(int slot, ItemStack item) {
        ItemStack normalized = normalizeItem(item);
        if (normalized == null) {
            items.remove(slot);
        } else {
            items.put(slot, normalized.clone());
        }
    }

    // 移除物品
    public void removeItem(int slot) {
        ItemStack oldItem = items.remove(slot);
        adjustCachedCounts(oldItem, -1);
    }

    /**
     * 移除物品但不触发灵石计数更新（批量操作时使用，完后手动调 updateStoneCounts）
     */
    public void removeItemSilent(int slot) {
        items.remove(slot);
    }

    /**
     * 扣除指定类型的灵石计数（批量扣除时直接维护，O(1) 避免全量扫描）
     */
    public void decrementStoneCount(String stoneType, int amount) {
        adjustCount(stoneCounts, stoneType, -amount);
    }

    public void incrementNiItemCount(String itemId, int amount) {
        adjustCount(niItemCounts, itemId, amount);
    }

    public void decrementNiItemCount(String itemId, int amount) {
        adjustCount(niItemCounts, itemId, -amount);
    }

    // 更新灵石计数
    public void updateStoneCounts() {
        updateCachedCounts();
    }

    public void updateCachedCounts() {
        for (String key : stoneCounts.keySet()) {
            stoneCounts.put(key, 0);
        }
        niItemCounts.clear();

        for (ItemStack item : items.values()) {
            if (item != null) {
                String stoneType = identifyStone(item);
                if (stoneType != null) {
                    adjustCount(stoneCounts, stoneType, item.getAmount());
                }

                String niItemId = identifyNiItem(item);
                if (niItemId != null) {
                    adjustCount(niItemCounts, niItemId, item.getAmount());
                }
            }
        }
    }

    private void adjustCachedCounts(ItemStack item, int direction) {
        if (item == null) {
            return;
        }

        int amountDelta = item.getAmount() * direction;
        String stoneType = identifyStone(item);
        if (stoneType != null) {
            adjustCount(stoneCounts, stoneType, amountDelta);
        }

        String niItemId = identifyNiItem(item);
        if (niItemId != null) {
            adjustCount(niItemCounts, niItemId, amountDelta);
        }
    }

    private void adjustCount(Map<String, Integer> counts, String key, int delta) {
        if (key == null || delta == 0) {
            return;
        }

        int value = counts.getOrDefault(key, 0) + delta;
        if (value <= 0) {
            if (stoneCounts == counts && counts.containsKey(key)) {
                counts.put(key, 0);
            } else {
                counts.remove(key);
            }
        } else {
            counts.put(key, value);
        }
    }
    
    // 识别灵石类型
    private String identifyStone(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            // 直接检查是否是四种灵石之一（包含颜色代码）
            if (displayName.contains("下品灵石") ||
                    displayName.contains("中品灵石") ||
                    displayName.contains("上品灵石") ||
                    displayName.contains("极品灵石")) {

                // 标准化名称，去除多余的空格等
                return standardizeStoneName(displayName);
            }
        }
        return null;
    }

    private String identifyNiItem(ItemStack item) {
        return NeigeitemsHook.getSafeItemId(item);
    }

    private ItemStack normalizeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return null;
        }
        return item;
    }

    private boolean areSameStack(ItemStack first, ItemStack second) {
        if (first == null && second == null) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.equals(second);
    }

    private String standardizeStoneName(String displayName) {
        // 将 § 替换为 &
        String cleanName = displayName.replace("§", "&");
        // 确保格式正确
        if (cleanName.contains("下品灵石")) {
            return "&f下品灵石";
        } else if (cleanName.contains("中品灵石")) {
            return "&a中品灵石";
        } else if (cleanName.contains("上品灵石")) {
            return "&e上品灵石";
        } else if (cleanName.contains("极品灵石")) {
            return "&c极品灵石";
        }
        return null;
    }

    public void debugInfo() {
        Chuwudai.getInstance().getLogger().info("储物袋 " + id + " 状态:");
        Chuwudai.getInstance().getLogger().info("容量: " + size + " 格");
        Chuwudai.getInstance().getLogger().info("物品数量: " + items.size());
        // 打印灵石统计
        for (Map.Entry<String, Integer> entry : stoneCounts.entrySet()) {
            Chuwudai.getInstance().getLogger().info(entry.getKey() + ": " + entry.getValue());
        }
        // 打印所有物品
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            if (entry.getValue() != null) {
                Chuwudai.getInstance().getLogger().info("槽位 " + entry.getKey() + ": " +
                        entry.getValue().getType() + " x" + entry.getValue().getAmount() +
                        " - " + (entry.getValue().hasItemMeta() ?
                        entry.getValue().getItemMeta().getDisplayName() : "无名称"));
            }
        }
    }


    // 获取已使用的槽位数量
    public int getUsedSlots() {
        return items.size();
    }

    // 获取剩余槽位数量
    public int getFreeSlots() {
        return size - getUsedSlots();
    }
    // 获取容量百分比 (0-100)
    public int getCapacityPercentage() {
        if (size == 0) return 0;
        return (int) ((getUsedSlots() * 100.0) / size);
    }
    // 获取进度条字符串
    public String getProgressBar() {
        int percentage = getCapacityPercentage();
        int filledBars = (percentage * 20) / 100; // 总共20个字符长度
        int emptyBars = 20 - filledBars;

        StringBuilder progressBar = new StringBuilder();
        progressBar.append("&a");
        for (int i = 0; i < filledBars; i++) {
            progressBar.append("|");
        }
        progressBar.append("&8");
        for (int i = 0; i < emptyBars; i++) {
            progressBar.append("|");
        }

        return progressBar.toString();
    }
    // 获取灵石计数
    public Map<String, Integer> getStoneCounts() {
        return new HashMap<>(stoneCounts);
    }

    public int getNiItemCount(String itemId) {
        return niItemCounts.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> getNiItemCounts() {
        return new HashMap<>(niItemCounts);
    }
    
    public String getId() { return id; }
    public int getSize() { return size; }

    public void setSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Bag size must be positive");
        }
        this.size = size;
    }

    public void addSize(int additionalSlots) {
        if (additionalSlots <= 0) {
            throw new IllegalArgumentException("Additional slots must be positive");
        }
        setSize(size + additionalSlots);
    }
    public int getTotalPages() {
        // 每页实际存储45格，计算需要多少页
        return (int) Math.ceil((double) size / 45);
    }

    public Map<Integer, ItemStack> getAllItems() {
        Map<Integer, ItemStack> copy = new HashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            ItemStack item = entry.getValue();
            if (item != null) {
                copy.put(entry.getKey(), item.clone());
            }
        }
        return copy;
    }

    public void setAllItems(Map<Integer, ItemStack> newItems) {
        this.items.clear();
        if (newItems != null) {
            for (Map.Entry<Integer, ItemStack> entry : newItems.entrySet()) {
                setItemSilent(entry.getKey(), entry.getValue());
            }
        }
        updateCachedCounts();
    }

    public BagData createSaveSnapshot() {
        BagData snapshot = new BagData(id, size);
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            snapshot.setItemSilent(entry.getKey(), entry.getValue());
        }
        snapshot.updateCachedCounts();
        return snapshot;
    }
}
