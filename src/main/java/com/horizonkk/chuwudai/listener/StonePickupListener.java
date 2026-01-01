package com.horizonkk.chuwudai.listener;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.bag.BagManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

public class StonePickupListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStonePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ItemStack pickedItem = event.getItem().getItemStack();
        // 调试日志
        Chuwudai.getInstance().getLogger().info("StonePickupListener triggered for player: " + player.getName());
        Chuwudai.getInstance().getLogger().info("Picked item: " + pickedItem.getType() + " display name: " + (pickedItem.hasItemMeta() && pickedItem.getItemMeta().hasDisplayName() ? pickedItem.getItemMeta().getDisplayName() : "no display name"));

        // 检查是否是灵石
        boolean isStone = isSpiritStone(pickedItem);
        Chuwudai.getInstance().getLogger().info("Is spirit stone: " + isStone);
        if (!isStone) {
            return;
        }
        // 检查是否启用自动拾取
        boolean autoPickupEnabled = Chuwudai.getInstance().getConfig().getBoolean("auto-pickup.enabled", true);
        Chuwudai.getInstance().getLogger().info("Auto pickup enabled: " + autoPickupEnabled);
        if (!autoPickupEnabled) {
            return;
        }
        // 检查是否需要在第一格放置储物袋
        boolean requireSlotZero = Chuwudai.getInstance().getConfig().getBoolean("auto-pickup.require-slot-zero", true);
        ItemStack bagItem = requireSlotZero ? player.getInventory().getItem(9) : findBagInInventory(player);
        Chuwudai.getInstance().getLogger().info("Require slot zero: " + requireSlotZero);
        Chuwudai.getInstance().getLogger().info("Bag item in slot 9: " + (bagItem != null ? bagItem.getType() + " with meta: " + bagItem.hasItemMeta() : "null"));

        if (bagItem == null || !BagItem.isBagItem(bagItem)) {
            if (requireSlotZero) {
                player.sendMessage(Chuwudai.getInstance().getConfig().getString("messages.prefix", "") +
                        Chuwudai.getInstance().getConfig().getString("messages.no-bag-in-slot", "&c请在背包第一格放置储物袋以自动吸收灵石！"));
            }
            Chuwudai.getInstance().getLogger().info("Bag item is null or not a bag item");
            return;
        }

        // 获取储物袋ID和数据
        String bagId = BagItem.getBagId(bagItem);
        Chuwudai.getInstance().getLogger().info("Bag ID: " + bagId);
        BagData bagData = Chuwudai.getInstance().getBagManager().getBagData(bagId);
        Chuwudai.getInstance().getLogger().info("Bag data loaded: " + (bagData != null));

        if (bagData == null) {
            return;
        }

        // 尝试将灵石存入储物袋
        int remaining = tryStoreStoneInBag(pickedItem, bagData);
        Chuwudai.getInstance().getLogger().info("After trying to store, remaining amount: " + remaining);

        if (remaining < pickedItem.getAmount()) {
            // 部分或全部存入成功
            int storedAmount = pickedItem.getAmount() - remaining;
            event.getItem().getItemStack().setAmount(remaining);

            if (remaining <= 0) {
                event.setCancelled(true);
                event.getItem().remove();
            }

            // 发送消息
            String stoneName = getStoneName(pickedItem);
            player.sendMessage(Chuwudai.getInstance().getConfig().getString("messages.prefix", "") +
                    Chuwudai.getInstance().getConfig().getString("messages.stone-pickup", "&a已自动存入 %amount% 个 %stone%")
                            .replace("%amount%", String.valueOf(storedAmount))
                            .replace("%stone%", stoneName));

            // 更新储物袋Lore
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 获取BagData和灵石数量
                    BagData bagData = Chuwudai.getInstance().getBagManager().getBagData(bagId);
                    if (bagData != null) {
                        Map<String, Integer> stoneCounts = bagData.getStoneCounts();
                        // 重新从玩家背包获取储物袋，确保引用是最新的
                        ItemStack currentBagItem = requireSlotZero ?
                                player.getInventory().getItem(9) :
                                findBagInInventory(player);
                        if (currentBagItem != null) {
                            BagItem.updateBagLore(currentBagItem, stoneCounts);
                        }
                        Chuwudai.getInstance().getBagManager().saveBagData(bagId);
                        // 强制刷新玩家背包显示
                        Player player = (Player) event.getEntity();
                        player.updateInventory();
                    }
                }
            }.runTaskAsynchronously(Chuwudai.getInstance());
        } else {
            Chuwudai.getInstance().getLogger().info("No stone stored, maybe bag is full or other reason.");
        }
    }

    private boolean isSpiritStone(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        String displayName = item.getItemMeta().getDisplayName();
        ConfigurationSection stones = Chuwudai.getInstance().getConfig().getConfigurationSection("spirit-stones");
        if (stones != null) {
            for (String stoneName : stones.getKeys(false)) {
                // 将配置中的名称中的"&"替换为"§"，然后与物品显示名称比较
                String coloredStoneName = stoneName.replace("&", "§");
                if (displayName.equals(coloredStoneName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ItemStack findBagInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && BagItem.isBagItem(item)) {
                return item;
            }
        }
        return null;
    }

    private int tryStoreStoneInBag(ItemStack stone, BagData bagData) {
        String stoneType = getStoneName(stone);
        int amount = stone.getAmount();
        int maxStackSize = stone.getMaxStackSize();
        // 寻找已有的灵石堆叠
        for (int i = 0; i < bagData.getSize(); i++) {
            ItemStack existing = bagData.getItem(i);
            if (existing != null &&
                    existing.getType() == stone.getType() &&
                    existing.hasItemMeta() && stone.hasItemMeta() && // 添加空值检查
                    existing.getItemMeta().hasDisplayName() && stone.getItemMeta().hasDisplayName() && // 添加显示名称检查
                    existing.getItemMeta().getDisplayName().equals(stone.getItemMeta().getDisplayName())) {

                int availableSpace = maxStackSize - existing.getAmount();
                if (availableSpace > 0) {
                    int toAdd = Math.min(availableSpace, amount);
                    existing.setAmount(existing.getAmount() + toAdd);
                    amount -= toAdd;

                    if (amount <= 0) {
                        return 0;
                    }
                }
            }
        }
        // 寻找空位
        for (int i = 0; i < bagData.getSize(); i++) {
            if (bagData.getItem(i) == null) {
                ItemStack newStack = stone.clone();
                int toAdd = Math.min(maxStackSize, amount);
                newStack.setAmount(toAdd);
                bagData.setItem(i, newStack);
                amount -= toAdd;

                if (amount <= 0) {
                    return 0;
                }
            }
        }

        return amount; // 无法完全存入的剩余数量
    }

    private String getStoneName(ItemStack stone) {
        if (stone.hasItemMeta() && stone.getItemMeta().hasDisplayName()) {
            return stone.getItemMeta().getDisplayName();
        }
        // 如果没有显示名称，返回类型名称作为备选
        return stone.getType().name();
    }
}