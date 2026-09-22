/*
 * 功能: 创建资产 ACL REST 请求体。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import java.util.List;

/**
 * 创建资产 ACL 请求体。
 *
 * @param principalId      被授权主体 ID
 * @param permissionCodes  权限编码列表（如 asset:read）
 */
public record CreateAssetAccessRequest(String principalId, List<String> permissionCodes) {
}
