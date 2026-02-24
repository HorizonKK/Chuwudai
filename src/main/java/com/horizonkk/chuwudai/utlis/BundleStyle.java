package com.horizonkk.chuwudai.utlis;

public enum BundleStyle {
    BLUE("blue", "蓝色", 10038, "&9"),
    BROWN("brown", "棕色", 10039, "&6"),
    CYAN("cyan", "青色", 10041, "&3"),
    GRAY("gray", "灰色", 10042, "&8"),
    GREEN("green", "绿色", 10043, "&2"),
    LIGHT_BLUE("light_blue", "浅蓝色", 10044, "&b"),
    LIGHT_GRAY("light_gray", "浅灰色", 10045, "&7"),
    LIME("lime", "黄绿色", 10046, "&a"),
    MAGENTA("magenta", "品红色", 10047, "&d"),
    ORANGE("orange", "橙色", 10048, "&6"),
    PINK("pink", "粉色", 10049, "&d"),
    PURPLE("purple", "紫色", 10050, "&5"),
    RED("red", "红色", 10051, "&c"),
    WHITE("white", "白色", 10052, "&f"),
    YELLOW("yellow", "黄色", 10038, "&e");

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
