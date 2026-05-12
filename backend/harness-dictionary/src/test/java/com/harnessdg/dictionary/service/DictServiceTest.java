/**
 * 功能：字典服务单元测试
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.dictionary.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.dictionary.cache.DictCacheManager;
import com.harnessdg.dictionary.mapper.SysDictGroupMapper;
import com.harnessdg.dictionary.mapper.SysDictItemMapper;
import com.harnessdg.dictionary.service.impl.DictServiceImpl;
import com.harnessdg.model.dict.dto.DictGroupDTO;
import com.harnessdg.model.dict.dto.DictItemDTO;
import com.harnessdg.model.dict.entity.SysDictGroup;
import com.harnessdg.model.dict.entity.SysDictItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @Mock private SysDictGroupMapper groupMapper;
    @Mock private SysDictItemMapper itemMapper;
    @Mock private DictCacheManager cacheManager;

    @InjectMocks private DictServiceImpl dictService;

    private SysDictGroup testGroup;
    private SysDictItem testItem;

    @BeforeEach
    void setUp() {
        testGroup = new SysDictGroup();
        testGroup.setId(1L);
        testGroup.setCode("task_type");
        testGroup.setName(Map.of("zh_CN", "任务类型", "en_US", "Task Type"));
        testGroup.setDescription(Map.of("zh_CN", "数据平台任务分类", "en_US", "Task category"));
        testGroup.setStatus("active");
        testGroup.setIsTree(false);
        testGroup.setIsMultiple(false);
        testGroup.setIsEditable(true);
        testGroup.setCreatedAt(OffsetDateTime.now());
        testGroup.setUpdatedAt(OffsetDateTime.now());

        testItem = new SysDictItem();
        testItem.setId(1L);
        testItem.setGroupCode("task_type");
        testItem.setCode("DATA_INGESTION");
        testItem.setValue("data_ingestion");
        testItem.setLabel(Map.of("zh_CN", "数据接入", "en_US", "Data Ingestion"));
        testItem.setStatus("active");
        testItem.setCreatedAt(OffsetDateTime.now());
        testItem.setUpdatedAt(OffsetDateTime.now());
    }

    /**
     * 测试：获取字典分组列表
     */
    @Test
    void testListGroups() {
        when(groupMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(testGroup));

        List<DictGroupDTO> result = dictService.listGroups(null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("task_type", result.get(0).getCode());
    }

    /**
     * 测试：按编码获取字典分组
     */
    @Test
    void testGetGroupByCode() {
        when(groupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testGroup);

        DictGroupDTO result = dictService.getGroupByCode("task_type", "zh_CN");

        assertNotNull(result);
        assertEquals("task_type", result.getCode());
        assertEquals("任务类型", result.getResolvedName());
    }

    /**
     * 测试：获取字典项列表
     */
    @Test
    void testListItems() {
        when(cacheManager.getItems("task_type")).thenReturn(null);
        when(itemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(testItem));

        List<DictItemDTO> result = dictService.listItems("task_type", "zh_CN");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("DATA_INGESTION", result.get(0).getCode());
        assertEquals("数据接入", result.get(0).getResolvedLabel());
    }
}
