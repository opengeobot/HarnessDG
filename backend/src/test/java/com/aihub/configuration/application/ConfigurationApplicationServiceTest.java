/*
 * 功能: 配置应用服务单元测试——Secret 拒绝、类型校验、版本自增、未授权路径覆盖。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.aihub.configuration.application.ConfigurationDtos.UpdateConfigCommand;
import com.aihub.configuration.domain.ConfigValueType;
import com.aihub.configuration.domain.SystemConfig;
import com.aihub.configuration.domain.ConfigurationRepository;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 配置应用服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationApplicationServiceTest {

    @Mock
    private ConfigurationRepository repository;
    @Mock
    private com.aihub.configuration.application.AuditPort auditPort;

    private ConfigurationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ConfigurationApplicationService(repository, auditPort, new ObjectMapper());
    }

    private SystemConfig config(String key, ConfigValueType type, String value, String def,
                                boolean hot, long version) {
        return new SystemConfig("cfg_" + key, key, type, value, def, "PLATFORM", null,
                hot, null, null, version, null);
    }

    @Test
    void secretKeyIsRejected() {
        assertThatThrownBy(() -> service.updateConfiguration("app.password",
                new UpdateConfigCommand("x", 1L, null), "admin"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).errorCode())
                        .isEqualTo(ErrorCode.CONFIG_SECRET_FORBIDDEN));
    }

    @Test
    void secretKeyWithTokenIsRejected() {
        assertThatThrownBy(() -> service.updateConfiguration("service.token",
                new UpdateConfigCommand("x", 1L, null), "admin"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void integerTypeRejectsNonNumeric() {
        given(repository.findByKey("job.dvc.maxRetries"))
                .willReturn(Optional.of(config("job.dvc.maxRetries", ConfigValueType.INTEGER, "5", "5", true, 1L)));
        assertThatThrownBy(() -> service.updateConfiguration("job.dvc.maxRetries",
                new UpdateConfigCommand("abc", 1L, null), "admin"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).errorCode())
                        .isEqualTo(ErrorCode.CONFIG_VALUE_INVALID));
    }

    @Test
    void booleanTypeRejectsNonBoolean() {
        given(repository.findByKey("mcp.writeTools.enabled"))
                .willReturn(Optional.of(config("mcp.writeTools.enabled", ConfigValueType.BOOLEAN,
                        "false", "false", false, 1L)));
        assertThatThrownBy(() -> service.updateConfiguration("mcp.writeTools.enabled",
                new UpdateConfigCommand("yes", 1L, null), "admin"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void durationTypeRejectsInvalid() {
        given(repository.findByKey("transfer.ttl"))
                .willReturn(Optional.of(config("transfer.ttl", ConfigValueType.DURATION, "PT5M", "PT5M", true, 1L)));
        assertThatThrownBy(() -> service.updateConfiguration("transfer.ttl",
                new UpdateConfigCommand("not-a-duration", 1L, null), "admin"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void jsonTypeRejectsInvalid() {
        given(repository.findByKey("feature.flags"))
                .willReturn(Optional.of(config("feature.flags", ConfigValueType.JSON, "{}", "{}", true, 1L)));
        assertThatThrownBy(() -> service.updateConfiguration("feature.flags",
                new UpdateConfigCommand("{invalid", 1L, null), "admin"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateSucceedsAndIncrementsVersion() {
        given(repository.findByKey("job.dvc.maxRetries"))
                .willReturn(Optional.of(config("job.dvc.maxRetries", ConfigValueType.INTEGER, "5", "5", true, 1L)));
        given(repository.update("job.dvc.maxRetries", "9", 1L, "admin"))
                .willReturn(config("job.dvc.maxRetries", ConfigValueType.INTEGER, "9", "5", true, 2L));
        var view = service.updateConfiguration("job.dvc.maxRetries",
                new UpdateConfigCommand(9, 1L, null), "admin");
        assertThat(view.version()).isEqualTo(2L);
        assertThat(view.value()).isEqualTo(9);
        verify(repository).update("job.dvc.maxRetries", "9", 1L, "admin");
    }

    @Test
    void updateRejectsVersionMismatch() {
        given(repository.findByKey("job.dvc.maxRetries"))
                .willReturn(Optional.of(config("job.dvc.maxRetries", ConfigValueType.INTEGER, "5", "5", true, 3L)));
        assertThatThrownBy(() -> service.updateConfiguration("job.dvc.maxRetries",
                new UpdateConfigCommand(9, 1L, null), "admin"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateNotFound() {
        given(repository.findByKey("nope")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateConfiguration("nope",
                new UpdateConfigCommand("x", 1L, null), "admin"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listConfigurationsExposesTypedValues() {
        given(repository.findAll()).willReturn(List.of(
                config("job.dvc.maxRetries", ConfigValueType.INTEGER, "5", "5", true, 1L),
                config("mcp.writeTools.enabled", ConfigValueType.BOOLEAN, "false", "false", false, 1L)));
        var views = service.listConfigurations();
        assertThat(views).hasSize(2);
        var intView = views.get(0);
        assertThat(intView.valueType()).isEqualTo(ConfigValueType.INTEGER);
        assertThat(intView.value()).isEqualTo(5);
        assertThat(views.get(1).value()).isEqualTo(false);
        assertThat(views.get(1).hotReloadable()).isFalse();
    }

    @Test
    void listConfigurationsFallsBackToDefaultWhenValueBlank() {
        given(repository.findAll()).willReturn(List.of(
                config("job.dvc.maxRetries", ConfigValueType.INTEGER, null, "5", true, 1L)));
        var views = service.listConfigurations();
        assertThat(views.get(0).value()).isEqualTo(5);
    }
}
