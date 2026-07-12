/*
 * 功能: 上传内容安全测试（NFR-SEC-007）——路径穿越拒绝、超大文件拒绝、
 *       空字节注入、符号链接猜测、状态机非法转换防护。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionStatus;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 上传内容安全测试。
 */
@DisplayName("NFR-SEC-007: 上传内容安全")
class UploadContentSecurityTest {

    // ── 超大文件拒绝 ─────────────────────────────────────────────

    @Nested
    @DisplayName("超大文件拒绝 (NFR-CAP-001)")
    class OversizedFileRejection {

        @Test
        void rejectSessionExceeding20GiB() {
            long oversized = UploadSession.MAX_SESSION_BYTES + 1;
            assertThatThrownBy(() -> UploadSession.create(
                    "upl_test1", "ast_1", "ver_1", "usr_1",
                    oversized, 1, Instant.now().plusSeconds(7200)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds limit");
        }

        @Test
        void acceptSessionExactlyAt20GiB() {
            UploadSession session = UploadSession.create(
                    "upl_test2", "ast_1", "ver_1", "usr_1",
                    UploadSession.MAX_SESSION_BYTES, 1,
                    Instant.now().plusSeconds(7200));
            assertThat(session.totalBytes()).isEqualTo(UploadSession.MAX_SESSION_BYTES);
            assertThat(session.status()).isEqualTo(UploadSessionStatus.OPEN);
        }

        @Test
        void rejectNegativeTotalBytes() {
            assertThatThrownBy(() -> UploadSession.create(
                    "upl_test3", "ast_1", "ver_1", "usr_1",
                    -1, 1, Instant.now().plusSeconds(7200)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        void rejectNegativeFileCount() {
            assertThatThrownBy(() -> UploadSession.create(
                    "upl_test4", "ast_1", "ver_1", "usr_1",
                    1024, -1, Instant.now().plusSeconds(7200)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }
    }

    // ── 路径穿越拒绝 ─────────────────────────────────────────────

    @Nested
    @DisplayName("路径穿越拒绝")
    class PathTraversalRejection {

        /**
         * 通过反射调用 UploadMaterializeJobHandler.validatePath，
         * 验证各类恶意路径被拒绝。
         */
        private void assertPathRejected(String maliciousPath) throws Exception {
            Class<?> handlerClass = Class.forName(
                    "com.aihub.transfer.infrastructure.UploadMaterializeJobHandler");
            Method validatePath = handlerClass.getDeclaredMethod("validatePath", String.class);
            validatePath.setAccessible(true);
            Object handler = null;
            try {
                // 尝试无参构造（如果有的话），否则通过 Unsafe 创建实例
                var unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafe.setAccessible(true);
                handler = ((sun.misc.Unsafe) unsafe.get(null))
                        .allocateInstance(handlerClass);
            } catch (Exception ignored) {
                // 如果无法实例化，直接测试静态方法
            }
            Object finalHandler = handler;
            assertThatThrownBy(() -> {
                try {
                    validatePath.invoke(finalHandler, maliciousPath);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            }).isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "拒绝路径穿越: {0}")
        @ValueSource(strings = {
                "../etc/passwd",
                "foo/../../../etc/shadow",
                "/absolute/path",
                "\\windows\\system32",
                "normal/file\0hidden.txt",
                "~/bashrc",
                "link->/etc/passwd",
        })
        void rejectMaliciousPaths(String path) throws Exception {
            assertPathRejected(path);
        }

        @Test
        void rejectNullPath() throws Exception {
            assertPathRejected(null);
        }

        @Test
        void rejectBlankPath() throws Exception {
            assertPathRejected("   ");
        }

        @Test
        void acceptNormalRelativePath() throws Exception {
            Class<?> handlerClass = Class.forName(
                    "com.aihub.transfer.infrastructure.UploadMaterializeJobHandler");
            Method validatePath = handlerClass.getDeclaredMethod("validatePath", String.class);
            validatePath.setAccessible(true);
            var unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafe.setAccessible(true);
            Object handler = ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(handlerClass);

            // 正常路径不应抛异常
            validatePath.invoke(handler, "model/weights/model.bin");
            validatePath.invoke(handler, "data/train.parquet");
        }
    }

    // ── 状态机非法转换防护 ─────────────────────────────────────────

    @Nested
    @DisplayName("上传会话状态机防护")
    class StateMachineProtection {

        @Test
        void cannotCommitNonOpenSession() {
            UploadSession session = UploadSession.create(
                    "upl_sm1", "ast_1", "ver_1", "usr_1",
                    1024, 1, Instant.now().plusSeconds(7200));
            session.commit();
            assertThatThrownBy(session::commit)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void cannotCancelCompletedSession() {
            UploadSession session = UploadSession.create(
                    "upl_sm2", "ast_1", "ver_1", "usr_1",
                    1024, 1, Instant.now().plusSeconds(7200));
            session.commit();
            session.markProcessing();
            session.complete();
            assertThatThrownBy(session::cancel)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void expiredSessionDetected() {
            UploadSession session = UploadSession.create(
                    "upl_sm3", "ast_1", "ver_1", "usr_1",
                    1024, 1, Instant.now().minusSeconds(1));
            assertThat(session.isExpired()).isTrue();
        }
    }
}
