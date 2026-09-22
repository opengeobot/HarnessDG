/*
 * 功能: 幂等记录只读应用服务——编排查询端口，不含持久化细节。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.job.application;

import com.aihub.job.domain.IdempotencyQueryPort;
import com.aihub.job.domain.IdempotencyRecordSummary;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 幂等记录只读应用服务。
 */
@Service
public class IdempotencyQueryService {

    private final IdempotencyQueryPort queryPort;

    public IdempotencyQueryService(IdempotencyQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    /** 列出最近完成的幂等记录。 */
    public List<IdempotencyRecordSummary> listRecords(int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 200);
        return queryPort.listRecent(effectiveLimit);
    }
}
