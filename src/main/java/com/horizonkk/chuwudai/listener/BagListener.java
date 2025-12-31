package com.horizonkk.chuwudai.listener;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.bag.BagManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class BagListener implements Listener {
    
    private final BagManager bagManager;
    
    public BagListener() {
        this.bagManager = Chuwudai.getInstance().getBagManager();
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        
        if (item != null && BagItem.isBagItem(item)) {
            event.setCancelled(true);
            
            String bagId = BagItem.getBagId(item);
            if (bagId != null) {
                bagManager.openBag(event.getPlayer(), bagId);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof com.horizonkk.chuwudai.bag.BagInventory) {
            com.horizonkk.chuwudai.bag.BagInventory bagInventory = 
                (com.horizonkk.chuwudai.bag.BagInventory) event.getInventory().getHolder();
            
            // 检查是否是控制按钮
            if (bagInventory.isControlSlot(event.getSlot())) {
                event.setCancelled(true);
                bagInventory.handleControlClick(event.getSlot());
                return;
            }
            
            // 检查放入的物品是否是灵石
            if (event.getCursor() != null) {
                ItemStack cursor = event.getCursor();
                if (!isSpiritStone(cursor)) {
                    event.setCancelled(true);
                    event.getWhoClicked().sendMessage(
                            Chuwudai.getInstance().getConfig().getString("messages.prefix", "") +
                            Chuwudai.getInstance().getConfig().getString("messages.invalid-stone", "&c这不是有效的灵石！"));
                }
            }
            
            // 异步保存数据
            Chuwudai.getInstance().getServer().getScheduler().runTaskAsynchronously(Chuwudai.getInstance(), () -> {
                bagInventory.saveItems();
                
                // 更新储物袋物品的Lore
                ItemStack bagItem = findBagItem(event.getWhoClicked(), bagInventory.getBagData().getId());
                if (bagItem != null) {
                    BagItem.updateBagLore(bagItem, bagInventory.getBagData().getStoneCounts());
                }
            });
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof com.horizonkk.chuwudai.bag.BagInventory) {
            com.horizonkk.chuwudai.bag.BagInventory bagInventory = 
                (com.horizonkk.chuwudai.bag.BagInventory) event.getInventory().getHolder();
            
            // 保存数据
            bagInventory.saveItems();
            
            // 更新背包中的储物袋Lore
            ItemStack bagItem = findBagItem(event.getPlayer(), bagInventory.getBagData().getId());
            if (bagItem != null) {
                BagItem.updateBagLore(bagItem, bagInventory.getBagData().getStoneCounts());
            }
        }
    }
    
    private boolean isSpiritStone(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        
        String displayName = item.getItemMeta().getDisplayName();
        // 检查是否是四种灵石之一
        return displayName.contains("下品灵石") || 
               displayName.contains("中品灵石") || 
               displayName.contains("上品灵石") || 
               displayName.contains("极品灵石");
    }
    
    private ItemStack findBagItem(org.bukkit.entity.HumanEntity player, String bagId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && BagItem.isBagItem(item)) {
                if (bagId.equals(BagItem.getBagId(item))) {
                    return item;
                }
            }
        }
        return null;
    }
}