package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.utlis.BundleStyle;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

public class BagManager {

    private final Random random = new Random();
    private final Map<String, BagData> bagCache = new WeakHashMap<>();

    // 生成8位随机ID
    public String generateBagId() {
        String id;
        do {
            id = String.format("%08d", random.nextInt(100000000));
        } while (bagCache.containsKey(id) || Chuwudai.getInstance().getDataManager().bagExists(id));
        return id;
    }

    // 创建储物袋物品（无样式参数，随机颜色）
    public ItemStack createBagItem(int size) {
        String randomStyle = BundleStyle.getRandomStyleName();
        return BagItem.createBag(size, generateBagId(), randomStyle);
    }

    // 创建储物袋物品（指定样式）
    public ItemStack createBagItem(int size, String style) {
        return BagItem.createBag(size, generateBagId(), style);
    }

    // 给予玩家储物袋（随机样式）
    public void giveBagToPlayer(Player player, int size) {
        String randomStyle = BundleStyle.getRandomStyleName();
        giveBagToPlayer(player, size, randomStyle);
    }

    // 给予玩家储物袋（指定样式）
    public void giveBagToPlayer(Player player, int size, String style) {
        ItemStack bag = createBagItem(size, style);
        String bagId = BagItem.getBagId(bag);

        BagData emptyBagData = new BagData(bagId, size);
        Chuwudai.getInstance().getDataManager().saveBag(emptyBagData);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(bag);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), bag);
        }

        String bagType = getBagTypeName(size);
        String styleName = getStyleChineseName(style);
        player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                Chuwudai.getInstance().getConfig().getString("messages.bag-received", "&a你获得了一个 %type% &a(ID: %id%, 样式: %style%)")
                        .replace("%type%", bagType)
                        .replace("%id%", bagId)
                        .replace("%style%", styleName)));
    }

    private String getStyleChineseName(String style) {
        return BundleStyle.fromStyleName(style).getChineseName();
    }

    // 根据大小获取储物袋类型名称
    private String getBagTypeName(int size) {
        ConfigurationSection bagTypes = Chuwudai.getInstance().getConfig().getConfigurationSection("bag-types");
        if (bagTypes != null) {
            ConfigurationSection typeInfo = bagTypes.getConfigurationSection(String.valueOf(size));
            if (typeInfo != null) {
                return typeInfo.getString("name");
            }
        }
        return "储物袋";
    }

    // 打开储物袋界面
    public void openBag(Player player, String bagId) {
        if (Chuwudai.getInstance().getModBagBridge() != null
                && Chuwudai.getInstance().getModBagBridge().openBag(player, bagId)) {
            return;
        }

        BagData bagData = getBagData(bagId);
        if (bagData == null) {
            int defaultSize = 27;
            bagData = new BagData(bagId, defaultSize);
            Chuwudai.getInstance().getDataManager().saveBag(bagData);
            bagCache.put(bagId, bagData);
        }
        try {
            BagInventory inventory = new BagInventory(bagData);
            if (Chuwudai.getInstance().getModBagBridge() != null) {
                Chuwudai.getInstance().getModBagBridge().beginVanillaSession(player, bagId);
            }
            inventory.open(player);
            player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                    Chuwudai.getInstance().getConfig().getString("messages.bag-opened", "&a已打开储物袋 %id%")
                            .replace("%id%", bagId)));
        } catch (Exception e) {
            player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                    "&c打开储物袋时发生错误，请联系管理员"));
            Chuwudai.getInstance().getLogger().severe("打开储物袋时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ItemStack findBagInPlayerInventory(Player player, String bagId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && BagItem.isBagItem(item)) {
                String itemBagId = BagItem.getBagId(item);
                if (bagId.equals(itemBagId)) {
                    return item;
                }
            }
        }
        return null;
    }

    // 获取或创建储物袋数据
    public BagData getBagData(String bagId) {
        if (bagCache.containsKey(bagId)) {
            return bagCache.get(bagId);
        }

        BagData data = Chuwudai.getInstance().getDataManager().loadBag(bagId);
        if (data != null) {
            bagCache.put(bagId, data);
        }
        return data;
    }

    // 保存储物袋数据
    public boolean isBagOpen(String bagId) {
        if (bagId == null) return false;

        if (Chuwudai.getInstance().getModBagBridge() != null
                && Chuwudai.getInstance().getModBagBridge().isBagOpen(bagId)) {
            return true;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (topInventory.getHolder() instanceof BagInventory) {
                BagInventory bagInventory = (BagInventory) topInventory.getHolder();
                if (bagId.equals(bagInventory.getBagData().getId())) {
                    return true;
                }
            } else if (topInventory.getHolder() instanceof BagOptionsInventory) {
                BagOptionsInventory optionsInventory = (BagOptionsInventory) topInventory.getHolder();
                if (bagId.equals(optionsInventory.getBagData().getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public BagData upgradeBagSize(ItemStack bagItem, int additionalSlots) {
        if (additionalSlots <= 0 || !BagItem.isBagItem(bagItem)) {
            return null;
        }

        String bagId = BagItem.getBagId(bagItem);
        if (isBagOpen(bagId)) {
            return null;
        }

        BagData bagData = getBagData(bagId);
        if (bagData == null) {
            return null;
        }

        bagData.addSize(additionalSlots);
        BagItem.setBagSize(bagItem, bagData.getSize());
        BagItem.updateBagLore(bagItem, bagData.getStoneCounts(), bagData);
        Chuwudai.getInstance().getDataManager().saveBag(bagData);
        bagCache.put(bagId, bagData);
        return bagData;
    }

    public void saveBagData(String bagId) {
        if (bagCache.containsKey(bagId)) {
            Chuwudai.getInstance().getDataManager().saveBag(bagCache.get(bagId));
        }
    }
}
