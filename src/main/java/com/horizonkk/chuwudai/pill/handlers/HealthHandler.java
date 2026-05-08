package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class HealthHandler implements PillEffectHandler {

    @Override
    public void execute(Player player, int value) {
        double currentHealth = player.getHealth();
        double maxHealth = player.getMaxHealth();
        player.setHealth(Math.min(currentHealth + value, maxHealth));
    }

    @Override
    public void execute(Player player, int value, int durationSeconds) {
        if (durationSeconds <= 0) {
            execute(player, value);
            return;
        }

        int totalTicks = durationSeconds * 20;
        double healthPerTick = (double) value / totalTicks;

        new BukkitRunnable() {
            int tick = 0;
            double accumulated = 0.0;

            @Override
            public void run() {
                if (tick >= totalTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                accumulated += healthPerTick;
                if (accumulated >= 1.0) {
                    int heal = (int) accumulated;
                    accumulated -= heal;
                    double maxHealth = player.getMaxHealth();
                    player.setHealth(Math.min(player.getHealth() + heal, maxHealth));
                }

                tick++;
            }
        }.runTaskTimer(Chuwudai.getInstance(), 0L, 1L);
    }

    @Override
    public String getEffectName() {
        return "生命值";
    }
}
