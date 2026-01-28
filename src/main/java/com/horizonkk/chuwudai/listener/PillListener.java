package com.horizonkk.chuwudai.listener;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.PillData;
import com.horizonkk.chuwudai.pill.PillInventory;
import com.horizonkk.chuwudai.pill.PillItem;
import com.horizonkk.chuwudai.pill.PillManager;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class PillListener implements Listener {

    private final PillManager pillManager;

    public PillListener() {
        this.pillManager = Chuwudai.getInstance().getPillManager();
    }

    // 按Shift右键打开丹瓶
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 检查是否是右键操作
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            // 检查玩家是否按下了Shift键
            Player player = event.getPlayer();
            if (!player.isSneaking()) {
                // 没有按Shift，不处理
                return;
            }
            // 检查手持物品是否是丹瓶
            ItemStack item = event.getItem();
            if (item != null && PillItem.isPillItem(item)) {
                event.setCancelled(true);

                String pillId = PillItem.getPillId(item);
                if (pillId != null) {
                    pillManager.openPill(player, pillId);
                }
            }
        }
    }

    // 检测在背包中右键丹瓶
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
        // 检查是否丹瓶
        if (!PillItem.isPillItem(clickedItem)) {
            return;
        }
        // 取消事件，防止物品被移动
        event.setCancelled(true);
        // 获取丹瓶ID并打开
        String pillId = PillItem.getPillId(clickedItem);
        if (pillId != null) {
            // 关闭背包界面
            event.getWhoClicked().closeInventory();
            // 延迟1 tick后打开丹瓶，避免冲突
            Chuwudai.getInstance().getServer().getScheduler().runTaskLater(Chuwudai.getInstance(), () -> {
                pillManager.openPill((Player) event.getWhoClicked(), pillId);
            }, 1L);
        }
    }

    // 处理丹瓶内部的点击事件
    @EventHandler
    public void onInventoryClickInPill(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof PillInventory) {
            PillInventory pillInventory = (PillInventory) event.getInventory().getHolder();

            // 检查是否是控制按钮
            if (pillInventory.isControlSlot(event.getSlot())) {
                event.setCancelled(true);
                pillInventory.handleControlClick(event.getSlot());
                return;
            }

            // 检查放入的物品是否是丹药
            ItemStack currentItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor(); // 拖拽的物品

            // 检查放入物品（放入背包格子）
            if (cursorItem != null && !cursorItem.getType().equals(Material.AIR)) {
                if (!pillInventory.getPillData().isValidPillItem(cursorItem)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player) {
                        Player player = (Player) event.getWhoClicked();
                        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                        Chuwudai.getInstance().getConfig().getString("messages.invalid-pill-item", "&c只能存放含有'丹药'或'丹'字样的物品！")));
                    }
                    return;
                }
            }

            // 检查现有物品（取出或移动）
            if (currentItem != null && !currentItem.getType().equals(Material.AIR)) {
                if (!pillInventory.getPillData().isValidPillItem(currentItem)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player) {
                        Player player = (Player) event.getWhoClicked();
                        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                        Chuwudai.getInstance().getConfig().getString("messages.invalid-pill-item", "&c只能存放含有'丹药'或'丹'字样的物品！")));
                    }
                    return;
                }
            }

            // 异步保存数据
            Chuwudai.getInstance().getServer().getScheduler().runTaskAsynchronously(Chuwudai.getInstance(), () -> {
                pillInventory.saveItems();
                // 更新丹瓶物品的Lore
                ItemStack pillItem = findPillItem(event.getWhoClicked(), pillInventory.getPillData().getId());
                if (pillItem != null) {
                    // 获取PillData和丹药名称
                    PillData pillData = pillInventory.getPillData();
                    List<String> pillNames = pillData.getPillNames();
                    // 更新Lore
                    PillItem.updatePillLore(pillItem, pillNames);
                }
            });
        }
    }

    // 处理丹瓶关闭事件
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PillInventory) {
            PillInventory pillInventory = (PillInventory) event.getInventory().getHolder();
            // 保存数据
            pillInventory.saveItems();
            // 更新背包中的丹瓶Lore
            ItemStack pillItem = findPillItem(event.getPlayer(), pillInventory.getPillData().getId());
            if (pillItem != null) {
                // 获取PillData和丹药名称
                PillData pillData = pillInventory.getPillData();
                List<String> pillNames = pillData.getPillNames();
                // 更新Lore
                PillItem.updatePillLore(pillItem, pillNames);
                // 调试信息
                Chuwudai.getInstance().getLogger().info("更新丹瓶 " + pillData.getId() + " 的Lore");
                Chuwudai.getInstance().getLogger().info("已用槽位: " + pillData.getUsedSlots() + "/" + pillData.getSize());
                Chuwudai.getInstance().getLogger().info("容量百分比: " + pillData.getCapacityPercentage() + "%");
            }
        }
    }

    // 检查Shift点击（快速移动）
    @EventHandler
    public void onInventoryDragInPill(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PillInventory) {
            PillInventory pillInventory = (PillInventory) event.getInventory().getHolder();

            // 检查拖拽的物品是否是丹药
            for (Integer slot : event.getRawSlots()) {
                if (event.getView().convertSlot(slot) < pillInventory.getInventory().getSize()) {
                    ItemStack draggedItem = event.getOldCursor();
                    if (draggedItem != null && !pillInventory.getPillData().isValidPillItem(draggedItem)) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player) {
                            Player player = (Player) event.getWhoClicked();
                            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                            Chuwudai.getInstance().getConfig().getString("messages.invalid-pill-item", "&c只能存放含有'丹药'或'丹'字样的物品！")));
                        }
                        return;
                    }
                }
            }
        }
    }

    // 查找玩家背包中的丹瓶
    private ItemStack findPillItem(HumanEntity player, String pillId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && PillItem.isPillItem(item)) {
                if (pillId.equals(PillItem.getPillId(item))) {
                    return item;
                }
            }
        }
        return null;
    }
}
