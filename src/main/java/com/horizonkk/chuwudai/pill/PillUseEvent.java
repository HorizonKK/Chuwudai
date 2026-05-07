package com.horizonkk.chuwudai.pill;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PillUseEvent implements Listener {

    @EventHandler
    public void onPlayerConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item != null && PillItem.isPillItem(item)) {
            event.setCancelled(true);
            String pillId = PillItem.getPillId(item);
            if (pillId != null) {
                PillData pillData = Chuwudai.getInstance().getPillManager().getPillData(pillId);
                if (pillData != null) {
                    ItemStack foundPill = null;
                    int foundSlot = -1;
                    for (int slot = 0; slot < pillData.getSize(); slot++) {
                        ItemStack pillInSlot = pillData.getItem(slot);
                        if (pillInSlot != null && pillData.isValidPillItem(pillInSlot)) {
                            foundPill = pillInSlot;
                            foundSlot = slot;
                            break;
                        }
                    }
                    if (foundPill != null && foundSlot >= 0) {
                        executePillEffect(player, foundPill);
                        if (foundPill.getAmount() > 1) {
                            foundPill.setAmount(foundPill.getAmount() - 1);
                            pillData.setItem(foundSlot, foundPill);
                        } else {
                            pillData.removeItem(foundSlot);
                        }
                        Chuwudai.getInstance().getPillManager().savePillData(pillId);
                        updatePillItemLore(item, pillData, player);
                        String pillName = foundPill.hasItemMeta() ?
                                foundPill.getItemMeta().getDisplayName() :
                                foundPill.getType().name();
                        player.sendMessage(ColorUtils.translateColorCodes(
                                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                        "&a服用丹药: &f" + pillName + " &a成功"));
                    } else {
                        player.sendMessage(ColorUtils.translateColorCodes(
                                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                        "&c丹瓶中没有可服用的丹药"));
                        updatePillItemLore(item, pillData, player);
                    }
                }
            }
        }
    }


    private void executePillEffect(Player player, ItemStack pillItem) {
        PillEffectRegistry.executeAllEffects(player, pillItem);
    }

    private void updatePillItemLore(ItemStack pillItem, PillData pillData, Player player) {
        java.util.List<String> pillNames = pillData.getPillNames();
        PillItem.updatePillLore(pillItem, pillNames);
        PlayerInventory inventory = player.getInventory();
        if (inventory.getItemInMainHand() != null &&
                PillItem.isPillItem(inventory.getItemInMainHand()) &&
                PillItem.getPillId(inventory.getItemInMainHand()).equals(pillData.getId())) {
            inventory.setItemInMainHand(pillItem);
        }
        else if (inventory.getItemInOffHand() != null &&
                PillItem.isPillItem(inventory.getItemInOffHand()) &&
                PillItem.getPillId(inventory.getItemInOffHand()).equals(pillData.getId())) {
            inventory.setItemInOffHand(pillItem);
        }
        else {
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && PillItem.isPillItem(item) &&
                        PillItem.getPillId(item).equals(pillData.getId())) {
                    inventory.setItem(i, pillItem);
                    break;
                }
            }
        }
        player.updateInventory();
    }
}
