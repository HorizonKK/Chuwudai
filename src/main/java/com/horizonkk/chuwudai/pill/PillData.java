package com.horizonkk.chuwudai.pill;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PillData {
    
    private final String id;
    private final int size;
    private final Map<Integer, ItemStack> items;
    private final List<String> pillNames; // 存储前9格丹药的名字
    
    public PillData(String id, int size) {
        this.id = id;
        this.size = size;
        this.items = new HashMap<>();
        this.pillNames = new ArrayList<>();
        updatePillNames();
    }
    
    // 获取物品
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }
    
    // 设置物品
    public void setItem(int slot, ItemStack item) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("槽位超出范围: " + slot);
        }
        
        if (item != null && !isValidPillItem(item)) {
            throw new IllegalArgumentException("物品不是丹药类型: " + item.getType());
        }
        
        items.put(slot, item);
        updatePillNames();
    }
    
    // 移除物品
    public void removeItem(int slot) {
        items.remove(slot);
        updatePillNames();
    }
    
    // 检查物品是否是丹药
    public boolean isValidPillItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        
        String displayName = item.getItemMeta().getDisplayName();
        return displayName.contains("丹药") || displayName.contains("丹");
    }
    
    // 更新丹药名称列表（前9格）
    private void updatePillNames() {
        pillNames.clear();
        for (int i = 0; i < Math.min(9, size); i++) {
            ItemStack item = items.get(i);
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String displayName = item.getItemMeta().getDisplayName();
                pillNames.add(displayName);
            } else {
                pillNames.add(null);
            }
        }
    }
    
    // 获取前9格的丹药名称
    public List<String> getPillNames() {
        return new ArrayList<>(pillNames);
    }
    
    // 获取已使用的槽位数量
    public int getUsedSlots() {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (items.get(i) != null) {
                count++;
            }
        }
        return count;
    }
    
    // 获取容量百分比 (0-100)
    public int getCapacityPercentage() {
        if (size == 0) return 0;
        return (int) ((getUsedSlots() * 100.0) / size);
    }
    
    public String getId() { 
        return id; 
    }
    
    public int getSize() { 
        return size; 
    }
    
    public Map<Integer, ItemStack> getAllItems() {
        return new HashMap<>(items);
    }
    
    public void setAllItems(Map<Integer, ItemStack> newItems) {
        this.items.clear();
        for (Map.Entry<Integer, ItemStack> entry : newItems.entrySet()) {
            Integer slot = entry.getKey();
            ItemStack item = entry.getValue();
            
            if (slot >= 0 && slot < size) {
                if (item != null && !isValidPillItem(item)) {
                    Chuwudai.getInstance().getLogger().warning("尝试添加非丹药物品到丹瓶: " + item.getType());
                    continue;
                }
                this.items.put(slot, item);
            }
        }
        updatePillNames();
    }
}
