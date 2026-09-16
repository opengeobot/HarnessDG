package com.aihub.version.application;

import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.integration.minio.infrastructure.MinioProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("签发 DVC 凭据应包含短期有效期")
    void issueCredentialsShouldIncludeShortTtl() {
        DvcConfigurationService.DvcCredentials credentials = service.issueCredentials("asset_1");

        assertThat(credentials.accessKey()).isEqualTo("minioadmin");
        assertThat(credentials.bucket()).isEqualTo("dvc-cache");
        assertThat(credentials.prefix()).isEqualTo("asset_1/");
        assertThat(credentials.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(14 * 60));

        verify(authorizationService).requirePermission("asset:read");
        verify(auditService).record(any());
    }

    @Test
    @DisplayName("生成 Version 级别 DVC 完整配置应包含短期 S3 凭据")
    void generateDvcRemoteConfigShouldIncludeShortTermCredentials() {
        DvcConfigurationService.DvcRemoteConfigFull config = service.generateDvcRemoteConfig("ver_1");

        assertThat(config.bucket()).isEqualTo("dvc-cache");
        assertThat(config.endpoint()).isEqualTo("http://localhost:9000");
        assertThat(config.prefix()).isEqualTo("versions/ver_1/");
        assertThat(config.accessKey()).isEqualTo("minioadmin");
        assertThat(config.secretKey()).isEqualTo("minioadmin");
        assertThat(config.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(14 * 60));
        assertThat(config.dvcRemoteConfig()).contains("[remote \"aihub\"]");
        assertThat(config.dvcRemoteConfig()).contains("s3://dvc-cache/versions/ver_1/");

        verify(authorizationService).requirePermission("asset:read");
        verify(auditService).record(any());
    }

    @Test
    @DisplayName("生成 Version 级别 DVC 配置审计中 secretKey 应脱敏")
    void generateDvcRemoteConfigAuditShouldMaskSecretKey() {
        service.generateDvcRemoteConfig("ver_1");

        // 验证审计事件中 secretKey 被脱敏为 "***"
        verify(auditService).record(argThat(event ->
                "***".equals(event.attributes().get("secretKey"))));
    }

    @Test
    @DisplayName("generateDvcRemoteConfig 应优先使用外部端点")
    void generateDvcRemoteConfigShouldPreferExternalEndpoint() {
        minioProperties.setExternalEndpoint("http://minio.example.com:9000");

        DvcConfigurationService.DvcRemoteConfigFull config = service.generateDvcRemoteConfig("ver_1");

        assertThat(config.endpoint()).isEqualTo("http://minio.example.com:9000");
    }
}
