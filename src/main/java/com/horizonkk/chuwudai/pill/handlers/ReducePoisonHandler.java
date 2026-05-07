package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;

public class ReducePoisonHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        // TODO: 集成您的丹毒系统
        // 示例：PoisonAPI.reducePoison(player, value);
    }
    
    @Override
    public String getEffectName() {
        return "减少丹毒";
    }
}
