package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class BagAutoPickupService {

    private static final String[] ORE_TIER_SUFFIXES = {
            "[一阶]", "[二阶]", "[三阶]", "[四阶]", "[五阶]", "[六阶]"
    };

    private BagAutoPickupService() {
    }

    public static StorageResult tryStoreItem(Player player, ItemStack item) {
        BatchStorageResult result = tryStoreItems(player, Collections.singletonList(item));
        ItemStack remaining = result.getRemainingItems().isEmpty() ? null : result.getRemainingItems().get(0);
        return new StorageResult(remaining, result.getStoredAmount(), result.shouldNotify());
    }

    public static BatchStorageResult tryStoreItems(Player player, Collection<ItemStack> items) {
        List<ItemStack> remainingItems = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return new BatchStorageResult(remainingItems, 0, false);
        }

        if (player == null || !Chuwudai.getInstance().getConfig().getBoolean("auto-pickup.enabled", true)) {
            addClones(remainingItems, items);
            return new BatchStorageResult(remainingItems, 0, false);
        }

        ItemStack bagItem = findAutoPickupBag(player);
        if (bagItem == null || !BagItem.isBagItem(bagItem)) {
            addClones(remainingItems, items);
            return new BatchStorageResult(remainingItems, 0, false);
        }

        String bagId = BagItem.getBagId(bagItem);
        BagData bagData = Chuwudai.getInstance().getBagManager().getBagData(bagId);
        if (bagData == null) {
            addClones(remainingItems, items);
            return new BatchStorageResult(remainingItems, 0, false);
        }

        int storedAmount = 0;
        boolean notify = false;

        for (ItemStack item : compactSimilarStacks(items)) {
            if (isEmpty(item)) {
                continue;
            }

            AutoPickupMatch match = getAutoPickupMatch(player, item);
            if (!match.shouldAbsorb()) {
                remainingItems.add(item.clone());
                continue;
            }

            int originalAmount = item.getAmount();
            int remaining = tryStoreItemInBag(item, bagData);
            if (remaining > 0) {
                ItemStack remainingItem = item.clone();
                remainingItem.setAmount(remaining);
                remainingItems.add(remainingItem);
            }

            if (remaining < originalAmount) {
                storedAmount += originalAmount - remaining;
                notify = notify || match.shouldNotify();
            }
        }

        if (storedAmount > 0) {
            BagItem.updateBagLore(bagItem, bagData.getStoneCounts(), bagData);
            Chuwudai.getInstance().getBagManager().queueBagSave(bagId);
        }

        return new BatchStorageResult(remainingItems, storedAmount, notify);
    }

    private static AutoPickupMatch getAutoPickupMatch(Player player, ItemStack item) {
        BagAutoPickupSettings.Settings settings = BagAutoPickupSettings.get(player.getUniqueId());

        boolean spiritStone = isSpiritStone(item);
        boolean matchesEnabledOption =
                (settings.isSpiritStones() && spiritStone)
                        || (settings.isOreItems() && isTieredOreItem(item))
                        || (settings.isVanillaItems() && isPlainVanillaItem(item));

        return new AutoPickupMatch(matchesEnabledOption, !spiritStone);
    }

    private static boolean isSpiritStone(ItemStack item) {
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

    private static boolean isTieredOreItem(ItemStack item) {
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

    private static boolean isPlainVanillaItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && !item.hasItemMeta();
    }

    private static ItemStack findAutoPickupBag(Player player) {
        boolean requireSlotZero = Chuwudai.getInstance().getConfig().getBoolean("auto-pickup.require-slot-zero", true);
        return requireSlotZero ? player.getInventory().getItem(9) : findBagInInventory(player);
    }

    private static ItemStack findBagInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && BagItem.isBagItem(item)) {
                return item;
            }
        }
        return null;
    }

    private static int tryStoreItemInBag(ItemStack item, BagData bagData) {
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

    private static void addClones(List<ItemStack> target, Collection<ItemStack> items) {
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                target.add(item.clone());
            }
        }
    }

    private static List<ItemStack> compactSimilarStacks(Collection<ItemStack> items) {
        List<ItemStack> compacted = new ArrayList<>();
        for (ItemStack item : items) {
            if (isEmpty(item)) {
                continue;
            }

            int amount = item.getAmount();
            int maxStackSize = Math.max(1, item.getMaxStackSize());
            for (ItemStack existing : compacted) {
                if (amount <= 0) {
                    break;
                }
                if (!existing.isSimilar(item) || existing.getAmount() >= maxStackSize) {
                    continue;
                }

                int toAdd = Math.min(maxStackSize - existing.getAmount(), amount);
                existing.setAmount(existing.getAmount() + toAdd);
                amount -= toAdd;
            }

            while (amount > 0) {
                int toAdd = Math.min(maxStackSize, amount);
                ItemStack copy = item.clone();
                copy.setAmount(toAdd);
                compacted.add(copy);
                amount -= toAdd;
            }
        }
        return compacted;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    public static final class StorageResult {
        private final ItemStack remainingItem;
        private final int storedAmount;
        private final boolean notify;

        private StorageResult(ItemStack remainingItem, int storedAmount, boolean notify) {
            this.remainingItem = remainingItem == null ? null : remainingItem.clone();
            this.storedAmount = storedAmount;
            this.notify = notify;
        }

        public ItemStack getRemainingItem() {
            return remainingItem == null ? null : remainingItem.clone();
        }

        public int getStoredAmount() {
            return storedAmount;
        }

        public boolean hasStoredItems() {
            return storedAmount > 0;
        }

        public boolean shouldNotify() {
            return notify && hasStoredItems();
        }
    }

    public static final class BatchStorageResult {
        private final List<ItemStack> remainingItems;
        private final int storedAmount;
        private final boolean notify;

        private BatchStorageResult(List<ItemStack> remainingItems, int storedAmount, boolean notify) {
            this.remainingItems = new ArrayList<>();
            for (ItemStack item : remainingItems) {
                if (!isEmpty(item)) {
                    this.remainingItems.add(item.clone());
                }
            }
            this.storedAmount = storedAmount;
            this.notify = notify;
        }

        public List<ItemStack> getRemainingItems() {
            List<ItemStack> copy = new ArrayList<>();
            for (ItemStack item : remainingItems) {
                copy.add(item.clone());
            }
            return copy;
        }

        public int getStoredAmount() {
            return storedAmount;
        }

        public boolean hasStoredItems() {
            return storedAmount > 0;
        }

        public boolean shouldNotify() {
            return notify && hasStoredItems();
        }
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
