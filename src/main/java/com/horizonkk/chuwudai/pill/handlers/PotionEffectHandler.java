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
    }

    public static boolean hasAnyPotionEffect(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();

        for (String effectName : EFFECT_TYPE_MAP.keySet()) {
            for (int amplifier = 0; amplifier <= 10; amplifier++) {
                String nbtKey;
                if (amplifier == 0) {
                    nbtKey = "effect_" + effectName.toLowerCase() + "_time";
                } else {
                    nbtKey = "effect_" + effectName.toLowerCase() + "_time_" + amplifier;
                }

                NamespacedKey key = new NamespacedKey("liandan", nbtKey);
                if (container.has(key, PersistentDataType.INTEGER)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void applyPotionEffects(Player player, ItemStack pillItem) {
        if (!pillItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = pillItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        for (Map.Entry<String, PotionEffectType> entry : EFFECT_TYPE_MAP.entrySet()) {
            String effectName = entry.getKey();
            PotionEffectType effectType = entry.getValue();

            for (int amplifier = 0; amplifier <= 10; amplifier++) {
                String nbtKey;
                if (amplifier == 0) {
                    nbtKey = "effect_" + effectName.toLowerCase() + "_time";
                } else {
                    nbtKey = "effect_" + effectName.toLowerCase() + "_time_" + amplifier;
                }

                NamespacedKey key = new NamespacedKey("liandan", nbtKey);
                if (container.has(key, PersistentDataType.INTEGER)) {
                    int durationTicks = container.get(key, PersistentDataType.INTEGER);

                    if (durationTicks > 0) {
                        PotionEffect effect = new PotionEffect(
                                effectType,
                                durationTicks,
                                amplifier,
                                false,
                                false,
                                true
                        );

                        player.addPotionEffect(effect);

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
