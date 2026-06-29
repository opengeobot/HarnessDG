/*
 * 功能: 资产 Git 仓库引用，记录 Gitea 仓库的全名与访问地址。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 资产 Git 仓库引用。
 *
 * <p>Gitea 是仓库、卡片与清单的事实源；平台只保存仓库引用用于跳转与克隆，不在数据库镜像仓库内容。
 *
 * @param fullName 仓库全名（owner/repo）
 * @param htmlUrl  仓库 Web 地址
 * @param cloneUrl 仓库 Git 克隆地址
 */
public record AssetRepositoryRef(String fullName, String htmlUrl, String cloneUrl) {
}
