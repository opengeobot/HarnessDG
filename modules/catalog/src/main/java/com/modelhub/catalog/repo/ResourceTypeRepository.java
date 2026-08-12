package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.ResourceTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceTypeRepository extends JpaRepository<ResourceTypeEntity, String> {

    List<ResourceTypeEntity> findByStatusOrderByTypeKey(String status);
}
