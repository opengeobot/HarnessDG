package com.harnessdg.ontology.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.model.ontology.dto.*;
import com.harnessdg.model.ontology.entity.OntDimension;
import com.harnessdg.model.ontology.entity.OntEntity;
import com.harnessdg.model.ontology.entity.OntMetric;
import com.harnessdg.ontology.mapper.OntDimensionMapper;
import com.harnessdg.ontology.mapper.OntEntityMapper;
import com.harnessdg.ontology.mapper.OntMetricMapper;
import com.harnessdg.ontology.service.impl.OntologyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * 功能：本体服务单元测试
 * 时间：2026-05-12
 * 作者：AxeXie
 */
@ExtendWith(MockitoExtension.class)
class OntologyServiceTest {

    @Mock private OntEntityMapper entityMapper;
    @Mock private OntMetricMapper metricMapper;
    @Mock private OntDimensionMapper dimensionMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private OntologyServiceImpl ontologyService;

    private OntEntity testEntity;
    private OntMetric testMetric;
    private OntDimension testDimension;

    @BeforeEach
    void setUp() {
        testEntity = new OntEntity();
        testEntity.setId(1L);
        testEntity.setCode("test_entity");
        testEntity.setName(Map.of("zh_CN", "测试实体", "en_US", "Test Entity"));
        testEntity.setDescription(Map.of("zh_CN", "测试描述", "en_US", "Test desc"));
        testEntity.setEntityType("business");
        testEntity.setDataDomain("sales");
        testEntity.setOwner("admin");
        testEntity.setStatus("draft");
        testEntity.setCreatedAt(OffsetDateTime.now());
        testEntity.setUpdatedAt(OffsetDateTime.now());

        testMetric = new OntMetric();
        testMetric.setId(1L);
        testMetric.setCode("test_metric");
        testMetric.setName(Map.of("zh_CN", "测试指标", "en_US", "Test Metric"));
        testMetric.setEntityId(1L);
        testMetric.setMetricType("derived");
        testMetric.setAggMethod("SUM");
        testMetric.setExpression("SUM(amount)");
        testMetric.setUnit("元");
        testMetric.setOwner("admin");
        testMetric.setStatus("draft");

        testDimension = new OntDimension();
        testDimension.setId(1L);
        testDimension.setCode("test_dim");
        testDimension.setName(Map.of("zh_CN", "测试维度", "en_US", "Test Dimension"));
        testDimension.setEntityId(1L);
        testDimension.setDimType("date");
        testDimension.setTableColumn("order_date");
        testDimension.setHierarchyLevel("1");
        testDimension.setStatus("draft");
    }

    @Test
    void testCreateEntity() {
        EntityCreateRequest request = new EntityCreateRequest();
        request.setCode("test_entity");
        request.setName(Map.of("zh_CN", "测试实体", "en_US", "Test Entity"));
        request.setDescription(Map.of("zh_CN", "测试描述", "en_US", "Test desc"));
        request.setEntityType("business");
        request.setDataDomain("sales");
        request.setOwner("admin");

        when(entityMapper.insert(any(OntEntity.class))).thenAnswer(invocation -> {
            OntEntity e = invocation.getArgument(0);
            e.setId(1L);
            e.setCreatedAt(OffsetDateTime.now());
            e.setUpdatedAt(OffsetDateTime.now());
            return 1;
        });

        OntEntityDTO result = ontologyService.createEntity(request);
        assertNotNull(result);
        assertEquals("test_entity", result.getCode());
        assertEquals("draft", result.getStatus());
        verify(eventPublisher).publishEvent(isA(OntologyServiceImpl.OntologyCreatedEvent.class));
    }

    @Test
    void testGetEntityById() {
        when(entityMapper.selectById(1L)).thenReturn(testEntity);
        when(metricMapper.selectList(any())).thenReturn(List.of(testMetric));
        when(dimensionMapper.selectList(any())).thenReturn(List.of(testDimension));

        OntEntityDTO result = ontologyService.getEntityById(1L, "zh_CN");
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("test_entity", result.getCode());
        assertEquals(1, result.getMetrics().size());
        assertEquals(1, result.getDimensions().size());
    }

