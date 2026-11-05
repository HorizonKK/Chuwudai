package com.horizonkk.chuwudai.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.*;
import static com.horizonkk.chuwudai.storage.StorageAccessException.Reason.*;

/** JDBC only: callers must never run this repository on the Bukkit thread. */
public final class MySqlRepository implements AutoCloseable {
    public enum Kind { BAG("bags"), PILL("pills"), TEMP("temp_boxes");
        final String table; Kind(String table) { this.table = table; }
    }
    public record Key(Kind kind, String id) {
        public Key { Objects.requireNonNull(kind); if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid container ID"); }
    }
    public record Lease(Key key, long epoch, long version, int size, Map<Integer, String> pages) {}
    public record Snapshot(int size, Map<Integer, String> pages, String operation) {
        public Snapshot { pages = Map.copyOf(pages); }
    }
    public enum ImportStatus { READY, IMPORTED, EXISTS, IN_USE }
    private final HikariDataSource pool;
    private final String prefix, owner;
    private final int ttl;

    public MySqlRepository(HikariConfig config, String prefix, String owner, int ttl) {
        if (!prefix.matches("[A-Za-z0-9_]{1,32}")) throw new IllegalArgumentException("Invalid table prefix");
        if (ttl < 10) throw new IllegalArgumentException("Lease must be at least 10 seconds");
        this.prefix = prefix; this.owner = owner; this.ttl = ttl;
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=2");
        pool = new HikariDataSource(config);
        try { initialize(); } catch (RuntimeException e) { pool.close(); throw e; }
    }
    private String table(String name) { return "`" + prefix + name + "`"; }
    private String table(Key key) { return table(key.kind.table); }
    private void initialize() {
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            for (Kind kind : Kind.values()) s.execute("CREATE TABLE IF NOT EXISTS " + table(kind.table) + " (id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, size INT NOT NULL, data LONGTEXT NOT NULL, version BIGINT NOT NULL DEFAULT 0, last_operation VARCHAR(36) NOT NULL DEFAULT '', updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            s.execute("CREATE TABLE IF NOT EXISTS " + table("bag_pages") + " (bag_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, page INT NOT NULL, data LONGTEXT NOT NULL, PRIMARY KEY(bag_id,page)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            s.execute("CREATE TABLE IF NOT EXISTS " + table("container_leases") + " (kind VARCHAR(8) CHARACTER SET ascii NOT NULL, id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, owner VARCHAR(128) NOT NULL DEFAULT '', epoch BIGINT NOT NULL DEFAULT 0, expires_at DATETIME(3) NOT NULL DEFAULT '2000-01-01 00:00:00', PRIMARY KEY(kind,id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            s.execute("CREATE TABLE IF NOT EXISTS " + table("schema_version") + " (id INT PRIMARY KEY, version INT NOT NULL) ENGINE=InnoDB");
            s.executeUpdate("INSERT IGNORE INTO " + table("schema_version") + " VALUES (1,1)");
            try (ResultSet r = s.executeQuery("SELECT version FROM " + table("schema_version") + " WHERE id=1")) {
                if (!r.next() || r.getInt(1) != 1) throw new SQLException("Unsupported storage schema version");
            }
        } catch (SQLException e) { throw failure(e); }
    }
    private void bindKey(PreparedStatement p, Key key, int offset) throws SQLException {
        p.setString(offset, key.kind.name()); p.setString(offset + 1, key.id);
    }
    private void leaseRow(Connection c, Key key) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + table("container_leases") + " (kind,id) VALUES (?,?) ON DUPLICATE KEY UPDATE id=id")) { bindKey(p,key,1); p.executeUpdate(); }
    }
    public Lease acquire(Key key, boolean createTemp) {
        return transaction(c -> {
            leaseRow(c,key);
            long epoch;
            try (PreparedStatement p = c.prepareStatement("SELECT owner,epoch,expires_at>NOW(3) AS alive FROM " + table("container_leases") + " WHERE kind=? AND id=? FOR UPDATE")) {
                bindKey(p,key,1);
                try (ResultSet r=p.executeQuery()) {
                    r.next();
                    if (r.getBoolean("alive")) throw new StorageAccessException(IN_USE,"容器正在其他会话中使用");
                    epoch = r.getLong("epoch") + 1;
                }
            }
            if (createTemp && key.kind == Kind.TEMP) {
                try (PreparedStatement p=c.prepareStatement("INSERT INTO " + table(key) + " (id,size,data) VALUES (?,54,'') ON DUPLICATE KEY UPDATE id=id")) { p.setString(1,key.id); p.executeUpdate(); }
            }
            int size; long version; Map<Integer,String> pages=new TreeMap<>();
            try (PreparedStatement p=c.prepareStatement("SELECT size,version,data FROM " + table(key) + " WHERE id=?")) {
                p.setString(1,key.id);
                try(ResultSet r=p.executeQuery()) {
                    if (!r.next()) throw new StorageAccessException(NOT_FOUND,"找不到容器 " + key.id);
                    size=r.getInt(1); version=r.getLong(2);
                    if (key.kind != Kind.BAG) pages.put(0,r.getString(3));
                }
            }
            if (key.kind == Kind.BAG) try (PreparedStatement p=c.prepareStatement("SELECT page,data FROM " + table("bag_pages") + " WHERE bag_id=?")) {
                p.setString(1,key.id); try(ResultSet r=p.executeQuery()) { while(r.next()) pages.put(r.getInt(1),r.getString(2)); }
            }
            try (PreparedStatement p=c.prepareStatement("UPDATE " + table("container_leases") + " SET owner=?,epoch=?,expires_at=TIMESTAMPADD(SECOND,?,NOW(3)) WHERE kind=? AND id=?")) {
                p.setString(1,owner); p.setLong(2,epoch); p.setInt(3,ttl); bindKey(p,key,4); p.executeUpdate();
            }
            return new Lease(key,epoch,version,size,Map.copyOf(pages));
        });
    }
    public String create(Kind kind, int size) {
        if (kind == Kind.TEMP || size <= 0) throw new IllegalArgumentException("Invalid creation request");
        for (int attempt=0;attempt<32;attempt++) {
            String id=String.format(Locale.ROOT,"%08d",java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000));
            try(Connection c=pool.getConnection(); PreparedStatement p=c.prepareStatement("INSERT INTO " + table(kind.table) + " (id,size,data) VALUES (?,?,'')")) {
                p.setString(1,id); p.setInt(2,size); p.executeUpdate(); return id;
            } catch(SQLException e) { if(e.getErrorCode()!=1062) throw failure(e); }
        }
        throw new StorageAccessException(UNAVAILABLE,"无法分配容器 ID");
    }
    private void verify(Connection c, Lease lease) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT owner,epoch,expires_at>NOW(3) FROM " + table("container_leases") + " WHERE kind=? AND id=? FOR UPDATE")) {
            bindKey(p,lease.key,1);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next() || !owner.equals(r.getString(1)) || lease.epoch!=r.getLong(2) || !r.getBoolean(3)) throw new StorageAccessException(LEASE_LOST,"容器占用已失效");
            }
        }
    }
    public void renew(Lease lease) {
        transaction(c -> { verify(c,lease);
            try(PreparedStatement p=c.prepareStatement("UPDATE " + table("container_leases") + " SET expires_at=TIMESTAMPADD(SECOND,?,NOW(3)) WHERE kind=? AND id=?")) {
                p.setInt(1,ttl); bindKey(p,lease.key,2); p.executeUpdate();
            } return null;
        });
    }
    public long save(Lease lease, long version, Snapshot snapshot) {
        return transaction(c -> {
            verify(c,lease);
            // An uncertain commit is resolved before any later operation is allowed to run.
            try(PreparedStatement p=c.prepareStatement("SELECT version,last_operation FROM " + table(lease.key) + " WHERE id=? FOR UPDATE")) {
                p.setString(1,lease.key.id);
                try(ResultSet r=p.executeQuery()) {
                    if(!r.next()) throw new StorageAccessException(NOT_FOUND,"容器已不存在");
                    if(snapshot.operation.equals(r.getString(2))) return r.getLong(1);
                    if(version!=r.getLong(1)) throw new StorageAccessException(LEASE_LOST,"容器版本冲突");
                }
            }
            if(lease.key.kind==Kind.BAG) {
                Map<Integer,String> previous=new HashMap<>();
                try(PreparedStatement p=c.prepareStatement("SELECT page,data FROM " + table("bag_pages") + " WHERE bag_id=?")) {
                    p.setString(1,lease.key.id); try(ResultSet r=p.executeQuery()) { while(r.next()) previous.put(r.getInt(1),r.getString(2)); }
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM " + table("bag_pages") + " WHERE bag_id=? AND page=?")) {
                    for(int page:previous.keySet()) if(!snapshot.pages.containsKey(page)) { p.setString(1,lease.key.id); p.setInt(2,page); p.addBatch(); } p.executeBatch();
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO " + table("bag_pages") + " (bag_id,page,data) VALUES (?,?,?) ON DUPLICATE KEY UPDATE data=?")) {
                    for(var entry:snapshot.pages.entrySet()) if(!entry.getValue().equals(previous.get(entry.getKey()))) {
                        p.setString(1,lease.key.id); p.setInt(2,entry.getKey()); p.setString(3,entry.getValue()); p.setString(4,entry.getValue()); p.addBatch();
                    } p.executeBatch();
                }
            }
            try(PreparedStatement p=c.prepareStatement("UPDATE " + table(lease.key) + " SET size=?,data=?,version=version+1,last_operation=?,updated_at=NOW(3) WHERE id=? AND version=?")) {
                p.setInt(1,snapshot.size); p.setString(2,lease.key.kind==Kind.BAG ? "__PAGED_STORAGE_V1__" : snapshot.pages.getOrDefault(0,"")); p.setString(3,snapshot.operation); p.setString(4,lease.key.id); p.setLong(5,version);
                if(p.executeUpdate()!=1) throw new StorageAccessException(LEASE_LOST,"容器版本冲突");
            }
            return version+1;
        });
    }
    public void release(Lease lease) {
        transaction(c -> {
            try(PreparedStatement p=c.prepareStatement("UPDATE " + table("container_leases") + " SET owner='',expires_at='2000-01-01 00:00:00' WHERE kind=? AND id=? AND owner=? AND epoch=?")) {
                bindKey(p,lease.key,1); p.setString(3,owner); p.setLong(4,lease.epoch); p.executeUpdate();
            } return null;
        });
    }
    /** Read-only preview; importContainer repeats these checks under the lease row lock. */
    public ImportStatus inspectImport(Key key) {
        try(Connection c=pool.getConnection()) { return importStatus(c,key); }
        catch(SQLException e) { throw failure(e); }
    }
    private ImportStatus importStatus(Connection c, Key key) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT expires_at>NOW(3) FROM " + table("container_leases") + " WHERE kind=? AND id=?")) {
            bindKey(p,key,1);
            try(ResultSet r=p.executeQuery()) { if(r.next() && r.getBoolean(1)) return ImportStatus.IN_USE; }
        }
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM " + table(key) + " WHERE id=?")) {
            p.setString(1,key.id);
            try(ResultSet r=p.executeQuery()) { if(r.next()) return ImportStatus.EXISTS; }
        }
        if(key.kind==Kind.BAG) try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM " + table("bag_pages") + " WHERE bag_id=? LIMIT 1")) {
            p.setString(1,key.id);
            try(ResultSet r=p.executeQuery()) {
                if(r.next()) throw new StorageAccessException(CORRUPT,"MySQL 中存在没有主记录的储物袋分页: " + key.id);
            }
        }
        return ImportStatus.READY;
    }
    /** Inserts one complete container atomically. Existing IDs and active leases are never overwritten. */
    public ImportStatus importContainer(Key key, Snapshot snapshot) {
        if(snapshot.size<=0 || (key.kind==Kind.TEMP && snapshot.size!=54)) throw new IllegalArgumentException("Invalid import size");
        for(int page:snapshot.pages.keySet()) {
            if(page<0 || (key.kind==Kind.BAG ? page*45L>=snapshot.size : page!=0)) throw new IllegalArgumentException("Invalid import page");
        }
        return transaction(c -> {
            // The same row lock is used by acquire(), including on other servers.
            leaseRow(c,key);
            try(PreparedStatement p=c.prepareStatement("SELECT epoch FROM " + table("container_leases") + " WHERE kind=? AND id=? FOR UPDATE")) {
                bindKey(p,key,1);
                try(ResultSet r=p.executeQuery()) { if(!r.next()) throw new SQLException("Missing import lease row"); }
            }
            ImportStatus status=importStatus(c,key);
            if(status!=ImportStatus.READY) return status;
            try(PreparedStatement p=c.prepareStatement("INSERT INTO " + table(key) + " (id,size,data,last_operation) VALUES (?,?,?,?)")) {
                p.setString(1,key.id); p.setInt(2,snapshot.size);
                p.setString(3,key.kind==Kind.BAG ? "__PAGED_STORAGE_V1__" : snapshot.pages.getOrDefault(0,""));
                p.setString(4,snapshot.operation);
                try { p.executeUpdate(); }
                catch(SQLException e) {
                    // A concurrent create() can win the primary key without taking a lease.
                    if(e.getErrorCode()==1062) return ImportStatus.EXISTS;
                    throw e;
                }
            }
            if(key.kind==Kind.BAG) try(PreparedStatement p=c.prepareStatement("INSERT INTO " + table("bag_pages") + " (bag_id,page,data) VALUES (?,?,?)")) {
                for(var page:snapshot.pages.entrySet()) {
                    p.setString(1,key.id); p.setInt(2,page.getKey()); p.setString(3,page.getValue()); p.addBatch();
                }
                p.executeBatch();
            }
            return ImportStatus.IMPORTED;
        });
    }
    private interface Work<T> { T run(Connection c) throws SQLException; }
    private <T> T transaction(Work<T> work) {
        for(int attempt=0;;attempt++) {
            try(Connection c=pool.getConnection()) {
                c.setAutoCommit(false);
                try { T value=work.run(c); c.commit(); return value; }
                catch(SQLException|RuntimeException e) { try { c.rollback(); } catch(SQLException rollback) { e.addSuppressed(rollback); } throw e; }
            } catch(SQLException e) {
                if(e.getErrorCode()==1213 && attempt<2) continue;
                throw failure(e);
            }
        }
    }
    private StorageAccessException failure(SQLException e) { return new StorageAccessException(UNAVAILABLE,"数据库操作失败 (SQLState="+e.getSQLState()+", code="+e.getErrorCode()+")",e); }
    public void close() { pool.close(); }
}
