/**
 * 功能：字典导入导出载体，包含分组元信息与全部字典项
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.dict.dto;

import lombok.Data;

import java.util.List;

@Data
public class DictExportData {

    /** 字典分组元信息 */
    private DictGroupCreateRequest group;

    /** 分组下的所有字典项（支持树形关系，通过 parentId 维护层级） */
    private List<DictItemCreateRequest> items;

    /** 导出时的版本号，便于未来兼容 */
    private String version = "1.0";

    /** 导出时间戳（毫秒） */
    private Long exportedAt;
}
