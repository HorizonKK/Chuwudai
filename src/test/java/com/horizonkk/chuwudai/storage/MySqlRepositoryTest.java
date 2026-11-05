package com.horizonkk.chuwudai.storage;

import com.zaxxer.hikari.HikariConfig;
import java.sql.*;
import java.util.*;
import static com.horizonkk.chuwudai.storage.MySqlRepository.*;
import static com.horizonkk.chuwudai.storage.StorageAccessException.Reason.*;

/** Standalone integration test. Run against an isolated MySQL 5.7.44 instance, no Maven required. */
public final class MySqlRepositoryTest {
    private static int assertions;
    private static void check(boolean condition,String message) { assertions++; if(!condition) throw new AssertionError(message); }
    private static void fails(StorageAccessException.Reason reason,Runnable action) {
        try { action.run(); throw new AssertionError("Expected "+reason); }
        catch(StorageAccessException e) { check(e.getReason()==reason,"Wrong failure: "+e.getReason()); }
    }
    private static HikariConfig config(String url) {
        HikariConfig c=new HikariConfig(); c.setJdbcUrl(url); c.setUsername("root"); c.setPassword(""); c.setMaximumPoolSize(3); c.setMinimumIdle(1); c.addDataSourceProperty("sslMode","DISABLED"); c.addDataSourceProperty("serverTimezone","UTC"); return c;
    }
    public static void main(String[] args) throws Exception {
        String server="jdbc:mysql://127.0.0.1:33317/";
        Class.forName("com.mysql.cj.jdbc.Driver");
        try(Connection c=DriverManager.getConnection(server+"?useSSL=false","root","");Statement s=c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS chuwudai_storage_test CHARACTER SET utf8mb4");
            try(ResultSet r=s.executeQuery("SELECT VERSION()")) { r.next(); check(r.getString(1).startsWith("5.7.44"),"Test requires MySQL 5.7.44"); }
        }
        String url=server+"chuwudai_storage_test", prefix="test_"+Long.toUnsignedString(System.nanoTime(),36)+"_";
        try(MySqlRepository a=new MySqlRepository(config(url),prefix,"server-a:boot1",30);
            MySqlRepository b=new MySqlRepository(config(url),prefix,"server-b:boot1",30);
            Connection admin=DriverManager.getConnection(url+"?useSSL=false","root",""); Statement sql=admin.createStatement()) {
            String id=a.create(Kind.BAG,90); Key key=new Key(Kind.BAG,id); Lease first=a.acquire(key,false);
            fails(IN_USE,()->b.acquire(key,false));
            String large="丹药-NBT-".repeat(20000); Snapshot snapshot=new Snapshot(90,Map.of(0,large,1,"page-two"),UUID.randomUUID().toString());
            check(a.save(first,0,snapshot)==1,"First commit");
            check(a.save(first,0,snapshot)==1,"Retry must not increment version twice");
            fails(LEASE_LOST,()->a.save(first,0,new Snapshot(90,Map.of(),UUID.randomUUID().toString())));
            a.release(first); Lease second=b.acquire(key,false);
            check(second.pages().get(0).equals(large),"LONGTEXT >64 KiB round trip");
            check(second.epoch()>first.epoch(),"Fence increases");
            fails(LEASE_LOST,()->a.save(first,1,new Snapshot(90,Map.of(),UUID.randomUUID().toString())));
            // Fail metadata update after pages were modified: all changes must roll back.
            sql.execute("CREATE TRIGGER "+prefix+"fail BEFORE UPDATE ON "+prefix+"bags FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure'");
            fails(UNAVAILABLE,()->b.save(second,1,new Snapshot(90,Map.of(0,"wrong"),UUID.randomUUID().toString())));
            sql.execute("DROP TRIGGER "+prefix+"fail");
            b.release(second); Lease afterRollback=a.acquire(key,false);
            check(afterRollback.pages().get(0).equals(large) && afterRollback.pages().size()==2,"Transaction rollback restores pages");
            a.save(afterRollback,1,new Snapshot(99,Map.of(),UUID.randomUUID().toString()));
            a.release(afterRollback); Lease empty=b.acquire(key,false);
            check(empty.size()==99 && empty.pages().isEmpty(),"Resize and clear pages");
            sql.executeUpdate("UPDATE "+prefix+"container_leases SET expires_at='2000-01-01' WHERE kind='BAG' AND id='"+id+"'");
            Lease takeover=a.acquire(key,false);
            fails(LEASE_LOST,()->b.renew(empty));
            b.release(empty); fails(IN_USE,()->b.acquire(key,false));
            a.release(takeover);
            Key temp=new Key(Kind.TEMP,UUID.randomUUID().toString()); Lease tempLease=a.acquire(temp,true);
            a.save(tempLease,0,new Snapshot(54,Map.of(0,"temp-items"),UUID.randomUUID().toString())); a.release(tempLease);
            Lease tempOther=b.acquire(temp,true); check(tempOther.pages().get(0).equals("temp-items"),"Temp box cross-instance persistence"); b.release(tempOther);
            Key pill=new Key(Kind.PILL,a.create(Kind.PILL,27)); Lease pillLease=a.acquire(pill,false);
            a.save(pillLease,0,new Snapshot(27,Map.of(0,"pills"),UUID.randomUUID().toString())); a.release(pillLease);
            Lease pillOther=b.acquire(pill,false); check(pillOther.pages().get(0).equals("pills"),"Pill persistence"); b.release(pillOther);
            fails(NOT_FOUND,()->a.acquire(new Key(Kind.BAG,"missing"),false));
            Key race=new Key(Kind.BAG,a.create(Kind.BAG,27));
            var barrier=new java.util.concurrent.CyclicBarrier(2);
            var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var left=executor.submit(()->{barrier.await();try {return a.acquire(race,false);} catch(StorageAccessException e) {if(e.getReason()!=IN_USE) throw e;return null;}});
                var right=executor.submit(()->{barrier.await();try {return b.acquire(race,false);} catch(StorageAccessException e) {if(e.getReason()!=IN_USE) throw e;return null;}});
                Lease l=left.get(),r=right.get();check((l==null)!=(r==null),"Exactly one concurrent acquirer wins");
                if(l!=null) a.release(l);else b.release(r);
            } finally { executor.shutdownNow(); }
            // Lock wait is bounded on 5.7 without NOWAIT/SKIP LOCKED.
            admin.setAutoCommit(false);
            sql.executeQuery("SELECT * FROM "+prefix+"container_leases WHERE kind='BAG' AND id='"+race.id()+"' FOR UPDATE").close();
            long start=System.nanoTime();fails(UNAVAILABLE,()->b.acquire(race,false));
            check(System.nanoTime()-start<java.util.concurrent.TimeUnit.SECONDS.toNanos(5),"Lock wait timeout is bounded");
            admin.rollback();admin.setAutoCommit(true);
            check(PayloadCodec.decompress(PayloadCodec.compress(large)).equals(large),"Compression round trip");
            fails(CORRUPT,()->PayloadCodec.decompress("invalid"));
            // The expired owner cannot remove a newer owner's lease.
            for(String table:List.of("bag_pages","bags","pills","temp_boxes","container_leases","schema_version")) sql.execute("DROP TABLE "+prefix+table);
        }
        System.out.println("PASS: "+assertions+" storage integration assertions on MySQL 5.7.44");
    }
}
