package com.horizonkk.chuwudai.hook;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagItem;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class PlaceholderAPI extends PlaceholderExpansion {

    private final Chuwudai plugin;

    public PlaceholderAPI(Chuwudai plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "chuwudai";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HorizonKK";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if(!org.bukkit.Bukkit.isPrimaryThread()) return "--";
        if (player == null) {
            return "";
        }
        // 统计灵石数量
        Map<String, Integer> stoneCounts = new HashMap<>();
        stoneCounts.put("&f下品灵石", 0);
        stoneCounts.put("&a中品灵石", 0);
        stoneCounts.put("&e上品灵石", 0);
        stoneCounts.put("&c极品灵石", 0);
        // 只检查索引为9的槽位
        int targetSlot = 9;
        ItemStack item = player.getInventory().getItem(targetSlot);
        if (item != null && BagItem.isBagItem(item)) {
            String bagId = BagItem.getBagId(item);
            if(plugin.getSharedStorage()!=null) {
                var shared=plugin.getSharedStorage();
                var key=new com.horizonkk.chuwudai.storage.MySqlRepository.Key(com.horizonkk.chuwudai.storage.MySqlRepository.Kind.BAG,bagId);
                if(!shared.isReady(key)) { shared.prepare(key).exceptionally(e->null); return "--"; }
            }
            BagData bagData = plugin.getBagManager().getBagData(bagId);
            if (bagData != null) {
                Map<String, Integer> bagStones = bagData.getStoneCounts();
                for (Map.Entry<String, Integer> entry : bagStones.entrySet()) {
                    String stoneType = entry.getKey();
                    int count = entry.getValue();
                    stoneCounts.put(stoneType, stoneCounts.getOrDefault(stoneType, 0) + count);
                }
            }
        }
        // 返回对应参数的值
        switch (params.toLowerCase()) {
            case "xpls":
                return String.valueOf(stoneCounts.get("&f下品灵石"));
            case "zpls":
                return String.valueOf(stoneCounts.get("&a中品灵石"));
            case "spls":
                return String.valueOf(stoneCounts.get("&e上品灵石"));
            case "jpls":
                return String.valueOf(stoneCounts.get("&c极品灵石"));
            case "is_bag":
                if (item != null && BagItem.isBagItem(item)){
                    return "true";
                } else {
                    return "false";
                }
            default:
                return null;
        }
    }

}
