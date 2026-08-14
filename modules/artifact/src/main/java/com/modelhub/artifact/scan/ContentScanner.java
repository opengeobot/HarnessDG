package com.modelhub.artifact.scan;

import org.springframework.stereotype.Component;

/**
 * 内容扫描接口（05 §11）：scan_status 未通过时对象保持 quarantine，不可下载或预览。
 * error/不可用时由 Job 重试且默认 fail closed，禁止先发布后补扫（05 §6.3 第 7 条）。
 */
public interface ContentScanner {

    /** clean 或 rejected；扫描器不可用时抛异常（fail closed，走重试）。 */
    record ScanResult(String status, int policyVersion) {}

    ScanResult scan(String objectKey, long sizeBytes);
}

/**
 * v1 默认扫描器：fail-closed — 未配置真实扫描器时拒绝发布（05 §6.3 第 7 条）。
 * 部署时必须配置真实扫描器实现（如 ClamAV）替换此默认实现。
 */
@Component
class DefaultContentScanner implements ContentScanner {

    @Override
    public ScanResult scan(String objectKey, long sizeBytes) {
        throw new IllegalStateException("Content scanner not configured: fail closed");
    }
}
