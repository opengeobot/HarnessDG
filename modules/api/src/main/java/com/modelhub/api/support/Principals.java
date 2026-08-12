package com.modelhub.api.support;

import com.modelhub.api.security.JwtAuthFilter;
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
}
