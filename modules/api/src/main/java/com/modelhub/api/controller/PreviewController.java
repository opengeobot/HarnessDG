package com.modelhub.api.controller;

import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.web.ApiEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Preview endpoints (05 sec 2): 200/202 dual response.
 */
@RestController
@RequestMapping("/api/v1/repositories/{repoId}/preview")
public class PreviewController {

    @GetMapping
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> get(@PathVariable UUID repoId) {
        // TODO: check if preview exists, return 200 with preview data or 202 if generating
        return ResponseEntity.accepted().body(ApiEnvelope.ok(Map.of(
                "status", "generating",
                "repositoryId", repoId.toString())));
    }

    @PostMapping("../preview-jobs")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> triggerPreview(@PathVariable UUID repoId) {
        // TODO: idempotently trigger preview generation job
        return ResponseEntity.accepted().body(ApiEnvelope.ok(Map.of(
                "status", "queued",
                "repositoryId", repoId.toString())));
    }
}