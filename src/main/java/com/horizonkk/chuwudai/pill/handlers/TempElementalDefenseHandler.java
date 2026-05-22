package com.horizonkk.chuwudai.pill.handlers;

import org.bukkit.entity.Player;

public class TempElementalDefenseHandler extends TempBuffHandler {

    private static final int DEFAULT_DURATION = 20;

    @Override
    protected void applyBuff(Player player, int value) {
        // TODO: 接入实际的五行抗性属性系统（如 MythicMobs、SX-Attribute 等）
    }

    @Override
    protected void removeBuff(Player player, int value) {
        // TODO: 接入实际的五行抗性属性系统
    }

    @Override
    public void execute(Player player, int value) {
        applyTemporaryBuff(player, value, DEFAULT_DURATION);
    }

    @Override
    public void execute(Player player, int value, int durationSeconds) {
        applyTemporaryBuff(player, value, durationSeconds > 0 ? durationSeconds : DEFAULT_DURATION);
    }

    @Override
    public String getEffectName() {
        return "临时五行抗性";
    }
}
