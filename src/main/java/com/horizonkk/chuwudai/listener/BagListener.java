package com.horizonkk.chuwudai.listener;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagInventory;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.bag.BagManager;
import com.horizonkk.chuwudai.bag.BagOptionsInventory;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class BagListener implements Listener {

    private final BagManager bagManager;
    private static final int BAG_UPGRADE_CUSTOM_MODEL_DATA = 10111;
    private static final int BAG_UPGRADE_SLOTS = 9;

    public BagListener() {
        this.bagManager = Chuwudai.getInstance().getBagManager();
    }

    // 保持原来的手持物品右键打开功能（可选）
    @EventHandler(ignoreCancelled = true)
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
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (handleBagUpgradeClick(event)) {
            return;
        }

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
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClickInBag(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof BagOptionsInventory) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().getHolder() instanceof BagOptionsInventory) {
                BagOptionsInventory optionsInventory = (BagOptionsInventory) event.getInventory().getHolder();
                if (optionsInventory.isMenuSlot(event.getSlot())) {
                    optionsInventory.handleClick(event.getSlot(), (Player) event.getWhoClicked());
                }
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof BagInventory) {
            BagInventory bagInventory = (BagInventory) event.getInventory().getHolder();
            if (event.getWhoClicked() instanceof Player
                    && Chuwudai.getInstance().getModBagBridge() != null
                    && !Chuwudai.getInstance().getModBagBridge().isVanillaSession(
                    (Player) event.getWhoClicked(), bagInventory.getBagData().getId())) {
                event.setCancelled(true);
                event.getWhoClicked().closeInventory();
                return;
            }

            if (event.getClickedInventory() != null
                    && event.getClickedInventory().getHolder() instanceof BagInventory
                    && bagInventory.isControlSlot(event.getSlot())) {
                event.setCancelled(true);
                bagInventory.handleControlClick(event.getSlot(), event.getClick(), (Player) event.getWhoClicked());
                return;
            }

            // 根据物品名称判断是否是控制按钮，而不是依赖槽位索引
            ItemStack clickedItem = event.getCurrentItem();
            if (isControlButtonItem(clickedItem)) {
                event.setCancelled(true);
                // 找到对应的控制槽位并处理点击
                int slot = findControlSlotByItem(bagInventory.getInventory(), clickedItem);
                if (slot != -1) {
                    bagInventory.handleControlClick(slot, event.getClick(), (Player) event.getWhoClicked());
                }
                return;
            }
        }
    }

    // 根据物品名称判断是否是控制按钮
    private boolean isControlButtonItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        String displayName = item.getItemMeta().getDisplayName();
        return displayName != null && (
                displayName.contains("上一页") ||
                        displayName.contains("下一页") ||
                        displayName.contains("关闭") ||
                        displayName.contains("第") && displayName.contains("页") ||
                        displayName.equals(" ") || // 普通玻璃板
                        displayName.contains("当前未解锁该行") // 未解锁玻璃板
        );
    }

    // 根据物品在库存中找到对应的槽位
    private int findControlSlotByItem(Inventory inventory, ItemStack targetItem) {
        if (targetItem == null || !targetItem.hasItemMeta()) return -1;

        String targetName = targetItem.getItemMeta().getDisplayName();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String itemName = item.getItemMeta().getDisplayName();
                if (itemName.equals(targetName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BagInventory) {
            BagInventory bagInventory =
                    (BagInventory) event.getInventory().getHolder();
            // 保存数据
            bagInventory.saveItems();
            bagManager.flushDirtyBagData(bagInventory.getBagData().getId());
            // 更新背包中的储物袋Lore
            ItemStack bagItem = findBagItem(event.getPlayer(), bagInventory.getBagData().getId());
            if (bagItem != null) {
                // 获取BagData和灵石数量
                BagData bagData = bagInventory.getBagData();
                Map<String, Integer> stoneCounts = bagData.getStoneCounts();
                // 更新Lore
                BagItem.updateBagLore(bagItem, stoneCounts);
            }
        }
    }

    private boolean handleBagUpgradeClick(InventoryClickEvent event) {
        ItemStack upgradeItem = event.getCursor();
        ItemStack bagItem = event.getCurrentItem();

        if (!isBagUpgradeItem(upgradeItem) || !BagItem.isBagItem(bagItem)) {
            return false;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return true;
        }

        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof BagInventory) {
            sendBagMessage(player, "&c储物袋打开时不能升级，请先关闭储物袋界面！");
            return true;
        }

        String bagId = BagItem.getBagId(bagItem);
        if (bagManager.isBagOpen(bagId)) {
            sendBagMessage(player, "&c储物袋打开时不能升级，请先关闭储物袋界面！");
            return true;
        }

        if(Chuwudai.getInstance().getSharedStorage()!=null) {
            var shared=Chuwudai.getInstance().getSharedStorage();
            var key=new com.horizonkk.chuwudai.storage.MySqlRepository.Key(com.horizonkk.chuwudai.storage.MySqlRepository.Kind.BAG,bagId);
            if(!shared.isReady(key)) { event.setCancelled(true); shared.prepare(key).exceptionally(e->null); event.getWhoClicked().sendMessage("§c储物袋正在加载，请稍后重试"); return true; }
        }
        BagData currentBag = bagManager.getBagData(bagId);
        if (currentBag == null) {
            sendBagMessage(player, "&c储物袋升级失败，数据不存在或当前无法升级！");
            return true;
        }

        int maxBagSize = bagManager.getMaxBagSize();
        if (currentBag.getSize() + BAG_UPGRADE_SLOTS > maxBagSize) {
            sendBagMessage(player, "&c储物袋容量已达上限，最大容量: &f" + maxBagSize + " &c格");
            return true;
        }

        BagData upgradedBag = bagManager.upgradeBagSize(bagItem, BAG_UPGRADE_SLOTS);
        if (upgradedBag == null) {
            sendBagMessage(player, "&c储物袋升级失败，数据不存在或当前无法升级！");
            return true;
        }

        consumeOneCursorItem(event);
        event.setCurrentItem(bagItem);
        player.updateInventory();
        sendBagMessage(player, "&a储物袋容量已增加一排，当前容量: &f" + upgradedBag.getSize() + " &a格");
        return true;
    }

    private boolean isBagUpgradeItem(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == BAG_UPGRADE_CUSTOM_MODEL_DATA;
    }

    private void consumeOneCursorItem(InventoryClickEvent event) {
        ItemStack cursorItem = event.getCursor();
        if (cursorItem == null) {
            return;
        }

        if (cursorItem.getAmount() <= 1) {
            event.getWhoClicked().setItemOnCursor(null);
            return;
        }

        ItemStack remaining = cursorItem.clone();
        remaining.setAmount(cursorItem.getAmount() - 1);
        event.getWhoClicked().setItemOnCursor(remaining);
    }

    private void sendBagMessage(Player player, String message) {
        String prefix = Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f");
        player.sendMessage(ColorUtils.translateColorCodes(prefix + message));
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
