/*
 * 功能: configuration 集成测试——以 Testcontainers PostgreSQL 验证 V8 迁移、预置配置、
 *       Secret 拒绝、类型校验、版本自增与 PlatformConfigService 类型安全读取。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.configuration.application.ConfigKeys;
import com.aihub.configuration.application.ConfigurationApplicationService;
import com.aihub.configuration.application.ConfigurationDtos.ConfigView;
import com.aihub.configuration.application.ConfigurationDtos.UpdateConfigCommand;
import com.aihub.configuration.application.PlatformConfigService;
import com.aihub.configuration.domain.ConfigValueType;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * configuration 集成测试。无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class ConfigurationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ConfigurationApplicationService configurationService;
    @Autowired
    private PlatformConfigService platformConfig;

    @Test
    void v8MigrationSeedsConfigurations() {
        List<ConfigView> configs = configurationService.listConfigurations();
        assertThat(configs).extracting(ConfigView::configKey)
                .contains(ConfigKeys.JOB_DVC_MAX_RETRIES,
                        ConfigKeys.MCP_WRITE_TOOLS_ENABLED,
                        ConfigKeys.SECURITY_AGENT_TOKEN_MAX_TTL_SECONDS);
        // 不可热更新项 hotReloadable=false。
        ConfigView writeTools = configs.stream()
                .filter(c -> c.configKey().equals(ConfigKeys.MCP_WRITE_TOOLS_ENABLED))
                .findFirst().orElseThrow();
        assertThat(writeTools.hotReloadable()).isFalse();
    }

    @Test
    void platformConfigServiceReadsTypedValues() {
        assertThat(platformConfig.getInt(ConfigKeys.JOB_DVC_MAX_RETRIES)).isEqualTo(5);
        assertThat(platformConfig.getBoolean(ConfigKeys.MCP_WRITE_TOOLS_ENABLED)).isFalse();
        assertThat(platformConfig.getInt(ConfigKeys.MCP_MAX_RESULT_ITEMS)).isEqualTo(50);
        // 缺失键回退。
        assertThat(platformConfig.getInt("missing.key", 42)).isEqualTo(42);
    }

    @Test
    void updateIncrementsVersionAndPersistsTypedValue() {
        ConfigView updated = configurationService.updateConfiguration(
                ConfigKeys.JOB_WEBHOOK_MAX_RETRIES,
                new UpdateConfigCommand(11, 1L, null), "system");
        assertThat(updated.version()).isEqualTo(2L);
        assertThat(updated.value()).isEqualTo(11);
        // 类型安全读取反映新值。
        assertThat(platformConfig.getInt(ConfigKeys.JOB_WEBHOOK_MAX_RETRIES)).isEqualTo(11);
    }

    @Test
    void updateRejectsVersionMismatch() {
        assertThatThrownBy(() -> configurationService.updateConfiguration(
                ConfigKeys.AUDIT_RETENTION_DAYS,
                new UpdateConfigCommand(200, 999L, null), "system"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void secretKeyIsRejected() {
        assertThatThrownBy(() -> configurationService.updateConfiguration(
                "app.password",
                new UpdateConfigCommand("leaked", 1L, null), "system"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).errorCode())
                        .isEqualTo(ErrorCode.CONFIG_SECRET_FORBIDDEN));
    }

    @Test
    void typeValidationRejectsInvalidInteger() {
        assertThatThrownBy(() -> configurationService.updateConfiguration(
                ConfigKeys.JOB_DVC_MAX_RETRIES,
                new UpdateConfigCommand("not-an-int", 1L, null), "system"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).errorCode())
                        .isEqualTo(ErrorCode.CONFIG_VALUE_INVALID));
    }

    @Test
    void booleanUpdateValidatesType() {
        // 提交非布尔值被拒。
        assertThatThrownBy(() -> configurationService.updateConfiguration(
                ConfigKeys.NOTIFICATION_WEBHOOK_ENABLED,
                new UpdateConfigCommand("yes", 1L, null), "system"))
                .isInstanceOf(ValidationException.class);
        // 提交合法布尔值通过。
        ConfigView updated = configurationService.updateConfiguration(
                ConfigKeys.NOTIFICATION_WEBHOOK_ENABLED,
                new UpdateConfigCommand(false, 1L, null), "system");
        assertThat(updated.value()).isEqualTo(false);
        assertThat(platformConfig.getBoolean(ConfigKeys.NOTIFICATION_WEBHOOK_ENABLED)).isFalse();
    }
}
