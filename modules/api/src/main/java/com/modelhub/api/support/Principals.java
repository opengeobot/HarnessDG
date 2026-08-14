package com.modelhub.api.support;

import com.modelhub.api.security.JwtAuthFilter;
import com.modelhub.identity.config.IdentityProperties;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

/** Controller 获取当前主体的统一入口。 */
public final class Principals {

    private Principals() {}

    public static CurrentPrincipal requireCurrent(HttpServletRequest request) {
        Object p = request.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        if (!(p instanceof CurrentPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证或凭证已过期");
        }
        return principal;
    }

    /** 匿名可达端点：有效 Token 返回主体，否则 null（授权层自行区分 403/404）。 */
    public static CurrentPrincipal optionalCurrent(HttpServletRequest request) {
        Object p = request.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        return p instanceof CurrentPrincipal principal ? principal : null;
    }

    /** 客户端 IP（与 AuthController.clientIp 同规则）：优先信任代理配置的
     *  client-ip-header（首个值），缺失时回退 remoteAddr。 */
    public static String clientIp(HttpServletRequest request, IdentityProperties props) {
        String header = props.clientIpHeader();
        if (header != null && !header.isBlank()) {
            String forwarded = request.getHeader(header);
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
