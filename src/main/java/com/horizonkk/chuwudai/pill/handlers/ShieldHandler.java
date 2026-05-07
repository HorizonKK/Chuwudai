package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;

public class ShieldHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        // TODO: 集成您的护盾系统
        // 示例：ShieldAPI.addShield(player, value);
    }
    
    @Override
    public String getEffectName() {
        return "护罩";
    }
}
