package com.horizonkk.chuwudai.data;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.utlis.DatabaseManager;
import com.horizonkk.chuwudai.utlis.ItemSerializationUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class BagDataManager {

    private final Map<String, BagData> loadedBags = new HashMap<>();
    private final String storageType;
    private final File dataFolder;
    private final String dbPath;

    public BagDataManager() {
        // 从配置获取存储类型
        this.storageType = Chuwudai.getInstance().getConfig().getString("storage.type", "yaml");

        if ("sqlite".equalsIgnoreCase(storageType)) {
            // SQLite存储
            String dbFile = Chuwudai.getInstance().getConfig().getString("storage.sqlite.file", "bags.db");
            this.dbPath = Chuwudai.getInstance().getDataFolder() + "/" + dbFile;
            this.dataFolder = null;

            // 初始化数据库连接池
            DatabaseManager.initialize(dbPath);
        } else {
            // YAML存储（默认）
            String folder = Chuwudai.getInstance().getConfig().getString("storage.yaml.folder", "bags");
            this.dataFolder = new File(Chuwudai.getInstance().getDataFolder(), folder);
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            this.dbPath = null;
        }
    }

    public boolean bagExists(String bagId) {
        if ("sqlite".equalsIgnoreCase(storageType)) {
            return bagExistsInSQLite(bagId);
        } else {
            return bagExistsInYAML(bagId);
        }
    }

    private boolean bagExistsInSQLite(String bagId) {
        String sql = "SELECT COUNT(*) FROM bags WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, bagId);
            ResultSet rs = stmt.executeQuery();

            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("检查储物袋是否存在时出错: " + e.getMessage());
            return false;
        }
    }

    private boolean bagExistsInYAML(String bagId) {
        File bagFile = new File(dataFolder, bagId + ".yml");
        return bagFile.exists();
    }

    public BagData loadBag(String bagId) {
        // 如果已经在内存中，直接返回
        if (loadedBags.containsKey(bagId)) {
            return loadedBags.get(bagId);
        }

        BagData bagData = null;
        if ("sqlite".equalsIgnoreCase(storageType)) {
            bagData = loadBagFromSQLite(bagId);
        } else {
            bagData = loadBagFromYAML(bagId);
        }

        if (bagData != null) {
            loadedBags.put(bagId, bagData);
        }

        return bagData;
    }

    private BagData loadBagFromSQLite(String bagId) {
        String sql = "SELECT size, data FROM bags WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, bagId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int size = rs.getInt("size");
                String data = rs.getString("data");

                BagData bagData = new BagData(bagId, size);

                // 从序列化数据反序列化物品
                Map<Integer, ItemStack> items = ItemSerializationUtil.deserializeItems(data);
                bagData.setAllItems(items);

                return bagData;
            }
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("从SQLite加载储物袋数据时出错: " + e.getMessage());
        }

        return null;
    }

    private BagData loadBagFromYAML(String bagId) {
        File bagFile = new File(dataFolder, bagId + ".yml");
        if (!bagFile.exists()) {
            return null; // 文件不存在，返回null
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(bagFile);

        int size = yml.getInt("size", 27);
        BagData bagData = new BagData(bagId, size);

        // 加载物品
        org.bukkit.configuration.ConfigurationSection itemsSection = yml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                int slot = Integer.parseInt(key);
                ItemStack item = itemsSection.getItemStack(key);
                if (item != null) {
                    bagData.setItem(slot, item);
                }
            }
        }

        return bagData;
    }

    public void saveBag(BagData bagData) {
        if ("sqlite".equalsIgnoreCase(storageType)) {
            saveBagToSQLite(bagData);
        } else {
            saveBagToYAML(bagData);
        }
    }

    private void saveBagToSQLite(BagData bagData) {
        String sql = """
            INSERT OR REPLACE INTO bags (id, size, data, last_modified)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, bagData.getId());
            stmt.setInt(2, bagData.getSize());
            stmt.setString(3, ItemSerializationUtil.serializeItems(bagData.getAllItems()));

            stmt.executeUpdate();

            // 更新内存中的缓存
            loadedBags.put(bagData.getId(), bagData);
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("保存储物袋数据到SQLite时出错: " + e.getMessage());
        }
    }

    private void saveBagToYAML(BagData bagData) {
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
            Chuwudai.getInstance().getLogger().severe("保存储物袋数据到YAML时出错: " + e.getMessage());
        }
    }

    public void loadAllBags() {
        if ("sqlite".equalsIgnoreCase(storageType)) {
            loadAllBagsFromSQLite();
        } else {
            loadAllBagsFromYAML();
        }
    }

    private void loadAllBagsFromSQLite() {
        String sql = "SELECT id, size, data FROM bags";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                String bagId = rs.getString("id");
                int size = rs.getInt("size");
                String data = rs.getString("data");

                BagData bagData = new BagData(bagId, size);
                Map<Integer, ItemStack> items = ItemSerializationUtil.deserializeItems(data);
                bagData.setAllItems(items);

                loadedBags.put(bagId, bagData);
                count++;
            }

            Chuwudai.getInstance().getLogger().info("已从SQLite加载 " + count + " 个储物袋");
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("从SQLite加载所有储物袋时出错: " + e.getMessage());
        }
    }

    private void loadAllBagsFromYAML() {
        File[] bagFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (bagFiles == null) return;

        int count = 0;
        for (File file : bagFiles) {
            String bagId = file.getName().replace(".yml", "");
            BagData bagData = loadBagFromYAML(bagId);
            if (bagData != null) {
                loadedBags.put(bagId, bagData);
                count++;
            }
        }

        Chuwudai.getInstance().getLogger().info("已从YAML加载 " + count + " 个储物袋");
    }

    public void saveAllBags() {
        int count = 0;
        for (BagData bagData : loadedBags.values()) {
            saveBag(bagData);
            count++;
        }
        Chuwudai.getInstance().getLogger().info("已保存 " + count + " 个储物袋");
    }

    public void close() {
        if ("sqlite".equalsIgnoreCase(storageType)) {
            DatabaseManager.close();
        }
    }
}
