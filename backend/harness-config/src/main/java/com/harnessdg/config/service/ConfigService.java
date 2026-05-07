/**
 * 功能：系统配置服务接口
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.config.service;

import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.model.config.dto.ConfigCreateRequest;
import com.harnessdg.model.config.dto.ConfigDTO;
import com.harnessdg.model.config.dto.ConfigHistoryDTO;
import com.harnessdg.model.config.dto.ConfigUpdateRequest;

import java.util.List;
import java.util.Map;

public interface ConfigService {

    // ===== 查询值（供内部模块消费，带缓存） =====

    String getValue(String key);

    String getValue(String key, String defaultValue);

    Map<String, String> getByCategory(String category);

    void setValue(String key, String value);

    // ===== 管理接口（供后台管理使用） =====

    PageResult<ConfigDTO> listConfigs(String category, String keyword, String environment, PageRequest pageRequest);

    ConfigDTO getConfig(String key);

    ConfigDTO createConfig(ConfigCreateRequest request);

    ConfigDTO updateConfig(String key, ConfigUpdateRequest request);

    void deleteConfig(String key);

    List<ConfigHistoryDTO> getHistory(String key);
}
