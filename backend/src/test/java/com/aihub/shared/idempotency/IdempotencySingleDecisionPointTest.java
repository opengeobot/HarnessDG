/*
 * 功能: 幂等判定点唯一性静态门禁——断言全仓库只有 IdempotencyService 一处幂等判定。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 幂等判定点唯一性门禁。
 *
 * <p>历史缺陷：{@code IdempotencyInterceptor} 曾被注册在 {@code /api/v1/**}，
 * 在 Controller 之前命中已有记录即直接回放首次响应并 {@code return false}，
 * **从不比对 request_fingerprint**，使 {@code IdempotencyService} 的摘要比对
 * （{@code IDEMPOTENCY_KEY_CONFLICT}，409）在 HTTP 层永不可达。
 *
 * <p>本测试固化「全局只有一处幂等判定」这一约束：
 * <ol>
 *   <li>{@code HandlerInterceptor} 的实现类不得引用幂等存储或幂等记录；</li>
 *   <li>不得存在类名为 {@code *IdempotencyInterceptor} / {@code *IdempotencyWebConfiguration}
 *       的源码；</li>
 *   <li>幂等复用标志 {@code X-Idempotency-Replayed} 不得由拦截器写入。</li>
 * </ol>
 *
 * <p>本测试不依赖 Docker。
 */
class IdempotencySingleDecisionPointTest {

    /** 拦截器中若同时出现这些符号，说明它又在 Controller 之前介入幂等判定。 */
    private static final List<String> STORE_SYMBOLS = List.of(
            "IdempotencyStore", "IdempotencyRecord", "IDEMPOTENCY_KEY_HEADER");

    private static final Pattern INTERCEPTOR_CLASS =
            Pattern.compile("class\\s+\\w+\\s+implements\\s+HandlerInterceptor");

    @Test
    void noHandlerInterceptorParticipatesInIdempotency() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : javaSources("src/main/java/com/aihub")) {
            String content = Files.readString(source);
            if (!INTERCEPTOR_CLASS.matcher(content).find()) {
                continue;
            }
            for (String symbol : STORE_SYMBOLS) {
                if (content.contains(symbol)) {
                    offenders.add(source.getFileName() + " -> " + symbol);
                }
            }
        }

        assertThat(offenders)
                .as("HandlerInterceptor 不得参与幂等判定（会出现抢先回放，使摘要比对不可达）：%s",
                        offenders)
                .isEmpty();
    }

    @Test
    void idempotencyInterceptorSourcesAreGone() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path source : javaSources("src/main/java/com/aihub")) {
            String name = source.getFileName().toString();
            if (name.endsWith("IdempotencyInterceptor.java")
                    || name.endsWith("IdempotencyWebConfiguration.java")) {
                found.add(name);
            }
        }

        assertThat(found)
                .as("抢先回放的全局幂等拦截器已被移除，不得重新引入：%s", found)
                .isEmpty();
    }

    @Test
    void replayHeaderIsNotWrittenByAnyInterceptor() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : javaSources("src/main/java/com/aihub")) {
            String content = Files.readString(source);
            if (INTERCEPTOR_CLASS.matcher(content).find()
                    && content.contains("X-Idempotency-Replayed")) {
                offenders.add(source.getFileName().toString());
            }
        }

        assertThat(offenders)
                .as("回放响应头只能由主动调用幂等服务的一方写出，不得由拦截器写出：%s", offenders)
                .isEmpty();
    }

    @Test
    void idempotencyServiceStillComparesFingerprint() throws IOException {
        Path service = resolve("src/main/java/com/aihub/job/application/IdempotencyService.java");
        String content = Files.readString(service);

        assertThat(content)
                .as("IdempotencyService 必须比对既有记录的请求摘要并抛 IDEMPOTENCY_KEY_CONFLICT")
                .contains("requestFingerprint()")
                .contains("IDEMPOTENCY_KEY_CONFLICT");
    }

    private List<Path> javaSources(String relativeDir) throws IOException {
        Path dir = resolve(relativeDir);
        assertThat(Files.isDirectory(dir))
                .as("未能定位源码目录：%s", dir.toAbsolutePath())
                .isTrue();
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private Path resolve(String relativePath) {
        Path path = Paths.get(relativePath);
        if (Files.exists(path)) {
            return path;
        }
        // 兼容从仓库根目录运行测试的工作目录
        return Paths.get("backend", relativePath);
    }
}
