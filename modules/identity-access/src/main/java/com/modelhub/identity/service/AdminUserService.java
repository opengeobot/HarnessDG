package com.modelhub.identity.service;

import com.modelhub.identity.domain.SysRoleEntity;
import com.modelhub.identity.domain.SysUserRoleEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.SysRoleRepository;
import com.modelhub.identity.repo.SysUserRoleRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 管理端用户服务（管理后台计划 §三）：用户列表/禁用/启用。
 * 仅 platform_admin；列表为 username 模糊 + status 过滤的 keyset cursor 分页（id DESC）。
 */
@Service
public class AdminUserService {

    /** 契约 AdminUser schema（不含 password_hash）。 */
    public record AdminUserView(UUID id, String username, String nickname, String status,
                                List<String> roles, OffsetDateTime createdAt) {}

    /** 分页查询行：内部自增 id 作为游标键。 */
    private record IdRow(long id, UUID publicId, String username, String nickname,
                         String status, OffsetDateTime createdAt) {}

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final SysUserRoleRepository userRoles;
    private final SysRoleRepository sysRoles;
    private final AuditService auditService;

    private final RowMapper<IdRow> idRowMapper = (rs, i) -> new IdRow(
            rs.getLong("id"),
            rs.getObject("public_id", UUID.class),
            rs.getString("username"),
            rs.getString("nickname"),
            rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class));

    public AdminUserService(JdbcTemplate jdbc, UserRepository users, SysUserRoleRepository userRoles,
                            SysRoleRepository sysRoles, AuditService auditService) {
        this.jdbc = jdbc;
        this.users = users;
        this.userRoles = userRoles;
        this.sysRoles = sysRoles;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public CursorResult<AdminUserView> listUsers(CurrentPrincipal actor, CursorQuery cursor,
                                                 String q, String status) {
        requireAdmin(actor);
        long lastId = cursor.lastKey() == Long.MIN_VALUE ? Long.MAX_VALUE : cursor.lastKey();
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, public_id, username, nickname, status, created_at FROM users WHERE id < ?");
        params.add(lastId);
        if (q != null && !q.isBlank()) {
            sql.append(" AND username ILIKE ?");
            params.add("%" + q.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(cursor.limit() + 1);
        List<IdRow> all = jdbc.query(sql.toString(), idRowMapper, params.toArray());
        boolean hasMore = all.size() > cursor.limit();
        List<IdRow> pageRows = all.stream().limit(cursor.limit()).toList();

        Map<Long, List<String>> rolesByUser = rolesByUserId(
                pageRows.stream().map(IdRow::id).toList());
        List<AdminUserView> items = pageRows.stream()
                .map(r -> new AdminUserView(r.publicId(), r.username(), r.nickname(), r.status(),
                        rolesByUser.getOrDefault(r.id(), List.of()), r.createdAt()))
                .toList();
        String next = hasMore && !pageRows.isEmpty()
                ? CursorQuery.encode(pageRows.get(pageRows.size() - 1).id()) : null;
        return new CursorResult<>(items, next);
    }

    /** 禁用用户：状态置 disabled 并递增 auth_version 使存量 JWT/会话失效。 */
    @Transactional
    public AdminUserView disableUser(CurrentPrincipal actor, UUID userPublicId) {
        requireAdmin(actor);
        UserEntity user = requireUser(userPublicId);
        if ("disabled".equals(user.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "用户已处于禁用状态");
        }
        if (holdsPlatformAdmin(user.getId())
                && userRoles.countActivePlatformAdminsExcluding(user.getId()) == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "不可禁用最后一个活跃 platform_admin 账户");
        }
        user.setStatus("disabled");
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setFailedAttempts(0);
        user.setUpdatedAt(OffsetDateTime.now());
        users.save(user);
        auditService.appendSimple(actor.username(), "user.admin_disable",
                "user:" + userPublicId, "success");
        return toView(user);
    }

    /** 启用用户：仅允许从 disabled 恢复为 active（locked 走 :unlock 端点）。 */
    @Transactional
    public AdminUserView enableUser(CurrentPrincipal actor, UUID userPublicId) {
        requireAdmin(actor);
        UserEntity user = requireUser(userPublicId);
        if (!"disabled".equals(user.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "仅禁用状态的用户可启用");
        }
        user.setStatus("active");
        user.setUpdatedAt(OffsetDateTime.now());
        users.save(user);
        auditService.appendSimple(actor.username(), "user.admin_enable",
                "user:" + userPublicId, "success");
        return toView(user);
    }

    // ---------- 内部 ----------

    private AdminUserView toView(UserEntity user) {
        return new AdminUserView(user.getPublicId(), user.getUsername(), user.getNickname(),
                user.getStatus(), userRoles.findActiveRoleCodes(user.getId()), user.getCreatedAt());
    }

    private boolean holdsPlatformAdmin(Long userId) {
        return userRoles.findActiveRoleCodes(userId).contains("platform_admin");
    }

    private Map<Long, List<String>> rolesByUserId(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> roleCodeById = sysRoles.findAll().stream()
                .collect(Collectors.toMap(SysRoleEntity::getId, SysRoleEntity::getCode));
        List<SysUserRoleEntity> assignments = userRoles.findByUserIdInAndRevokedAtIsNull(Set.copyOf(userIds));
        return assignments.stream().collect(Collectors.groupingBy(SysUserRoleEntity::getUserId,
                Collectors.mapping(a -> roleCodeById.getOrDefault(a.getRoleId(), ""),
                        Collectors.toList())));
    }

    private UserEntity requireUser(UUID userPublicId) {
        return users.findByPublicId(userPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }

    private static void requireAdmin(CurrentPrincipal actor) {
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证或凭证已过期");
        }
        if (!actor.isPlatformAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅 platform_admin 可执行该操作");
        }
    }
}
