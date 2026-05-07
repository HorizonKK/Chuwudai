package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;

public class PoisonHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        // TODO: 集成您的丹毒系统
        // 示例：PoisonAPI.addPoison(player, value);
    }
    
    @Override
    public String getEffectName() {
        return "丹毒";
    }
}
