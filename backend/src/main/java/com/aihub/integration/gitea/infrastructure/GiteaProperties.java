/*
 * 功能: Gitea 集成配置属性，绑定部署期 Endpoint、令牌、超时与功能开关。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gitea 集成配置（前缀 {@code aihub.gitea}）。
 *
 * <p>令牌等敏感信息来自环境变量/Secret，不落库、不入日志。{@code enabled} 默认关闭，便于在无 Gitea
 * 的开发/测试环境使用 Noop 仓库开通器；Compose 环境置为 {@code true} 接通真实 Gitea。
 *
 * <p><b>超时必须有限</b>：建仓作业由可靠任务 Worker 以租约方式执行，租约时长
 * （{@code aihub.job.worker.lease-seconds}，默认 60 秒）必须显著大于单次 Handler 的最大执行时间。
 * 若 HTTP 调用可以无限期阻塞，该前提不成立——实测 Gitea 处于 pause 状态时单次尝试阻塞 122 秒，
 * 租约在尝试未结束时到期，任务被另一个 Worker 重新领取并重复执行，进而触发
 * {@code ASSET_CONCURRENT_MODIFICATION} 这类伪冲突。因此连接与读取超时必须为有限值。
 *
 * <p><b>默认值的量级依据</b>：{@code provision} 在一次尝试内最多串行发起 4 次 HTTP 调用
 * （建组织仓库 → 回退用户仓库 → 写 {@code asset.yaml} → 覆盖 {@code README.md}，
 * 其中写文件各自还需一次读 sha）。因此最坏耗时约为 {@code 4 × readTimeoutMillis}，
 * 它必须小于租约时长：默认 {@code 4 × 10s = 40s < 60s}，留有约 1/3 余量。
 * 若部署时调大读取超时或调小租约，必须同时复核该不等式，
 * 否则将退化为「尝试跨越租约 → 重复领取」。
 * 正常路径下实测单次调用约 0.8 秒，10 秒约有 12 倍余量。
 *
 * @param enabled              是否启用真实 Gitea 仓库开通
 * @param baseUrl              Gitea 基础地址（如 http://gitea:3000）
 * @param token                服务账号令牌（敏感，禁止记录）
 * @param defaultBranch        仓库默认分支
 * @param connectTimeoutMillis 建立连接超时（毫秒），必须为正
 * @param readTimeoutMillis    读取响应超时（毫秒），必须为正且满足 4 × 本值 < 任务租约时长
 * @param webhookSecret        Webhook HMAC-SHA256 密钥（敏感，禁止记录）；入站验签与出站注册共用同一值
 * @param webhookUrl           平台入站回调地址（如 http://backend:8080/api/v1/webhooks/gitea）；
 *                             为空则跳过建仓后的 push webhook 注册（无 Gitea 的开发环境保持现状）
 */
@ConfigurationProperties(prefix = "aihub.gitea")
public record GiteaProperties(
        boolean enabled,
        String baseUrl,
        String token,
        String defaultBranch,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        String webhookSecret,
        String webhookUrl) {

    /** 连接超时默认值：与平台其它外部依赖保持同一量级。 */
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
    /** 读取超时默认值：见类注释，4 × 10s = 40s 小于默认 60s 租约。 */
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;

    public GiteaProperties {
        if (defaultBranch == null || defaultBranch.isBlank()) {
            defaultBranch = "main";
        }
        if (connectTimeoutMillis <= 0) {
            connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        }
        if (readTimeoutMillis <= 0) {
            readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
        }
        // 归一化为非 null：入站验签与出站注册都以「空=未配置」判定，避免各处 null 检查漂移。
        if (webhookSecret == null) {
            webhookSecret = "";
        }
        if (webhookUrl == null) {
            webhookUrl = "";
        }
    }

    /** 是否已配置可用的 push webhook 注册（回调地址与密钥齐备）。 */
    public boolean webhookRegistrationEnabled() {
        return !webhookUrl.isBlank() && !webhookSecret.isBlank();
    }
}
