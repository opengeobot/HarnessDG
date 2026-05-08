/**
 * 功能：血缘边 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.lineage.dto;

import lombok.Data;

import java.util.Map;

@Data
public class LineageEdgeDTO {

    private Long id;

    private Long sourceNodeId;

    private Long targetNodeId;

    private String edgeType;

    private String transformLogic;

    private Map<String, Object> extra;
}
