/*
 * 功能: SensitiveDataMasker 单元测试——验证敏感字段、Bearer/JWT 与预签名查询串脱敏。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link SensitiveDataMasker} 离线单元测试。
 */
class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void masksSensitiveFieldNamesEntirely() {
        assertThat(masker.isSensitiveField("Authorization")).isTrue();
        assertThat(masker.maskField("password", "p@ssw0rd")).isEqualTo(SensitiveDataMasker.MASK);
        assertThat(masker.maskField("Cookie", "session=abc")).isEqualTo(SensitiveDataMasker.MASK);
        assertThat(masker.maskField("displayName", "qwen")).isEqualTo("qwen");
    }

    @Test
    void masksBearerTokenWithinText() {
        String masked = masker.maskValue("Authorization: Bearer abc.def.ghi-token_value");
        assertThat(masked).contains("Bearer " + SensitiveDataMasker.MASK);
        assertThat(masked).doesNotContain("abc.def.ghi-token_value");
    }

    @Test
    void masksCompactJwt() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c3JfMDEifQ.signaturepart";
        assertThat(masker.maskValue("token=" + jwt)).doesNotContain(jwt);
    }

    @Test
    void masksPresignedQueryParameters() {
        String url = "https://minio/bucket/obj?X-Amz-Credential=AKIA123&X-Amz-Signature=deadbeef&page=1";
        String masked = masker.maskValue(url);
        assertThat(masked).doesNotContain("AKIA123");
        assertThat(masked).doesNotContain("deadbeef");
        assertThat(masked).contains("page=1");
    }
}
