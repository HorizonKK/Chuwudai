package com.horizonkk.chuwudai.data;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BagDataManager {
    
    private final Map<String, BagData> loadedBags = new HashMap<>();
    private final File dataFolder;
    
    public BagDataManager() {
        this.dataFolder = new File(Chuwudai.getInstance().getDataFolder(), "bags");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }
    
    public boolean bagExists(String bagId) {
        File bagFile = new File(dataFolder, bagId + ".yml");
        return bagFile.exists();
    }

    public BagData loadBag(String bagId) {
        // 如果已经在内存中，直接返回
        if (loadedBags.containsKey(bagId)) {
            return loadedBags.get(bagId);
        }

        File bagFile = new File(dataFolder, bagId + ".yml");
        if (!bagFile.exists()) {
            return null; // 文件不存在，返回null
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(bagFile);

        int size = yml.getInt("size", 27);
        BagData bagData = new BagData(bagId, size);

        // 加载物品
        ConfigurationSection itemsSection = yml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                int slot = Integer.parseInt(key);
                ItemStack item = itemsSection.getItemStack(key);
                if (item != null) {
                    bagData.setItem(slot, item);
                }
            }
        }

        loadedBags.put(bagId, bagData);
        return bagData;
    }
    
    public void saveBag(BagData bagData) {
        File bagFile = new File(dataFolder, bagData.getId() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        
        yml.set("id", bagData.getId());
        yml.set("size", bagData.getSize());
        
        // 保存物品
        for (int i = 0; i < bagData.getSize(); i++) {
            ItemStack item = bagData.getItem(i);
            if (item != null) {
                yml.set("items." + i, item);
            }
        }
        
        try {
            yml.save(bagFile);
        } catch (IOException e) {
            Chuwudai.getInstance().getLogger().severe("保存储物袋数据时出错: " + e.getMessage());
        }
    }
    
    public void loadAllBags() {
        File[] bagFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (bagFiles == null) return;
        
        for (File file : bagFiles) {
            String bagId = file.getName().replace(".yml", "");
            loadBag(bagId);
        }
        
        Chuwudai.getInstance().getLogger().info("已加载 " + loadedBags.size() + " 个储物袋");
    }
    
    public void saveAllBags() {
        for (BagData bagData : loadedBags.values()) {
            saveBag(bagData);
        }
        Chuwudai.getInstance().getLogger().info("已保存 " + loadedBags.size() + " 个储物袋");
    }
}