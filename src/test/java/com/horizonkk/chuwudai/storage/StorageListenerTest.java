package com.horizonkk.chuwudai.storage;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.Temporary.TempBoxInventory;
import com.horizonkk.chuwudai.Temporary.TempBoxManager;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagInventory;
import com.horizonkk.chuwudai.bag.BagOptionsInventory;
import com.horizonkk.chuwudai.pill.PillInventory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

/** Event/scheduler regression tests; no live server or database required. */
public final class StorageListenerTest {
    private static int scenarios;

    private record View(Player player, InventoryView inventoryView, InventoryHolder holder) {}

    private static final class Fixture {
        final Chuwudai plugin = mock(Chuwudai.class);
        final SharedStorage shared = mock(SharedStorage.class);
        final TempBoxManager tempBoxes = mock(TempBoxManager.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final List<Runnable> tasks = new ArrayList<>();
        final StorageListener listener = new StorageListener(plugin);

        Fixture(MockedStatic<Bukkit> bukkit) {
            when(plugin.getSharedStorage()).thenReturn(shared);
            when(plugin.getTempBoxManager()).thenReturn(tempBoxes);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                tasks.add(call.getArgument(1));
                return mock(BukkitTask.class);
            });
        }

        View view(InventoryHolder holder) {
            Player player = mock(Player.class);
            InventoryView view = mock(InventoryView.class);
            Inventory top = mock(Inventory.class);
            when(top.getHolder()).thenReturn(holder);
            when(top.getSize()).thenReturn(54);
            when(view.getTopInventory()).thenReturn(top);
            when(view.getPlayer()).thenReturn(player);
            when(player.getOpenInventory()).thenReturn(view);
            when(player.isOnline()).thenReturn(true);
            return new View(player, view, holder);
        }

        InventoryClickEvent click(View view) {
            return new InventoryClickEvent(view.inventoryView(), InventoryType.SlotType.CONTAINER,
                    0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        }

        InventoryDragEvent drag(View view) {
            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getView()).thenReturn(view.inventoryView());
            return event;
        }

