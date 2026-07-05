package com.aihub.mcp.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 写操作确认令牌。
 *
 * <p>绑定 Principal + Tool + 参数摘要 + 过期时间 + 单次使用。
 * 客户端在调用写 Tool 前需先获取确认令牌，然后在写 Tool 参数中附带该令牌。
 * 服务端校验令牌一致性后执行操作，用后即焚。
 */
public record ConfirmationToken(
        String tokenId,
        String principalId,
        String toolName,
        String argumentsDigest,
        Instant expiresAt,
        boolean consumed) {

    /** 创建新确认令牌（5 分钟有效期）。 */
    public static ConfirmationToken create(String principalId, String toolName,
                                            Map<String, Object> arguments) {
        String tokenId = UUID.randomUUID().toString();
        String digest = computeDigest(arguments);
        Instant expiresAt = Instant.now().plusSeconds(300);
        return new ConfirmationToken(tokenId, principalId, toolName, digest, expiresAt, false);
    }

    /** 校验令牌是否与当前调用匹配且未过期未消费。 */
    public void validate(String callerPrincipalId, String callerToolName,
                         Map<String, Object> callerArguments) {
        if (consumed) {
            throw new IllegalStateException("Confirmation token already consumed");
        }
        if (Instant.now().isAfter(expiresAt)) {
            throw new IllegalStateException("Confirmation token expired");
        }
        if (!principalId.equals(callerPrincipalId)) {
            throw new IllegalStateException("Confirmation token principal mismatch");
        }
        if (!toolName.equals(callerToolName)) {
            throw new IllegalStateException("Confirmation token tool mismatch");
        }
        String callerDigest = computeDigest(callerArguments);
        if (!argumentsDigest.equals(callerDigest)) {
            throw new IllegalStateException("Confirmation token arguments mismatch");
        }
    }

    /** 返回已消费状态的令牌副本。 */
    public ConfirmationToken markConsumed() {
        return new ConfirmationToken(tokenId, principalId, toolName, argumentsDigest, expiresAt, true);
    }

    private static String computeDigest(Map<String, Object> arguments) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String serialized = arguments.toString();
            byte[] hash = md.digest(serialized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
