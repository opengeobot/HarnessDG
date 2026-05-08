package com.harnessdg.model.relation.dto;

import lombok.Data;

import java.util.Map;

/**
 * 功能：实体关系 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
public class RelationDTO {

    private Long id;
    private Long sourceEntityId;
    private String sourceEntityCode;
    private Long targetEntityId;
    private String targetEntityCode;
    private String relationType;
    private Map<String, String> name;
    private Map<String, String> description;
    private String cardinality;
    private Map<String, Object> extra;
    private String resolvedName;
    private String status;
}
