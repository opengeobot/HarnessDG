package com.modelhub.artifact.repo;

import com.modelhub.artifact.domain.ObjectBlobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ObjectBlobRepository extends JpaRepository<ObjectBlobEntity, Long> {

    Optional<ObjectBlobEntity> findByPublicId(UUID publicId);

    /** 租户内 sha256 去重（05 §7）：同一 namespace 下同 hash 只保留一个 blob。 */
    Optional<ObjectBlobEntity> findByNamespaceIdAndSha256(Long namespaceId, String sha256);
}
