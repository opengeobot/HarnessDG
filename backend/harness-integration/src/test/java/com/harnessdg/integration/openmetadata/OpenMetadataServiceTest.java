/**
 * 功能：OpenMetadata 服务集成测试
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.integration.openmetadata;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.harnessdg.integration.config.IntegrationConfig;
import com.harnessdg.model.ontology.entity.OntDimension;
import com.harnessdg.model.ontology.entity.OntEntity;
import com.harnessdg.model.ontology.entity.OntMetric;
import com.harnessdg.ontology.mapper.OntDimensionMapper;
import com.harnessdg.ontology.mapper.OntEntityMapper;
import com.harnessdg.ontology.mapper.OntMetricMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * OpenMetadata 服务集成测试
 * 使用 WireMock mock OpenMetadata API，验证注册/打标/血缘请求正确
 */
@SpringBootTest(classes = {
        OpenMetadataService.class,
        OpenMetadataClient.class,
        IntegrationConfig.class,
        OpenMetadataServiceTest.TestConfig.class
}, properties = {
        "harnessdg.integration.open-metadata.enabled=true",
        "harnessdg.integration.open-metadata.endpoint=http://localhost",
        "harnessdg.integration.open-metadata.api-path=/api/v1",
        "harnessdg.integration.open-metadata.timeout-ms=5000"
})
class OpenMetadataServiceTest {

