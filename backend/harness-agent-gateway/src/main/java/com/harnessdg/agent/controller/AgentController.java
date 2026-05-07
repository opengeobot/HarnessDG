package com.harnessdg.agent.controller;

import com.harnessdg.agent.service.AgentGatewayService;
import com.harnessdg.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentGatewayService agentGatewayService;

    @PostMapping("/chat")
    public Mono<R<Map>> chat(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.getOrDefault("sessionId", "");
        String message = (String) body.getOrDefault("message", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.get("context");
        return agentGatewayService.chat(sessionId, message, context)
                .map(R::ok);
    }

    @PostMapping("/intent")
    public Mono<R<Map>> recognizeIntent(@RequestBody Map<String, String> body) {
        return agentGatewayService.intentRecognize(body.get("query"))
                .map(R::ok);
    }
}
