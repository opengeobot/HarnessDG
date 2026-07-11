/*
 * 功能: 幂等记录只读 REST 适配器——管理端浏览 api_idempotency 表。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.job.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.IdempotencyQueryService;
import com.aihub.job.domain.IdempotencyRecordSummary;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 幂等记录只读 REST 适配器。
 *
 * <p>要求 {@code job:read} 权限（fail-closed）；仅查询，不提供创建/删除。
 */
@RestController
@RequestMapping("/api/v1/system/idempotency")
public class IdempotencyController {

    private final IdempotencyQueryService queryService;
    private final AuthorizationService authorizationService;

    public IdempotencyController(IdempotencyQueryService queryService,
                                 AuthorizationService authorizationService) {
        this.queryService = queryService;
        this.authorizationService = authorizationService;
    }

    /** 查询最近完成的幂等记录（按创建时间降序）。 */
    @GetMapping
    public ApiResponse<List<IdempotencyRecordSummary>> listRecords(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        authorizationService.requirePermission(Permissions.JOB_READ);
        return respond(queryService.listRecords(limit));
    }

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
