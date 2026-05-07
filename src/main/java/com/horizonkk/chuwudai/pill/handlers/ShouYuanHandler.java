package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;

public class ShouYuanHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        // TODO: 集成您的寿元系统
        // 示例：ShouYuanAPI.addShouYuan(player, value);
    }
    
    @Override
    public String getEffectName() {
        return "寿元";
    }
}
