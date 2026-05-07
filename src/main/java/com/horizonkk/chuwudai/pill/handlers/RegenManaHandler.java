package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class RegenManaHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        // 默认2秒内分10次恢复
        int durationSeconds = 2;
        int totalTicks = durationSeconds * 20;
        int times = 10;
        int ticksPerHeal = totalTicks / times;
        int manaPerTick = value / times;
        
        new BukkitRunnable() {
            int count = 0;
            
            @Override
            public void run() {
                if (count >= times || !player.isOnline()) {
                    cancel();
                    return;
                }
                
                // TODO: 集成您的法力系统
                // 示例：ManaAPI.addMana(player, manaPerTick);
                
                count++;
            }
        }.runTaskTimer(Chuwudai.getInstance(), 0L, ticksPerHeal);
    }
    
    @Override
    public String getEffectName() {
        return "法力恢复";
    }
}
