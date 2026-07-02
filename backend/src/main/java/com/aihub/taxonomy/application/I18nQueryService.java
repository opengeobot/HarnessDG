/*
 * 功能: 国际化文案查询应用服务，按 locale 返回 i18n_key→文案 映射，供前端/错误消息展示。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.application;

import com.aihub.taxonomy.domain.I18nMessageRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 国际化文案查询应用服务。
 *
 * <p>本任务保证数据存在并暴露查询能力，前端集成不在本任务范围。
 */
@Service
public class I18nQueryService {

    private final I18nMessageRepository repository;

    public I18nQueryService(I18nMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * 返回指定 locale 下的 i18n_key→文案 映射。
     *
     * @param locale 语言区域（zh-CN / en-US），为空时回退 zh-CN
     */
    @Transactional(readOnly = true)
    public Map<String, String> messages(String locale) {
        String resolved = (locale == null || locale.isBlank()) ? "zh-CN" : locale;
        return repository.findAllByLocale(resolved);
    }
}
