package com.horizonkk.chuwudai.utlis;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ItemSerializationUtil {

    // 紧凑格式序列化ItemStack
    public static String serializeItem(ItemStack item) {
        if (item == null) return "null";

        Map<String, Object> itemData = new HashMap<>();
        itemData.put("type", item.getType().name());
        itemData.put("amount", item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Map<String, Object> metaMap = new HashMap<>();

            // 序列化显示名称
            if (meta.hasDisplayName()) {
                metaMap.put("name", meta.getDisplayName());
            }

            // 序列化Lore
            if (meta.hasLore()) {
                metaMap.put("lore", meta.getLore());
            }

            // 序列化附魔
            if (meta.hasEnchants()) {
                Map<String, Integer> enchants = new HashMap<>();
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    enchants.put(entry.getKey().getKey().getKey(), entry.getValue());
                }
                metaMap.put("enchants", enchants);
            }

            // 序列化自定义模型数据
            if (meta.hasCustomModelData()) {
                metaMap.put("custom_model_data", meta.getCustomModelData());
            }

            itemData.put("meta", metaMap);
        }

        // 使用紧凑的JSON格式而不是YAML
        return mapToJson(itemData);
    }

    // 批量序列化物品
    public static String serializeItems(Map<Integer, ItemStack> items) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"");
            sb.append(serializeItem(entry.getValue()).replace("\"", "\\\""));
            sb.append("\"");
            first = false;
        }

        sb.append("}");
        return sb.toString();
    }

    // 反序列化
    public static ItemStack deserializeItem(String data) {
        if ("null".equals(data)) return null;

        Map<String, Object> itemData = jsonToMap(data);
        if (itemData == null) return null;

        Material type = Material.valueOf((String) itemData.get("type"));
        int amount = ((Number) itemData.get("amount")).intValue();

        ItemStack item = new ItemStack(type, amount);

        if (itemData.containsKey("meta")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metaMap = (Map<String, Object>) itemData.get("meta");
            ItemMeta meta = item.getItemMeta();

            // 设置显示名称
            if (metaMap.containsKey("name")) {
                meta.setDisplayName((String) metaMap.get("name"));
            }

            // 设置Lore
            if (metaMap.containsKey("lore")) {
                @SuppressWarnings("unchecked")
                java.util.List<String> lore = (java.util.List<String>) metaMap.get("lore");
                meta.setLore(lore);
            }

            // 设置附魔
            if (metaMap.containsKey("enchants")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> enchants = (Map<String, Object>) metaMap.get("enchants");
                for (Map.Entry<String, Object> entry : enchants.entrySet()) {
                    Enchantment enchant = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(entry.getKey()));
                    if (enchant != null) {
                        int level = ((Number) entry.getValue()).intValue();
                        meta.addEnchant(enchant, level, true);
                    }
                }
            }

            // 设置自定义模型数据
            if (metaMap.containsKey("custom_model_data")) {
                meta.setCustomModelData(((Number) metaMap.get("custom_model_data")).intValue());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    // 批量反序列化
    public static Map<Integer, ItemStack> deserializeItems(String data) {
        Map<Integer, ItemStack> items = new HashMap<>();

        if (data == null || data.isEmpty() || "{}".equals(data)) {
            return items;
        }

        // 解析JSON格式的数据
        data = data.trim();
        if (data.startsWith("{") && data.endsWith("}")) {
            data = data.substring(1, data.length() - 1);

            String[] pairs = data.split(",");
            for (String pair : pairs) {
                int colonIndex = pair.indexOf(':');
                if (colonIndex > 0) {
                    String key = pair.substring(0, colonIndex).trim();
                    String value = pair.substring(colonIndex + 1).trim();

                    // 移除引号
                    if (key.startsWith("\"") && key.endsWith("\"")) {
                        key = key.substring(1, key.length() - 1);
                    }
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    try {
                        int slot = Integer.parseInt(key);
                        ItemStack item = deserializeItem(value);
                        items.put(slot, item);
                    } catch (NumberFormatException e) {
                        // 忽略无效的键
                    }
                }
            }
        }

        return items;
    }

    // 简单的JSON解析器（用于基本的键值对）
    private static Map<String, Object> jsonToMap(String json) {
        Map<String, Object> map = new HashMap<>();

        if (json == null || json.isEmpty()) return map;

        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return map;

        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return map;

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex > 0) {
                String key = pair.substring(0, colonIndex).trim();
                String value = pair.substring(colonIndex + 1).trim();

                // 移除引号
                if (key.startsWith("\"") && key.endsWith("\"")) {
                    key = key.substring(1, key.length() - 1);
                }
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                map.put(key, value);
            }
        }

        return map;
    }

    // 简单的Map转JSON
    private static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"");
            sb.append(String.valueOf(entry.getValue()).replace("\"", "\\\""));
            sb.append("\"");
            first = false;
        }

        sb.append("}");
        return sb.toString();
    }
}
