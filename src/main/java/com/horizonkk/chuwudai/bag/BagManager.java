package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class BagManager {

    private final Random random = new Random();
    private final Map<String, BagData> bagCache = new HashMap<>();

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
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(bag);

        if (!leftover.isEmpty()) {
            // 背包已满，掉落在地上
            player.getWorld().dropItem(player.getLocation(), bag);
        }

        String bagType = getBagTypeName(size);
        String id = BagItem.getBagId(bag);
        player.sendMessage(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                Chuwudai.getInstance().getConfig().getString("messages.bag-received", "&a你获得了一个 %type% &a(ID: %id%)")
                        .replace("%type%", bagType)
                        .replace("%id%", id));
    }

    // 根据大小获取储物袋类型名称
    private String getBagTypeName(int size) {
        Map<?, ?> bagTypes = Chuwudai.getInstance().getConfig().getConfigurationSection("bag-types").getValues(false);
        if (bagTypes.containsKey(String.valueOf(size))) {
            Map<?, ?> typeInfo = (Map<?, ?>) bagTypes.get(String.valueOf(size));
            return (String) typeInfo.get("name");
        }
        return "储物袋";
    }

    // 打开储物袋界面
    public void openBag(Player player, String bagId) {
        BagData bagData = getBagData(bagId);
        if (bagData == null) {
            player.sendMessage(Chuwudai.getInstance().getConfig().getString("messages.prefix", "") +
                    Chuwudai.getInstance().getConfig().getString("messages.bag-not-found", "")
                            .replace("%id%", bagId));
            return;
        }

        BagInventory inventory = new BagInventory(bagData);
        inventory.open(player);
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