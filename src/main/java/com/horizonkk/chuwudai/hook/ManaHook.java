package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.entity.Player;

public class ManaHook {

    private static boolean isHooked = false;

    static {
        initialize();
    }

    /**
     * 初始化钩子
     */
    private static void initialize() {
        try {
            // 检查 HoXiuLian 插件是否存在
            if (Chuwudai.getInstance().getServer().getPluginManager().getPlugin("HoXiuLian") != null) {
                // 尝试加载 ManaAPI 类
                Class.forName("com.horizonkk.hoXiuLian.api.ManaAPI");
                isHooked = true;
                Chuwudai.getInstance().getLogger().info("成功挂钩 HoXiuLian 法力系统！");
            } else {
                isHooked = false;
                Chuwudai.getInstance().getLogger().warning("未找到 HoXiuLian 插件，法力系统功能将不可用！");
            }
        } catch (ClassNotFoundException e) {
            isHooked = false;
            Chuwudai.getInstance().getLogger().warning("无法加载 HoXiuLian ManaAPI，法力系统功能将不可用！");
        }
    }

    /**
     * 检查法力系统是否可用
     * @return 是否已挂钩
     */
    public static boolean isHooked() {
        return isHooked;
    }

    // ==================== 法力系统API ====================

    /**
     * 获取玩家当前法力值
     * @param player 玩家对象
     * @return 法力值
     */
    public static double getPlayerMana(Player player) {
        if (!isHooked || player == null) return 0.0;
        try {
            return com.horizonkk.hoXiuLian.api.ManaAPI.getCurrentMana(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取玩家法力值时发生错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取玩家最大法力值
     * @param player 玩家对象
     * @return 最大法力值
     */
    public static double getPlayerMaxMana(Player player) {
        if (!isHooked || player == null) return 0.0;
        try {
            return com.horizonkk.hoXiuLian.api.ManaAPI.getMaxMana(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取玩家最大法力值时发生错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 设置玩家当前法力值
     * @param player 玩家对象
     * @param mana 法力值
     */
    public static void setPlayerMana(Player player, double mana) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.ManaAPI.setCurrentMana(player, mana);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("设置玩家法力值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 设置玩家最大法力值
     * @param player 玩家对象
     * @param maxMana 最大法力值
     */
    public static void setPlayerMaxMana(Player player, double maxMana) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.ManaAPI.setMaxMana(player, maxMana);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("设置玩家最大法力值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 为玩家增加法力值
     * @param player 玩家对象
     * @param amount 法力值数量
     */
    public static void addPlayerMana(Player player, double amount) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.ManaAPI.addMana(player, amount);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("为玩家增加法力值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 消耗玩家法力值
     * @param player 玩家对象
     * @param amount 法力值数量
     * @return 是否消耗成功
     */
    public static boolean consumePlayerMana(Player player, double amount) {
        if (!isHooked || player == null) return false;
        try {
            return com.horizonkk.hoXiuLian.api.ManaAPI.consumeMana(player, amount);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("消耗玩家法力值时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 恢复玩家法力值到最大值
     * @param player 玩家对象
     */
    public static void restorePlayerMana(Player player) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.ManaAPI.restoreMana(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("恢复玩家法力值时发生错误: " + e.getMessage());
        }
    }
}
