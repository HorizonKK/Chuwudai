package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.hook.ZhenYuanHook;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class ZhenYuanHandler implements PillEffectHandler {

    @Override
    public void execute(Player player, int value) {
        if (!ZhenYuanHook.isHooked()) {
            player.giveExp(value);
            return;
        }

        ItemStack pillItem = getPlayerHeldPillItem(player);
        int durationSeconds = 0;

        if (pillItem != null && pillItem.hasItemMeta()) {
            NamespacedKey durationKey = new NamespacedKey("liandan", "dan_yao_zhenyuan_duration");
            PersistentDataContainer container = pillItem.getItemMeta().getPersistentDataContainer();
            if (container.has(durationKey, PersistentDataType.INTEGER)) {
                durationSeconds = container.get(durationKey, PersistentDataType.INTEGER);
            }
        }

        if (durationSeconds <= 0) {
            ZhenYuanHook.addPlayerZhenYuan(player, value);
        } else {
            applyZhenYuanOverTime(player, value, durationSeconds);
        }
    }

    private void applyZhenYuanOverTime(Player player, int totalAmount, int durationSeconds) {
        int times = 10;
        int totalTicks = durationSeconds * 20;
        int ticksPerRestore = totalTicks / times;
        int amountPerTick = totalAmount / times;
        int remainder = totalAmount % times;

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= times || !player.isOnline()) {
                    cancel();
                    return;
                }

                int restoreAmount = amountPerTick;
                if (count == times - 1) {
                    restoreAmount += remainder;
                }

                ZhenYuanHook.addPlayerZhenYuan(player, restoreAmount);

                count++;
            }
        }.runTaskTimer(Chuwudai.getInstance(), 0L, ticksPerRestore);
    }

    private ItemStack getPlayerHeldPillItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && item.hasItemMeta()) {
            return item;
        }

        item = player.getInventory().getItemInOffHand();
        if (item != null && item.hasItemMeta()) {
            return item;
        }

        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem != null && invItem.hasItemMeta()) {
                PersistentDataContainer container = invItem.getItemMeta().getPersistentDataContainer();
                NamespacedKey key = new NamespacedKey("liandan", "dan_yao_zhenyuan");
                if (container.has(key, PersistentDataType.INTEGER)) {
                    return invItem;
                }
            }
        }

        return null;
    }

    @Override
    public String getEffectName() {
        return "真元";
    }
}
