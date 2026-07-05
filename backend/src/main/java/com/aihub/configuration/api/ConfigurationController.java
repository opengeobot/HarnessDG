/*
 * 功能: 配置管理 REST 适配器，提供非敏感运行配置的列表与更新接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.configuration.api.ConfigurationRequests.UpdateConfigurationRequest;
import com.aihub.configuration.application.ConfigurationApplicationService;
import com.aihub.configuration.application.ConfigurationDtos.ConfigView;
import com.aihub.configuration.application.ConfigurationDtos.UpdateConfigCommand;
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置管理 REST 适配器。
 *
 * <p>授权判定走统一 {@link AuthorizationService}（fail-closed）：列表与更新均需 {@code system:configure}
 * （高风险，Agent 一律拒绝）。Secret 入库在应用层拒绝；不可热更新项在响应 {@code hotReloadable=false}
 * 体现，客户端据此提示需重启。
 */
@RestController
@RequestMapping("/api/v1/system/configurations")
public class ConfigurationController {

    private final ConfigurationApplicationService configurationService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public ConfigurationController(ConfigurationApplicationService configurationService,
                                   AuthorizationService authorizationService,
                                   IdempotencyService idempotencyService,
                                   ObjectMapper objectMapper) {
        this.configurationService = configurationService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
    }

    /**
     * 查询非敏感运行配置。
     */
    @GetMapping
    public ApiResponse<List<ConfigView>> listConfigurations() {
        authorizationService.requirePermission(Permissions.SYSTEM_CONFIGURE);
        return respond(configurationService.listConfigurations());
    }

    /**
     * 更新非敏感运行配置。
     */
    @PutMapping("/{configKey}")
    public ApiResponse<ConfigView> updateConfiguration(
            @PathVariable String configKey,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody UpdateConfigurationRequest request) {
        authorizationService.requirePermission(Permissions.SYSTEM_CONFIGURE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                principalId(), "PUT", "/api/v1/system/configurations/" + configKey);
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<ConfigView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            ConfigView view = configurationService.updateConfiguration(configKey,
                    new UpdateConfigCommand(request.value(), request.expectedVersion(),
                            request.confirmation()),
                    principalId());
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(200, idempotency.serialize(view));
        });
        return respond(ref.get());
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
