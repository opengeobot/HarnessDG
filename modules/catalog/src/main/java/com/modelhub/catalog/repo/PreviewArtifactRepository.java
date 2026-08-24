package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.PreviewArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreviewArtifactRepository extends JpaRepository<PreviewArtifactEntity, Long> {

    /** 同一 (repo, ref) 的最新版本行（version 递增，迟到旧任务以此做写保护）。 */
    Optional<PreviewArtifactEntity> findFirstByRepositoryIdAndRefNameOrderByVersionDesc(
            Long repositoryId, String refName);

    Optional<PreviewArtifactEntity> findByJobId(Long jobId);
}
