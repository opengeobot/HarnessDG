/**
 * 功能：系统配置实体
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.config.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_config", autoResultMap = true)
public class SysConfig extends BaseEntity {

    private String configKey;

    private String configValue;

    /** string / number / boolean / json */
    private String valueType;

    private String category;

    @TableField(value = "description", typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    private Boolean isEncrypted;

    private Boolean isReadonly;

    /** all / dev / test / prod */
    private String environment;
}
