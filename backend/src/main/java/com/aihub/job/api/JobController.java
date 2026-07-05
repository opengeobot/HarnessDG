/*
 * 功能: 任务管理 REST 适配器——游标查询任务、人工重试/取消；授权走统一 AuthorizationService。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.IdempotencyService;
import com.aihub.job.application.JobApplicationService;
import com.aihub.job.application.JobView;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务管理 REST 适配器。
 *
 * <p>列表需 {@code job:read}；重试/取消需 {@code job:manage}（高风险动作，统一授权 fail-closed）。
 * 适配层不含业务规则，编排委托 {@link JobApplicationService}。
 */
@RestController
@RequestMapping("/api/v1/system/jobs")
public class JobController {

    private final JobApplicationService jobService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public JobController(JobApplicationService jobService,
                          AuthorizationService authorizationService,
                          IdempotencyService idempotencyService,
                          ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
    }

    /**
     * 游标查询持久化任务。
     */
    @GetMapping
    public ApiResponse<CursorPage<JobView>> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        authorizationService.requirePermission(Permissions.JOB_READ);
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        return respond(jobService.listJobs(status, cursor, effectiveLimit));
    }

    /**
     * 人工重试可重试任务（DEAD/RETRY_WAIT → PENDING）。
     */
    @PostMapping("/{jobId}:retry")
    public ApiResponse<Map<String, String>> retryJob(
            @PathVariable String jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.JOB_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                principalId(), "POST", "/api/v1/system/jobs/" + jobId + ":retry");
        Map<String, String> result = Map.of("jobId", jobId, "status", "PENDING");
        idempotencyService.execute(key, null, () -> {
            jobService.retryJob(jobId);
            return new IdempotencyService.IdempotencyResponse(200, idempotency.serialize(result));
        });
        return respond(result);
    }

    /**
     * 取消尚未开始且允许取消的任务（PENDING/RETRY_WAIT → CANCELLED）。
     */
    @PostMapping("/{jobId}:cancel")
    public ApiResponse<Map<String, String>> cancelJob(
            @PathVariable String jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.JOB_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                principalId(), "POST", "/api/v1/system/jobs/" + jobId + ":cancel");
        Map<String, String> result = Map.of("jobId", jobId, "status", "CANCELLED");
        idempotencyService.execute(key, null, () -> {
            jobService.cancelJob(jobId);
            return new IdempotencyService.IdempotencyResponse(200, idempotency.serialize(result));
        });
        return respond(result);
    }

    private static String principalId() {
        return PrincipalContextHolder.current().map(PrincipalContext::principalId).orElse(null);
    }

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
