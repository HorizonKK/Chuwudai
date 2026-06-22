package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BagInventory implements InventoryHolder {

    private final BagData bagData;
    private Inventory inventory;
    private int currentPage;
    private UUID viewerId;
    private final Map<Integer, Integer> slotMapping = new HashMap<>();
    // 每页的实际存储格数（不包括控制栏）
    private static final int ITEMS_PER_PAGE = 45;
    // 控制栏起始位置
    private static final int CONTROL_BAR_START = 45;

    public BagInventory(BagData bagData) {
        this(bagData, 0);
    }

    public BagInventory(BagData bagData, int currentPage) {
        this.bagData = bagData;
        this.currentPage = Math.max(0, Math.min(currentPage, Math.max(0, bagData.getTotalPages() - 1)));
        createInventory();
    }

    private void createInventory() {
        int totalPages = bagData.getTotalPages();
        int actualSize = bagData.getSize();
        // 计算当前页的起始和结束位置
        int pageStart = currentPage * 45;
        int pageEnd = Math.min(pageStart + 45, actualSize);
        int currentPageStorageCount = pageEnd - pageStart;
        // 计算当前页需要的存储行数（每行9格）
        int storageRows = (int) Math.ceil((double) currentPageStorageCount / 9);
        // 计算总界面大小（存储行数 + 1排控制栏）
        int inventorySize = (storageRows + 1) * 9;
        // 但要确保不超过Bukkit的最大限制（通常是54格）
//        inventorySize = Math.min(inventorySize, 54);
        inventory = Bukkit.createInventory(this, inventorySize, "储物袋 #" + bagData.getId());
        loadItems();
        addControlButtons();
    }

    private void loadItems() {
        slotMapping.clear();
        int pageStart = currentPage * 45;
        int pageEnd = Math.min(pageStart + 45, bagData.getSize());

        // 计算当前页实际的存储槽数量
        int currentPageStorageCount = pageEnd - pageStart;
        // 计算可用的存储槽位数（控制栏始终在最后）
        int maxStorageSlots = inventory.getSize() - 9;

        // 先放置实际的物品
        for (int i = pageStart; i < pageEnd && (i - pageStart) < maxStorageSlots; i++) {
            int invSlot = i - pageStart;
            slotMapping.put(invSlot, i);

            ItemStack item = bagData.getItem(i);
            if (item != null) {
                inventory.setItem(invSlot, item);
            }
        }

        // 然后将超出实际存储数量的槽位用黑色玻璃板填充
        ItemStack lockedGlassPane = createLockedGlassPane();
        for (int i = currentPageStorageCount; i < maxStorageSlots; i++) {
            inventory.setItem(i, lockedGlassPane);
        }
    }

    // 创建未解锁行的玻璃板
    private ItemStack createLockedGlassPane() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes("&c当前未解锁该行 取消点击"));
        meta.setLore(Arrays.asList(
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes("&7该行槽位尚未解锁"),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes("&7请升级储物袋以解锁更多存储空间")
        ));
        glass.setItemMeta(meta);
        return glass;
    }

    private void addControlButtons() {
        int totalPages = bagData.getTotalPages();
        int inventorySize = inventory.getSize();

        // 计算控制栏起始位置（始终在最后）
        int controlStart = inventorySize - 9;

        // 清除控制栏区域
        for (int i = controlStart; i < Math.min(controlStart + 9, inventorySize); i++) {
            inventory.setItem(i, null);
        }

        // 添加玻璃板分隔线（除了按钮位置）
        ItemStack glassPane = createGlassPane();
        for (int i = controlStart; i < Math.min(controlStart + 9, inventorySize); i++) {
            // 排除按钮位置：最左、中、最右
            if (i != controlStart && i != controlStart + 4 && i != Math.min(controlStart + 8, inventorySize - 1)) {
                inventory.setItem(i, glassPane);
            }
        }

        // 上一页按钮（最左侧）
        if (currentPage > 0) {
            ItemStack prevButton = createButton(
                    Material.ARROW,
                    Chuwudai.getInstance().getConfig().getString("ui.page-prev", "&c← 上一页"),
                    Arrays.asList("§7点击切换到上一页")
            );
            inventory.setItem(controlStart, prevButton);
        } else {
            // 不需要上一页时显示玻璃板
            inventory.setItem(controlStart, glassPane);
        }

        // 页码和储物袋信息（中间）- 添加颜色处理
        int pageStart = currentPage * 45;
        int pageEnd = Math.min(pageStart + 45, bagData.getSize());
        int currentPageStorageCount = pageEnd - pageStart;

        String pageTitle = Chuwudai.getInstance().getConfig().getString("ui.current-page", "&7第 %page% 页 / %total% 页")
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%total%", String.valueOf(totalPages));

        ItemStack pageInfo = createButton(
                Material.BOOK,
                pageTitle,
                createPageInfoLore(currentPageStorageCount)
        );
        inventory.setItem(controlStart + 4, pageInfo);

        // 下一页按钮（最右侧）
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = createButton(
                    Material.ARROW,
                    Chuwudai.getInstance().getConfig().getString("ui.page-next", "&a下一页 →"),
                    Arrays.asList("§7点击切换到下一页")
            );
            inventory.setItem(Math.min(controlStart + 8, inventorySize - 1), nextButton);
        } else {
            // 不需要下一页时显示玻璃板
            inventory.setItem(Math.min(controlStart + 8, inventorySize - 1), glassPane);
        }
    }

    private ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        return glass;
    }

    private java.util.List<String> createPageInfoLore(int currentPageStorageCount) {
        BagAutoPickupSettings.Settings settings = getAutoPickupSettings();
        return Arrays.asList(
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes("&7总容量: &f" + bagData.getSize() + " 格"),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes("&7当前页存储: &f" + currentPageStorageCount + " 格"),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes("&7存储物品: &f" + bagData.getUsedSlots() + " 个"),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes(""),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes("&6自动吸收选项:"),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes(getAutoPickupStatusLine("原版物品", settings.isVanillaItems())),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes(getAutoPickupStatusLine("矿石物品", settings.isOreItems())),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes(getAutoPickupStatusLine("灵石", settings.isSpiritStones())),
                com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes("&8点击打开选项菜单")
        );
    }

    private BagAutoPickupSettings.Settings getAutoPickupSettings() {
        if (viewerId == null) {
            return BagAutoPickupSettings.get(new UUID(0L, 0L));
        }
        return BagAutoPickupSettings.get(viewerId);
    }

    private String getAutoPickupStatusLine(String name, boolean enabled) {
        return "  &7" + name + ": " + (enabled ? "&a开启" : "&c关闭");
    }

    private ItemStack createButton(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        // 使用ColorUtils处理颜色代码
        meta.setDisplayName(com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes(name));

        if (lore != null) {
            // 处理Lore中的颜色代码
            java.util.List<String> coloredLore = new java.util.ArrayList<>();
            for (String line : lore) {
                coloredLore.add(com.horizonkk.chuwudai.utlis.ColorUtils.translateColorCodes(line));
            }
            meta.setLore(coloredLore);
        }

        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player) {
        this.viewerId = player.getUniqueId();
        addControlButtons();
        player.openInventory(inventory);
        if (Chuwudai.getInstance().getModBagBridge() != null) {
            Chuwudai.getInstance().getModBagBridge().beginVanillaSession(player, bagData.getId());
        }
    }

    public boolean isControlSlot(int slot) {
        int inventorySize = inventory.getSize();
        int controlStart = inventorySize - 9;
        // 控制栏范围，包含所有控制按钮（包括中间的书籍按钮）
        return (slot >= controlStart && slot < inventorySize);
    }

    public void handleControlClick(int slot) {
        handleControlClick(slot, ClickType.LEFT, null);
    }

    public void handleControlClick(int slot, ClickType click, Player player) {
        int totalPages = bagData.getTotalPages();
        int inventorySize = inventory.getSize();
        int controlStart = inventorySize - 9;

        // 检查是否点击了页码信息按钮（中间位置）
        if (slot == controlStart + 4) {
            if (player != null) {
                new BagOptionsInventory(bagData, currentPage).open(player);
            }
            return;
        }

        if (slot == controlStart && currentPage > 0) {
            // 上一页
            currentPage--;
            refresh();
        } else if (slot == Math.min(controlStart + 8, inventorySize - 1) && currentPage < totalPages - 1) {
            // 下一页
            currentPage++;
            refresh();
        }
        // 其他控制槽位（包括中间书籍按钮周围）不处理任何操作
    }

    public void refresh() {
        saveItems();
        inventory.clear();
        loadItems();
        addControlButtons();

        for (org.bukkit.entity.HumanEntity viewer : inventory.getViewers()) {
            if (viewer instanceof Player) {
                ((Player) viewer).updateInventory();
            }
        }
    }

    public void saveItems() {
        for (int invSlot : slotMapping.keySet()) {
            int dataSlot = slotMapping.get(invSlot);
            ItemStack item = inventory.getItem(invSlot);

            if (item != null && !isControlItem(item)) {
                bagData.setItem(dataSlot, item);
            } else if (item == null) {
                bagData.removeItem(dataSlot);
            }
        }

        // 保存数据
        Chuwudai.getInstance().getBagManager().saveBagData(bagData.getId());
    }

    private boolean isControlItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        String displayName = item.getItemMeta().getDisplayName();
        return displayName != null && (
                displayName.contains("上一页") ||
                        displayName.contains("下一页") ||
                        displayName.contains("关闭") ||
                        displayName.contains("第") && displayName.contains("页") ||
                        displayName.equals(" ") || // 普通玻璃板
                        displayName.contains("当前未解锁该行") // 未解锁玻璃板
        );
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public BagData getBagData() {
        return bagData;
    }

    // 获取每页实际可用的存储格数
    public static int getItemsPerPage() {
        return ITEMS_PER_PAGE;
    }
}
