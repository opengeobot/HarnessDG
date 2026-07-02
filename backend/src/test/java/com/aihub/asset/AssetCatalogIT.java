/*
 * 功能: 资产目录集成测试——以 Testcontainers PostgreSQL 验证登记/检索/更新/删除全链路。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSearchQuery;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.application.UpdateAssetCommand;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.DatasetProfile;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
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
 * 资产目录集成测试。
 *
 * <p>使用真实 PostgreSQL 验证 Flyway 迁移、MyBatis-Plus CRUD、jsonb 类型处理与显式 SQL 检索协同工作。
 * 无 Docker 时整体跳过，不阻断 {@code ./mvnw verify}。默认 Noop 仓库开通器使流程无需 Gitea。
 *
 * <p>注意：本 IT 依赖授权/字典/标签治理 Bean 的完整上下文；无 Docker 时跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class AssetCatalogIT {

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
    private AssetApplicationService assetService;

    private CreateAssetCommand modelCommand(String name) {
        return new CreateAssetCommand(AssetType.MODEL, null, null, "nlp", name, name + " 展示名",
                "领域问答模型，关键词 qwendomain", Visibility.INTERNAL, List.of("team-nlp"),
                List.of("text-generation", "llm"), null, "Apache-2.0",
                new ModelProfile("pytorch", "text-generation", "decoder-only"), null, "usr_01");
    }

    @Test
    void registersQueriesUpdatesAndSoftDeletesAsset() {
        AssetView created = assetService.createAsset(modelCommand("qwen-domain-7b"));
        assertThat(created.assetId()).startsWith("ast_");
        assertThat(created.repository().fullName()).isEqualTo("nlp/qwen-domain-7b");

        AssetView fetched = assetService.getAsset(created.assetId(), "usr_01");
        assertThat(fetched.model().framework()).isEqualTo("pytorch");
        assertThat(fetched.tags()).contains("llm");

        // 关键词 + 框架 + owner 过滤命中。
        CursorPage<?> hit = assetService.searchAssets(new AssetSearchQuery("qwendomain", AssetType.MODEL,
                "nlp", null, "pytorch", null, null, null, null, "team-nlp", false, null, 10, "usr_01"));
        assertThat(hit.items()).hasSize(1);

        // 不匹配的框架过滤为空。
        CursorPage<?> miss = assetService.searchAssets(new AssetSearchQuery(null, AssetType.MODEL,
                null, null, "tensorflow", null, null, null, null, null, false, null, 10, "usr_01"));
        assertThat(miss.items()).isEmpty();

        assetService.updateAsset(created.assetId(), new UpdateAssetCommand(null, null, "改名后", "新描述",
                Visibility.PUBLIC, List.of("team-platform"), List.of("chat"), null, "MIT",
                new ModelProfile("vllm", "chat", null), null, "usr_02"));
        AssetView updated = assetService.getAsset(created.assetId(), "usr_02");
        assertThat(updated.displayName()).isEqualTo("改名后");
        assertThat(updated.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(updated.model().framework()).isEqualTo("vllm");

        assetService.deleteAsset(created.assetId(), "usr_03");
        assertThatThrownBy(() -> assetService.getAsset(created.assetId(), "usr_03"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsDuplicateCoordinateAndSupportsDatasetType() {
        assetService.createAsset(modelCommand("dup-model"));
        assertThatThrownBy(() -> assetService.createAsset(modelCommand("dup-model")))
                .isInstanceOf(ConflictException.class);

        AssetView dataset = assetService.createAsset(new CreateAssetCommand(AssetType.DATASET, null, null,
                "vision", "defect-images", "缺陷图像", "图像数据集", Visibility.PRIVATE,
                List.of("team-cv"), List.of("vision"), null, "CC-BY-4.0", null,
                new DatasetProfile("parquet", "image"), "usr_01"));
        assertThat(dataset.dataset().format()).isEqualTo("parquet");

        CursorPage<?> datasets = assetService.searchAssets(new AssetSearchQuery(null, AssetType.DATASET,
                null, null, null, null, "parquet", "image", null, null, false, null, 10, "usr_01"));
        assertThat(datasets.items()).hasSize(1);
    }
}
