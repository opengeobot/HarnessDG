package com.modelhub.identity.repo;

import com.modelhub.identity.domain.NamespaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NamespaceRepository extends JpaRepository<NamespaceEntity, Long> {

    Optional<NamespaceEntity> findByUserIdAndNamespaceType(Long userId, String namespaceType);

    Optional<NamespaceEntity> findByOrganizationIdAndNamespaceType(Long organizationId, String namespaceType);

    boolean existsBySlug(String slug);
}
