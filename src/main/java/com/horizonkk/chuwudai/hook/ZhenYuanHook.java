package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.entity.Player;

public class ZhenYuanHook {

    private static boolean isHooked = false;

    static {
        initialize();
    }

    /**
     * 初始化钩子
     */
    private static void initialize() {
        try {
            if (Chuwudai.getInstance().getServer().getPluginManager().getPlugin("HoXiuLian") != null) {
                Class.forName("com.horizonkk.hoXiuLian.api.HoXiuLianAPI");
                isHooked = true;
                Chuwudai.getInstance().getLogger().info("成功挂钩 HoXiuLian 修真系统！");
            } else {
                isHooked = false;
                Chuwudai.getInstance().getLogger().warning("未找到 HoXiuLian 插件，真元系统功能将不可用！");
            }
        } catch (ClassNotFoundException e) {
            isHooked = false;
            Chuwudai.getInstance().getLogger().warning("无法加载 HoXiuLian API，真元系统功能将不可用！");
        }
    }

    /**
     * 检查真元系统是否可用
     */
    public static boolean isHooked() {
        return isHooked;
    }

    // ==================== 真元系统API ====================

    /**
     * 获取玩家当前真元值
     */
    public static double getPlayerZhenYuan(Player player) {
        if (!isHooked || player == null) return 0.0;
        try {
            return com.horizonkk.hoXiuLian.api.HoXiuLianAPI.getPlayerCultivationExp(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取玩家真元值时发生错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 设置玩家当前真元值
     */
    public static void setPlayerZhenYuan(Player player, double zhenyuan) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.HoXiuLianAPI.setPlayerCultivationExp(player, zhenyuan);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("设置玩家真元值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 为玩家增加真元值
     * @return 是否升级
     */
    public static boolean addPlayerZhenYuan(Player player, double amount) {
        if (!isHooked || player == null) return false;
        try {
            return com.horizonkk.hoXiuLian.api.HoXiuLianAPI.addPlayerExperience(player, amount);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("为玩家增加真元值时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 减少玩家真元值
     * @return 是否降级
     */
    public static boolean reducePlayerZhenYuan(Player player, double amount) {
        if (!isHooked || player == null) return false;
        try {
            return com.horizonkk.hoXiuLian.api.HoXiuLianAPI.reducePlayerExperience(player, amount);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("减少玩家真元值时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取升级到下一级所需真元
     */
    public static double getExpToNextLevel(Player player) {
        if (!isHooked || player == null) return 0.0;
        try {
            return com.horizonkk.hoXiuLian.api.HoXiuLianAPI.getExpToNextLevel(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取升级所需真元时发生错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取当前升级进度百分比
     */
    public static double getProgressPercent(Player player) {
        if (!isHooked || player == null) return 0.0;
        try {
            return com.horizonkk.hoXiuLian.api.HoXiuLianAPI.getProgressPercent(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取升级进度时发生错误: " + e.getMessage());
            return 0.0;
        }
    }
}
