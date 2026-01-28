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
                    // 检查槽位0是否有丹药
                    ItemStack pillInSlot0 = pillData.getItem(0);

                    if (pillInSlot0 != null && pillData.isValidPillItem(pillInSlot0)) {
                        // 执行丹药效果
                        executePillEffect(player, pillInSlot0);

                        // 消耗槽位0的丹药
                        if (pillInSlot0.getAmount() > 1) {
                            pillInSlot0.setAmount(pillInSlot0.getAmount() - 1);
                            pillData.setItem(0, pillInSlot0);
                        } else {
                            pillData.removeItem(0);
                        }

                        // 保存丹瓶数据
                        Chuwudai.getInstance().getPillManager().savePillData(pillId);

                        // 更新丹瓶Lore
                        updatePillItemLore(item, pillData);

                        // 发送成功消息
                        String pillName = pillInSlot0.hasItemMeta() ?
                                pillInSlot0.getItemMeta().getDisplayName() :
                                pillInSlot0.getType().name();
                        player.sendMessage(ColorUtils.translateColorCodes(
                                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                        "&a服用丹药: &f" + pillName + " &a成功"));

                        // 重要：不消耗丹瓶物品
                    } else {
                        // 槽位0没有丹药
                        player.sendMessage(ColorUtils.translateColorCodes(
                                Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                                        "&c丹瓶第1格没有可服用的丹药"));

                        // 重要：同样不消耗丹瓶物品
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
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey foodKey = new NamespacedKey(Chuwudai.getInstance(), "dan_yao_food");

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
    private void updatePillItemLore(ItemStack pillItem, PillData pillData) {
        java.util.List<String> pillNames = pillData.getPillNames();
        PillItem.updatePillLore(pillItem, pillNames);
    }
}
