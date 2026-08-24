package com.modelhub.artifact.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.artifact.domain.FileVersionEntity;
import com.modelhub.artifact.domain.ObjectBlobEntity;
import com.modelhub.artifact.repo.FileVersionRepository;
import com.modelhub.artifact.repo.ObjectBlobRepository;
import com.modelhub.artifact.storage.ObjectStorageService;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.PreviewArtifactEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.catalog.repo.PreviewArtifactRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.JobEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 预览生成 Worker（05 §5）：轮询 jobs 表领取 queued 的 preview.generate，
 * 采样首个文本类文件（优先 .csv/.jsonl/.txt）头部内容为不可变快照，
 * 写入 MinIO previews/{key} 并回填 preview_artifacts。
 * 迟到旧任务保护：仅当该行仍是 (repo, ref) 最新版本行时才写结果（05 §5.1）。
 */
@Component
public class PreviewWorker {

    private static final Logger log = LoggerFactory.getLogger(PreviewWorker.class);
    private static final String JOB_TYPE = "preview.generate";
    private static final int SAMPLE_LIMIT = 50;
    /** 采样源文件大小上限（05 §5.3 限制下载字节数）。 */
    private static final long MAX_SOURCE_BYTES = 16L * 1024 * 1024;

    private final JobRepository jobs;
    private final PreviewArtifactRepository previews;
    private final FileVersionRepository fileVersions;
    private final ObjectBlobRepository blobs;
    private final GitBindingRepository gitBindings;
    private final RepositoryRepository repositories;
    private final GiteaClient gitea;
    private final ObjectStorageService storage;
    private final JobEventService jobEvents;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public PreviewWorker(JobRepository jobs, PreviewArtifactRepository previews,
                         FileVersionRepository fileVersions, ObjectBlobRepository blobs,
                         GitBindingRepository gitBindings, RepositoryRepository repositories,
                         GiteaClient gitea, ObjectStorageService storage, JobEventService jobEvents,
                         TransactionTemplate tx, ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.previews = previews;
        this.fileVersions = fileVersions;
        this.blobs = blobs;
        this.gitBindings = gitBindings;
        this.repositories = repositories;
        this.gitea = gitea;
        this.storage = storage;
        this.jobEvents = jobEvents;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${modelhub.artifact.preview-interval-ms:1000}")
    public void poll() {
        List<JobEntity> queued;
        try {
            queued = jobs.findByJobTypeAndStatusOrderByCreatedAtAsc(JOB_TYPE, "queued");
        } catch (Exception e) {
            log.warn("预览任务轮询失败: {}", e.getMessage());
            return;
        }
        for (JobEntity job : queued) {
            if (claim(job.getId())) {
                try {
                    process(job);
                } catch (Exception e) {
                    log.error("预览生成失败 job={}: {}", job.getPublicId(), e.getMessage(), e);
                    fail(job, "preview_source_unavailable",
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            }
        }
    }

    // ---------- 领取 / 终态回写（05 §1 状态机） ----------

    private boolean claim(Long jobId) {
        return Boolean.TRUE.equals(tx.execute(t -> {
            JobEntity j = jobs.findById(jobId).orElse(null);
            if (j == null || !"queued".equals(j.getStatus())) {
                return false;
            }
            OffsetDateTime now = OffsetDateTime.now();
            j.setStatus("running");
            if (j.getStartedAt() == null) {
                j.setStartedAt(now);
            }
            j.setUpdatedAt(now);
            jobs.save(j);
            jobEvents.record(j.getId(), "status_changed", Map.of("status", "running"));
            return true;
        }));
    }

    private void succeed(JobEntity job, Map<String, Object> summary) {
        tx.executeWithoutResult(t -> {
            JobEntity j = jobs.findById(job.getId()).orElse(null);
            if (j == null) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now();
            j.setStatus("succeeded");
            j.setResultSummary(toJson(summary));
            if (j.getFinishedAt() == null) {
                j.setFinishedAt(now);
            }
            j.setUpdatedAt(now);
            jobs.save(j);
            jobEvents.record(j.getId(), "status_changed", Map.of("status", "succeeded"));
        });
    }

    private void fail(JobEntity job, String errorCode, String message) {
        tx.executeWithoutResult(t -> {
            JobEntity j = jobs.findById(job.getId()).orElse(null);
            if (j == null) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now();
            j.setStatus("failed");
            j.setErrorCode(errorCode);
            j.setErrorMessage(message != null && message.length() > 480 ? message.substring(0, 480) : message);
            if (j.getFinishedAt() == null) {
                j.setFinishedAt(now);
            }
            j.setUpdatedAt(now);
            jobs.save(j);
            jobEvents.record(j.getId(), "status_changed", Map.of("status", "failed"));
        });
    }

    // ---------- 生成逻辑 ----------

    private void process(JobEntity job) throws Exception {
        JsonNode payload = objectMapper.readTree(job.getPayload() == null ? "{}" : job.getPayload());
        long repositoryId = payload.path("repositoryId").asLong();
        String refName = payload.path("ref").asText("");
        String commitSha = payload.path("commitSha").asText("");

        PreviewArtifactEntity row = previews.findByJobId(job.getId()).orElse(null);
        if (row == null) {
            succeed(job, Map.of("status", "orphaned"));
            return;
        }
        RepositoryEntity repo = repositories.findById(repositoryId).orElse(null);
        if (repo == null) {
            updateRowGuarded(row, refName, r -> r.setStatus("failed"));
            fail(job, "repository_missing", "仓库不存在: " + repositoryId);
            return;
        }

        FileVersionEntity source = pickSampleSource(fileVersions.findByRepositoryId(repositoryId));
        if (source == null) {
            // 无文本类文件（或仓库为空）→ unsupported（05 §5.3）
            boolean applied = updateRowGuarded(row, refName, r -> {
                r.setStatus("unsupported");
                r.setSourceCommitSha(commitSha.isEmpty() ? null : commitSha);
            });
            succeed(job, Map.of("status", applied ? "unsupported" : "superseded"));
            return;
        }

        byte[] content = readContent(repo, source, commitSha);
        Sample sample = sample(content, source);
        String sampleJson = objectMapper.writeValueAsString(sample.rows());
        storage.putObject(row.getArtifactKey(), sampleJson.getBytes(StandardCharsets.UTF_8),
                "application/json");
        String manifestHash = sha256Hex(sampleJson);

        boolean applied = updateRowGuarded(row, refName, r -> {
            r.setStatus("ready");
            r.setSourceCommitSha(commitSha);
            r.setManifestHash(manifestHash);
            r.setRowCount(sample.rowCount());
            r.setSampleCount((long) sample.rows().size());
            r.setSampleStrategy("head");
            r.setSample(sampleJson);
        });
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", applied ? "ready" : "superseded");
        summary.put("manifestHash", manifestHash);
        summary.put("rowCount", sample.rowCount());
        summary.put("sampleCount", sample.rows().size());
        summary.put("sourcePath", source.getPath());
        succeed(job, summary);
        log.info("预览生成完成 job={} repo={} path={} rows={} applied={}",
                job.getPublicId(), repositoryId, source.getPath(), sample.rowCount(), applied);
    }

    /** 选取采样源：active 文本类文件，优先 .csv/.jsonl/.txt 扩展名，大小受限。 */
    private FileVersionEntity pickSampleSource(List<FileVersionEntity> versions) {
        List<FileVersionEntity> candidates = versions.stream()
                .filter(fv -> "active".equals(fv.getStatus()))
                .filter(this::isTextLike)
                .filter(fv -> fv.getSizeBytes() > 0 && fv.getSizeBytes() <= MAX_SOURCE_BYTES)
                .toList();
        for (String ext : List.of(".csv", ".jsonl", ".txt")) {
            for (FileVersionEntity fv : candidates) {
                if (fv.getPath().toLowerCase(Locale.ROOT).endsWith(ext)) {
                    return fv;
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean isTextLike(FileVersionEntity fv) {
        String ct = fv.getContentType() == null ? "" : fv.getContentType().toLowerCase(Locale.ROOT);
        if (ct.startsWith("text/") || ct.contains("csv") || ct.contains("jsonl")) {
            return true;
        }
        String p = fv.getPath().toLowerCase(Locale.ROOT);
        return p.endsWith(".csv") || p.endsWith(".jsonl") || p.endsWith(".txt");
    }

    /** 读取源内容：object → MinIO；git → Gitea raw（优先精确 commit ref，回退分支）。 */
    private byte[] readContent(RepositoryEntity repo, FileVersionEntity fv, String commitSha) {
        if ("object".equals(fv.getContentSource())) {
            ObjectBlobEntity blob = fv.getObjectBlobId() == null ? null
                    : blobs.findById(fv.getObjectBlobId()).orElse(null);
            if (blob == null) {
                throw new IllegalStateException("object blob 缺失: " + fv.getPublicId());
            }
            return storage.getObjectBytes(blob.getObjectKey());
        }
        GitBindingEntity binding = gitBindings.findByRepositoryId(repo.getId())
                .orElseThrow(() -> new IllegalStateException("Git 绑定缺失 repositoryId=" + repo.getId()));
        byte[] content = commitSha.isEmpty() ? null
                : gitea.rawFile(binding.getExternalNamespace(), binding.getExternalName(), commitSha, fv.getPath());
        if (content == null) {
            content = gitea.rawFile(binding.getExternalNamespace(), binding.getExternalName(),
                    fv.getBranch(), fv.getPath());
        }
        if (content == null) {
            throw new IllegalStateException("文件内容不可读: " + fv.getPath());
        }
        return content;
    }

    private record Sample(List<Object> rows, long rowCount) {}

    /** 头部采样（05 §5）：CSV → 行数组，JSONL → 对象，其余文本 → 行字符串；rowCount 为全量非空行数。 */
    private Sample sample(byte[] content, FileVersionEntity fv) throws Exception {
        String text = new String(content, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\r?\n", -1)) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        String path = fv.getPath().toLowerCase(Locale.ROOT);
        String ct = fv.getContentType() == null ? "" : fv.getContentType().toLowerCase(Locale.ROOT);
        boolean csv = path.endsWith(".csv") || ct.contains("csv");
        boolean jsonl = path.endsWith(".jsonl") || ct.contains("jsonl");
        List<Object> rows = new ArrayList<>();
        for (String line : lines) {
            if (rows.size() >= SAMPLE_LIMIT) {
                break;
            }
            if (jsonl) {
                rows.add(objectMapper.readValue(line, Object.class));
            } else if (csv) {
                rows.add(splitCsvLine(line));
            } else {
                rows.add(line);
            }
        }
        return new Sample(rows, lines.size());
    }

    /** 最小 CSV 行切分（支持引号与引号内转义 ""）。 */
    private static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    /**
     * 迟到旧任务保护（05 §5.1）：仅当该行仍是 (repo, ref) 的最新版本行时才应用变更，
     * 否则放弃写入（新预览已由更新的 job 接管）。
     */
    private boolean updateRowGuarded(PreviewArtifactEntity row, String refName,
                                     Consumer<PreviewArtifactEntity> mutator) {
        return Boolean.TRUE.equals(tx.execute(t -> {
            PreviewArtifactEntity current = previews.findById(row.getId()).orElse(null);
            if (current == null) {
                return false;
            }
            String effectiveRef = refName.isEmpty() ? current.getRefName() : refName;
            PreviewArtifactEntity latest = previews
                    .findFirstByRepositoryIdAndRefNameOrderByVersionDesc(current.getRepositoryId(), effectiveRef)
                    .orElse(null);
            if (latest == null || !latest.getId().equals(current.getId())) {
                log.info("预览行已被新版本取代，跳过回写 row={} ref={}", row.getId(), effectiveRef);
                return false;
            }
            mutator.accept(current);
            current.setUpdatedAt(OffsetDateTime.now());
            previews.save(current);
            return true;
        }));
    }

    private static String sha256Hex(String data) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
