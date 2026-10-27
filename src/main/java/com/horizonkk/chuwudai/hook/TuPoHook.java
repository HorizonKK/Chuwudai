package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.hoXiuLian.api.TuPoAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** HoXiuLian 基础突破概率接口。 */
public final class TuPoHook {

    private static Plugin checkedPlugin;
    private static boolean available;

    private TuPoHook() {
    }

    /** 延迟检查依赖及 API，避免插件互相 softdepend 时过早初始化。 */
    public static boolean isHooked() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("HoXiuLian");
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        if (checkedPlugin != plugin) {
            checkedPlugin = plugin;
            available = false;
            try {
                Class.forName("com.horizonkk.hoXiuLian.api.TuPoAPI", false, plugin.getClass().getClassLoader())
                        .getMethod("addBreakthroughChance", Player.class, String.class, double.class);
                available = true;
                Chuwudai.getInstance().getLogger().info("成功挂钩 HoXiuLian 突破概率系统！");
            } catch (ReflectiveOperationException | LinkageError e) {
                Chuwudai.getInstance().getLogger().warning("HoXiuLian 缺少突破概率 API，请更新修真插件: " + e.getMessage());
            }
        }
        return available;
    }

    /**
     * 为在线玩家指定的突破前境界组增加基础概率，amount 单位为百分点。
     * 必须在主线程调用。true 表示已增加；存档失败由 HoXiuLian 重试，不应重复发放。
     */
    public static boolean addBreakthroughChance(Player player, String groupName, double amount) {
        if (player == null || groupName == null || groupName.isBlank()
                || !Double.isFinite(amount) || amount <= 0 || !Bukkit.isPrimaryThread() || !isHooked()) {
            return false;
        }
        try {
            return TuPoAPI.addBreakthroughChance(player, groupName, amount);
        } catch (RuntimeException | LinkageError e) {
            Chuwudai.getInstance().getLogger().warning("增加玩家突破概率失败: player=" + player.getName()
                    + ", group=" + groupName + ", amount=" + amount + ", 错误: " + e.getMessage());
            return false;
        }
    }
}
