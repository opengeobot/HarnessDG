/*
 * 功能: Agent 接入配置包应用服务。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.integration.application;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.integration.application.AgentBundleView.ExampleCommand;
import com.aihub.integration.application.AgentBundleView.SkillTemplate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 接入配置包服务。
 */
@Service
public class AgentBundleApplicationService {

    private final AuthorizationService authorizationService;
    private final String publicBaseUrl;

    public AgentBundleApplicationService(
            AuthorizationService authorizationService,
            @Value("${aihub.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.authorizationService = authorizationService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 返回 Agent 接入配置包。 */
    public AgentBundleView getAgentBundle() {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_READ);
        List<SkillTemplate> skills = List.of(
                new SkillTemplate("asset-search", "Search and inspect platform assets",
                        "docs/ai-spec/skills/asset-search.md"),
                new SkillTemplate("version-publish", "Draft, validate and publish versions",
                        "docs/ai-spec/skills/version-publish.md"),
                new SkillTemplate("upload-resume", "Multipart upload with session recovery",
                        "docs/ai-spec/skills/upload-resume.md"));
        List<ExampleCommand> commands = List.of(
                new ExampleCommand("Login", "aih login --username <user> --password <pass>"),
                new ExampleCommand("Search datasets", "aih dataset search --query medical"),
                new ExampleCommand("Inspect asset", "aih dataset inspect --id ast_xxx"),
                new ExampleCommand("Push upload", "aih dataset push --asset ast_xxx --version ver_xxx --dir ./data"),
                new ExampleCommand("Resume upload", "aih dataset resume --session upl_xxx --dir ./data"),
                new ExampleCommand("Verify version", "aih dataset verify --asset ast_xxx --version ver_xxx --dir ./data"));
        return AgentBundleView.of(publicBaseUrl, skills, commands);
    }
}
