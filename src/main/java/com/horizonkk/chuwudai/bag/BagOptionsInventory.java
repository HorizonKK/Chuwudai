package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.UUID;

public class BagOptionsInventory implements InventoryHolder {

    private static final int VANILLA_SLOT = 2;
    private static final int ORE_SLOT = 4;
    private static final int STONE_SLOT = 6;
    private static final int BACK_SLOT = 8;

    private final BagData bagData;
    private final int returnPage;
    private UUID viewerId;
    private Inventory inventory;

    public BagOptionsInventory(BagData bagData, int returnPage) {
        this.bagData = bagData;
        this.returnPage = returnPage;
        this.inventory = Bukkit.createInventory(this, 9, ColorUtils.translateColorCodes("&8自动吸收选项"));
    }

    public void open(Player player) {
        this.viewerId = player.getUniqueId();
        refresh();
        player.openInventory(inventory);
    }

    public boolean isMenuSlot(int slot) {
        return slot >= 0 && slot < inventory.getSize();
    }

    public void handleClick(int slot, Player player) {
        UUID playerId = player.getUniqueId();
        if (slot == VANILLA_SLOT) {
            BagAutoPickupSettings.toggleVanillaItems(playerId);
            refresh();
        } else if (slot == ORE_SLOT) {
            BagAutoPickupSettings.toggleOreItems(playerId);
            refresh();
        } else if (slot == STONE_SLOT) {
            BagAutoPickupSettings.toggleSpiritStones(playerId);
            refresh();
        } else if (slot == BACK_SLOT) {
            new BagInventory(bagData, returnPage).open(player);
        }
    }

    private void refresh() {
        inventory.clear();
        BagAutoPickupSettings.Settings settings = BagAutoPickupSettings.get(viewerId);
        inventory.setItem(VANILLA_SLOT, createOptionItem(
                Material.CHEST,
                "&f原版物品",
                settings.isVanillaItems(),
                "&7吸收没有 CustomModelData、没有任意标签的普通物品"
        ));
        inventory.setItem(ORE_SLOT, createOptionItem(
                Material.IRON_ORE,
                "&b矿石物品",
                settings.isOreItems(),
                "&7吸收名字后缀为 [一阶] 到 [六阶] 的物品"
        ));
        inventory.setItem(STONE_SLOT, createOptionItem(
                Material.EMERALD,
                "&a灵石",
                settings.isSpiritStones(),
                "&7吸收配置中的灵石物品"
        ));
        inventory.setItem(BACK_SLOT, createButton(
                Material.ARROW,
                "&e返回储物袋",
                "&7返回上一页储物袋界面"
        ));
    }

    private ItemStack createOptionItem(Material material, String name, boolean enabled, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtils.translateColorCodes((enabled ? "&a" : "&c") + "■ " + name));
        meta.setLore(Arrays.asList(
                ColorUtils.translateColorCodes(description),
                ColorUtils.translateColorCodes("&7状态: " + (enabled ? "&a开启" : "&c关闭")),
                ColorUtils.translateColorCodes("&8点击切换")
        ));
        if (enabled) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtils.translateColorCodes(name));
        meta.setLore(Arrays.asList(ColorUtils.translateColorCodes(lore)));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public BagData getBagData() {
        return bagData;
    }
}
