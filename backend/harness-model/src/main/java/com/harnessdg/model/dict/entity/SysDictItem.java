package com.harnessdg.model.dict.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_dict_item", autoResultMap = true)
public class SysDictItem extends BaseEntity {

    private String groupCode;

    private Long parentId;

    private String code;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> label;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    private String value;

    private String icon;

    private String color;

    private Integer sortOrder;

    private Boolean isDefault;

    private Boolean isSystem;

    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;
}
