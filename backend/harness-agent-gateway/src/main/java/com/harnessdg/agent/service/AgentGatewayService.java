package com.harnessdg.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentGatewayService {

    private final WebClient.Builder webClientBuilder;

    @Value("${harnessdg.agent.endpoint:http://localhost:8088}")
    private String agentEndpoint;

    public Mono<Map> chat(String sessionId, String message, Map<String, Object> context) {
        return webClientBuilder.build()
                .post()
                .uri(agentEndpoint + "/api/agent/chat")
                .bodyValue(Map.of(
                        "session_id", sessionId,
                        "message", message,
                        "context", context != null ? context : Map.of()
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .doOnError(e -> log.error("Agent chat failed: {}", e.getMessage()));
    }

    public Mono<Map> intentRecognize(String query) {
        return webClientBuilder.build()
                .post()
                .uri(agentEndpoint + "/api/agent/intent")
                .bodyValue(Map.of("query", query))
                .retrieve()
                .bodyToMono(Map.class);
    }
}
