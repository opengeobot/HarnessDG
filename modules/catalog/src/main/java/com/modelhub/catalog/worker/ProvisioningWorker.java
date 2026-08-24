package com.modelhub.catalog.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.config.CatalogProperties;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.GiteaClient.GiteaRepo;
import com.modelhub.catalog.service.JobEventService;
import com.modelhub.identity.domain.NamespaceEntity;
import com.modelhub.identity.repo.NamespaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Outbox 事件处理器（05 §8/§9.2/§10）：
 * - provisioning Saga：幂等建 Gitea org/repo + 初始文件 + 回填 git_bindings；
 * - deletion：先置 private 再 archive，DB 置 deleted 并计算保留期；
 * - restore：解除 archive 并按目标可见性恢复。
 * 所有 Gitea 调用在事务外执行，DB 收口在独立事务中完成，失败由
 * {@link OutboxPoller} 按重试策略处理。
 */
@Service
public class ProvisioningWorker {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningWorker.class);

    private final RepositoryRepository repositories;
    private final NamespaceRepository namespaces;
    private final GitBindingRepository gitBindings;
    private final JobRepository jobs;
    private final GiteaClient gitea;
    private final TransactionTemplate tx;
    private final CatalogProperties props;
    private final JobEventService jobEvents;

    public ProvisioningWorker(RepositoryRepository repositories, NamespaceRepository namespaces,
                              GitBindingRepository gitBindings, JobRepository jobs, GiteaClient gitea,
                              TransactionTemplate tx, CatalogProperties props, JobEventService jobEvents) {
        this.repositories = repositories;
        this.namespaces = namespaces;
        this.gitBindings = gitBindings;
        this.jobs = jobs;
        this.gitea = gitea;
        this.tx = tx;
        this.props = props;
        this.jobEvents = jobEvents;
    }

    // ---------- provisioning Saga（05 §8） ----------

    public void handleProvision(JsonNode payload) {
        long repositoryId = payload.path("repositoryId").asLong();
        ProvisionTarget target = tx.execute(s -> {
            RepositoryEntity repo = repositories.findById(repositoryId).orElse(null);
            if (repo == null || !"provisioning".equals(repo.getLifecycleStatus())) {
                return null; // 已完成或被删除：事件直接标记投递成功（幂等）
            }
            NamespaceEntity ns = namespaces.findById(repo.getNamespaceId()).orElse(null);
            if (ns == null) {
                return null;
            }
            return new ProvisionTarget(repo.getId(), repo.getPublicId().toString(), ns.getSlug(),
                    ns.getDisplayName(), repo.getNormalizedName(), repo.getDisplayName(),
                    repo.getDescription(), repo.getResourceType(), repo.getVisibility(),
                    repo.getDefaultBranch());
        });
        if (target == null) {
            return;
        }
        startJob(target.publicId());

        // 事务外执行 Gitea 幂等动作
        gitea.ensureOrganization(target.nsSlug(), target.nsDisplayName());
        boolean isPrivate = !"public".equals(target.visibility());
        GiteaRepo external = gitea.ensureRepository(target.nsSlug(), target.name(), isPrivate);
        gitea.putFile(target.nsSlug(), target.name(), "README.md", readme(target),
                "chore: initialize repository", target.defaultBranch());
        gitea.putFile(target.nsSlug(), target.name(), "LICENSE", mitLicense(),
                "chore: add LICENSE", target.defaultBranch());
        gitea.putFile(target.nsSlug(), target.name(), "MODELHUB_MANIFEST.json", manifest(target),
                "chore: add ModelHub manifest", target.defaultBranch());
        String headSha = gitea.headCommitSha(target.nsSlug(), target.name(), target.defaultBranch());

        // 事务内收口：回填投影 + 置 active
        tx.executeWithoutResult(s -> {
            RepositoryEntity repo = repositories.findById(target.repositoryId()).orElse(null);
            if (repo == null || !"provisioning".equals(repo.getLifecycleStatus())) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now();
            GitBindingEntity binding = gitBindings.findByRepositoryId(repo.getId()).orElseGet(() -> {
                GitBindingEntity b = new GitBindingEntity();
                b.setRepositoryId(repo.getId());
                return b;
            });
            binding.setProvider("gitea");
            binding.setExternalRepositoryId(String.valueOf(external.id()));
            binding.setExternalNamespace(target.nsSlug());
            binding.setExternalName(target.name());
            binding.setObjectFormat("sha1");
            binding.setSyncStatus("synced");
            binding.setLastSyncedAt(now);
            binding.setLastError(null);
            gitBindings.save(binding);

            repo.setLifecycleStatus("active");
            repo.setLatestCommitSha(headSha);
            repo.setProvisionLastError(null);
            repo.setProvisionNextRetryAt(null);
            repo.setUpdatedAt(now);
            repositories.save(repo);
        });
        log.info("provisioning 完成: {}/{}", target.nsSlug(), target.name());
    }

    private record ProvisionTarget(long repositoryId, String publicId, String nsSlug, String nsDisplayName,
                                   String name, String displayName, String description, String type,
                                   String visibility, String defaultBranch) {}

    private static String readme(ProvisionTarget t) {
        String title = t.displayName() == null || t.displayName().isBlank() ? t.name() : t.displayName();
        StringBuilder sb = new StringBuilder("# ").append(title).append("\n\n");
        if (t.description() != null && !t.description().isBlank()) {
            sb.append(t.description()).append("\n\n");
        }
        return sb.append("本仓库由 ModelHub 管理（").append(t.type()).append("）。\n").toString();
    }

    private static String mitLicense() {
        return "MIT License\n\nCopyright (c) " + java.time.Year.now() + " ModelHub Contributors\n\n"
                + "Permission is hereby granted, free of charge, to any person obtaining a copy\n"
                + "of this software and associated documentation files (the \"Software\"), to deal\n"
                + "in the Software without restriction, including without limitation the rights\n"
                + "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n"
                + "copies of the Software, and to permit persons to whom the Software is\n"
                + "furnished to do so, subject to the following conditions:\n\n"
                + "The above copyright notice and this permission notice shall be included in all\n"
                + "copies or substantial portions of the Software.\n\n"
                + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n"
                + "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n"
                + "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.\n";
    }

    private static String manifest(ProvisionTarget t) {
        return "{\"manifestVersion\":1,\"repositoryId\":\"" + t.publicId()
                + "\",\"type\":\"" + t.type() + "\",\"name\":\"" + t.name()
                + "\",\"defaultBranch\":\"" + t.defaultBranch() + "\"}";
    }

    // ---------- deletion Saga（05 §9.2 第 2 步） ----------

    public void handleDeletion(JsonNode payload) {
        long repositoryId = payload.path("repositoryId").asLong();
        DeleteTarget target = tx.execute(s -> {
            RepositoryEntity repo = repositories.findById(repositoryId).orElse(null);
            if (repo == null || !"deleting".equals(repo.getLifecycleStatus())) {
                return null;
            }
            NamespaceEntity ns = namespaces.findById(repo.getNamespaceId()).orElse(null);
            if (ns == null) {
                return null;
            }
            return new DeleteTarget(repo.getId(), repo.getPublicId().toString(),
                    ns.getSlug(), repo.getNormalizedName(),
                    gitBindings.findByRepositoryId(repo.getId()).isPresent());
        });
        if (target == null) {
            return;
        }
        startJob(target.publicId());

        // 未完成 provisioning（无 git_bindings）的仓库无需外部归档，直接 DB 收口（05 §9.2 容错）
        if (target.bound()) {
            gitea.archiveRepository(target.nsSlug(), target.name());
        }

        tx.executeWithoutResult(s -> {
            RepositoryEntity repo = repositories.findById(target.repositoryId()).orElse(null);
            if (repo == null || !"deleting".equals(repo.getLifecycleStatus())) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now();
            repo.setLifecycleStatus("deleted");
            repo.setDeletedAt(now);
            repo.setRetentionUntil(now.plusDays(props.getRetentionDays()));
            repo.setUpdatedAt(now);
            repositories.save(repo);
            completeJob(target.publicId(), "repository.deletion");
        });
        log.info("仓库已置为 deleted: {}/{}", target.nsSlug(), target.name());
    }

    private record DeleteTarget(long repositoryId, String publicId, String nsSlug, String name,
                                boolean bound) {}

    // ---------- restore Saga（05 §9.2 第 3 步） ----------

    public void handleRestore(JsonNode payload) {
        long repositoryId = payload.path("repositoryId").asLong();
        DeleteTarget target = tx.execute(s -> {
            RepositoryEntity repo = repositories.findById(repositoryId).orElse(null);
            // API 层已将状态恢复到目标态；仅 active/archived 需要 Gitea 解除归档
            if (repo == null || (!"active".equals(repo.getLifecycleStatus())
                    && !"archived".equals(repo.getLifecycleStatus()))) {
                return null;
            }
            NamespaceEntity ns = namespaces.findById(repo.getNamespaceId()).orElse(null);
            if (ns == null) {
                return null;
            }
            return new DeleteTarget(repo.getId(), repo.getPublicId().toString(),
                    ns.getSlug(), repo.getNormalizedName(), true);
        });
        if (target == null) {
            return;
        }
        startJob(target.publicId());
        boolean isPrivate = !"public".equals(payload.path("visibility").asText("public"));
        gitea.unarchiveRepository(target.nsSlug(), target.name(), isPrivate);
        tx.executeWithoutResult(s -> completeJob(target.publicId(), "repository.restore"));
        log.info("仓库已恢复: {}/{}", target.nsSlug(), target.name());
    }

    private void completeJob(String publicId, String preferredType) {
        transitionJob(publicId, "succeeded");
    }

    /** Job 首次被处理时置 running 并记录事件（幂等：重复投递不重复记录）。 */
    private void startJob(String publicId) {
        tx.executeWithoutResult(s -> transitionJob(publicId, "running"));
    }

    /** 状态迁移 + startedAt/finishedAt 维护 + job_events 记录（与调用方同事务）。 */
    private void transitionJob(String publicId, String toStatus) {
        jobs.findFirstByAggregateTypeAndAggregateIdAndStatusInOrderByCreatedAtDesc(
                        "repository", publicId, List.of("queued", "running"))
                .ifPresent(job -> {
                    if (toStatus.equals(job.getStatus())) {
                        return;
                    }
                    OffsetDateTime now = OffsetDateTime.now();
                    job.setStatus(toStatus);
                    if ("running".equals(toStatus) && job.getStartedAt() == null) {
                        job.setStartedAt(now);
                    }
                    if (isTerminal(toStatus) && job.getFinishedAt() == null) {
                        job.setFinishedAt(now);
                    }
                    job.setUpdatedAt(now);
                    jobs.save(job);
                    jobEvents.record(job.getId(), "status_changed", Map.of("status", toStatus));
                });
    }

    private static boolean isTerminal(String status) {
        return "succeeded".equals(status) || "failed".equals(status)
                || "cancelled".equals(status) || "dead_letter".equals(status);
    }
}
