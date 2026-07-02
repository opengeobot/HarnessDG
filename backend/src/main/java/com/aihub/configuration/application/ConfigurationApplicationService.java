/*
 * 功能: 配置应用服务，编排非敏感运行配置的查询与更新；拒绝 Secret 入库，按值类型校验提交值。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.application;

import com.aihub.configuration.application.ConfigurationDtos.ConfigView;
import com.aihub.configuration.application.ConfigurationDtos.UpdateConfigCommand;
import com.aihub.configuration.domain.ConfigValueType;
import com.aihub.configuration.domain.ConfigurationRepository;
import com.aihub.configuration.domain.SystemConfig;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置应用服务。
 *
 * <p><b>Secret 拒绝：</b>更新时若 configKey 命中敏感模式（含 password/secret/token/privateKey/credential 等，
 * 大小写不敏感）一律拒绝（{@link ErrorCode#CONFIG_SECRET_FORBIDDEN}），<b>绝不把密码/Token/私钥写入 system_config</b>。
 *
 * <p><b>类型/校验：</b>按 {@link ConfigValueType} 校验提交值并序列化为文本存储；不可热更新项
 * （{@code hotReloadable=false}）在响应中体现，客户端据此提示需重启。变更经 AuditPort 记录（TODO Task 10），
 * version 自增。
 */
@Service
public class ConfigurationApplicationService {

    /** 敏感配置键模式：命中即拒绝入库（密码/Token/私钥/凭据等）。 */
    static final Pattern SECRET_PATTERN =
            Pattern.compile("(?i)(password|secret|token|privatekey|credential)");

    private final ConfigurationRepository repository;
    private final AuditPort auditPort;
    private final ObjectMapper objectMapper;

    public ConfigurationApplicationService(ConfigurationRepository repository,
                                           AuditPort auditPort,
                                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 列出全部配置。
     */
    @Transactional(readOnly = true)
    public List<ConfigView> listConfigurations() {
        return repository.findAll().stream()
                .map(config -> ConfigView.from(config, typedValue(config)))
                .toList();
    }

    /**
     * 更新配置。
     */
    @Transactional
    public ConfigView updateConfiguration(String configKey, UpdateConfigCommand command, String actorId) {
        if (command == null) {
            throw new ValidationException("command is required");
        }
        rejectSecretKey(configKey);
        SystemConfig existing = repository.findByKey(configKey)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CONFIG_NOT_FOUND,
                        "configuration not found", Map.of("configKey", configKey)));
        if (command.expectedVersion() != existing.version()) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "configuration version mismatch",
                    Map.of("expected", command.expectedVersion(), "actual", existing.version()));
        }
        String serialized = validateAndSerialize(existing.valueType(), command.value());
        SystemConfig updated = repository.update(configKey, serialized, existing.version(), actorId);
        auditPort.record("SYSTEM_CONFIGURATION_UPDATED", actorId, configKey,
                Map.of("version", updated.version(), "hotReloadable", updated.hotReloadable()));
        return ConfigView.from(updated, typedValue(updated));
    }

    /** 命中敏感模式的配置键一律拒绝入库。 */
    static void rejectSecretKey(String configKey) {
        if (configKey != null && SECRET_PATTERN.matcher(configKey).find()) {
            throw new ValidationException(ErrorCode.CONFIG_SECRET_FORBIDDEN,
                    "secret-bearing configuration key is forbidden",
                    Map.of("configKey", configKey));
        }
    }

    /** 按值类型校验提交值并序列化为存储文本。 */
    String validateAndSerialize(ConfigValueType type, Object value) {
        try {
            return switch (type) {
                case STRING -> value == null ? "" : String.valueOf(value);
                case INTEGER -> {
                    int i = parseInteger(value);
                    yield String.valueOf(i);
                }
                case LONG -> {
                    long l = parseLong(value);
                    yield String.valueOf(l);
                }
                case BOOLEAN -> {
                    if (!(value instanceof Boolean b)) {
                        throw new IllegalArgumentException("value must be a boolean");
                    }
                    yield String.valueOf(b);
                }
                case DURATION -> {
                    String text = requireText(value);
                    Duration.parse(text);
                    yield text;
                }
                case JSON -> {
                    String text = value == null ? "null" : String.valueOf(value);
                    objectMapper.readTree(text);
                    yield text;
                }
            };
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception ex) {
            throw new ValidationException(ErrorCode.CONFIG_VALUE_INVALID,
                    "configuration value does not match type " + type,
                    Map.of("valueType", type.name(), "reason", ex.getMessage()));
        }
    }

    /** 解析配置的当前值（configValue 为空回退 defaultValue）为类型安全对象。 */
    Object typedValue(SystemConfig config) {
        String raw = config.configValue();
        if (raw == null || raw.isBlank()) {
            raw = config.defaultValue();
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return switch (config.valueType()) {
                case STRING -> raw;
                case INTEGER -> Integer.valueOf(raw.trim());
                case LONG -> Long.valueOf(raw.trim());
                case BOOLEAN -> Boolean.valueOf(raw.trim());
                case DURATION -> raw;
                case JSON -> raw;
            };
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    private int parseInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private long parseLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value).trim());
    }

    private String requireText(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return String.valueOf(value);
    }
}
