package com.horizonkk.chuwudai.utlis;

import java.util.Random;

public enum BundleStyle {
    BLUE("blue", "蓝色", 10007, "&9"),
    BROWN("brown", "棕色", 10008, "&6"),
    CYAN("cyan", "青色", 10010, "&3"),
    GRAY("gray", "灰色", 10011, "&8"),
    GREEN("green", "绿色", 10012, "&2"),
    LIGHT_BLUE("light_blue", "浅蓝色", 10013, "&b"),
    LIGHT_GRAY("light_gray", "浅灰色", 10014, "&7"),
    LIME("lime", "黄绿色", 10015, "&a"),
    MAGENTA("magenta", "品红色", 10016, "&d"),
    ORANGE("orange", "橙色", 10017, "&6"),
    PINK("pink", "粉色", 10018, "&d"),
    PURPLE("purple", "紫色", 10019, "&5"),
    RED("red", "红色", 10020, "&c"),
    WHITE("white", "白色", 10021, "&f"),
    YELLOW("yellow", "黄色", 10022, "&e");

    private final String styleName;
    private final String chineseName;
    private final int customModelData;
    private final String colorCode;

    BundleStyle(String styleName, String chineseName, int customModelData, String colorCode) {
        this.styleName = styleName;
        this.chineseName = chineseName;
        this.customModelData = customModelData;
        this.colorCode = colorCode;
    }

    public String getStyleName() {
        return styleName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getColorCode() {
        return colorCode;
    }

    /**
     * 获取转换后的颜色代码（已转换为 § 格式）
     * @return 转换后的颜色代码
     */
    public String getTranslatedColorCode() {
        return ColorUtils.translateColorCodes(colorCode);
    }

    // 根据样式名称获取枚举
    public static BundleStyle fromStyleName(String styleName) {
        for (BundleStyle style : values()) {
            if (style.getStyleName().equalsIgnoreCase(styleName)) {
                return style;
            }
        }
        return BROWN; // 默认返回棕色
    }

    // 根据CustomModelData获取枚举
    public static BundleStyle fromCustomModelData(int customModelData) {
        for (BundleStyle style : values()) {
            if (style.getCustomModelData() == customModelData) {
                return style;
            }
        }
        return BROWN; // 默认返回棕色
    }

    /**
     * 获取随机样式
     * @return 随机的BundleStyle枚举值
     */
    public static BundleStyle getRandomStyle() {
        BundleStyle[] styles = values();
        Random random = new Random();
        return styles[random.nextInt(styles.length)];
    }

    /**
     * 获取随机样式的名称
     * @return 随机样式的英文名称
     */
    public static String getRandomStyleName() {
        return getRandomStyle().getStyleName();
    }

    // 获取所有样式名称列表
    public static String[] getAllStyleNames() {
        BundleStyle[] styles = values();
        String[] names = new String[styles.length];
        for (int i = 0; i < styles.length; i++) {
            names[i] = styles[i].getStyleName();
        }
        return names;
    }

    // 获取所有中文名称列表
    public static String[] getAllChineseNames() {
        BundleStyle[] styles = values();
        String[] names = new String[styles.length];
        for (int i = 0; i < styles.length; i++) {
            names[i] = styles[i].getChineseName();
        }
        return names;
    }

    // 验证样式是否有效
    public static boolean isValidStyle(String styleName) {
        for (BundleStyle style : values()) {
            if (style.getStyleName().equalsIgnoreCase(styleName)) {
                return true;
            }
        }
        return false;
    }
}
