/*
 * 功能: Gitea RestClient 装配——统一注入基址、鉴权头与有限超时。
 * 时间: 2026-09-20
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Gitea {@link RestClient} 的统一装配点。
 *
 * <p>存在的理由是<b>超时不能被遗漏</b>：建仓作业在可靠任务 Worker 的租约内执行
 * （{@code aihub.job.worker.lease-seconds}，默认 60 秒），只有调用本身有界，
 * 「租约时长显著大于单次执行时间」这一前提才成立。一旦某个调用点自行构造
 * {@code RestClient} 而忘记设置超时，Gitea 无响应时该次尝试会跨越租约边界，
 * 任务被第二个 Worker 重新领取并重复执行。因此所有 Gitea 调用点必须经此装配。
 *
 * <p>不新增依赖：使用 Spring 内置的 {@link SimpleClientHttpRequestFactory}。
 */
final class GiteaRestClientFactory {

    private GiteaRestClientFactory() {
    }

    /**
     * 构造带有限连接/读取超时与 Token 鉴权头的客户端。
     *
     * <p>Token 只作为请求头下发，绝不进入 URL 或日志。
     */
    static RestClient create(RestClient.Builder builder, GiteaProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "token " + properties.token())
                .requestFactory(requestFactory)
                .build();
    }
}
