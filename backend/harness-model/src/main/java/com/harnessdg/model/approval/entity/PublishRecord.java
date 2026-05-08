package com.harnessdg.model.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 功能：发布记录 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov_publish_record")
public class PublishRecord extends BaseEntity {

    private String businessType;

    private String businessId;

    private Integer version;

    private String publishedBy;

    private OffsetDateTime publishedAt;

    private Long approvalInstanceId;
}
