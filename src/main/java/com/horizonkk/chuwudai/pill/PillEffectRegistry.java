package com.horizonkk.chuwudai.pill;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.handlers.AttributePackHandler;
import com.horizonkk.chuwudai.pill.handlers.PotionEffectHandler;
import com.horizonkk.chuwudai.pill.handlers.TuPoHandler;
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
        tryExecuteAllEffects(player, pillItem);
    }

    /** 执行丹药效果；纯突破丹全部失败时返回 false，调用方应保留丹药。 */
    public static boolean tryExecuteAllEffects(Player player, ItemStack pillItem) {
        if (pillItem == null || !pillItem.hasItemMeta()) {
            return false;
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

        if (AttributePackHandler.applyAttributePack(player, pillItem)) {
            appliedEffects.add(AttributePackHandler.describe(pillItem));
        }

        // 应用药水效果（effect_*_time 配置）
        PotionEffectHandler.applyPotionEffects(player, pillItem);

        // 最后发放永久突破加成，避免其他 Handler 抛错后丹药未扣除却已增加概率。
        TuPoHandler.Result breakthrough = TuPoHandler.applyEffects(player, pillItem);
        appliedEffects.addAll(breakthrough.appliedEffects());

        // 记录日志（可选）
        if (!appliedEffects.isEmpty()) {
            Chuwudai.getInstance().getLogger().info(
                    "玩家 " + player.getName() + " 服用丹药，应用效果: " + String.join(", ", appliedEffects)
            );
        }
        // 混合丹有任一效果生效即消耗，防止成功的永久加成被重复领取。
        return !breakthrough.hasTags() || !appliedEffects.isEmpty()
                || PotionEffectHandler.hasAnyPotionEffect(pillItem);
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
     * 检查物品是否包含任何丹药效果（含动态 tupo_<境界> 标签）
     */
    public static boolean hasAnyEffect(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (TuPoHandler.hasAnyEffect(item)) {
            return true;
        }
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

        return AttributePackHandler.hasAttributePack(item)
                || PotionEffectHandler.hasAnyPotionEffect(item);
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
