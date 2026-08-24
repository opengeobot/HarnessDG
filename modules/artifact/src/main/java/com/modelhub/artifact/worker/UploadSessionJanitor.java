package com.modelhub.artifact.worker;

import com.modelhub.artifact.domain.UploadSessionEntity;
import com.modelhub.artifact.repo.UploadSessionRepository;
import com.modelhub.artifact.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 上传会话过期收敛（05 §5/§6.1）：UploadService.checkNotExpired 只在请求触达时
 * 懒惰过期，客户端失联（initiated/uploading 后不再调用）的会话由本任务主动终结——
 * 条件置 expired 并终止 Provider 侧未完成的 Multipart，释放分片存储。
 * 幂等：重复执行只处理仍处非终态且已过期的会话；外部副作用（AbortMultipart）
 * 在数据库认领事务之外执行（05 §6.1 禁止在数据库事务中等待对象存储网络调用）。
 */
@Component
public class UploadSessionJanitor {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionJanitor.class);

    /** Janitor 可终结的非终态集合（05 §5；conflict/aborting 由 publish/abort 链路收敛）。 */
    static final Set<String> EXPIRABLE_STATUSES =
            Set.of("initiated", "uploading", "verifying", "scanning", "committing");

    private final UploadSessionRepository sessions;
    private final ObjectStorageService storage;
    private final TransactionTemplate tx;

    public UploadSessionJanitor(UploadSessionRepository sessions, ObjectStorageService storage,
                                TransactionTemplate tx) {
        this.sessions = sessions;
        this.storage = storage;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${modelhub.artifact.janitor-interval-ms:60000}")
    public void sweep() {
        expireOverdueSessions();
    }

    /** 主动过期已到期的非终态会话，返回本次终结数（幂等：无到期会话返回 0）。 */
    public int expireOverdueSessions() {
        List<UploadSessionEntity> due =
                sessions.findByExpiresAtBeforeAndStatusIn(OffsetDateTime.now(), EXPIRABLE_STATUSES);
        int expired = 0;
        for (UploadSessionEntity s : due) {
            if (expireOne(s)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expireOne(UploadSessionEntity s) {
        String providerUploadId = s.getProviderUploadId();
        String objectKey = s.getObjectKey();
        // 事务内条件认领：并发 complete/abort 已推进状态时跳过（05 §5 状态机 CAS）
        boolean claimed = Boolean.TRUE.equals(tx.execute(t -> {
            UploadSessionEntity locked = sessions.findByIdForUpdate(s.getId()).orElse(null);
            if (locked == null || !EXPIRABLE_STATUSES.contains(locked.getStatus())) {
                return false;
            }
            locked.setStatus("expired");
            locked.setUpdatedAt(OffsetDateTime.now());
            sessions.save(locked);
            return true;
        }));
        if (!claimed) {
            return false;
        }
        if (providerUploadId != null) {
            try {
                storage.abortMultipart(objectKey, providerUploadId);
            } catch (Exception e) {
                // DB 已终态、Multipart 残留仅浪费分片存储：告警不回滚（对账任务可见）
                log.warn("过期会话 Multipart 终止失败 uploadId={} key={}: {}",
                        s.getPublicId(), objectKey, e.getMessage());
            }
        }
        log.info("上传会话已过期 uploadId={} repositoryId={} providerUploadId={}",
                s.getPublicId(), s.getRepositoryId(), providerUploadId);
        return true;
    }
}
