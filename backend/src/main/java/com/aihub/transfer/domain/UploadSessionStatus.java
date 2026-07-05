package com.aihub.transfer.domain;

/**
 * 上传会话状态枚举。
 */
public enum UploadSessionStatus {
    OPEN,
    COMMITTING,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
