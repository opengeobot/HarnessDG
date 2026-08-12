package com.modelhub.shared.web;

import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;

import java.util.List;

/**
 * ETag/If-Match 条件更新（04 §10）。ETag 使用强校验带引号格式。
 */
public final class ETags {

    private ETags() {}

    public static String ofVersion(long version) {
        return "\"" + Long.toHexString(version + 0x5F000000L) + "\"";
    }

    /**
     * 校验 If-Match：缺失返回 428→按契约以 400 VALIDATION_FAILED 表达 required 缺失；
     * 不匹配返回 412 PRECONDITION_FAILED；"*" 恒通过。
     */
    public static void requireMatch(String ifMatchHeader, String currentEtag, String resourceLabel) {
        if (ifMatchHeader == null || ifMatchHeader.isBlank()) {
            throw ApiException.badRequest(resourceLabel + " 的条件更新必须携带 If-Match",
                    List.of(new ApiException.Detail("If-Match", "required")));
        }
        String trimmed = ifMatchHeader.trim();
        if (trimmed.startsWith("W/")) {
            trimmed = trimmed.substring(2);
        }
        if ("*".equals(trimmed)) {
            return;
        }
        if (!trimmed.equals(currentEtag)) {
            throw new ApiException(ErrorCode.PRECONDITION_FAILED,
                    resourceLabel + " 已被修改，If-Match 校验失败，请刷新后重试");
        }
    }
}
