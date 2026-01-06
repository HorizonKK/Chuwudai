package com.horizonkk.chuwudai.utlis;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ItemSerializationUtil {
    
    public static String serializeItems(Map<Integer, ItemStack> items) {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            if (entry.getValue() != null) {
                config.set("items." + entry.getKey(), entry.getValue());
            }
        }
        return config.saveToString();
    }
    
    public static Map<Integer, ItemStack> deserializeItems(String data) {
        YamlConfiguration config = new YamlConfiguration();
        Map<Integer, ItemStack> items = new HashMap<>();
        
        try {
            config.loadFromString(data);
            if (config.contains("items")) {
                for (String key : config.getConfigurationSection("items").getKeys(false)) {
                    ItemStack item = config.getItemStack("items." + key);
                    if (item != null) {
                        items.put(Integer.parseInt(key), item);
                    }
                }
            }
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
        
        return items;
    }
}
