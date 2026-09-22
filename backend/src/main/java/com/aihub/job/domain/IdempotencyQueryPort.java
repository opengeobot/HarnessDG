/*
 * 功能: 幂等记录只读查询端口——领域层不依赖 JDBC 细节。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.job.domain;

import java.util.List;

/**
 * 幂等记录只读查询端口。
 */
public interface IdempotencyQueryPort {

    /**
     * 按创建时间降序列出最近完成的幂等记录。
     *
     * @param limit 最大条数（1–200）
     */
    List<IdempotencyRecordSummary> listRecent(int limit);
}
