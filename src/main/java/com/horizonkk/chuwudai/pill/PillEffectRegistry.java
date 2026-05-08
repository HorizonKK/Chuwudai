package com.horizonkk.chuwudai.pill;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.handlers.PotionEffectHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class PillEffectRegistry {

    /**
     * 执行物品的所有丹药效果
     */
    public static void executeAllEffects(Player player, ItemStack pillItem) {
        if (!pillItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = pillItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        List<String> appliedEffects = new ArrayList<>();

        // 遍历所有已注册的效果类型
        for (PillEffectType effectType : PillEffectType.values()) {
            NamespacedKey key = new NamespacedKey("liandan", effectType.getNbtKey());

            if (container.has(key, PersistentDataType.INTEGER)) {
                int value = container.get(key, PersistentDataType.INTEGER);

                // 检查是否有持续时间配置
                NamespacedKey durationKey = new NamespacedKey("liandan", effectType.getNbtKey() + "_duration");
                int duration = 0;
                if (container.has(durationKey, PersistentDataType.INTEGER)) {
                    duration = container.get(durationKey, PersistentDataType.INTEGER);
                }

                try {
                    effectType.getHandler().execute(player, value, duration);
                    appliedEffects.add(effectType.getDisplayName() + "+" + value
                            + (duration > 0 ? "(" + duration + "秒)" : ""));
                } catch (Exception e) {
                    Chuwudai.getInstance().getLogger().warning(
                            "执行丹药效果失败: " + effectType.getNbtKey() + ", 错误: " + e.getMessage()
                    );
                    e.printStackTrace();
                }
            }
        }

        // 应用药水效果（effect_*_time 配置）
        PotionEffectHandler.applyPotionEffects(player, pillItem);

        // 记录日志（可选）
        if (!appliedEffects.isEmpty()) {
            Chuwudai.getInstance().getLogger().info(
                    "玩家 " + player.getName() + " 服用丹药，应用效果: " + String.join(", ", appliedEffects)
            );
        }
    }

    /**
     * 检查物品是否有指定的效果
     */
    public static boolean hasEffect(ItemStack item, PillEffectType effectType) {
        if (!item.hasItemMeta()) {
            return false;
        }

        NamespacedKey key = new NamespacedKey("liandan", effectType.getNbtKey());
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        return container.has(key, PersistentDataType.INTEGER);
    }

    /**
     * 检查物品是否包含任何丹药效果（枚举效果或药水效果）
     */
    public static boolean hasAnyEffect(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        for (PillEffectType type : PillEffectType.values()) {
            NamespacedKey key = new NamespacedKey("liandan", type.getNbtKey());
            if (container.has(key, PersistentDataType.INTEGER)) {
                return true;
            }
        }

        return PotionEffectHandler.hasAnyPotionEffect(item);
    }

    /**
     * 获取物品的指定效果值
     */
    public static int getEffectValue(ItemStack item, PillEffectType effectType) {
        if (!item.hasItemMeta()) {
            return 0;
        }

        NamespacedKey key = new NamespacedKey("liandan", effectType.getNbtKey());
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        if (container.has(key, PersistentDataType.INTEGER)) {
            return container.get(key, PersistentDataType.INTEGER);
        }

        return 0;
    }
}
