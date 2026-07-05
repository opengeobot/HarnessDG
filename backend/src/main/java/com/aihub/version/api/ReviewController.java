package com.aihub.version.api;

import com.aihub.version.application.PublishApplicationService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布审批 REST 控制器。
 *
 * <p>提供提交发布请求与审批决策接口。
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final PublishApplicationService publishApplicationService;

    public ReviewController(PublishApplicationService publishApplicationService) {
        this.publishApplicationService = publishApplicationService;
    }

    /**
     * 提交发布请求。
     */
    @PostMapping("/versions/{versionId}/publish-requests")
    public Map<String, String> submitPublishRequest(@PathVariable String versionId) {
        String requestId = publishApplicationService.submitPublishRequest(versionId);
        return Map.of("requestId", requestId);
    }

    /**
     * 提交审批决策。
     */
    @PostMapping("/publish-requests/{requestId}/decisions")
    public Map<String, String> submitDecision(@PathVariable String requestId,
                                              @RequestBody DecisionRequest body) {
        publishApplicationService.submitDecision(requestId, body.decision(), body.comments());
        return Map.of("status", "submitted");
    }

    /**
     * 弃用版本。
     */
    @PostMapping("/versions/{versionId}/deprecate")
    public Map<String, String> deprecateVersion(@PathVariable String versionId) {
        publishApplicationService.deprecateVersion(versionId);
        return Map.of("status", "deprecated");
    }

    /**
     * 归档版本。
     */
    @PostMapping("/versions/{versionId}/archive")
    public Map<String, String> archiveVersion(@PathVariable String versionId) {
        publishApplicationService.archiveVersion(versionId);
        return Map.of("status", "archived");
    }

    public record DecisionRequest(String decision, String comments) {}
}
