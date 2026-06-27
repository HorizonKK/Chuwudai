package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.hoXiuLian.api.AttributePackAPI;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class AttributePackHandler {

    private static final String NAMESPACE = "liandan";
    private static final NamespacedKey PACK_KEY = new NamespacedKey(NAMESPACE, "dan_yao_attrpack");
    private static final NamespacedKey LEVEL_KEY = new NamespacedKey(NAMESPACE, "dan_yao_attrpack_level");
    private static final NamespacedKey DURATION_KEY = new NamespacedKey(NAMESPACE, "dan_yao_attrpack_duration");

    private AttributePackHandler() {
    }

    public static boolean hasAttributePack(ItemStack item) {
        return getPackName(item) != null;
    }

    public static boolean applyAttributePack(Player player, ItemStack item) {
        String packName = getPackName(item);
        if (packName == null) {
            return false;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("HoXiuLian")) {
            Chuwudai.getInstance().getLogger().warning(
                    "丹药属性包效果需要 HoXiuLian 已启用，已跳过: " + packName
            );
            return false;
        }

        int level = getInt(item, LEVEL_KEY, 1);
        long duration = getInt(item, DURATION_KEY, 0);
        boolean success = duration > 0
                ? AttributePackAPI.givePack(player, packName, level, duration)
                : AttributePackAPI.givePack(player, packName, level);

        if (!success) {
            Chuwudai.getInstance().getLogger().warning(
                    "丹药属性包效果应用失败: player=" + player.getName()
                            + ", pack=" + packName + ", level=" + level
            );
        }
        return success;
    }

    public static String describe(ItemStack item) {
        String packName = getPackName(item);
        if (packName == null) {
            return "";
        }

        int level = getInt(item, LEVEL_KEY, 1);
        int duration = getInt(item, DURATION_KEY, 0);
        return "属性包:" + packName + " Lv." + level
                + (duration > 0 ? "(" + duration + "秒)" : "");
    }

    private static String getPackName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        if (!container.has(PACK_KEY, PersistentDataType.STRING)) {
            return null;
        }

        String packName = container.get(PACK_KEY, PersistentDataType.STRING);
        if (packName == null || packName.trim().isEmpty()) {
            return null;
        }
        return packName.trim();
    }

    private static int getInt(ItemStack item, NamespacedKey key, int fallback) {
        if (item == null || !item.hasItemMeta()) {
            return fallback;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(key, PersistentDataType.INTEGER)) {
            return fallback;
        }

        Integer value = container.get(key, PersistentDataType.INTEGER);
        return value != null ? value : fallback;
    }
}
