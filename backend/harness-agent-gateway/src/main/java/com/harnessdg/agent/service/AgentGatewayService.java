package com.harnessdg.agent.service;

import com.harnessdg.common.log.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentGatewayService {

    private final WebClient.Builder webClientBuilder;

    @Value("${harnessdg.agent.endpoint:http://localhost:8089}")  // Python Agent 服务
    private String agentEndpoint;

    @Value("${harnessdg.agent.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${harnessdg.agent.retry-count:2}")
    private int retryCount;

    public Mono<Map> chat(String sessionId, String message, Map<String, Object> context) {
        String traceId = TraceContext.getTraceId();

        return webClientBuilder.build()
                .post()
                .uri(agentEndpoint + "/api/agent/chat")
                .header("X-Trace-Id", traceId != null ? traceId : "")
                .bodyValue(Map.of(
                        "session_id", sessionId,
                        "message", message,
                        "context", context != null ? context : Map.of()
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .retryWhen(Retry.backoff(retryCount, Duration.ofSeconds(1))
                        .filter(this::isRetryableError))
                .doOnSuccess(response -> log.debug("Agent chat response for session: {}", sessionId))
                .doOnError(e -> log.error("Agent chat failed: traceId={}, error={}", traceId, e.getMessage()))
                .onErrorResume(e -> buildFallbackResponse(sessionId, e));
    }

    public Mono<Map> intentRecognize(String query) {
        String traceId = TraceContext.getTraceId();

        return webClientBuilder.build()
                .post()
                .uri(agentEndpoint + "/api/agent/intent")
                .header("X-Trace-Id", traceId != null ? traceId : "")
                .bodyValue(Map.of("query", query))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .retryWhen(Retry.backoff(retryCount, Duration.ofSeconds(1))
                        .filter(this::isRetryableError))
                .doOnError(e -> log.error("Intent recognition failed: traceId={}, error={}", traceId, e.getMessage()))
                .onErrorResume(e -> Mono.just(Map.of(
                        "intent", "unknown",
                        "confidence", 0.0,
                        "entities", Map.of(),
                        "error", "Intent recognition failed: " + e.getMessage()
                )));
    }

    /**
     * 阻塞式聊天调用(用于 Servlet 控制器)
     */
    public Map<String, Object> chatBlocking(String sessionId, String message, Map<String, Object> context) {
        return chat(sessionId, message, context).block();
    }

    /**
     * 阻塞式意图识别调用(用于 Servlet 控制器)
     */
    public Map<String, Object> intentRecognizeBlocking(String query) {
        return intentRecognize(query).block();
    }

    /**
     * 判断是否可重试的错误
     */
    private boolean isRetryableError(Throwable e) {
        if (e instanceof WebClientResponseException wcre) {
            int statusCode = wcre.getStatusCode().value();
            return statusCode >= 500 && statusCode < 600;
        }
        return true;
    }

    /**
     * 构建降级响应
     */
    private Mono<Map> buildFallbackResponse(String sessionId, Throwable e) {
        return Mono.just(Map.of(
                "reply", "AI 服务暂时不可用，请稍后再试。",
                "session_id", sessionId,
                "error", e.getMessage()
        ));
    }
}
