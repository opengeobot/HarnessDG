package com.modelhub.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.shared.idempotency.JdbcIdempotencyService;
import com.modelhub.shared.idempotency.JdbcIdempotencyService.Acquired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Controller 幂等编排（04 §10）：begin 占位/识别重放，finish 回填首次结果。
 * 重放命中时 Controller 直接返回 recordedBody，保证与首次响应逐字节一致。
 */
@Component
public class IdempotentOps {

    private final JdbcIdempotencyService idempotency;

    public IdempotentOps(JdbcIdempotencyService idempotency) {
        this.idempotency = idempotency;
    }

    /** 校验并占位；replay=true 时调用方必须直接返回 recorded 响应。 */
    public Acquired begin(String scope, String keyHeader, JsonNode body) {
        String key = JdbcIdempotencyService.validateHeader(keyHeader);
        String hash = sha256(body == null || body.isNull() ? "" : body.toString());
        return idempotency.acquire(scope, key, hash);
    }

    public void finish(String scope, String keyHeader, int status, String body) {
        String key = JdbcIdempotencyService.validateHeader(keyHeader);
        idempotency.complete(scope, key, status, body);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
