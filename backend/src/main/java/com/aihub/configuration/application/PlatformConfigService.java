/*
 * 功能: 平台配置类型安全读取服务，供其它模块按类型读取 system_config，带默认值回退，避免散落字符串 key。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.application;

import com.aihub.configuration.domain.ConfigurationRepository;
import com.aihub.configuration.domain.SystemConfig;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.InternalException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台配置类型安全读取服务。
 *
 * <p>供其它模块以 {@link ConfigKeys} 常量读取配置，按值类型解析当前值（为空回退默认值），
 * 提供带或不带默认值回退的重载。读取不触发审计；写更新经 {@link ConfigurationApplicationService}。
 */
@Service
public class PlatformConfigService {

    private final ConfigurationRepository repository;

    public PlatformConfigService(ConfigurationRepository repository) {
        this.repository = repository;
    }

    /**
     * 读取字符串配置（为空回退默认值）。
     */
    @Transactional(readOnly = true)
    public String getString(String key) {
        SystemConfig config = require(key);
        return rawValue(config);
    }

    /**
     * 读取字符串配置，缺失或为空时返回 fallback。
     */
    @Transactional(readOnly = true)
    public String getString(String key, String fallback) {
        return repository.findByKey(key)
                .map(this::rawValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }

    /**
     * 读取 int 配置（为空回退默认值）。
     */
    @Transactional(readOnly = true)
    public int getInt(String key) {
        SystemConfig config = require(key);
        return Integer.parseInt(rawValue(config).trim());
    }

    /**
     * 读取 int 配置，缺失或解析失败时返回 fallback。
     */
    @Transactional(readOnly = true)
    public int getInt(String key, int fallback) {
        try {
            return repository.findByKey(key)
                    .map(this::rawValue)
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> Integer.parseInt(v.trim()))
                    .orElse(fallback);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * 读取 long 配置（为空回退默认值）。
     */
    @Transactional(readOnly = true)
    public long getLong(String key) {
        SystemConfig config = require(key);
        return Long.parseLong(rawValue(config).trim());
    }

    /**
     * 读取 long 配置，缺失或解析失败时返回 fallback。
     */
    @Transactional(readOnly = true)
    public long getLong(String key, long fallback) {
        try {
            return repository.findByKey(key)
                    .map(this::rawValue)
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> Long.parseLong(v.trim()))
                    .orElse(fallback);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * 读取 boolean 配置（为空回退默认值）。
     */
    @Transactional(readOnly = true)
    public boolean getBoolean(String key) {
        SystemConfig config = require(key);
        return Boolean.parseBoolean(rawValue(config).trim());
    }

    /**
     * 读取 boolean 配置，缺失时返回 fallback。
     */
    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean fallback) {
        return repository.findByKey(key)
                .map(this::rawValue)
                .filter(v -> v != null && !v.isBlank())
                .map(v -> Boolean.parseBoolean(v.trim()))
                .orElse(fallback);
    }

    private SystemConfig require(String key) {
        return repository.findByKey(key)
                .orElseThrow(() -> new InternalException("configuration key not found: " + key));
    }

    private String rawValue(SystemConfig config) {
        String value = config.configValue();
        if (value == null || value.isBlank()) {
            return config.defaultValue();
        }
        return value;
    }
}
