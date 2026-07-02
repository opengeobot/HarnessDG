/*
 * 功能: 国际化文案查询端口，约定按 locale 取 i18n_key→文案 的映射。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.domain;

import java.util.Map;

/**
 * 国际化文案查询端口。
 *
 * <p>供前端/错误消息按 locale 取 i18n_key→文案 的映射。实现位于 infrastructure。
 */
public interface I18nMessageRepository {

    /**
     * 返回指定 locale 下的全部 i18n_key→文案 映射。
     *
     * @param locale 语言区域（zh-CN / en-US）
     * @return 键值映射（不存在时返回空映射）
     */
    Map<String, String> findAllByLocale(String locale);
}
