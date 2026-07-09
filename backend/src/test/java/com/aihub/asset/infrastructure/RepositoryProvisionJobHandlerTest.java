package com.aihub.asset.infrastructure;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetCard;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetRepositoryProvisioner;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ProvisioningStatus;
import com.aihub.asset.domain.Visibility;
import com.aihub.job.domain.JobContext;
import com.aihub.notification.domain.OutboxEvent;
import com.aihub.notification.domain.OutboxRepository;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepositoryProvisionJobHandler")
class RepositoryProvisionJobHandlerTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetRepositoryProvisioner repositoryProvisioner;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private IdGenerator idGenerator;

    private RepositoryProvisionJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new RepositoryProvisionJobHandler(
                assetRepository, repositoryProvisioner, outboxRepository, idGenerator);
    }

    @Test
    @DisplayName("应返回正确的任务类型")
    void shouldReturnCorrectType() {
        assertThat(handler.type()).isEqualTo("REPOSITORY_PROVISION");
    }

    @Test
    @DisplayName("资产未找到时应静默跳过")
    void shouldSkipWhenAssetNotFound() throws Exception {
        when(assetRepository.findByAssetId("ast_missing")).thenReturn(Optional.empty());

        JobContext context = new JobContext("job_1", "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_missing\"}", 0, "principal_1", "trace_1", "ast_missing");

        handler.handle(context);

        verify(assetRepository, never()).update(any());
        verify(outboxRepository, never()).append(any());
    }

    @Test
    @DisplayName("已完成建仓时应幂等跳过")
    void shouldSkipWhenAlreadyCompleted() throws Exception {
        Asset asset = buildAsset(ProvisioningStatus.COMPLETED);
        when(assetRepository.findByAssetId("ast_1")).thenReturn(Optional.of(asset));

        JobContext context = new JobContext("job_1", "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_1\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        verify(repositoryProvisioner, never()).provision(any());
        verify(assetRepository, never()).update(any());
    }

    @Test
    @DisplayName("成功路径: 建仓 → COMPLETED → 发布 Outbox 事件")
    void shouldProvisionSuccessfully() throws Exception {
        Asset asset = buildAsset(ProvisioningStatus.PENDING);
        when(assetRepository.findByAssetId("ast_1")).thenReturn(Optional.of(asset));

        AssetRepositoryRef ref = new AssetRepositoryRef("ns/name", "https://gitea/ns/name", "https://gitea/ns/name.git");
        when(repositoryProvisioner.provision(any())).thenReturn(ref);
        when(idGenerator.generate(IdPrefix.REQUEST)).thenReturn("req_1");

        JobContext context = new JobContext("job_1", "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_1\"}", 0, "principal_1", "trace_1", "ast_1");

        try (MockedStatic<AssetCard> cardMock = mockStatic(AssetCard.class)) {
            cardMock.when(() -> AssetCard.render(any(Asset.class)))
                    .thenReturn(new AssetCard.CardFiles("# README", "name: name"));

            handler.handle(context);
        }

        verify(assetRepository).update(asset);
        verify(outboxRepository).append(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("建仓失败时应标记 FAILED 并重新抛出异常")
    void shouldMarkFailedOnProvisionerError() throws Exception {
        Asset asset = buildAsset(ProvisioningStatus.PENDING);
        when(assetRepository.findByAssetId("ast_1")).thenReturn(Optional.of(asset));
        when(repositoryProvisioner.provision(any())).thenThrow(new RuntimeException("provision failed"));

        JobContext context = new JobContext("job_1", "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_1\"}", 0, "principal_1", "trace_1", "ast_1");

        try (MockedStatic<AssetCard> cardMock = mockStatic(AssetCard.class)) {
            cardMock.when(() -> AssetCard.render(any(Asset.class)))
                    .thenReturn(new AssetCard.CardFiles("# README", "name: name"));

            assertThatThrownBy(() -> handler.handle(context))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("provision failed");
        }

        verify(assetRepository).update(asset);
        verify(outboxRepository, never()).append(any());
    }

    private Asset buildAsset(ProvisioningStatus provisioningStatus) {
        Instant now = Instant.now();
        return new Asset.Builder()
                .assetId("ast_1")
                .type(AssetType.MODEL)
                .namespace("test-ns")
                .name("test-name")
                .displayName("Test Name")
                .description("Test Description")
                .visibility(Visibility.PUBLIC)
                .status(AssetStatus.ACTIVE)
                .provisioningStatus(provisioningStatus)
                .createdBy("user_1")
                .updatedBy("user_1")
                .createdAt(now)
                .updatedAt(now)
                .rowVersion(1L)
                .build();
    }
}
