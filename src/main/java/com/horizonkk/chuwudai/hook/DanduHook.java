package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.entity.Player;

public class DanduHook {

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
                // 尝试加载 DanduAPI 类
                Class.forName("com.horizonkk.hoXiuLian.api.DanduAPI");
                isHooked = true;
                Chuwudai.getInstance().getLogger().info("成功挂钩 HoXiuLian 丹毒系统！");
            } else {
                isHooked = false;
                Chuwudai.getInstance().getLogger().warning("未找到 HoXiuLian 插件，丹毒系统功能将不可用！");
            }
        } catch (ClassNotFoundException e) {
            isHooked = false;
            Chuwudai.getInstance().getLogger().warning("无法加载 HoXiuLian DanduAPI，丹毒系统功能将不可用！");
        }
    }

    /**
     * 检查丹毒系统是否可用
     * @return 是否已挂钩
     */
    public static boolean isHooked() {
        return isHooked;
    }

    // ==================== 丹毒系统API ====================

    /**
     * 获取玩家当前丹毒值
     * @param player 玩家对象
     * @return 丹毒值
     */
    public static double getDandu(Player player) {
        if (!isHooked || player == null) return 0.0;
        try {
            return com.horizonkk.hoXiuLian.api.DanduAPI.getDandu(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取玩家丹毒值时发生错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取玩家最大丹毒值
     * @param player 玩家对象
     * @return 最大丹毒值
     */
    public static double getMaxDandu(Player player) {
        if (!isHooked || player == null) return 100.0;
        try {
            return com.horizonkk.hoXiuLian.api.DanduAPI.getMaxDandu(player);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("获取玩家最大丹毒值时发生错误: " + e.getMessage());
            return 100.0;
        }
    }

    /**
     * 设置玩家丹毒值
     * @param player 玩家对象
     * @param value 丹毒值
     */
    public static void setDandu(Player player, double value) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.DanduAPI.setDandu(player, value);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("设置玩家丹毒值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 设置玩家最大丹毒值
     * @param player 玩家对象
     * @param maxValue 最大丹毒值
     */
    public static void setMaxDandu(Player player, double maxValue) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.DanduAPI.setMaxDandu(player, maxValue);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("设置玩家最大丹毒值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 为玩家增加丹毒值
     * @param player 玩家对象
     * @param amount 丹毒值数量
     */
    public static void addDandu(Player player, double amount) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.DanduAPI.addDandu(player, amount);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("为玩家增加丹毒值时发生错误: " + e.getMessage());
        }
    }

    /**
     * 减少玩家丹毒值
     * @param player 玩家对象
     * @param amount 丹毒值数量
     */
    public static void reduceDandu(Player player, double amount) {
        if (!isHooked || player == null) return;
        try {
            com.horizonkk.hoXiuLian.api.DanduAPI.reduceDandu(player, amount);
        } catch (Exception e) {
            Chuwudai.getInstance().getLogger().warning("减少玩家丹毒值时发生错误: " + e.getMessage());
        }
    }
}
