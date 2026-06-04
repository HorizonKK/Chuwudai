package com.horizonkk.chuwudai.Temporary;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class TempBoxListener implements Listener {

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TempBoxInventory) {
            TempBoxInventory tempBox = (TempBoxInventory) event.getInventory().getHolder();
            TempBoxManager manager = Chuwudai.getInstance().getTempBoxManager();
            manager.saveItems(tempBox.getOwnerUuid(), tempBox.extractItems());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TempBoxInventory) {
            // 禁止对暂存箱界面使用数字键交换物品（防止物品意外移动到玩家背包的热键栏边界）
            if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TempBoxInventory) {
            // 允许在暂存箱内拖拽，不做额外限制
        }
    }
}
