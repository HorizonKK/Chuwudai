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
import java.util.HashMap;
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
        ConfigurationSection bagTypesSection = Chuwudai.getInstance().getConfig().getConfigurationSection("bag-types");
        if (bagTypesSection != null) {
            ConfigurationSection typeInfo = bagTypesSection.getConfigurationSection(String.valueOf(size));
            if (typeInfo != null) {
                name = typeInfo.getString("name", "储物袋");
            }
        }

        meta.setDisplayName(name.replace("&", "§"));

        // 创建完整的Lore
        List<String> lore = createCompleteLore(id, size, new HashMap<>());
        meta.setLore(lore);

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

    // 更新储物袋Lore显示灵石数量和容量信息
    public static void updateBagLore(ItemStack bagItem, Map<String, Integer> stoneCounts) {
        if (!isBagItem(bagItem)) return;

        ItemMeta meta = bagItem.getItemMeta();
        List<String> lore = meta.getLore();

        if (lore == null) {
            lore = new ArrayList<>();
        }

        // 获取储物袋ID以获取BagData
        String bagId = getBagId(bagItem);
        BagData bagData = null;
        if (bagId != null) {
            bagData = Chuwudai.getInstance().getBagManager().getBagData(bagId);
        }
        // 重建Lore
        lore = rebuildLoreWithBagInfo(lore, stoneCounts, bagData);

        meta.setLore(lore);
        bagItem.setItemMeta(meta);
    }

    // 重建Lore包含储物袋信息
    private static List<String> rebuildLoreWithBagInfo(List<String> oldLore,
                                                       Map<String, Integer> stoneCounts, BagData bagData) {
        List<String> lore = new ArrayList<>();
        // 第一部分：储物袋基本信息和ID
        boolean foundBaseInfo = false;
        for (String line : oldLore) {
            if (line.contains("ID:") || line.contains("容量:")) {
                lore.add(line);
                foundBaseInfo = true;
            } else if (!line.contains("当前存储:") &&
                    !line.contains("下品灵石:") &&
                    !line.contains("中品灵石:") &&
                    !line.contains("上品灵石:") &&
                    !line.contains("极品灵石:") &&
                    !line.contains("可通过置于背包") &&
                    !line.contains("储物大小:") &&
                    !line.contains("当前容量:")) {
                // 保留其他非储物袋信息的行
                lore.add(line);
            }
        }
        // 如果没有找到基本信息行，添加一个空行
        if (!foundBaseInfo) {
            lore.add("");
        }
        // 第二部分：操作提示
        lore.add("§8可通过置于背包的储物袋槽位中右键点击打开储物袋");
        lore.add("");

        // 第三部分：灵石存储信息
        lore.add("§7当前存储:");
        lore.add("  §f下品灵石: §7" + stoneCounts.getOrDefault("&f下品灵石", 0));
        lore.add("  §a中品灵石: §7" + stoneCounts.getOrDefault("&a中品灵石", 0));
        lore.add("  §e上品灵石: §7" + stoneCounts.getOrDefault("&e上品灵石", 0));
        lore.add("  §c极品灵石: §7" + stoneCounts.getOrDefault("&c极品灵石", 0));

        // 第四部分：容量信息（如果有BagData）
        if (bagData != null) {
            lore.add("");
            lore.add("§f储物大小: §7" + bagData.getUsedSlots() + "/" + bagData.getSize() + " 槽位");
            lore.add("§f当前容量: " + formatProgressBar(bagData));
        } else {
            // 如果没有BagData，显示默认信息
            lore.add("");
            lore.add("§f储物大小: §70/0 槽位");
            lore.add("§f当前容量: §70% §8||||||||||||||||||||");
        }

        return lore;
    }

    // 格式化进度条
    private static String formatProgressBar(BagData bagData) {
        int used = bagData.getUsedSlots();
        int total = bagData.getSize();
        int percentage = bagData.getCapacityPercentage();
        StringBuilder progressBar = new StringBuilder();
        // 添加百分比文字
        progressBar.append("§7").append(percentage).append("% ");
        // 从配置获取进度条设置
        int barLength = Chuwudai.getInstance().getConfig().getInt("progress-bar.length", 20);
        String filledColor = Chuwudai.getInstance().getConfig().getString("progress-bar.filled-color", "&a");
        String emptyColor = Chuwudai.getInstance().getConfig().getString("progress-bar.empty-color", "&8");
        boolean showPercentage = Chuwudai.getInstance().getConfig().getBoolean("progress-bar.show-percentage", true);
        // 生成进度条
        int filledBars = (percentage * barLength) / 100;
        int emptyBars = barLength - filledBars;
        progressBar.append(filledColor.replace("&", "§"));
        for (int i = 0; i < filledBars; i++) {
            progressBar.append("|");
        }
        progressBar.append(emptyColor.replace("&", "§"));
        for (int i = 0; i < emptyBars; i++) {
            progressBar.append("|");
        }

        return progressBar.toString();
    }

    // 创建完整的储物袋Lore（用于新创建储物袋）
    private static List<String> createCompleteLore(String bagId, int size, Map<String, Integer> stoneCounts) {
        List<String> lore = new ArrayList<>();
        // 获取配置中的bag-types信息
        ConfigurationSection bagTypesSection = Chuwudai.getInstance().getConfig().getConfigurationSection("bag-types");
        if (bagTypesSection != null) {
            ConfigurationSection typeInfo = bagTypesSection.getConfigurationSection(String.valueOf(size));
            if (typeInfo != null) {
                List<String> baseLore = typeInfo.getStringList("lore");
                for (String line : baseLore) {
                    lore.add(line.replace("&", "§").replace("%id%", bagId));
                }
            } else {
                // 默认Lore
                lore.add("§7容量: " + size + "格");
                lore.add("§8ID: " + bagId);
            }
        } else {
            // 默认Lore
            lore.add("§7容量: " + size + "格");
            lore.add("§8ID: " + bagId);
        }
        // 添加操作提示
        lore.add("§8可通过置于背包的储物袋槽位中右键点击打开储物袋");
        lore.add("");
        // 添加灵石存储信息
        lore.add("§7当前存储:");
        lore.add("  §f下品灵石: §70");
        lore.add("  §a中品灵石: §70");
        lore.add("  §e上品灵石: §70");
        lore.add("  §c极品灵石: §70");
        // 添加容量信息
        lore.add("");
        lore.add("§f储物大小: §70/" + size + " 槽位");
        lore.add("§f当前容量: §70% §8||||||||||||||||||||");
        return lore;
    }
}