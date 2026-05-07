/**
 * 功能：系统配置变更历史展示对象
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.config.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ConfigHistoryDTO {

    private Long id;

    private String configKey;

    private String oldValue;

    private String newValue;

    private String environment;

    private String changeType;

    private String changedBy;

    private OffsetDateTime changedAt;

    private String comment;
}
