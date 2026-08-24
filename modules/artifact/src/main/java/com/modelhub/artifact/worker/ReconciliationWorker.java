package com.modelhub.artifact.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存储一致性对账（05 §10.3，只读检测、不自动修复）：
 * - active file_version 指向缺失/已删除对象；
 * - 孤儿对象（available 且 ref_count=0，越过 1 天宽限期）；
 * - ref_count 与 staging/active 引用数不一致；
 * - 未终结 Multipart（非终态会话过期超 1 小时，Janitor 失效的迹象）。
 * 每项输出 WARN（计数 + 最多 20 个样例标识）与一条 INFO 汇总；
 * 修复动作必须经人工/安全任务验证保留期与审计后执行（05 §10.3 末段）。
 */
@Component
public class ReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationWorker.class);

    /** 对账报告：四项检测计数（供调度日志与测试断言）。 */
    public record ReconciliationReport(long missingObjects, long orphans, long refMismatches,
                                       long staleUploads) {}

    private static final int SAMPLE_LIMIT = 20;

    /** 上传会话终态集合（05 §5）；非终态且过期超 1 小时视为 Janitor 残留。 */
    private static final String NON_TERMINAL_UPLOAD_STATUSES =
            "('initiated','uploading','verifying','scanning','committing','conflict','aborting')";

    private final JdbcTemplate jdbc;

    public ReconciliationWorker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${modelhub.artifact.reconciliation-interval-ms:3600000}")
    public void reconcile() {
        runChecks();
    }

    public ReconciliationReport runChecks() {
        long missingObjects = checkMissingObjects();
        long orphans = checkOrphanObjects();
        long refMismatches = checkRefCountMismatches();
        long staleUploads = countStaleUploads();
        log.info("reconciliation: missingObjects={} orphans={} refMismatches={} staleUploads={}",
                missingObjects, orphans, refMismatches, staleUploads);
        return new ReconciliationReport(missingObjects, orphans, refMismatches, staleUploads);
    }

    /** 检测 1：staging/active 的 object source file_version 指向缺失或已删除的 object_blobs。 */
    private long checkMissingObjects() {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM file_versions fv
                LEFT JOIN object_blobs ob ON ob.id = fv.object_blob_id
                WHERE fv.status IN ('staging','active') AND fv.content_source = 'object'
                  AND fv.object_blob_id IS NOT NULL
                  AND (ob.id IS NULL OR ob.status = 'deleted')
                """, Long.class);
        long c = count == null ? 0 : count;
        if (c > 0) {
            List<String> sample = jdbc.queryForList("""
                    SELECT fv.public_id::text FROM file_versions fv
                    LEFT JOIN object_blobs ob ON ob.id = fv.object_blob_id
                    WHERE fv.status IN ('staging','active') AND fv.content_source = 'object'
                      AND fv.object_blob_id IS NOT NULL
                      AND (ob.id IS NULL OR ob.status = 'deleted')
                    LIMIT %d
                    """.formatted(SAMPLE_LIMIT), String.class);
            log.warn("reconciliation: active file_version 指向缺失/已删除对象 count={} sample={}", c, sample);
        }
        return c;
    }

    /** 检测 2：孤儿对象——available 且 ref_count=0，越过 1 天宽限期（去重竞争残留等）。 */
    private long checkOrphanObjects() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM object_blobs "
                        + "WHERE status = 'available' AND ref_count = 0 "
                        + "AND created_at < now() - interval '1 day'", Long.class);
        long c = count == null ? 0 : count;
        if (c > 0) {
            List<String> sample = jdbc.queryForList(
                    "SELECT public_id::text FROM object_blobs "
                            + "WHERE status = 'available' AND ref_count = 0 "
                            + "AND created_at < now() - interval '1 day' "
                            + "LIMIT " + SAMPLE_LIMIT, String.class);
            log.warn("reconciliation: 孤儿对象（available 且 ref_count=0，超 1 天宽限）count={} sample={}",
                    c, sample);
        }
        return c;
    }

    /** 检测 3：ref_count 与 staging/active 实际引用数不一致（V7：ref_count 由事务维护）。 */
    private long checkRefCountMismatches() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM object_blobs ob WHERE ob.ref_count <> ("
                        + "SELECT count(*) FROM file_versions fv "
                        + "WHERE fv.object_blob_id = ob.id AND fv.status IN ('staging','active'))",
                Long.class);
        long c = count == null ? 0 : count;
        if (c > 0) {
            List<String> sample = jdbc.queryForList(
                    "SELECT ob.public_id::text || ':ref=' || ob.ref_count || ':actual=' || ("
                            + "SELECT count(*) FROM file_versions fv "
                            + "WHERE fv.object_blob_id = ob.id AND fv.status IN ('staging','active')) "
                            + "FROM object_blobs ob WHERE ob.ref_count <> ("
                            + "SELECT count(*) FROM file_versions fv "
                            + "WHERE fv.object_blob_id = ob.id AND fv.status IN ('staging','active')) "
                            + "LIMIT " + SAMPLE_LIMIT, String.class);
            log.warn("reconciliation: ref_count 与实际引用不一致 count={} sample={}", c, sample);
        }
        return c;
    }

    /** 检测 4：非终态会话过期超 1 小时（Janitor 正常应已终结；残留即清理链路故障），仅计数。 */
    private long countStaleUploads() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM upload_sessions "
                        + "WHERE status IN " + NON_TERMINAL_UPLOAD_STATUSES
                        + " AND expires_at < now() - interval '1 hour'", Long.class);
        long c = count == null ? 0 : count;
        if (c > 0) {
            log.warn("reconciliation: 未终结 Multipart/过期会话超 1 小时 count={}", c);
        }
        return c;
    }
}
