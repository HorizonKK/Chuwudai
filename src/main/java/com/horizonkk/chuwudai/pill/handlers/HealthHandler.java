package com.horizonkk.chuwudai.pill.handlers;


import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;

public class HealthHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        double currentHealth = player.getHealth();
        double maxHealth = player.getMaxHealth();
        double newHealth = Math.min(currentHealth + value, maxHealth);
        player.setHealth(newHealth);
    }
    
    @Override
    public String getEffectName() {
        return "生命值";
    }
}
