package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
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

    // 创建储物袋物品
    public ItemStack createBagItem(int size) {
        return BagItem.createBag(size, generateBagId());
    }

    // 给予玩家储物袋
    public void giveBagToPlayer(Player player, int size) {
        ItemStack bag = createBagItem(size);
        String bagId = BagItem.getBagId(bag); // 获取新生成的ID
        // 创建空的储物袋数据并保存
        BagData emptyBagData = new BagData(bagId, size);
        Chuwudai.getInstance().getDataManager().saveBag(emptyBagData);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(bag);
        if (!leftover.isEmpty()) {
            // 背包已满，掉落在地上
            player.getWorld().dropItem(player.getLocation(), bag);
        }

        String bagType = getBagTypeName(size);
        player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                Chuwudai.getInstance().getConfig().getString("messages.bag-received", "&a你获得了一个 %type% &a(ID: %id%)")
                        .replace("%type%", bagType)
                        .replace("%id%", bagId)));
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
        BagData bagData = getBagData(bagId);
        if (bagData == null) {
            // 检查是否是有效的储物袋ID（存在对应的物品）
            // 如果是新创建的储物袋，初始化数据
            int defaultSize = 27; // 可以通过其他方式获取实际大小
            bagData = new BagData(bagId, defaultSize);
            // 保存新创建的空数据
            Chuwudai.getInstance().getDataManager().saveBag(bagData);
            bagCache.put(bagId, bagData);
        }
        try {
            BagInventory inventory = new BagInventory(bagData);
            inventory.open(player);
            // 发送成功消息
            player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                    Chuwudai.getInstance().getConfig().getString("messages.bag-opened", "&a已打开储物袋 %id%")
                            .replace("%id%", bagId)));
            // 注意：打开时不需要更新Lore，因为库存事件会处理
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
    public void saveBagData(String bagId) {
        if (bagCache.containsKey(bagId)) {
            Chuwudai.getInstance().getDataManager().saveBag(bagCache.get(bagId));
        }
    }
}