package com.modelhub.catalog.service;

import com.modelhub.catalog.repo.VisitEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 统计投影重算（06 §7.1）：repository_stats 由强事实表幂等重建，消除增量竞争与重放副作用
 * （INTERACT-001/002）。重算式为同一 UPDATE 语句，行不存在时先 INSERT 零行（PG JDBC 不支持
 * prepared statement 多语句，故拆为两条）。供 StatsEventHandler 消费与对账重建（rebuild*）。
 */
@Service
public class StatsRebuildService {

    private static final String INSERT_ZERO =
            "INSERT INTO repository_stats (repository_id, likes, favorites, downloads, visits, file_count, updated_at) "
            + "VALUES (?, 0, 0, 0, 0, 0, now()) ON CONFLICT (repository_id) DO NOTHING";

    /** 全量重算：COUNT 子查询参数顺序 = likes/favorites/visits/downloads/file_count/WHERE。 */
    private static final String RECALCULATE =
            "UPDATE repository_stats SET "
            + "likes = (SELECT COUNT(*) FROM repository_likes WHERE repository_id = ?), "
            + "favorites = (SELECT COUNT(*) FROM repository_favorites WHERE repository_id = ?), "
            + "visits = (SELECT COUNT(*) FROM visit_events WHERE repository_id = ?), "
            + "downloads = (SELECT COUNT(*) FROM download_sessions WHERE repository_id = ?), "
            + "file_count = (SELECT COUNT(*) FROM file_versions WHERE repository_id = ? AND status = 'active'), "
            + "updated_at = now() "
            + "WHERE repository_id = ?";

    private static final String FIND_ID_BY_PUBLIC = "SELECT id FROM repositories WHERE public_id = ?";

    private static final String ALL_IDS = "SELECT id FROM repositories";

    private final JdbcTemplate jdbc;
    private final VisitEventRepository visitEvents;

    public StatsRebuildService(JdbcTemplate jdbc, VisitEventRepository visitEvents) {
        this.jdbc = jdbc;
        this.visitEvents = visitEvents;
    }

    /** 幂等重算单个仓库（06 §7.1 幂等消费者核心）。 */
    @Transactional
    public void recalculate(Long repositoryId) {
        jdbc.update(INSERT_ZERO, repositoryId);
        jdbc.update(RECALCULATE, repositoryId, repositoryId, repositoryId,
                repositoryId, repositoryId, repositoryId);
    }

    /** VisitRecorded 消费：UNIQUE(repository_id, visitor_hash, window_start) 兜底去重后重算。 */
    @Transactional
    public void recordVisit(Long repositoryId, String visitorHash, OffsetDateTime windowStart) {
        visitEvents.insertIgnore(repositoryId, visitorHash, windowStart);
        recalculate(repositoryId);
    }

    /** 对账重建单仓库（INTERACT-002）：不暴露 HTTP 端点（04 无契约），供集成测试验证。 */
    @Transactional
    public void rebuild(UUID repoId) {
        Long id = jdbc.query(FIND_ID_BY_PUBLIC, rs -> rs.next() ? rs.getLong(1) : null, repoId);
        if (id == null) {
            throw new IllegalArgumentException("仓库不存在: " + repoId);
        }
        recalculate(id);
    }

    /** 对账重建全部仓库（启动自愈/人工对账）。 */
    @Transactional
    public void rebuildAll() {
        for (Long id : jdbc.queryForList(ALL_IDS, Long.class)) {
            recalculate(id);
        }
    }
}
