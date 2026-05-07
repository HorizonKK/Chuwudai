package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class RegenHealthHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        // 默认2秒内分10次恢复
        int durationSeconds = 2;
        int totalTicks = durationSeconds * 20;
        int times = 10;
        int ticksPerHeal = totalTicks / times;
        int healPerTick = value / times;
        
        new BukkitRunnable() {
            int count = 0;
            
            @Override
            public void run() {
                if (count >= times || !player.isOnline()) {
                    cancel();
                    return;
                }
                
                double currentHealth = player.getHealth();
                double maxHealth = player.getMaxHealth();
                double newHealth = Math.min(currentHealth + healPerTick, maxHealth);
                player.setHealth(newHealth);
                
                count++;
            }
        }.runTaskTimer(Chuwudai.getInstance(), 0L, ticksPerHeal);
    }
    
    @Override
    public String getEffectName() {
        return "生命恢复";
    }
}
