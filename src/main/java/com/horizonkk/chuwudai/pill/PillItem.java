package com.horizonkk.chuwudai.pill;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class PillItem {
    
    private static final NamespacedKey PILL_ID_KEY = new NamespacedKey(Chuwudai.getInstance(), "pill_id");
    private static final NamespacedKey PILL_SIZE_KEY = new NamespacedKey(Chuwudai.getInstance(), "pill_size");

    // 创建丹瓶物品
    public static ItemStack createPill(int size, String id) {
        Material material = Material.GLASS_BOTTLE; // 默认材质
        int customModelData = 10031; // 默认自定义模型数据
        
        // 从配置中获取材质信息
        ConfigurationSection pillTypesSection = Chuwudai.getInstance().getConfig().getConfigurationSection("pill-types");
        if (pillTypesSection != null) {
            ConfigurationSection typeInfo = pillTypesSection.getConfigurationSection(String.valueOf(size));
            if (typeInfo != null) {
                String materialName = typeInfo.getString("material");
                if (materialName != null) {
                    try {
                        material = Material.valueOf(materialName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        Chuwudai.getInstance().getLogger().warning("无效的材质名称: " + materialName + ", 使用默认材质");
                    }
                }
                
                customModelData = typeInfo.getInt("custom-model-data", customModelData);
            }
        }
        
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        
        // 设置名称
        String name = "丹瓶";
        if (pillTypesSection != null) {
            ConfigurationSection typeInfo = pillTypesSection.getConfigurationSection(String.valueOf(size));
            if (typeInfo != null) {
                name = typeInfo.getString("name", "丹瓶");
            }
        }
        meta.setDisplayName(name.replace("&", "§"));
        
        // 创建完整的Lore
        List<String> lore = createCompleteLore(id, size);
        meta.setLore(lore);
        
        // 设置自定义模型数据（如果配置了）
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        
        // 添加NBT数据
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(PILL_ID_KEY, PersistentDataType.STRING, id);
        data.set(PILL_SIZE_KEY, PersistentDataType.INTEGER, size);
        
        item.setItemMeta(meta);
        return item;
    }

    // 从物品获取丹瓶ID
    public static String getPillId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        if (data.has(PILL_ID_KEY, PersistentDataType.STRING)) {
            return data.get(PILL_ID_KEY, PersistentDataType.STRING);
        }
        return null;
    }

    // 检查是否是丹瓶
    public static boolean isPillItem(ItemStack item) {
        return getPillId(item) != null;
    }

    // 更新丹瓶Lore显示前9格的丹药名称
    public static void updatePillLore(ItemStack pillItem, List<String> pillNames) {
        if (!isPillItem(pillItem)) return;

        ItemMeta meta = pillItem.getItemMeta();
        List<String> lore = meta.getLore();

        if (lore == null) {
            lore = new ArrayList<>();
        }

        // 获取丹瓶ID以获取PillData
        String pillId = getPillId(pillItem);
        PillData pillData = null;
        if (pillId != null) {
            pillData = Chuwudai.getInstance().getPillManager().getPillData(pillId);
        }
        
        // 重建Lore包含丹瓶信息
        lore = rebuildLoreWithPillInfo(lore, pillNames, pillData);

        meta.setLore(lore);
        pillItem.setItemMeta(meta);
    }

    // 重建Lore包含丹瓶信息
    private static List<String> rebuildLoreWithPillInfo(List<String> oldLore, List<String> pillNames, PillData pillData) {
        List<String> lore = new ArrayList<>();

        // 识别并提取基础信息（非重复部分）
        List<String> baseInfo = new ArrayList<>();
        boolean inPillSection = false;
        boolean inCapacitySection = false;

        for (String line : oldLore) {
            if (line.contains("容量: ") && line.contains("格") && line.contains("ID: ")) {
                // 基础信息行，保留
                baseInfo.add(line);
            } else if (line.contains("可通过置于背包")) {
                // 操作提示行，保留
                baseInfo.add(line);
            } else if (line.contains("当前丹药:")) {
                // 开始丹药信息段
                inPillSection = true;
                continue; // 不保留旧的丹药标题行
            } else if (line.matches("\\s*§7\\[\\d+\\]\\s.*")) {
                // 丹药名称行，跳过
                continue;
            } else if (line.contains("丹瓶大小:") || line.contains("当前容量:")) {
                // 容量信息段
                inCapacitySection = true;
                continue; // 不保留旧的容量信息行
            } else if (line.trim().isEmpty() && !baseInfo.isEmpty() &&
                    (baseInfo.get(baseInfo.size()-1).contains("丹瓶") ||
                            baseInfo.get(baseInfo.size()-1).contains("容量"))) {
                // 保留分割空行
                continue; // 跳过空行以避免重复
            } else {
                // 其他行保留
                baseInfo.add(line);
            }
        }

        // 添加基础信息
        lore.addAll(baseInfo);

        // 添加丹药信息
        lore.add("§7当前丹药:");
        for (int i = 0; i < Math.min(9, pillNames.size()); i++) {
            String pillName = pillNames.get(i);
            if (pillName != null) {
                lore.add("  §7[" + (i+1) + "] §f" + pillName);
            } else {
                lore.add("  §7[" + (i+1) + "] §7空");
            }
        }

        // 找到容量信息应该插入的位置（通常在丹药信息之后）
        lore.add("");
        if (pillData != null) {
            lore.add("§f丹瓶大小: §7" + pillData.getUsedSlots() + "/" + pillData.getSize() + " 槽位");
            lore.add("§f当前容量: " + formatProgressBar(pillData));
        } else {
            lore.add("§f丹瓶大小: §70/0 槽位");
            lore.add("§f当前容量: §70% §8||||||||||||||||||||");
        }

        return lore;
    }

    // 格式化进度条
    private static String formatProgressBar(PillData pillData) {
        int used = pillData.getUsedSlots();
        int total = pillData.getSize();
        int percentage = pillData.getCapacityPercentage();
        StringBuilder progressBar = new StringBuilder();
        
        // 添加百分比文字
        progressBar.append("§7").append(percentage).append("% ");
        
        // 从配置获取进度条设置
        int barLength = Chuwudai.getInstance().getConfig().getInt("progress-bar.length", 20);
        String filledColor = Chuwudai.getInstance().getConfig().getString("progress-bar.filled-color", "&a");
        String emptyColor = Chuwudai.getInstance().getConfig().getString("progress-bar.empty-color", "&8");
        
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

    // 创建完整的丹瓶Lore（用于新创建丹瓶）
    private static List<String> createCompleteLore(String pillId, int size) {
        List<String> lore = new ArrayList<>();
        
        // 获取配置中的pill-types信息
        ConfigurationSection pillTypesSection = Chuwudai.getInstance().getConfig().getConfigurationSection("pill-types");
        if (pillTypesSection != null) {
            ConfigurationSection typeInfo = pillTypesSection.getConfigurationSection(String.valueOf(size));
            if (typeInfo != null) {
                List<String> baseLore = typeInfo.getStringList("lore");
                for (String line : baseLore) {
                    lore.add(line.replace("&", "§").replace("%id%", pillId));
                }
            } else {
                // 默认Lore
                lore.add("§7容量: " + size + "格");
                lore.add("§8ID: " + pillId);
            }
        } else {
            // 默认Lore
            lore.add("§7容量: " + size + "格");
            lore.add("§8ID: " + pillId);
        }
        
        // 添加操作提示
        lore.add("§8可通过置于背包的丹瓶槽位中右键点击打开丹瓶");
        lore.add("");
        
        // 添加丹药信息
        lore.add("§7当前丹药:");
        for (int i = 0; i < 9; i++) {
            lore.add("  §7[" + (i+1) + "] §7空");
        }
        
        // 添加容量信息
        lore.add("");
        lore.add("§f丹瓶大小: §70/" + size + " 槽位");
        lore.add("§f当前容量: §70% §8||||||||||||||||||||");
        
        return lore;
    }
}
