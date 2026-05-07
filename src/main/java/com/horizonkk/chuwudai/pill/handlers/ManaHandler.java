package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.hook.ManaHook;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class ManaHandler implements PillEffectHandler {

    @Override
    public void execute(Player player, int value) {
        if (!ManaHook.isHooked()) {
            player.giveExp(value);
            return;
        }

        ItemStack pillItem = getPlayerHeldPillItem(player);
        int durationSeconds = 0;

        if (pillItem != null && pillItem.hasItemMeta()) {
            NamespacedKey durationKey = new NamespacedKey("liandan", "dan_yao_mana_duration");
            PersistentDataContainer container = pillItem.getItemMeta().getPersistentDataContainer();
            if (container.has(durationKey, PersistentDataType.INTEGER)) {
                durationSeconds = container.get(durationKey, PersistentDataType.INTEGER);
            }
        }

        if (durationSeconds <= 0) {
            ManaHook.addPlayerMana(player, value);
        } else {
            applyManaOverTime(player, value, durationSeconds);
        }
    }

    private void applyManaOverTime(Player player, int totalAmount, int durationSeconds) {
        int totalTicks = durationSeconds * 20;
        double manaPerTick = (double) totalAmount / totalTicks;

        new BukkitRunnable() {
            int count = 0;
            double accumulatedMana = 0.0;

            @Override
            public void run() {
                if (count >= totalTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                double currentMana = ManaHook.getPlayerMana(player);
                double maxMana = ManaHook.getPlayerMaxMana(player);

                accumulatedMana += manaPerTick;

                if (accumulatedMana >= 1.0) {
                    int restoreAmount = (int) accumulatedMana;
                    accumulatedMana -= restoreAmount;

                    double newMana = Math.min(currentMana + restoreAmount, maxMana);
                    ManaHook.setPlayerMana(player, newMana);
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
                NamespacedKey key = new NamespacedKey("liandan", "dan_yao_mana");
                if (container.has(key, PersistentDataType.INTEGER)) {
                    return invItem;
                }
            }
        }

        return null;
    }

    @Override
    public String getEffectName() {
        return "法力值";
    }
}
