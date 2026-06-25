package com.horizonkk.chuwudai.listener;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagAutoPickupSettings;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagInventory;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StonePickupListener implements Listener {

    private static final String[] ORE_TIER_SUFFIXES = {
            "[一阶]", "[二阶]", "[三阶]", "[四阶]", "[五阶]", "[六阶]"
    };

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

        if (!Chuwudai.getInstance().getConfig().getBoolean("auto-pickup.enabled", true)) {
            return;
        }

        ItemStack pickedItem = event.getItem().getItemStack();
        AutoPickupMatch match = getAutoPickupMatch(player, pickedItem);
        if (!match.shouldAbsorb()) {
            return;
        }

        boolean requireSlotZero = Chuwudai.getInstance().getConfig().getBoolean("auto-pickup.require-slot-zero", true);
        ItemStack bagItem = requireSlotZero ? player.getInventory().getItem(9) : findBagInInventory(player);
        if (bagItem == null || !BagItem.isBagItem(bagItem)) {
            return;
        }

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = Chuwudai.getInstance().getBagManager().getBagData(bagId);
        if (bagData == null) {
            return;
        }

        int originalAmount = pickedItem.getAmount();
        int remaining = tryStoreItemInBag(pickedItem, bagData);
        if (remaining >= originalAmount) {
            return;
        }

        int storedAmount = originalAmount - remaining;
        if (remaining > 0) {
            event.getItem().getItemStack().setAmount(remaining);
        } else {
            event.setCancelled(true);
            event.getItem().remove();
        }

        ItemStack currentBagItem = requireSlotZero ? player.getInventory().getItem(9) : findBagInInventory(player);
        if (currentBagItem != null) {
            BagItem.updateBagLore(currentBagItem, bagData.getStoneCounts(), bagData);
        }

        Chuwudai.getInstance().getBagManager().queueBagSave(bagId);

        if (match.shouldNotify()) {
            sendThrottledStorageNotice(player, storedAmount);
        }
    }

    private AutoPickupMatch getAutoPickupMatch(Player player, ItemStack item) {
        BagAutoPickupSettings.Settings settings = BagAutoPickupSettings.get(player.getUniqueId());

        boolean spiritStone = isSpiritStone(item);
        boolean matchesEnabledOption =
                (settings.isSpiritStones() && spiritStone)
                        || (settings.isOreItems() && isTieredOreItem(item))
                        || (settings.isVanillaItems() && isPlainVanillaItem(item));

        return new AutoPickupMatch(matchesEnabledOption, !spiritStone);
    }

    private boolean isSpiritStone(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = item.getItemMeta().getDisplayName();
        ConfigurationSection stones = Chuwudai.getInstance().getConfig().getConfigurationSection("spirit-stones");
        if (stones == null) {
            return false;
        }

        for (String stoneName : stones.getKeys(false)) {
            String coloredStoneName = ColorUtils.translateColorCodes(stoneName);
            if (displayName.equals(coloredStoneName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTieredOreItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = ColorUtils.removeColorCodes(item.getItemMeta().getDisplayName()).trim();
        for (String suffix : ORE_TIER_SUFFIXES) {
            if (displayName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlainVanillaItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && !item.hasItemMeta();
    }

    private ItemStack findBagInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && BagItem.isBagItem(item)) {
                return item;
            }
        }
        return null;
    }

    private int tryStoreItemInBag(ItemStack item, BagData bagData) {
        int amount = item.getAmount();
        int maxStackSize = item.getMaxStackSize();

        for (int i = 0; i < bagData.getSize(); i++) {
            ItemStack existing = bagData.getItem(i);
            if (existing != null && existing.isSimilar(item)) {
                int availableSpace = Math.min(maxStackSize, existing.getMaxStackSize()) - existing.getAmount();
                if (availableSpace <= 0) {
                    continue;
                }

                int toAdd = Math.min(availableSpace, amount);
                existing.setAmount(existing.getAmount() + toAdd);
                amount -= toAdd;
                bagData.setItem(i, existing);

                if (amount <= 0) {
                    return 0;
                }
            }
        }

        for (int i = 0; i < bagData.getSize(); i++) {
            if (bagData.getItem(i) == null) {
                ItemStack newStack = item.clone();
                int toAdd = Math.min(maxStackSize, amount);
                newStack.setAmount(toAdd);
                bagData.setItem(i, newStack);
                amount -= toAdd;

                if (amount <= 0) {
                    return 0;
                }
            }
        }

        return amount;
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

    private static final class AutoPickupMatch {
        private final boolean absorb;
        private final boolean notify;

        private AutoPickupMatch(boolean absorb, boolean notify) {
            this.absorb = absorb;
            this.notify = notify;
        }

        private boolean shouldAbsorb() {
            return absorb;
        }

        private boolean shouldNotify() {
            return absorb && notify;
        }
    }
}
