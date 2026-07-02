/*
 * 功能: 审计查询应用服务，提供按主体/动作/资源过滤的游标分页查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.audit.application;

import com.aihub.audit.domain.AuditRecord;
import com.aihub.audit.domain.AuditRepository;
import com.aihub.shared.api.CursorPage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计查询应用服务。
 *
 * <p>按 occurred_at, id 键集游标分页（最新优先），支持按 principalId/action/resourceId 过滤。
 * 仅返回视图，不暴露持久化实体或内部主键。
 */
@Service
public class AuditQueryService {

    private final AuditRepository auditRepository;

    public AuditQueryService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * 游标查询审计记录。
     *
     * @param principalId 主体过滤（可空）
     * @param action      动作过滤（可空）
     * @param resourceId  资源过滤（可空）
     * @param cursor      游标（可空）
     * @param limit       每页条数
     * @return 游标分页结果
     */
    @Transactional(readOnly = true)
    public CursorPage<AuditLogView> listAuditLogs(String principalId, String action, String resourceId,
                                                  String cursor, int limit) {
        Cursor decoded = Cursor.decode(cursor);
        List<AuditRecord> records = auditRepository.list(
                principalId, action, resourceId, decoded.time(), decoded.id(), limit + 1);
        boolean hasMore = records.size() > limit;
        List<AuditLogView> views = records.stream().limit(limit).map(AuditLogView::from).toList();
        String nextCursor = null;
        if (hasMore) {
            AuditRecord last = records.get(limit - 1);
            nextCursor = Cursor.encode(last.occurredAt(), last.id());
        }
        return new CursorPage<>(views, nextCursor, hasMore);
    }

    /**
     * 扩展过滤游标查询审计记录。
     *
     * @param eventType     事件类型过滤（可空）
     * @param principalId   主体过滤（可空）
     * @param resourceType  资源类型过滤（可空）
     * @param resourceId    资源过滤（可空）
     * @param createdAfter  发生时间下界（可空）
     * @param createdBefore 发生时间上界（可空）
     * @param cursor        游标（可空）
     * @param limit         每页条数
     * @return 游标分页结果
     */
    @Transactional(readOnly = true)
    public CursorPage<AuditLogView> searchAuditLogs(String eventType, String principalId, String resourceType,
                                                     String resourceId, Instant createdAfter,
                                                     Instant createdBefore, String cursor, int limit) {
        Cursor decoded = Cursor.decode(cursor);
        List<AuditRecord> records = auditRepository.search(
                eventType, principalId, resourceType, resourceId, createdAfter, createdBefore,
                decoded.time(), decoded.id(), limit + 1);
        boolean hasMore = records.size() > limit;
        List<AuditLogView> views = records.stream().limit(limit).map(AuditLogView::from).toList();
        String nextCursor = null;
        if (hasMore) {
            AuditRecord last = records.get(limit - 1);
            nextCursor = Cursor.encode(last.occurredAt(), last.id());
        }
        return new CursorPage<>(views, nextCursor, hasMore);
    }

    /** 游标（不透明，base64 编码 occurredAt|id）。 */
    private record Cursor(Instant time, Long id) {
        static Cursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return new Cursor(null, null);
            }
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = decoded.indexOf('|');
            if (sep < 0) {
                return new Cursor(null, null);
            }
            return new Cursor(Instant.parse(decoded.substring(0, sep)),
                    Long.valueOf(decoded.substring(sep + 1)));
        }

        static String encode(Instant time, Long id) {
            String raw = time.toString() + "|" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}
