package com.modelhub.identity.repo;

import com.modelhub.identity.domain.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    Optional<ApiKeyEntity> findByPublicId(UUID publicId);

    List<ApiKeyEntity> findByKeyPrefixAndRevokedAtIsNull(String prefix);

    List<ApiKeyEntity> findByUserIdAndRevokedAtIsNull(Long userId);
}