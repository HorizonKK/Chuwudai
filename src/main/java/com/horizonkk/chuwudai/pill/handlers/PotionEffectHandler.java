package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class PotionEffectHandler implements PillEffectHandler {
    
    private static final Map<String, PotionEffectType> EFFECT_TYPE_MAP = new HashMap<>();
    
    static {
        // 注册所有支持的药水效果类型
        EFFECT_TYPE_MAP.put("Saturation", PotionEffectType.SATURATION);
        EFFECT_TYPE_MAP.put("Speed", PotionEffectType.SPEED);
        EFFECT_TYPE_MAP.put("Jump", PotionEffectType.JUMP_BOOST);
        EFFECT_TYPE_MAP.put("Strength", PotionEffectType.STRENGTH);
        EFFECT_TYPE_MAP.put("Haste", PotionEffectType.HASTE);
        EFFECT_TYPE_MAP.put("Regeneration", PotionEffectType.REGENERATION);
        EFFECT_TYPE_MAP.put("Resistance", PotionEffectType.RESISTANCE);
        EFFECT_TYPE_MAP.put("Fire_Resistance", PotionEffectType.FIRE_RESISTANCE);
        EFFECT_TYPE_MAP.put("Water_Breathing", PotionEffectType.WATER_BREATHING);
        EFFECT_TYPE_MAP.put("Invisibility", PotionEffectType.INVISIBILITY);
        EFFECT_TYPE_MAP.put("Night_Vision", PotionEffectType.NIGHT_VISION);
        EFFECT_TYPE_MAP.put("Health_Boost", PotionEffectType.HEALTH_BOOST);
        EFFECT_TYPE_MAP.put("Absorption", PotionEffectType.ABSORPTION);
        EFFECT_TYPE_MAP.put("Luck", PotionEffectType.LUCK);
    }
    
    @Override
    public void execute(Player player, int value) {
        // 这个方法不会被直接调用，使用 executeFromItem 方法
    }
    
    /**
     * 从物品中读取并应用所有药水效果
     */
    public static void applyPotionEffects(Player player, ItemStack pillItem) {
        if (!pillItem.hasItemMeta()) {
            return;
        }
        
        ItemMeta meta = pillItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        
        // 遍历所有注册的药水效果类型
        for (Map.Entry<String, PotionEffectType> entry : EFFECT_TYPE_MAP.entrySet()) {
            String effectName = entry.getKey();
            PotionEffectType effectType = entry.getValue();
            
            // 检查是否有该效果的配置（支持多级）
            for (int amplifier = 0; amplifier <= 10; amplifier++) {
                String nbtKey;
                if (amplifier == 0) {
                    nbtKey = "effect_" + effectName + "_time";
                } else {
                    nbtKey = "effect_" + effectName + "_time_" + amplifier;
                }
                
                NamespacedKey key = new NamespacedKey(Chuwudai.getInstance(), nbtKey);
                if (container.has(key, PersistentDataType.INTEGER)) {
                    int durationTicks = container.get(key, PersistentDataType.INTEGER);
                    
                    if (durationTicks > 0) {
                        // 创建并应用药水效果
                        PotionEffect effect = new PotionEffect(
                            effectType,
                            durationTicks,
                            amplifier,
                            false, // 不显示粒子效果（可选）
                            false, // 不显示图标（可选）
                            true   // 环境效果（可选）
                        );
                        
                        player.addPotionEffect(effect);
                        
                        // 记录日志
                        Chuwudai.getInstance().getLogger().info(
                            "玩家 " + player.getName() + " 获得药水效果: " + 
                            effectName + " Lv." + (amplifier + 1) + 
                            " 持续 " + (durationTicks / 20) + " 秒"
                        );
                    }
                }
            }
        }
    }
    
    @Override
    public String getEffectName() {
        return "药水效果";
    }
}
