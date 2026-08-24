package com.modelhub.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modelhub.api.support.Principals;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.JobEventEntity;
import com.modelhub.catalog.service.JobQueryService;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Jobs 查询面（05 §1）：状态查询 / 取消 / SSE 事件流。
 * 授权：Job 仅创建者与 platform_admin 可见（越权一律 404 不泄漏存在性）。
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobsController {

    /** SSE 流生命周期上限与增量轮询间隔。 */
    private static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long POLL_INTERVAL_MS = 2_000L;

    /** 全量共享的 SSE 轮询线程池（守护线程，不阻塞进程退出）。 */
    private static final ScheduledExecutorService SSE_POLLER = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "job-sse-poller");
        t.setDaemon(true);
        return t;
    });

    private final JobQueryService jobQueryService;
    private final ObjectMapper objectMapper;

    public JobsController(JobQueryService jobQueryService, ObjectMapper objectMapper) {
        this.jobQueryService = jobQueryService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{jobId}")
    public ApiEnvelope<Map<String, Object>> get(@PathVariable UUID jobId,
                                                HttpServletRequest request) {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        return ApiEnvelope.ok(view(findAuthorizedJob(jobId, principal)));
    }

    @PostMapping("/{jobId}:cancel")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> cancel(@PathVariable UUID jobId,
                                                                   HttpServletRequest request) {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        // 终态 409 / cancel_requested 幂等受理 / 首次迁移 + 事件写入均收敛在服务内
        JobEntity job = jobQueryService.requestCancel(findAuthorizedJob(jobId, principal));
        return ResponseEntity.accepted().body(ApiEnvelope.ok(view(job)));
    }

    @GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID jobId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
                             HttpServletRequest request) {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        JobEntity job = findAuthorizedJob(jobId, principal);

        long from = 0L;
        if (lastEventIdHeader != null && !lastEventIdHeader.isBlank()) {
            long lastEventId = parseLastEventId(lastEventIdHeader);
            List<JobEventEntity> history = jobQueryService.eventsAsc(job.getId());
            if (lastEventId < 0 || history.isEmpty()) {
                throw historyExpired();
            }
            // 保留历史的起点早于客户端游标 → 请求的事件已无法完整回放
            if (lastEventId < history.get(0).getSequence() - 1) {
                throw historyExpired();
            }
            from = lastEventId;
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> pollTask = new AtomicReference<>();
        Runnable stop = () -> {
            stopped.set(true);
            ScheduledFuture<?> task = pollTask.get();
            if (task != null) {
                task.cancel(false);
            }
        };
        emitter.onCompletion(stop);
        emitter.onTimeout(() -> {
            stop.run();
            emitter.complete();
        });

        long internalJobId = job.getId();
        String connectStatus = job.getStatus();
        AtomicLong lastSent = new AtomicLong(from);

        // 1) 历史/断线重连事件在请求线程内同步回放
        try {
            for (JobEventEntity event : jobQueryService.eventsAfter(internalJobId, from)) {
                sendEvent(emitter, event, connectStatus);
                lastSent.set(event.getSequence());
            }
        } catch (IOException | IllegalStateException ex) {
            emitter.complete();
            return emitter;
        }

        // 2) Job 已终态：回放完即收口，无需轮询
        if (JobQueryService.TERMINAL_STATUSES.contains(currentStatus(internalJobId))) {
            emitter.complete();
            return emitter;
        }

        // 3) 异步轮询增量直至终态或流超时
        Runnable poll = () -> {
            if (stopped.get()) {
                return;
            }
            try {
                for (JobEventEntity event : jobQueryService.eventsAfter(internalJobId, lastSent.get())) {
                    sendEvent(emitter, event, connectStatus);
                    lastSent.set(event.getSequence());
                }
                if (JobQueryService.TERMINAL_STATUSES.contains(currentStatus(internalJobId))) {
                    emitter.complete();
                }
            } catch (Exception ex) {
                stop.run();
                emitter.complete();
            }
        };
        pollTask.set(SSE_POLLER.scheduleWithFixedDelay(poll, POLL_INTERVAL_MS, POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS));
        return emitter;
    }

    // ---------- 内部 ----------

    /** Job 并发消失视为终态（服务内兜底 failed）。 */
    private String currentStatus(long internalJobId) {
        return jobQueryService.statusOf(internalJobId);
    }

    private void sendEvent(SseEmitter emitter, JobEventEntity event, String fallbackStatus)
            throws IOException {
        JsonNode data = readJson(event.getData());
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", event.getEventType());
        payload.put("sequence", event.getSequence());
        payload.set("data", data);
        payload.put("status", data.path("status").isTextual()
                ? data.path("status").asText() : fallbackStatus);
        emitter.send(SseEmitter.event()
                .id(String.valueOf(event.getSequence()))
                .name(event.getEventType())
                .data(payload.toString(), MediaType.APPLICATION_JSON));
    }

    private JobEntity findAuthorizedJob(UUID jobId, CurrentPrincipal principal) {
        JobEntity job = jobQueryService.findByPublicId(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Job 不存在"));
        if (!principal.isPlatformAdmin()
                && (job.getCreatedBy() == null || !job.getCreatedBy().equals(principal.userId()))) {
            // 非创建者访问返回 404，避免泄漏 Job 存在性
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Job 不存在");
        }
        return job;
    }

    private Map<String, Object> view(JobEntity job) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", job.getPublicId().toString());
        data.put("type", job.getJobType());
        data.put("status", job.getStatus());
        data.put("createdAt", job.getCreatedAt() == null ? null : job.getCreatedAt().toString());
        data.put("progressCurrent", job.getProgressCurrent());
        data.put("progressTotal", job.getProgressTotal());
        data.put("progressMessage", job.getProgressMessage());
        data.put("resultSummary", readJson(job.getResultSummary()));
        data.put("errorCode", job.getErrorCode());
        data.put("startedAt", job.getStartedAt() == null ? null : job.getStartedAt().toString());
        data.put("finishedAt", job.getFinishedAt() == null ? null : job.getFinishedAt().toString());
        return data;
    }

    private JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("job JSON 字段解析失败", e);
        }
    }

    private static long parseLastEventId(String header) {
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Last-Event-ID 必须是整数", List.of());
        }
    }

    private static ApiException historyExpired() {
        return new ApiException(ErrorCode.EVENT_HISTORY_EXPIRED, "Last-Event-ID 早于保留的事件历史");
    }
}
