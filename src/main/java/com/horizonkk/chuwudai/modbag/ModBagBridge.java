package com.horizonkk.chuwudai.modbag;

import com.horizonkk.chuwudai.Chuwudai;
import com.horizonkk.chuwudai.bag.BagData;
import com.horizonkk.chuwudai.bag.BagInventory;
import com.horizonkk.chuwudai.bag.BagItem;
import com.horizonkk.chuwudai.utlis.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ModBagBridge implements Listener, PluginMessageListener {
    private static final String HELLO_CHANNEL = "xiuxiankeypress:bag_hello";
    private static final String OPEN_CHANNEL = "xiuxiankeypress:bag_open";
    private static final String UPDATE_CHANNEL = "xiuxiankeypress:bag_update";
    private static final String CLICK_CHANNEL = "xiuxiankeypress:bag_click";
    private static final String DETAIL_REQUEST_CHANNEL = "xiuxiankeypress:bag_detail_page";
    private static final String DETAIL_CHANNEL = "xiuxiankeypress:bag_detail";
    private static final String PAGE_CHANNEL = "xiuxiankeypress:bag_page";
    private static final String CLOSE_CHANNEL = "xiuxiankeypress:bag_close";
    private static final int PROTOCOL_VERSION = 1;
    private static final int BAG_SLOTS_PER_PAGE = BagInventory.getItemsPerPage();
    private static final int VISIBLE_PAGES = 6;
    private static final int AREA_BAG = 0;
    private static final int AREA_PLAYER = 1;
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_PREVIEW_LORE_LINES = 0;
    private static final int MAX_DETAIL_LORE_LINES = 64;
    private static final int MAX_PREVIEW_TEXT_LENGTH = 16;
    private static final int MAX_DETAIL_TEXT_LENGTH = 128;
    private static final long SESSION_TIMEOUT_MILLIS = 120_000L;

    private final Chuwudai plugin;
    private final Set<UUID> modPlayers = new HashSet<>();
    private final Map<UUID, BagSession> sessions = new HashMap<>();
    private BukkitTask cleanupTask;

    public ModBagBridge(Chuwudai plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CLICK_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, DETAIL_REQUEST_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, PAGE_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CLOSE_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, OPEN_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, UPDATE_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, DETAIL_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CLOSE_CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::closeExpiredSessions, 20L * 30L, 20L * 30L);
    }

    public boolean openBag(Player player, String bagId) {
        if (!isModPlayer(player) || bagId == null || bagId.isBlank()) {
            return false;
        }

        BagData bagData = getOrCreateBagData(bagId);
        closeExistingSession(player, true);
        player.closeInventory();

        BagSession session = new BagSession(bagId, BagOpenMode.MOD);
        sessions.put(player.getUniqueId(), session);
        sendSnapshot(player, session, bagData, OPEN_CHANNEL);
        sendBagOpenedMessage(player, bagId);
        return true;
    }

    public void beginVanillaSession(Player player, String bagId) {
        if (player == null || bagId == null || bagId.isBlank()) {
            return;
        }
        closeExistingSession(player, true);
        sessions.put(player.getUniqueId(), new BagSession(bagId, BagOpenMode.VANILLA));
    }

    public boolean isVanillaSession(Player player, String bagId) {
        BagSession session = sessions.get(player.getUniqueId());
        return session != null
                && session.mode == BagOpenMode.VANILLA
                && session.bagId.equals(bagId)
                && playerStillOwnsBag(player, bagId);
    }

    public void endVanillaSession(Player player, String bagId) {
        BagSession session = sessions.get(player.getUniqueId());
        if (session != null && session.mode == BagOpenMode.VANILLA && session.bagId.equals(bagId)) {
            sessions.remove(player.getUniqueId());
        }
    }

    public boolean isBagOpen(String bagId) {
        for (BagSession session : sessions.values()) {
            if (session.bagId.equals(bagId)) {
                return true;
            }
        }
        return false;
    }

    public void closeAll() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        List<UUID> viewers = new ArrayList<>(sessions.keySet());
        for (UUID viewer : viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                closeExistingSession(player, true);
            }
        }
        sessions.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> onPluginMessageReceived(channel, player, message));
            return;
        }

        try {
            switch (channel) {
                case HELLO_CHANNEL -> handleHello(player, message);
                case CLICK_CHANNEL -> handleClick(player, message);
                case DETAIL_REQUEST_CHANNEL -> handleDetailPage(player, message);
                case PAGE_CHANNEL -> handlePage(player, message);
                case CLOSE_CHANNEL -> handleClose(player, message);
                default -> {
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Rejected malformed mod bag packet from " + player.getName() + ": " + ex.getMessage());
            BagSession session = sessions.get(player.getUniqueId());
            if (session != null && session.mode == BagOpenMode.MOD) {
                sendSnapshot(player, session, getOrCreateBagData(session.bagId), UPDATE_CHANNEL);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        closeExistingSession(event.getPlayer(), false);
        modPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof BagInventory bagInventory)) {
            return;
        }
        endVanillaSession(player, bagInventory.getBagData().getId());
    }

    private void handleHello(Player player, byte[] message) {
        ModBagByteBuf buf = ModBagByteBuf.reader(message);
        int protocol = buf.readVarInt();
        boolean modPagesEnabled = !buf.canRead() || buf.readBoolean();
        if (protocol == PROTOCOL_VERSION && modPagesEnabled) {
            modPlayers.add(player.getUniqueId());
        } else {
            modPlayers.remove(player.getUniqueId());
            BagSession session = sessions.get(player.getUniqueId());
            if (session != null && session.mode == BagOpenMode.MOD) {
                closeExistingSession(player, true);
            }
        }
    }

    private void handleClick(Player player, byte[] message) {
        ModBagByteBuf buf = ModBagByteBuf.reader(message);
        String bagId = buf.readString(MAX_ID_LENGTH);
        int revision = buf.readVarInt();
        int area = buf.readVarInt();
        int slot = buf.readVarInt();
        int button = buf.readVarInt();
        boolean shiftDown = buf.readBoolean();

        BagSession session = requireModSession(player, bagId);
        if (session == null) {
            return;
        }
        BagData bagData = getOrCreateBagData(bagId);
        if (!validateRevision(player, session, bagData, revision)) {
            return;
        }
        if (!isValidSlot(area, slot, bagData)) {
            sendSnapshot(player, session, bagData, UPDATE_CHANNEL);
            return;
        }
        if (area == AREA_BAG && !isSelectedDetailSlot(session, slot, bagData)) {
            sendSnapshot(player, session, bagData, UPDATE_CHANNEL);
            return;
        }

        boolean changed = shiftDown
                ? handleShiftClick(player, session, bagData, area, slot)
                : handleNormalClick(player, session, bagData, area, slot, button);
        if (changed) {
            session.revision++;
            plugin.getBagManager().queueBagSave(bagData.getId());
            updateBagLore(player, bagData);
            player.updateInventory();
        }
        sendSnapshot(player, session, bagData, UPDATE_CHANNEL);
    }

    private void handlePage(Player player, byte[] message) {
        ModBagByteBuf buf = ModBagByteBuf.reader(message);
        String bagId = buf.readString(MAX_ID_LENGTH);
        int revision = buf.readVarInt();
        int requestedBigPage = buf.readVarInt();

        BagSession session = requireModSession(player, bagId);
        if (session == null) {
            return;
        }
        BagData bagData = getOrCreateBagData(bagId);
        if (!validateRevision(player, session, bagData, revision)) {
            return;
        }
        int maxBigPage = Math.max(0, (bagData.getTotalPages() + VISIBLE_PAGES - 1) / VISIBLE_PAGES - 1);
        session.bigPage = Math.max(0, Math.min(requestedBigPage, maxBigPage));
        if (!isVisiblePage(session, session.detailPage, bagData)) {
            session.detailPage = -1;
        }
        sendSnapshot(player, session, bagData, UPDATE_CHANNEL);
    }

    private void handleDetailPage(Player player, byte[] message) {
        ModBagByteBuf buf = ModBagByteBuf.reader(message);
        String bagId = buf.readString(MAX_ID_LENGTH);
        int revision = buf.readVarInt();
        int requestedPage = buf.readVarInt();

        BagSession session = requireModSession(player, bagId);
        if (session == null) {
            return;
        }
        BagData bagData = getOrCreateBagData(bagId);
        if (!validateRevision(player, session, bagData, revision)) {
            return;
        }
        if (!isVisiblePage(session, requestedPage, bagData)) {
            sendSnapshot(player, session, bagData, UPDATE_CHANNEL);
            return;
        }
        session.detailPage = requestedPage;
        sendDetailPage(player, session, bagData, requestedPage);
    }

    private void handleClose(Player player, byte[] message) {
        ModBagByteBuf buf = ModBagByteBuf.reader(message);
        String bagId = buf.readString(MAX_ID_LENGTH);
        BagSession session = sessions.get(player.getUniqueId());
        if (session != null && session.mode == BagOpenMode.MOD && session.bagId.equals(bagId)) {
            closeExistingSession(player, false);
        }
    }

    private BagSession requireModSession(Player player, String bagId) {
        BagSession session = sessions.get(player.getUniqueId());
        if (session == null || session.mode != BagOpenMode.MOD || !session.bagId.equals(bagId)) {
            return null;
        }
        if (!playerStillOwnsBag(player, bagId)) {
            forceCloseModSession(player, session);
            return null;
        }
        session.touch();
        return session;
    }

    private boolean validateRevision(Player player, BagSession session, BagData bagData, int revision) {
        if (revision != session.revision) {
            sendSnapshot(player, session, bagData, UPDATE_CHANNEL);
            return false;
        }
        return true;
    }

    private boolean handleNormalClick(Player player, BagSession session, BagData bagData, int area, int slot, int button) {
        if (isBagItemInProtectedSlot(player, bagData, area, slot)) {
            return false;
        }

        ItemStack slotItem = getSlotItem(player, bagData, area, slot);
        ItemStack cursor = cloneOrNull(session.cursorItem);
        boolean targetIsBag = area == AREA_BAG;

        if (button == 1) {
            return handleRightClick(player, session, bagData, area, slot, cursor, slotItem, targetIsBag);
        }
        return handleLeftClick(player, session, bagData, area, slot, cursor, slotItem, targetIsBag);
    }

    private boolean handleLeftClick(Player player, BagSession session, BagData bagData, int area, int slot,
                                    ItemStack cursor, ItemStack slotItem, boolean targetIsBag) {
        if (isEmpty(cursor)) {
            session.cursorItem = cloneOrNull(slotItem);
            setSlotItem(player, bagData, area, slot, null);
            return !isEmpty(slotItem);
        }

        if (targetIsBag && BagItem.isBagItem(cursor)) {
            return false;
        }

        if (isEmpty(slotItem)) {
            setSlotItem(player, bagData, area, slot, cursor);
            session.cursorItem = null;
            return true;
        }

        if (canStack(cursor, slotItem)) {
            int moved = moveIntoExistingStack(cursor, slotItem);
            if (moved <= 0) {
                return false;
            }
            setSlotItem(player, bagData, area, slot, slotItem);
            session.cursorItem = isEmpty(cursor) ? null : cursor;
            return true;
        }

        setSlotItem(player, bagData, area, slot, cursor);
        session.cursorItem = slotItem;
        return true;
    }

    private boolean handleRightClick(Player player, BagSession session, BagData bagData, int area, int slot,
                                     ItemStack cursor, ItemStack slotItem, boolean targetIsBag) {
        if (isEmpty(cursor)) {
            if (isEmpty(slotItem)) {
                return false;
            }
            int take = (slotItem.getAmount() + 1) / 2;
            ItemStack taken = slotItem.clone();
            taken.setAmount(take);
            slotItem.setAmount(slotItem.getAmount() - take);
            session.cursorItem = taken;
            setSlotItem(player, bagData, area, slot, slotItem);
            return true;
        }

        if (targetIsBag && BagItem.isBagItem(cursor)) {
            return false;
        }

        if (isEmpty(slotItem)) {
            ItemStack placed = cursor.clone();
            placed.setAmount(1);
            setSlotItem(player, bagData, area, slot, placed);
            cursor.setAmount(cursor.getAmount() - 1);
            session.cursorItem = isEmpty(cursor) ? null : cursor;
            return true;
        }

        if (canStack(cursor, slotItem) && slotItem.getAmount() < slotItem.getMaxStackSize()) {
            slotItem.setAmount(slotItem.getAmount() + 1);
            cursor.setAmount(cursor.getAmount() - 1);
            setSlotItem(player, bagData, area, slot, slotItem);
            session.cursorItem = isEmpty(cursor) ? null : cursor;
            return true;
        }

        setSlotItem(player, bagData, area, slot, cursor);
        session.cursorItem = slotItem;
        return true;
    }

    private boolean handleShiftClick(Player player, BagSession session, BagData bagData, int area, int slot) {
        if (!isEmpty(session.cursorItem) || isBagItemInProtectedSlot(player, bagData, area, slot)) {
            return false;
        }

        ItemStack source = getSlotItem(player, bagData, area, slot);
        if (isEmpty(source)) {
            return false;
        }

        if (area == AREA_BAG) {
            ItemStack remaining = addToPlayerInventory(player, source);
            setSlotItem(player, bagData, area, slot, remaining);
            return remaining == null || remaining.getAmount() != source.getAmount();
        }

        ItemStack remaining = addToBag(bagData, source);
        setSlotItem(player, bagData, area, slot, remaining);
        return remaining == null || remaining.getAmount() != source.getAmount();
    }

    private ItemStack addToPlayerInventory(Player player, ItemStack source) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(source.clone());
        if (leftovers.isEmpty()) {
            return null;
        }
        int remainingAmount = 0;
        ItemStack remaining = null;
        for (ItemStack item : leftovers.values()) {
            if (!isEmpty(item)) {
                remaining = item.clone();
                remainingAmount += item.getAmount();
            }
        }
        if (remaining == null || remainingAmount <= 0) {
            return null;
        }
        remaining.setAmount(remainingAmount);
        return remaining;
    }

    private ItemStack addToBag(BagData bagData, ItemStack source) {
        if (BagItem.isBagItem(source)) {
            return source.clone();
        }

        ItemStack remaining = source.clone();
        for (int slot = 0; slot < bagData.getSize() && !isEmpty(remaining); slot++) {
            ItemStack existing = bagData.getItem(slot);
            if (canStack(remaining, existing)) {
                moveIntoExistingStack(remaining, existing);
                setBagSlot(bagData, slot, existing);
            }
        }

        for (int slot = 0; slot < bagData.getSize() && !isEmpty(remaining); slot++) {
            ItemStack existing = bagData.getItem(slot);
            if (!isEmpty(existing)) {
                continue;
            }
            int amount = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(amount);
            setBagSlot(bagData, slot, placed);
            remaining.setAmount(remaining.getAmount() - amount);
        }
        return isEmpty(remaining) ? null : remaining;
    }

    private int moveIntoExistingStack(ItemStack source, ItemStack target) {
        if (!canStack(source, target)) {
            return 0;
        }
        int room = target.getMaxStackSize() - target.getAmount();
        int moved = Math.min(room, source.getAmount());
        target.setAmount(target.getAmount() + moved);
        source.setAmount(source.getAmount() - moved);
        return moved;
    }

    private boolean canStack(ItemStack source, ItemStack target) {
        return !isEmpty(source) && !isEmpty(target) && source.isSimilar(target)
                && target.getAmount() < target.getMaxStackSize();
    }

    private boolean isBagItemInProtectedSlot(Player player, BagData bagData, int area, int slot) {
        ItemStack item = getSlotItem(player, bagData, area, slot);
        return !isEmpty(item) && BagItem.isBagItem(item);
    }

    private ItemStack getSlotItem(Player player, BagData bagData, int area, int slot) {
        if (area == AREA_BAG) {
            return cloneOrNull(bagData.getItem(slot));
        }
        return cloneOrNull(player.getInventory().getItem(slot));
    }

    private void setSlotItem(Player player, BagData bagData, int area, int slot, ItemStack item) {
        if (area == AREA_BAG) {
            setBagSlot(bagData, slot, item);
            return;
        }
        player.getInventory().setItem(slot, normalize(item));
    }

    private void setBagSlot(BagData bagData, int slot, ItemStack item) {
        ItemStack normalized = normalize(item);
        if (isEmpty(normalized)) {
            bagData.removeItem(slot);
        } else {
            bagData.setItem(slot, normalized);
        }
    }

    private boolean isValidSlot(int area, int slot, BagData bagData) {
        if (area == AREA_BAG) {
            return slot >= 0 && slot < bagData.getSize();
        }
        return area == AREA_PLAYER && slot >= 0 && slot < 36;
    }

    private boolean isVisiblePage(BagSession session, int page, BagData bagData) {
        if (page < 0 || page >= bagData.getTotalPages()) {
            return false;
        }
        int firstVisiblePage = session.bigPage * VISIBLE_PAGES;
        int lastVisiblePage = Math.min(bagData.getTotalPages(), firstVisiblePage + VISIBLE_PAGES);
        return page >= firstVisiblePage && page < lastVisiblePage;
    }

    private boolean isSelectedDetailSlot(BagSession session, int slot, BagData bagData) {
        if (slot < 0 || slot >= bagData.getSize()) {
            return false;
        }
        int page = slot / BAG_SLOTS_PER_PAGE;
        return page == session.detailPage && isVisiblePage(session, page, bagData);
    }

    private void sendSnapshot(Player player, BagSession session, BagData bagData, String channel) {
        ModBagByteBuf buf = ModBagByteBuf.writer();
        buf.writeString(session.bagId);
        buf.writeVarInt(session.revision);
        buf.writeVarInt(session.bigPage);
        buf.writeVarInt(bagData.getSize());
        buf.writeVarInt(bagData.getTotalPages());
        writeItem(buf, session.cursorItem);
        writeBagItems(buf, session, bagData);
        writePlayerItems(buf, player);
        player.sendPluginMessage(plugin, channel, buf.toByteArray());
    }

    private void sendDetailPage(Player player, BagSession session, BagData bagData, int page) {
        ModBagByteBuf buf = ModBagByteBuf.writer();
        buf.writeString(session.bagId);
        buf.writeVarInt(session.revision);
        buf.writeVarInt(page);

        int startSlot = page * BAG_SLOTS_PER_PAGE;
        int endSlot = Math.min(bagData.getSize(), startSlot + BAG_SLOTS_PER_PAGE);
        List<Integer> slots = new ArrayList<>();
        for (int slot = startSlot; slot < endSlot; slot++) {
            if (!isEmpty(bagData.getItem(slot))) {
                slots.add(slot);
            }
        }

        buf.writeVarInt(slots.size());
        for (int slot : slots) {
            buf.writeVarInt(slot);
            writeDetailedItem(buf, bagData.getItem(slot));
        }
        player.sendPluginMessage(plugin, DETAIL_CHANNEL, buf.toByteArray());
    }

    private void writeBagItems(ModBagByteBuf buf, BagSession session, BagData bagData) {
        int startPage = session.bigPage * VISIBLE_PAGES;
        int startSlot = startPage * BAG_SLOTS_PER_PAGE;
        int endSlot = Math.min(bagData.getSize(), startSlot + VISIBLE_PAGES * BAG_SLOTS_PER_PAGE);
        List<Integer> slots = new ArrayList<>();
        for (int slot = startSlot; slot < endSlot; slot++) {
            if (!isEmpty(bagData.getItem(slot))) {
                slots.add(slot);
            }
        }
        buf.writeVarInt(slots.size());
        for (int slot : slots) {
            buf.writeVarInt(slot);
            writeItem(buf, bagData.getItem(slot));
        }
    }

    private void writePlayerItems(ModBagByteBuf buf, Player player) {
        PlayerInventory inventory = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            if (!isEmpty(inventory.getItem(slot))) {
                slots.add(slot);
            }
        }
        buf.writeVarInt(slots.size());
        for (int slot : slots) {
            buf.writeVarInt(slot);
            writeItem(buf, inventory.getItem(slot));
        }
    }

    private void writeItem(ModBagByteBuf buf, ItemStack item) {
        writeItem(buf, item, MAX_PREVIEW_LORE_LINES, MAX_PREVIEW_TEXT_LENGTH);
    }

    private void writeDetailedItem(ModBagByteBuf buf, ItemStack item) {
        writeItem(buf, item, MAX_DETAIL_LORE_LINES, MAX_DETAIL_TEXT_LENGTH);
    }

    private void writeItem(ModBagByteBuf buf, ItemStack item, int maxLoreLines, int maxTextLength) {
        if (isEmpty(item)) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeString(item.getType().getKey().toString());
        buf.writeVarInt(item.getAmount());
        ItemMeta meta = item.getItemMeta();
        int customModelData = meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
        buf.writeVarInt(customModelData);
        buf.writeString(limitText(meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "", maxTextLength));
        List<String> lore = meta != null && meta.hasLore() ? meta.getLore() : List.of();
        int loreCount = Math.min(lore == null ? 0 : lore.size(), maxLoreLines);
        buf.writeVarInt(loreCount);
        for (int i = 0; i < loreCount; i++) {
            buf.writeString(limitText(lore.get(i), maxTextLength));
        }
    }

    private void forceCloseModSession(Player player, BagSession session) {
        closeExistingSession(player, true);
    }

    private void closeExistingSession(Player player, boolean notifyClient) {
        BagSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (session.mode == BagOpenMode.MOD) {
            returnCursorItem(player, session);
            BagData bagData = getOrCreateBagData(session.bagId);
            plugin.getBagManager().flushDirtyBagData(bagData.getId());
            updateBagLore(player, bagData);
            if (notifyClient) {
                ModBagByteBuf buf = ModBagByteBuf.writer();
                buf.writeString(session.bagId);
                player.sendPluginMessage(plugin, CLOSE_CHANNEL, buf.toByteArray());
            }
        }
    }

    private void returnCursorItem(Player player, BagSession session) {
        if (isEmpty(session.cursorItem)) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(session.cursorItem.clone());
        for (ItemStack item : leftover.values()) {
            if (!isEmpty(item)) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
        session.cursorItem = null;
        player.updateInventory();
    }

    private void closeExpiredSessions() {
        long now = System.currentTimeMillis();
        for (UUID playerId : new ArrayList<>(sessions.keySet())) {
            BagSession session = sessions.get(playerId);
            if (session == null || session.mode != BagOpenMode.MOD || !session.isExpired(now)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                closeExistingSession(player, true);
            } else {
                sessions.remove(playerId);
            }
        }
    }

    private boolean playerStillOwnsBag(Player player, String bagId) {
        return findBagItem(player, bagId) != null;
    }

    private ItemStack findBagItem(Player player, String bagId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!isEmpty(item) && BagItem.isBagItem(item) && bagId.equals(BagItem.getBagId(item))) {
                return item;
            }
        }
        return null;
    }

    private void updateBagLore(Player player, BagData bagData) {
        ItemStack bagItem = findBagItem(player, bagData.getId());
        if (bagItem != null) {
            BagItem.updateBagLore(bagItem, bagData.getStoneCounts(), bagData);
        }
    }

    private BagData getOrCreateBagData(String bagId) {
        BagData bagData = plugin.getBagManager().getBagData(bagId);
        if (bagData != null) {
            return bagData;
        }
        BagData created = new BagData(bagId, 27);
        plugin.getDataManager().saveBag(created);
        return created;
    }

    private boolean isModPlayer(Player player) {
        return player != null && modPlayers.contains(player.getUniqueId());
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private ItemStack normalize(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
    private void sendBagOpenedMessage(Player player, String bagId) {
        player.sendMessage(ColorUtils.translateColorCodes(plugin.getConfig().getString("messages.prefix", "&8[&6Storage&8] &f")
                + plugin.getConfig().getString("messages.bag-opened", "&aOpened bag %id%").replace("%id%", bagId)));
    }

    private enum BagOpenMode {
        VANILLA,
        MOD
    }

    private static final class BagSession {
        private final String bagId;
        private final BagOpenMode mode;
        private ItemStack cursorItem;
        private int revision;
        private int bigPage;
        private int detailPage = -1;
        private long lastActionTime = System.currentTimeMillis();

        private BagSession(String bagId, BagOpenMode mode) {
            this.bagId = bagId;
            this.mode = mode;
        }

        private void touch() {
            this.lastActionTime = System.currentTimeMillis();
        }

        private boolean isExpired(long now) {
            return now - lastActionTime > SESSION_TIMEOUT_MILLIS;
        }
    }
}


