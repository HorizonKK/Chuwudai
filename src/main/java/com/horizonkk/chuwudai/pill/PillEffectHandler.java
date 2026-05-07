package com.horizonkk.chuwudai.pill;

import org.bukkit.entity.Player;

public interface PillEffectHandler {

    /**
     * 执行丹药效果
     * @param player 玩家
     * @param value 效果数值
     */
    void execute(Player player, int value);

    /**
     * 获取效果描述（用于日志或调试）
     * @return 效果名称
     */
    String getEffectName();
}