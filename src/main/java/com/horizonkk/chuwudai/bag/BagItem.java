package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.utlis.BundleStyle;
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

    public static ItemStack createBag(int size, String id, String style) {
        Material material = Material.PAPER; // 使用PAPER材质
        int customModelData = getBundleCustomModelData(style); // 根据样式获取customModelData
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        // 设置名称
        String name = getStyledBagName(size, style);
        meta.setDisplayName(name.replace("&", "§"));
        // 创建完整的Lore
        List<String> lore = createCompleteLore(id, size, style, new HashMap<>());
        meta.setLore(lore);
        // 设置自定义模型数据
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        // 添加NBT数据
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(BAG_ID_KEY, PersistentDataType.STRING, id);
        data.set(BAG_SIZE_KEY, PersistentDataType.INTEGER, size);

        item.setItemMeta(meta);
        return item;
    }

    // 创建储物袋物品（不指定样式，使用默认棕色）
    public static ItemStack createBag(int size, String id) {
        return createBag(size, id, "brown"); // 调用带样式参数的方法，传入默认棕色
    }

    // 根据样式获取CustomModelData
    private static int getBundleCustomModelData(String style) {
        return BundleStyle.fromStyleName(style).getCustomModelData();
    }

    // 获取带样式的储物袋名称
    private static String getStyledBagName(int size, String style) {
        String baseName = getBaseBagName(size);
        String styleColor = getStyleColorCode(style);
        return styleColor + baseName;
    }

    // 获取基础储物袋名称
    private static String getBaseBagName(int size) {
        return "储物袋";
    }

    // 获取样式的颜色代码
    private static String getStyleColorCode(String style) {
        return BundleStyle.fromStyleName(style).getColorCode();
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
        // 标记是否已经添加了基本信息、操作提示、灵石信息和容量信息
        boolean addedStorageInfo = false;
        boolean addedCapacityInfo = false;
        // 复制旧Lore，跳过需要替换的部分
        for (String line : oldLore) {
            // 跳过旧的灵石信息行
            if (line.contains("当前存储:") ||
                    line.contains("下品灵石:") ||
                    line.contains("中品灵石:") ||
                    line.contains("上品灵石:") ||
                    line.contains("极品灵石:")) {
                if (!addedStorageInfo) {
                    // 只保留第一次遇到的"当前存储:"行，后续的灵石行跳过
                    lore.add("§7当前存储:");
                    lore.add("  §f下品灵石: §7" + stoneCounts.getOrDefault("&f下品灵石", 0));
                    lore.add("  §a中品灵石: §7" + stoneCounts.getOrDefault("&a中品灵石", 0));
                    lore.add("  §e上品灵石: §7" + stoneCounts.getOrDefault("&e上品灵石", 0));
                    lore.add("  §c极品灵石: §7" + stoneCounts.getOrDefault("&c极品灵石", 0));
                    addedStorageInfo = true;
                }
                continue; // 跳过这些行
            }
            // 跳过旧的容量信息行
            if (line.contains("储物大小:") || line.contains("当前容量:")) {
                if (!addedCapacityInfo && bagData != null) {
                    // 添加新的容量信息
                    lore.add("§f储物大小: §7" + bagData.getUsedSlots() + "/" + bagData.getSize() + " 槽位");
                    lore.add("§f当前容量: " + formatProgressBar(bagData));
                    addedCapacityInfo = true;
                } else if (!addedCapacityInfo) {
                    // 如果没有BagData，添加默认信息
                    lore.add("§f储物大小: §70/0 槽位");
                    lore.add("§f当前容量: §70% §8||||||||||||||||||||");
                    addedCapacityInfo = true;
                }
                continue; // 跳过这些行
            }
            // 保留其他行
            lore.add(line);
        }
        // 如果还没有添加灵石信息，添加它
        if (!addedStorageInfo) {
            lore.add("§7当前存储:");
            lore.add("  §f下品灵石: §7" + stoneCounts.getOrDefault("&f下品灵石", 0));
            lore.add("  §a中品灵石: §7" + stoneCounts.getOrDefault("&a中品灵石", 0));
            lore.add("  §e上品灵石: §7" + stoneCounts.getOrDefault("&e上品灵石", 0));
            lore.add("  §c极品灵石: §7" + stoneCounts.getOrDefault("&c极品灵石", 0));
        }
        // 如果还没有添加容量信息，添加它
        if (!addedCapacityInfo) {
            if (bagData != null) {
                lore.add("§f储物大小: §7" + bagData.getUsedSlots() + "/" + bagData.getSize() + " 槽位");
                lore.add("§f当前容量: " + formatProgressBar(bagData));
            } else {
                lore.add("§f储物大小: §70/0 槽位");
                lore.add("§f当前容量: §70% §8||||||||||||||||||||");
            }
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
    private static List<String> createCompleteLore(String bagId, int size, String style, Map<String, Integer> stoneCounts) {
        List<String> lore = new ArrayList<>();

        // 添加样式信息作为第一行
        String styleDisplayName = getStyleDisplayName(style);
        String styleColor = getStyleColorCode(style);
        lore.add("§7样式: " + styleColor + styleDisplayName);
        lore.add("§8ID: " + bagId);

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

    private static String getStyleDisplayName(String style) {
        return BundleStyle.fromStyleName(style).getChineseName();
    }

}