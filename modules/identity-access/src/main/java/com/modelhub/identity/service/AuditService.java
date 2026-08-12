package com.modelhub.identity.service;

import com.modelhub.identity.domain.AuditLogEntity;
import com.modelhub.identity.repo.AuditLogRepository;
import com.modelhub.shared.web.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计服务（02 §2）：只追加；失败不阻断主流程但必须告警。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(String actor, String action, String resource, String result,
                       String ip, String userAgent, String detailsJson) {
        try {
            AuditLogEntity e = new AuditLogEntity();
            e.setActor(actor);
            e.setAction(action);
            e.setResource(resource);
            e.setResult(result);
            e.setTraceId(TraceContext.currentTraceId());
            e.setIp(ip);
            e.setUserAgent(userAgent);
            e.setDetails(detailsJson);
            auditLogs.save(e);
        } catch (RuntimeException ex) {
            log.error("audit append failed action={} actor={}", action, actor, ex);
            throw ex;
        }
    }

    public void appendSimple(String actor, String action, String resource, String result) {
        append(actor, action, resource, result, null, null, null);
    }
}
