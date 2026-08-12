package com.modelhub.artifact.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 分片状态投影（05 §6.2）：仅是对象存储 ListParts 的缓存，不得比 ListParts 更权威。 */
@Entity
@Table(name = "upload_parts")
@IdClass(UploadPartId.class)
public class UploadPartEntity {

    @Id
    @Column(name = "upload_session_id", nullable = false)
    private Long uploadSessionId;

    @Id
    @Column(name = "part_number", nullable = false)
    private Integer partNumber;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column
    private String etag;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getUploadSessionId() { return uploadSessionId; }
    public void setUploadSessionId(Long uploadSessionId) { this.uploadSessionId = uploadSessionId; }
    public Integer getPartNumber() { return partNumber; }
    public void setPartNumber(Integer partNumber) { this.partNumber = partNumber; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
