package com.horizonkk.chuwudai.pill.handlers;


import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;

public class ManaHandler implements PillEffectHandler {
    
    @Override
    public void execute(Player player, int value) {
        // TODO: 集成您的法力系统
        // 示例：ManaAPI.addMana(player, value);
        
        // 临时使用经验值模拟（实际使用时替换为您的法力系统）
        player.giveExp(value);
    }
    
    @Override
    public String getEffectName() {
        return "法力值";
    }
}
