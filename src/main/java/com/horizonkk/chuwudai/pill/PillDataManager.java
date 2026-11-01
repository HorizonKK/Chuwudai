package com.horizonkk.chuwudai.pill;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.utlis.DatabaseManager;
import com.horizonkk.chuwudai.utlis.EfficientItemSerializationUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class PillDataManager {
    private boolean mysql() { return Chuwudai.getInstance().getSharedStorage()!=null; }
    private com.horizonkk.chuwudai.storage.MySqlRepository.Key key(String id) { return new com.horizonkk.chuwudai.storage.MySqlRepository.Key(com.horizonkk.chuwudai.storage.MySqlRepository.Kind.PILL, id); }


    private final Map<String, PillData> loadedPills = new HashMap<>();
    private final String storageType;
    private final File dataFolder;
    private final String dbPath;

    public PillDataManager() {
        // 从配置获取存储类型
        this.storageType = Chuwudai.getInstance().getConfig().getString("storage.type", "yaml");

        if ("sqlite".equalsIgnoreCase(storageType)) {
            // SQLite存储
            String dbFile = Chuwudai.getInstance().getConfig().getString("storage.sqlite.file", "bags.db");
            this.dbPath = Chuwudai.getInstance().getDataFolder() + "/" + dbFile;
            this.dataFolder = null;

            // 初始化数据库连接池
            // Pool is initialized once by Chuwudai.
        } else if ("mysql".equalsIgnoreCase(storageType)) {
            this.dbPath=null; this.dataFolder=null;
        } else {
            // YAML存储（默认）
            String folder = Chuwudai.getInstance().getConfig().getString("storage.yaml.folder", "pills");
            this.dataFolder = new File(Chuwudai.getInstance().getDataFolder(), folder);
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            this.dbPath = null;
        }
    }

    public boolean pillExists(String pillId) {
        if(mysql()) { Chuwudai.getInstance().getSharedStorage().get(key(pillId)); return true; }
        if ("sqlite".equalsIgnoreCase(storageType)) {
            return pillExistsInSQLite(pillId);
        } else {
            return pillExistsInYAML(pillId);
        }
    }

    private boolean pillExistsInSQLite(String pillId) {
        String sql = "SELECT COUNT(*) FROM pills WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pillId);
            ResultSet rs = stmt.executeQuery();

            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("检查丹瓶是否存在时出错: " + e.getMessage());
            return false;
        }
    }

    private boolean pillExistsInYAML(String pillId) {
        File pillFile = new File(dataFolder, pillId + ".yml");
        return pillFile.exists();
    }

    public PillData loadPill(String pillId) {
        if(mysql()) return (PillData)Chuwudai.getInstance().getSharedStorage().get(key(pillId));
        // 如果已经在内存中，直接返回
        if (loadedPills.containsKey(pillId)) {
            return loadedPills.get(pillId);
        }

        PillData pillData = null;
        if ("sqlite".equalsIgnoreCase(storageType)) {
            pillData = loadPillFromSQLite(pillId);
        } else {
            pillData = loadPillFromYAML(pillId);
        }

        if (pillData != null) {
            loadedPills.put(pillId, pillData);
        }

        return pillData;
    }

    private PillData loadPillFromSQLite(String pillId) {
        String sql = "SELECT size, data FROM pills WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pillId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int size = rs.getInt("size");
                String data = rs.getString("data");

                PillData pillData = new PillData(pillId, size);

                // 解压缩数据后反序列化物品
                Map<Integer, ItemStack> items = deserializeCompressedItems(data);
                pillData.setAllItems(items);

                return pillData;
            }
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("从SQLite加载丹瓶数据时出错: " + e.getMessage());
        }

        return null;
    }

    private PillData loadPillFromYAML(String pillId) {
        File pillFile = new File(dataFolder, pillId + ".yml");
        if (!pillFile.exists()) {
            return null; // 文件不存在，返回null
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(pillFile);

        int size = yml.getInt("size", 9);
        PillData pillData = new PillData(pillId, size);

        // 加载物品
        org.bukkit.configuration.ConfigurationSection itemsSection = yml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                int slot = Integer.parseInt(key);
                ItemStack item = itemsSection.getItemStack(key);
                if (item != null) {
                    if (pillData.isValidPillItem(item)) {
                        pillData.setItem(slot, item);
                    }
                }
            }
        }

        return pillData;
    }

    public void savePill(PillData pillData) {
        if(mysql()) { Chuwudai.getInstance().getSharedStorage().touch(key(pillData.getId())); return; }
        if ("sqlite".equalsIgnoreCase(storageType)) {
            savePillToSQLite(pillData);
        } else {
            savePillToYAML(pillData);
        }
    }

    private void savePillToSQLite(PillData pillData) {
        String sql = """
            INSERT OR REPLACE INTO pills (id, size, data, last_modified)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, pillData.getId());
            stmt.setInt(2, pillData.getSize());
            // 压缩数据后存储
            stmt.setString(3, serializeCompressedItems(pillData.getAllItems()));

            stmt.executeUpdate();

            // 更新内存中的缓存
            loadedPills.put(pillData.getId(), pillData);
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("保存丹瓶数据到SQLite时出错: " + e.getMessage());
        }
    }

    private void savePillToYAML(PillData pillData) {
        File pillFile = new File(dataFolder, pillData.getId() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();

        yml.set("id", pillData.getId());
        yml.set("size", pillData.getSize());

        // 保存物品
        for (int i = 0; i < pillData.getSize(); i++) {
            ItemStack item = pillData.getItem(i);
            if (item != null && pillData.isValidPillItem(item)) {
                yml.set("items." + i, item);
            }
        }

        try {
            yml.save(pillFile);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().severe("保存丹瓶数据到YAML时出错: " + e.getMessage());
        }
    }

    public void loadAllPills() {
        if(mysql()) return;
        if ("sqlite".equalsIgnoreCase(storageType)) {
            loadAllPillsFromSQLite();
        } else {
            loadAllPillsFromYAML();
        }
    }

    private void loadAllPillsFromSQLite() {
        String sql = "SELECT id, size, data FROM pills";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                String pillId = rs.getString("id");
                int size = rs.getInt("size");
                String data = rs.getString("data");

                PillData pillData = new PillData(pillId, size);
                Map<Integer, ItemStack> items = deserializeCompressedItems(data);
                pillData.setAllItems(items);

                loadedPills.put(pillId, pillData);
                count++;
            }

            Chuwudai.getInstance().getLogger().info("已从SQLite加载 " + count + " 个丹瓶");
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("从SQLite加载所有丹瓶时出错: " + e.getMessage());
        }
    }

    private void loadAllPillsFromYAML() {
        File[] pillFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (pillFiles == null) return;

        int count = 0;
        for (File file : pillFiles) {
            String pillId = file.getName().replace(".yml", "");
            PillData pillData = loadPillFromYAML(pillId);
            if (pillData != null) {
                loadedPills.put(pillId, pillData);
                count++;
            }
        }

        Chuwudai.getInstance().getLogger().info("已从YAML加载 " + count + " 个丹瓶");
    }

    public void saveAllPills() {
        if(mysql()) return;
        int count = 0;
        for (PillData pillData : loadedPills.values()) {
            savePill(pillData);
            count++;
        }
        Chuwudai.getInstance().getLogger().info("已保存 " + count + " 个丹瓶");
    }

    public void close() {
        if ("sqlite".equalsIgnoreCase(storageType)) {
            // Pool is closed once by Chuwudai.
        }
    }

    // 压缩序列化方法
    private String serializeCompressedItems(Map<Integer, ItemStack> items) {
        String serialized = EfficientItemSerializationUtil.serializeItems(items);

        // 使用Deflater压缩数据
        try {
            byte[] input = serialized.getBytes("UTF-8");
            Deflater deflater = new Deflater();
            deflater.setInput(input);
            deflater.finish();

            byte[] buffer = new byte[1024];
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();

            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            deflater.end();
            byte[] output = outputStream.toByteArray();

            // Base64编码压缩后的数据
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            e.printStackTrace();
            // 如果压缩失败，返回原始数据
            return serialized;
        }
    }

    // 解压缩反序列化方法
    private Map<Integer, ItemStack> deserializeCompressedItems(String compressedData) {
        try {
            // Base64解码
            byte[] compressedBytes = Base64.getDecoder().decode(compressedData);

            // 解压缩
            Inflater inflater = new Inflater();
            inflater.setInput(compressedBytes);

            byte[] buffer = new byte[1024];
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            inflater.end();
            String decompressed = outputStream.toString("UTF-8");

            // 反序列化
            return EfficientItemSerializationUtil.deserializeItems(decompressed);
        } catch (Exception e) {
            e.printStackTrace();
            // 如果解压缩失败，尝试直接反序列化原始数据
            return EfficientItemSerializationUtil.deserializeItems(compressedData);
        }
    }
}
