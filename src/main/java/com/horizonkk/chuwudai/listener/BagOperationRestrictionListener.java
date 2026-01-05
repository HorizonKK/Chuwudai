package com.horizonkk.chuwudai.listener;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagInventory;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class BagOperationRestrictionListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 检查是否在储物袋界面中
        if (!isInBagInventory(event.getInventory())) {
            return;
        }

        // 检查点击的物品是否是储物袋
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && isBagItem(clickedItem)) {
            // 阻止所有操作
            event.setCancelled(true);
            
            // 发送提示消息
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                        Chuwudai.getInstance().getConfig().getString("messages.bag-operation-restricted", "&c无法在储物袋界面中操作储物袋物品！")));
            }
        }

        // 检查拖拽操作
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            // 检查玩家背包中是否有储物袋
            if (hasBagInPlayerInventory(event.getWhoClicked().getInventory())) {
                event.setCancelled(true);
                
                if (event.getWhoClicked() instanceof Player) {
                    Player player = (Player) event.getWhoClicked();
                    player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                            Chuwudai.getInstance().getConfig().getString("messages.bag-operation-restricted", "&c无法在储物袋界面中操作储物袋物品！")));
                }
            }
        }
    }

    // 检查是否在储物袋界面中
    private boolean isInBagInventory(Inventory inventory) {
        return inventory.getHolder() instanceof BagInventory;
    }

    // 检查是否是储物袋物品
    private boolean isBagItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            return displayName.contains("储物袋");
        }

        return false;
    }

    // 检查玩家背包中是否有储物袋
    private boolean hasBagInPlayerInventory(Inventory playerInventory) {
        for (ItemStack item : playerInventory.getContents()) {
            if (item != null && isBagItem(item)) {
                return true;
            }
        }
        return false;
    }
}
