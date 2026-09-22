/*
 * 功能: 通知收件人解析——按权限、资产归属与订阅关系解析站内通知目标主体。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.application;

import com.aihub.asset.discussion.domain.DiscussionSubscriptionRepository;
import com.aihub.authorization.domain.Permissions;
import com.aihub.organization.domain.TeamRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通知收件人解析器。
 *
 * <p>在数据库阶段解析具备指定权限的主体、资产 Owner Team 成员、遗留 owners 列表与讨论订阅者，
 * 供版本/讨论/运维事件 fan-out 复用。
 */
@Component
public class NotificationRecipientResolver {

    private final JdbcTemplate jdbcTemplate;
    private final TeamRepository teamRepository;
    private final DiscussionSubscriptionRepository subscriptionRepository;

    public NotificationRecipientResolver(JdbcTemplate jdbcTemplate,
                                         TeamRepository teamRepository,
                                         DiscussionSubscriptionRepository subscriptionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.teamRepository = teamRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** 解析资产作用域内具备 {@code asset:review} 的主体（含平台/组织/项目 RBAC 与资源 ACL）。 */
    public Set<String> resolveReviewers(String assetId) {
        return resolvePrincipalsWithPermission(assetId, Permissions.ASSET_REVIEW);
    }

    /** 解析具备 {@code system:observe} 的主体（平台级 RBAC）。 */
    public Set<String> resolveSystemObservers() {
        Set<String> result = new LinkedHashSet<>();
        List<String> platformObservers = jdbcTemplate.queryForList("""
                SELECT DISTINCT rb.principal_id
                FROM iam_role_binding rb
                JOIN iam_role r ON r.role_id = rb.role_id AND r.status = 'ACTIVE'
                JOIN iam_role_permission rp ON rp.role_id = r.role_id
                JOIN iam_permission p ON p.permission_id = rp.permission_id AND p.code = ?
                WHERE rb.scope_type = 'PLATFORM'
                """, String.class, Permissions.SYSTEM_OBSERVE);
        result.addAll(platformObservers);
        return Set.copyOf(result);
    }

    /** 解析资产 Owner（Owner Team 成员 + 遗留 owners JSON 列表）。 */
    public Set<String> resolveAssetOwners(String assetId) {
        Set<String> owners = new LinkedHashSet<>();
        Map<String, Object> row = loadAssetScopeRow(assetId);
        if (row == null) {
            return Set.of();
        }
        String ownerTeamId = stringValue(row.get("owner_team_id"));
        if (StringUtils.hasText(ownerTeamId)) {
            teamRepository.findMembersByTeamId(ownerTeamId).forEach(m -> owners.add(m.principalId()));
        }
        String ownersJson = stringValue(row.get("owners_json"));
        if (StringUtils.hasText(ownersJson) && ownersJson.startsWith("[")) {
            parseOwnersJson(ownersJson).forEach(owners::add);
        }
        return Set.copyOf(owners);
    }

    /** 解析资产讨论订阅者。 */
    public Set<String> resolveSubscribers(String assetId) {
        return subscriptionRepository.findSubscribers(assetId);
    }

    private Set<String> resolvePrincipalsWithPermission(String assetId, String permissionCode) {
        Map<String, Object> row = loadAssetScopeRow(assetId);
        String orgId = row == null ? null : stringValue(row.get("organization_id"));
        String projectId = row == null ? null : stringValue(row.get("project_id"));

        Set<String> result = new LinkedHashSet<>();
        List<String> rbac = jdbcTemplate.queryForList("""
                SELECT DISTINCT rb.principal_id
                FROM iam_role_binding rb
                JOIN iam_role r ON r.role_id = rb.role_id AND r.status = 'ACTIVE'
                JOIN iam_role_permission rp ON rp.role_id = r.role_id
                JOIN iam_permission p ON p.permission_id = rp.permission_id AND p.code = ?
                WHERE rb.scope_type = 'PLATFORM'
                   OR (rb.scope_type = 'ORGANIZATION' AND rb.scope_id = ?)
                   OR (rb.scope_type = 'PROJECT' AND rb.scope_id = ?)
                """, String.class, permissionCode, orgId, projectId);
        result.addAll(rbac);

        List<String> acl = jdbcTemplate.queryForList("""
                SELECT DISTINCT principal_id FROM iam_resource_acl
                WHERE resource_type = 'ASSET' AND resource_id = ? AND permission = ?
                """, String.class, assetId, permissionCode);
        result.addAll(acl);
        return Set.copyOf(result);
    }

    private Map<String, Object> loadAssetScopeRow(String assetId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT organization_id, project_id, owner_team_id, owners::text AS owners_json
                FROM asset WHERE asset_id = ? AND deleted_at IS NULL
                """, assetId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Set<String> parseOwnersJson(String json) {
        Set<String> owners = new HashSet<>();
        String trimmed = json.trim();
        if (trimmed.length() < 2) {
            return owners;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) {
            return owners;
        }
        for (String part : inner.split(",")) {
            String id = part.trim().replace("\"", "");
            if (!id.isBlank()) {
                owners.add(id);
            }
        }
        return owners;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
