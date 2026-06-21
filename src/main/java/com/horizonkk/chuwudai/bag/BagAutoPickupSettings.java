package com.horizonkk.chuwudai.bag;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BagAutoPickupSettings {

    private static final ConcurrentMap<UUID, Settings> PLAYER_SETTINGS = new ConcurrentHashMap<>();

    private BagAutoPickupSettings() {
    }

    public static Settings get(UUID playerId) {
        return PLAYER_SETTINGS.computeIfAbsent(playerId, id -> new Settings());
    }

    public static boolean toggleVanillaItems(UUID playerId) {
        Settings settings = get(playerId);
        settings.vanillaItems = !settings.vanillaItems;
        return settings.vanillaItems;
    }

    public static boolean toggleOreItems(UUID playerId) {
        Settings settings = get(playerId);
        settings.oreItems = !settings.oreItems;
        return settings.oreItems;
    }

    public static boolean toggleSpiritStones(UUID playerId) {
        Settings settings = get(playerId);
        settings.spiritStones = !settings.spiritStones;
        return settings.spiritStones;
    }

    public static final class Settings {
        private boolean vanillaItems = false;
        private boolean oreItems = true;
        private boolean spiritStones = true;

        private Settings() {
        }

        public boolean isVanillaItems() {
            return vanillaItems;
        }

        public boolean isOreItems() {
            return oreItems;
        }

        public boolean isSpiritStones() {
            return spiritStones;
        }
    }
}
