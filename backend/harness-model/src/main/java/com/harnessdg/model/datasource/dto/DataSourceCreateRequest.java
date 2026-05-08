/**
 * 功能：数据源创建请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class DataSourceCreateRequest {

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "sourceType 不能为空")
    private String sourceType;

    private Map<String, Object> connectionConfig;

    private Map<String, String> description;

    private String owner;

    private Map<String, Object> tags;
}
