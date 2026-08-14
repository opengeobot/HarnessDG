package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.identity.service.ApiKeyService;
import com.modelhub.identity.service.ApiKeyService.ApiKeyView;
import com.modelhub.identity.service.ApiKeyService.CreatedApiKey;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API Key CRUD endpoints (03 sec 7).
 * POST: create (plaintext returned ONCE).
 * GET: list (no secret/hash).
 * DELETE: idempotent revoke.
 */
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeysController {

    public record CreateApiKeyRequest(String name, String scopes, OffsetDateTime expiresAt) {}

    private final ApiKeyService apiKeyService;

    public ApiKeysController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> create(
            @RequestBody CreateApiKeyRequest body,
            HttpServletRequest request) {
        CreatedApiKey created = apiKeyService.create(
                Principals.requireCurrent(request),
                body.name(), body.scopes(), body.expiresAt());
        Map<String, Object> data = Map.of(
                "id", created.view().id().toString(),
                "name", created.view().name(),
                "key", created.plaintextKey(),
                "scopes", created.view().scopes(),
                "expiresAt", created.view().expiresAt() == null ? "" : created.view().expiresAt().toString(),
                "createdAt", created.view().createdAt().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.created(data));
    }

    @GetMapping
    public ApiEnvelope<Map<String, List<ApiKeyView>>> list(HttpServletRequest request) {
        List<ApiKeyView> items = apiKeyService.list(Principals.requireCurrent(request));
        return ApiEnvelope.ok(Map.of("items", items));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID keyId,
                                       HttpServletRequest request) {
        apiKeyService.revoke(Principals.requireCurrent(request), keyId);
        return ResponseEntity.noContent().build();
    }
}