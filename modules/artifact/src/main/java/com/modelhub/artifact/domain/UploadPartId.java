package com.modelhub.artifact.domain;

import java.io.Serializable;
import java.util.Objects;

/** 分片投影复合主键（upload_parts：upload_session_id + part_number）。 */
public class UploadPartId implements Serializable {

    private Long uploadSessionId;
    private Integer partNumber;

    public UploadPartId() { }

    public UploadPartId(Long uploadSessionId, Integer partNumber) {
        this.uploadSessionId = uploadSessionId;
        this.partNumber = partNumber;
    }

    public Long getUploadSessionId() { return uploadSessionId; }
    public void setUploadSessionId(Long uploadSessionId) { this.uploadSessionId = uploadSessionId; }
    public Integer getPartNumber() { return partNumber; }
    public void setPartNumber(Integer partNumber) { this.partNumber = partNumber; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UploadPartId that)) return false;
        return Objects.equals(uploadSessionId, that.uploadSessionId)
                && Objects.equals(partNumber, that.partNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uploadSessionId, partNumber);
    }
}
