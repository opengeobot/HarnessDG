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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 资产目录集成测试。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class AssetCatalogIT {

    private static final String TEST_TEAM_ID = "team_catalog_it";

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
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedOwnerTeam() {
        jdbcTemplate.update(
                "INSERT INTO organization (organization_id, name, status, created_by, created_at, updated_at, row_version) "
                        + "VALUES (:orgId, :name, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (organization_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("orgId", "org_catalog_it")
                        .addValue("name", "Catalog IT Org"));
        jdbcTemplate.update(
                "INSERT INTO team (team_id, organization_id, name, description, status, created_by, "
                        + "created_at, updated_at, row_version) "
                        + "VALUES (:teamId, :orgId, :name, NULL, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (team_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("teamId", TEST_TEAM_ID)
                        .addValue("orgId", "org_catalog_it")
                        .addValue("name", "Catalog IT Team"));
    }

    private CreateAssetCommand modelCommand(String name) {
        return new CreateAssetCommand(AssetType.MODEL, null, null, "nlp", name, name + " 展示名",
                "领域问答模型，关键词 qwendomain", Visibility.INTERNAL, null,
                List.of("text-generation", "llm"), null, "Apache-2.0", TEST_TEAM_ID,
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

        CursorPage<?> hit = assetService.searchAssets(new AssetSearchQuery("qwendomain", AssetType.MODEL,
                "nlp", null, null, null, null, TEST_TEAM_ID, "pytorch", null, null, null, null, null, null, null, null, null, null, null, false, null, 10, "usr_01"));
        assertThat(hit.items()).hasSize(1);

        CursorPage<?> miss = assetService.searchAssets(new AssetSearchQuery(null, AssetType.MODEL,
                null, null, null, null, null, null, "tensorflow", null, null, null, null, null, null, null, null, null, null, null, false, null, 10, "usr_01"));
        assertThat(miss.items()).isEmpty();

        assetService.updateAsset(created.assetId(), new UpdateAssetCommand(0L, null, null, "改名后", "新描述",
                Visibility.PUBLIC, null, "MIT",
                null,
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
                null, List.of("vision"), null, "CC-BY-4.0", TEST_TEAM_ID, null,
                new DatasetProfile("parquet", "image"), "usr_01"));
        assertThat(dataset.dataset().format()).isEqualTo("parquet");

        CursorPage<?> datasets = assetService.searchAssets(new AssetSearchQuery(null, AssetType.DATASET,
                null, null, null, null, null, null, null, null, "parquet", "image", null, null, null, null, null, null, null, false, null, 10, "usr_01"));
        assertThat(datasets.items()).hasSize(1);
    }
}
