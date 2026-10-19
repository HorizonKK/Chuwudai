package com.horizonkk.chuwudai.data;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.utlis.DatabaseManager;
import com.horizonkk.chuwudai.utlis.EfficientItemSerializationUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class BagDataManager {
    private static final String PAGED_STORAGE_MARKER = "__PAGED_STORAGE_V1__";

    private final Map<String, BagData> loadedBags = new ConcurrentHashMap<>();
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

                // 解压缩数据后反序列化物品
                Map<Integer, ItemStack> pageItems = loadPagedItems(conn, bagId);
                if (!pageItems.isEmpty() || hasPagedData(conn, bagId)) {
                    bagData.setAllItems(pageItems);
                } else if (data != null && !data.isEmpty() && !PAGED_STORAGE_MARKER.equals(data)) {
                    Map<Integer, ItemStack> items = deserializeCompressedItems(data);
                    bagData.setAllItems(items);
                    bagData.markLegacyStorage();
                }

                return bagData;
            }
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("从SQLite加载储物袋数据时出错: " + e.getMessage());
        }

        return null;
    }

    private boolean hasPagedData(Connection conn, String bagId) throws SQLException {
        String sql = "SELECT 1 FROM bag_pages WHERE bag_id = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bagId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Map<Integer, ItemStack> loadPagedItems(Connection conn, String bagId) throws SQLException {
        String sql = "SELECT data FROM bag_pages WHERE bag_id = ? ORDER BY page";
        Map<Integer, ItemStack> items = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bagId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.putAll(deserializeCompressedItems(rs.getString("data")));
                }
            }
        }
        return items;
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
                    bagData.setItemSilent(slot, item);
                }
            }
            bagData.updateStoneCounts();
        }

        return bagData;
    }

    public void saveBag(BagData bagData) {
        saveBag(bagData, true);
    }

    public void saveBagSnapshot(BagData bagData) {
        saveBag(bagData, false);
    }

    public void saveBagSize(String bagId, int size) {
        if ("sqlite".equalsIgnoreCase(storageType)) {
            saveBagSizeToSQLite(bagId, size);
        } else {
            saveBagSizeToYAML(bagId, size);
        }
    }

    private void saveBag(BagData bagData, boolean updateCache) {
        if ("sqlite".equalsIgnoreCase(storageType)) {
            saveBagToSQLitePaged(bagData, updateCache);
        } else {
            saveBagToYAML(bagData);
        }
    }

    private void saveBagToSQLitePaged(BagData bagData, boolean updateCache) {
        Set<Integer> pagesToSave = bagData.isLegacyStorage()
                ? bagData.getAllPageIndexes()
                : bagData.getDirtyPages();

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                upsertBagMetadata(conn, bagData.getId(), bagData.getSize());
                savePagedItems(conn, bagData, pagesToSave, bagData.isLegacyStorage());
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

            if (updateCache) {
                loadedBags.put(bagData.getId(), bagData);
            }
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("保存分页储物袋数据到SQLite时出错: " + e.getMessage());
        }
    }

    private void upsertBagMetadata(Connection conn, String bagId, int size) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO bags (id, size, data, last_modified)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bagId);
            stmt.setInt(2, size);
            stmt.setString(3, PAGED_STORAGE_MARKER);
            stmt.executeUpdate();
        }
    }

    private int savePagedItems(Connection conn, BagData bagData, Set<Integer> pagesToSave, boolean replaceAllPages) throws SQLException {
        if (replaceAllPages) {
            try (PreparedStatement deleteAll = conn.prepareStatement("DELETE FROM bag_pages WHERE bag_id = ?")) {
                deleteAll.setString(1, bagData.getId());
                deleteAll.executeUpdate();
            }
        }

        if (pagesToSave == null || pagesToSave.isEmpty()) {
            return 0;
        }

        String upsertSql = """
            INSERT INTO bag_pages (bag_id, page, data, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(bag_id, page) DO UPDATE SET
                data = excluded.data,
                updated_at = CURRENT_TIMESTAMP
            """;
        String deleteSql = "DELETE FROM bag_pages WHERE bag_id = ? AND page = ?";

        int writtenPages = 0;
        try (PreparedStatement upsert = conn.prepareStatement(upsertSql);
             PreparedStatement delete = conn.prepareStatement(deleteSql)) {
            for (int page : pagesToSave) {
                Map<Integer, ItemStack> pageItems = bagData.getItemsInPage(page);
                if (pageItems.isEmpty()) {
                    delete.setString(1, bagData.getId());
                    delete.setInt(2, page);
                    delete.addBatch();
                    continue;
                }

                upsert.setString(1, bagData.getId());
                upsert.setInt(2, page);
                upsert.setString(3, serializeCompressedItems(pageItems));
                upsert.addBatch();
                writtenPages++;
            }
            upsert.executeBatch();
            delete.executeBatch();
        }
        return writtenPages;
    }

    private void saveBagToSQLite(BagData bagData, boolean updateCache) {
        String sql = """
            INSERT OR REPLACE INTO bags (id, size, data, last_modified)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, bagData.getId());
            stmt.setInt(2, bagData.getSize());
            // 压缩数据后存储
            stmt.setString(3, serializeCompressedItems(bagData.getAllItems()));

            stmt.executeUpdate();

            // 更新内存中的缓存
            if (updateCache) {
                loadedBags.put(bagData.getId(), bagData);
            }
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("保存储物袋数据到SQLite时出错: " + e.getMessage());
        }
    }

    private void saveBagSizeToSQLite(String bagId, int size) {
        String sql = "UPDATE bags SET size = ?, last_modified = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, size);
            stmt.setString(2, bagId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Chuwudai.getInstance().getLogger().severe("保存储物袋容量到SQLite时出错: " + e.getMessage());
        }
    }

    private void saveBagToYAML(BagData bagData) {
        File bagFile = new File(dataFolder, bagData.getId() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();

        yml.set("id", bagData.getId());
        yml.set("size", bagData.getSize());

        // 保存物品（只遍历非空槽位）
        for (Map.Entry<Integer, ItemStack> entry : bagData.getAllItems().entrySet()) {
            yml.set("items." + entry.getKey(), entry.getValue());
        }

        try {
            yml.save(bagFile);
        } catch (IOException e) {
            Chuwudai.getInstance().getLogger().severe("保存储物袋数据到YAML时出错: " + e.getMessage());
        }
    }

    private void saveBagSizeToYAML(String bagId, int size) {
        File bagFile = new File(dataFolder, bagId + ".yml");
        if (!bagFile.exists()) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(bagFile.toPath(), StandardCharsets.UTF_8);
            boolean updated = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.startsWith(" ") && !line.startsWith("\t") && line.trim().startsWith("size:")) {
                    lines.set(i, "size: " + size);
                    updated = true;
                    break;
                }
            }

            if (!updated) {
                lines.add("size: " + size);
            }

            Files.write(bagFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Chuwudai.getInstance().getLogger().severe("保存储物袋容量到YAML时出错: " + e.getMessage());
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
                Map<Integer, ItemStack> pageItems = loadPagedItems(conn, bagId);
                if (!pageItems.isEmpty() || hasPagedData(conn, bagId)) {
                    bagData.setAllItems(pageItems);
                } else if (data != null && !data.isEmpty() && !PAGED_STORAGE_MARKER.equals(data)) {
                    Map<Integer, ItemStack> items = deserializeCompressedItems(data);
                    bagData.setAllItems(items);
                    bagData.markLegacyStorage();
                }

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
            if (!bagData.hasDirtyPages()) {
                continue;
            }
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

    public boolean isSQLiteStorage() {
        return "sqlite".equalsIgnoreCase(storageType);
    }

    public MigrationResult migrateLegacyBagsToPagedStorage() {
        if (!isSQLiteStorage()) {
            throw new IllegalStateException("Paged migration is only available for sqlite storage.");
        }

        List<LegacyBagRow> rows = new ArrayList<>();
        String selectSql = "SELECT id, size, data FROM bags WHERE data IS NOT NULL AND data <> '' AND data <> ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, PAGED_STORAGE_MARKER);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LegacyBagRow(
                            rs.getString("id"),
                            rs.getInt("size"),
                            rs.getString("data")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to scan legacy bag rows.", e);
        }

        int migratedBags = 0;
        int failedBags = 0;
        int writtenPages = 0;
        int migratedItems = 0;

        for (LegacyBagRow row : rows) {
            try {
                Map<Integer, ItemStack> items = deserializeCompressedItems(row.data);
                BagData bagData = new BagData(row.id, row.size);
                bagData.setAllItems(items);

                try (Connection conn = DatabaseManager.getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        upsertBagMetadata(conn, bagData.getId(), bagData.getSize());
                        writtenPages += savePagedItems(conn, bagData, bagData.getAllPageIndexes(), true);
                        conn.commit();
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                }

                loadedBags.put(row.id, bagData);
                migratedBags++;
                migratedItems += items.size();
            } catch (Exception e) {
                failedBags++;
                Chuwudai.getInstance().getLogger().severe("Failed to migrate bag " + row.id + " to paged storage: " + e.getMessage());
            }
        }

        return new MigrationResult(rows.size(), migratedBags, failedBags, writtenPages, migratedItems);
    }

    public static final class MigrationResult {
        private final int scannedBags;
        private final int migratedBags;
        private final int failedBags;
        private final int writtenPages;
        private final int migratedItems;

        private MigrationResult(int scannedBags, int migratedBags, int failedBags, int writtenPages, int migratedItems) {
            this.scannedBags = scannedBags;
            this.migratedBags = migratedBags;
            this.failedBags = failedBags;
            this.writtenPages = writtenPages;
            this.migratedItems = migratedItems;
        }

        public int getScannedBags() {
            return scannedBags;
        }

        public int getMigratedBags() {
            return migratedBags;
        }

        public int getFailedBags() {
            return failedBags;
        }

        public int getWrittenPages() {
            return writtenPages;
        }

        public int getMigratedItems() {
            return migratedItems;
        }
    }

    private static final class LegacyBagRow {
        private final String id;
        private final int size;
        private final String data;

        private LegacyBagRow(String id, int size, String data) {
            this.id = id;
            this.size = size;
            this.data = data;
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
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

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
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

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
