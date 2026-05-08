package com.harnessdg.agent.controller;

import com.harnessdg.agent.service.AgentGatewayService;
import com.harnessdg.common.response.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentGatewayService agentGatewayService;

    @PostMapping("/chat")
    public R<Map> chat(@RequestBody Map<String, Object> body) {
        try {
            String sessionId = (String) body.getOrDefault("sessionId", "");
            String message = (String) body.getOrDefault("message", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) body.get("context");
            
            // 使用阻塞式调用
            Map<String, Object> result = agentGatewayService.chatBlocking(sessionId, message, context);
            return R.ok(result);
        } catch (Exception e) {
            log.error("Agent chat failed", e);
            return R.fail(com.harnessdg.common.response.ErrorCode.AGENT_ERROR);
        }
    }

    @PostMapping("/intent")
    public R<Map> recognizeIntent(@RequestBody Map<String, String> body) {
        try {
            Map<String, Object> result = agentGatewayService.intentRecognizeBlocking(body.get("query"));
            return R.ok(result);
        } catch (Exception e) {
            log.error("Intent recognition failed", e);
            return R.fail(com.harnessdg.common.response.ErrorCode.AGENT_ERROR);
        }
    }
}
