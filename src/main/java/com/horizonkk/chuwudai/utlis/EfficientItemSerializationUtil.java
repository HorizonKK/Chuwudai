package com.horizonkk.chuwudai.utlis;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class EfficientItemSerializationUtil {
    
    // 使用Base64编码的二进制序列化
    public static String serializeItems(Map<Integer, ItemStack> items) {
        YamlConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            if (entry.getValue() != null) {
                config.set("items." + entry.getKey(), entry.getValue());
            }
        }
        
        // 将YAML配置转换为字节数组，然后Base64编码
        String yamlString = config.saveToString();
        return Base64.getEncoder().encodeToString(yamlString.getBytes());
    }
    
    public static Map<Integer, ItemStack> deserializeItems(String data) {
        Map<Integer, ItemStack> items = new HashMap<>();
        
        if (data == null || data.isEmpty()) {
            return items;
        }
        
        try {
            // Base64解码，然后解析YAML
            byte[] decodedBytes = Base64.getDecoder().decode(data);
            String yamlString = new String(decodedBytes);
            
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(yamlString);
            
            if (config.contains("items")) {
                for (String key : config.getConfigurationSection("items").getKeys(false)) {
                    ItemStack item = config.getItemStack("items." + key);
                    if (item != null) {
                        items.put(Integer.parseInt(key), item);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return items;
    }
}
