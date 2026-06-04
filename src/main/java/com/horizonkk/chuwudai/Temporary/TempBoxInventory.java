package com.horizonkk.chuwudai.Temporary;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TempBoxInventory implements InventoryHolder {

    private final UUID ownerUuid;
    private final Inventory inventory;

    public TempBoxInventory(UUID ownerUuid, Map<Integer, ItemStack> items) {
        this.ownerUuid = ownerUuid;
        this.inventory = Bukkit.createInventory(this, 54, "暂存箱");
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < 54) {
                inventory.setItem(slot, entry.getValue().clone());
            }
        }
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public Map<Integer, ItemStack> extractItems() {
        Map<Integer, ItemStack> result = new HashMap<>();
        for (int i = 0; i < 54; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                result.put(i, item.clone());
            }
        }
        return result;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
