/*
 * 功能: authorization 运行时引导器——为引导创建的平台管理员补齐 ADMIN 角色绑定（幂等）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.infrastructure;

import com.aihub.authorization.application.RoleBindingApplicationService;
import com.aihub.authorization.domain.Permissions;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * authorization 运行时引导器。
 *
 * <p>迁移无法预知运行期生成的管理员 {@code principal_id}，故由本引导器在启动时补齐：查询 {@code iam_user}
 * 中持有 {@code user:manage} 粗粒度 Scope 的活跃管理员（即 identity 的 BootstrapAdminInitializer 创建者），
 * 为其确保平台 ADMIN 角色绑定。幂等：已存在绑定则跳过。设为最低优先级，确保在 identity 引导器之后执行。
 *
 * <p>本引导器只读 {@code iam_user.scopes} 定位 bootstrap 管理员，不修改 identity 数据，不破坏模块边界
 * （绑定写入经 authorization 自身的 application 服务完成）。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuthorizationBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationBootstrap.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RoleBindingApplicationService roleBindingService;

    public AuthorizationBootstrap(NamedParameterJdbcTemplate jdbcTemplate,
                                  RoleBindingApplicationService roleBindingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.roleBindingService = roleBindingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> adminPrincipalIds = jdbcTemplate.queryForList(
                "SELECT principal_id FROM iam_user WHERE status <> 'DISABLED' "
                        + "AND scopes @> :scope::jsonb",
                new MapSqlParameterSource("scope", "[\"" + Permissions.USER_MANAGE + "\"]"),
                String.class);
        for (String principalId : adminPrincipalIds) {
            roleBindingService.ensurePlatformAdmin(principalId);
        }
        if (!adminPrincipalIds.isEmpty()) {
            LOG.info("Ensured platform ADMIN role binding for {} bootstrap administrator(s).",
                    adminPrincipalIds.size());
        }
    }
}
