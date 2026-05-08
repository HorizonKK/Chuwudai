package com.horizonkk.chuwudai.pill;

import org.bukkit.entity.Player;

public interface PillEffectHandler {

    /**
     * 即时执行丹药效果
     */
    void execute(Player player, int value);

    /**
     * 在持续时间内逐步结算丹药效果
     * @param durationSeconds 持续时间（秒），0 表示即时
     */
    default void execute(Player player, int value, int durationSeconds) {
        execute(player, value);
    }

    String getEffectName();
}
