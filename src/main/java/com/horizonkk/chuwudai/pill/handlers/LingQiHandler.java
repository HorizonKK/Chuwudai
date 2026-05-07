package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;

public class LingQiHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        // TODO: 集成您的灵气系统
        // 示例：LingQiAPI.addMaxLingQi(player, value);
    }
    
    @Override
    public String getEffectName() {
        return "灵气上限";
    }
}
