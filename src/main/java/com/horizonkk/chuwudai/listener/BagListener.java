package com.horizonkk.chuwudai.listener;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagInventory;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.bag.BagManager;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class BagListener implements Listener {

    private final BagManager bagManager;

    public BagListener() {
        this.bagManager = Chuwudai.getInstance().getBagManager();
    }

    // 保持原来的手持物品右键打开功能（可选）
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 检查是否是右键空气、方块或者物品
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();

            if (item != null && BagItem.isBagItem(item)) {
                event.setCancelled(true);

                String bagId = BagItem.getBagId(item);
                if (bagId != null) {
                    bagManager.openBag(event.getPlayer(), bagId);
                }
            }
        }
    }

    // 新添加：检测在背包中右键储物袋
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 检查点击类型是否是右键
        if (event.getClick() != ClickType.RIGHT) {
            return;
        }
        // 检查当前打开的库存是否是玩家自己的背包
        if (event.getView().getType() != InventoryType.CRAFTING &&
                event.getView().getType() != InventoryType.PLAYER) {
            return;
        }
        // 检查点击的槽位是否是索引9（背包第一行第一格）
        int slot = event.getSlot();
        if (slot != 9) {  // 只允许在索引9位置右键打开
            return;
        }
        // 获取被点击的物品
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        // 检查是否储物袋
        if (!BagItem.isBagItem(clickedItem)) {
            return;
        }
        // 取消事件，防止物品被移动
        event.setCancelled(true);
        // 获取储物袋ID并打开
        String bagId = BagItem.getBagId(clickedItem);
        if (bagId != null) {
            // 关闭背包界面
            event.getWhoClicked().closeInventory();
            // 延迟1 tick后打开储物袋，避免冲突
            Chuwudai.getInstance().getServer().getScheduler().runTaskLater(Chuwudai.getInstance(), () -> {
                bagManager.openBag((org.bukkit.entity.Player) event.getWhoClicked(), bagId);
            }, 1L);
        }
    }

    // 原有的事件处理（保持不动）
    @EventHandler
    public void onInventoryClickInBag(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof BagInventory) {
            BagInventory bagInventory = (BagInventory) event.getInventory().getHolder();
            // 检查是否是控制按钮
            if (bagInventory.isControlSlot(event.getSlot())) {
                event.setCancelled(true);
                bagInventory.handleControlClick(event.getSlot());
                return;
            }
            // 异步保存数据
            Chuwudai.getInstance().getServer().getScheduler().runTaskAsynchronously(Chuwudai.getInstance(), () -> {
                bagInventory.saveItems();
                // 更新储物袋物品的Lore
                ItemStack bagItem = findBagItem(event.getWhoClicked(), bagInventory.getBagData().getId());
                if (bagItem != null) {
                    // 获取BagData和灵石数量
                    BagData bagData = bagInventory.getBagData();
                    Map<String, Integer> stoneCounts = bagData.getStoneCounts();
                    // 更新Lore
                    BagItem.updateBagLore(bagItem, stoneCounts);
                }
            });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BagInventory) {
            BagInventory bagInventory =
                    (BagInventory) event.getInventory().getHolder();
            // 保存数据
            bagInventory.saveItems();
            // 更新背包中的储物袋Lore
            ItemStack bagItem = findBagItem(event.getPlayer(), bagInventory.getBagData().getId());
            if (bagItem != null) {
                // 获取BagData和灵石数量
                BagData bagData = bagInventory.getBagData();
                Map<String, Integer> stoneCounts = bagData.getStoneCounts();
                // 更新Lore
                BagItem.updateBagLore(bagItem, stoneCounts);
                // 调试信息
                Chuwudai.getInstance().getLogger().info("更新储物袋 " + bagData.getId() + " 的Lore");
                Chuwudai.getInstance().getLogger().info("已用槽位: " + bagData.getUsedSlots() + "/" + bagData.getSize());
                Chuwudai.getInstance().getLogger().info("容量百分比: " + bagData.getCapacityPercentage() + "%");
            }
        }
    }


    private ItemStack findBagItem(HumanEntity player, String bagId) {
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