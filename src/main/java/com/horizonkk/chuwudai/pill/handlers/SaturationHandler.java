package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SaturationHandler implements PillEffectHandler {

    @Override
    public void execute(Player player, int value) {
        player.setFoodLevel(Math.min(player.getFoodLevel() + value, 20));
        player.setSaturation(Math.min(player.getSaturation() + value, 20.0f));
    }

    @Override
    public void execute(Player player, int value, int durationSeconds) {
        if (durationSeconds <= 0) {
            execute(player, value);
            return;
        }

        int totalTicks = durationSeconds * 20;
        double perTick = (double) value / totalTicks;

        new BukkitRunnable() {
            int tick = 0;
            double accumulated = 0.0;

            @Override
            public void run() {
                if (tick >= totalTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                accumulated += perTick;
                if (accumulated >= 1.0) {
                    int amt = (int) accumulated;
                    accumulated -= amt;
                    player.setFoodLevel(Math.min(player.getFoodLevel() + amt, 20));
                }

                tick++;
            }
        }.runTaskTimer(Chuwudai.getInstance(), 0L, 1L);
    }

    @Override
    public String getEffectName() {
        return "饱食度";
    }
}
