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
 * v1 默认扫描器：无外部扫描引擎时直接放行（clean, policyVersion=1）。
 * 部署可替换为真实恶意内容扫描实现；替换后异常即 fail closed。
 */
@Component
class DefaultContentScanner implements ContentScanner {

    @Override
    public ScanResult scan(String objectKey, long sizeBytes) {
        return new ScanResult("clean", 1);
    }
}