        void tick() {
            List<Runnable> due = new ArrayList<>(tasks);
            tasks.clear();
            due.forEach(Runnable::run);
        }
    }

    private static void check(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }

    private static void scenario(String name, Consumer<Fixture> test) {
        try(MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            test.accept(new Fixture(bukkit));
            bukkit.verify(Bukkit::getOnlinePlayers, never());
        }
        scenarios++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        scenario("ordinary inventories and options menus schedule no captures", f -> {
            for(InventoryHolder holder : new InventoryHolder[]{null, mock(InventoryHolder.class), mock(BagOptionsInventory.class)}) {
                View view = f.view(holder);
                for(int i=0; i<100; i++) {
                    f.listener.changed(f.click(view));
                    f.listener.changed(f.drag(view));
                }
            }
            check(f.tasks.isEmpty(), "Unrelated events must not schedule work");
            verifyNoInteractions(f.shared, f.tempBoxes);
        });
        scenario("cancelled clicks and drags are ignored", f -> {
            View view = f.view(mock(BagInventory.class));
            InventoryClickEvent click = f.click(view);
            click.setCancelled(true);
            InventoryDragEvent drag = f.drag(view);
            when(drag.isCancelled()).thenReturn(true);
            f.listener.changed(click);
            f.listener.changed(drag);
            check(f.tasks.isEmpty(), "Cancelled events must not schedule work");
        });
        scenario("200 events become one deferred capture; a later tick captures again", f -> {
            View view = f.view(mock(BagInventory.class));
            for(int i=0; i<100; i++) {
                f.listener.changed(f.click(view));
                f.listener.changed(f.drag(view));
            }
            check(f.tasks.size()==1, "Events must share one scheduled task");
            verify(f.shared, never()).captureView(any());
            f.tick();
            verify(f.shared).captureView(view.player());
            f.listener.changed(f.click(view));
            check(f.tasks.size()==1, "A later tick must be able to schedule another capture");
            f.tick();
            verify(f.shared, times(2)).captureView(view.player());
        });
        scenario("multiple active players share a task and leave other players untouched", f -> {
            View bag = f.view(mock(BagInventory.class));
            View pill = f.view(mock(PillInventory.class));
            View temp = f.view(mock(TempBoxInventory.class));
            View other = f.view(mock(BagInventory.class));
            f.listener.changed(f.click(bag));
            f.listener.changed(f.drag(pill));
            f.listener.changed(f.click(temp));
            check(f.tasks.size()==1, "Players must share the next-tick batch");
            f.tick();
            verify(f.shared).captureView(bag.player());
            verify(f.shared).captureView(pill.player());
            verify(f.shared).captureView(temp.player());
            verify(f.shared, never()).captureView(other.player());
            verify(f.shared, never()).captureViews();
            verifyNoMoreInteractions(f.shared);
        });
        scenario("shift-click from the bottom inventory captures the open container", f -> {
            View view = f.view(mock(BagInventory.class));
            InventoryClickEvent event = new InventoryClickEvent(view.inventoryView(), InventoryType.SlotType.CONTAINER,
                    63, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
            f.listener.changed(event);
            f.tick();
            verify(f.shared).captureView(view.player());
        });
        scenario("closed, switched and offline views are not captured", f -> {
            View closed = f.view(mock(BagInventory.class));
            View switched = f.view(mock(PillInventory.class));
            View offline = f.view(mock(TempBoxInventory.class));
            for(View view : List.of(closed, switched, offline)) f.listener.changed(f.click(view));
            InventoryView ordinary = f.view(null).inventoryView();
            InventoryView replacement = f.view(mock(BagInventory.class)).inventoryView();
            when(closed.player().getOpenInventory()).thenReturn(ordinary);
            when(switched.player().getOpenInventory()).thenReturn(replacement);
            when(offline.player().isOnline()).thenReturn(false);
            f.tick();
            verifyNoInteractions(f.shared, f.tempBoxes);
        });
        scenario("after switching, a new event captures only the current view", f -> {
            View oldView = f.view(mock(BagInventory.class));
            View newView = f.view(mock(PillInventory.class));
            f.listener.changed(f.click(oldView));
            when(newView.inventoryView().getPlayer()).thenReturn(oldView.player());
            when(oldView.player().getOpenInventory()).thenReturn(newView.inventoryView());
            f.listener.changed(f.drag(newView));
            check(f.tasks.size()==1, "Switching must not add a duplicate batch");
            f.tick();
            verify(f.shared).captureView(oldView.player());
            verifyNoMoreInteractions(f.shared);
        });
        scenario("local storage captures only the affected temporary box", f -> {
            when(f.plugin.getSharedStorage()).thenReturn(null);
            View temp = f.view(mock(TempBoxInventory.class));
            View other = f.view(mock(TempBoxInventory.class));
            f.listener.changed(f.click(f.view(mock(BagInventory.class))));
            f.listener.changed(f.click(f.view(mock(PillInventory.class))));
            check(f.tasks.isEmpty(), "Local bag/pill listeners already handle their own saves");
            f.listener.changed(f.click(temp));
            f.listener.changed(f.drag(temp));
            f.tick();
            verify(f.tempBoxes).capture((TempBoxInventory)temp.holder());
            verify(f.tempBoxes, never()).capture((TempBoxInventory)other.holder());
            verifyNoMoreInteractions(f.tempBoxes);
        });
        scenario("close and quit remove pending captures", f -> {
            View bag = f.view(mock(BagInventory.class));
            f.listener.changed(f.click(bag));
            when(f.shared.releasePlayer(bag.player())).thenReturn(CompletableFuture.completedFuture(null));
            f.listener.quit(new PlayerQuitEvent(bag.player(), "quit"));
            f.tick();
            verify(f.shared, never()).captureView(any());
            when(f.plugin.getSharedStorage()).thenReturn(null);
            View temp = f.view(mock(TempBoxInventory.class));
            f.listener.changed(f.click(temp));
            f.listener.close(new InventoryCloseEvent(temp.inventoryView()));
            f.tick();
            verifyNoInteractions(f.tempBoxes);
        });
        scenario("single-view capture retains readiness and storage-failure handling", f -> {
            SharedStorage storage = mock(SharedStorage.class);
            View view = f.view(mock(BagInventory.class));
            BagInventory bag = (BagInventory)view.holder();
            when(bag.getBagData()).thenReturn(new BagData("12345678", 27));
            doCallRealMethod().when(storage).captureView(view.player());
            var key = new MySqlRepository.Key(MySqlRepository.Kind.BAG, "12345678");
            storage.captureView(view.player());
            verify(bag, never()).saveItems();
            when(storage.isReady(key)).thenReturn(true);
            storage.captureView(view.player());
            verify(bag).saveItems();
            doThrow(new StorageAccessException(StorageAccessException.Reason.LEASE_LOST, "test")).when(bag).saveItems();
            storage.captureView(view.player());
            verify(view.player()).closeInventory();
        });
        System.out.println("Storage listener regression scenarios passed: " + scenarios);
    }
}
