package com.horizonkk.chuwudai.pill;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class PillManager {
    private com.horizonkk.chuwudai.storage.SharedStorage shared() { return Chuwudai.getInstance().getSharedStorage(); }
    private com.horizonkk.chuwudai.storage.MySqlRepository.Key key(String id) { return new com.horizonkk.chuwudai.storage.MySqlRepository.Key(com.horizonkk.chuwudai.storage.MySqlRepository.Kind.PILL,id); }



    private final Random random = new Random();
    private final Map<String, PillData> pillCache = new HashMap<>();

    // 生成8位随机ID
    public String generatePillId() {
        if(shared()!=null) throw new com.horizonkk.chuwudai.storage.StorageAccessException(com.horizonkk.chuwudai.storage.StorageAccessException.Reason.NOT_READY,"MySQL 创建请使用 createPillAsync");
        String id;
        do {
            id = String.format("%08d", random.nextInt(100000000));
        } while (pillCache.containsKey(id) || Chuwudai.getInstance().getPillDataManager().pillExists(id));
        return id;
    }

    // 创建丹瓶物品
    public ItemStack createPillItem(int size) {
        return PillItem.createPill(size, generatePillId());
    }

    // 给予玩家丹瓶
    public void givePillToPlayer(Player player, int size) {
        if(shared()!=null) { com.horizonkk.chuwudai.api.ChuwudaiAPI.createPillAsync(size).whenComplete((item,e)->shared().onMain(()->{ if(e!=null) player.sendMessage("§c创建失败："+e.getMessage()); else if(player.isOnline()) player.getInventory().addItem(item).values().forEach(left->player.getWorld().dropItemNaturally(player.getLocation(),left)); return null; })); return; }
        ItemStack pill = createPillItem(size);
        String pillId = PillItem.getPillId(pill); // 获取新生成的ID

        // 创建空的丹瓶数据并保存
        PillData emptyPillData = new PillData(pillId, size);
        Chuwudai.getInstance().getPillDataManager().savePill(emptyPillData);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(pill);
        if (!leftover.isEmpty()) {
            // 背包已满，掉落在地上
            player.getWorld().dropItem(player.getLocation(), pill);
        }

        String pillType = getPillTypeName(size);
        player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                Chuwudai.getInstance().getConfig().getString("messages.pill-received", "&a你获得了一个 %type% &a(ID: %id%)")
                        .replace("%type%", pillType)
                        .replace("%id%", pillId)));
    }

    // 根据大小获取丹瓶类型名称
    private String getPillTypeName(int size) {
        ConfigurationSection pillTypes = Chuwudai.getInstance().getConfig().getConfigurationSection("pill-types");
        if (pillTypes != null) {
            ConfigurationSection typeInfo = pillTypes.getConfigurationSection(String.valueOf(size));
            if (typeInfo != null) {
                String name = typeInfo.getString("name");
                if (name != null) {
                    return name.replace("&", "§");
                }
            }
        }
        return "丹瓶";
    }

    // 打开丹瓶界面
    public void openPill(Player player, String pillId) {
        if(pillId==null || java.util.Arrays.stream(player.getInventory().getContents()).noneMatch(item->item!=null && item.getAmount()>0 && PillItem.isPillItem(item) && pillId.equals(PillItem.getPillId(item)))) { player.sendMessage("§c你必须携带对应的丹瓶才能打开！"); return; }
        if(shared()!=null && !shared().isReady(key(pillId))) {
            shared().prepare(key(pillId)).whenComplete((v,e)->shared().onMain(()->{
                if(player.isOnline()) { if(e==null) openPill(player,pillId); else player.sendMessage("§c容器暂不可用："+e.getMessage()); }
                return null;
            })); return;
        }
        if(shared()!=null && com.horizonkk.chuwudai.storage.StorageListener.isViewed(key(pillId))) { player.sendMessage("§c容器正在使用中"); return; }

        PillData pillData = getPillData(pillId);
        if (pillData == null) {
            player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                    Chuwudai.getInstance().getConfig().getString("messages.pill-not-found", "&c找不到ID为 %id% 的丹瓶！")
                            .replace("%id%", pillId)));
            return;
        }

        try {
            PillInventory inventory = new PillInventory(pillData);
            inventory.open(player);

            // 发送成功消息
            player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                    Chuwudai.getInstance().getConfig().getString("messages.pill-opened", "&a已打开丹瓶 %id%")
                            .replace("%id%", pillId)));
            // 注意：打开时不更新Lore，只在关闭或内容改变时更新
        } catch (Exception e) {
            player.sendMessage(ColorUtils.translateColorCodes(Chuwudai.getInstance().getConfig().getString("messages.prefix", "&8[&6储物袋&8] &f") +
                    "&c打开丹瓶时发生错误，请联系管理员"));
            Chuwudai.getInstance().getLogger().severe("打开丹瓶时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 获取或创建丹瓶数据
    public PillData getPillData(String pillId) {
        if(shared()!=null) return (PillData)shared().get(key(pillId));
        if (pillCache.containsKey(pillId)) {
            return pillCache.get(pillId);
        }

        PillData data = Chuwudai.getInstance().getPillDataManager().loadPill(pillId);
        if (data != null) {
            pillCache.put(pillId, data);
        }
        return data;
    }

    // 保存丹瓶数据
    public void savePillData(String pillId) {
        if(shared()!=null) { shared().touch(key(pillId)); return; }
        if (pillCache.containsKey(pillId)) {
            Chuwudai.getInstance().getPillDataManager().savePill(pillCache.get(pillId));
        }
    }
}
