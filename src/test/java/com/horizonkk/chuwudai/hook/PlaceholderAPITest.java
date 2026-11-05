package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.bag.BagManager;
import com.horizonkk.chuwudai.storage.MySqlRepository.Key;
import com.horizonkk.chuwudai.storage.MySqlRepository.Kind;
import com.horizonkk.chuwudai.storage.SharedStorage;
import com.horizonkk.chuwudai.storage.StorageAccessException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

/** Snapshot and actual background-thread PAPI checks; no live server or database required. */
public final class PlaceholderAPITest {
    private static int scenarios;

    private static final class Fixture {
        final Chuwudai plugin;
        final MockedStatic<BagItem> bags;
        final SharedStorage shared = mock(SharedStorage.class);
        final BagManager manager = mock(BagManager.class);
        final BagData data = mock(BagData.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final ItemStack bag = mock(ItemStack.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final BukkitTask task = mock(BukkitTask.class);
        final Key key = new Key(Kind.BAG, "12345678");
        final CompletableFuture<Void> pending = new CompletableFuture<>();
        final Map<String, Integer> counts = new HashMap<>(Map.of(
                "&f下品灵石", 11, "&a中品灵石", 22, "&e上品灵石", 33, "&c极品灵石", 44));
        final PlaceholderAPI expansion;
        Runnable refresh;

        Fixture(Chuwudai plugin, MockedStatic<Bukkit> bukkit, MockedStatic<BagItem> bags) {
            this.plugin = plugin;
            this.bags = bags;
            Thread mainThread = Thread.currentThread();
            bukkit.when(Bukkit::isPrimaryThread).thenAnswer(call -> Thread.currentThread() == mainThread);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            when(plugin.getSharedStorage()).thenReturn(shared);
            when(plugin.getBagManager()).thenReturn(manager);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getInventory()).thenAnswer(call -> {
                check(Thread.currentThread() == mainThread, "Inventory access must stay on the main thread");
                return inventory;
            });
            when(inventory.getItem(9)).thenReturn(bag);
            bags.when(() -> BagItem.getBagId(bag)).thenReturn(key.id());
            when(shared.prepare(key)).thenReturn(pending);
            when(manager.getBagData(key.id())).thenReturn(data);
            when(data.getStoneCounts()).thenReturn(counts);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(2L), eq(5L)))
                    .thenAnswer(call -> { refresh = call.getArgument(1); return task; });
            expansion = new PlaceholderAPI(plugin);
            expansion.start();
        }

        String value(String parameter) {
            return expansion.onPlaceholderRequest(player, parameter);
        }

