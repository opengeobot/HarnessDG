/*
 * 功能: 资产 Card 投影端口——从 Gitea 锁定 Commit 读取 asset.yaml/README.md。
 * 时间: 2026-07-04
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 资产 Card 投影端口（Port）。
 *
 * <p>由资产模块定义、集成适配器实现。从 Gitea 仓库的指定 Commit 读取 {@code asset.yaml} 和
 * {@code README.md}，返回投影内容。平台在 PG 存储带 {@code sourceCommit} 的投影，
 * 不存独立权威副本——Gitea 仓库始终是事实源。
 *
 * <p><b>安全标记</b>：投影内容标记为 {@code untrustedContent}，前端渲染时必须经 DOMPurify 等
 * 清洗器过滤，防止 XSS。
 */
public interface AssetCardProjectionPort {

    /**
     * 从仓库指定 Commit 读取卡片文件。
     *
     * @param repoFullName 仓库全名（owner/repo）
     * @param commit       Commit SHA（可空，为空时读取默认分支 HEAD）
     * @return 卡片投影内容
     */
    CardProjection fetchCard(String repoFullName, String commit);

    /**
     * 卡片投影内容。
     *
     * @param readme      README.md 内容（可空）
     * @param assetYaml   asset.yaml 内容（可空）
     * @param sourceCommit 来源 Commit SHA
     */
    record CardProjection(String readme, String assetYaml, String sourceCommit) {
    }
}
