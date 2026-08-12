package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.DatasetProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetProfileRepository extends JpaRepository<DatasetProfileEntity, Long> {
}