        void background(Runnable action) {
            try {
                CompletableFuture.runAsync(action).get(10, TimeUnit.SECONDS);
            } catch (Exception error) {
                throw new AssertionError("Background PAPI query failed", error);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void scenario(String name, Consumer<Fixture> test) {
        Chuwudai plugin = mock(Chuwudai.class);
        when(plugin.getName()).thenReturn("Chuwudai");
        try (MockedStatic<Chuwudai> singleton = mockStatic(Chuwudai.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            singleton.when(Chuwudai::getInstance).thenReturn(plugin);
            try (MockedStatic<BagItem> bags = mockStatic(BagItem.class)) {
                Fixture fixture = new Fixture(plugin, bukkit, bags);
                try {
                    test.accept(fixture);
                } finally {
                    fixture.expansion.close();
                }
            }
        }
        scenarios++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        scenario("cold cache and unknown placeholders have safe fallbacks", f -> {
            f.background(() -> {
                check(f.value("is_bag").equals("false"), "Cold cache must have a boolean fallback");
                for (String name : List.of("xpls", "zpls", "spls", "jpls")) {
                    check(f.value(name).equals("0"), "Cold counts must remain numeric");
                }
                check(f.value("unknown") == null, "Unknown placeholder must remain unresolved");
                check(f.expansion.onPlaceholderRequest(null, "xpls").isEmpty(), "Null player must be supported");
            });
            verifyNoInteractions(f.shared, f.manager, f.inventory, f.data);
        });
        scenario("MySQL loading preserves bag detection and coalesces requests", f -> {
            f.expansion.onJoin(new PlayerJoinEvent(f.player, null));
            check(f.value("is_bag").equals("true"), "Bag detection must not wait for MySQL");
            check(f.value("xpls").equals("0"), "Loading counts must remain numeric");
            for (int i = 0; i < 100; i++) f.refresh.run();
            verify(f.shared, times(1)).prepare(f.key);
            verifyNoInteractions(f.manager);
            when(f.shared.isReady(f.key)).thenReturn(true);
            f.pending.complete(null);
            f.refresh.run();
            f.background(() -> {
                check(f.value("XPLS").equals("11"), "Loaded lower-grade count");
                check(f.value("zpls").equals("22"), "Loaded middle-grade count");
                check(f.value("spls").equals("33"), "Loaded higher-grade count");
                check(f.value("jpls").equals("44"), "Loaded top-grade count");
            });
        });
        scenario("repeated async HUD queries only read the published snapshot", f -> {
            when(f.shared.isReady(f.key)).thenReturn(true);
            f.refresh.run();
            clearInvocations(f.plugin, f.shared, f.manager, f.inventory, f.data, f.scheduler, f.player);
            f.background(() -> {
                for (int i = 0; i < 1000; i++) {
                    check(f.value("is_bag").equals("true"), "Async bag flag");
                    check(f.value("xpls").equals("11"), "Async stone count");
                }
            });
            verify(f.player, never()).getInventory();
            verifyNoInteractions(f.plugin, f.shared, f.manager, f.inventory, f.data, f.scheduler);
        });
        scenario("SQLite and YAML publish detached counts on each refresh", f -> {
            when(f.plugin.getSharedStorage()).thenReturn(null);
            f.refresh.run();
            f.counts.put("&f下品灵石", 99);
            f.background(() -> check(f.value("xpls").equals("11"), "Published values must not share a mutable map"));
            f.refresh.run();
            f.background(() -> check(f.value("xpls").equals("99"), "Next refresh must pick up changes"));
            verifyNoInteractions(f.shared);
        });
        scenario("changing or removing a bag discards previous counts", f -> {
            when(f.shared.isReady(f.key)).thenReturn(true);
            f.refresh.run();
            Key other = new Key(Kind.BAG, "87654321");
            ItemStack otherBag = mock(ItemStack.class);
            f.bags.when(() -> BagItem.getBagId(otherBag)).thenReturn(other.id());
            when(f.shared.prepare(other)).thenReturn(new CompletableFuture<>());
            when(f.inventory.getItem(9)).thenReturn(otherBag);
            f.refresh.run();
            check(f.value("is_bag").equals("true"), "An unloaded bag is still a bag");
            check(f.value("xpls").equals("0"), "A different bag must not reuse the old count");
            verify(f.shared).prepare(other);
            when(f.inventory.getItem(9)).thenReturn(null);
            f.refresh.run();
            check(f.value("is_bag").equals("false"), "Removing a bag must clear its flag");
            check(f.value("xpls").equals("0"), "Removing a bag must clear its counts");
        });
        scenario("failed loads are throttled and quit prevents late resurrection", f -> {
            f.pending.completeExceptionally(new IllegalStateException("offline"));
            f.refresh.run();
            for (int i = 0; i < 20; i++) f.refresh.run();
            verify(f.shared, times(1)).prepare(f.key);
            f.expansion.onQuit(new PlayerQuitEvent(f.player, null));
            f.background(() -> {
                check(f.value("is_bag").equals("false"), "Quit must discard the snapshot");
                check(f.value("xpls").equals("0"), "Quit must discard the counts");
            });
        });
        scenario("lease failure during refresh clears stale counts", f -> {
            when(f.shared.isReady(f.key)).thenReturn(true);
            f.refresh.run();
            when(f.manager.getBagData(f.key.id())).thenThrow(new StorageAccessException(
                    StorageAccessException.Reason.LEASE_LOST, "lease changed during refresh"));
            f.refresh.run();
            check(f.value("is_bag").equals("true"), "A storage failure must not change bag identity");
            check(f.value("xpls").equals("0"), "A storage failure must discard old counts");
        });
        scenario("background readers remain safe during main-thread refreshes", f -> {
            when(f.shared.isReady(f.key)).thenReturn(true);
            f.refresh.run();
            CompletableFuture<Void> readers = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 2000; i++) {
                    String value = f.value("xpls");
                    check(value.equals("11") || value.equals("12"), "Reader must see a complete published value");
                }
            });
            for (int i = 0; i < 200; i++) {
                f.counts.put("&f下品灵石", i % 2 == 0 ? 11 : 12);
                f.refresh.run();
            }
            readers.orTimeout(10, TimeUnit.SECONDS).join();
        });
        scenario("shutdown cancels refreshes and clears snapshots", f -> {
            when(f.shared.isReady(f.key)).thenReturn(true);
            f.refresh.run();
            f.expansion.close();
            verify(f.task).cancel();
            f.background(() -> check(f.value("is_bag").equals("false"), "Shutdown must clear cached players"));
        });
        System.out.println("Placeholder snapshot regression scenarios passed: " + scenarios);
    }
}
