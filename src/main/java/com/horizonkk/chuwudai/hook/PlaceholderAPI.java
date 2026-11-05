package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.storage.MySqlRepository.Key;
import com.horizonkk.chuwudai.storage.MySqlRepository.Kind;
import com.horizonkk.chuwudai.storage.SharedStorage;
import com.horizonkk.chuwudai.storage.StorageAccessException;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlaceholderAPI extends PlaceholderExpansion implements Listener, AutoCloseable {

    private final Chuwudai plugin;
    // Publish all five values together; async readers never touch inventories or BagData.
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    // Only accessed by the main-thread refresh task and player events.
    private final Map<UUID, LoadAttempt> loads = new HashMap<>();
    private BukkitTask refreshTask;

    private record Snapshot(boolean bag, String xpls, String zpls, String spls, String jpls) {
        private static final Snapshot EMPTY = new Snapshot(false, "0", "0", "0", "0");
        private static final Snapshot LOADING = new Snapshot(true, "0", "0", "0", "0");

        private static Snapshot from(BagData data) {
            Map<String, Integer> counts = data.getStoneCounts();
            return new Snapshot(true,
                    String.valueOf(counts.getOrDefault("&f下品灵石", 0)),
                    String.valueOf(counts.getOrDefault("&a中品灵石", 0)),
                    String.valueOf(counts.getOrDefault("&e上品灵石", 0)),
                    String.valueOf(counts.getOrDefault("&c极品灵石", 0)));
        }
    }

    private record LoadAttempt(String bagId, CompletableFuture<Void> future, long retryAfter) {}

    public PlaceholderAPI(Chuwudai plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (refreshTask != null) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Let the startup data-load task run first, including when players are already online.
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        }, 2L, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        snapshots.remove(uuid);
        loads.remove(uuid);
    }

    private void refresh(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Placeholder snapshots require the main thread");
        UUID uuid = player.getUniqueId();
        String bagId = BagItem.getBagId(player.getInventory().getItem(9));
        if (bagId == null) {
            snapshots.put(uuid, Snapshot.EMPTY);
            loads.remove(uuid);
            return;
        }

        Snapshot snapshot = Snapshot.LOADING;
        SharedStorage shared = plugin.getSharedStorage();
        Key key = new Key(Kind.BAG, bagId);
        if (shared != null && !shared.isReady(key)) {
            requestLoad(uuid, bagId, shared, key);
        } else {
            try {
                BagData data = plugin.getBagManager().getBagData(bagId);
                if (data != null) snapshot = Snapshot.from(data);
                loads.remove(uuid);
            } catch (StorageAccessException error) {
                // A lease may fail between isReady() and get(); discard the old counts.
                if (shared != null) requestLoad(uuid, bagId, shared, key);
            }
        }
        snapshots.put(uuid, snapshot);
    }

    private void requestLoad(UUID uuid, String bagId, SharedStorage shared, Key key) {
        LoadAttempt previous = loads.get(uuid);
        long now = System.nanoTime();
        if (previous != null && previous.bagId().equals(bagId)
                && (!previous.future().isDone() || now - previous.retryAfter() < 0)) return;
        loads.put(uuid, new LoadAttempt(bagId, shared.prepare(key), now + TimeUnit.SECONDS.toNanos(1)));
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        HandlerList.unregisterAll(this);
        snapshots.clear();
        loads.clear();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "chuwudai";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HorizonKK";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        Snapshot snapshot = snapshots.getOrDefault(player.getUniqueId(), Snapshot.EMPTY);
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "xpls" -> snapshot.xpls();
            case "zpls" -> snapshot.zpls();
            case "spls" -> snapshot.spls();
            case "jpls" -> snapshot.jpls();
            case "is_bag" -> String.valueOf(snapshot.bag());
            default -> null;
        };
    }

}
