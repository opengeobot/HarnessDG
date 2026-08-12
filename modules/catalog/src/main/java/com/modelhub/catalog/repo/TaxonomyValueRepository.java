package com.modelhub.catalog.repo;

import com.modelhub.catalog.domain.TaxonomyValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxonomyValueRepository extends JpaRepository<TaxonomyValueEntity, Long> {

    List<TaxonomyValueEntity> findByTaxonomyIdOrderBySortOrder(Long taxonomyId);
}
