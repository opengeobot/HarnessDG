/*
 * 功能: 国际化文案领域记录，对应 system_i18n_message 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.domain;

/**
 * 国际化文案记录。
 *
 * @param locale      语言区域（zh-CN / en-US）
 * @param messageKey  国际化键
 * @param message     展示文案
 */
public record I18nMessage(String locale, String messageKey, String message) {
}
