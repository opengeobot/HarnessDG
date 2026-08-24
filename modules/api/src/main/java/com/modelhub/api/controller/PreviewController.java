package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.artifact.service.PreviewService;
import com.modelhub.artifact.service.PreviewService.DownloadView;
import com.modelhub.artifact.service.PreviewService.GetResult;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 预览端点（04 Preview / 05 §5）：
 * - GET preview：ready → 200 PreviewEnvelope；pending/running → 202 JobEnvelope；
 * - POST preview-jobs：Idempotency-Key 必填，202 JobEnvelope（同 commit 幂等复用）；
 * - preview/download：短周期预签名 URL（契约方法为 GET，同时接受 POST 兼容触发式调用）。
 * 读端点匿名可达（public 仓库），写端点要求认证。
 */
@RestController
public class PreviewController {

    private final PreviewService previews;

    public PreviewController(PreviewService previews) {
        this.previews = previews;
    }

    @GetMapping("/api/v1/repositories/{repoId}/preview")
    public ResponseEntity<ApiEnvelope<Object>> get(@PathVariable UUID repoId,
                                                   @RequestParam(required = false) String ref,
                                                   HttpServletRequest request) {
        GetResult result = previews.get(Principals.optionalCurrent(request), repoId, ref);
        if (result.preview() != null) {
            return ResponseEntity.ok(ApiEnvelope.ok(result.preview()));
        }
        return ResponseEntity.accepted().body(ApiEnvelope.ok(result.job()));
    }

    @PostMapping("/api/v1/repositories/{repoId}/preview-jobs")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> trigger(
            @PathVariable UUID repoId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        String ref = body == null || !(body.get("ref") instanceof String refValue) ? null : refValue;
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        Map<String, Object> job = previews.trigger(principal, repoId, ref, force);
        return ResponseEntity.accepted().body(ApiEnvelope.ok(job));
    }

    @RequestMapping(value = "/api/v1/repositories/{repoId}/preview/download",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ApiEnvelope<DownloadView> download(@PathVariable UUID repoId,
                                              @RequestParam(required = false) String ref,
                                              HttpServletRequest request) {
        return ApiEnvelope.ok(previews.download(Principals.optionalCurrent(request), repoId, ref));
    }
}
