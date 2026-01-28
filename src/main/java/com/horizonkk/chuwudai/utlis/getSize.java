package com.horizonkk.chuwudai.utlis;

public class getSize {
    // 添加储物袋大小名称获取方法
    public String getBagSizeName(int size) {
        switch (size) {
            case 27: return "小型";
            case 54: return "中型";
            case 81: return "大型";
            case 108: return "巨型";
            default: return "";
        }
    }

    // 添加丹瓶大小名称获取方法
    public String getPillSizeName(int size) {
        switch (size) {
            case 9: return "小型";
            case 18: return "中型";
            case 27: return "大型";
            default: return "";
        }
    }
}
