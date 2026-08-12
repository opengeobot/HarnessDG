package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.ModelProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelProfileRepository extends JpaRepository<ModelProfileEntity, Long> {
}
