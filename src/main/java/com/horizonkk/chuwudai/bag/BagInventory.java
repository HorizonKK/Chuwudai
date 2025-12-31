package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BagInventory implements InventoryHolder {
    
    private final BagData bagData;
    private Inventory inventory;
    private int currentPage;
    private final Map<Integer, Integer> slotMapping = new HashMap<>();
    
    public BagInventory(BagData bagData) {
        this.bagData = bagData;
        this.currentPage = 0;
        createInventory();
    }
    
    private void createInventory() {
        int size = bagData.getSize();
        int totalPages = bagData.getTotalPages();
        
        // 如果只有一页，直接使用对应大小
        if (totalPages == 1) {
            inventory = Bukkit.createInventory(this, size, "储物袋 #" + bagData.getId());
        } else {
            // 多页使用54格，加上控制栏
            inventory = Bukkit.createInventory(this, 54, "储物袋 #" + bagData.getId() + " (第 " + (currentPage + 1) + " 页)");
        }
        
        loadItems();
        addControlButtons();
    }
    
    private void loadItems() {
        slotMapping.clear();
        int startSlot = currentPage * 45; // 每页45个内容格子
        int endSlot = Math.min(startSlot + 45, bagData.getSize());
        
        for (int i = startSlot; i < endSlot; i++) {
            int invSlot = i - startSlot;
            slotMapping.put(invSlot, i);
            
            ItemStack item = bagData.getItem(i);
            if (item != null) {
                inventory.setItem(invSlot, item);
            }
        }
    }
    
    private void addControlButtons() {
        int totalPages = bagData.getTotalPages();
        
        if (totalPages > 1) {
            // 上一页按钮 (如果不在第一页)
            if (currentPage > 0) {
                ItemStack prevButton = createButton(
                        Material.ARROW,
                        Chuwudai.getInstance().getConfig().getString("ui.page-prev", "&c← 上一页"),
                        Arrays.asList("§7点击切换到上一页")
                );
                inventory.setItem(45, prevButton);
            }
            
            // 页码显示
            ItemStack pageInfo = createButton(
                    Material.PAPER,
                    Chuwudai.getInstance().getConfig().getString("ui.current-page", "&7第 %page% 页 / %total% 页")
                            .replace("%page%", String.valueOf(currentPage + 1))
                            .replace("%total%", String.valueOf(totalPages)),
                    null
            );
            inventory.setItem(49, pageInfo);
            
            // 下一页按钮 (如果不在最后一页)
            if (currentPage < totalPages - 1) {
                ItemStack nextButton = createButton(
                        Material.ARROW,
                        Chuwudai.getInstance().getConfig().getString("ui.page-next", "&a下一页 →"),
                        Arrays.asList("§7点击切换到下一页")
                );
                inventory.setItem(53, nextButton);
            }
            
            // 关闭按钮
            ItemStack closeButton = createButton(
                    Material.BARRIER,
                    Chuwudai.getInstance().getConfig().getString("ui.close-button", "&c关闭"),
                    Arrays.asList("§7点击关闭储物袋")
            );
            inventory.setItem(48, closeButton);
        } else {
            // 单页的关闭按钮
            ItemStack closeButton = createButton(
                    Material.BARRIER,
                    Chuwudai.getInstance().getConfig().getString("ui.close-button", "&c关闭"),
                    Arrays.asList("§7点击关闭储物袋")
            );
            // 放在最后一格
            inventory.setItem(bagData.getSize() - 1, closeButton);
        }
    }
    
    private ItemStack createButton(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name.replace("&", "§"));
        
        if (lore != null) {
            meta.setLore(lore);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    public void open(Player player) {
        player.openInventory(inventory);
    }
    
    public boolean isControlSlot(int slot) {
        if (bagData.getTotalPages() == 1) {
            return slot == bagData.getSize() - 1; // 关闭按钮
        } else {
            return slot >= 45; // 底部控制栏
        }
    }
    
    public void handleControlClick(int slot) {
        if (slot == 45 && currentPage > 0) {
            // 上一页
            currentPage--;
            refresh();
        } else if (slot == 53 && currentPage < bagData.getTotalPages() - 1) {
            // 下一页
            currentPage++;
            refresh();
        } else if (slot == 48) {
            // 关闭
            Player viewer = (Player) inventory.getViewers().get(0);
            viewer.closeInventory();
        }
    }
    
    public void refresh() {
        inventory.clear();
        loadItems();
        addControlButtons();
        
        // 更新标题显示当前页码
        int totalPages = bagData.getTotalPages();
        if (totalPages > 1) {
            String title = "储物袋 #" + bagData.getId() + " (第 " + (currentPage + 1) + " 页)";
            // Bukkit API 不支持动态修改标题，需要关闭后重新打开
            if (!inventory.getViewers().isEmpty()) {
                Player viewer = (Player) inventory.getViewers().get(0);
                viewer.closeInventory();
                open(viewer);
            }
        }
    }
    
    public void saveItems() {
        for (int invSlot : slotMapping.keySet()) {
            int dataSlot = slotMapping.get(invSlot);
            ItemStack item = inventory.getItem(invSlot);
            
            if (item != null && !isControlItem(item)) {
                bagData.setItem(dataSlot, item);
            } else if (item == null) {
                bagData.removeItem(dataSlot);
            }
        }
        
        // 保存数据
        Chuwudai.getInstance().getBagManager().saveBagData(bagData.getId());
    }
    
    private boolean isControlItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        String displayName = item.getItemMeta().getDisplayName();
        return displayName != null && (
                displayName.contains("上一页") ||
                displayName.contains("下一页") ||
                displayName.contains("关闭") ||
                displayName.contains("第") && displayName.contains("页")
        );
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public BagData getBagData() {
        return bagData;
    }
}