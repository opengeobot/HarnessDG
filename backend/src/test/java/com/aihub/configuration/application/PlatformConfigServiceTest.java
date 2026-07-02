/*
 * 功能: 平台配置类型安全读取服务单元测试——按类型读取、默认值回退、缺失回退。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.aihub.configuration.domain.ConfigValueType;
import com.aihub.configuration.domain.SystemConfig;
import com.aihub.configuration.domain.ConfigurationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 平台配置类型安全读取服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PlatformConfigServiceTest {

    @Mock
    private ConfigurationRepository repository;

    private PlatformConfigService service;

    @BeforeEach
    void setUp() {
        service = new PlatformConfigService(repository);
    }

    private SystemConfig config(String key, ConfigValueType type, String value, String def) {
        return new SystemConfig("cfg_" + key, key, type, value, def, "PLATFORM", null,
                true, null, null, 1L, null);
    }

    @Test
    void getIntReadsCurrentValue() {
        given(repository.findByKey("job.dvc.maxRetries"))
                .willReturn(Optional.of(config("job.dvc.maxRetries", ConfigValueType.INTEGER, "9", "5")));
        assertThat(service.getInt("job.dvc.maxRetries")).isEqualTo(9);
    }

    @Test
    void getIntFallsBackToDefaultWhenValueBlank() {
        given(repository.findByKey("job.dvc.maxRetries"))
                .willReturn(Optional.of(config("job.dvc.maxRetries", ConfigValueType.INTEGER, null, "5")));
        assertThat(service.getInt("job.dvc.maxRetries")).isEqualTo(5);
    }

    @Test
    void getIntFallbackWhenMissing() {
        given(repository.findByKey("missing")).willReturn(Optional.empty());
        assertThat(service.getInt("missing", 42)).isEqualTo(42);
    }

    @Test
    void getLongReadsValue() {
        given(repository.findByKey("transfer.web.maxSessionBytes"))
                .willReturn(Optional.of(config("transfer.web.maxSessionBytes", ConfigValueType.LONG,
                        "1234567890", "0")));
        assertThat(service.getLong("transfer.web.maxSessionBytes")).isEqualTo(1234567890L);
    }

    @Test
    void getBooleanReadsValue() {
        given(repository.findByKey("mcp.writeTools.enabled"))
                .willReturn(Optional.of(config("mcp.writeTools.enabled", ConfigValueType.BOOLEAN,
                        "true", "false")));
        assertThat(service.getBoolean("mcp.writeTools.enabled")).isTrue();
    }

    @Test
    void getBooleanFallbackWhenMissing() {
        given(repository.findByKey("missing")).willReturn(Optional.empty());
        assertThat(service.getBoolean("missing", true)).isTrue();
    }

    @Test
    void getStringReadsValue() {
        given(repository.findByKey("audit.retentionDays"))
                .willReturn(Optional.of(config("audit.retentionDays", ConfigValueType.STRING, "180", "180")));
        assertThat(service.getString("audit.retentionDays")).isEqualTo("180");
    }

    @Test
    void getStringFallbackWhenMissing() {
        given(repository.findByKey("missing")).willReturn(Optional.empty());
        assertThat(service.getString("missing", "default")).isEqualTo("default");
    }
}
