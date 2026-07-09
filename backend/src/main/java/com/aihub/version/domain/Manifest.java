package com.aihub.version.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    /** Manifest schema 版本标识。 */
    public static final String SCHEMA_VERSION = "aihub/manifest-v1";

    /**
     * 构建 aihub/manifest-v1 结构 Manifest。
     *
     * <p>digest 计算排除 {@code generatedAt} 等易变字段；artifacts 按 path 字典序排列。
     */
    public static Manifest forVersion(String assetId, String versionId,
                                      List<ArtifactEntry> artifacts) {
        List<Map<String, Object>> artifactMaps = artifacts.stream()
                .sorted(Comparator.comparing(ArtifactEntry::path))
                .map(ArtifactEntry::toMap)
                .toList();
        Map<String, Object> manifestEntries = new LinkedHashMap<>();
        manifestEntries.put("schemaVersion", SCHEMA_VERSION);
        manifestEntries.put("assetId", assetId);
        manifestEntries.put("versionId", versionId);
        manifestEntries.put("artifacts", artifactMaps);
        return new Manifest(manifestEntries);
    }

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
            if ("generatedAt".equals(entry.getKey())) {
                continue;
            }
            if (value instanceof List<?> list) {
                String serialized = serializeList(list);
                if (serialized.isBlank()) continue;
                sb.append(entry.getKey()).append("=").append(serialized).append("\n");
            } else {
                String strVal = value.toString();
                if (strVal.isBlank()) continue;
                sb.append(entry.getKey()).append("=").append(strVal).append("\n");
            }
        }
        return sb.toString();
    }

    private String serializeList(List<?> list) {
        if (list.isEmpty()) {
            return "";
        }
        if (list.get(0) instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> maps = (List<Map<String, Object>>) list;
            return maps.stream()
                    .map(this::serializeArtifactMap)
                    .sorted()
                    .collect(Collectors.joining(";"));
        }
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String serializeArtifactMap(Map<String, Object> artifact) {
        TreeMap<String, Object> sorted = new TreeMap<>(artifact);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            String strVal = value.toString();
            if (strVal.isBlank()) continue;
            parts.add(entry.getKey() + ":" + strVal);
        }
        return String.join("|", parts);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** Manifest 工件条目（用于 digest 计算的稳定字段子集）。 */
    public record ArtifactEntry(String path, String sha256, long size, String mediaType) {

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", path);
            map.put("sha256", sha256);
            map.put("size", size);
            if (mediaType != null && !mediaType.isBlank()) {
                map.put("mediaType", mediaType);
            }
            return map;
        }
    }
}
