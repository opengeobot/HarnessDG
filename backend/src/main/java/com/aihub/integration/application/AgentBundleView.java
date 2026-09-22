/*
 * 功能: Agent 接入配置包视图。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.integration.application;

import java.util.List;
import java.util.Map;

/**
 * Agent 接入配置包。
 */
public record AgentBundleView(String mcpEndpointUrl,
                              String openApiUrl,
                              List<SkillTemplate> skillTemplates,
                              List<ExampleCommand> exampleCommands) {

    /** Skill 模板条目。 */
    public record SkillTemplate(String name, String description, String path) {}

    /** 示例命令。 */
    public record ExampleCommand(String label, String command) {}

    /** 从配置构建视图。 */
    public static AgentBundleView of(String baseUrl,
                                       List<SkillTemplate> skills,
                                       List<ExampleCommand> commands) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new AgentBundleView(
                normalized + "/api/v1/mcp",
                normalized + "/v3/api-docs",
                skills,
                commands);
    }
}
