package com.modelhub.identity.security;

import java.util.Set;
import java.util.UUID;

/** 当前请求主体（Access JWT 解析并在线校验后的结果）。 */
public record CurrentPrincipal(
        Long userId,
        UUID userPublicId,
        String username,
        long authVersion,
        String sessionId,
        Set<String> platformRoles) {

    public boolean isPlatformAdmin() {
        return platformRoles.contains("platform_admin");
    }

    public boolean isPlatformAuditor() {
        return platformRoles.contains("platform_auditor");
    }
}
