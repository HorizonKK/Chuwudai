package com.horizonkk.chuwudai.Temporary;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TempBoxManager {

    private final Map<UUID, Map<Integer, ItemStack>> playerBoxes = new ConcurrentHashMap<>();
    private final Map<UUID, TempBoxInventory> openInventories = new ConcurrentHashMap<>();
    private static final int BOX_SIZE = 54;

    public Map<Integer, ItemStack> getPlayerItems(UUID playerUuid) {
        return playerBoxes.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
    }

    public void openTempBox(Player player) {
        Map<Integer, ItemStack> items = getPlayerItems(player.getUniqueId());
        TempBoxInventory inventory = new TempBoxInventory(player.getUniqueId(), items);
        openInventories.put(player.getUniqueId(), inventory);
        inventory.open(player);
    }

    /**
     * 向玩家暂存箱添加物品，放入第一个空位。若暂存箱已打开则写入实时界面。
     * @return 是否成功放入（箱子未满）
     */
    public boolean addItem(UUID playerUuid, ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return false;

        TempBoxInventory openInv = openInventories.get(playerUuid);
        if (openInv != null) {
            Inventory inv = openInv.getInventory();
            for (int i = 0; i < BOX_SIZE; i++) {
                ItemStack slotItem = inv.getItem(i);
                if (slotItem == null || slotItem.getType() == org.bukkit.Material.AIR) {
                    inv.setItem(i, item.clone());
                    return true;
                }
            }
            return false;
        }

        Map<Integer, ItemStack> items = getPlayerItems(playerUuid);
        for (int i = 0; i < BOX_SIZE; i++) {
            if (!items.containsKey(i)) {
                items.put(i, item.clone());
                return true;
            }
        }
        return false;
    }

    /**
     * 向玩家暂存箱添加物品到指定槽位。若暂存箱已打开则写入实时界面。
     * @return 是否成功放入
     */
    public boolean addItemToSlot(UUID playerUuid, int slot, ItemStack item) {
        if (slot < 0 || slot >= BOX_SIZE) return false;
        if (item == null || item.getType() == org.bukkit.Material.AIR) return false;

        TempBoxInventory openInv = openInventories.get(playerUuid);
        if (openInv != null) {
            openInv.getInventory().setItem(slot, item.clone());
            return true;
        }

        Map<Integer, ItemStack> items = getPlayerItems(playerUuid);
        items.put(slot, item.clone());
        return true;
    }

    /**
     * 从关闭的暂存箱界面同步数据回内存。更新现有 map 引用而非替换，避免并发附加丢失。
     */
    public void saveItems(UUID playerUuid, Map<Integer, ItemStack> items) {
        openInventories.remove(playerUuid);
        if (items.isEmpty()) {
            playerBoxes.remove(playerUuid);
        } else {
            Map<Integer, ItemStack> existing = playerBoxes.get(playerUuid);
            if (existing != null) {
                existing.clear();
                existing.putAll(items);
            } else {
                playerBoxes.put(playerUuid, new ConcurrentHashMap<>(items));
            }
        }
    }

    public void clearBox(UUID playerUuid) {
        openInventories.remove(playerUuid);
        playerBoxes.remove(playerUuid);
    }

    public int getUsedSlots(UUID playerUuid) {
        TempBoxInventory openInv = openInventories.get(playerUuid);
        if (openInv != null) {
            int count = 0;
            Inventory inv = openInv.getInventory();
            for (int i = 0; i < BOX_SIZE; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    count++;
                }
            }
            return count;
        }
        Map<Integer, ItemStack> items = playerBoxes.get(playerUuid);
        return items != null ? items.size() : 0;
    }

    public int getBoxSize() {
        return BOX_SIZE;
    }
}
