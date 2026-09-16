/*
 * 功能: 性能测试数据生成器——为性能基准测试生成批量 SQL INSERT 语句，覆盖 asset、
 *       version、artifact、audit_log 等核心表。支持参数化批量插入。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.performance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 性能测试数据生成器。
 *
 * <p>使用 JdbcTemplate 执行批量 INSERT，生成符合 Schema 约束的随机测试数据。
 * 生成的数据分布接近生产模式：80% MODEL + 20% DATASET、多种 visibility/framework/task 分布。
 */
@Component
public class PerformanceFixtureGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String[] FRAMEWORKS = {"pytorch", "tensorflow", "onnx", "vllm", "safetensors"};
    private static final String[] TASKS = {"text-generation", "classification", "embedding", "image-generation", "chat"};
    private static final String[] ARCHITECTURES = {"decoder-only", "encoder-decoder", "transformer", "resnet", "unet"};
    private static final String[] FORMATS = {"parquet", "csv", "json", "tfrecord", "image"};
    private static final String[] MODALITIES = {"text", "image", "audio", "multimodal"};
    private static final String[] VISIBILITIES = {"PUBLIC", "INTERNAL", "PRIVATE"};
    private static final String[] STATUSES = {"ACTIVE", "DEPRECATED"};
    private static final String[] LICENSES = {"Apache-2.0", "MIT", "CC-BY-4.0", "GPL-3.0", "BSD-3-Clause"};
    private static final String[] NAMESPACES = {"nlp", "cv", "audio", "multimodal", "platform", "research"};
    private static final String[] AUDIT_EVENTS = {
            "ASSET_CREATED", "ASSET_UPDATED", "ASSET_DELETED",
            "DOWNLOAD_TICKET_ISSUED", "ROLE_CREATED", "LOGIN_SUCCESSFUL",
            "AUTH_PERMISSION_DENIED", "TOKEN_REFRESHED"
    };
    private static final String[] AUDIT_RESULTS = {"SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "FAILED", "DENIED"};
    private static final String[] PRINCIPAL_TYPES = {"USER", "AGENT", "SERVICE"};
    private static final String[] RESOURCE_TYPES = {"ASSET", "ROLE", "USER", "CONFIGURATION", "VERSION"};
    private static final String[] AUDIT_ACTIONS = {
            "asset:create", "asset:update", "asset:delete",
            "download:presign", "role:create", "auth:login",
            "auth:permission_check", "token:refresh"
    };

    private static final String ASSET_INSERT_SQL = """
            INSERT INTO asset (asset_id, type, namespace, name, display_name, description,
                               visibility, status, owners, tags, license, provisioning_status,
                               created_by, updated_by, created_at, updated_at, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String ASSET_MODEL_INSERT_SQL = """
            INSERT INTO asset_model (asset_id, framework, task, architecture,
                                     parameter_scale, precision, weight_format, runtime)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String ASSET_DATASET_INSERT_SQL = """
            INSERT INTO asset_dataset (asset_id, format, modality,
                                       sample_count, total_bytes)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String VERSION_INSERT_SQL = """
            INSERT INTO asset_version (version_id, asset_id, version, status,
                                       source_commit, manifest_digest, git_tag,
                                       published_at, published_by, notes, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String ARTIFACT_INSERT_SQL = """
            INSERT INTO version_artifact (artifact_id, version_id, path, dvc_hash, sha256, size, media_type)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String AUDIT_LOG_INSERT_SQL = """
            INSERT INTO audit_log (audit_id, event_type, principal_id, principal_type,
                                   action, resource_type, resource_id, result,
                                   error_code, trace_id, occurred_at, row_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * 生成 N 条 asset 记录（含扩展表）。80% MODEL + 20% DATASET 分布。
     *
     * @param jdbcTemplate 数据库连接
     * @param count        生成数量
     * @return 生成的 asset_id 列表
     */
    public List<String> generateAssets(JdbcTemplate jdbcTemplate, int count) {
        List<String> assetIds = new ArrayList<>(count);
        List<Object[]> assetBatch = new ArrayList<>(count);
        List<Object[]> modelBatch = new ArrayList<>();
        List<Object[]> datasetBatch = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String assetId = generateAssetId(i);
            boolean isModel = RANDOM.nextDouble() < 0.8;
            String type = isModel ? "MODEL" : "DATASET";
            String namespace = pick(NAMESPACES);
            String name = (isModel ? "model" : "dataset") + "-" + i;
            String displayName = "Test " + (isModel ? "Model" : "Dataset") + " #" + i;
            String description = "Performance test " + type.toLowerCase() + " asset number " + i
                    + " with sample description for full-text search and keyword matching";
            String visibility = pick(VISIBILITIES);
            String status = pick(STATUSES);
            String owners = "[\"team-" + namespace + "\"]";
            String tags = "[\"" + pick(FRAMEWORKS) + "\", \"perf-test\"]";
            String license = pick(LICENSES);
            String createdBy = "usr_perf_" + (i % 10);

            assetBatch.add(new Object[]{
                    assetId, type, namespace, name, displayName, description,
                    visibility, status, owners, tags, license, "COMPLETED",
                    createdBy, createdBy, Instant.now(), Instant.now(), 0
            });
            assetIds.add(assetId);

            if (isModel) {
                modelBatch.add(new Object[]{
                        assetId, pick(FRAMEWORKS), pick(TASKS), pick(ARCHITECTURES),
                        pick(new String[]{"7B", "13B", "70B", "175B"}),
                        pick(new String[]{"fp16", "bf16", "int8"}),
                        pick(new String[]{"safetensors", "gguf", "bin"}),
                        pick(new String[]{"onnx", "vllm", "triton"})
                });
            } else {
                datasetBatch.add(new Object[]{
                        assetId, pick(FORMATS), pick(MODALITIES),
                        RANDOM.nextInt(100000), RANDOM.nextLong(1024, 1_073_741_824L)
                });
            }
        }

        jdbcTemplate.batchUpdate(ASSET_INSERT_SQL, assetBatch);
        if (!modelBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(ASSET_MODEL_INSERT_SQL, modelBatch);
        }
        if (!datasetBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(ASSET_DATASET_INSERT_SQL, datasetBatch);
        }

        return assetIds;
    }

    /**
     * 为已有资产生成版本记录。每个资产生成指定数量的版本（含 artifact）。
     *
     * @param jdbcTemplate      数据库连接
     * @param assetIds          已有的 asset_id 列表
     * @param versionsPerAsset  每个资产的版本数
     */
    public void generateVersions(JdbcTemplate jdbcTemplate, List<String> assetIds, int versionsPerAsset) {
        if (assetIds.isEmpty()) {
            return;
        }

        List<Object[]> versionBatch = new ArrayList<>();
        List<Object[]> artifactBatch = new ArrayList<>();
        int seq = 0;

        for (String assetId : assetIds) {
            for (int v = 0; v < versionsPerAsset; v++) {
                String versionId = "ver_perf_" + seq;
                String versionStr = String.format("v%d.%d.0", v + 1, RANDOM.nextInt(10));
                String status = RANDOM.nextDouble() < 0.6 ? "PUBLISHED" : "DRAFT";
                String commitSha = String.format("%040x", RANDOM.nextLong());
                String digest = String.format("%064x", RANDOM.nextLong());
                String gitTag = versionStr + (status.equals("PUBLISHED") ? "-released" : "");
                Instant now = Instant.now();
                Instant publishedAt = status.equals("PUBLISHED") ? now : null;
                String publishedBy = status.equals("PUBLISHED") ? "usr_perf_pub" : null;

                versionBatch.add(new Object[]{
                        versionId, assetId, versionStr, status,
                        commitSha, digest, gitTag,
                        publishedAt, publishedBy, "perf test version",
                        "usr_perf_creator", now, now
                });

                // 每个版本 1-3 个 artifact
                int artifactCount = RANDOM.nextInt(3) + 1;
                for (int a = 0; a < artifactCount; a++) {
                    String artifactId = "art_perf_" + (seq * 10 + a);
                    String path = "models/" + assetId + "/" + versionStr + "/"
                            + pick(new String[]{"model.bin", "config.json", "tokenizer.json", "README.md"});
                    String dvcHash = String.format("0%051d", RANDOM.nextLong());
                    String sha256 = String.format("%064x", RANDOM.nextLong());
                    long size = RANDOM.nextLong(1024, 1_073_741_824L);
                    String mediaType = pick(new String[]{"application/octet-stream", "application/json", "text/plain"});

                    artifactBatch.add(new Object[]{
                            artifactId, versionId, path, dvcHash, sha256, size, mediaType
                    });
                }
                seq++;
            }
        }

        jdbcTemplate.batchUpdate(VERSION_INSERT_SQL, versionBatch);
        if (!artifactBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(ARTIFACT_INSERT_SQL, artifactBatch);
        }
    }

    /**
     * 生成 N 条审计日志记录。
     *
     * @param jdbcTemplate 数据库连接
     * @param count        生成数量
     */
    public void generateAuditLogs(JdbcTemplate jdbcTemplate, int count) {
        List<Object[]> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String auditId = "aud_perf_" + i;
            int eventIdx = RANDOM.nextInt(AUDIT_EVENTS.length);
            String eventType = AUDIT_EVENTS[eventIdx];
            String action = AUDIT_ACTIONS[eventIdx];
            String principalId = "usr_perf_" + RANDOM.nextInt(50);
            String principalType = pick(PRINCIPAL_TYPES);
            String resourceType = pick(RESOURCE_TYPES);
            String resourceId = resourceType + "_" + RANDOM.nextInt(100);
            String result = pick(AUDIT_RESULTS);
            String errorCode = result.equals("SUCCEEDED") ? null : "PERF_ERR_" + RANDOM.nextInt(10);
            String traceId = "trace-perf-" + i;
            Instant occurredAt = Instant.now().minusSeconds(RANDOM.nextInt(count));

            batch.add(new Object[]{
                    auditId, eventType, principalId, principalType,
                    action, resourceType, resourceId, result,
                    errorCode, traceId, occurredAt, 0
            });
        }
        jdbcTemplate.batchUpdate(AUDIT_LOG_INSERT_SQL, batch);
    }

    private String generateAssetId(int index) {
        return "ast_perf_" + index;
    }

    private <T> T pick(T[] options) {
        return options[RANDOM.nextInt(options.length)];
    }
}