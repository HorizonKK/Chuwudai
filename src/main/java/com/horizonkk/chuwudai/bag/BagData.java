package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.hook.NeigeitemsHook;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BagData {
    private Runnable storageCheck, storageChanged;
    public void bindStorage(Runnable check, Runnable changed) { this.storageCheck=check; this.storageChanged=changed; }
    private void beforeMutation() { if(storageCheck!=null) { storageCheck.run(); storageChanged.run(); } }

    public static final int STORAGE_PAGE_SIZE = 45;
    
    private final String id;
    private int size;
    private final Map<Integer, ItemStack> items;
    private final Map<String, Integer> stoneCounts;
    private final Map<String, Integer> niItemCounts;
    private final Set<Integer> dirtyPages;
    private boolean legacyStorage;
    private final Map<Integer,Long> pageVersions=new HashMap<>();
    private long nextPageVersion;
    
    public BagData(String id, int size) {
        this.id = id;
        this.size = size;
        this.items = new HashMap<>();
        this.stoneCounts = new HashMap<>();
        this.niItemCounts = new HashMap<>();
        this.dirtyPages = new HashSet<>();
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
        beforeMutation();
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
        markSlotDirty(slot);
    }

    /**
     * 设置物品但不触发灵石计数更新（批量操作时使用，完后手动调 updateStoneCounts）
     */
    public void setItemSilent(int slot, ItemStack item) {
        beforeMutation();
        ItemStack normalized = normalizeItem(item);
        if (normalized == null) {
            items.remove(slot);
        } else {
            items.put(slot, normalized.clone());
        }
    }

    // 移除物品
    public void removeItem(int slot) {
        beforeMutation();
        ItemStack oldItem = items.remove(slot);
        adjustCachedCounts(oldItem, -1);
        if (oldItem != null) {
            markSlotDirty(slot);
        }
    }

    /**
     * 移除物品但不触发灵石计数更新（批量操作时使用，完后手动调 updateStoneCounts）
     */
    public void removeItemSilent(int slot) {
        beforeMutation();
        items.remove(slot);
    }

    /**
     * 扣除指定类型的灵石计数（批量扣除时直接维护，O(1) 避免全量扫描）
     */
    public void decrementStoneCount(String stoneType, int amount) {
        beforeMutation();
        adjustCount(stoneCounts, stoneType, -amount);
    }

    public void incrementNiItemCount(String itemId, int amount) {
        beforeMutation();
        adjustCount(niItemCounts, itemId, amount);
    }

    public void decrementNiItemCount(String itemId, int amount) {
        beforeMutation();
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
        beforeMutation();
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
        return (int) Math.ceil((double) size / STORAGE_PAGE_SIZE);
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
        beforeMutation();
        this.items.clear();
        if (newItems != null) {
            for (Map.Entry<Integer, ItemStack> entry : newItems.entrySet()) {
                setItemSilent(entry.getKey(), entry.getValue());
            }
        }
        updateCachedCounts();
        dirtyPages.clear();
    }

    public BagData createSaveSnapshot() {
        BagData snapshot = new BagData(id, size);
        Set<Integer> pagesToSnapshot = legacyStorage ? getAllPageIndexes() : new HashSet<>(dirtyPages);

        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            int slot = entry.getKey();
            int page = getPageForSlot(slot);
            if (pagesToSnapshot.contains(page)) {
                snapshot.setItemSilent(slot, entry.getValue());
            }
        }
        snapshot.dirtyPages.addAll(pagesToSnapshot);
        snapshot.legacyStorage = legacyStorage;
        snapshot.pageVersions.putAll(pageVersions);
        return snapshot;
    }

    public BagData createFullSaveSnapshot() {
        BagData snapshot = new BagData(id, size);
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            snapshot.setItemSilent(entry.getKey(), entry.getValue());
        }
        snapshot.dirtyPages.addAll(getAllPageIndexes());
        snapshot.legacyStorage = legacyStorage;
        snapshot.pageVersions.putAll(pageVersions);
        return snapshot;
    }

    public void acknowledgeSnapshot(BagData snapshot) {
        for(int page:snapshot.dirtyPages) if(java.util.Objects.equals(pageVersions.get(page),snapshot.pageVersions.get(page))) dirtyPages.remove(page);
        if(snapshot.legacyStorage) legacyStorage=false;
    }
    public Set<Integer> getDirtyPages() {
        return new HashSet<>(dirtyPages);
    }

    public boolean hasDirtyPages() {
        return !dirtyPages.isEmpty();
    }

    public boolean isLegacyStorage() {
        return legacyStorage;
    }

    public void markLegacyStorage() {
        this.legacyStorage = true;
    }

    public Set<Integer> getAllPageIndexes() {
        int totalPages = getTotalPages();
        if (totalPages <= 0) {
            return Collections.emptySet();
        }
        Set<Integer> pages = new HashSet<>();
        for (int page = 0; page < totalPages; page++) {
            pages.add(page);
        }
        return pages;
    }

    public Map<Integer, ItemStack> getItemsInPage(int page) {
        Map<Integer, ItemStack> pageItems = new HashMap<>();
        if (page < 0) {
            return pageItems;
        }

        int startSlot = page * STORAGE_PAGE_SIZE;
        int endSlot = Math.min(size, startSlot + STORAGE_PAGE_SIZE);
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            int slot = entry.getKey();
            if (slot < startSlot || slot >= endSlot) {
                continue;
            }
            ItemStack item = entry.getValue();
            if (item != null) {
                pageItems.put(slot, item.clone());
            }
        }
        return pageItems;
    }

    private void markSlotDirty(int slot) {
        if (slot >= 0) {
            dirtyPages.add(getPageForSlot(slot));
            pageVersions.put(getPageForSlot(slot),++nextPageVersion);
        }
    }

    private int getPageForSlot(int slot) {
        return slot / STORAGE_PAGE_SIZE;
    }
}
