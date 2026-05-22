package com.horizonkk.chuwudai.pill;

import com.horizonkk.chuwudai.pill.handlers.*;

import java.util.HashMap;
import java.util.Map;

public enum PillEffectType {

    // ===== 基础属性类 =====
    FOOD("dan_yao_food", "饱食度", new SaturationHandler()),
    HEALTH("dan_yao_health", "生命值", new HealthHandler()),
    MANA("dan_yao_mana", "法力值", new ManaHandler()),
    LING_QI("dan_yao_lingqi", "灵气上限", new LingQiHandler()),
    ZHEN_YUAN("dan_yao_zhenyuan", "真元", new ZhenYuanHandler()),
    SHOU_YUAN("dan_yao_shouyuan", "寿元", new ShouYuanHandler()),

    // ===== 恢复类（持续恢复）=====
    HU_FU("dan_yao_huifu", "生命恢复", new RegenHealthHandler()),
    HUI_FA("dan_yao_huifa", "法力恢复", new RegenManaHandler()),

    // ===== 护盾类 =====
    HU_ZHAO("dan_yao_huzhao", "护罩", new ShieldHandler()),

    // ===== 毒性类 =====
    DAN_DU("dan_yao_dandu", "丹毒", new DanduHandler()),
    JIAN_DAN_DU("dan_yao_jiandandu", "减丹毒", new ReducePoisonHandler()),

    // ===== 临时增益类 =====
    LIN_SHI_DAMAGE("dan_yao_linshi_damage", "临时物伤", new TempDamageHandler()),
    LIN_SHI_WU_KANG("dan_yao_linshi_wukang", "临时物抗", new TempPhysicalDefenseHandler()),
    LIN_SHI_FA_KANG("dan_yao_linshi_fakang", "临时法抗", new TempMagicDefenseHandler()),
    LIN_SHI_WU_XING_KANG("dan_yao_linshi_wuxingkang", "临时五行抗性", new TempElementalDefenseHandler());

    private final String nbtKey;
    private final String displayName;
    private final PillEffectHandler handler;

    private static final Map<String, PillEffectType> KEY_MAP = new HashMap<>();

    static {
        for (PillEffectType type : values()) {
            KEY_MAP.put(type.nbtKey, type);
        }
    }

    PillEffectType(String nbtKey, String displayName, PillEffectHandler handler) {
        this.nbtKey = nbtKey;
        this.displayName = displayName;
        this.handler = handler;
    }

    public String getNbtKey() {
        return nbtKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PillEffectHandler getHandler() {
        return handler;
    }

    /**
     * 根据NBT键查找效果类型
     */
    public static PillEffectType getByKey(String key) {
        return KEY_MAP.get(key);
    }
}
