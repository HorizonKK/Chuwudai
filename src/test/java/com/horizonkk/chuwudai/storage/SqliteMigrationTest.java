package com.horizonkk.chuwudai.storage;

import com.zaxxer.hikari.HikariConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import static com.horizonkk.chuwudai.storage.MySqlRepository.*;
import static com.horizonkk.chuwudai.storage.StorageAccessException.Reason.*;

/** Opt-in integration test: isolated MySQL 5.7.44 on port 33317 and generated SQLite fixtures only. */
public final class SqliteMigrationTest {
    private static int assertions;
    private static final SqliteMigrationPayload CODEC = new SqliteMigrationPayload();
    private static void check(boolean condition, String message) {
        assertions++;
        if(!condition) throw new AssertionError(message);
    }
    private static void fails(StorageAccessException.Reason reason, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + reason); }
        catch(StorageAccessException e) { check(e.getReason()==reason, "Wrong failure: " + e.getReason()); }
    }
    private static String base64(String yaml) {
        return Base64.getEncoder().encodeToString(yaml.getBytes(StandardCharsets.UTF_8));
    }
    private static String raw(int... slots) {
        if(slots.length==0) return base64("{}\n");
        StringBuilder yaml = new StringBuilder("items:\n");
        for(int slot:slots) yaml.append("  '").append(slot).append("':\n")
                .append("    ==: org.bukkit.inventory.ItemStack\n")
                .append("    v: 4671\n    type: PAPER\n    amount: 3\n")
                .append("    meta:\n      ==: ItemMeta\n      meta-type: UNSPECIFIC\n")
                .append("      display-name: '丹药测试'\n")
                .append("      internal: 'AAECAw=='\n")
                .append("      custom: {nested: [1, 2, '001'], flag: true}\n");
        return base64(yaml.toString());
    }
    private static String compressed(int... slots) { return PayloadCodec.compress(raw(slots)); }
    private static Snapshot snapshot(int size, int... slots) {
        return new Snapshot(size, CODEC.encode(Kind.BAG, CODEC.read(raw(slots), size, null, false)), UUID.randomUUID().toString());
    }
    private static void put(Connection c, Kind kind, String id, int size, String data) throws SQLException {
        String sql=kind==Kind.TEMP ? "INSERT INTO temp_boxes(id,data) VALUES (?,?)"
                : "INSERT INTO " + kind.table + "(id,size,data) VALUES (?,?,?)";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,id);
            if(kind==Kind.TEMP) p.setString(2,data);
            else { p.setInt(2,size); p.setString(3,data); }
            p.executeUpdate();
        }
    }
    private static void page(Connection c, String id, int page, String data) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO bag_pages(bag_id,page,data) VALUES (?,?,?)")) {
            p.setString(1,id); p.setInt(2,page); p.setString(3,data); p.executeUpdate();
        }
    }
    private static HikariConfig config(String url) {
        HikariConfig c=new HikariConfig();
        c.setJdbcUrl(url); c.setUsername("root"); c.setPassword("");
        c.setMaximumPoolSize(3); c.setMinimumIdle(1);
        c.addDataSourceProperty("sslMode","DISABLED"); c.addDataSourceProperty("serverTimezone","UTC");
        return c;
    }
    private static Map<String,Long> counts(Connection c, String prefix) throws SQLException {
        Map<String,Long> counts=new TreeMap<>();
        for(String table:List.of("bags","bag_pages","pills","temp_boxes","container_leases","schema_version")) {
            try(Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COUNT(*) FROM " + prefix + table)) {
                r.next(); counts.put(table,r.getLong(1));
            }
        }
        return counts;
    }
    private static void verify(MySqlRepository repo, Kind kind, String id, int size, int... slots) {
        Lease lease=repo.acquire(new Key(kind,id),false);
        try {
            check(lease.size()==size,"Preserve capacity: " + id);
            Map<Integer,Object> actual=new TreeMap<>();
            for(var page:lease.pages().entrySet()) actual.putAll(CODEC.read(page.getValue(),size,kind==Kind.BAG ? page.getKey() : null,true));
            check(actual.equals(CODEC.read(raw(slots),size,null,false)),"Preserve slots and nested metadata: " + id);
        } finally { repo.release(lease); }
    }
    private static void payloadChecks() {
        Map<Integer,Object> items=CODEC.read(compressed(0,46,89),90,null,true);
        Map<Integer,String> pages=CODEC.encode(Kind.BAG,items);
        check(pages.keySet().equals(Set.of(0,1)),"Split legacy bag at 45 slots");
        check(CODEC.read(pages.get(1),90,1,true).keySet().equals(Set.of(46,89)),"Keep absolute slot numbers");
        check(items.equals(CODEC.read(raw(0,46,89),90,null,true)),"Uncompressed legacy fallback");
        check(CODEC.encode(Kind.BAG,CODEC.read(raw(),90,null,false)).isEmpty(),"Omit empty pages");
        fails(CORRUPT,()->CODEC.read(compressed(46),90,0,true));
        fails(CORRUPT,()->CODEC.read(compressed(90),90,null,true));
        fails(CORRUPT,()->CODEC.read("broken",90,null,true));
        fails(CORRUPT,()->CODEC.read(base64("items: {'0': {type: PAPER}}"),90,null,false));
        fails(CORRUPT,()->CODEC.read(Base64.getEncoder().encodeToString(new byte[]{(byte)0xc3,0x28}),90,null,false));
        try {
            CODEC.read(base64("items:\n  '0': {}\n  '0': {}\n"),90,null,false);
            throw new AssertionError("Duplicate YAML keys accepted");
        } catch(org.yaml.snakeyaml.error.YAMLException expected) { assertions++; }
    }
    public static void main(String[] args) throws Exception {
        payloadChecks();
        Class.forName("org.sqlite.JDBC"); Class.forName("com.mysql.cj.jdbc.Driver");
        Path root=Path.of("target","storage-check"); Files.createDirectories(root);
        Path folder=Files.createTempDirectory(root,"migration-").toAbsolutePath();
        Path source=folder.resolve("bags.db"), reports=folder.resolve("reports");
        String temp=UUID.randomUUID().toString();
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:" + source); Statement s=c.createStatement()) {
            s.execute("CREATE TABLE bags(id TEXT PRIMARY KEY,size INTEGER NOT NULL,data TEXT NOT NULL)");
            s.execute("CREATE TABLE bag_pages(bag_id TEXT,page INTEGER,data TEXT,PRIMARY KEY(bag_id,page))");
            s.execute("CREATE TABLE pills(id TEXT PRIMARY KEY,size INTEGER NOT NULL,data TEXT NOT NULL)");
            s.execute("CREATE TABLE temp_boxes(id TEXT PRIMARY KEY,data TEXT NOT NULL)");
            put(c,Kind.BAG,"legacy",90,compressed(0,46));
            put(c,Kind.BAG,"paged",90,compressed(2)); // Stale legacy data must not override pages.
            page(c,"paged",0,compressed(1)); page(c,"paged",1,compressed(89));
            put(c,Kind.BAG,"empty",540,"__PAGED_STORAGE_V1__");
            put(c,Kind.BAG,"existing",90,compressed(2));
            put(c,Kind.BAG,"busy",90,compressed(2));
            put(c,Kind.BAG,"broken",90,"not-base64");
            put(c,Kind.PILL,"pill",27,raw(26));
            put(c,Kind.TEMP,temp,54,raw(53));
        }
        byte[] sourceHash=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        String server="jdbc:mysql://127.0.0.1:33317/", database="chuwudai_storage_test";
        try(Connection c=DriverManager.getConnection(server+"?useSSL=false","root",""); Statement s=c.createStatement()) {
            try(ResultSet r=s.executeQuery("SELECT VERSION()")) { r.next(); check(r.getString(1).startsWith("5.7.44"),"Requires isolated MySQL 5.7.44"); }
            s.execute("CREATE DATABASE IF NOT EXISTS " + database + " CHARACTER SET utf8mb4");
        }
        String url=server+database, prefix="migration_"+Long.toUnsignedString(System.nanoTime(),36)+"_";
        try(Connection admin=DriverManager.getConnection(url+"?useSSL=false","root",""); Statement sql=admin.createStatement()) {
            try(MySqlRepository repo=new MySqlRepository(config(url),prefix,"migration-test",300)) {
                repo.importContainer(new Key(Kind.BAG,"existing"),snapshot(90,5));
                repo.importContainer(new Key(Kind.BAG,"busy"),snapshot(90,6));
                Lease busy=repo.acquire(new Key(Kind.BAG,"busy"),false);
                Map<String,Long> before=counts(admin,prefix);
                SqliteMigration.Result preview=new SqliteMigration(repo).run(source,reports,true);
                check(preview.scanned()==8 && preview.imported().equals(Map.of(Kind.BAG,3L,Kind.PILL,1L,Kind.TEMP,1L)),"Preview imports all container types");
                check(preview.existing()==1 && preview.busy()==1 && preview.failed()==1,"Preview reports conflicts and corrupt payloads");
                check(before.equals(counts(admin,prefix)),"Preview must not insert even lease rows");
                SqliteMigration.Result result=new SqliteMigration(repo).run(source,reports,false);
                check(result.imported().equals(preview.imported()),"Import matches preview");
                check(result.existing()==1 && result.busy()==1 && result.failed()==1,"Import skips conflicts and corrupt data");
                check(Files.readString(result.reportFile()).contains("COMPLETE"),"Write completion report");
                verify(repo,Kind.BAG,"legacy",90,0,46);
                verify(repo,Kind.BAG,"paged",90,1,89);
                verify(repo,Kind.BAG,"empty",540);
                verify(repo,Kind.BAG,"existing",90,5);
                verify(repo,Kind.PILL,"pill",27,26);
                verify(repo,Kind.TEMP,temp,54,53);
                check(repo.inspectImport(new Key(Kind.BAG,"broken"))==ImportStatus.READY,"No empty replacement for corrupt container");
                SqliteMigration.Result repeated=new SqliteMigration(repo).run(source,reports,false);
                check(repeated.imported().isEmpty() && repeated.existing()==6 && repeated.busy()==1 && repeated.failed()==1,"Repeated import never overwrites");
                repo.renew(busy); repo.release(busy);
                verify(repo,Kind.BAG,"busy",90,6);
                check(Arrays.equals(sourceHash,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))),"Source file remains byte-for-byte unchanged");

                sql.execute("CREATE TRIGGER " + prefix + "fail BEFORE INSERT ON " + prefix + "bag_pages FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected migration failure'");
                try {
                    fails(UNAVAILABLE,()->repo.importContainer(new Key(Kind.BAG,"atomic"),snapshot(90,0,46)));
                    check(repo.inspectImport(new Key(Kind.BAG,"atomic"))==ImportStatus.READY,"Failed page insert rolls back parent and pages");
                } finally { sql.execute("DROP TRIGGER " + prefix + "fail"); }

                Key emptyTemp=new Key(Kind.TEMP,UUID.randomUUID().toString());
                Lease emptyLease=repo.acquire(emptyTemp,true); repo.release(emptyLease);
                Snapshot replacement=new Snapshot(54,CODEC.encode(Kind.TEMP,CODEC.read(raw(0),54,null,false)),UUID.randomUUID().toString());
                check(repo.importContainer(emptyTemp,replacement)==ImportStatus.EXISTS,"Existing empty temp box is not overwritten");
                verify(repo,Kind.TEMP,emptyTemp.id(),54);

                Path missing=folder.resolve("missing.db");
                fails(UNAVAILABLE,()->new SqliteMigration(repo).run(missing,reports,false));
                check(!Files.exists(missing),"Missing SQLite file must not be created");
                Path old=folder.resolve("old.db");
                try(Connection c=DriverManager.getConnection("jdbc:sqlite:" + old); Statement s=c.createStatement()) {
                    s.execute("CREATE TABLE bags(id TEXT PRIMARY KEY,size INTEGER,data TEXT)");
                    put(c,Kind.BAG,"old-only",90,raw(45));
                }
                check(new SqliteMigration(repo).run(old,reports,false).imported().get(Kind.BAG)==1L,"Old database without optional tables");
                verify(repo,Kind.BAG,"old-only",90,45);
                try(Connection c=DriverManager.getConnection("jdbc:sqlite:" + source)) { page(c,"orphan",0,compressed(0)); }
                Map<String,Long> beforeOrphan=counts(admin,prefix);
                fails(UNAVAILABLE,()->new SqliteMigration(repo).run(source,reports,false));
                check(beforeOrphan.equals(counts(admin,prefix)),"Orphan source pages stop migration before target writes");
            } finally {
                for(String table:List.of("bag_pages","bags","pills","temp_boxes","container_leases","schema_version")) sql.execute("DROP TABLE IF EXISTS " + prefix + table);
            }
        }
        System.out.println("PASS: " + assertions + " SQLite migration assertions; fixtures/reports: " + folder);
    }
}
