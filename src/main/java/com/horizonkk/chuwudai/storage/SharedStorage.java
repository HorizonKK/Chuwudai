package com.horizonkk.chuwudai.storage;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.*;
import com.horizonkk.chuwudai.pill.*;
import com.horizonkk.chuwudai.Temporary.TempBoxInventory;
import com.horizonkk.chuwudai.utlis.EfficientItemSerializationUtil;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static com.horizonkk.chuwudai.storage.MySqlRepository.*;
import static com.horizonkk.chuwudai.storage.StorageAccessException.Reason.*;

/** One authoritative, main-thread object per local lease. JDBC and compression run on the worker. */
public final class SharedStorage {
    private final Chuwudai plugin;
    private final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"Chuwudai-storage"); t.setDaemon(true); return t; });
    private final ScheduledExecutorService heartbeats=Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"Chuwudai-leases"); t.setDaemon(true); return t; });
    private final Set<Key> viewChanges=new HashSet<>();
    private final Map<Key,Session> sessions=new ConcurrentHashMap<>();
    private final Map<Key,CompletableFuture<Void>> loading=new HashMap<>();
    private final Map<UUID,Set<Key>> players=new HashMap<>();
    private final long delay,ttl,heartbeat;
    private volatile MySqlRepository repository;
    private volatile boolean stopping;
    private boolean migrating;
    private CompletableFuture<SqliteMigration.Result> migration;
    private final CompletableFuture<Void> ready;
    private static final class Session {
        final Lease lease;
        Object data;
        long changed, acknowledged, due, lastAccess=System.currentTimeMillis();
        volatile long deadline,version;
        volatile Throwable fault;
        boolean closing;
        CompletableFuture<Void> pending=CompletableFuture.completedFuture(null);
        Session(Lease lease,long deadline) { this.lease=lease; this.deadline=deadline; this.version=lease.version(); }
    }
    public SharedStorage(Chuwudai plugin) {
        this.plugin=plugin;
        var c=plugin.getConfig();
        delay=Math.max(1,c.getLong("storage.save-delay-ticks",60))*50;
        ttl=Math.max(10,c.getLong("storage.lease-seconds",30));
        heartbeat=Math.max(1,c.getLong("storage.heartbeat-seconds",10));
        if(heartbeat*2>=ttl) throw new IllegalArgumentException("heartbeat-seconds must be less than half lease-seconds");
        String server=c.getString("storage.server-id","");
        if(server.isBlank() || server.length()>80) throw new IllegalArgumentException("Configure a unique storage.server-id (1-80 characters)");
        String host=c.getString("storage.mysql.host","127.0.0.1");
        String database=c.getString("storage.mysql.database","chuwudai");
        if(!host.matches("[A-Za-z0-9.:-]+") || !database.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid MySQL host/database");
        HikariConfig config=new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://"+host+":"+c.getInt("storage.mysql.port",3306)+"/"+database);
        config.setUsername(c.getString("storage.mysql.username","chuwudai"));
        config.setPassword(c.getString("storage.mysql.password",""));
        config.setMaximumPoolSize(Math.max(1,c.getInt("storage.mysql.maximum-pool-size",10)));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(c.getLong("storage.mysql.connection-timeout-ms",5000));
        config.addDataSourceProperty("connectTimeout","5000");
        config.addDataSourceProperty("socketTimeout","5000");
        config.addDataSourceProperty("characterEncoding","UTF-8");
        config.addDataSourceProperty("serverTimezone","UTC");
        config.addDataSourceProperty("sslMode",c.getString("storage.mysql.ssl-mode","PREFERRED"));
        String prefix=c.getString("storage.mysql.table-prefix","chuwudai_");
        ready=CompletableFuture.runAsync(() -> repository=new MySqlRepository(config,prefix,server+":"+UUID.randomUUID(),(int)ttl),io);
        ready.whenComplete((v,e)->{ if(e!=null) plugin.getLogger().severe("MySQL 初始化失败，容器操作已禁用: "+root(e).getMessage()); });
        heartbeats.scheduleWithFixedDelay(this::renew,heartbeat,heartbeat,TimeUnit.SECONDS);
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    private static Throwable root(Throwable e) { while(e.getCause()!=null && (e instanceof CompletionException || e instanceof ExecutionException)) e=e.getCause(); return e; }
    private void main() { if(!Bukkit.isPrimaryThread()) throw new IllegalStateException("Container API requires the Bukkit main thread"); }
    public <T> CompletableFuture<T> onMain(java.util.concurrent.Callable<T> action) {
        CompletableFuture<T> future=new CompletableFuture<>();
        Runnable r=()->{ try { if(stopping) throw new StorageAccessException(UNAVAILABLE,"插件正在关闭"); future.complete(action.call()); } catch(Throwable e) { future.completeExceptionally(e); } };
        if(stopping) future.completeExceptionally(new StorageAccessException(UNAVAILABLE,"插件正在关闭"));
        else if(Bukkit.isPrimaryThread()) r.run(); else Bukkit.getScheduler().runTask(plugin,r);
        return future;
    }
    public boolean isReady(Key key) {
        Session s=sessions.get(key);
        return s!=null && !s.closing && s.fault==null && System.nanoTime()<s.deadline && !stopping;
    }
    public void check(Key key) {
        main(); Session s=sessions.get(key);
        if(migrating) throw new StorageAccessException(UNAVAILABLE,"SQLite 迁移正在进行，请稍后使用容器");
        if(s==null) throw new StorageAccessException(NOT_READY,"数据尚未加载，请使用异步接口");
        if(s.fault!=null) throw new StorageAccessException(UNAVAILABLE,"容器暂不可用",s.fault);
        if(s.closing || stopping || System.nanoTime()>=s.deadline) throw new StorageAccessException(LEASE_LOST,"容器正在交接或占用已失效");
    }
    public void withViewChange(Key key, Runnable action) {
        main(); boolean added=viewChanges.add(key);
        try { action.run(); } finally { if(added) viewChanges.remove(key); }
    }
    private void checkMutation(Key key) {
        check(key);
        if(!viewChanges.contains(key) && StorageListener.isViewed(key)) throw new StorageAccessException(IN_USE,"容器界面正在使用中");
    }
    private void checkMutation(Key key, Session expected) {
        checkMutation(key);
        if(sessions.get(key)!=expected) throw new StorageAccessException(LEASE_LOST,"数据引用属于旧会话，请重新加载");
    }
    public Object get(Key key) { check(key); Session s=sessions.get(key); s.lastAccess=System.currentTimeMillis(); return s.data; }
    public void touch(Key key) {
        check(key); Session s=sessions.get(key);
        if(s.changed==s.acknowledged) s.due=System.currentTimeMillis()+delay;
        s.lastAccess=System.currentTimeMillis(); s.changed++;
    }
    public CompletableFuture<Void> prepare(Key key) {
        main();
        if(stopping || migrating) return CompletableFuture.failedFuture(new StorageAccessException(UNAVAILABLE,"存储正在关闭或迁移"));
        if(isReady(key)) return CompletableFuture.completedFuture(null);
        if(sessions.containsKey(key)) return CompletableFuture.failedFuture(new StorageAccessException(IN_USE,"容器正在保存、交接或故障处理中"));
        if(loading.containsKey(key)) return loading.get(key);
        CompletableFuture<Void> result=new CompletableFuture<>(); loading.put(key,result);
        ready.thenApplyAsync(v -> {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            for(int attempt=0;;attempt++) {
                long start=System.nanoTime();
                try { Lease l=repository.acquire(key,key.kind()==Kind.TEMP);
                    try {
                        Map<Integer,String> decoded=new TreeMap<>(); l.pages().forEach((page,value)->decoded.put(page,PayloadCodec.decompress(value)));
                        l=new Lease(l.key(),l.epoch(),l.version(),l.size(),Map.copyOf(decoded));
                    } catch(RuntimeException e) { repository.release(l); throw e; }
                    return new Session(l,start+TimeUnit.SECONDS.toNanos(ttl-2)); }
                catch(StorageAccessException e) {
                    if(e.getReason()!=IN_USE || attempt>=2 || System.nanoTime()>=until) throw e;
                    try { Thread.sleep(100); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw e; }
                }
            }
        },io).whenComplete((s,error)-> {
            if(stopping) { if(s!=null) repository.release(s.lease); result.completeExceptionally(new StorageAccessException(UNAVAILABLE,"插件正在关闭")); return; }
            onMain(()-> {
                loading.remove(key);
                if(error!=null) { result.completeExceptionally(root(error)); return null; }
                try {
                    Map<Integer,ItemStack> items=new HashMap<>();
                    if(s.lease.size()<=0) throw new StorageAccessException(CORRUPT,"容器容量无效");
                    for(var page:s.lease.pages().entrySet()) {
                        var decoded=EfficientItemSerializationUtil.deserializeItems(page.getValue());
                        for(var item:decoded.entrySet()) {
                            int slot=item.getKey();
                            if(slot<0 || slot>=s.lease.size() || items.containsKey(slot) || (key.kind()==Kind.BAG && slot/45!=page.getKey())) throw new StorageAccessException(CORRUPT,"容器槽位数据无效");
                            if(key.kind()==Kind.PILL && !new PillData(key.id(),s.lease.size()).isValidPillItem(item.getValue())) throw new StorageAccessException(CORRUPT,"丹瓶包含无效物品");
                            items.put(slot,item.getValue());
                        }
                    }
                    if(key.kind()==Kind.BAG) { BagData b=new BagData(key.id(),s.lease.size()); b.setAllItems(items); b.bindStorage(()->checkMutation(key,s),()->touch(key)); s.data=b; }
                    else if(key.kind()==Kind.PILL) { PillData p=new PillData(key.id(),s.lease.size()); p.setAllItems(items); p.bindStorage(()->checkMutation(key,s),()->touch(key)); s.data=p; }
                    else s.data=items;
                    sessions.put(key,s); check(key); result.complete(null);
                } catch(Throwable e) { sessions.remove(key); io.execute(()->repository.release(s.lease)); result.completeExceptionally(e); }
                return null;
            });
        });
        return result;
    }
    public CompletableFuture<String> create(Kind kind,int size) {
        main();
        if(stopping || migrating) return CompletableFuture.failedFuture(new StorageAccessException(UNAVAILABLE,"存储正在关闭或迁移"));
        return ready.thenApplyAsync(v->repository.create(kind,size),io).thenCompose(id->onMain(()->id));
    }
    public CompletableFuture<SqliteMigration.Result> migrateSqlite(Path source, boolean dryRun) {
        main();
        if(stopping || migrating) return CompletableFuture.failedFuture(new IllegalStateException("已有迁移任务运行中，或插件正在关闭"));
        if(!Bukkit.getOnlinePlayers().isEmpty()) return CompletableFuture.failedFuture(new IllegalStateException("请在无玩家在线时从控制台执行迁移"));
        if(!sessions.isEmpty() || !loading.isEmpty()) return CompletableFuture.failedFuture(new IllegalStateException("容器仍在保存或释放，请等待约 15 秒后重试"));
        migrating=true;
        Path reports=plugin.getDataFolder().toPath().resolve("migration-reports");
        migration=ready.thenApplyAsync(v->new SqliteMigration(repository).run(source,reports,dryRun),io);
        migration.whenComplete((result,error)->{ if(!stopping) onMain(()->{ migrating=false; return null; }); });
        return migration;
    }
    @SuppressWarnings("unchecked")
    private Snapshot snapshot(Session s) {
        Map<Integer,String> pages=new TreeMap<>(); int size;
        if(s.data instanceof BagData bag) {
            size=bag.getSize();
            for(int page:bag.getAllPageIndexes()) { var items=bag.getItemsInPage(page); if(!items.isEmpty()) pages.put(page,EfficientItemSerializationUtil.serializeItems(items)); }
        } else if(s.data instanceof PillData pill) { size=pill.getSize(); pages.put(0,EfficientItemSerializationUtil.serializeItems(pill.getAllItems())); }
        else { size=54; pages.put(0,EfficientItemSerializationUtil.serializeItems((Map<Integer,ItemStack>)s.data)); }
        return new Snapshot(size,pages,UUID.randomUUID().toString());
    }
    public CompletableFuture<Void> flush(Key key) {
        main(); Session s=sessions.get(key);
        if(s==null) return CompletableFuture.failedFuture(new StorageAccessException(NOT_READY,"容器尚未加载，不能确认保存"));
        if(s.closing || s.changed==s.acknowledged) return s.pending;
        Snapshot raw=snapshot(s); long generation=s.changed;
        CompletableFuture<Void> write=CompletableFuture.runAsync(()-> {
            Map<Integer,String> encoded=new TreeMap<>(); raw.pages().forEach((page,value)->encoded.put(page,PayloadCodec.compress(value)));
            Snapshot encodedSnapshot=new Snapshot(raw.size(),encoded,raw.operation());
            try {
                if(s.fault!=null) throw new StorageAccessException(UNAVAILABLE,"Previous save failed",s.fault);
                try { s.version=repository.save(s.lease,s.version,encodedSnapshot); }
                catch(StorageAccessException e) { if(e.getReason()!=UNAVAILABLE) throw e; s.version=repository.save(s.lease,s.version,encodedSnapshot); }
            } catch(Throwable e) { s.fault=e; recovery(key,encodedSnapshot); throw e; }
        },io);
        s.pending=write;
        write.whenComplete((v,e)-> { if(!stopping) onMain(()->{ if(e==null) s.acknowledged=Math.max(s.acknowledged,generation); else plugin.getLogger().severe("容器保存失败 "+key+": "+root(e).getMessage()); return null; }); });
        return write;
    }
    public CompletableFuture<Void> release(Key key) {
        main(); Session s=sessions.get(key);
        if(s==null && !loading.containsKey(key)) return CompletableFuture.completedFuture(null);
        if(s==null) return loading.get(key).thenCompose(v->onMain(()->release(key)).thenCompose(f->f));
        if(s.closing) return s.pending;
        CompletableFuture<Void> saved=flush(key); s.closing=true;
        CompletableFuture<Void> released=saved.thenRunAsync(()->repository.release(s.lease),io);
        s.pending=released;
        released.whenComplete((v,e)->{ if(!stopping) onMain(()->{ if(e==null) sessions.remove(key,s); return null; }); });
        return released;
    }
    private void renew() {
        if(repository==null) return;
        for(Session s:sessions.values()) {
            if(s.fault!=null) continue;
            long start=System.nanoTime();
            try { repository.renew(s.lease); s.deadline=start+TimeUnit.SECONDS.toNanos(ttl-2); }
            catch(Throwable e) { s.fault=e; plugin.getLogger().warning("容器续期失败 "+s.lease.key()+": "+e.getMessage()); }
        }
    }
    public void captureViews() {
        for(Player p:Bukkit.getOnlinePlayers()) captureView(p);
    }
    void captureView(Player player) {
        Object holder=player.getOpenInventory().getTopInventory().getHolder();
        try {
            if(holder instanceof BagInventory b && isReady(new Key(Kind.BAG,b.getBagData().getId()))) b.saveItems();
            else if(holder instanceof PillInventory b && isReady(new Key(Kind.PILL,b.getPillData().getId()))) b.saveItems();
            else if(holder instanceof TempBoxInventory b && isReady(new Key(Kind.TEMP,b.getOwnerUuid().toString()))) plugin.getTempBoxManager().capture(b);
        } catch(StorageAccessException e) { player.closeInventory(); }
    }
    private void tick() {
        if(stopping) return;
        captureViews();
        for(Session s:new ArrayList<>(sessions.values())) if(!s.closing && s.fault==null && s.changed==s.acknowledged && s.pending.isDone() && System.currentTimeMillis()-s.lastAccess>15000 && !StorageListener.isViewed(s.lease.key())) release(s.lease.key());
        for(Session s:sessions.values()) if(!s.closing && s.fault==null && s.changed>s.acknowledged && s.pending.isDone() && System.currentTimeMillis()>=s.due) flush(s.lease.key());
    }
    public Set<Key> carried(Player player) {
        Set<Key> keys=new TreeSet<>(Comparator.comparing((Key k)->k.kind().name()).thenComparing(Key::id));
        for(ItemStack item:player.getInventory().getContents()) {
            if(item==null || item.getAmount()<1) continue;
            if(BagItem.isBagItem(item)) keys.add(new Key(Kind.BAG,BagItem.getBagId(item)));
            else if(PillItem.isPillItem(item)) keys.add(new Key(Kind.PILL,PillItem.getPillId(item)));
        }
        return keys;
    }
    public void requirePlayer(Player player) { for(Key key:carried(player)) checkMutation(key); }
    public CompletableFuture<Void> preparePlayer(Player player) {
        main(); Set<Key> keys=carried(player); keys.add(new Key(Kind.TEMP,player.getUniqueId().toString()));
        players.computeIfAbsent(player.getUniqueId(),k->new HashSet<>()).addAll(keys);
        return CompletableFuture.allOf(keys.stream().map(this::prepare).toArray(CompletableFuture[]::new));
    }
    public CompletableFuture<Void> releasePlayer(Player player) {
        main(); captureViews(); Set<Key> keys=players.remove(player.getUniqueId());
        Set<Key> all=carried(player); if(keys!=null) all.addAll(keys); all.add(new Key(Kind.TEMP,player.getUniqueId().toString()));
        return CompletableFuture.allOf(all.stream().map(this::release).toArray(CompletableFuture[]::new));
    }
    private void recovery(Key key,Snapshot snapshot) {
        try {
            Path dir=plugin.getDataFolder().toPath().resolve("recovery"); Files.createDirectories(dir);
            Properties data=new Properties(); data.setProperty("kind",key.kind().name()); data.setProperty("id",key.id()); data.setProperty("size",String.valueOf(snapshot.size())); data.setProperty("operation",snapshot.operation());
            snapshot.pages().forEach((page,value)->data.setProperty("page."+page,value));
            try(var out=Files.newBufferedWriter(dir.resolve(key.kind()+"-"+key.id()+"-"+snapshot.operation()+".properties"),StandardCharsets.UTF_8)) { data.store(out,"Recovery only. Never replay automatically after a lease change."); }
        } catch(Exception e) { plugin.getLogger().severe("无法保存恢复快照: "+e.getMessage()); }
    }
    public void close() {
        main(); captureViews();
        List<CompletableFuture<Void>> writes=new ArrayList<>();
        for(Key key:new ArrayList<>(sessions.keySet())) writes.add(release(key));
        if(migration!=null) writes.add(migration.handle((result,error)->null));
        stopping=true;
        try { CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).get(30,TimeUnit.SECONDS); }
        catch(Exception e) { plugin.getLogger().severe("存储关闭未完全提交，请检查 recovery 目录: "+root(e).getMessage()); }
        heartbeats.shutdownNow(); io.shutdownNow(); if(repository!=null) repository.close();
    }
}
