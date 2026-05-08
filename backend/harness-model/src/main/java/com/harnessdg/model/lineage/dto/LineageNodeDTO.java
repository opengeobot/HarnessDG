/**
 * 功能：血缘节点 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.lineage.dto;

import lombok.Data;

import java.util.Map;

@Data
public class LineageNodeDTO {

    private Long id;

    private String nodeType;

    private String nodeKey;

    private Map<String, String> name;

    private Long entityId;

    private Long metricId;

    private String dataSource;

    private Map<String, Object> extra;
}
