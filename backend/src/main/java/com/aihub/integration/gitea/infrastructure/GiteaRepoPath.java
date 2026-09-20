/*
 * 功能: Gitea 仓库路径值对象——把 owner/repo 全名拆成两个独立路径段，避免斜杠被百分号编码。
 * 时间: 2026-09-20
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import java.util.Map;

/**
 * Gitea 仓库路径（{@code owner}/{@code repo}）。
 *
 * <p>Gitea 的仓库接口形如 {@code /api/v1/repos/{owner}/{repo}/...}。
 * 仓库全名（形如 {@code owner/repo}）若整体作为 <b>单个</b> URI 模板变量传入，
 * Spring 的 {@code DefaultUriBuilderFactory} 会在 {@code TEMPLATE_AND_VALUES} 模式下
 * 把值里的 {@code /} 百分号编码为 {@code %2F}，请求落到
 * {@code /api/v1/repos/owner%2Frepo/...} 并返回 404。因此必须拆成两段分别传参。
 *
 * <p>实测对照（同一容器、同一 Token、同一仓库）：
 * <pre>
 *   GET /api/v1/repos/owner/repo/contents/README.md?ref=main   -&gt; 200
 *   GET /api/v1/repos/owner%2Frepo/contents/README.md?ref=main -&gt; 404
 * </pre>
 *
 * @param owner 仓库所有者（组织或用户名）
 * @param repo  仓库名
 */
record GiteaRepoPath(String owner, String repo) {

    /**
     * 解析仓库全名。
     *
     * <p>只接受恰好一个 {@code /} 且两侧非空的形式；其他形式以参数校验错误明确拒绝，
     * 而不是发出一个注定 404 的请求去猜测。
     */
    static GiteaRepoPath parse(String fullName) {
        if (fullName == null) {
            throw new ValidationException(ErrorCode.COMMON_INVALID_ARGUMENT,
                    "gitea repository full_name is missing", Map.of());
        }
        int slash = fullName.indexOf('/');
        if (slash <= 0 || slash != fullName.lastIndexOf('/') || slash == fullName.length() - 1) {
            throw new ValidationException(ErrorCode.COMMON_INVALID_ARGUMENT,
                    "gitea repository full_name must be exactly owner/repo", Map.of());
        }
        return new GiteaRepoPath(fullName.substring(0, slash), fullName.substring(slash + 1));
    }

    String fullName() {
        return owner + "/" + repo;
    }
}
