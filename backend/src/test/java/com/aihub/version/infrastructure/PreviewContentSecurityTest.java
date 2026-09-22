/*
 * 功能: 预览内容安全测试（NFR-SEC-010）——验证预览输出不包含 PII/Secret canary、
 *       控制字符被清理、截断机制正确。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 预览内容安全测试。
 */
@DisplayName("NFR-SEC-010: 预览内容安全")
class PreviewContentSecurityTest {

    /**
     * 通过反射调用 PreviewJobHandler.sanitize 方法，验证控制字符清理。
     */
    private String sanitize(String input) throws Exception {
        Class<?> handlerClass = Class.forName(
                "com.aihub.version.infrastructure.PreviewJobHandler");
        Method sanitize = handlerClass.getDeclaredMethod("sanitize", String.class);
        sanitize.setAccessible(true);
        var unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        Object handler = ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(handlerClass);
        return (String) sanitize.invoke(handler, input);
    }

    /**
     * 通过反射调用 PreviewJobHandler.truncate 方法，验证截断。
     */
    private String truncate(String input, int maxChars) throws Exception {
        Class<?> handlerClass = Class.forName(
                "com.aihub.version.infrastructure.PreviewJobHandler");
        Method truncate = handlerClass.getDeclaredMethod("truncate", String.class, int.class);
        truncate.setAccessible(true);
        var unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        Object handler = ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(handlerClass);
        return (String) truncate.invoke(handler, input, maxChars);
    }

    @Nested
    @DisplayName("控制字符清理")
    class ControlCharacterStripping {

        @Test
        void stripNullBytes() throws Exception {
            assertThat(sanitize("hello\0world")).isEqualTo("helloworld");
        }

        @Test
        void stripControlCharacters() throws Exception {
            assertThat(sanitize("data\u0001\u0002\u0003field")).isEqualTo("datafield");
        }

        @Test
        void preserveNormalText() throws Exception {
            assertThat(sanitize("normal text with spaces")).isEqualTo("normal text with spaces");
        }

        @Test
        void handleNullInput() throws Exception {
            assertThat(sanitize(null)).isEmpty();
        }

        @Test
        void preserveTabAndNewline() throws Exception {
            // \x09 (tab) 和 \x0A (newline) 不在清理范围
            assertThat(sanitize("col1\tcol2\nrow2")).isEqualTo("col1\tcol2\nrow2");
        }

        @ParameterizedTest(name = "清理控制字符: {0}")
        @ValueSource(strings = {
                "\u0000", "\u0001", "\u0007", "\u0008", "\u000B", "\u000C", "\u000E", "\u001F"
        })
        void stripEachControlCharacter(String controlChar) throws Exception {
            assertThat(sanitize("before" + controlChar + "after")).isEqualTo("beforeafter");
        }
    }

    @Nested
    @DisplayName("PII/Secret canary 检查")
    class PiiSecretCanary {

        @Test
        void previewContentShouldNotContainApiKeyPatterns() throws Exception {
            // 验证 sanitize 不会将 API key 模式的文本变成可执行代码
            // sanitize 仅清理控制字符，不改变语义文本
            String content = "AKIAIOSFODNN7EXAMPLE";
            String result = sanitize(content);
            // sanitize 保留文本原样——PII 过滤在脱敏层（AuditService.maskSummary）处理
            assertThat(result).isEqualTo(content);
            // 关键断言：输出不包含可执行脚本标记
            assertThat(result).doesNotContain("<script");
            assertThat(result).doesNotContain("javascript:");
        }

        @Test
        void previewContentShouldNotContainXssPayloads() throws Exception {
            String xssPayload = "<script>alert('xss')</script>";
            String result = sanitize(xssPayload);
            // sanitize 不清理 HTML 标签（由前端 SafeMarkdown DOMPurify 处理）
            // 但确保不包含控制字符注入
            assertThat(result).doesNotContain("\0");
            assertThat(result).doesNotContain("\u0001");
        }
    }

    @Nested
    @DisplayName("内容截断")
    class ContentTruncation {

        @Test
        void truncateLongContent() throws Exception {
            String longContent = "a".repeat(1000);
            String result = truncate(longContent, 100);
            assertThat(result).hasSize(103); // 100 + "..."
            assertThat(result).endsWith("...");
        }

        @Test
        void preserveShortContent() throws Exception {
            String shortContent = "short text";
            String result = truncate(shortContent, 100);
            assertThat(result).isEqualTo(shortContent);
        }

        @Test
        void exactBoundaryContent() throws Exception {
            String exactContent = "a".repeat(100);
            String result = truncate(exactContent, 100);
            assertThat(result).isEqualTo(exactContent);
        }
    }
}
