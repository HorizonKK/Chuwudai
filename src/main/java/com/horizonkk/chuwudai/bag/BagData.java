package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class BagData {
    
    private final String id;
    private final int size;
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
    
    // 移除物品
    public void removeItem(int slot) {
        items.remove(slot);
        updateStoneCounts();
    }
    
    // 更新灵石计数
    private void updateStoneCounts() {
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
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            // 检查是否是配置中的灵石
            Map<?, ?> stones = Chuwudai.getInstance().getConfig().getConfigurationSection("spirit-stones").getValues(false);
            for (Object key : stones.keySet()) {
                if (displayName.equals(key)) {
                    return (String) key;
                }
            }
        }
        return null;
    }
    
    // 获取灵石计数
    public Map<String, Integer> getStoneCounts() {
        return new HashMap<>(stoneCounts);
    }
    
    public String getId() { return id; }
    public int getSize() { return size; }
    public int getTotalPages() { return (int) Math.ceil(size / 54.0); }
}