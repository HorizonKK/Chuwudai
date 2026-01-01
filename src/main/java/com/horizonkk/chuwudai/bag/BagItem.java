package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BagItem {
    
    private static final NamespacedKey BAG_ID_KEY = new NamespacedKey(Chuwudai.getInstance(), "bag_id");
    private static final NamespacedKey BAG_SIZE_KEY = new NamespacedKey(Chuwudai.getInstance(), "bag_size");
    
    // 创建储物袋物品
    public static ItemStack createBag(int size, String id) {
        Material material;
        switch (size) {
            case 27: material = Material.LEATHER; break;
            case 54: material = Material.IRON_INGOT; break;
            case 81: material = Material.GOLD_INGOT; break;
            case 108: material = Material.DIAMOND; break;
            default: material = Material.CHEST;
        }

        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        // 设置名称
        String name = "储物袋";
        List<String> lore = new ArrayList<>();

        // 安全地获取配置部分
        ConfigurationSection bagTypesSection = Chuwudai.getInstance().getConfig().getConfigurationSection("bag-types");
        if (bagTypesSection != null) {
            ConfigurationSection typeInfo = bagTypesSection.getConfigurationSection(String.valueOf(size));
            if (typeInfo != null) {
                name = typeInfo.getString("name", "储物袋");

                // 获取lore列表
                List<String> loreList = typeInfo.getStringList("lore");
                lore = new ArrayList<>(loreList);
            }
        }

        meta.setDisplayName(name.replace("&", "§"));

        // 设置Lore
        List<String> finalLore = new ArrayList<>();
        for (String line : lore) {
            finalLore.add(line.replace("&", "§").replace("%id%", id));
        }

        // 添加当前灵石数量（初始为空）
        finalLore.add("");
        finalLore.add("§7当前存储:");
        finalLore.add("  §f下品灵石: §70");
        finalLore.add("  §a中品灵石: §70");
        finalLore.add("  §e上品灵石: §70");
        finalLore.add("  §c极品灵石: §70");

        meta.setLore(finalLore);

        // 添加NBT数据
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(BAG_ID_KEY, PersistentDataType.STRING, id);
        data.set(BAG_SIZE_KEY, PersistentDataType.INTEGER, size);

        item.setItemMeta(meta);
        return item;
    }
    
    // 从物品获取储物袋ID
    public static String getBagId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        
        if (data.has(BAG_ID_KEY, PersistentDataType.STRING)) {
            return data.get(BAG_ID_KEY, PersistentDataType.STRING);
        }
        return null;
    }
    
    // 检查是否是储物袋
    public static boolean isBagItem(ItemStack item) {
        return getBagId(item) != null;
    }
    
    // 更新储物袋Lore显示灵石数量
    public static void updateBagLore(ItemStack bagItem, Map<String, Integer> stoneCounts) {
        if (!isBagItem(bagItem)) return;
        
        ItemMeta meta = bagItem.getItemMeta();
        List<String> lore = meta.getLore();
        
        if (lore == null || lore.size() < 8) return;
        
        // 更新灵石数量行
        lore.set(lore.size() - 4, "  §f下品灵石: §7" + stoneCounts.getOrDefault("&f下品灵石", 0));
        lore.set(lore.size() - 3, "  §a中品灵石: §7" + stoneCounts.getOrDefault("&a中品灵石", 0));
        lore.set(lore.size() - 2, "  §e上品灵石: §7" + stoneCounts.getOrDefault("&e上品灵石", 0));
        lore.set(lore.size() - 1, "  §c极品灵石: §7" + stoneCounts.getOrDefault("&c极品灵石", 0));
        
        meta.setLore(lore);
        bagItem.setItemMeta(meta);
    }
}