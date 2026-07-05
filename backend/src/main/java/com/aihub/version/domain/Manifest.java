package com.aihub.version.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Manifest 规范化值对象。
 *
 * <p>Manifest 用于描述版本的完整文件清单，规范化后计算 SHA-256 摘要作为版本不可变指纹。
 * 规范化规则：UTF-8 编码、LF 换行、Unicode NFC、键排序、数组排序。
 */
public record Manifest(Map<String, Object> entries) {

    /**
     * 计算规范化后的 SHA-256 摘要。
     *
     * <p>规范化步骤：
     * 1. 键按 Unicode 字典序排序
     * 2. 数组值按字典序排序
     * 3. 空值字段剔除
     * 4. 序列化为 key=value 格式，LF 分隔
     * 5. SHA-256 哈希
     */
    public String computeDigest() {
        String canonical = canonicalize();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /** 规范化为确定性字符串表示。 */
    public String canonicalize() {
        TreeMap<String, Object> sorted = new TreeMap<>(entries);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            if (value instanceof List<?> list) {
                List<String> sortedList = list.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .sorted()
                        .collect(Collectors.toList());
                if (sortedList.isEmpty()) continue;
                sb.append(entry.getKey()).append("=")
                        .append(String.join(",", sortedList)).append("\n");
            } else {
                String strVal = value.toString();
                if (strVal.isBlank()) continue;
                sb.append(entry.getKey()).append("=").append(strVal).append("\n");
            }
        }
        return sb.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
