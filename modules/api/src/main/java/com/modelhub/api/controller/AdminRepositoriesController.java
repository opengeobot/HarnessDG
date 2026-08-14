package com.modelhub.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.api.support.IdempotentOps;
import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.CatalogService;
import com.modelhub.catalog.service.CatalogService.JobView;
import com.modelhub.shared.idempotency.JdbcIdempotencyService.Acquired;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 仓库管理面端点（04 §8，05 §8/§9.2）：仅 platform_admin；
 * retry 处理 provisioning 失败，delete 处理 failed/draft/provisioning 源状态。
 */
@RestController
@RequestMapping("/api/v1/admin/repositories")
public class AdminRepositoriesController {

    private final CatalogService catalog;
    private final IdempotentOps idempotent;
    private final ObjectMapper objectMapper;

    public AdminRepositoriesController(CatalogService catalog, IdempotentOps idempotent, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.idempotent = idempotent;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{repoId:[0-9a-fA-F-]+}:retry")
    public ResponseEntity<String> retry(@PathVariable UUID repoId,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                        HttpServletRequest request) throws Exception {
        Acquired acq = idempotent.begin(Principals.requireCurrent(request).userId() + ":" + "repository.admin_retry" + "." + idempotencyKey, null);
        if (acq.replay()) {
            return ResponseEntity.status(acq.recordedStatus())
                    .contentType(MediaType.APPLICATION_JSON).body(acq.recordedBody());
        }
        JobView job = catalog.adminRetry(Principals.requireCurrent(request), repoId);
        String responseBody = objectMapper.writeValueAsString(ApiEnvelope.ok(job));
        idempotent.finish(Principals.requireCurrent(request).userId() + ":" + "repository.admin_retry" + "." + idempotencyKey, 202, responseBody);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }

    @PostMapping("/{repoId:[0-9a-fA-F-]+}:delete")
    public ResponseEntity<String> delete(@PathVariable UUID repoId,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         HttpServletRequest request) throws Exception {
        Acquired acq = idempotent.begin(Principals.requireCurrent(request).userId() + ":" + "repository.admin_delete" + "." + idempotencyKey, null);
        if (acq.replay()) {
            return ResponseEntity.status(acq.recordedStatus())
                    .contentType(MediaType.APPLICATION_JSON).body(acq.recordedBody());
        }
        JobView job = catalog.adminDelete(Principals.requireCurrent(request), repoId);
        String responseBody = objectMapper.writeValueAsString(ApiEnvelope.ok(job));
        idempotent.finish(Principals.requireCurrent(request).userId() + ":" + "repository.admin_delete" + "." + idempotencyKey, 202, responseBody);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }
}
