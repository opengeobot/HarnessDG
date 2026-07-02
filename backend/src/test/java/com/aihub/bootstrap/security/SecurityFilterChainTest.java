/*
 * 功能: SecurityFilterChain Web 切片测试——验证 fail-closed：受保护端点 401、匿名放行端点通过。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.bootstrap.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link SecurityConfiguration} fail-closed 安全过滤链 Web 切片测试。
 *
 * <p>使用内置测试控制器覆盖受保护端点与匿名放行端点，验证：
 * <ul>
 *   <li>受保护端点无 Token → 401，响应体为统一 {@link com.aihub.shared.api.ApiError}（code=AUTH_UNAUTHENTICATED）；</li>
 *   <li>携带认证主体 → 放行；</li>
 *   <li>匿名放行端点（auth 登录/刷新）无 Token 也可访问。</li>
 * </ul>
 */
@WebMvcTest(SecurityFilterChainTest.ProbeController.class)
@Import({SecurityFilterChainTest.ProbeController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class,
        SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SecurityFilterChainTest {

    /** 启动类位于 com.aihub.bootstrap 且带 @MapperScan，为切片测试提供本地配置锚点避免装配 Mapper。 */
    @Configuration
    static class TestConfig {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/assets/probe")
        String protectedProbe() {
            return "ok";
        }

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "login-ok";
        }

        @PostMapping("/api/v1/auth/refresh")
        String refresh() {
            return "refresh-ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorizedApiError() throws Exception {
        mockMvc.perform(get("/api/v1/assets/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.i18nKey").value(ErrorCode.AUTH_UNAUTHENTICATED.defaultI18nKey()));
    }

    @Test
    void protectedEndpointWithAuthenticatedPrincipalIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/assets/probe").with(user("usr_01")))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousAuthEndpointsArePermitted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isOk());
    }
}
