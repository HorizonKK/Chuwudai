package com.horizonkk.hoXiuLian.api;

import com.horizonkk.hoXiuLian.manager.PillToxicitySystem;
import org.bukkit.entity.Player;

public class DanduAPI {

    public static double getDandu(Player player) {
        PillToxicitySystem system = PillToxicitySystem.getInstance();
        if (system != null) {
            return system.getDandu(player);
        }
        return 0.0;
    }

    public static double getMaxDandu(Player player) {
        PillToxicitySystem system = PillToxicitySystem.getInstance();
        if (system != null) {
            return system.getMaxDandu(player);
        }
        return 100.0;
    }

    public static void setDandu(Player player, double value) {
        PillToxicitySystem system = PillToxicitySystem.getInstance();
        if (system != null) {
            system.setDandu(player, value);
        }
    }

    public static void setMaxDandu(Player player, double maxValue) {
        PillToxicitySystem system = PillToxicitySystem.getInstance();
        if (system != null) {
            system.setMaxDandu(player, maxValue);
        }
    }

    public static void addDandu(Player player, double amount) {
        PillToxicitySystem system = PillToxicitySystem.getInstance();
        if (system != null) {
            system.addDandu(player, amount);
        }
    }

    public static void reduceDandu(Player player, double amount) {
        PillToxicitySystem system = PillToxicitySystem.getInstance();
        if (system != null) {
            system.reduceDandu(player, amount);
        }
    }
}