    @Test
    void testListEntities() {
        Page<OntEntity> page = new Page<>();
        page.setRecords(List.of(testEntity));
        page.setTotal(1);
        when(entityMapper.selectPage(any(), any())).thenReturn(page);

        PageRequest pr = new PageRequest();
        pr.setPage(1);
        pr.setPageSize(10);

        PageResult<OntEntityDTO> result = ontologyService.listEntities("sales", "draft", null, pr);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getItems().size());
        assertEquals("test_entity", result.getItems().get(0).getCode());
    }

    @Test
    void testUpdateEntity() {
        when(entityMapper.selectById(1L)).thenReturn(testEntity);
        when(entityMapper.updateById(any(OntEntity.class))).thenReturn(1);

        EntityCreateRequest request = new EntityCreateRequest();
        request.setCode("updated_entity");
        request.setName(Map.of("zh_CN", "更新实体", "en_US", "Updated Entity"));
        request.setDescription(Map.of("zh_CN", "更新描述", "en_US", "Updated"));
        request.setEntityType("business");
        request.setDataDomain("sales");
        request.setOwner("admin");

        OntEntityDTO result = ontologyService.updateEntity(1L, request);
        assertNotNull(result);
        assertEquals("updated_entity", result.getCode());
    }

    @Test
    void testDeleteEntity() {
        when(entityMapper.selectById(1L)).thenReturn(testEntity);
        when(entityMapper.deleteById(1L)).thenReturn(1);
        assertDoesNotThrow(() -> ontologyService.deleteEntity(1L));
    }

    @Test
    void testCreateMetric() {
        MetricCreateRequest request = new MetricCreateRequest();
        request.setCode("test_metric");
        request.setName(Map.of("zh_CN", "测试指标", "en_US", "Test Metric"));
        request.setEntityId(1L);
        request.setMetricType("derived");
        request.setAggMethod("SUM");
        request.setExpression("SUM(amount)");
        request.setOwner("admin");

        when(metricMapper.insert(any(OntMetric.class))).thenAnswer(invocation -> {
            OntMetric m = invocation.getArgument(0);
            m.setId(1L);
            m.setCreatedAt(OffsetDateTime.now());
            m.setUpdatedAt(OffsetDateTime.now());
            return 1;
        });

        OntMetricDTO result = ontologyService.createMetric(request);
        assertNotNull(result);
        assertEquals("test_metric", result.getCode());
        assertEquals("draft", result.getStatus());
        verify(eventPublisher).publishEvent(isA(OntologyServiceImpl.OntologyCreatedEvent.class));
    }

    @Test
    void testListMetricsByEntity() {
        when(metricMapper.selectList(any())).thenReturn(List.of(testMetric));
        List<OntMetricDTO> result = ontologyService.listMetricsByEntity(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test_metric", result.get(0).getCode());
    }

    @Test
    void testCreateDimension() {
        DimensionCreateRequest request = new DimensionCreateRequest();
        request.setCode("test_dim");
        request.setName(Map.of("zh_CN", "测试维度", "en_US", "Test Dimension"));
        request.setEntityId(1L);
        request.setDimType("date");
        request.setTableColumn("order_date");
        request.setHierarchyLevel("1");

        when(dimensionMapper.insert(any(OntDimension.class))).thenAnswer(invocation -> {
            OntDimension d = invocation.getArgument(0);
            d.setId(1L);
            d.setCreatedAt(OffsetDateTime.now());
            d.setUpdatedAt(OffsetDateTime.now());
            return 1;
        });

        OntDimensionDTO result = ontologyService.createDimension(request);
        assertNotNull(result);
        assertEquals("test_dim", result.getCode());
        assertEquals("draft", result.getStatus());
    }

    @Test
    void testListDimensionsByEntity() {
        when(dimensionMapper.selectList(any())).thenReturn(List.of(testDimension));
        List<OntDimensionDTO> result = ontologyService.listDimensionsByEntity(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test_dim", result.get(0).getCode());
    }
}
