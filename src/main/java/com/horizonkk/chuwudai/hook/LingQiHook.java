package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.entity.Player;

public class LingQiHook {

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
                // 尝试加载 LingQiAPI 类
                Class.forName("com.horizonkk.hoXiuLian.api.LingQiAPI");
                isHooked = true;
                Chuwudai.getInstance().getLogger().info("成功挂钩 HoXiuLian 灵气系统！");
            } else {
                isHooked = false;
                Chuwudai.getInstance().getLogger().warning("未找到 HoXiuLian 插件，灵气系统功能将不可用！");
            }
        } catch (ClassNotFoundException e) {
            isHooked = false;
            Chuwudai.getInstance().getLogger().warning("无法加载 HoXiuLian LingQiAPI，灵气系统功能将不可用！");
        }
    }

    /**
     * 检查灵气系统是否可用
     * @return 是否已挂钩
     */
    public static boolean isHooked() {
        return isHooked;
    }

    // ==================== 灵气系统API ====================

    /**
     * 获取玩家当前灵气值
     * @param player 玩家对象
     * @return 灵气值
     */
    public static double getLingQi(Player player) {
        if (!isHooked || player == null) return 0.0;
        try {
            return com.horizonkk.hoXiuLian.api.LingQiAPI.getLingQi(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取玩家灵气值时发生错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取玩家最大灵气值
     * @param player 玩家对象
     * @return 最大灵气值
     */
    public static double getMaxLingQi(Player player) {
        if (!isHooked || player == null) return 100.0;
        try {
            return com.horizonkk.hoXiuLian.api.LingQiAPI.getMaxLingQi(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取玩家最大灵气值时发生错误: " + e.getMessage());
            return 100.0;
        }
    }

    /**
     * 设置玩家当前灵气值
     * @param player 玩家对象
     * @param value 灵气值
     */
    public static void setLingQi(Player player, double value) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.LingQiAPI.setLingQi(player, value);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("设置玩家灵气值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 设置玩家最大灵气值
     * @param player 玩家对象
     * @param maxValue 最大灵气值
     */
    public static void setMaxLingQi(Player player, double maxValue) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.LingQiAPI.setMaxLingQi(player, maxValue);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("设置玩家最大灵气值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 为玩家增加灵气值
     * @param player 玩家对象
     * @param amount 灵气值数量
     */
    public static void addLingQi(Player player, double amount) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.LingQiAPI.addLingQi(player, amount);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("为玩家增加灵气值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 消耗玩家灵气值
     * @param player 玩家对象
     * @param amount 灵气值数量
     * @return 是否消耗成功
     */
    public static boolean consumeLingQi(Player player, double amount) {
        if (!isHooked || player == null) return false;
        try {
            return com.horizonkk.hoXiuLian.api.LingQiAPI.consumeLingQi(player, amount);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("消耗玩家灵气值时发生错误: " + e.getMessage());
            return false;
        }
    }
}
