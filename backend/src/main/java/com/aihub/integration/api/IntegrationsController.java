/*
 * 功能: Agent 接入配置包 REST 适配器。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.integration.api;

import com.aihub.integration.application.AgentBundleApplicationService;
import com.aihub.integration.application.AgentBundleView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集成配置 REST 控制器。
 */
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationsController {

    private final AgentBundleApplicationService agentBundleService;

    public IntegrationsController(AgentBundleApplicationService agentBundleService) {
        this.agentBundleService = agentBundleService;
    }

    /** 返回 Agent 接入配置包。 */
    @GetMapping("/agent-bundle")
    public AgentBundleView getAgentBundle() {
        return agentBundleService.getAgentBundle();
    }
}
