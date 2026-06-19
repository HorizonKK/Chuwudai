package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class BagData {
    
    private final String id;
    private int size;
    private final Map<Integer, ItemStack> items;
    private final Map<String, Integer> stoneCounts;
    
    public BagData(String id, int size) {
        this.id = id;
        this.size = size;
        this.items = new HashMap<>();
        this.stoneCounts = new HashMap<>();
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
        return items.get(slot);
    }
    
    // 设置物品
    public void setItem(int slot, ItemStack item) {
        items.put(slot, item);
        updateStoneCounts();
    }

    /**
     * 设置物品但不触发灵石计数更新（批量操作时使用，完后手动调 updateStoneCounts）
     */
    public void setItemSilent(int slot, ItemStack item) {
        items.put(slot, item);
    }

    // 移除物品
    public void removeItem(int slot) {
        items.remove(slot);
        updateStoneCounts();
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
        stoneCounts.merge(stoneType, -amount, Integer::sum);
    }

    // 更新灵石计数
    public void updateStoneCounts() {
        // 重置计数
        for (String key : stoneCounts.keySet()) {
            stoneCounts.put(key, 0);
        }
        
        // 重新计算
        for (ItemStack item : items.values()) {
            if (item != null) {
                String stoneType = identifyStone(item);
                if (stoneType != null) {
                    int current = stoneCounts.getOrDefault(stoneType, 0);
                    stoneCounts.put(stoneType, current + item.getAmount());
                }
            }
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
        return new HashMap<>(items);
    }

    public void setAllItems(Map<Integer, ItemStack> newItems) {
        this.items.clear();
        this.items.putAll(newItems);
        updateStoneCounts();
    }
}
