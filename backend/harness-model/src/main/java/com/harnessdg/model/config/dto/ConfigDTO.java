/**
 * 功能：系统配置展示对象
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.config.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class ConfigDTO {

    private Long id;

    private String configKey;

    private String configValue;

    private String valueType;

    private String category;

    private Map<String, String> description;

    private Boolean isEncrypted;

    private Boolean isReadonly;

    private String environment;

    private String updatedBy;

    private OffsetDateTime updatedAt;
}
