package com.horizonkk.chuwudai.storage;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Moves serialized YAML maps without creating or touching Bukkit ItemStacks off-thread. */
final class SqliteMigrationPayload {
    private static final int LIMIT = 16 * 1024 * 1024;
    private final Yaml yaml;

    SqliteMigrationPayload() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(LIMIT);
        yaml = new Yaml(new SafeConstructor(options));
    }

    Map<Integer, Object> read(String payload, int size, Integer page, boolean compressed) {
        if(payload == null) throw invalid("NULL 物品数据");
        if(payload.isEmpty()) return new TreeMap<>();
        String raw = payload;
        if(compressed) {
            try { raw = PayloadCodec.decompress(payload); }
            catch(StorageAccessException e) {
                // Old SQLite writers fall back to uncompressed Base64 YAML on compression failure.
                raw = payload;
            }
        }
        if(raw.length() > (LIMIT * 4L / 3) + 4) throw invalid("物品数据过大");
        try {
            byte[] bytes = Base64.getDecoder().decode(raw);
            if(bytes.length > LIMIT) throw invalid("物品 YAML 超过 16 MiB");
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            Object document = yaml.load(text);
            if(!(document instanceof Map<?, ?> root)) throw invalid("物品 YAML 顶层不是映射");
            if(root.isEmpty()) return new TreeMap<>();
            if(root.size()!=1 || !(root.get("items") instanceof Map<?, ?> values)) throw invalid("无效的 items 节点");
            Map<Integer, Object> items = new TreeMap<>();
            for(var entry : values.entrySet()) {
                int slot = Integer.parseInt(String.valueOf(entry.getKey()));
                if(slot<0 || slot>=size || (page!=null && slot/45!=page)) throw invalid("物品槽位越界或分页不匹配: " + slot);
                if(!(entry.getValue() instanceof Map<?, ?> item) || !"org.bukkit.inventory.ItemStack".equals(item.get("=="))) {
                    throw invalid("槽位 " + slot + " 的物品序列化格式无效");
                }
                if(items.putIfAbsent(slot, entry.getValue())!=null) throw invalid("重复物品槽位: " + slot);
            }
            return items;
        } catch(CharacterCodingException | IllegalArgumentException e) {
            throw new StorageAccessException(StorageAccessException.Reason.CORRUPT, "SQLite 物品编码无效", e);
        }
    }

    Map<Integer, String> encode(MySqlRepository.Kind kind, Map<Integer, Object> items) {
        Map<Integer, Map<String, Object>> pages = new TreeMap<>();
        for(var entry : items.entrySet()) {
            int page = kind==MySqlRepository.Kind.BAG ? entry.getKey()/45 : 0;
            pages.computeIfAbsent(page, ignored -> new LinkedHashMap<>()).put(String.valueOf(entry.getKey()), entry.getValue());
        }
        Map<Integer, String> result = new TreeMap<>();
        for(var page : pages.entrySet()) {
            String text = yaml.dump(Map.of("items", page.getValue()));
            String base64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            // The runtime decompressor limits the decompressed Base64 string, not the YAML bytes.
            if(base64.length() > LIMIT) throw invalid("迁移后的单页数据超过运行时 16 MiB 限制");
            result.put(page.getKey(), PayloadCodec.compress(base64));
        }
        return result;
    }

    private StorageAccessException invalid(String message) {
        return new StorageAccessException(StorageAccessException.Reason.CORRUPT, message);
    }
}
