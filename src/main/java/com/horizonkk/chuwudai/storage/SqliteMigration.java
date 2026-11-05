package com.horizonkk.chuwudai.storage;

import org.sqlite.SQLiteConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static com.horizonkk.chuwudai.storage.MySqlRepository.*;

/** One read-only SQLite snapshot; one MySQL transaction per container. Runs on the storage worker. */
public final class SqliteMigration {
    private static final String PAGED = "__PAGED_STORAGE_V1__";
    public record Result(boolean dryRun, Map<Kind, Long> imported, long scanned, long existing,
                         long busy, long failed, Path reportFile) {
        public Result { imported = Map.copyOf(imported); }
    }
    private final MySqlRepository target;
    private final SqliteMigrationPayload codec = new SqliteMigrationPayload();
    private final Map<Kind, Long> imported = new EnumMap<>(Kind.class);
    private long scanned, existing, busy, failed;

    public SqliteMigration(MySqlRepository target) { this.target = target; }

    public Result run(Path source, Path reportFolder, boolean dryRun) {
        Path report = null;
        try {
            if(!Files.isRegularFile(source)) throw new IOException("SQLite 文件不存在: " + source);
            source = source.toRealPath();
            Files.createDirectories(reportFolder);
            report = Files.createTempFile(reportFolder, dryRun ? "sqlite-check-" : "sqlite-import-", ".log");
            try(BufferedWriter log = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
                log.write("mode=" + (dryRun ? "CHECK" : "IMPORT") + "\nsource=" + source + "\n");
                log.flush();
                try {
                    SQLiteConfig config = new SQLiteConfig();
                    config.setReadOnly(true);
                    config.setBusyTimeout(5000);
                    Class.forName("org.sqlite.JDBC");
                    try(Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + source, config.toProperties())) {
                        try(Statement s=sqlite.createStatement()) { s.execute("PRAGMA query_only=ON"); }
                        sqlite.setAutoCommit(false);
                        Set<String> tables = tables(sqlite);
                        if(!tables.contains("bags") && !tables.contains("pills") && !tables.contains("temp_boxes")) {
                            throw new SQLException("源数据库中没有 Chuwudai 容器表");
                        }
                        checkOrphanPages(sqlite, tables);
                        for(Kind kind : Kind.values()) {
                            if(tables.contains(kind.table)) migrateTable(sqlite, kind, tables.contains("bag_pages"), dryRun, log);
                        }
                        sqlite.rollback(); // End the source read snapshot; no source changes are made.
                    }
                    log.write("COMPLETE scanned=" + scanned + " imported/ready=" + imported + " existing=" + existing
                            + " busy=" + busy + " failed=" + failed + "\n");
                } catch(Exception e) {
                    log.write("ABORTED " + clean(e.getMessage()) + "\ncompleted=" + imported + "\n");
                    throw e;
                }
            }
            return new Result(dryRun, imported, scanned, existing, busy, failed, report);
        } catch(Exception e) {
            throw new StorageAccessException(StorageAccessException.Reason.UNAVAILABLE,
                    "SQLite 迁移中止: " + e.getMessage() + (report==null ? "" : "；报告: " + report)
                            + "。已提交的容器会保留，重新执行不会覆盖已有 ID。", e);
        }
    }

    private Set<String> tables(Connection sqlite) throws SQLException {
        Set<String> names = new HashSet<>();
        try(Statement s=sqlite.createStatement(); ResultSet r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while(r.next()) names.add(r.getString(1));
        }
        return names;
    }

    private void checkOrphanPages(Connection sqlite, Set<String> tables) throws SQLException {
        if(!tables.contains("bag_pages")) return;
        String query = tables.contains("bags")
                ? "SELECT bag_id FROM bag_pages p WHERE NOT EXISTS (SELECT 1 FROM bags b WHERE b.id=p.bag_id) LIMIT 1"
                : "SELECT bag_id FROM bag_pages LIMIT 1";
        try(Statement s=sqlite.createStatement(); ResultSet r=s.executeQuery(query)) {
            if(r.next()) throw new SQLException("SQLite 有缺失主记录的储物袋分页: " + r.getString(1));
        }
    }

    private void migrateTable(Connection sqlite, Kind kind, boolean hasPages, boolean dryRun, BufferedWriter log) throws SQLException, IOException {
        String query = kind==Kind.TEMP ? "SELECT id,54 AS size,data FROM temp_boxes ORDER BY id"
                : "SELECT id,size,data FROM " + kind.table + " ORDER BY id";
        try(Statement s=sqlite.createStatement(); ResultSet rows=s.executeQuery(query)) {
            while(rows.next()) {
                if(Thread.currentThread().isInterrupted()) throw new IOException("迁移已被停止");
                scanned++;
                String id = rows.getString("id");
                try {
                    Key key = new Key(kind,id);
                    long rawSize = rows.getLong("size");
                    if(rows.wasNull() || rawSize<=0 || rawSize>Integer.MAX_VALUE) throw new IllegalArgumentException("无效容量");
                    int size = (int)rawSize;
                    if(kind==Kind.TEMP && !UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("暂存箱 UUID 格式无效");
                    Map<Integer, Object> items = kind==Kind.BAG
                            ? readBag(sqlite,id,size,rows.getString("data"),hasPages)
                            : codec.read(rows.getString("data"),size,null,kind==Kind.PILL);
                    Snapshot snapshot = new Snapshot(size,codec.encode(kind,items),UUID.randomUUID().toString());
                    ImportStatus status = dryRun ? target.inspectImport(key) : target.importContainer(key,snapshot);
                    switch(status) {
                        case READY, IMPORTED -> imported.merge(kind,1L,Long::sum);
                        case EXISTS -> existing++;
                        case IN_USE -> busy++;
                    }
                    log.write(status + " " + kind + " " + id + " items=" + items.size() + "\n");
                } catch(StorageAccessException e) {
                    if(e.getReason()!=StorageAccessException.Reason.CORRUPT) throw e;
                    failed++;
                    log.write("FAILED " + kind + " " + clean(id) + " " + clean(e.getMessage()) + "\n");
                } catch(IllegalArgumentException | org.yaml.snakeyaml.error.YAMLException e) {
                    failed++;
                    log.write("FAILED " + kind + " " + clean(id) + " " + clean(e.getMessage()) + "\n");
                }
                log.flush();
            }
        }
    }

    private Map<Integer, Object> readBag(Connection sqlite, String id, int size, String legacy, boolean hasPages) throws SQLException {
        Map<Integer, Object> items = new TreeMap<>();
        boolean paged = false;
        if(hasPages) try(PreparedStatement p=sqlite.prepareStatement("SELECT page,data FROM bag_pages WHERE bag_id=? ORDER BY page")) {
            p.setString(1,id);
            try(ResultSet rows=p.executeQuery()) {
                while(rows.next()) {
                    paged = true;
                    long page = rows.getLong("page");
                    if(rows.wasNull() || page<0 || page>Integer.MAX_VALUE || page*45L>=size) throw new IllegalArgumentException("分页编号越界");
                    Map<Integer, Object> values = codec.read(rows.getString("data"),size,(int)page,true);
                    for(var entry : values.entrySet()) {
                        if(items.putIfAbsent(entry.getKey(),entry.getValue())!=null) throw new IllegalArgumentException("重复物品槽位");
                    }
                }
            }
        }
        // Same precedence as BagDataManager: present page rows are authoritative over the legacy blob.
        if(paged || PAGED.equals(legacy)) return items;
        return codec.read(legacy,size,null,true);
    }

    private static String clean(String text) { return String.valueOf(text).replace('\n',' ').replace('\r',' '); }
}
