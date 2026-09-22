/*
 * 功能: 弃用资产 REST 请求体。
 * 时间: 2026-07-08
 * 作者: AxeXie
 */
package com.aihub.asset.api;

/**
 * 弃用资产请求体。
 *
 * @param deprecationReason   弃用原因编码（如 REPLACED/OUTDATED/SECURITY_ISSUE/UNSUPPORTED）
 * @param deprecationNote     弃用说明（自由文本，可空）
 * @param replacementAssetId  替代资产 ID（可空）
 */
public record DeprecateAssetRequest(String deprecationReason,
                                    String deprecationNote,
                                    String replacementAssetId) {
}
