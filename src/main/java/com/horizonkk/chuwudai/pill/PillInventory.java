package com.horizonkk.chuwudai.pill;

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

public class PillInventory implements InventoryHolder {
    
    private final PillData pillData;
    private Inventory inventory;
    
    public PillInventory(PillData pillData) {
        this.pillData = pillData;
        createInventory();
    }
    
    private void createInventory() {
        int size = pillData.getSize();
        
        // 限制库存大小不超过54格
        int inventorySize = Math.min(size, 54);
        if (inventorySize % 9 != 0) {
            inventorySize = ((inventorySize / 9) + 1) * 9; // 对齐到9的倍数
        }
        
        inventory = Bukkit.createInventory(this, inventorySize, "丹瓶 #" + pillData.getId());
        
        loadItems();
        addControlButtons();
    }
    
    private void loadItems() {
        for (int i = 0; i < pillData.getSize() && i < 54; i++) {
            ItemStack item = pillData.getItem(i);
            if (item != null) {
                inventory.setItem(i, item);
            }
        }
    }
    
    private void addControlButtons() {
        // 关闭按钮 - 放在最后一个槽位
        ItemStack closeButton = createButton(
                Material.BARRIER,
                Chuwudai.getInstance().getConfig().getString("ui.close-button", "&c关闭"),
                Arrays.asList("§7点击关闭丹瓶")
        );
        inventory.setItem(Math.min(53, inventory.getSize() - 1), closeButton);
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
        return slot == (Math.min(53, inventory.getSize() - 1)); // 关闭按钮
    }
    
    public void handleControlClick(int slot) {
        if (slot == (Math.min(53, inventory.getSize() - 1))) {
            // 关闭
            Player viewer = (Player) inventory.getViewers().get(0);
            viewer.closeInventory();
        }
    }
    
    public void refresh() {
        inventory.clear();
        loadItems();
        addControlButtons();
        
        // 更新标题显示
        String title = "丹瓶 #" + pillData.getId();
        if (!inventory.getViewers().isEmpty()) {
            Player viewer = (Player) inventory.getViewers().get(0);
            viewer.closeInventory();
            open(viewer);
        }
    }
    
    public void saveItems() {
        for (int slot = 0; slot < Math.min(pillData.getSize(), 54); slot++) {
            ItemStack item = inventory.getItem(slot);
            
            if (item != null && pillData.isValidPillItem(item)) {
                pillData.setItem(slot, item);
            } else if (item != null && !pillData.isValidPillItem(item)) {
                // 如果物品不是有效丹药，则移除
                pillData.removeItem(slot);
            } else if (item == null) {
                pillData.removeItem(slot);
            }
        }
        
        // 保存数据
        Chuwudai.getInstance().getPillManager().savePillData(pillData.getId());
    }
    
    private boolean isControlItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        String displayName = item.getItemMeta().getDisplayName();
        return displayName != null && (
                displayName.contains("关闭")
        );
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public PillData getPillData() {
        return pillData;
    }
}
