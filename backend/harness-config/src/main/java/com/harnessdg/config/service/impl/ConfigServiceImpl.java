/**
 * 功能：系统配置服务实现（含缓存 + 历史记录）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.config.mapper.SysConfigHistoryMapper;
import com.harnessdg.config.mapper.SysConfigMapper;
import com.harnessdg.config.service.ConfigService;
import com.harnessdg.model.config.dto.ConfigCreateRequest;
import com.harnessdg.model.config.dto.ConfigDTO;
import com.harnessdg.model.config.dto.ConfigHistoryDTO;
import com.harnessdg.model.config.dto.ConfigUpdateRequest;
import com.harnessdg.model.config.entity.SysConfig;
import com.harnessdg.model.config.entity.SysConfigHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final SysConfigMapper configMapper;
    private final SysConfigHistoryMapper historyMapper;

    private final Cache<String, String> configCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    // ===== 值查询 =====

    @Override
    public String getValue(String key) {
        return configCache.get(key, this::loadFromDb);
    }

    @Override
    public String getValue(String key, String defaultValue) {
        String value = getValue(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public Map<String, String> getByCategory(String category) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getCategory, category);
        List<SysConfig> configs = configMapper.selectList(wrapper);
        return configs.stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, c ->
                        c.getConfigValue() == null ? "" : c.getConfigValue()));
    }

    @Override
    @Transactional
    public void setValue(String key, String value) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, key);
        SysConfig existing = configMapper.selectOne(wrapper);
        if (existing != null) {
            String oldValue = existing.getConfigValue();
            existing.setConfigValue(value);
            configMapper.updateById(existing);
            writeHistory(key, oldValue, value, existing.getEnvironment(), "update", null);
        } else {
            SysConfig newConfig = new SysConfig();
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            newConfig.setValueType("string");
            newConfig.setCategory("custom");
            newConfig.setIsEncrypted(false);
            newConfig.setIsReadonly(false);
            newConfig.setEnvironment("all");
            configMapper.insert(newConfig);
            writeHistory(key, null, value, "all", "create", null);
        }
        configCache.invalidate(key);
    }

    // ===== 管理接口 =====

    @Override
    public PageResult<ConfigDTO> listConfigs(String category, String keyword, String environment, PageRequest pageRequest) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(SysConfig::getCategory, category);
        }
        if (StringUtils.hasText(environment)) {
            wrapper.eq(SysConfig::getEnvironment, environment);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysConfig::getConfigKey, keyword)
                    .or().like(SysConfig::getConfigValue, keyword));
        }
        wrapper.orderByAsc(SysConfig::getCategory).orderByAsc(SysConfig::getConfigKey);

        Page<SysConfig> page = new Page<>(pageRequest.getPage(), pageRequest.getPageSize());
        Page<SysConfig> result = configMapper.selectPage(page, wrapper);

        List<ConfigDTO> items = result.getRecords().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(), pageRequest.getPage(), pageRequest.getPageSize());
    }

    @Override
    public ConfigDTO getConfig(String key) {
        SysConfig config = findByKey(key);
        return toDTO(config);
    }

    @Override
    @Transactional
    public ConfigDTO createConfig(ConfigCreateRequest request) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, request.getConfigKey());
        String env = StringUtils.hasText(request.getEnvironment()) ? request.getEnvironment() : "all";
        wrapper.eq(SysConfig::getEnvironment, env);
        if (configMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFIG_KEY_EXISTS);
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(request.getConfigKey());
        config.setConfigValue(request.getConfigValue());
        config.setValueType(StringUtils.hasText(request.getValueType()) ? request.getValueType() : "string");
        config.setCategory(request.getCategory());
        config.setDescription(request.getDescription());
        config.setIsEncrypted(Boolean.TRUE.equals(request.getIsEncrypted()));
        config.setIsReadonly(Boolean.TRUE.equals(request.getIsReadonly()));
        config.setEnvironment(env);
        configMapper.insert(config);

        writeHistory(config.getConfigKey(), null, config.getConfigValue(), env, "create", request.getComment());
        configCache.invalidate(config.getConfigKey());

        return toDTO(configMapper.selectById(config.getId()));
    }

    @Override
    @Transactional
    public ConfigDTO updateConfig(String key, ConfigUpdateRequest request) {
        SysConfig existing = findByKey(key);
        if (Boolean.TRUE.equals(existing.getIsReadonly())) {
            throw new BizException(ErrorCode.CONFIG_READONLY);
        }
        String oldValue = existing.getConfigValue();

        if (request.getConfigValue() != null) {
            existing.setConfigValue(request.getConfigValue());
        }
        if (StringUtils.hasText(request.getValueType())) {
            existing.setValueType(request.getValueType());
        }
        if (StringUtils.hasText(request.getCategory())) {
            existing.setCategory(request.getCategory());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getIsEncrypted() != null) {
            existing.setIsEncrypted(request.getIsEncrypted());
        }
        if (request.getIsReadonly() != null) {
            existing.setIsReadonly(request.getIsReadonly());
        }
        if (StringUtils.hasText(request.getEnvironment())) {
            existing.setEnvironment(request.getEnvironment());
        }
        configMapper.updateById(existing);

        if (!Objects.equals(oldValue, existing.getConfigValue())) {
            writeHistory(key, oldValue, existing.getConfigValue(),
                    existing.getEnvironment(), "update", request.getComment());
        }
        configCache.invalidate(key);

        return toDTO(configMapper.selectById(existing.getId()));
    }

    @Override
    @Transactional
    public void deleteConfig(String key) {
        SysConfig existing = findByKey(key);
        if (Boolean.TRUE.equals(existing.getIsReadonly())) {
            throw new BizException(ErrorCode.CONFIG_READONLY);
        }
        configMapper.deleteById(existing.getId());
        writeHistory(key, existing.getConfigValue(), null, existing.getEnvironment(), "delete", null);
        configCache.invalidate(key);
    }

    @Override
    public List<ConfigHistoryDTO> getHistory(String key) {
        LambdaQueryWrapper<SysConfigHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfigHistory::getConfigKey, key)
                .orderByDesc(SysConfigHistory::getChangedAt);
        List<SysConfigHistory> records = historyMapper.selectList(wrapper);
        return records.stream().map(this::toHistoryDTO).collect(Collectors.toList());
    }

    // ===== 内部辅助 =====

    private String loadFromDb(String key) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, key);
        SysConfig config = configMapper.selectOne(wrapper);
        return config != null ? config.getConfigValue() : null;
    }

    private SysConfig findByKey(String key) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, key);
        SysConfig config = configMapper.selectOne(wrapper);
        if (config == null) {
            throw new BizException(ErrorCode.CONFIG_NOT_FOUND);
        }
        return config;
    }

    private void writeHistory(String key, String oldValue, String newValue,
                              String environment, String changeType, String comment) {
        SysConfigHistory history = new SysConfigHistory();
        history.setConfigKey(key);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setEnvironment(StringUtils.hasText(environment) ? environment : "all");
        history.setChangeType(changeType);
        history.setChangedBy(currentUsername());
        history.setChangedAt(OffsetDateTime.now());
        history.setComment(comment);
        historyMapper.insert(history);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof String s && !"anonymousUser".equals(s)) {
                return s;
            }
        }
        return "system";
    }

    private ConfigDTO toDTO(SysConfig entity) {
        if (entity == null) {
            return null;
        }
        ConfigDTO dto = new ConfigDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private ConfigHistoryDTO toHistoryDTO(SysConfigHistory entity) {
        ConfigHistoryDTO dto = new ConfigHistoryDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
