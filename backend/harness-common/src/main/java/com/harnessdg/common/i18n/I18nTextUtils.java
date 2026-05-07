/**
 * 功能：I18nText JSON 辅助工具，处理多语言文本字段
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public final class I18nTextUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_LOCALE = "zh_CN";
    private static final String FALLBACK_LOCALE = "en_US";

    private I18nTextUtils() {}

    /**
     * 从 I18nText JSON 中提取指定语言的文本
     */
    public static String resolve(String i18nJson, String locale) {
        if (i18nJson == null || i18nJson.isBlank()) {
            return "";
        }

        try {
            Map<String, String> textMap = MAPPER.readValue(i18nJson, new TypeReference<>() {});
            return resolveFromMap(textMap, locale);
        } catch (Exception e) {
            log.warn("Failed to parse I18nText JSON: {}", i18nJson, e);
            return i18nJson;
        }
    }

    /**
     * 从 Map 中提取指定语言的文本（适用于 JSONB 已反序列化为 Map 的场景）
     */
    public static String resolve(Map<String, String> textMap, String locale) {
        if (textMap == null || textMap.isEmpty()) {
            return "";
        }
        return resolveFromMap(textMap, locale);
    }

    private static String resolveFromMap(Map<String, String> textMap, String locale) {
        String value = textMap.get(locale);
        if (value != null) {
            return value;
        }
        value = textMap.get(DEFAULT_LOCALE);
        if (value != null) {
            return value;
        }
        return textMap.getOrDefault(FALLBACK_LOCALE, "");
    }

    /**
     * 构建 I18nText JSON
     */
    public static String build(String zhCN, String enUS) {
        try {
            Map<String, String> textMap = Map.of("zh_CN", zhCN, "en_US", enUS);
            return MAPPER.writeValueAsString(textMap);
        } catch (Exception e) {
            log.warn("Failed to build I18nText JSON", e);
            return "{\"zh_CN\":\"" + zhCN + "\",\"en_US\":\"" + enUS + "\"}";
        }
    }
}
