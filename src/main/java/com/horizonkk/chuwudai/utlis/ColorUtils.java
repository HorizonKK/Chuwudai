// src/main/java/com/horizonkk/chuwudai/utils/ColorUtils.java
package com.horizonkk.chuwudai.utlis;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Pattern;

public class ColorUtils {
    
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    
    /**
     * 将颜色代码转换为实际颜色
     * 支持 & 和 § 颜色代码以及十六进制颜色
     * 
     * @param text 需要转换的文本
     * @return 转换后的文本
     */
    public static String translateColorCodes(String text) {
        if (text == null) {
            return null;
        }
        // 替换十六进制颜色代码
        text = translateHexColors(text);
        // 替换标准颜色代码
        text = ChatColor.translateAlternateColorCodes('&', text);
        return text;
    }
    
    /**
     * 转换十六进制颜色代码
     * 
     * @param text 需要转换的文本
     * @return 转换后的文本
     */
    private static String translateHexColors(String text) {
        if (text == null) {
            return null;
        }
        
        // 查找并替换十六进制颜色代码
        java.util.regex.Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hex = matcher.group();
            String replacement = ChatColor.of(hex).toString();
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    /**
     * 移除所有颜色代码
     * 
     * @param text 需要处理的文本
     * @return 移除颜色后的文本
     */
    public static String removeColorCodes(String text) {
        if (text == null) {
            return null;
        }
        
        // 移除十六进制颜色
        text = HEX_PATTERN.matcher(text).replaceAll("");
        
        // 移除标准颜色代码
        text = ChatColor.stripColor(text);
        
        // 移除 & 和 § 颜色代码
        text = text.replaceAll("&[0-9a-fA-Fk-oK-OrR]", "");
        text = text.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
        
        return text;
    }
}
