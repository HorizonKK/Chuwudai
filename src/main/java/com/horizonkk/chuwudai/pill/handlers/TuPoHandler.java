package com.horizonkk.chuwudai.pill.handlers;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.hook.TuPoHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pers.neige.neigeitems.libs.bot.inker.bukkit.nbt.Nbt;
import pers.neige.neigeitems.libs.bot.inker.bukkit.nbt.NbtCompound;
import pers.neige.neigeitems.libs.bot.inker.bukkit.nbt.NbtNumeric;
import pers.neige.neigeitems.libs.bot.inker.bukkit.nbt.NbtString;
import pers.neige.neigeitems.utils.ItemUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态境界突破效果，读取实际服用物品的 NBT，直接服用和丹瓶服用共用。
 * <pre>
 * nbt:
 *   tupo_炼气: 15
 *   tupo_筑基: 2.5
 * </pre>
 * 数值为永久增加的基础概率百分点，必须是正有限数，不受 duration 影响。
 * 中文键通过已依赖的 NeigeItems NBT API 读取，不能创建为 Bukkit NamespacedKey。
 * 也兼容 PublicBukkitValues 中的 liandan:tupo_&lt;境界&gt;；重复境界以根标签为准，仅生效一次。
 */
public final class TuPoHandler {

    private TuPoHandler() {
    }

    public record Result(boolean hasTags, List<String> appliedEffects) {
        public Result {
            appliedEffects = List.copyOf(appliedEffects);
        }
    }

    public static boolean hasAnyEffect(ItemStack item) {
        // 无效数值也识别为突破丹，在服用时拒绝生效，避免被当作普通食物消耗。
        return !readEffects(item).isEmpty();
    }

    public static Result applyEffects(Player player, ItemStack item) {
        Map<String, Double> effects = readEffects(item);
        List<String> applied = new ArrayList<>();
        for (Map.Entry<String, Double> entry : effects.entrySet()) {
            String group = entry.getKey();
            double amount = entry.getValue();
            if (group.isBlank() || !Double.isFinite(amount) || amount <= 0) {
                Chuwudai.getInstance().getLogger().warning("无效的突破丹标签: tupo_" + group
                        + "，数值必须为大于 0 的有限数字。");
                continue;
            }
            if (TuPoHook.addBreakthroughChance(player, group, amount)) {
                String value = BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
                applied.add(group + "基础突破概率+" + value + "个百分点");
                player.sendMessage("§a" + group + "基础突破概率增加 " + value + " 个百分点。");
            } else {
                Chuwudai.getInstance().getLogger().warning("突破丹效果未生效: player=" + player.getName()
                        + ", group=" + group + ", amount=" + amount + "。请检查 HoXiuLian API、境界键名及玩家数据。");
            }
        }
        return new Result(!effects.isEmpty(), applied);
    }

    private static Map<String, Double> readEffects(ItemStack item) {
        if (item == null || item.getType().isAir() || !Bukkit.getPluginManager().isPluginEnabled("NeigeItems")) {
            return Map.of();
        }
        try {
            return NbtReader.read(item);
        } catch (RuntimeException | LinkageError e) {
            Chuwudai.getInstance().getLogger().warning("读取突破丹 NBT 失败: " + e.getMessage());
            return Map.of();
        }
    }

    /** 隔离可选 NeigeItems 类型，未安装时其他丹药效果仍可加载。 */
    private static final class NbtReader {
        private static Map<String, Double> read(ItemStack item) {
            NbtCompound nbt = ItemUtils.getNbtOrNull(item);
            Map<String, Double> effects = new LinkedHashMap<>();
            if (nbt != null) {
                collect(nbt, "tupo_", effects);
                collect(nbt.getCompound("PublicBukkitValues", null), "liandan:tupo_", effects);
            }
            return effects;
        }

        private static void collect(NbtCompound nbt, String prefix, Map<String, Double> effects) {
            if (nbt == null) return;
            for (String key : nbt.keySet()) {
                if (!key.startsWith(prefix)) continue;
                String group = key.substring(prefix.length());
                // 不按下划线拆分，保留完整境界键名。
                effects.putIfAbsent(group, readAmount(nbt.get(key)));
            }
        }

        private static double readAmount(Nbt<?> value) {
            if (value instanceof NbtNumeric<?> number) {
                return number.getAsDouble();
            }
            if (value instanceof NbtString text) {
                try {
                    return Double.parseDouble(text.getAsString().trim());
                } catch (NumberFormatException ignored) {
                    // 保留无效标签的存在信息，服用时提示并拒绝该效果。
                }
            }
            return Double.NaN;
        }
    }
}
