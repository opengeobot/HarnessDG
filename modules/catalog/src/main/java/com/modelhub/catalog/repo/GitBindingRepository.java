package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.GitBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitBindingRepository extends JpaRepository<GitBindingEntity, Long> {

    Optional<GitBindingEntity> findByRepositoryId(Long repositoryId);
}
