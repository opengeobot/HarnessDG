package com.modelhub.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.api.dto.ArtifactRequests.InitiateUploadRequest;
import com.modelhub.api.dto.ArtifactRequests.PartUrlRequest;
import com.modelhub.api.dto.ArtifactRequests.PublishUploadRequest;
import com.modelhub.api.support.IdempotentOps;
import com.modelhub.api.support.Principals;
import com.modelhub.artifact.service.UploadService;
import com.modelhub.artifact.service.UploadService.InitiateCmd;
import com.modelhub.artifact.service.UploadService.PartUrlView;
import com.modelhub.artifact.service.UploadService.PublishCmd;
import com.modelhub.artifact.service.UploadService.UploadView;
import com.modelhub.catalog.service.CatalogService.JobView;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.idempotency.JdbcIdempotencyService.Acquired;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 上传端点（04 Artifacts / 05 §6）：initiate 201、状态查询、part-urls 批次签发、
 * complete/publish/abort 202 + JobEnvelope；写操作携带 Idempotency-Key（04 §10）。
 */
@RestController
public class UploadsController {

    private final UploadService uploads;
    private final IdempotentOps idempotent;
    private final ObjectMapper objectMapper;

    public UploadsController(UploadService uploads, IdempotentOps idempotent, ObjectMapper objectMapper) {
        this.uploads = uploads;
        this.idempotent = idempotent;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/repositories/{repoId}/uploads")
    public ResponseEntity<String> initiate(@PathVariable UUID repoId,
                                           @Valid @RequestBody InitiateUploadRequest body,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           HttpServletRequest request) throws Exception {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        JsonNode hashSource = objectMapper.valueToTree(body);
        Acquired acq = idempotent.begin(principal, request.getMethod(), request.getRequestURI(),
                idempotencyKey, hashSource);
        if (acq.replay()) {
            return ResponseEntity.status(acq.recordedStatus())
                    .contentType(MediaType.APPLICATION_JSON).body(acq.recordedBody());
        }
        UploadView view = uploads.initiate(principal, repoId,
                new InitiateCmd(body.branch(), body.baseCommitSha(), body.path(), body.sizeBytes(),
                        body.sha256(), body.contentType()),
                idempotencyKey);
        String responseBody = objectMapper.writeValueAsString(ApiEnvelope.created(view));
        idempotent.finish(principal, request.getMethod(), request.getRequestURI(),
                idempotencyKey, 201, responseBody);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }

    @GetMapping("/api/v1/uploads/{uploadId}")
    public ApiEnvelope<UploadView> get(@PathVariable UUID uploadId, HttpServletRequest request) {
        return ApiEnvelope.ok(uploads.getUpload(Principals.requireCurrent(request), uploadId));
    }

    @PostMapping("/api/v1/uploads/{uploadId}/part-urls")
    public ApiEnvelope<Map<String, List<PartUrlView>>> partUrls(@PathVariable UUID uploadId,
                                                                @Valid @RequestBody PartUrlRequest body,
                                                                HttpServletRequest request) {
        List<PartUrlView> items = uploads.partUrls(Principals.requireCurrent(request), uploadId,
                body.partNumbers());
        return ApiEnvelope.ok(Map.of("items", items));
    }

    @PostMapping("/api/v1/uploads/{uploadId}:complete")
    public ResponseEntity<String> complete(@PathVariable UUID uploadId,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           HttpServletRequest request) throws Exception {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        return acceptJob(principal, request.getMethod(), request.getRequestURI(), idempotencyKey,
                uploads.complete(principal, uploadId));
    }

    @PostMapping("/api/v1/uploads/{uploadId}:publish")
    public ResponseEntity<String> publish(@PathVariable UUID uploadId,
                                          @Valid @RequestBody PublishUploadRequest body,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                          HttpServletRequest request) throws Exception {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        return acceptJob(principal, request.getMethod(), request.getRequestURI(), idempotencyKey,
                uploads.publish(principal, uploadId,
                        new PublishCmd(body.baseCommitSha(), body.conflictResolution(), body.commitMessage())));
    }

    @PostMapping("/api/v1/uploads/{uploadId}:abort")
    public ResponseEntity<String> abort(@PathVariable UUID uploadId,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                        HttpServletRequest request) throws Exception {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        if (idempotencyKey == null) {
            JobView job = uploads.abort(principal, uploadId);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(ApiEnvelope.ok(job)));
        }
        return acceptJob(principal, request.getMethod(), request.getRequestURI(), idempotencyKey,
                uploads.abort(principal, uploadId));
    }

    /** 202 JobEnvelope + Idempotency 重放（04 §10）；scope = 主体+方法+路径。 */
    private ResponseEntity<String> acceptJob(CurrentPrincipal principal, String method, String path,
                                             String idempotencyKey, JobView job) throws Exception {
        Acquired acq = idempotent.begin(principal, method, path, idempotencyKey, null);
        if (acq.replay()) {
            return ResponseEntity.status(acq.recordedStatus())
                    .contentType(MediaType.APPLICATION_JSON).body(acq.recordedBody());
        }
        String responseBody = objectMapper.writeValueAsString(ApiEnvelope.ok(job));
        idempotent.finish(principal, method, path, idempotencyKey, 202, responseBody);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }
}