    static WireMockServer wireMockServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        registry.add("harnessdg.integration.open-metadata.endpoint",
                () -> "http://localhost:" + wireMockServer.port());
    }

    @Autowired
    private OpenMetadataService openMetadataService;

    @Autowired
    private OntEntityMapper ontEntityMapper;

    @Autowired
    private OntMetricMapper ontMetricMapper;

    @Autowired
    private OntDimensionMapper ontDimensionMapper;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public OntEntityMapper ontEntityMapper() {
            return mock(OntEntityMapper.class);
        }

        @Bean
        @Primary
        public OntMetricMapper ontMetricMapper() {
            return mock(OntMetricMapper.class);
        }

        @Bean
        @Primary
        public OntDimensionMapper ontDimensionMapper() {
            return mock(OntDimensionMapper.class);
        }
    }

    /**
     * 测试用例 1: 同步实体到 OpenMetadata - 成功
     */
    @Test
    void testSyncEntityToOpenMetadata_success() {
        // Arrange
        OntEntity entity = createTestEntity(1L, "test_entity", "测试实体", "sales", "user1");
        when(ontEntityMapper.selectById(1L)).thenReturn(entity);

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/tables"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"table-1\", \"name\": \"test_entity\"}")));

        // Act & Assert
        assertDoesNotThrow(() -> openMetadataService.syncEntityToOpenMetadata(1L));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/tables"))
                .withRequestBody(containing("\"name\":\"test_entity\""))
                .withRequestBody(containing("\"database\":\"sales\"")));
    }

    /**
     * 测试用例 2: 同步实体到 OpenMetadata - 实体不存在
     */
    @Test
    void testSyncEntityToOpenMetadata_notFound() {
        // Arrange
        when(ontEntityMapper.selectById(999L)).thenReturn(null);

        // Act & Assert
        assertDoesNotThrow(() -> openMetadataService.syncEntityToOpenMetadata(999L));

        wireMockServer.verify(0, postRequestedFor(anyUrl()));
    }

    /**
     * 测试用例 3: 同步指标到 OpenMetadata - 成功
     */
    @Test
    void testSyncMetricToOpenMetadata_success() {
        // Arrange
        OntMetric metric = createTestMetric(1L, "revenue", "收入指标", 1L, "SUM(amount)", "元");
        when(ontMetricMapper.selectById(1L)).thenReturn(metric);

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/metrics"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"metric-1\", \"name\": \"revenue\"}")));

        // Act & Assert
        assertDoesNotThrow(() -> openMetadataService.syncMetricToOpenMetadata(1L));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/metrics"))
                .withRequestBody(containing("\"name\":\"revenue\""))
                .withRequestBody(containing("\"expression\":\"SUM(amount)\"")));
    }

    /**
     * 测试用例 4: 同步指标到 OpenMetadata - 指标不存在
     */
    @Test
    void testSyncMetricToOpenMetadata_notFound() {
        // Arrange
        when(ontMetricMapper.selectById(999L)).thenReturn(null);

        // Act & Assert
        assertDoesNotThrow(() -> openMetadataService.syncMetricToOpenMetadata(999L));

        wireMockServer.verify(0, postRequestedFor(anyUrl()));
    }

    /**
     * 测试用例 5: 从 Pipeline 配置提取并注册血缘关系 - 成功
     */
    @Test
    void testSyncLineageFromPipeline_success() {
        // Arrange
        OntEntity entity = createTestEntity(1L, "source_table", "源表", "sales", "user1");
        OntMetric metric = createTestMetric(1L, "revenue", "收入", 1L, "SUM(amount)", "元");
        OntDimension dimension = createTestDimension(1L, "date_dim", "日期维度", 1L, "created_at");

        when(ontEntityMapper.selectById(1L)).thenReturn(entity);
        when(ontMetricMapper.selectList(any())).thenReturn(List.of(metric));
        when(ontDimensionMapper.selectList(any())).thenReturn(List.of(dimension));

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/lineage"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"lineage-1\"}")));

        // Act & Assert
        assertDoesNotThrow(() -> openMetadataService.syncLineageFromPipeline(1L));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/lineage"))
                .withRequestBody(containing("\"fromEntity\""))
                .withRequestBody(containing("\"toEntity\""))
                .withRequestBody(containing("\"source_table\""))
                .withRequestBody(containing("\"source_table_output\"")));
    }

    /**
     * 测试用例 6: 自动打标 - 包含数据域标签
     */
    @Test
    void testAutoTagEntity_withDataDomain() {
        // Arrange
        OntEntity entity = createTestEntity(1L, "test_entity", "测试实体", "marketing", "user1");
        when(ontEntityMapper.selectById(1L)).thenReturn(entity);

        wireMockServer.stubFor(put(urlEqualTo("/api/v1/tables/test_entity/tags"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"tags\": [\"domain:marketing\", \"owner:user1\"]}")));

        // Act & Assert
        assertDoesNotThrow(() -> openMetadataService.autoTagEntity(1L));

        wireMockServer.verify(putRequestedFor(urlEqualTo("/api/v1/tables/test_entity/tags"))
                .withRequestBody(containing("\"domain:marketing\""))
                .withRequestBody(containing("\"owner:user1\"")));
    }

    /**
     * 测试用例 7: 自动打标 - 包含负责人标签
     */
    @Test
    void testAutoTagEntity_withOwner() {
        // Arrange
        OntEntity entity = createTestEntity(1L, "test_entity", "测试实体", null, "admin_user");
        when(ontEntityMapper.selectById(1L)).thenReturn(entity);

        wireMockServer.stubFor(put(urlEqualTo("/api/v1/tables/test_entity/tags"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())));

        // Act & Assert
        assertDoesNotThrow(() -> openMetadataService.autoTagEntity(1L));

        wireMockServer.verify(putRequestedFor(urlEqualTo("/api/v1/tables/test_entity/tags"))
                .withRequestBody(containing("\"owner:admin_user\"")));
    }

    /**
     * 测试用例 8: 自动打标 - 包含 Tags 敏感等级标签
     */
    @Test
    void testAutoTagEntity_withTags() {
        // Arrange
        OntEntity entity = createTestEntity(1L, "test_entity", "测试实体", "finance", "user1");
        entity.setTags(Map.of("sensitivity", "high", "other_key", "other_value"));
        when(ontEntityMapper.selectById(1L)).thenReturn(entity);

        wireMockServer.stubFor(put(urlEqualTo("/api/v1/tables/test_entity/tags"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())));

        // Act & Assert
        assertDoesNotThrow(() -> openMetadataService.autoTagEntity(1L));

        wireMockServer.verify(putRequestedFor(urlEqualTo("/api/v1/tables/test_entity/tags"))
                .withRequestBody(containing("\"domain:finance\""))
                .withRequestBody(containing("\"owner:user1\""))
                .withRequestBody(containing("\"sensitivity:high\"")));
    }

    /**
     * 测试用例 9: 同步实体到 OpenMetadata - 服务不可用（降级）
     */
    @Test
    void testSyncEntityToOpenMetadata_serviceUnavailable() {
        // Arrange
        OntEntity entity = createTestEntity(1L, "test_entity", "测试实体", "sales", "user1");
        when(ontEntityMapper.selectById(1L)).thenReturn(entity);

        // WireMock 返回 503 服务不可用
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/tables"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .withBody("{\"error\": \"Service Unavailable\"}")));

        // Act & Assert - 应该降级为 log warning，不抛出异常
        assertDoesNotThrow(() -> openMetadataService.syncEntityToOpenMetadata(1L));
    }

    /**
     * 测试用例 10: 同步指标到 OpenMetadata - 服务不可用（降级）
     */
    @Test
    void testSyncMetricToOpenMetadata_serviceUnavailable() {
        // Arrange
        OntMetric metric = createTestMetric(1L, "revenue", "收入指标", 1L, "SUM(amount)", "元");
        when(ontMetricMapper.selectById(1L)).thenReturn(metric);

        // WireMock 返回 503 服务不可用
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/metrics"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .withBody("{\"error\": \"Service Unavailable\"}")));

        // Act & Assert - 应该降级为 log warning，不抛出异常
        assertDoesNotThrow(() -> openMetadataService.syncMetricToOpenMetadata(1L));
    }

    // === Helper Methods ===

    private OntEntity createTestEntity(Long id, String code, String description, String domain, String owner) {
        OntEntity entity = new OntEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(Map.of("en", description));
        entity.setDescription(Map.of("zh", description));
        entity.setDataDomain(domain);
        entity.setOwner(owner);
        entity.setStatus("active");
        entity.setTags(Map.of("fields", List.of(
                Map.of("name", "id", "type", "BIGINT", "description", "Primary Key", "is_primary_key", true),
                Map.of("name", "name", "type", "VARCHAR", "description", "Name")
        )));
        return entity;
    }

    private OntMetric createTestMetric(Long id, String code, String description, Long entityId, String expression, String unit) {
        OntMetric metric = new OntMetric();
        metric.setId(id);
        metric.setCode(code);
        metric.setName(Map.of("en", description));
        metric.setDescription(Map.of("zh", description));
        metric.setEntityId(entityId);
        metric.setMetricType("derived");
        metric.setAggMethod("SUM");
        metric.setExpression(expression);
        metric.setUnit(unit);
        metric.setOwner("user1");
        metric.setStatus("active");
        return metric;
    }

    private OntDimension createTestDimension(Long id, String code, String description, Long entityId, String tableColumn) {
        OntDimension dimension = new OntDimension();
        dimension.setId(id);
        dimension.setCode(code);
        dimension.setName(Map.of("en", description));
        dimension.setDescription(Map.of("zh", description));
        dimension.setEntityId(entityId);
        dimension.setDimType("time");
        dimension.setTableColumn(tableColumn);
        dimension.setHierarchyLevel(1);
        dimension.setStatus("active");
        return dimension;
    }
}
