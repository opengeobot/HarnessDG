/*
 * 功能: 发布审批法定人数策略——高敏感资产需两名独立审批人。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.application;

import java.util.Set;

/**
 * 发布审批法定人数策略。
 *
 * <p>资产 {@code sensitivityCode} 为 {@code CONFIDENTIAL}/{@code SECRET}（或别名
 * {@code HIGH}/{@code RESTRICTED}）时需 2 名不同 Principal 的 {@code APPROVE} 决策；
 * 其余敏感级别 1 名即可。
 */
public final class ReviewQuorumPolicy {

    private static final Set<String> DUAL_APPROVAL_SENSITIVITIES = Set.of(
            "CONFIDENTIAL", "SECRET", "HIGH", "RESTRICTED");

    private ReviewQuorumPolicy() {
    }

    public static int requiredApprovals(String sensitivityCode) {
        if (sensitivityCode == null || sensitivityCode.isBlank()) {
            return 1;
        }
        return DUAL_APPROVAL_SENSITIVITIES.contains(sensitivityCode.trim().toUpperCase()) ? 2 : 1;
    }
}
