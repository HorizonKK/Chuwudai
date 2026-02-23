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
        // 检查是否是丹瓶
        if (item != null && PillItem.isPillItem(item)) {
            event.setCancelled(true); // 取消原生食用行为
            String pillId = PillItem.getPillId(item);
            if (pillId != null) {
                // 获取丹瓶数据
                PillData pillData = Chuwudai.getInstance().getPillManager().getPillData(pillId);
                if (pillData != null) {
                    // 从索引0开始查找有效丹药
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
                        // 执行丹药效果
                        executePillEffect(player, foundPill);
                        // 消耗找到的槽位的丹药
                        if (foundPill.getAmount() > 1) {
                            foundPill.setAmount(foundPill.getAmount() - 1);
                            pillData.setItem(foundSlot, foundPill);
                        } else {
                            pillData.removeItem(foundSlot);
                        }
                        // 保存丹瓶数据
                        Chuwudai.getInstance().getPillManager().savePillData(pillId);
                        // 更新丹瓶Lore
                        updatePillItemLore(item, pillData, player);
                        // 发送成功消息
                        String pillName = foundPill.hasItemMeta() ?
                                foundPill.getItemMeta().getDisplayName() :
                                foundPill.getType().name();
                        player.sendMessage(ColorUtils.translateColorCodes(
                                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                        "&a服用丹药: &f" + pillName + " &a成功"));
                        // 重要：不消耗丹瓶物品
                    } else {
                        // 没有找到任何有效丹药
                        player.sendMessage(ColorUtils.translateColorCodes(
                                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                        "&c丹瓶中没有可服用的丹药"));
                        // 仍需更新Lore以反映当前状态
                        updatePillItemLore(item, pillData, player);
                    }
                }
            }
        }
    }


    /**
     * 执行丹药效果
     * @param player 玩家
     * @param pillItem 丹药物品
     */
    private void executePillEffect(Player player, ItemStack pillItem) {
        // 检查物品是否包含NBT数据（如饱食度增加量）
        if (pillItem.hasItemMeta()) {
            ItemMeta meta = pillItem.getItemMeta();
            // 从ItemMeta中获取持久化数据
            PersistentDataContainer container = meta.getPersistentDataContainer();
            // 使用正确的命名空间键
            NamespacedKey foodKey = new NamespacedKey("liandan", "dan_yao_food");
            if (container.has(foodKey, PersistentDataType.INTEGER)) {
                int foodIncrease = container.get(foodKey, PersistentDataType.INTEGER);
                if (foodIncrease > 0) {
                    applySaturationEffect(player, foodIncrease);
                }
            }
        }
        // 这里可以扩展其他效果，如药水效果等
    }

    /**
     * 应用饱食度效果
     * @param player 玩家
     * @param foodIncrease 食物增加量
     */
    private void applySaturationEffect(Player player, int foodIncrease) {
        // 获取玩家当前饱食度
        int currentFoodLevel = player.getFoodLevel();
        int maxFoodLevel = 20;
        // 计算新的饱食度（不超过最大值）
        int newFoodLevel = Math.min(currentFoodLevel + foodIncrease, maxFoodLevel);
        player.setFoodLevel(newFoodLevel);
        // 设置饱和度
        float currentSaturation = player.getSaturation();
        float newSaturation = currentSaturation + foodIncrease;
        player.setSaturation(newSaturation);
        // 发送消息
        player.sendMessage("§a[丹药效果] 您的饱食度增加了 " + foodIncrease + " 点，当前饱食度: " + newFoodLevel);
    }

    /**
     * 更新丹瓶物品的Lore
     * @param pillItem 丹瓶物品
     * @param pillData 丹瓶数据
     */
    private void updatePillItemLore(ItemStack pillItem, PillData pillData, Player player) {
        java.util.List<String> pillNames = pillData.getPillNames();
        PillItem.updatePillLore(pillItem, pillNames);
        // 将更新后的物品重新设置到玩家背包中
        PlayerInventory inventory = player.getInventory();
        // 检查丹瓶是否在主手
        if (inventory.getItemInMainHand() != null &&
                PillItem.isPillItem(inventory.getItemInMainHand()) &&
                PillItem.getPillId(inventory.getItemInMainHand()).equals(pillData.getId())) {
            inventory.setItemInMainHand(pillItem);
        }
        // 检查副手
        else if (inventory.getItemInOffHand() != null &&
                PillItem.isPillItem(inventory.getItemInOffHand()) &&
                PillItem.getPillId(inventory.getItemInOffHand()).equals(pillData.getId())) {
            inventory.setItemInOffHand(pillItem);
        }
        // 检查背包其他位置
        else {
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && PillItem.isPillItem(item) &&
                        PillItem.getPillId(item).equals(pillData.getId())) {
                    inventory.setItem(i, pillItem);
                    break; // 找到并更新后退出循环
                }
            }
        }
        // 强制更新玩家背包显示
        player.updateInventory();
    }
}
