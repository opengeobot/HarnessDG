/*
 * 功能: MCP 下载 handle 视图，不含预签名 URL，避免 secrets 进入 Tool 文本响应。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.mcp.application;

import com.aihub.transfer.application.DownloadApplicationService.DownloadTicket;
import java.time.Instant;

/**
 * MCP 下载 handle，引用服务端票据字段，不包含 presignedUrl。
 *
 * @param versionId         版本 ID（handle 主键）
 * @param artifactId        工件 ID（可空）
 * @param method            下载方法（PRESIGNED_URL / GIT_DVC）
 * @param expiresAt         过期时间
 * @param fileName          文件名（可空）
 * @param fileSize          文件大小（可空）
 * @param gitCloneUrl       Git 克隆地址（GIT_DVC，可空）
 * @param revision          修订标识（可空）
 * @param dvcCredentialsUrl DVC 凭据端点（可空）
 * @param assetId           资产 ID
 */
public record McpDownloadHandle(String versionId,
                                String artifactId,
                                String method,
                                Instant expiresAt,
                                String fileName,
                                Long fileSize,
                                String gitCloneUrl,
                                String revision,
                                String dvcCredentialsUrl,
                                String assetId) {

    /**
     * 由下载票据构造 MCP handle，剥离 presignedUrl。
     */
    public static McpDownloadHandle fromTicket(String versionId, String artifactId, DownloadTicket ticket) {
        return new McpDownloadHandle(
                versionId,
                artifactId,
                ticket.method(),
                ticket.expiresAt(),
                ticket.fileName(),
                ticket.fileSize(),
                ticket.gitCloneUrl(),
                ticket.revision(),
                ticket.dvcCredentialsUrl(),
                ticket.assetId());
    }
}
