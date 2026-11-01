package com.horizonkk.chuwudai.Temporary;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.storage.*;
import com.horizonkk.chuwudai.utlis.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static com.horizonkk.chuwudai.storage.MySqlRepository.*;

public class TempBoxManager {
    private final Chuwudai plugin=Chuwudai.getInstance();
    private final Map<UUID,Map<Integer,ItemStack>> boxes=new HashMap<>();
    private final Map<UUID,TempBoxInventory> open=new HashMap<>();
    private final Map<UUID,Throwable> failures=new ConcurrentHashMap<>();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Chuwudai-tempboxes");t.setDaemon(true);return t;});
    private final boolean sqlite;
    private final Path folder;
    public TempBoxManager() {
        sqlite="sqlite".equalsIgnoreCase(plugin.getConfig().getString("storage.type","sqlite"));
        folder=plugin.getDataFolder().toPath().resolve("tempboxes");
        if(plugin.getSharedStorage()!=null) return;
        try {
            if(sqlite) try(Connection c=DatabaseManager.getConnection(); Statement s=c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS temp_boxes (id TEXT PRIMARY KEY, data TEXT NOT NULL)");
                try(ResultSet r=s.executeQuery("SELECT id,data FROM temp_boxes")) { while(r.next()) boxes.put(UUID.fromString(r.getString(1)),EfficientItemSerializationUtil.deserializeItems(r.getString(2))); }
            } else {
                Files.createDirectories(folder);
                try(var files=Files.list(folder)) { for(Path p:files.filter(p->p.toString().endsWith(".yml")).toList()) boxes.put(UUID.fromString(p.getFileName().toString().replace(".yml","")),EfficientItemSerializationUtil.deserializeItems(Base64.getEncoder().encodeToString(Files.readAllBytes(p)))); }
            }
        } catch(Exception e) { throw new StorageAccessException(StorageAccessException.Reason.CORRUPT,"暂存箱加载失败",e); }
    }
    private Key key(UUID uuid) { return new Key(Kind.TEMP,uuid.toString()); }
    @SuppressWarnings("unchecked")
    public Map<Integer,ItemStack> getPlayerItems(UUID uuid) {
        Map<Integer,ItemStack> source=plugin.getSharedStorage()==null ? boxes.computeIfAbsent(uuid,k->new HashMap<>()) : (Map<Integer,ItemStack>)plugin.getSharedStorage().get(key(uuid));
        return copy(source);
    }
    private Map<Integer,ItemStack> copy(Map<Integer,ItemStack> source) { Map<Integer,ItemStack> copy=new HashMap<>(); source.forEach((slot,item)->{if(item!=null) copy.put(slot,item.clone());}); return copy; }
    public void openTempBox(Player player) {
        UUID uuid=player.getUniqueId(); var shared=plugin.getSharedStorage();
        if(shared!=null && !shared.isReady(key(uuid))) {
            shared.prepare(key(uuid)).whenComplete((v,e)->shared.onMain(()->{ if(player.isOnline()) { if(e==null) openTempBox(player); else player.sendMessage("§c暂存箱暂不可用："+e.getMessage()); } return null; })); return;
        }
        if(open.containsKey(uuid)) { player.sendMessage("§c暂存箱正在使用中"); return; }
        TempBoxInventory inventory=new TempBoxInventory(uuid,getPlayerItems(uuid)); open.put(uuid,inventory); inventory.open(player);
    }
    public boolean addItem(UUID uuid,ItemStack item) {
        if(item==null || item.getType().isAir()) return false;
        Map<Integer,ItemStack> items=live(uuid);
        for(int slot=0;slot<54;slot++) if(!items.containsKey(slot)) return addItemToSlot(uuid,slot,item);
        return false;
    }
    private Map<Integer,ItemStack> live(UUID uuid) { getPlayerItems(uuid); return open.containsKey(uuid) ? open.get(uuid).extractItems() : getPlayerItems(uuid); }
    public boolean addItemToSlot(UUID uuid,int slot,ItemStack item) {
        if(slot<0 || slot>=54 || item==null || item.getType().isAir()) return false;
        Map<Integer,ItemStack> items=live(uuid); items.put(slot,item.clone()); replace(uuid,items);
        if(open.containsKey(uuid)) open.get(uuid).getInventory().setItem(slot,item.clone());
        return true;
    }
    public void capture(TempBoxInventory inventory) { replace(inventory.getOwnerUuid(),inventory.extractItems()); }
    @SuppressWarnings("unchecked")
    private void replace(UUID uuid,Map<Integer,ItemStack> items) {
        var shared=plugin.getSharedStorage();
        if(shared!=null) {
            Map<Integer,ItemStack> current=(Map<Integer,ItemStack>)shared.get(key(uuid));
            if(current.equals(items)) return;
            shared.touch(key(uuid)); current.clear(); current.putAll(copy(items));
        } else {
            if(items.equals(boxes.get(uuid))) return;
            boxes.put(uuid,copy(items)); persist(uuid,items);
        }
    }
    private void persist(UUID uuid,Map<Integer,ItemStack> items) {
        String data=EfficientItemSerializationUtil.serializeItems(items);
        writer.execute(()->{
            try {
                if(sqlite) try(Connection c=DatabaseManager.getConnection();PreparedStatement p=c.prepareStatement("INSERT OR REPLACE INTO temp_boxes(id,data) VALUES (?,?)")) { p.setString(1,uuid.toString());p.setString(2,data);p.executeUpdate(); }
                else {
                    Path temp=Files.createTempFile(folder,uuid.toString(),".tmp"); Files.write(temp,Base64.getDecoder().decode(data));
                    try { Files.move(temp,folder.resolve(uuid+".yml"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
                    catch(AtomicMoveNotSupportedException e) { Files.move(temp,folder.resolve(uuid+".yml"),StandardCopyOption.REPLACE_EXISTING); }
                }
                failures.remove(uuid);
            } catch(Exception e) { failures.put(uuid,e); plugin.getLogger().severe("暂存箱保存失败 "+uuid+": "+e.getMessage()); }
        });
    }
    public void saveItems(UUID uuid,Map<Integer,ItemStack> items) {
        open.remove(uuid);
        if(plugin.getSharedStorage()!=null && !plugin.getSharedStorage().isReady(key(uuid))) return;
        replace(uuid,items);
        if(plugin.getSharedStorage()!=null) plugin.getSharedStorage().flush(key(uuid));
    }
    public void clearBox(UUID uuid) { replace(uuid,Map.of()); if(open.containsKey(uuid)) open.get(uuid).getInventory().clear(); }
    public int getUsedSlots(UUID uuid) { return live(uuid).size(); }
    public int getBoxSize() { return 54; }
    public CompletableFuture<Void> flushAsync() { return CompletableFuture.runAsync(()->{ if(!failures.isEmpty()) throw new StorageAccessException(StorageAccessException.Reason.UNAVAILABLE,"暂存箱保存失败",failures.values().iterator().next()); },writer); }
    public void close() {
        for(TempBoxInventory view:new ArrayList<>(open.values())) if(plugin.getSharedStorage()==null || plugin.getSharedStorage().isReady(key(view.getOwnerUuid()))) capture(view);
        writer.shutdown(); try { if(!writer.awaitTermination(30,TimeUnit.SECONDS)) plugin.getLogger().severe("暂存箱保存等待超时"); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
