/**
 * 功能：更新系统配置请求
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.config.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ConfigUpdateRequest {

    private String configValue;

    private String valueType;

    private String category;

    private Map<String, String> description;

    private Boolean isEncrypted;

    private Boolean isReadonly;

    private String environment;

    private String comment;
}
