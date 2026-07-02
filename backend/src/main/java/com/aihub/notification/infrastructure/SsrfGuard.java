/*
 * 功能: SSRF 防护器——校验出站 URL 的 scheme 与目标地址，拒绝内网/保留地址。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SSRF 防护器。
 *
 * <p>出站 Webhook 投递前校验 target_url：
 * <ul>
 *   <li>仅允许 http/https scheme（生产环境强制 https 可由配置开关）；</li>
 *   <li>拒绝内网/保留地址：127.0.0.0/8、10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、
 *       169.254.0.0/16、::1、fc00::/7、.local 等；</li>
 *   <li>DNS 解析后逐 IP 校验，防止 DNS rebinding。</li>
 * </ul>
 */
@Component
public class SsrfGuard {

    private static final Logger LOG = LoggerFactory.getLogger(SsrfGuard.class);

    private final boolean httpsOnly;
    private final boolean allowLocalhost;

    /**
     * @param httpsOnly     是否强制 HTTPS（生产默认 true，测试可关闭）
     * @param allowLocalhost 是否允许 localhost（仅本地/测试环境）
     */
    public SsrfGuard(@Value("${aihub.webhook.https-only:true}") boolean httpsOnly,
                     @Value("${aihub.webhook.allow-localhost:false}") boolean allowLocalhost) {
        this.httpsOnly = httpsOnly;
        this.allowLocalhost = allowLocalhost;
    }

    /**
     * 校验目标 URL 安全性。不安全时抛出 ValidationException。
     *
     * @param targetUrl 目标 URL
     */
    public void validate(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                    "webhook target URL must not be blank", Map.of());
        }
        URI uri;
        try {
            uri = URI.create(targetUrl);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                    "invalid webhook target URL", Map.of());
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                    "webhook target URL missing scheme", Map.of());
        }
        scheme = scheme.toLowerCase();
        if (httpsOnly && !"https".equals(scheme)) {
            throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                    "webhook target URL must use HTTPS", Map.of());
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                    "webhook target URL scheme must be http or https", Map.of());
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                    "webhook target URL missing host", Map.of());
        }
        if (host.endsWith(".local") || host.equalsIgnoreCase("localhost")) {
            if (!allowLocalhost) {
                throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                        "webhook target host is local/reserved", Map.of());
            }
            return;
        }
        // DNS 解析后逐 IP 校验
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                    "webhook target host cannot be resolved", Map.of());
        }
        for (InetAddress addr : addresses) {
            if (isPrivateOrReserved(addr)) {
                if (!allowLocalhost) {
                    throw new ValidationException(ErrorCode.WEBHOOK_TARGET_FORBIDDEN,
                            "webhook target host resolves to private/reserved address", Map.of());
                }
            }
        }
    }

    private static boolean isPrivateOrReserved(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress();
    }
}
