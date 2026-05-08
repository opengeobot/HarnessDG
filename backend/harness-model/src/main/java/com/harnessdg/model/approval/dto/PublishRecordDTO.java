/**
 * 功能：发布记录 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.approval.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PublishRecordDTO {

    private Long id;

    private String businessType;

    private String businessId;

    private Integer version;

    private String publishedBy;

    private OffsetDateTime publishedAt;

    private Long approvalInstanceId;
}
