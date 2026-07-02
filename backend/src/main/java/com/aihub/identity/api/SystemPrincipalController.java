/*
 * 功能: 系统主体查询 REST 适配器，提供统一访问主体的检索接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.identity.api.IdentityResponses.PrincipalSummaryPayload;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.identity.application.PrincipalQueryApplicationService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统主体查询 REST 适配器。要求认证 + 占位 Scope 校验（fail-closed）。
 */
@RestController
@RequestMapping("/api/v1/system/principals")
public class SystemPrincipalController {

    private final PrincipalQueryApplicationService principalService;
    private final AuthorizationService authorizationService;

    public SystemPrincipalController(PrincipalQueryApplicationService principalService,
                                     AuthorizationService authorizationService) {
        this.principalService = principalService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询统一访问主体。
     */
    @GetMapping
    public ApiResponse<List<PrincipalSummaryPayload>> listPrincipals(
            @RequestParam(required = false) PrincipalType principalType,
            @RequestParam(required = false) String keyword) {
        authorizationService.requirePermission(Permissions.USER_READ);
        return IdentityApiContext.respond(
                principalService.listPrincipals(principalType, keyword).stream()
                        .map(PrincipalSummaryPayload::from).toList());
    }
}
