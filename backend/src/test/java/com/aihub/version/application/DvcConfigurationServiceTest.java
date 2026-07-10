/*
 * 功能: DvcConfigurationService 单元测试。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.application;

import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.integration.minio.infrastructure.MinioProperties;
import com.aihub.shared.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DvcConfigurationService")
class DvcConfigurationServiceTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AuditService auditService;

    private MinioProperties minioProperties;
    private DvcConfigurationService service;

    @BeforeEach
    void setUp() {
        minioProperties = new MinioProperties();
        minioProperties.setEndpoint("http://localhost:9000");
        minioProperties.setAccessKey("minioadmin");
        minioProperties.setSecretKey("minioadmin");
        minioProperties.setDvcAccessKey("dvc-scoped-key");
        minioProperties.setDvcSecretKey("dvc-scoped-secret");
        service = new DvcConfigurationService(minioProperties, authorizationService, auditService);
    }

    @Test
    @DisplayName("生成 DVC Remote 配置应使用 MinIO 端点")
    void generateRemoteConfigShouldUseMinioEndpoint() {
        DvcConfigurationService.DvcRemoteConfig config = service.generateRemoteConfig("asset_1");

        assertThat(config.bucket()).isEqualTo("dvc-cache");
        assertThat(config.endpoint()).isEqualTo("http://localhost:9000");
        assertThat(config.type()).isEqualTo("s3");
        assertThat(config.prefix()).isEqualTo("asset_1/");
        assertThat(config.expiresAt()).isNotNull();

        verify(authorizationService).requirePermission("asset:read");
        verify(auditService).record(any());
    }

    @Test
    @DisplayName("生成 DVC Remote 配置应优先使用外部端点")
    void generateRemoteConfigShouldPreferExternalEndpoint() {
        minioProperties.setExternalEndpoint("http://minio.example.com:9000");

        DvcConfigurationService.DvcRemoteConfig config = service.generateRemoteConfig("asset_1");

        assertThat(config.endpoint()).isEqualTo("http://minio.example.com:9000");
    }

    @Test
    @DisplayName("签发 DVC 凭据应使用 scoped 密钥而非 root")
    void issueCredentialsShouldUseScopedKeys() {
        DvcConfigurationService.DvcCredentials credentials = service.issueCredentials("asset_1");

        assertThat(credentials.accessKey()).isEqualTo("dvc-scoped-key");
        assertThat(credentials.secretKey()).isEqualTo("dvc-scoped-secret");
        assertThat(credentials.bucket()).isEqualTo("dvc-cache");
        assertThat(credentials.prefix()).isEqualTo("asset_1/");
        assertThat(credentials.credentialType()).isEqualTo("SCOPED_CONFIG_KEY");
        assertThat(credentials.limitationNote()).contains("STS");
        assertThat(credentials.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(14 * 60));

        verify(authorizationService).requirePermission("asset:read");
        verify(auditService).record(any());
    }

    @Test
    @DisplayName("未配置 scoped 密钥时应 fail-closed")
    void issueCredentialsShouldFailWhenScopedKeysMissing() {
        minioProperties.setDvcAccessKey("");
        minioProperties.setDvcSecretKey("");

        assertThatThrownBy(() -> service.issueCredentials("asset_1"))
                .isInstanceOf(ValidationException.class);
    }
}
