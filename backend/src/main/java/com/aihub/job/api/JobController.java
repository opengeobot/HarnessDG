/*
 * 功能: 任务管理 REST 适配器——游标查询任务、人工重试/取消；授权走统一 AuthorizationService。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.JobApplicationService;
import com.aihub.job.application.JobView;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public JobController(JobApplicationService jobService, AuthorizationService authorizationService) {
        this.jobService = jobService;
        this.authorizationService = authorizationService;
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
    public ApiResponse<Map<String, String>> retryJob(@PathVariable String jobId) {
        authorizationService.requirePermission(Permissions.JOB_MANAGE);
        jobService.retryJob(jobId);
        return respond(Map.of("jobId", jobId, "status", "PENDING"));
    }

    /**
     * 取消尚未开始且允许取消的任务（PENDING/RETRY_WAIT → CANCELLED）。
     */
    @PostMapping("/{jobId}:cancel")
    public ApiResponse<Map<String, String>> cancelJob(@PathVariable String jobId) {
        authorizationService.requirePermission(Permissions.JOB_MANAGE);
        jobService.cancelJob(jobId);
        return respond(Map.of("jobId", jobId, "status", "CANCELLED"));
    }

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
