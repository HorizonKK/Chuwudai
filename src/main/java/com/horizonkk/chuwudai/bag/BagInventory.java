package com.horizonkk.chuwudai.bag;

import com.horizonkk.chuwudai.Chuwudai;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BagInventory implements InventoryHolder {

    private final BagData bagData;
    private Inventory inventory;
    private int currentPage;
    private final Map<Integer, Integer> slotMapping = new HashMap<>();
    // 每页的实际存储格数（不包括控制栏）
    private static final int ITEMS_PER_PAGE = 45;
    // 控制栏起始位置
    private static final int CONTROL_BAR_START = 45;

    public BagInventory(BagData bagData) {
        this.bagData = bagData;
        this.currentPage = 0;
        createInventory();
    }

    private void createInventory() {
        int totalPages = bagData.getTotalPages();
        int actualSize = bagData.getSize();
        // 对于小容量储物袋，使用合适的界面大小
        int inventorySize;
        if (actualSize <= 27) {
            // 小型储物袋：3排存储 + 1排控制 = 4排 = 36格
            inventorySize = 36;
        } else if (actualSize <= 45) {
            // 中型储物袋：5排存储 + 1排控制 = 6排 = 54格
            inventorySize = 54;
        } else {
            // 大型储物袋：统一使用54格界面
            inventorySize = 54;
        }
        inventory = Bukkit.createInventory(this, inventorySize, "储物袋 #" + bagData.getId() + " (第 " + (currentPage + 1) + " 页)");
        loadItems();
        addControlButtons();
    }

    private void loadItems() {
        slotMapping.clear();
        int startSlot = currentPage * ITEMS_PER_PAGE;
        int endSlot = Math.min(startSlot + ITEMS_PER_PAGE, bagData.getSize());
        // 计算实际可用的存储格子数量
        int storageRows = (inventory.getSize() - 9) / 9; // 减去控制栏的1排
        int maxStorageSlots = storageRows * 9;
        for (int i = startSlot; i < endSlot && i < maxStorageSlots; i++) {
            int invSlot = i - startSlot;
            slotMapping.put(invSlot, i);

            ItemStack item = bagData.getItem(i);
            if (item != null) {
                inventory.setItem(invSlot, item);
            }
        }
    }
    private void addControlButtons() {
        int totalPages = bagData.getTotalPages();
        int inventorySize = inventory.getSize();
        int controlStart = inventorySize - 9; // 控制栏始终在最后1排

        // 清除控制栏区域
        for (int i = controlStart; i < inventorySize; i++) {
            inventory.setItem(i, null);
        }

        // 添加玻璃板分隔线（除了按钮位置）
        ItemStack glassPane = createGlassPane();
        for (int i = controlStart; i < inventorySize; i++) {
            // 排除按钮位置
            if (i != controlStart && i != controlStart + 2 && i != controlStart + 3 && i != inventorySize - 1) {
                inventory.setItem(i, glassPane);
            }
        }

        // 上一页按钮
        if (currentPage > 0) {
            ItemStack prevButton = createButton(
                    Material.ARROW,
                    Chuwudai.getInstance().getConfig().getString("ui.page-prev", "&c← 上一页"),
                    Arrays.asList("§7点击切换到上一页")
            );
            inventory.setItem(controlStart, prevButton);
        }

        // 页码显示
        ItemStack pageInfo = createButton(
                Material.PAPER,
                Chuwudai.getInstance().getConfig().getString("ui.current-page", "&7第 %page% 页 / %total% 页")
                        .replace("%page%", String.valueOf(currentPage + 1))
                        .replace("%total%", String.valueOf(totalPages)),
                null
        );
        inventory.setItem(controlStart + 2, pageInfo);

        // 关闭按钮
        ItemStack closeButton = createButton(
                Material.BARRIER,
                Chuwudai.getInstance().getConfig().getString("ui.close-button", "&c关闭"),
                Arrays.asList("§7点击关闭储物袋")
        );
        inventory.setItem(controlStart + 3, closeButton);

        // 下一页按钮
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = createButton(
                    Material.ARROW,
                    Chuwudai.getInstance().getConfig().getString("ui.page-next", "&a下一页 →"),
                    Arrays.asList("§7点击切换到下一页")
            );
            inventory.setItem(inventorySize - 1, nextButton);
        }
    }

    private ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        return glass;
    }

    private ItemStack createButton(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name.replace("&", "§"));

        if (lore != null) {
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public boolean isControlSlot(int slot) {
        int inventorySize = inventory.getSize();
        int controlStart = inventorySize - 9;
        // 控制栏范围：最后9个格子
        return slot >= controlStart && slot < inventorySize;
    }

    public void handleControlClick(int slot) {
        if (slot == 45 && currentPage > 0) {
            // 上一页
            currentPage--;
            refresh();
        } else if (slot == 53 && currentPage < bagData.getTotalPages() - 1) {
            // 下一页
            currentPage++;
            refresh();
        } else if (slot == 48) {
            // 关闭
            Player viewer = (Player) inventory.getViewers().get(0);
            viewer.closeInventory();
        }
    }

    public void refresh() {
        inventory.clear();
        loadItems();
        addControlButtons();

        // 更新标题显示当前页码
        String title = "储物袋 #" + bagData.getId() + " (第 " + (currentPage + 1) + " 页)";
        if (!inventory.getViewers().isEmpty()) {
            Player viewer = (Player) inventory.getViewers().get(0);
            viewer.closeInventory();
            open(viewer);
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
                        displayName.equals(" ") // 玻璃板
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
