package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.GatedRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatedRequestRepository extends JpaRepository<GatedRequestEntity, Long> {

    Optional<GatedRequestEntity> findByPublicId(UUID publicId);

    Optional<GatedRequestEntity> findFirstByRepositoryIdAndUserIdAndStatus(
            Long repositoryId, Long userId, String status);

    List<GatedRequestEntity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    List<GatedRequestEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
