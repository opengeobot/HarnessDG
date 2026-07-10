/*
 * 功能: 版本 Git/DVC 物化端口——将 Manifest 与 DVC 指针提交到 Gitea。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.domain;

import java.util.List;
import java.util.Map;

/**
 * 版本 Git 物化端口。
 *
 * <p>将 manifest.json 与 .dvc 指针写入资产 Git 仓库并返回 sourceCommit。
 */
public interface VersionMaterializationPort {

    /**
     * 提交版本物化内容到 Git。
     *
     * @param request 物化请求
     * @return 物化结果（含 sourceCommit）
     */
    MaterializationResult materialize(MaterializationRequest request);

    /** 物化请求。 */
    record MaterializationRequest(
            String repoFullName,
            String versionLiteral,
            String manifestJson,
            List<DvcPointer> dvcPointers) {
    }

    /** DVC 指针文件。 */
    record DvcPointer(String dvcFilePath, String content) {
    }

    /** 物化结果。 */
    record MaterializationResult(String sourceCommit, boolean giteaBacked, String note) {
    }
}
