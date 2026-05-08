package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.hook.ManaHook;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ManaHandler implements PillEffectHandler {

    @Override
    public void execute(Player player, int value) {
        if (!ManaHook.isHooked()) {
            player.giveExp(value);
            return;
        }
        double maxMana = ManaHook.getPlayerMaxMana(player);
        ManaHook.setPlayerMana(player, Math.min(ManaHook.getPlayerMana(player) + value, maxMana));
    }

    @Override
    public void execute(Player player, int value, int durationSeconds) {
        if (durationSeconds <= 0) {
            execute(player, value);
            return;
        }
        if (!ManaHook.isHooked()) {
            player.giveExp(value);
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
                    double maxMana = ManaHook.getPlayerMaxMana(player);
                    ManaHook.setPlayerMana(player, Math.min(ManaHook.getPlayerMana(player) + amt, maxMana));
                }

                tick++;
            }
        }.runTaskTimer(Chuwudai.getInstance(), 0L, 1L);
    }

    @Override
    public String getEffectName() {
        return "法力值";
    }
}
