package com.horizonkk.chuwudai.pill.handlers;


import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class TempBuffHandler implements PillEffectHandler {
    
    protected abstract void applyBuff(Player player, int value);
    protected abstract void removeBuff(Player player, int value);
    
    protected void applyTemporaryBuff(Player player, int value, int durationSeconds) {
        applyBuff(player, value);
        
        // 延迟移除效果
        new BukkitRunnable() {
            @Override
            public void run() {
                removeBuff(player, value);
            }
        }.runTaskLater(Chuwudai.getInstance(), durationSeconds * 20L);
    }
}
