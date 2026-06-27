package com.horizonkk.chuwudai.listener;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagAutoPickupService;
import com.horizonkk.chuwudai.bag.BagInventory;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StonePickupListener implements Listener {

    private final ConcurrentMap<UUID, Long> lastStorageNoticeAt = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStonePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (isPlayerInBagInventory(player)) {
            return;
        }

        ItemStack pickedItem = event.getItem().getItemStack();
        BagAutoPickupService.StorageResult result = BagAutoPickupService.tryStoreItem(player, pickedItem);
        if (!result.hasStoredItems()) {
            return;
        }

        ItemStack remainingItem = result.getRemainingItem();
        if (remainingItem != null) {
            event.getItem().getItemStack().setAmount(remainingItem.getAmount());
        } else {
            event.setCancelled(true);
            event.getItem().remove();
        }

        if (result.shouldNotify()) {
            sendThrottledStorageNotice(player, result.getStoredAmount());
        }
    }

    private boolean isPlayerInBagInventory(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof BagInventory;
    }

    private void sendThrottledStorageNotice(Player player, int storedAmount) {
        long cooldownMillis = Chuwudai.getInstance().getConfig().getLong("auto-pickup.message-cooldown-ms", 3000L);
        if (cooldownMillis < 0) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastNotice = lastStorageNoticeAt.get(playerId);
        if (lastNotice != null && now - lastNotice < cooldownMillis) {
            return;
        }

        lastStorageNoticeAt.put(playerId, now);
        String prefix = Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f");
        String message = Chuwudai.getInstance().getConfig().getString(
                "messages.item-pickup",
                "&a储物袋已自动吸收 %amount% 个物品。");
        player.sendMessage(ColorUtils.translateColorCodes(prefix + message
                .replace("%amount%", String.valueOf(storedAmount))));
    }

}
