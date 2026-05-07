package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class HealthHandler implements PillEffectHandler {

    @Override
    public void execute(Player player, int value) {
        ItemStack pillItem = getPlayerHeldPillItem(player);
        int durationSeconds = 0;

        if (pillItem != null && pillItem.hasItemMeta()) {
            NamespacedKey durationKey = new NamespacedKey("liandan", "dan_yao_health_duration");
            PersistentDataContainer container = pillItem.getItemMeta().getPersistentDataContainer();
            if (container.has(durationKey, PersistentDataType.INTEGER)) {
                durationSeconds = container.get(durationKey, PersistentDataType.INTEGER);
            }
        }

        if (durationSeconds <= 0) {
            double currentHealth = player.getHealth();
            double maxHealth = player.getMaxHealth();
            double newHealth = Math.min(currentHealth + value, maxHealth);
            player.setHealth(newHealth);
        } else {
            applyHealthOverTime(player, value, durationSeconds);
        }
    }

    private void applyHealthOverTime(Player player, int totalAmount, int durationSeconds) {
        int totalTicks = durationSeconds * 20;
        int times = totalTicks;
        double healthPerTick = (double) totalAmount / times;

        new BukkitRunnable() {
            int count = 0;
            double accumulatedHealth = 0.0;

            @Override
            public void run() {
                if (count >= times || !player.isOnline()) {
                    cancel();
                    return;
                }

                double currentHealth = player.getHealth();
                double maxHealth = player.getMaxHealth();

                accumulatedHealth += healthPerTick;

                if (accumulatedHealth >= 1.0) {
                    int restoreAmount = (int) accumulatedHealth;
                    accumulatedHealth -= restoreAmount;

                    double newHealth = Math.min(currentHealth + restoreAmount, maxHealth);
                    player.setHealth(newHealth);
                }

                count++;
            }
        }.runTaskTimer(Chuwudai.getInstance(), 0L, 1L);
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
                NamespacedKey key = new NamespacedKey("liandan", "dan_yao_health");
                if (container.has(key, PersistentDataType.INTEGER)) {
                    return invItem;
                }
            }
        }

        return null;
    }

    @Override
    public String getEffectName() {
        return "生命值";
    }
}
