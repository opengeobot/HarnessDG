package com.modelhub.artifact.repo;

import com.modelhub.artifact.domain.ObjectBlobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ObjectBlobRepository extends JpaRepository<ObjectBlobEntity, Long> {

    Optional<ObjectBlobEntity> findByPublicId(UUID publicId);

    /** 租户内去重（05 §7 / 03 §5.4）：去重键为 (namespace, sha256, size_bytes)，同 sha 不同大小不共享 blob。 */
    Optional<ObjectBlobEntity> findByNamespaceIdAndSha256AndSizeBytes(
            Long namespaceId, String sha256, long sizeBytes);
}
