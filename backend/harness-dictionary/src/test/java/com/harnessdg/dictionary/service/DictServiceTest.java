package com.harnessdg.dictionary.service;

import com.harnessdg.model.dict.dto.DictGroupDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 功能：字典服务单元测试（示范）
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class DictServiceTest {

    @Autowired
    private DictService dictService;

    @Test
    void testListGroups() {
        // 测试获取字典分组列表
        List<DictGroupDTO> groups = dictService.listGroups(null, null);
        assertNotNull(groups);
        // 初始数据应该至少有 task_type 等系统字典
        assertFalse(groups.isEmpty());
    }

    @Test
    void testGetGroupByCode() {
        // 测试按编码获取字典分组
        DictGroupDTO group = dictService.getGroupByCode("task_type", "zh_CN");
        assertNotNull(group);
        assertEquals("task_type", group.getCode());
        assertNotNull(group.getResolvedName());
    }

    @Test
    void testListItems() {
        // 测试获取字典项列表
        var items = dictService.listItems("task_type", "zh_CN");
        assertNotNull(items);
        assertFalse(items.isEmpty());
    }
}
