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

    @EventHandler(ignoreCancelled = true)
    public void onPlayerConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        // 丹瓶：从丹瓶中取第一颗丹药服用
        if (PillItem.isPillItem(item)) {
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
                        if (!executePillEffect(player, foundPill)) {
                            return;
                        }
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
            return;
        }
        // 直接吃丹药（物品自身带有 liandan 或 tupo_<境界> NBT 效果）
        if (PillEffectRegistry.hasAnyEffect(item)) {
            event.setCancelled(true);
            if (!executePillEffect(player, item)) {
                return;
            }

            consumeHeldItem(player, item);

            String pillName = item.hasItemMeta() ?
                    item.getItemMeta().getDisplayName() :
                    item.getType().name();
            player.sendMessage(ColorUtils.translateColorCodes(
                    Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                            "&a服用丹药: &f" + pillName + " &a成功"));
        }
    }


    private boolean executePillEffect(Player player, ItemStack pillItem) {
        boolean applied = PillEffectRegistry.tryExecuteAllEffects(player, pillItem);
        if (!applied) {
            player.sendMessage(ColorUtils.translateColorCodes(
                    Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                            "&c丹药效果未生效，未消耗丹药，请联系管理员检查突破配置。"));
        }
        return applied;
    }

    private void consumeHeldItem(Player player, ItemStack consumed) {
        PlayerInventory inventory = player.getInventory();
        ItemStack mainHand = inventory.getItemInMainHand();
        if (mainHand == consumed || mainHand.equals(consumed)) {
            if (mainHand.getAmount() > 1) {
                mainHand.setAmount(mainHand.getAmount() - 1);
            } else {
                inventory.setItemInMainHand(null);
            }
            return;
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand == consumed || offHand.equals(consumed)) {
            if (offHand.getAmount() > 1) {
                offHand.setAmount(offHand.getAmount() - 1);
            } else {
                inventory.setItemInOffHand(null);
            }
        }
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
