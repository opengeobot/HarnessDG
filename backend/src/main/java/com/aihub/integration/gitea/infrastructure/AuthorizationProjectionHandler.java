package com.aihub.integration.gitea.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 权限投影 Worker。
 *
 * <p>PG 角色/ACL 变更 → Outbox → Worker 计算目标 Gitea Team/Repo 权限。
 * 单向投影：只从 PG 推送到 Gitea，不反向合并。
 *
 * <p>处理流程：
 * <ol>
 *   <li>读取变更事件中的 principal/resource</li>
 *   <li>计算目标 Gitea 权限集合</li>
 *   <li>与当前 Gitea 权限对比</li>
 *   <li>差异部分通过 Gitea API 同步</li>
 * </ol>
 */
@Component
public class AuthorizationProjectionHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationProjectionHandler.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuthorizationProjectionHandler(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "AUTH_PROJECTION";
    }

    @Override
    public void handle(JobContext context) {
        String payload = context.payload();
        if (payload == null || payload.isEmpty()) {
            LOG.warn("auth projection job has empty payload jobId={}", context.jobId());
            return;
        }

        try {
            JsonNode event = objectMapper.readTree(payload);
            String changeType = event.path("changeType").asText("");
            String principalId = event.path("principalId").asText(null);
            String resourceType = event.path("resourceType").asText(null);
            String resourceId = event.path("resourceId").asText(null);

            LOG.info("processing auth projection changeType={} principal={} resource={}/{}",
                    changeType, principalId, resourceType, resourceId);

            switch (changeType) {
                case "ROLE_BINDING_CHANGED" -> projectRoleBindingChange(principalId);
                case "ACL_CHANGED" -> projectAclChange(resourceType, resourceId);
                case "ORGANIZATION_MEMBERSHIP_CHANGED" -> projectOrgMembershipChange(principalId);
                default -> LOG.debug("unhandled auth projection changeType={}", changeType);
            }

            LOG.info("auth projection completed jobId={}", context.jobId());

        } catch (Exception e) {
            LOG.error("auth projection failed jobId={}", context.jobId(), e);
            throw new RuntimeException("auth projection failed: " + e.getMessage(), e);
        }
    }

    private void projectRoleBindingChange(String principalId) {
        // 查询该主体的所有角色绑定
        List<Map<String, Object>> bindings = jdbcTemplate.queryForList(
                "SELECT rb.role_id, r.code AS role_code " +
                        "FROM iam_role_binding rb JOIN iam_role r ON rb.role_id = r.id " +
                        "WHERE rb.principal_id = ?", principalId);

        LOG.info("projecting {} role bindings for principal={}", bindings.size(), principalId);
        // P5 简化：记录投影意图，实际 Gitea API 调用待集成测试环境就绪后启用
        for (Map<String, Object> binding : bindings) {
            LOG.debug("role binding principal={} role={}", principalId, binding.get("role_code"));
        }
    }

    private void projectAclChange(String resourceType, String resourceId) {
        // 查询该资源的所有 ACL 条目
        List<Map<String, Object>> acls = jdbcTemplate.queryForList(
                "SELECT principal_id, permission_code FROM iam_resource_acl " +
                        "WHERE resource_type = ? AND resource_id = ?",
                resourceType, resourceId);

        LOG.info("projecting {} ACL entries for resource={}/{}", acls.size(), resourceType, resourceId);
        // P5 简化：记录投影意图
    }

    private void projectOrgMembershipChange(String principalId) {
        // 查询该主体的组织成员关系
        List<Map<String, Object>> memberships = jdbcTemplate.queryForList(
                "SELECT organization_id, role FROM org_membership WHERE principal_id = ?",
                principalId);

        LOG.info("projecting {} org memberships for principal={}", memberships.size(), principalId);
        // P5 简化：记录投影意图
    }
}
