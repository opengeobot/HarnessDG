/**
 * 功能：数据源 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.datasource.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DataSourceDTO {

    private Long id;

    private String name;

    private String code;

    private String sourceType;

    private Map<String, Object> connectionConfig;

    private Map<String, String> description;

    private String status;

    private String owner;

    private Map<String, Object> tags;
}
