package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.SchemaVersionEntity;
import com.modelhub.catalog.domain.SchemaVersionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersionEntity, SchemaVersionId> {

    Optional<SchemaVersionEntity> findByTypeKeyAndVersionAndStatus(String typeKey, Integer version, String status);

    List<SchemaVersionEntity> findByTypeKeyOrderByVersionDesc(String typeKey);
}
