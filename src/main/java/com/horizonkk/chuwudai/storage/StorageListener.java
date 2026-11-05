package com.horizonkk.chuwudai.storage;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.*;
import com.horizonkk.chuwudai.pill.*;
import com.horizonkk.chuwudai.Temporary.TempBoxInventory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.horizonkk.chuwudai.storage.MySqlRepository.*;

public final class StorageListener implements Listener {
    private final Chuwudai plugin;
    private final Map<Player, InventoryHolder> pendingCaptures = new LinkedHashMap<>();
    private boolean captureScheduled;
    public StorageListener(Chuwudai plugin) { this.plugin=plugin; }
    public static Key key(Object holder) {
        if(holder instanceof BagInventory b) return new Key(Kind.BAG,b.getBagData().getId());
        if(holder instanceof BagOptionsInventory b) return new Key(Kind.BAG,b.getBagData().getId());
        if(holder instanceof PillInventory p) return new Key(Kind.PILL,p.getPillData().getId());
        if(holder instanceof TempBoxInventory t) return new Key(Kind.TEMP,t.getOwnerUuid().toString());
        return null;
    }
    public static boolean isViewed(Key key) {
        if(key.kind()==Kind.BAG && Chuwudai.getInstance().getModBagBridge()!=null && Chuwudai.getInstance().getModBagBridge().isBagOpen(key.id())) return true;
        for(Player p:Bukkit.getOnlinePlayers()) if(key.equals(key(p.getOpenInventory().getTopInventory().getHolder()))) return true;
        return false;
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        if(plugin.getSharedStorage()!=null) Bukkit.getScheduler().runTaskLater(plugin,()->{if(e.getPlayer().isOnline()) plugin.getSharedStorage().preparePlayer(e.getPlayer()).exceptionally(error->null);},20);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e) {
        pendingCaptures.remove(e.getPlayer());
        if(plugin.getSharedStorage()!=null) plugin.getSharedStorage().releasePlayer(e.getPlayer()).exceptionally(error->{plugin.getLogger().warning("退出保存未完成: "+error.getMessage());return null;});
    }
    @EventHandler(priority=EventPriority.LOWEST) public void click(InventoryClickEvent e) {
        var shared=plugin.getSharedStorage(); if(shared==null) return;
        Key key=key(e.getView().getTopInventory().getHolder());
        if(key!=null && !shared.isReady(key)) { e.setCancelled(true); e.getWhoClicked().sendMessage("§c容器正在保存或暂不可用"); }
    }
    @EventHandler(priority=EventPriority.LOWEST) public void drag(InventoryDragEvent e) {
        var shared=plugin.getSharedStorage(); if(shared==null) return;
        Key key=key(e.getView().getTopInventory().getHolder()); if(key!=null && !shared.isReady(key)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void changed(InventoryClickEvent e) {
        if(!e.isCancelled()) captureLater(e.getView());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void changed(InventoryDragEvent e) {
        if(!e.isCancelled()) captureLater(e.getView());
    }
    private void captureLater(InventoryView view) {
        if(!(view.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = view.getTopInventory().getHolder();
        if(!(holder instanceof BagInventory || holder instanceof PillInventory || holder instanceof TempBoxInventory)) return;
        // Use the top inventory so shift-clicks from the player's inventory are included.
        if(plugin.getSharedStorage()==null && !(holder instanceof TempBoxInventory)) return;
        pendingCaptures.put(player, holder);
        if(captureScheduled) return;
        captureScheduled = true;
        Bukkit.getScheduler().runTask(plugin, this::capturePendingViews);
    }
    private void capturePendingViews() {
        Map<Player, InventoryHolder> captures = new LinkedHashMap<>(pendingCaptures);
        pendingCaptures.clear();
        captureScheduled = false;
        for(var entry : captures.entrySet()) {
            Player player = entry.getKey();
            // Close listeners save the old view; never recapture it after a switch or quit.
            if(!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder()!=entry.getValue()) continue;
            var shared = plugin.getSharedStorage();
            if(shared!=null) shared.captureView(player);
            else if(entry.getValue() instanceof TempBoxInventory temp) plugin.getTempBoxManager().capture(temp);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void close(InventoryCloseEvent e) {
        if(e.getPlayer() instanceof Player player) pendingCaptures.remove(player, e.getInventory().getHolder());
        var shared=plugin.getSharedStorage(); if(shared==null) return;
        Key key=key(e.getInventory().getHolder()); if(key==null) return;
        Bukkit.getScheduler().runTask(plugin,()->{ if(!isViewed(key)) shared.release(key).exceptionally(error->null); });
    }
}
