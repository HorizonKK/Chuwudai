package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.pill.PillEffectHandler;
import org.bukkit.entity.Player;

public class SaturationHandler implements PillEffectHandler {

    @Override
    public void execute(Player player, int value) {
        int currentFoodLevel = player.getFoodLevel();
        int newFoodLevel = Math.min(currentFoodLevel + value, 20);
        player.setFoodLevel(newFoodLevel);

        float currentSaturation = player.getSaturation();
        float newSaturation = Math.min(currentSaturation + value, 20.0f);
        player.setSaturation(newSaturation);
    }

    @Override
    public String getEffectName() {
        return "饱食度";
    }
}
