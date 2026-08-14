package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.repo.JobRepository;
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

import java.util.Map;
import java.util.UUID;

/**
 * Jobs query surface (05 sec 1): status, cancel, SSE events.
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobsController {

    private final JobRepository jobs;

    public JobsController(JobRepository jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/{jobId}")
    public ApiEnvelope<Map<String, Object>> get(@PathVariable UUID jobId,
                                                HttpServletRequest request) {
        JobEntity job = findJob(jobId);
        return ApiEnvelope.ok(Map.of(
                "id", job.getPublicId().toString(),
                "type", job.getJobType(),
                "status", job.getStatus(),
                "createdAt", job.getCreatedAt().toString()));
    }

    @PostMapping("/{jobId}:cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID jobId,
                                       HttpServletRequest request) {
        JobEntity job = findJob(jobId);
        if ("succeeded".equals(job.getStatus()) || "failed".equals(job.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "Job already terminal");
        }
        job.setStatus("cancel_requested");
        jobs.save(job);
        return ResponseEntity.accepted().build();
    }

    @GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID jobId,
                             @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
                             HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);
        // TODO: wire to job_events table for replay + live streaming
        emitter.complete();
        return emitter;
    }

    private JobEntity findJob(UUID jobId) {
        return jobs.findByPublicId(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Job not found"));
    }
}