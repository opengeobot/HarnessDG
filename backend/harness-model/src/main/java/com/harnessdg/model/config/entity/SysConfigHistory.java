/**
 * 功能：系统配置变更历史实体
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.config.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config_history")
public class SysConfigHistory extends BaseEntity {

    private String configKey;

    private String oldValue;

    private String newValue;

    private String environment;

    /** create / update / delete */
    private String changeType;

    private String changedBy;

    private OffsetDateTime changedAt;

    private String comment;
}
