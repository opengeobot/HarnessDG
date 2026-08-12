package com.modelhub.artifact.repo;

import com.modelhub.artifact.domain.FileVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersionEntity, Long> {

    Optional<FileVersionEntity> findByPublicId(UUID publicId);

    /** 同一 (repo, branch, path) 的 head 版本（staging/active 部分唯一索引保证至多一条）。 */
    Optional<FileVersionEntity> findByRepositoryIdAndBranchAndPathAndStatusIn(
            Long repositoryId, String branch, String path, Collection<String> statuses);

    List<FileVersionEntity> findByRepositoryIdAndBranchOrderByPathAsc(Long repositoryId, String branch);

    List<FileVersionEntity> findByRepositoryIdAndBranchAndPathOrderByCreatedAtDesc(
            Long repositoryId, String branch, String path);

    List<FileVersionEntity> findByRepositoryId(Long repositoryId);
}
