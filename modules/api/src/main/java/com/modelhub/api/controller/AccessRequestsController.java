package com.modelhub.api.controller;

import com.modelhub.api.dto.RepoRequests.ApproveAccessRequestRequest;
import com.modelhub.api.dto.RepoRequests.RequestAccessRequest;
import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.GatedAccessService;
import com.modelhub.catalog.service.GatedAccessService.AccessRequestView;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * gated 访问申请端点（04 §4.4、02 §4）：申请/审批/撤销全流程。
 * 动作子资源用 "{requestId}:action" 冒号语法，regex 捕获段区分。
 */
@RestController
@RequestMapping("/api/v1/repositories/{repoId}/access-requests")
public class AccessRequestsController {

    /** cursor 响应 data：items + nextCursor。 */
    public record AccessRequestPageData(List<AccessRequestView> items, String nextCursor) {}

    private final GatedAccessService gated;

    public AccessRequestsController(GatedAccessService gated) {
        this.gated = gated;
    }

    @GetMapping
    public ApiEnvelope<AccessRequestPageData> list(@PathVariable UUID repoId,
                                                   @RequestParam Map<String, String> params,
                                                   HttpServletRequest request) {
        CursorQuery cursor = CursorQuery.from(new HashMap<>(params));
        CursorResult<AccessRequestView> result =
                gated.listForRepo(Principals.requireCurrent(request), repoId, cursor);
        return ApiEnvelope.ok(new AccessRequestPageData(result.items(), result.nextCursor()));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<AccessRequestView>> request(@PathVariable UUID repoId,
                                                                  @Valid @RequestBody RequestAccessRequest body,
                                                                  HttpServletRequest request) {
        AccessRequestView view = gated.requestAccess(Principals.requireCurrent(request), repoId, body.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.created(view));
    }

    @PostMapping("/{requestId:[0-9a-fA-F-]+}:approve")
    public ApiEnvelope<AccessRequestView> approve(@PathVariable UUID repoId, @PathVariable UUID requestId,
                                                  @RequestBody(required = false) ApproveAccessRequestRequest body,
                                                  @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                  HttpServletRequest request) {
        return ApiEnvelope.ok(gated.approve(Principals.requireCurrent(request), repoId, requestId,
                body == null ? null : body.grantExpiresAt(), ifMatch));
    }

    @PostMapping("/{requestId:[0-9a-fA-F-]+}:reject")
    public ApiEnvelope<AccessRequestView> reject(@PathVariable UUID repoId, @PathVariable UUID requestId,
                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                 HttpServletRequest request) {
        return ApiEnvelope.ok(gated.reject(Principals.requireCurrent(request), repoId, requestId, ifMatch));
    }

    @PostMapping("/{requestId:[0-9a-fA-F-]+}:revoke")
    public ApiEnvelope<AccessRequestView> revoke(@PathVariable UUID repoId, @PathVariable UUID requestId,
                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                 HttpServletRequest request) {
        return ApiEnvelope.ok(gated.revoke(Principals.requireCurrent(request), repoId, requestId, ifMatch));
    }
}
